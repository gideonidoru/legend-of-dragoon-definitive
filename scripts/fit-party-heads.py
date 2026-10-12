#!/usr/bin/env python3
"""Development native-pivot fits for custom party heads. AGPL v3.
Reads local original sources. Custom packs/UVs/GLBs are public deliverables;
full actor controls and inspection renders must remain outside the checkout.
Crown/chin fits are development candidates, not native gameplay acceptance.
"""
import argparse,hashlib,importlib.util,json,struct
from pathlib import Path
import numpy as np
import trimesh
ROOT=Path(__file__).resolve().parents[1]
HEADS={'rose':(9,10),'lavitz':(7,8),'shana':(7,12),'haschel':(7,7),'albert':(7,8),'meru':(6,8),'kongol':(7,7),'miranda':(7,11)}


def face_anchor(vertices, crown, chin):
    """Frontmost central face surface, independent of rear hair/neck volume.

    This is a framing anchor, not a semantic facial landmark detector. Crown and
    chin are explicitly authored; matching orthographic views remain mandatory.
    """
    lo,hi=vertices.min(0),vertices.max(0)
    center=(lo[0]+hi[0])*.5
    face=vertices[(vertices[:,1]>crown+(chin-crown)*.30)&
                  (vertices[:,1]<crown+(chin-crown)*.85)&
                  (np.abs(vertices[:,0]-center)<(hi[0]-lo[0])*.28)]
    if len(face)<3:raise ValueError('Insufficient central face anchor vertices')
    front=face[face[:,2]<=face[:,2].min()+(chin-crown)*.02]
    return front.mean(0)


