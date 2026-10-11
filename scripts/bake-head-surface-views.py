#!/usr/bin/env python3
"""Register profile hair/ear paint without transferring another face. AGPL v3.

This authoring experiment keeps the registered frontal face immutable. Every
profile uses the same mesh, explicit camera, visible-surface depth test and
authored anatomical anchors. It is not a runtime or release selection.
"""
from pathlib import Path
import argparse,importlib.util,json,hashlib
import numpy as np
from PIL import Image
import trimesh
from scipy.interpolate import RBFInterpolator
from scipy.ndimage import distance_transform_edt,map_coordinates,gaussian_filter
spec=importlib.util.spec_from_file_location('front_bake',Path(__file__).with_name('bake-registered-head-paint.py'))
front=importlib.util.module_from_spec(spec);spec.loader.exec_module(front)

def hair_confidence(rgb,kind):
    r,g,b=rgb[...,0],rgb[...,1],rgb[...,2]
    if kind=='white':return np.clip((np.minimum.reduce((r,g,b))-.46)/.20,0,1)*np.clip((.28-(np.maximum.reduce((r,g,b))-np.minimum.reduce((r,g,b))))/.16,0,1)
    if kind=='blue':return np.clip((b-np.maximum(r,g)*.93)/.06,0,1)
    if kind=='brown':return np.clip((.66-r)/.22,0,1)*np.clip((r-b-.035)/.08,0,1)
    if kind=='charcoal':return np.clip((.46-np.maximum.reduce((r,g,b)))/.18,0,1)*np.clip((.15-(np.maximum.reduce((r,g,b))-np.minimum.reduce((r,g,b))))/.10,0,1)
    if kind=='blond':return np.clip(((g-b)/np.maximum(r-g,.025)-1.08)/.65,0,1)
    raise ValueError('Unknown authored hair material')

