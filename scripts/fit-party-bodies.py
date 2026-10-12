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
from PIL import Image,ImageDraw
from scipy.spatial import cKDTree
ROOT=Path(__file__).resolve().parents[1]
HEAD_PARTS={'dart':[7],'rose':[7,10],'lavitz':[8],'shana':[12],'haschel':[7,8,9],'albert':[8],'meru':[8,9,10,11,13,14],'kongol':[7],'miranda':[11]}

def closest_native_triangles(native_triangles,generated_centroids,metric):
    """Return indices in the supplied native triangle table and fit distances."""
    if not len(native_triangles):raise ValueError('Selected native part has no usable triangles')
    scaled_triangles=native_triangles*metric;query_points=generated_centroids*metric
    neighbor_count=min(48,len(native_triangles))
    _,neighbor_indices=cKDTree(scaled_triangles.mean(1)).query(query_points,k=neighbor_count)
    neighbor_indices=np.asarray(neighbor_indices).reshape(len(query_points),neighbor_count)
    closest_points=trimesh.triangles.closest_point(scaled_triangles[neighbor_indices].reshape(-1,3,3),np.repeat(query_points,neighbor_count,axis=0)).reshape(-1,neighbor_count,3)
    distances=np.linalg.norm(closest_points-query_points[:,None],axis=2)
    closest_columns=distances.argmin(1);query_indices=np.arange(len(query_points))
    return neighbor_indices[query_indices,closest_columns],distances[query_indices,closest_columns]

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

