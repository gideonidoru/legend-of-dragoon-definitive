#!/usr/bin/env python3
"""Initial rigid-part retopology from custom neural body sculpts. AGPL v3.
Source native parts are read locally for identity, material lineage and pivot mapping.
Only generated geometry, its own material and metadata are written to public output.
Nearest-surface partitioning is a DEVELOPMENT fit, not joint/animation acceptance.
"""
from pathlib import Path
import argparse,hashlib,importlib.util,json,struct
import numpy as np
import trimesh
from scipy.spatial import cKDTree
ROOT=Path(__file__).resolve().parents[1]
HEAD_PARTS={'dart':[7],'rose':[7,10],'lavitz':[8],'shana':[12],'haschel':[7,8,9],'albert':[8],'meru':[8,9,10,11,13,14],'kongol':[7],'miranda':[11]}

def geometry_hash(part,headers):
    digest=hashlib.sha256();put=lambda n:digest.update(struct.pack('>I',n&0xffffffff));put(1);put(0)
    for rows in [part['vertices'],part['normals']]:put(len(rows));digest.update(np.asarray(rows,dtype='>f4').tobytes())
    put(len(part['faces']))
    for face in part['faces']:
        h=headers[face['sourceFace']];mode=h>>24;mode=(mode&~8)|(8 if len(face['vertices'])==4 else 0)|(16 if not mode&1 else 0)
        put((mode<<24|(h&0x40000))&0xff040000)
        for vertex,normal in zip(face['vertices'],face['normals']):
            if not mode&1:put(normal)
            put(vertex)
    return digest.hexdigest()