def fit(files,character,mesh_path,output,landmarks_path=None):
    if output.exists() or output.resolve().is_relative_to(ROOT):raise ValueError('Use a new private study folder')
    spec=importlib.util.spec_from_file_location('party_study',ROOT/'experiments/dart-modelshd-charhd/build-study.py');study=importlib.util.module_from_spec(spec);spec.loader.exec_module(study)
    source_audit=json.loads((ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json').read_text())
    actor=next(r for r in source_audit['characters'] if r['character']==character)
    landmarks_path=landmarks_path or ROOT/'integrations/charhd/production/party-reconstruction/head-proportion-landmarks-v1.json'
    landmarks=json.loads(landmarks_path.read_text())['characters'][character]
    if hashlib.sha256(mesh_path.read_bytes()).hexdigest()!=landmarks['meshInputSha256']:
        raise ValueError('Proportion landmarks belong to a different custom mesh')
    mesh=next(iter(trimesh.load(mesh_path,force='scene',process=False).geometry.values()))
    raw=np.asarray(mesh.vertices);normals=np.asarray(mesh.vertex_normals);raw_uv=np.asarray(mesh.visual.uv)
    # Neural reconstruction faces +X, native inspection camera faces -Z; native up is -Y.
    basis=np.array([[0,1,0],[0,0,-1],[-1,0,0]])
    common=raw@basis.T;common_normals=normals@basis.T
    output.mkdir(parents=True);(output/'parts').mkdir();records=[]
    texture=mesh.visual.material.baseColorTexture.convert('RGB');texture.save(output/'head-basecolour.png')
    texture_hash=hashlib.sha256((output/'head-basecolour.png').read_bytes()).hexdigest()
    for form,head_index in zip(('field','combat'),HEADS[character]):
        record=next(r for r in actor['models'] if r['form']==form)
        data=(files/record['source']).read_bytes()
        if hashlib.sha256(data).hexdigest()!=record['sourceSha256']:raise ValueError('Source changed')
        parts,native,identity,part_ids,current=study.baseline(data)
        model=files/record['source'];anim=model.with_name(str(int(model.name)+1) if form=='field' else '0').read_bytes()
        if hashlib.sha256(anim).hexdigest()!=record['animationSha256']:raise ValueError('Animation changed')
        keys=study.poses.read_keyframes(anim,len(parts));posed=study.poses.posed_vertices(parts,keys[0],0)
        head=posed[head_index];target=landmarks['forms'][form]
        crown,chin=landmarks['rawCrownY'],landmarks['rawChinY']
        target_crown,target_chin=target['sourceCrownY'],target['sourceChinY']
        source_crown=target_crown
        if 'targetPosedBodyToSkullHeightRatio' in target:
            ratio=target['targetPosedBodyToSkullHeightRatio']
            if not 5<=ratio<=9:raise ValueError('Invalid authored body/head proportion')
            height=(np.concatenate(posed)[:,1].max()-target_chin)/(ratio-1)
            target_crown=target_chin-height
        transform=keys[0][head_index];rotation=study.poses.rotation(*(transform[:3]*(2*np.pi/4096)))
        fit_common=common;fit_normals=common_normals;pose_frame=None
        if 'sourceAnatomicalBasis' in target:
            # Authored native right/down/back axes, before the original bone pose.
            local_frame=np.asarray(target['sourceAnatomicalBasis'],dtype=float).T
            if local_frame.shape!=(3,3) or not np.allclose(local_frame.T@local_frame,np.eye(3),atol=1e-8) or not np.isclose(np.linalg.det(local_frame),1):
                raise ValueError('Invalid authored anatomical frame')
            pose_frame=rotation@local_frame
            fit_common=common@pose_frame.T;fit_normals=common_normals@pose_frame.T
        crown_point=np.asarray(landmarks.get('rawCrownPoint',[0,crown,0]),dtype=float)
        chin_point=np.asarray(landmarks.get('rawChinPoint',[0,chin,0]),dtype=float)
        if crown_point.shape!=(3,) or chin_point.shape!=(3,) or not np.isfinite([crown_point,chin_point]).all():
            raise ValueError('Invalid authored crown/chin points')
        posed_crown=crown_point if pose_frame is None else crown_point@pose_frame.T
        posed_chin=chin_point if pose_frame is None else chin_point@pose_frame.T
        vertical_span=posed_chin[1]-posed_crown[1]
        scale=(target_chin-target_crown)/vertical_span
        if not np.isfinite(scale) or scale<=0:raise ValueError('Invalid head scale')
        # Detect the framing anchor in the neutral anatomy, then pose it. Using
        # neutral Y bounds on a turned/pitched mesh selects a different surface.
        neutral_anchor=face_anchor(common,crown,chin)
        raw_anchor=neutral_anchor if pose_frame is None else neutral_anchor@pose_frame.T
        source_anchor=face_anchor(head,source_crown,target_chin)
        fit_offset=source_anchor-raw_anchor*scale
        fit_offset[1]=target_chin-posed_chin[1]*scale
        q=fit_common*scale+fit_offset
        custom_faces=np.asarray(mesh.faces);custom_uv=raw_uv;custom_normals=fit_normals;neck_report=None
        if 'neckAttachment' in target:
            raise ValueError('Rejected neck clipping controls: rebuild the candidate anatomy before fitting')
        points=((q-transform[3:])@rotation).astype(np.float32)
        ns=(custom_normals@rotation).astype(np.float32);ns/=np.linalg.norm(ns,axis=1)[:,None]
        table=struct.unpack_from('<I',data)[0]+12;offset,count=struct.unpack_from('<7I',data,table+head_index*28)[4:6];cursor=table+offset;headers=[]
        for _ in range(count):
            header=struct.unpack_from('<I',data,cursor)[0];headers.append(header);cursor+=4+((header>>8)&255)*4
        choices=[i for i,h in enumerate(headers) if (h>>24)&4 and not (h>>24)&3]
        if not choices:choices=[i for i,h in enumerate(headers) if not (h>>24)&1]
        if not choices:raise ValueError('No supported head material source')
        source_face=max(choices,key=lambda i:len(parts[head_index][1][i][0]))
        corners=len(parts[head_index][1][source_face][0]);weights=[1/corners]*corners
        old=current[head_index];signs=[]
        for face in old['faces']:
            a,b,c=np.asarray(old['vertices'])[face['vertices'][:3]]
            signs.append(np.dot(np.cross(b-a,c-a),np.asarray(old['normals'])[face['normals']].mean(0)))
        sign=np.sign(np.median(signs))
        faces=[]
        for refs in custom_faces:
            refs=refs.tolist();a,b,c=points[refs]
            if np.linalg.norm(np.cross(b-a,c-a))<=1e-9:raise ValueError('Collapsed custom triangle')
            if np.dot(np.cross(b-a,c-a),ns[refs].mean(0))*sign<0:refs.reverse()
            faces.append({'sourceFace':source_face,'vertices':refs,'normals':refs,'sourceWeights':[weights]*3})
        part={'vertices':points.tolist(),'normals':ns.tolist(),'faces':faces}
        pack={'version':1,'sourceGeometrySha256':part_ids[head_index],'parts':[part]}
        packed=output/'parts'/(part_ids[head_index]+'.json');packed.write_text(json.dumps(pack,separators=(',',':'),allow_nan=False))
        d=hashlib.sha256();put=lambda n:d.update(struct.pack('>I',n&0xffffffff));put(1);put(0)
        for values in [points,ns]:put(len(values));d.update(values.astype('>f4').tobytes())
        put(len(faces));new_mode=((headers[source_face]>>24)&~8)|16;new_header=new_mode<<24|(headers[source_face]&0x40000)
        for face in faces:
            put(new_header&0xff040000)
            for vertex in face['vertices']:put(vertex);put(vertex)
        binding={'version':1,'sourceGeometrySha256':part_ids[head_index],'geometrySha256':d.hexdigest(),'uvs':(custom_uv*[1,-1]+[0,1]).tolist(),'texture':'head-basecolour.png','textureSha256':texture_hash}
        (output/(form+'-head-uv.json')).write_text(json.dumps(binding,separators=(',',':'),allow_nan=False))
        current[head_index]=part
        for key in keys:
            unpacked=[study.unpack(p,original) for p,original in zip(current,parts)]
            if any(not np.isfinite(v).all() for v in study.poses.posed_vertices(unpacked,key,0)):raise ValueError('Nonfinite stored-pose sample')
        (output/(form+'-native-candidate.json')).write_text(json.dumps({'version':1,'sourceGeometrySha256':identity,'parts':current},separators=(',',':'),allow_nan=False))
        # Custom-only editable head in its posed native frame, no original actor geometry.
        exported=trimesh.Trimesh(q,custom_faces,vertex_normals=custom_normals,process=False)
        exported.visual=trimesh.visual.TextureVisuals(uv=custom_uv,material=trimesh.visual.material.PBRMaterial(baseColorTexture=texture,roughnessFactor=1,metallicFactor=0))
        exported.export(output/(form+'-fitted-head.glb'))
        head_height=target_chin-target_crown
        records.append({'form':form,'source':record['source'],'sourcePartIndex':head_index,'sourcePartGeometrySha256':part_ids[head_index],'geometrySha256':binding['geometrySha256'],'sourceMaterialFace':source_face,'packSha256':hashlib.sha256(packed.read_bytes()).hexdigest(),'triangles':len(faces),'vertices':len(points),'scale':scale,'offset':fit_offset.tolist(),'rawCrownY':crown,'rawChinY':chin,'sourceCrownY':source_crown,'sourceChinY':target_chin,'targetCrownY':target_crown,'posedBodyToSkullHeightRatio':float((np.concatenate(posed)[:,1].max()-target_crown)/head_height),'rawFrontAnchor':raw_anchor.tolist(),'sourceFrontAnchor':source_anchor.tolist(),'storedPoseSamples':len(keys),'fitStatus':'crown-chin-fit-needs-visual-and-native-review','otherPartsUnchanged':True,'neckAttachment':neck_report})
        records[-1].update(rawCrownPoint=crown_point.tolist(),rawChinPoint=chin_point.tolist(),
                           sourceAnatomicalBasis=target.get('sourceAnatomicalBasis'),
                           posedAnatomicalFrame=pose_frame.tolist() if pose_frame is not None else None,
                           fittedCrownPoint=(posed_crown*scale+fit_offset).tolist(),
                           fittedChinPoint=(posed_chin*scale+fit_offset).tolist())
    receipt={'format':1,'character':character,'state':'development-not-runtime-selected','meshInputSha256':hashlib.sha256(mesh_path.read_bytes()).hexdigest(),'landmarksSha256':hashlib.sha256(landmarks_path.read_bytes()).hexdigest(),'textureSha256':texture_hash,'forms':records,'scope':'Source-bound crown/chin head fits and finite stored poses. Proportion ratios describe this pose, not standing anatomy. Does not prove attachments, native animation or Steam Deck performance.'}
    (output/'fit-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');return receipt


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--files',type=Path,required=True);parser.add_argument('--character',choices=list(HEADS),required=True);parser.add_argument('--mesh',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--landmarks',type=Path);a=parser.parse_args();print(json.dumps(fit(a.files,a.character,a.mesh,a.output,a.landmarks)))