def fit(files,character,mesh_path,output,depth_weight=1.0,overlap_rings=0,form='combat',segmentation=None):
    if form not in ('field','combat'):raise ValueError('Unsupported native form')
    if form=='field' and character!='dart':raise ValueError('Only Dart has a verified field body part map')
    if overlap_rings not in (0,1,2):raise ValueError("Overlap ring count outside budget")
    if not .05<=depth_weight<=1:raise ValueError("Depth weight outside development fit budget")
    if output.exists():raise ValueError('Use a new candidate output')
    spec=importlib.util.spec_from_file_location('party_study',ROOT/'experiments/dart-modelshd-charhd/build-study.py');st=importlib.util.module_from_spec(spec);spec.loader.exec_module(st)
    audit=json.loads((ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json').read_text());actor=next(a for a in audit['characters'] if a['character']==character);record=next(m for m in actor['models'] if m['form']==form)
    model=files/record['source'];data=model.read_bytes()
    if hashlib.sha256(data).hexdigest()!=record['sourceSha256']:raise ValueError('Source drift')
    source,_,identity,ids,baseline=st.baseline(data);animation=model.with_name('0' if form=='combat' else str(int(model.name)+1)).read_bytes()
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
    centroids=points[np.asarray(mesh.faces)].mean(1);metric=np.array([1.,1.,depth_weight])
    nearest_native_indices,distance=closest_native_triangles(triangles,centroids,metric)
    assigned=owners[nearest_native_indices];face_sources=source_faces[nearest_native_indices]
    # Explicit anatomical regions replace unreliable nearest-surface ownership
    # where limbs and the sword overlap in the approved reconstruction pose.
    if segmentation:
        authored=json.loads(segmentation.read_text())
        if authored['character']!=character or authored['form']!=form or authored['meshInputSha256']!=hashlib.sha256(mesh_path.read_bytes()).hexdigest():raise ValueError('Part regions belong to a different sculpt')
        size=authored['viewSize'];padding=authored['viewPadding']
        if size!=640 or padding!=20:raise ValueError('Unsupported authored inspection frame')
        original=np.asarray(mesh.vertices);center=(original.min(0)+original.max(0))*.5;extent=np.linalg.norm(original-center,axis=1).max();pixel_scale=(size-padding)/(2*extent)
        pixels=np.column_stack(((original[:,1]-center[1])*pixel_scale+size/2,-(original[:,2]-center[2])*pixel_scale+size/2))[np.asarray(mesh.faces)].mean(1)
        labels=Image.new('I',(size,size),-1);draw=ImageDraw.Draw(labels)
        for region in authored['regions']:
            index=region['sourcePartIndex']
            if not 0<=index<len(source):raise ValueError('Unknown native part')
            draw.polygon([tuple(p) for p in region['polygon']],fill=index)
        indices=np.clip(pixels.astype(int),0,size-1);mapped=np.asarray(labels)[indices[:,1],indices[:,0]];mask=mapped>=0;assigned[mask]=mapped[mask]
        # Source-face lineage must be recomputed inside the selected native part.
        for index in np.unique(assigned):
            generated_face_indices=np.flatnonzero(assigned==index)
            native_triangle_indices=np.flatnonzero(owners==index)
            nearest_part_triangle_indices,part_distances=closest_native_triangles(triangles[native_triangle_indices],centroids[generated_face_indices],metric)
            nearest_native_indices=native_triangle_indices[nearest_part_triangle_indices]
            face_sources[generated_face_indices]=source_faces[nearest_native_indices]
            distance[generated_face_indices]=part_distances
    _,weld=np.unique(np.round(points,4),axis=0,return_inverse=True)
    adjacency=trimesh.Trimesh(np.unique(np.round(points,4),axis=0),weld[np.asarray(mesh.faces)],process=False).face_adjacency
    output.mkdir(parents=True);(output/'parts').mkdir();(output/'uvs').mkdir();texture=mesh.visual.material.baseColorTexture.convert('RGB');texture.save(output/'body-basecolour.png');texture_sha=hashlib.sha256((output/'body-basecolour.png').read_bytes()).hexdigest()
    records=[];part_objects={};custom_faces=[];table=struct.unpack_from('<I',data)[0]+12
    for index in range(len(source)):
        if index in HEAD_PARTS[character]:continue
        selected_faces=np.flatnonzero(assigned==index)
        owned=set(selected_faces.tolist())
        for _ in range(overlap_rings):
            neighbors=adjacency[np.isin(adjacency[:,0],selected_faces)|np.isin(adjacency[:,1],selected_faces)].reshape(-1)
            selected_faces=np.union1d(selected_faces,neighbors)
        if not len(selected_faces):
            records.append({'sourcePartIndex':index,'state':'missing-generated-part-original-fallback-required','triangles':0});continue
        original_refs=np.asarray(mesh.faces)[selected_faces];refs,inverse=np.unique(original_refs,return_inverse=True);faces=inverse.reshape(-1,3)
        bone=st.poses.rotation(*(key[index,:3]*(2*np.pi/4096)));part_points=points[refs].copy()
        # A single front image underestimates limb depth; restore the native rest
        # depth per rigid part without changing the approved front silhouette.
        if depth_weight<1:part_points[:,2]+=np.median(posed[index][:,2])-np.median(part_points[:,2])
        if overlap_rings:
            original_owned_refs=np.unique(np.asarray(mesh.faces)[np.asarray(sorted(owned))]);expanded=~np.isin(refs,original_owned_refs)
            part_points[expanded]-=normals[refs][expanded]*.75
        local=((part_points-key[index,3:])@bone).astype(np.float32);ns=(normals[refs]@bone).astype(np.float32);ns/=np.maximum(np.linalg.norm(ns,axis=1)[:,None],1e-8)
        old=baseline[index];signs=[]
        for f in old['faces']:
            a,b,c=np.asarray(old['vertices'])[f['vertices'][:3]];signs.append(np.dot(np.cross(b-a,c-a),np.asarray(old['normals'])[f['normals']].mean(0)))
        sign=np.sign(np.median(signs));packed=[]
        for face,original_face,triangle_index in zip(faces,face_sources[selected_faces],selected_faces):
            if triangle_index not in owned:original_face=0
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
        record={'character':character,'form':form,'sourcePartIndex':index,'sourceGeometrySha256':ids[index],'geometrySha256':geometry,'baselineGeometrySha256':geometry_hash(old,headers),'pack':'parts/'+path.name,'packSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'uv':'uvs/'+uvpath.name,'uvSha256':hashlib.sha256(uvpath.read_bytes()).hexdigest(),'texture':'body-basecolour.png','textureSha256':texture_sha,'width':2048,'height':2048,'triangles':len(packed),'vertices':len(refs),'uncappedPartitionBoundaryEdges':int((open_edges==1).sum()),'p95SourceFitDistance':float(np.percentile(distance[selected_faces],95)),'state':'initial-rigid-part-partition-needs-joint-and-native-review'}
        part_objects[index]=part;records.append(record);custom_faces.extend(original_refs.tolist())
    # Verify rigid transforms across every stored idle keyframe; this is numerical evidence only.
    for pose in keys:
        for index,part in part_objects.items():
            bone=st.poses.rotation(*(pose[index,:3]*(2*np.pi/4096)));q=np.asarray(part['vertices'])@bone.T+pose[index,3:]
            if not np.isfinite(q).all():raise ValueError('Nonfinite stored pose')
    # Public editable mesh excludes generated face/hair parts: those use the separate approved/head candidates.
    meshbody=trimesh.Trimesh(points,np.asarray(custom_faces),vertex_normals=normals,process=False);meshbody.visual=trimesh.visual.TextureVisuals(uv=np.asarray(mesh.visual.uv),material=trimesh.visual.material.PBRMaterial(baseColorTexture=texture,roughnessFactor=1,metallicFactor=0));meshbody.remove_unreferenced_vertices();meshbody.export(output/'body-mesh.glb')
    manifest={'format':1,'state':'development-not-runtime-selected','parts':[row for row in records if 'pack' in row]};(output/'characters.json').write_text(json.dumps(manifest,indent=2)+'\n')
    receipt={'format':1,'character':character,'form':form,'state':'development-not-runtime-selected','sourceModelSha256':hashlib.sha256(data).hexdigest(),'sourceAnimationSha256':hashlib.sha256(animation).hexdigest(),'meshInputSha256':hashlib.sha256(mesh_path.read_bytes()).hexdigest(),'textureSha256':texture_sha,'bodyMeshSha256':hashlib.sha256((output/'body-mesh.glb').read_bytes()).hexdigest(),'scale':scale,'partitionDepthWeight':depth_weight,'overlapRings':overlap_rings,'segmentationSha256':hashlib.sha256(segmentation.read_bytes()).hexdigest() if segmentation else None,'storedIdlePoseSamples':len(keys),'excludedHeadParts':HEAD_PARTS[character],'parts':records,'scope':'Initial generated body partition on native rigid pivots. Uncapped joints, changed grips, missing thin accessories and side/rear material cleanup must be reviewed. UV fallback uses source material lineage; new material uses its own normalized atlas. Not animation, gameplay or Steam Deck acceptance.'};(output/'fit-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--files',type=Path,required=True);p.add_argument('--character',choices=list(HEAD_PARTS),required=True);p.add_argument('--form',choices=('field','combat'),default='combat');p.add_argument('--mesh',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--depth-weight',type=float,default=1.0);p.add_argument('--overlap-rings',type=int,default=0);p.add_argument('--segmentation',type=Path);a=p.parse_args();result=fit(a.files,a.character,a.mesh,a.output,a.depth_weight,a.overlap_rings,a.form,a.segmentation);print(a.character,a.form,len(result['parts']),'body-part records')
