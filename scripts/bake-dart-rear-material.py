#!/usr/bin/env python3
"""Project a custom rear reference onto the existing custom body UV atlas. AGPL v3.
This is a reproducible 3D material bake, not a concept-art substitute for gameplay.
Only visible rear-facing texels are transferred; accepted head and front paint are untouched.
"""
from pathlib import Path
import argparse,hashlib,json
import numpy as np,trimesh
from PIL import Image
from scipy.interpolate import RBFInterpolator
from scipy.ndimage import map_coordinates,distance_transform_edt
# Manually authored corresponding paint landmarks in the 640px sculpt inspection
# and 1254px custom rear reference. They are not a semantic landmark detector.
LANDMARKS=np.array([[[450,40],[890,58]],[[299,151],[500,305]],[[305,223],[520,429]],[[258,244],[480,440]],[[345,247],[639,446]],[[289,346],[517,691]],[[289,372],[512,742]],[[252,384],[483,764]],[[319,380],[598,767]],[[243,436],[473,879]],[[370,442],[684,886]],[[221,494],[438,971]],[[358,510],[678,999]],[[211,574],[398,1161]],[[372,590],[760,1190]],[[371,315],[719,611]]],dtype=float)

def bake(mesh_path,reference_path,output):
    if output.exists():raise ValueError('Use a new output')
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()));v=np.asarray(mesh.vertices);f=np.asarray(mesh.faces);uv=np.asarray(mesh.visual.uv);n=np.asarray(mesh.vertex_normals)
    image=mesh.visual.material.baseColorTexture.convert('RGB');reference=Image.open(reference_path).convert('RGBA')
    if image.size!=(2048,2048) or reference.size!=(1254,1254):raise ValueError('Bake inputs differ from the reviewed dimensions')
    center=(v.min(0)+v.max(0))*.5;extent=np.linalg.norm(v-center,axis=1).max();scale=620/(2*extent)
    def project(points):return np.column_stack((-(points[:,1]-center[1])*scale+320,-(points[:,2]-center[2])*scale+320))
    screen=project(v);depth=-v[:,0];z=np.full((640,640),-np.inf)
    def raster(coords,width,height):
        lo=np.maximum(np.floor(coords.min(0)).astype(int),0);hi=np.minimum(np.ceil(coords.max(0)).astype(int),[width-1,height-1])
        if np.any(hi<lo):return None
        a,b,c=coords;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-10:return None
        x,y=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5);wa=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/det;wb=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/det
        return lo,hi,np.stack((wa,wb,1-wa-wb),axis=-1)
    for face in f:
        result=raster(screen[face],640,640)
        if result is None:continue
        lo,hi,bary=result;d=bary@depth[face];patch=z[lo[1]:hi[1]+1,lo[0]:hi[0]+1];good=(bary.min(-1)>=-1e-8)&(d>patch);patch[good]=d[good]
    pixels=np.asarray(image).copy();covered=np.zeros((2048,2048),bool);changed=np.zeros_like(covered);warp=RBFInterpolator(LANDMARKS[:,0],LANDMARKS[:,1],kernel='thin_plate_spline',smoothing=4)
    rgba=np.asarray(reference,dtype=float)
    for face in f:
        coords=uv[face]*[2048,-2048]+[0,2048];result=raster(coords,2048,2048)
        if result is None:continue
        lo,hi,bary=result;valid=bary.min(-1)>=-1e-8
        if not valid.any():continue
        points=bary[valid]@v[face];normals=bary[valid]@n[face];normals/=np.maximum(np.linalg.norm(normals,axis=1)[:,None],1e-8)
        projected=project(points);indices=np.clip(projected.astype(int),0,639);visible=(-points[:,0]>=z[indices[:,1],indices[:,0]]-.015)
        target=warp(projected);sample=np.stack((target[:,1],target[:,0]));colour=np.column_stack([map_coordinates(rgba[...,c],sample,order=1,mode='constant',cval=0) for c in range(4)])
        weight=np.clip((-normals[:,0]-.08)/.55,0,1)*visible*(colour[:,3]/255)
        patch=pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1];old=patch[valid].astype(float);patch[valid]=np.clip(old*(1-weight[:,None])+colour[:,:3]*weight[:,None],0,255).astype(np.uint8)
        covered[lo[1]:hi[1]+1,lo[0]:hi[0]+1]|=valid;mask=changed[lo[1]:hi[1]+1,lo[0]:hi[0]+1];mask[valid]|=weight>.5
    distance,nearest=distance_transform_edt(~covered,return_indices=True);pad=(~covered)&(distance<=4);pixels[pad]=pixels[tuple(nearest[:,pad])]
    output.mkdir();Image.fromarray(pixels).save(output/'body-basecolour.png')
    receipt={'format':1,'state':'development-material-needs-rear-and-native-review','meshSha256':hashlib.sha256(mesh_path.read_bytes()).hexdigest(),'rearReferenceSha256':hashlib.sha256(reference_path.read_bytes()).hexdigest(),'originalTextureSha256':hashlib.sha256(image.tobytes()).hexdigest(),'textureSha256':hashlib.sha256((output/'body-basecolour.png').read_bytes()).hexdigest(),'rearTransferredTexels':int(changed.sum()),'landmarks':LANDMARKS.tolist(),'head':'Independent accepted head untouched','scope':'Visibility-aware rear projection; front sculpt paint retained. New reference alignment is manually authored.'}
    (output/'bake-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mesh',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();print(json.dumps(bake(a.mesh,a.reference,a.output)))