def bake(mesh_path,base_path,paint_path,controls_path,output):
    if output.exists():raise ValueError('Use a new output')
    control=json.loads(controls_path.read_text())
    if {f['name'] for f in control['featureLocks']}!={'left-eye','right-eye','nose','mouth','chin'}:raise ValueError('Require all five authored facial feature locks')
    if not control['views'] or any(v['angle'] not in [-90,90,180] for v in control['views']):raise ValueError('Only explicit side/rear surface views are supported')
    for key,path in [('meshSha256',mesh_path),('baseTextureSha256',base_path),('paintSha256',paint_path)]:
        if control[key]!=front.sha(path):raise ValueError('Changed input: '+key)
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()))
    raw=np.asarray(mesh.vertices);faces=np.asarray(mesh.faces);uv=np.asarray(mesh.visual.uv);normal=np.asarray(mesh.vertex_normals)
    base=Image.open(base_path).convert('RGB');size=base.width
    if base.size!=(2048,2048):raise ValueError('Expected 2K surface bake')
    maps,covered=front.raster(uv*[size,-size]+[0,size],faces,size,np.column_stack((raw@front.BASIS.T,normal@front.BASIS.T)))
    p=maps[covered,:3];n=maps[covered,3:];n/=np.maximum(np.linalg.norm(n,axis=1)[:,None],1e-9)
    vertices=raw@front.BASIS.T;old=np.asarray(base)[covered];colors=old.astype(float)/255
    raw_hair=hair_confidence(old.astype(float)/255,control['hairMaterial'])
    lock=control['frontalFaceLock'];locked=(np.abs(p[:,0])<lock['halfWidth'])&(p[:,1]>lock['top'])&(p[:,1]<lock['bottom'])&(p[:,2]<lock['back'])&(raw_hair<.2)
    lock_distance=np.maximum.reduce((np.abs(p[:,0])-lock['halfWidth'],lock['top']-p[:,1],p[:,1]-lock['bottom'],p[:,2]-lock['back']))
    feather=np.clip(lock_distance/.06,0,1);feather=feather*feather*(3-2*feather)
    feather[raw_hair>=.2]=1
    for feature in control['featureLocks']:
        distance=np.sqrt(np.sum(((p-np.array(feature['commonPoint']))/np.array(feature['commonRadii']))**2,axis=1))
        locked|=distance<=1
        feather*=np.clip((distance-1)/.4,0,1)
    feather[locked]=0
    facial_height=(p[:,1]>lock['top'])&(p[:,1]<lock['bottom'])
    hair=raw_hair
    hair_atlas=np.zeros((size,size));hair_atlas[covered]=hair
    hair=(gaussian_filter(hair_atlas,8)/np.maximum(gaussian_filter(covered.astype(float),8),1e-9))[covered]
    def smooth(value):
        value=np.clip(value,0,1);return value*value*(3-2*value)
    volume=control['geometricHair'];scalp=smooth((volume['foreheadY']+.08-p[:,1])/.14);rear=smooth((p[:,2]-volume['backStart']+.08)/.16);side=smooth((np.abs(p[:,0])-volume['sideHalfWidth']+.08)/.16)*smooth((p[:,2]+.18)/.16);geometric=np.maximum.reduce((scalp,rear,side))
    if not volume['longHair']:geometric*=smooth((volume['chinY']+.065-p[:,1])/.08)
    if control['hairMaterial']=='white':geometric=scalp*smooth((.23-np.abs(p[:,0]))/.07)
    if control['hairMaterial']=='blue' and control.get('protectGoldOrnaments',False):geometric*=~((old[:,0]>old[:,2]*1.3)&(old[:,1]>old[:,2]*1.15))
    if control.get('protectRedBand',False):geometric*=~((old[:,0]>old[:,1]*2)&(old[:,0]>old[:,2]*2))
    hair=np.maximum(hair,geometric)
    receipts=[]
    for view in control['views']:
        a=np.deg2rad(view['angle']);rotation=np.array([[np.cos(a),0,np.sin(a)],[0,1,0],[-np.sin(a),0,np.cos(a)]])
        camera=view['camera'];res=camera['size'];scale=camera['scale'];center=np.asarray(camera['center']);v=vertices@rotation.T;posed=p@rotation.T;ns=n@rotation.T
        screen=v[:,:2]*scale+center;depth=np.full((res,res),np.inf)
        for f in faces:
            tri=screen[f];lo=np.maximum(np.floor(tri.min(0)).astype(int),0);hi=np.minimum(np.ceil(tri.max(0)).astype(int),res-1)
            if np.any(hi<lo):continue
            b0,b1,b2=tri;det=(b1[1]-b2[1])*(b0[0]-b2[0])+(b2[0]-b1[0])*(b0[1]-b2[1])
            if abs(det)<1e-10:continue
            xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
            w0=((b1[1]-b2[1])*(xx-b2[0])+(b2[0]-b1[0])*(yy-b2[1]))/det
            w1=((b2[1]-b0[1])*(xx-b2[0])+(b0[0]-b2[0])*(yy-b2[1]))/det
            weights=np.stack((w0,w1,1-w0-w1),-1);z=weights@v[f,2];old_depth=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1];ok=(weights.min(-1)>=-1e-8)&(z<old_depth);old_depth[ok]=z[ok]
        anchors=view['anchors'];actual=np.array([a['meshPixel'] for a in anchors]);paint=np.array([a['paintPixel'] for a in anchors]);warp=RBFInterpolator(actual,paint,kernel='thin_plate_spline',smoothing=0)
        pixels=posed[:,:2]*scale+center;mapped=np.concatenate([warp(pixels[i:i+65536]) for i in range(0,len(pixels),65536)])
        source=Image.open(paint_path).convert('RGBA').crop(tuple(view['paintRect']))
        if view.get('mirrorPaint',False):source=source.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        rgba=np.asarray(source,float)/255;_,nearest=distance_transform_edt(rgba[...,3]<.98,return_indices=True);extended=rgba[nearest[0],nearest[1],:3]
        coords=np.stack((mapped[:,1]*(source.height-1)/(res-1),mapped[:,0]*(source.width-1)/(res-1)))
        sampled=np.column_stack([map_coordinates(extended[...,i],coords,order=1,mode='nearest') for i in range(3)])
        hair_source=(hair_confidence(rgba[...,:3],control['hairMaterial'])>.5)&(rgba[...,3]>.98)
        if view['angle']!=180:
            ear=view['ear'];yy,xx=np.indices(hair_source.shape);ex,ey=np.asarray(ear['paintPixel'])*np.array([source.width-1,source.height-1])/(res-1)
            source_region=(yy<ey-55*source.height/res)|((xx>ex-40*source.width/res) if view['angle']>0 else (xx<ex+40*source.width/res))
            hair_source&=source_region
            if control['hairMaterial'] not in ['white','blue']:
                rear_of_ear=(xx>ex+40*source.width/res) if view['angle']>0 else (xx<ex-40*source.width/res)
                hair_source|=rear_of_ear&(rgba[...,3]>.98)
        elif control['hairMaterial'] not in ['white','blue']:hair_source=rgba[...,3]>.98
        elif control['hairMaterial']=='blue':hair_source=(hair_confidence(rgba[...,:3],'blue')>.15)&(rgba[...,3]>.98)
        if not hair_source.any():raise ValueError('No verified source hair region')
        _,nearest_hair=distance_transform_edt(~hair_source,return_indices=True)
        hair_rgb=rgba[nearest_hair[0],nearest_hair[1],:3]
        sampled_hair=np.column_stack([map_coordinates(hair_rgb[...,i],coords,order=1,mode='nearest') for i in range(3)])
        x=np.clip(np.floor(pixels[:,0]).astype(int),0,res-1);y=np.clip(np.floor(pixels[:,1]).astype(int),0,res-1)
        facing=-ns[:,2];visible=np.isfinite(depth[y,x])&(np.abs(depth[y,x]-posed[:,2])<.025)&(pixels.min(1)>=0)&(pixels.max(1)<res)
        # Depth limits exclude the nose, eyes and mouth from all profile layers.
        back_weight=np.clip((p[:,2]-view['minimumAnatomicalBack'])/.06,0,1)
        back_weight=back_weight*back_weight*(3-2*back_weight)
        back_weight[~facial_height]=1
        hair_weight=np.clip((facing-.10)/.65,0,1)*visible*back_weight*feather*hair
        ear_weight=np.zeros(len(p))
        if view['angle']!=180 and view['ear'].get('paintEar',True):
            ear=view['ear'];ellipsoid=np.sqrt(np.sum(((p-np.array(ear['commonPoint']))/np.array(ear['commonRadii']))**2,axis=1));inside=np.clip((1.15-ellipsoid)/.35,0,1)
            source_ellipse=np.sqrt(np.sum(((mapped-np.array(ear['paintPixel']))/np.array(ear['paintRadii']))**2,axis=1));source_gate=np.clip((1.1-source_ellipse)/.25,0,1)
            ear_weight=np.clip((facing-.10)/.65,0,1)*visible*inside*source_gate*feather
        weight=np.maximum(hair_weight,ear_weight)
        selected=weight>0;samples=pixels[selected][::max(1,int(selected.sum())//8192)];mapped_samples=warp(samples);dx=warp(samples+[1,0])-mapped_samples;dy=warp(samples+[0,1])-mapped_samples
        determinant=float((dx[:,0]*dy[:,1]-dx[:,1]*dy[:,0]).min())
        residual=float(np.linalg.norm(warp(actual)-paint,axis=1).max())
        if determinant<=0 or residual>1e-5:raise ValueError('Surface registration folds or misses its anchors')
        colors=colors*(1-hair_weight[:,None])+sampled_hair*hair_weight[:,None]
        colors=colors*(1-ear_weight[:,None])+sampled*ear_weight[:,None]
        receipts.append({'angle':view['angle'],'paintedTexels':int(selected.sum()),'hairTexels':int((hair_weight>0).sum()),'earTexels':int((ear_weight>0).sum()),'minimumWarpDeterminant':determinant,'anchorResidualPixels':residual})
    quantized=np.clip(colors*255,0,255).astype(np.uint8);quantized[locked]=old[locked]
    if not np.array_equal(quantized[locked],old[locked]):raise ValueError('Frontal face changed')
    atlas=np.zeros((size,size,3),np.uint8);atlas[covered]=quantized;distance,nearest=distance_transform_edt(~covered,return_indices=True);padding=(~covered)&(distance<=8);atlas[padding]=atlas[nearest[0,padding],nearest[1,padding]]
    output.mkdir(parents=True);tex=Image.fromarray(atlas);tex.save(output/'head-basecolour.png');mesh.visual=trimesh.visual.TextureVisuals(uv=uv,material=trimesh.visual.material.PBRMaterial(baseColorTexture=tex,roughnessFactor=1,metallicFactor=0));mesh.export(output/'head-textured.glb')
    receipt={'state':'experimental-not-accepted','method':'registered visible profile hair and ears; immutable frontal facial paint','inputs':{p.name:front.sha(p) for p in [mesh_path,base_path,paint_path,controls_path]},'bakerSha256':front.sha(Path(__file__)),'lockedFrontalFaceTexels':int(locked.sum()),'lockedFrontalFaceTexelsChanged':0,'protectedFeatures':[f['name'] for f in control['featureLocks']],'profileFacialFeatureLayers':0,'views':receipts,'triangles':len(faces),'accepted':False};(output/'bake-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['mesh','base','paint','controls','output']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args();print(json.dumps(bake(a.mesh,a.base,a.paint,a.controls,a.output)))
