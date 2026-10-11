#!/usr/bin/env python3
"""Haschel authored skin development prototype. AGPL v3. Not runtime selected."""
from pathlib import Path
import numpy as np,trimesh,xatlas,importlib.util,json,hashlib
from PIL import Image
import argparse
parser=argparse.ArgumentParser(description='Build the unaccepted Haschel authored-skin experiment from custom public inputs.')
parser.add_argument('--authoring',type=Path)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
W=Path(__file__).resolve().parents[2]
A=args.authoring or W/'integrations/charhd/production/party-reconstruction/head-program-v4/haschel/authoring'
out=args.output
out.mkdir(parents=True)
B=np.array([[0,1,0],[0,0,-1],[-1,0,0]])

def module(n,p):s=importlib.util.spec_from_file_location(n,p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=module('b',Path(__file__).with_name('frontal-skin-baker.py'));surface=module('surface',W/'scripts/bake-head-surface-views.py')
# One continuous authored skin surface; no inferred facial mesh beneath it.
ykeys=np.array([-.34,-.25,-.17,-.08,0,.08,.16,.22,.28,.3227,.35,.39,.41,.43]);width=np.array([.12,.22,.25,.266,.27,.26,.235,.215,.185,.125,.11,.11,.112,.115]);front=np.array([-.26,-.31,-.34,-.365,-.372,-.381,-.393,-.412,-.423,-.4107,-.32,-.18,-.12,-.12]);back=np.array([.08,.13,.14,.15,.16,.15,.12,.09,.06,.03,.02,.015,.01,.01]);ys=np.linspace(ykeys[0],ykeys[-1],57);theta=np.linspace(-np.pi,np.pi,81);v=[]
for y in ys:
 rx=np.interp(y,ykeys,width);fz=np.interp(y,ykeys,front);bz=np.interp(y,ykeys,back);cz=(fz+bz)*.5;rz=(bz-fz)*.5
 for t in theta:
  x=rx*np.sin(t);z=cz-rz*np.cos(t);f=max(0,np.cos(t))**6
  gauss=lambda xx,yy,sx,sy:np.exp(-.5*((x-xx)/sx)**2-.5*((y-yy)/sy)**2)
  z-=f*(.091*gauss(.0009,.0765,.044,.065)+.022*gauss(-.0965,-.070,.070,.024)+.022*gauss(.0965,-.070,.070,.024)+.012*gauss(.0009,.2182,.10,.021))
  z+=f*(.012*gauss(-.0965,-.0405,.052,.020)+.012*gauss(.0965,-.0405,.052,.020))
  v.append([x,y,z])
f=[]
for j in range(len(ys)-1):
 for i in range(len(theta)-1):
  a=j*len(theta)+i;f.extend([[a,a+len(theta),a+1],[a+1,a+len(theta),a+len(theta)+1]])
# Authored ear surfaces attached at the skull side, not another painted ear on the cheek.
for sign in [-1,1]:
 start=len(v);nr,nt=17,49
 for ir in range(nr):
  radius=.005+.995*ir/(nr-1)
  for t in np.linspace(-np.pi,np.pi,nt):
   y=.035+radius*.095*np.sin(t);z=-.24+radius*.060*np.cos(t)
   x=sign*(.273+.026*np.exp(-((radius-.75)/.18)**2)+.014*(1-radius))
   v.append([x,y,z])
 for ir in range(nr-1):
  for it in range(nt-1):
   a=start+ir*nt+it;tri=[[a,a+nt,a+1],[a+1,a+nt,a+nt+1]]
   f.extend([t[::-1] if sign==1 else t for t in tri])
v=np.array(v);f=np.array(f);vm,idx,uv=xatlas.parametrize(v.astype('float32'),f.astype('uint32'));m=trimesh.Trimesh(v[vm]@B,idx,process=False);m.visual=trimesh.visual.TextureVisuals(uv=uv,material=trimesh.visual.material.PBRMaterial(baseColorTexture=Image.new('RGB',(2048,2048),(197,145,96))));m.export(out/'skin.glb');Image.new('RGB',(2048,2048),(197,145,96)).save(out/'base.png');c=json.loads((A/'front-registration.json').read_text());c.update(meshSha256=b.sha(out/'skin.glb'),baseTextureSha256=b.sha(out/'base.png'));c['sourceFeatureLimits']=[{**a,'commonRadii':[.001,.001,.001],'fillFromInnerSourceBorder':True} for a in c['sourceFeatureLimits']];(out/'controls.json').write_text(json.dumps(c,indent=2));print(b.bake(out/'skin.glb',out/'base.png',A/'head-paint-frontal-v1.png',out/'controls.json',out/'registered'),flush=True)
# Keep the jaw/neck skin free of stretched painted chin shadows.
mesh=next(iter(trimesh.load(out/'registered/head-textured.glb',force='scene',process=False).geometry.values()));pmap,covered=b.raster(np.asarray(mesh.visual.uv)*[2048,-2048]+[0,2048],np.asarray(mesh.faces),2048,np.asarray(mesh.vertices)@B.T);tex=np.asarray(Image.open(out/'registered/head-basecolour.png').convert('RGB')).copy();p=pmap[covered];w=np.clip((p[:,1]-.325)/.025,0,1);w=w*w*(3-2*w);colors=tex[covered].astype(float);tone=np.array([197,145,96]);tex[covered]=np.clip(colors*(1-w[:,None])+tone*w[:,None],0,255).astype('uint8');Image.fromarray(tex).save(out/'registered/head-basecolour.png');mesh.visual.material.baseColorTexture=Image.fromarray(tex);mesh.export(out/'registered/head-textured.glb')
c=json.loads((A/'profile-registration.json').read_text());c.update(meshSha256=b.sha(out/'registered/head-textured.glb'),baseTextureSha256=b.sha(out/'registered/head-basecolour.png'));c['geometricHair'].update(foreheadY=-99,backStart=99,sideHalfWidth=99,longHair=False)
for view in c['views']:
 sign=1 if view['angle']==90 else -1;view['ear'].update(commonPoint=[sign*.295,.035,-.24],commonRadii=[.050,.115,.078]);view['anchors'][7]['meshPixel']=[350-sign*.24*view['camera']['scale'],350+.035*view['camera']['scale']]
(out/'ear-controls.json').write_text(json.dumps(c,indent=2));original_confidence=surface.hair_confidence;surface.hair_confidence=lambda rgb,material:np.zeros(rgb.shape[:-1]) if rgb.ndim==2 else original_confidence(rgb,material)
print(surface.bake(out/'registered/head-textured.glb',out/'registered/head-basecolour.png',A/'head-paint-profile-v1.png',out/'ear-controls.json',out/'ear-registered'),flush=True)

# Keep only custom reconstructed hair surfaces, excluding the old face/jaw/neck.
hair=next(iter(trimesh.load(A/'coherent-head.glb',force='scene',process=False).geometry.values()));p=np.asarray(hair.vertices)@B.T;huv=np.asarray(hair.visual.uv);im=np.asarray(Image.open(A/'coherent-basecolour.png').convert('RGB'));xy=np.clip((huv*[2048,-2048]+[0,2048]).astype(int),0,2047);rgb=im[xy[:,1],xy[:,0]];dark=(rgb.mean(1)<105)&(rgb[:,0]<rgb[:,2]*1.4+10);cent=p[hair.faces].mean(1);keep=(cent[:,1]<-.16)|(((np.abs(cent[:,0])>.285)|(cent[:,2]>.03))&(dark[hair.faces].mean(1)>.65));skin=next(iter(trimesh.load(out/'ear-registered/head-textured.glb',force='scene',process=False).geometry.values()));sv=np.asarray(skin.vertices);sf=np.asarray(skin.faces);suv=np.asarray(skin.visual.uv);combined=trimesh.Trimesh(np.concatenate([sv,hair.vertices]),np.concatenate([sf,np.asarray(hair.faces)[keep]+len(sv)]),process=False);atlas=Image.new('RGB',(2048,2048));atlas.paste(Image.open(out/'ear-registered/head-basecolour.png').resize((1024,2048),Image.Resampling.LANCZOS),(0,0));atlas.paste(Image.open(A/'registered-head-basecolour.png').resize((1024,2048),Image.Resampling.LANCZOS),(1024,0));combined.visual=trimesh.visual.TextureVisuals(uv=np.concatenate([suv*[.5,1],huv*[.5,1]+[.5,0]]),material=trimesh.visual.material.PBRMaterial(baseColorTexture=atlas));combined.remove_unreferenced_vertices();combined.export(out/'head-textured.glb');atlas.save(out/'head-basecolour.png');print('skin',len(sf),'retained hair',keep.sum(),flush=True)

# Smooth UV seams on the exported float32 mesh, matching native fitting input.
combined=next(iter(trimesh.load(out/'head-textured.glb',force='scene',process=False).geometry.values()))
positions=np.asarray(combined.vertices)
faces=np.asarray(combined.faces)
_,weld=np.unique(np.round(positions,6),axis=0,return_inverse=True)
cross=np.cross(positions[faces[:,1]]-positions[faces[:,0]],positions[faces[:,2]]-positions[faces[:,0]])
sums=np.zeros((weld.max()+1,3))
for corner in range(3):np.add.at(sums,weld[faces[:,corner]],cross)
normals=sums[weld]
length=np.linalg.norm(normals,axis=1)
normals[length<1e-9]=np.asarray(combined.vertex_normals)[length<1e-9]
length=np.linalg.norm(normals,axis=1)
if not np.isfinite(normals).all() or np.any(length<1e-9):raise ValueError('Degenerate authored normal')
combined.vertex_normals=normals/length[:,None]
combined.export(out/'head-smoothed.glb')

inputs={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [A/'coherent-head.glb',A/'coherent-basecolour.png',A/'registered-head-basecolour.png',A/'head-paint-frontal-v1.png',A/'head-paint-profile-v1.png',A/'front-registration.json',A/'profile-registration.json']}
(out/'source-receipt.json').write_text(json.dumps({'state':'unaccepted-authoring-experiment','customInputSha256':inputs,'toolSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'originalRgbPixelsCopied':0,'baseFacePaintLayers':0,'frontalFacePaintLayers':1,'profileFacePaintLayers':0,'separateEarPaint':True,'geometryAndTexturePerformanceUnmeasured':True},indent=2)+'\n')
