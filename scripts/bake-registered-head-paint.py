#!/usr/bin/env python3
"""Bake one landmark-registered facial paint onto a custom head. AGPL v3.

Uses a coherent neural surface bake for unseen surfaces. Never blends two facial
paintings or projects profile facial features onto the front. Inputs are custom
assets; matched original-body review remains a separate private verification.
"""
import argparse, hashlib, json
from pathlib import Path
import numpy as np
import trimesh
from PIL import Image
from scipy.interpolate import RBFInterpolator
from scipy.ndimage import distance_transform_edt, map_coordinates
BASIS=np.array([[0,1,0],[0,0,-1],[-1,0,0]])

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def raster(vertices, faces, size, values):
    result=np.zeros((size,size,values.shape[1]),np.float32)
    covered=np.zeros((size,size),bool)
    for f in faces:
        tri=vertices[f];lo=np.maximum(np.floor(tri.min(0)).astype(int),0);hi=np.minimum(np.ceil(tri.max(0)).astype(int),size-1)
        if np.any(hi<lo):continue
        a,b,c=tri;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-10:continue
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
        weights=np.stack((wa,wb,1-wa-wb),-1);mask=weights.min(-1)>=-1e-8
        result[lo[1]:hi[1]+1,lo[0]:hi[0]+1][mask]=(weights@values[f])[mask]
        covered[lo[1]:hi[1]+1,lo[0]:hi[0]+1]|=mask
    return result,covered

