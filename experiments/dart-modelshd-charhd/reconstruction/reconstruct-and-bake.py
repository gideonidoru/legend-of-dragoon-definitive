#!/usr/bin/env python3
"""Offline Dart AA head reconstruction and UV bake. AGPL v3; see LICENSE.
Reuses the locally verified TripoSR pipeline (MIT, Tripo AI and Stability AI).
Supply isolated local tooling and a custom RGBA source image. No game inputs,
weights, runtime dependencies or private diagnostics are published by this tool.
"""
from pathlib import Path
import argparse,importlib.util
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tooling',type=Path,required=True)
parser.add_argument('--image',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--character',default='dart',help='Authoring receipt identity; does not imply approval')
parser.add_argument('--asset-kind',choices=['head','body'],default='head')
parser.add_argument('--triangles',type=int,nargs='+',default=[6000,12000])
args=parser.parse_args()
import os,sys,json,time,hashlib,resource,socket,subprocess
os.environ.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HUB_DISABLE_TELEMETRY='1',TORCH_FORCE_WEIGHTS_ONLY_LOAD='1')
def no_network(*args,**kwargs):raise RuntimeError('Network is disabled during private inference')
socket.socket.connect=no_network;socket.socket.connect_ex=no_network;socket.create_connection=no_network
root=args.tooling.resolve()
output=args.output.resolve();output.mkdir(parents=True,exist_ok=False)
source=root/'TripoSR-local-study';weights=root/'TripoSR-weights'
input_image=args.image.resolve()
def digest(p):
 with Path(p).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
assert digest(weights/'model.ckpt')=='429e2c6b22a0923967459de24d67f05962b235f79cde6b032aa7ed2ffcd970ee'
assert subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()=='107cefdc244c39106fa830359024f6a2f1c78871'
assert not subprocess.check_output(['git','-C',str(source),'status','--porcelain'],text=True).strip()
manifest={'experiment':'party-aa-head-reconstruction-1','character':args.character,'assetKind':args.asset_kind,'sourceRevision':'107cefdc244c39106fa830359024f6a2f1c78871','inputSha256':digest(input_image),'weightsSha256':digest(weights/'model.ckpt'),'configSha256':digest(weights/'config.yaml'),'dinoConfigSha256':digest(weights/'dino-config.json'),'dependencyLockSha256':digest(root/'dependency-lock-round30.txt'),'runnerSha256':digest(__file__),'device':'cpu','threads':8,'resolution':256,'foregroundRatio':0.85,'networkDisabled':True,'nativeImport':False,'artAccepted':False}
began=time.monotonic(); stages={}
def stage(name):print(json.dumps({'stage':name,'elapsedSeconds':round(time.monotonic()-began,3)}),flush=True)
stage('Importing isolated runtime')
sys.path.insert(0,str(source))
import numpy as np,torch
from PIL import Image
from tsr.system import TSR
from tsr.utils import resize_foreground
import tsr.models.tokenizers.image as tokenizer
# Resolve the already downloaded supplier config locally; never use a Hub request.
def local_config(repo_id,filename,**kwargs):
 if repo_id!='facebook/dino-vitb16' or filename!='config.json':raise RuntimeError('Unexpected model configuration request')
 return str(weights/'dino-config.json')
tokenizer.hf_hub_download=local_config
torch.set_num_threads(8);torch.set_num_interop_threads(2);torch.manual_seed(0)
manifest['torch']=torch.__version__;manifest['python']=sys.version
stage('Loading verified weights'); t=time.monotonic()
model=TSR.from_pretrained(str(weights),config_name='config.yaml',weight_name='model.ckpt')
model.eval().to('cpu');model.renderer.set_chunk_size(4096)
stages['loadSeconds']=time.monotonic()-t
rgba=resize_foreground(Image.open(input_image).convert('RGBA'),0.85)
array=np.asarray(rgba,dtype=np.float32)/255.0
rgb=array[...,:3]*array[...,3:4]+0.5*(1-array[...,3:4])
image=Image.fromarray(np.clip(rgb*255,0,255).astype(np.uint8),'RGB')
manifest['inferencePixelsSha256']=hashlib.sha256(image.tobytes()).hexdigest();manifest['inferenceSize']=list(image.size)
stage('Inferring head volume');t=time.monotonic()
with torch.inference_mode():codes=model([image],device='cpu')
stages['inferenceSeconds']=time.monotonic()-t

