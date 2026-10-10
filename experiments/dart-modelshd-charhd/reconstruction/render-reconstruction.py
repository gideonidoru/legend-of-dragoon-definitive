#!/usr/bin/env python3
"""Render actual reconstructed GLB geometry; studio inspection, not gameplay. AGPL v3."""
from pathlib import Path
import argparse,math
import numpy as np,trimesh
from PIL import Image
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mesh',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--size',type=int,default=640)
args=parser.parse_args()
width=height=args.size
def draw_mesh(mesh,yaw,clay):
 v=np.asarray(mesh.vertices);f=np.asarray(mesh.faces);normal=np.asarray(mesh.vertex_normals)
 center=(v.min(axis=0)+v.max(axis=0))*0.5;extent=np.linalg.norm(v-center,axis=1).max()
 angle=math.radians(yaw);toward=np.array([math.cos(angle),math.sin(angle),0.]);right=np.array([-math.sin(angle),math.cos(angle),0.]);up=np.array([0.,0.,1.])
 rel=v-center;scale=min((width-20)/(2*extent),(height-20)/(2*extent))
 xy=np.column_stack([rel@right*scale+width/2,-rel@up*scale+height/2]);depth=rel@toward
 canvas=np.zeros((height,width,4),np.uint8);zbuffer=np.full((height,width),-np.inf)
 light=toward+up*.7+right*.4;light/=np.linalg.norm(light);diffuse=.45+.55*np.maximum(0,normal@light)
 uv=np.asarray(mesh.visual.uv) if mesh.visual.kind=='texture' else None
 texture=np.asarray(mesh.visual.material.baseColorTexture.convert('RGB')) if uv is not None else None
 vertex_colour=np.asarray(mesh.visual.vertex_colors)[:,:3].astype(float) if uv is None else None
 for refs in f:
  tri=xy[refs];lo=np.maximum(np.floor(tri.min(axis=0)).astype(int),0);hi=np.minimum(np.ceil(tri.max(axis=0)).astype(int),[width-1,height-1])
  if np.any(hi<lo):continue
  a,b,c=tri;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(det)<1e-9:continue
  xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
  wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
  bary=np.stack([wa,wb,1-wa-wb],axis=-1);d=bary@depth[refs];old=zbuffer[lo[1]:hi[1]+1,lo[0]:hi[0]+1];mask=(bary.min(axis=-1)>=-1e-8)&(d>old)
  if clay:rgb=np.broadcast_to(np.array([190.,184.,170.]),(*mask.shape,3))
  elif uv is not None:
   tex_uv=bary@uv[refs];ux=np.clip((tex_uv[...,0]*texture.shape[1]).astype(int),0,texture.shape[1]-1);uy=np.clip(((1-tex_uv[...,1])*texture.shape[0]).astype(int),0,texture.shape[0]-1);rgb=texture[uy,ux].astype(float)
  else:rgb=bary@vertex_colour[refs]
  rgb=np.clip(rgb*(bary@diffuse[refs])[...,None],0,255).astype(np.uint8)
  old[mask]=d[mask];patch=canvas[lo[1]:hi[1]+1,lo[0]:hi[0]+1];patch[mask,:3]=rgb[mask];patch[mask,3]=255
 return Image.fromarray(canvas,'RGBA')
args.output.mkdir(parents=True,exist_ok=False)
scene=trimesh.load(args.mesh,force='scene',process=False)
mesh=trimesh.util.concatenate(list(scene.geometry.values())) if len(scene.geometry)>1 else next(iter(scene.geometry.values()))
for angle in [0,30,60,90,180,270,330]:
 for clay in [False,True]:
  image=draw_mesh(mesh,angle,clay)
  bg=Image.new('RGB',image.size,(26,30,34));bg.paste(image,mask=image.getchannel('A'))
  bg.save(args.output/(f'view-{angle}-'+('clay' if clay else 'colour')+'.png'))
print('Rendered actual GLB geometry',len(mesh.faces),'triangles')
