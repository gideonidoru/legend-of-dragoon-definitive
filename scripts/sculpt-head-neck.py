#!/usr/bin/env python3
"""Retract a generated central neck stub behind its authored chin. AGPL v3.

Custom mesh input only. Keeps topology, UVs, crown, facial landmarks and hair;
changes only an explicitly authored central region below the chin. This is an
authoring experiment and still requires native body/pose review.
"""
import argparse,json
from pathlib import Path
import numpy as np,trimesh
BASIS=np.array([[0,1,0],[0,0,-1],[-1,0,0]])
def sculpt(mesh_path,controls_path,output):
    if output.exists():raise ValueError('Use a new output')
    import hashlib
    control=json.loads(controls_path.read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    if sha(mesh_path)!=control['meshSha256']:raise ValueError('Changed custom mesh')
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()));raw=np.asarray(mesh.vertices).copy();p=raw@BASIS.T;before=p.copy();chin=control['chinY'];transition=control['transition'];radius=control['centralHalfWidth'];back=control['maximumFrontZ'];start=chin+control['chinClearance']
    if transition<=0 or radius<=0 or not 0<control['depthCompression']<=1:raise ValueError('Invalid neck controls')
    weight=np.clip((p[:,1]-start)/transition,0,1);weight=weight*weight*(3-2*weight)
    edge=np.clip((radius-np.abs(p[:,0]))/(radius*.25),0,1);edge=edge*edge*(3-2*edge)
    depth=np.clip((back-p[:,2])/.05,0,1);depth=depth*depth*(3-2*depth);weight*=edge*depth
    p[:,2]=(1-weight)*p[:,2]+weight*(control['neckCenterZ']+p[:,2]*control['depthCompression'])
    p[:,0]*=1-weight*control['widthCompression'];mesh.vertices=p@BASIS
    faces=np.asarray(mesh.faces);_,weld=np.unique(np.round(mesh.vertices,6),axis=0,return_inverse=True);cross=np.cross(mesh.vertices[faces[:,1]]-mesh.vertices[faces[:,0]],mesh.vertices[faces[:,2]]-mesh.vertices[faces[:,0]]);sums=np.zeros((weld.max()+1,3))
    for i in range(3):np.add.at(sums,weld[faces[:,i]],cross)
    normals=sums[weld];length=np.linalg.norm(normals,axis=1);normals[length<1e-10]=mesh.vertex_normals[length<1e-10];normals/=np.linalg.norm(normals,axis=1)[:,None];mesh.vertex_normals=normals
    if not np.array_equal(p[before[:,1]<=start],before[before[:,1]<=start]):raise ValueError('Facial region moved')
    if not np.isfinite(p).all() or np.any(np.linalg.norm(cross,axis=1)<1e-10):raise ValueError('Degenerate sculpt')
    output.mkdir();mesh.export(output/'head-neck-sculpt.glb');receipt={'state':'experimental-not-accepted','inputs':{mesh_path.name:sha(mesh_path),controls_path.name:sha(controls_path)},'changedVertices':int((weight>0).sum()),'triangles':len(faces),'faceTopologyChanged':False,'uvChanged':False,'facialVerticesMoved':0,'maximumDisplacement':float(np.linalg.norm(p-before,axis=1).max()),'method':'continuous central sub-chin neck retraction; no clipping or deleted faces'};(output/'sculpt-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for k in ['mesh','controls','output']:p.add_argument('--'+k,type=Path,required=True)
    a=p.parse_args();print(json.dumps(sculpt(a.mesh,a.controls,a.output)))