def bake(mesh_path, base_path, paint_path, controls_path, output):
    if output.exists():raise ValueError('Output already exists')
    control=json.loads(controls_path.read_text())
    for key,path in [('meshSha256',mesh_path),('baseTextureSha256',base_path),('paintSha256',paint_path)]:
        if control[key]!=sha(path):raise ValueError('Changed input: '+key)
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()))
    raw=np.asarray(mesh.vertices);faces=np.asarray(mesh.faces);uv=np.asarray(mesh.visual.uv);ns=np.asarray(mesh.vertex_normals)
    if not np.isfinite([raw,ns]).all() or uv.min()<0 or uv.max()>1:raise ValueError('Invalid mesh')
    base=Image.open(base_path).convert('RGB');size=base.width
    if base.size!=(2048,2048):raise ValueError('Expected 2K coherent surface bake')
    xy=uv*[size,-size]+[0,size]
    maps,covered=raster(xy,faces,size,np.column_stack((raw@BASIS.T,ns@BASIS.T)))
    p=maps[covered,:3];n=maps[covered,3:];n/=np.maximum(np.linalg.norm(n,axis=1)[:,None],1e-9)
    camera=control['camera'];res=camera['size'];scale=camera['scale'];center=np.asarray(camera['center'])
    screen=(raw@BASIS.T)[:,:2]*scale+center
    depth=np.full((res,res),np.inf)
    # Visibility must be computed from the same mesh/camera as the landmarks.
    for f in faces:
        tri=screen[f];lo=np.maximum(np.floor(tri.min(0)).astype(int),0);hi=np.minimum(np.ceil(tri.max(0)).astype(int),res-1)
        if np.any(hi<lo):continue
        a,b,c=tri;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-10:continue
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
        weights=np.stack((wa,wb,1-wa-wb),-1);z=weights@(raw@BASIS.T)[f,2]
        old=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1];ok=(weights.min(-1)>=-1e-8)&(z<old);old[ok]=z[ok]
    anchors=control['anchors'];actual=np.array([a['meshPixel'] for a in anchors]);paint=np.array([a['paintPixel'] for a in anchors])
    if len(anchors)<9 or not np.isfinite([actual,paint]).all():raise ValueError('Insufficient finite anatomical anchors')
    warp=RBFInterpolator(actual,paint,kernel='thin_plate_spline',smoothing=0)
    residual=float(np.linalg.norm(warp(actual)-paint,axis=1).max())
    if residual>1e-5:raise ValueError('Registration does not reproduce anchors')
    pixels=p[:,:2]*scale+center
    mapped=np.concatenate([warp(pixels[i:i+65536]) for i in range(0,len(pixels),65536)])
    source=Image.open(paint_path).convert('RGBA').crop(tuple(control['paintRect']))
    rgb=np.asarray(source,float)/255
    _,near=distance_transform_edt(rgb[...,3]<.98,return_indices=True)
    extended=rgb[near[0],near[1],:3]
    coords=np.stack((mapped[:,1]*(source.height-1)/(res-1),mapped[:,0]*(source.width-1)/(res-1)))
    sampled=np.column_stack([map_coordinates(extended[...,i],coords,order=1,mode='nearest') for i in range(3)])
    x=np.clip(np.floor(pixels[:,0]).astype(int),0,res-1);y=np.clip(np.floor(pixels[:,1]).astype(int),0,res-1)
    visible=np.isfinite(depth[y,x])&(np.abs(depth[y,x]-p[:,2])<.025)&(pixels.min(1)>=0)&(pixels.max(1)<res)
    facing=-n[:,2];valid=visible&(facing>.04)
    colors=np.asarray(base,float)[covered]/255
    # One facial source replaces the front completely. Fade only outside the
    # anatomical face, where a second eye/mouth/chin cannot survive underneath.
    face=control['faceRegion'];face_owned=(np.abs(p[:,0])<face['halfWidth'])&(p[:,1]>face['top'])&(p[:,1]<face['bottom'])&(p[:,2]<face['back'])
    facial_samples=pixels[valid&face_owned]
    if not len(facial_samples):raise ValueError('No visible registered facial surface')
    facial_samples=facial_samples[::max(1,len(facial_samples)//8192)]
    mapped_samples=warp(facial_samples)
    dx=warp(facial_samples+[1,0])-mapped_samples
    dy=warp(facial_samples+[0,1])-mapped_samples
    minimum_jacobian=float((dx[:,0]*dy[:,1]-dx[:,1]*dy[:,0]).min())
    if minimum_jacobian<=0:raise ValueError('Facial registration folds over itself')
    weight=np.clip((facing-.04)/.5,0,1)*valid;weight[valid&face_owned]=1
    prevented_features=0
    for feature in control.get('sourceFeatureLimits',[]):
        source_distance=np.sqrt(np.sum(((mapped-np.array(feature['paintPixel']))/np.array(feature['paintRadii']))**2,axis=1))
        actual_distance=np.sqrt(np.sum(((p-np.array(feature['commonPoint']))/np.array(feature['commonRadii']))**2,axis=1))
        source_region=np.clip((1.25-source_distance)/.25,0,1)
        actual_region=np.clip((1.25-actual_distance)/.25,0,1)
        prevention=source_region*(1-actual_region)
        prevented_features+=int(((prevention>.99)&(weight>0)).sum())
        weight*=1-prevention
    colors=colors*(1-weight[:,None])+sampled*weight[:,None]
    output.mkdir(parents=True)
    atlas=np.zeros((size,size,3),np.uint8);atlas[covered]=np.clip(colors*255,0,255).astype(np.uint8)
    d,nearest=distance_transform_edt(~covered,return_indices=True);padding=(~covered)&(d<=8);atlas[padding]=atlas[nearest[0,padding],nearest[1,padding]]
    tex=Image.fromarray(atlas);tex.save(output/'head-basecolour.png')
    mesh.visual=trimesh.visual.TextureVisuals(uv=uv,material=trimesh.visual.material.PBRMaterial(baseColorTexture=tex,roughnessFactor=1,metallicFactor=0));mesh.export(output/'head-textured.glb')
    receipt={'state':'development-needs-artistic-review','method':'one registered front facial source; coherent surface colors elsewhere','inputs':{str(p.name):sha(p) for p in [mesh_path,base_path,paint_path,controls_path]},'bakerSha256':sha(Path(__file__)),'anchorResidualPixels':residual,'minimumFacialWarpDeterminant':minimum_jacobian,'paintedTexels':int(valid.sum()),'fullyReplacedFacialTexels':int((valid&face_owned&(weight==1)).sum()),'preventedMisplacedFeatureTexels':prevented_features,'triangles':len(faces),'faceProfilePaintLayers':0,'accepted':False}
    (output/'bake-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['mesh','base','paint','controls','output']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args();print(json.dumps(bake(a.mesh,a.base,a.paint,a.controls,a.output)))