def fit(files,character,mesh_path,output):
    if output.exists():raise ValueError('Use a new candidate output')
    spec=importlib.util.spec_from_file_location('party_study',ROOT/'experiments/dart-modelshd-charhd/build-study.py');st=importlib.util.module_from_spec(spec);spec.loader.exec_module(st)
    audit=json.loads((ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json').read_text());actor=next(a for a in audit['characters'] if a['character']==character);record=next(m for m in actor['models'] if m['form']=='combat')
    model=files/record['source'];data=model.read_bytes()
    if hashlib.sha256(data).hexdigest()!=record['sourceSha256']:raise ValueError('Source drift')
    source,_,identity,ids,baseline=st.baseline(data);animation=model.with_name('0').read_bytes()
    if hashlib.sha256(animation).hexdigest()!=record['animationSha256']:raise ValueError('Animation drift')
    keys=st.poses.read_keyframes(animation,len(source));key=keys[0];posed=st.poses.posed_vertices(source,key,0)
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()));raw=np.asarray(mesh.vertices);normals=np.asarray(mesh.vertex_normals);uv=np.asarray(mesh.visual.uv)*[1,-1]+[0,1]
    basis=np.array([[0,1,0],[0,0,-1],[-1,0,0]]);raw=raw@basis.T;normals=normals@basis.T
    native=np.concatenate(posed);lo,hi=native.min(0),native.max(0);rl,rh=raw.min(0),raw.max(0)
    scale=(hi[1]-lo[1])/(rh[1]-rl[1]);points=(raw-(rl+rh)*.5)*scale+(lo+hi)*.5
    if not np.isfinite(points).all() or scale<=0:raise ValueError('Invalid fit')
    # Exact closest points within nearby source triangles, rather than part centroids.
    triangles=[];owners=[];source_faces=[]
    for index,((_,polygons),pos) in enumerate(zip(source,posed)):
        for face,(refs,*_) in enumerate(polygons):
            for corners in [(0,1,2),(1,3,2)] if len(refs)==4 else [(0,1,2)]:
                tri=pos[np.asarray(refs)[list(corners)]]
                if np.linalg.norm(np.cross(tri[1]-tri[0],tri[2]-tri[0]))>1e-8:
                    triangles.append(tri);owners.append(index);source_faces.append(face)
    triangles=np.asarray(triangles);owners=np.asarray(owners);source_faces=np.asarray(source_faces)
    centroids=points[np.asarray(mesh.faces)].mean(1);k=min(24,len(triangles));_,neighbors=cKDTree(triangles.mean(1)).query(centroids,k=k)
    closest=trimesh.triangles.closest_point(triangles[neighbors].reshape(-1,3,3),np.repeat(centroids,k,axis=0)).reshape(-1,k,3)
    distances=np.linalg.norm(closest-centroids[:,None],axis=2);col=distances.argmin(1);rows=np.arange(len(centroids));selected=neighbors[rows,col]
    assigned=owners[selected];face_sources=source_faces[selected];distance=distances[rows,col]
    output.mkdir(parents=True);(output/'parts').mkdir();(output/'uvs').mkdir();texture=mesh.visual.material.baseColorTexture.convert('RGB');texture.save(output/'body-basecolour.png');texture_sha=hashlib.sha256((output/'body-basecolour.png').read_bytes()).hexdigest()
    records=[];part_objects={};custom_faces=[];table=struct.unpack_from('<I',data)[0]+12
    for index in range(len(source)):
        if index in HEAD_PARTS[character]:continue
        selected_faces=np.flatnonzero(assigned==index)
        if not len(selected_faces):
            records.append({'sourcePartIndex':index,'state':'missing-generated-part-original-fallback-required','triangles':0});continue
        original_refs=np.asarray(mesh.faces)[selected_faces];refs,inverse=np.unique(original_refs,return_inverse=True);faces=inverse.reshape(-1,3)
        bone=st.poses.rotation(*(key[index,:3]*(2*np.pi/4096)));local=((points[refs]-key[index,3:])@bone).astype(np.float32);ns=(normals[refs]@bone).astype(np.float32);ns/=np.maximum(np.linalg.norm(ns,axis=1)[:,None],1e-8)
        old=baseline[index];signs=[]
        for f in old['faces']:
            a,b,c=np.asarray(old['vertices'])[f['vertices'][:3]];signs.append(np.dot(np.cross(b-a,c-a),np.asarray(old['normals'])[f['normals']].mean(0)))
        sign=np.sign(np.median(signs));packed=[]
        for face,original_face in zip(faces,face_sources[selected_faces]):
            face=face.tolist();a,b,c=local[face]
            if np.linalg.norm(np.cross(b-a,c-a))<1e-8:continue
            if np.dot(np.cross(b-a,c-a),ns[face].mean(0))*sign<0:face.reverse()
            corners=len(source[index][1][original_face][0]);weights=[1/corners]*corners
            packed.append({'sourceFace':int(original_face),'vertices':face,'normals':face,'sourceWeights':[weights]*3})
        if not packed:raise ValueError('Empty retained custom part')
        part={'vertices':local.tolist(),'normals':ns.tolist(),'faces':packed};payload={'version':1,'sourceGeometrySha256':ids[index],'parts':[part]};path=output/'parts'/(ids[index]+'.json');path.write_text(json.dumps(payload,separators=(',',':'),allow_nan=False))
        off,count=struct.unpack_from('<7I',data,table+index*28)[4:6];cursor=table+off;headers=[]
        for _ in range(count):
            h=struct.unpack_from('<I',data,cursor)[0];headers.append(h);cursor+=4+((h>>8)&255)*4
        geometry=geometry_hash(part,headers);binding={'version':1,'sourceGeometrySha256':ids[index],'geometrySha256':geometry,'uvs':uv[refs].tolist(),'texture':'body-basecolour.png','textureSha256':texture_sha};uvpath=output/'uvs'/(ids[index]+'.json');uvpath.write_text(json.dumps(binding,separators=(',',':'),allow_nan=False))
        open_edges=np.bincount(trimesh.Trimesh(local,faces,process=False).edges_unique_inverse)
        record={'character':character,'form':'combat','sourcePartIndex':index,'sourceGeometrySha256':ids[index],'geometrySha256':geometry,'baselineGeometrySha256':geometry_hash(old,headers),'pack':'parts/'+path.name,'packSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'uv':'uvs/'+uvpath.name,'uvSha256':hashlib.sha256(uvpath.read_bytes()).hexdigest(),'texture':'body-basecolour.png','textureSha256':texture_sha,'width':2048,'height':2048,'triangles':len(packed),'vertices':len(refs),'uncappedPartitionBoundaryEdges':int((open_edges==1).sum()),'p95SourceFitDistance':float(np.percentile(distance[selected_faces],95)),'state':'initial-rigid-part-partition-needs-joint-and-native-review'}
        part_objects[index]=part;records.append(record);custom_faces.extend(original_refs.tolist())
    # Verify rigid transforms across every stored idle keyframe; this is numerical evidence only.
    for pose in keys:
        for index,part in part_objects.items():
            bone=st.poses.rotation(*(pose[index,:3]*(2*np.pi/4096)));q=np.asarray(part['vertices'])@bone.T+pose[index,3:]
            if not np.isfinite(q).all():raise ValueError('Nonfinite stored pose')
    # Public editable mesh excludes generated face/hair parts: those use the separate approved/head candidates.
    meshbody=trimesh.Trimesh(points,np.asarray(custom_faces),vertex_normals=normals,process=False);meshbody.visual=trimesh.visual.TextureVisuals(uv=np.asarray(mesh.visual.uv),material=trimesh.visual.material.PBRMaterial(baseColorTexture=texture,roughnessFactor=1,metallicFactor=0));meshbody.remove_unreferenced_vertices();meshbody.export(output/'body-mesh.glb')
    manifest={'format':1,'state':'development-not-runtime-selected','parts':[row for row in records if 'pack' in row]};(output/'characters.json').write_text(json.dumps(manifest,indent=2)+'\n')
    receipt={'format':1,'character':character,'state':'development-not-runtime-selected','sourceModelSha256':hashlib.sha256(data).hexdigest(),'sourceAnimationSha256':hashlib.sha256(animation).hexdigest(),'meshInputSha256':hashlib.sha256(mesh_path.read_bytes()).hexdigest(),'textureSha256':texture_sha,'bodyMeshSha256':hashlib.sha256((output/'body-mesh.glb').read_bytes()).hexdigest(),'scale':scale,'storedIdlePoseSamples':len(keys),'excludedHeadParts':HEAD_PARTS[character],'parts':records,'scope':'Initial generated body partition on native rigid pivots. Uncapped joints, changed grips, missing thin accessories and side/rear material cleanup must be reviewed. UV fallback uses source material lineage; new material uses its own normalized atlas. Not animation, gameplay or Steam Deck acceptance.'};(output/'fit-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--files',type=Path,required=True);p.add_argument('--character',choices=list(HEAD_PARTS),required=True);p.add_argument('--mesh',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();result=fit(a.files,a.character,a.mesh,a.output);print(a.character,len(result['parts']),'body-part records')
