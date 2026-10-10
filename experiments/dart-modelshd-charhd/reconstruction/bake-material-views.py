#!/usr/bin/env python3
"""Bake custom three-view paint onto actual mesh UVs with occlusion checks. AGPL v3."""
from pathlib import Path
import argparse,json,hashlib
import numpy as np,trimesh
from PIL import Image
from scipy.ndimage import map_coordinates,distance_transform_edt
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mesh',type=Path,required=True)
parser.add_argument('--paint',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--angles',type=int,nargs='+',default=[0,90,180],help='Paint columns from left to right')
parser.add_argument('--hair-only',action='store_true',help='Restrict opposite-side cleanup to hair; preserve the established face and bandana')
a=parser.parse_args();a.output.mkdir(parents=True,exist_ok=False)
m=next(iter(trimesh.load(a.mesh,force='scene',process=False).geometry.values()))
v=np.asarray(m.vertices);ns=np.asarray(m.vertex_normals);faces=np.asarray(m.faces);uv=np.asarray(m.visual.uv)
res=2048;points=np.zeros((res,res,3),np.float32);normals=np.zeros_like(points);covered=np.zeros((res,res),bool)
xy=uv*[res,-res]+[0,res]
def raster(coords,extent):
 lo=np.maximum(np.floor(coords.min(0)).astype(int),0);hi=np.minimum(np.ceil(coords.max(0)).astype(int),extent-1)
 if np.any(hi<lo):return None
 x,y=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
 p,q,r=coords;d=(q[1]-r[1])*(p[0]-r[0])+(r[0]-q[0])*(p[1]-r[1])
 if abs(d)<1e-9:return None
 w0=((q[1]-r[1])*(x-r[0])+(r[0]-q[0])*(y-r[1]))/d
 w1=((r[1]-p[1])*(x-r[0])+(p[0]-r[0])*(y-r[1]))/d
 w=np.stack((w0,w1,1-w0-w1),-1)
 return lo,hi,w,w.min(-1)>=-1e-8
for refs in faces:
 data=raster(xy[refs],res)
 if data is None:continue
 lo,hi,w,valid=data
 points[lo[1]:hi[1]+1,lo[0]:hi[0]+1][valid]=(w@v[refs])[valid]
 normals[lo[1]:hi[1]+1,lo[0]:hi[0]+1][valid]=(w@ns[refs])[valid]
 covered[lo[1]:hi[1]+1,lo[0]:hi[0]+1]|=valid
p=points[covered];n=normals[covered];n/=np.maximum(np.linalg.norm(n,axis=1)[:,None],1e-8)
source=np.asarray(Image.open(a.paint).convert('RGB'),dtype=float)
center=(v.min(0)+v.max(0))*.5;extent=np.linalg.norm(v-center,axis=1).max();scale=(640-20)/(2*extent)/640
colour=np.zeros((len(p),3));sum_weight=np.zeros(len(p));reports=[]
for column,angle in enumerate(a.angles):
 theta=np.deg2rad(angle);toward=np.array([np.cos(theta),np.sin(theta),0]);right=np.array([-np.sin(theta),np.cos(theta),0]);up=np.array([0,0,1])
 screen=np.column_stack(((v-center)@right*scale+.5,-(v-center)@up*scale+.5))
 depth=(v-center)@toward;z=np.full((640,640),-np.inf)
 for refs in faces:
  data=raster(screen[refs]*640,640)
  if data is None:continue
  lo,hi,w,valid=data;d=w@depth[refs];old=z[lo[1]:hi[1]+1,lo[0]:hi[0]+1];valid&=d>old;old[valid]=d[valid]
 pos=np.column_stack(((p-center)@right*scale+.5,-(p-center)@up*scale+.5));d=(p-center)@toward
 zi=np.clip((pos*640).astype(int),0,639);visible=np.isfinite(z[zi[:,1],zi[:,0]])&(np.abs(d-z[zi[:,1],zi[:,0]])<.025)
 weight=np.maximum(0,n@toward)**3*visible*(pos.min(1)>=0)*(pos.max(1)<=1)
 coords=np.stack((pos[:,1]*(source.shape[0]-1),(pos[:,0]+column)*source.shape[1]/len(a.angles)))
 sampled=np.column_stack([map_coordinates(source[...,c],coords,order=1,mode='nearest') for c in range(3)])
 # Never transfer the neutral studio background across an imperfectly registered silhouette.
 weight*=((sampled.max(1)-sampled.min(1))>18)&(sampled[:,0]>50)&(sampled[:,0]>sampled[:,1]*.98)
 colour+=sampled*weight[:,None];sum_weight+=weight
 reports.append({'viewDegrees':angle,'visibleTexels':int(visible.sum()),'weightedTexels':int((weight>.1).sum())})
pixels=np.array(m.visual.material.baseColorTexture.convert('RGB'))
if pixels.shape[:2]!=(res,res):raise ValueError('Expected 2K UV texture')
old=pixels[covered].astype(float);factor=np.clip(sum_weight*4,0,1)
if a.hair_only:
 hair=((old[:,0]-old[:,1])<(old[:,1]-old[:,2])*1.1)&(old[:,1]>old[:,0]*.42)
 hair&=(p[:,2]>-.20)&~((p[:,0]>.08)&(p[:,2]<.16))
 factor*=hair
result=old*(1-factor[:,None])+colour/np.maximum(sum_weight[:,None],1e-8)*factor[:,None]
pixels[covered]=np.clip(result,0,255).astype(np.uint8)
distance,nearest=distance_transform_edt(~covered,return_indices=True);padding=(~covered)&(distance<=8);pixels[padding]=pixels[tuple(nearest[:,padding])]
image=Image.fromarray(pixels);image.save(a.output/'head-6000-basecolour.png')
m.visual=trimesh.visual.TextureVisuals(uv=uv,material=trimesh.visual.material.PBRMaterial(baseColorTexture=image,metallicFactor=0,roughnessFactor=1))
m.export(a.output/'head-6000-textured.glb')
check=next(iter(trimesh.load(a.output/'head-6000-textured.glb',force='scene',process=False).geometry.values()))
assert np.array_equal(v,check.vertices) and np.array_equal(faces,check.faces) and np.array_equal(uv,check.visual.uv), 'Material bake changed geometry or UVs'
assert np.allclose(ns,check.vertex_normals,atol=1e-6), 'Material bake changed normals'
record={'status':'experimental-not-approved','meshSourceSha256':hashlib.sha256(a.mesh.read_bytes()).hexdigest(),'paintSha256':hashlib.sha256(a.paint.read_bytes()).hexdigest(),'geometryUnchanged':True,'coveredTexels':int(covered.sum()),'transferredTexels':int((factor>.5).sum()),'views':reports,'textureSha256':hashlib.sha256((a.output/'head-6000-basecolour.png').read_bytes()).hexdigest()}
(a.output/'manifest.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))