import trimesh,fast_simplification,xatlas
from PIL import Image
from scipy.ndimage import distance_transform_edt
stage('Extracting 256-grid head volume');t=time.monotonic()
with torch.inference_mode():mesh=model.extract_mesh(codes,has_vertex_color=True,resolution=256)[0]
assert len(mesh.vertices) and len(mesh.faces) and np.isfinite(mesh.vertices).all()
source_mesh=output/'head-full.glb';mesh.export(source_mesh)
stages['extractSeconds']=time.monotonic()-t
parts=mesh.split(only_watertight=False,repair=False)
assert sum(len(p.faces) for p in parts)==len(mesh.faces)
main=max(parts,key=lambda p:len(p.faces))
manifest.update(sourceMeshSha256=digest(source_mesh),sourceTriangles=len(mesh.faces),mainComponentTriangles=len(main.faces),excludedComponentTriangles=[len(p.faces) for p in parts if p is not main],componentRepair=False,textureSize=[2048,2048],fastSimplification='0.2.0',xatlas='0.0.11',variants=[])
if not args.triangles or len(args.triangles)>3 or any(not 1000<=t<=30000 for t in args.triangles):raise ValueError('Invalid authoring density budget')
for target in args.triangles:
 stage(f'Reducing experimental head to {target} triangles');t=time.monotonic()
 v,f=fast_simplification.simplify(np.asarray(main.vertices,dtype=np.float64),np.asarray(main.faces,dtype=np.int32),target_count=target,agg=5.0,preserve_border=True)
 reduced=trimesh.Trimesh(v,f,process=False);reduced.remove_unreferenced_vertices()
 original_positions=reduced.vertices.copy()
 counts=np.bincount(reduced.edges_unique_inverse,minlength=len(reduced.edges_unique))
 pinned=np.unique(reduced.edges_unique[counts==1])
 laplacian=trimesh.smoothing.laplacian_calculation(reduced,pinned_vertices=pinned)
 trimesh.smoothing.filter_taubin(reduced,lamb=0.35,nu=0.36,iterations=6,laplacian_operator=laplacian)
 displacement=np.linalg.norm(reduced.vertices-original_positions,axis=1)
 assert np.array_equal(reduced.vertices[pinned],original_positions[pinned]),'Boundary moved'
 assert displacement.max()<0.03,'Cleanup exceeded shape budget'
 assert np.isfinite(reduced.vertices).all() and len(reduced.faces)>0
 atlas=xatlas.Atlas();atlas.add_mesh(np.asarray(reduced.vertices,dtype=np.float32),np.asarray(reduced.faces,dtype=np.uint32),normals=np.asarray(reduced.vertex_normals,dtype=np.float32))
 pack=xatlas.PackOptions();pack.resolution=512;pack.padding=8
 chart=xatlas.ChartOptions();chart.max_cost=8.0;chart.normal_deviation_weight=1.0;chart.normal_seam_weight=1.0
 atlas.generate(chart_options=chart,pack_options=pack)
 mapping,indices,uvs=atlas[0];uv_vertices=np.asarray(reduced.vertices)[mapping]
 assert np.isfinite(uvs).all() and uvs.min()>=0 and uvs.max()<=1
 resolution=2048;points=np.zeros((resolution,resolution,3),dtype=np.float32);baked_normals=np.zeros_like(points);covered=np.zeros((resolution,resolution),dtype=bool)
 xy=uvs*np.array([resolution,-resolution])+np.array([0,resolution])
 empty_faces=0
 for face in indices:
  tri=xy[face];lo=np.maximum(np.floor(tri.min(axis=0)).astype(int),0);hi=np.minimum(np.ceil(tri.max(axis=0)).astype(int),resolution-1)
  if np.any(hi<lo):empty_faces+=1;continue
  a,b,c=tri;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(det)<1e-10:empty_faces+=1;continue
  xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+0.5,np.arange(lo[1],hi[1]+1)+0.5)
  wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
  bary=np.stack([wa,wb,1-wa-wb],axis=-1);valid=bary.min(axis=-1)>=-1e-8
  if not valid.any():empty_faces+=1;continue
  patch=points[lo[1]:hi[1]+1,lo[0]:hi[0]+1];patch[valid]=(bary@uv_vertices[face])[valid]
  baked_normals[lo[1]:hi[1]+1,lo[0]:hi[0]+1][valid]=(bary@np.asarray(reduced.vertex_normals)[mapping][face])[valid]
  covered[lo[1]:hi[1]+1,lo[0]:hi[0]+1]|=valid
 stage(f'Baking {target}-triangle face texture')
 with torch.inference_mode():
  colour=model.renderer.query_triplane(model.decoder,torch.from_numpy(points[covered]),codes[0])['color'].cpu().numpy()
 projection_spec=importlib.util.spec_from_file_location('projection',Path(__file__).with_name('projection-bake.py'))
 projection=importlib.util.module_from_spec(projection_spec);projection_spec.loader.exec_module(projection)
 transferred,weight,transfer_report=projection.transfer(points,baked_normals,covered,rgba,reduced)
 colour=colour*(1-weight[:,None])+transferred*weight[:,None]
 pixels=np.zeros((resolution,resolution,3),dtype=np.uint8);pixels[covered]=np.clip(colour*255,0,255).astype(np.uint8)
 distance,nearest=distance_transform_edt(~covered,return_indices=True);padding=(~covered)&(distance<=4)
 pixels[padding]=pixels[tuple(nearest[:,padding])]
 texture=Image.fromarray(pixels,'RGB');texture_file=output/f'head-{target}-basecolour.png';texture.save(texture_file)
 material=trimesh.visual.material.PBRMaterial(baseColorTexture=texture,metallicFactor=0.0,roughnessFactor=1.0)
 candidate=trimesh.Trimesh(uv_vertices,indices,vertex_normals=np.asarray(reduced.vertex_normals)[mapping],process=False)
 candidate.visual=trimesh.visual.TextureVisuals(uv=uvs,material=material)
 mesh_file=output/f'head-{target}-textured.glb';candidate.export(mesh_file)
 manifest['variants'].append({'targetTriangles':target,**transfer_report,'cleanupIterations':6,'pinnedBoundaryVertices':len(pinned),'maximumCleanupDisplacement':float(displacement.max()),'p95CleanupDisplacement':float(np.percentile(displacement,95)),'triangles':len(candidate.faces),'verticesWithUvSeams':len(candidate.vertices),'watertightAfterUvSplits':bool(candidate.is_watertight),'coveredTexels':int(covered.sum()),'facesWithoutPixelCenters':empty_faces,'uvCharts':atlas.get_mesh_chart_count(0),'atlasPackingDimensions':[atlas.width,atlas.height],'paddingPixels':4,'elapsedSeconds':time.monotonic()-t,'meshSha256':digest(mesh_file),'textureSha256':digest(texture_file),'glbBytes':mesh_file.stat().st_size})
manifest.update(stages=stages,elapsedSeconds=time.monotonic()-began,peakRssBytes=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,artAccepted=False,nativeImport=False)
(output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest),flush=True)
