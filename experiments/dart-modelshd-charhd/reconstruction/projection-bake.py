"""Visibility-aware transfer of custom source paint to reconstructed UVs. AGPL v3.
This is a 3D texture-baking operation, not a substitute concept/game screenshot.
"""
import numpy as np
from scipy.ndimage import map_coordinates

def project(points):
    depth=1.9-points[...,0]
    f=1/(2*np.tan(np.deg2rad(40)/2))
    return np.stack((.5+points[...,1]/depth*f,.5-points[...,2]/depth*f),axis=-1),depth

def transfer(points,normals,covered,rgba,mesh):
    size=1024
    uv,depth=project(np.asarray(mesh.vertices))
    projected=uv*size
    z=np.full((size,size),np.inf)
    for refs in mesh.faces:
        coords=projected[refs]
        lo=np.maximum(np.floor(coords.min(0)).astype(int),0);hi=np.minimum(np.ceil(coords.max(0)).astype(int),size-1)
        if np.any(hi<lo):continue
        a,b,c=coords;det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-10:continue
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
        weights=np.stack((wa,wb,1-wa-wb),axis=-1)
        d=1/(weights@(1/depth[refs]))
        old=z[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
        good=(weights.min(-1)>=-1e-8)&(d<old)
        old[good]=d[good]
    p=points[covered];n=normals[covered]
    source_uv,d=project(p)
    coords=np.stack((source_uv[:,1]*(rgba.height-1),source_uv[:,0]*(rgba.width-1)))
    pixels=np.asarray(rgba,dtype=float)
    sampled=np.column_stack([map_coordinates(pixels[...,c],coords,order=1,mode='nearest') for c in range(4)])
    zi=np.clip((source_uv*size).astype(int),0,size-1)
    visible=np.isfinite(z[zi[:,1],zi[:,0]]) & (np.abs(d-z[zi[:,1],zi[:,0]])<.025)
    toward=np.array([1.9,0,0])-p;toward/=np.linalg.norm(toward,axis=1)[:,None]
    facing=np.einsum('ij,ij->i',n,toward)
    weight=np.clip((facing-.1)/.5,0,1)
    weight*=visible*(sampled[:,3]/255)*(np.min(source_uv,axis=1)>=0)*(np.max(source_uv,axis=1)<=1)
    return sampled[:,:3]/255,weight,{'projectionTransferredTexels':int(np.count_nonzero(weight>.5)), 'projectionVisibleTexels':int(np.count_nonzero(visible))}
