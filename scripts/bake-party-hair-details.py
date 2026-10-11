#!/usr/bin/env python3
"""Custom strand paint for existing animated Rose/Meru hair. AGPL v3.

Reads owner-provided originals locally. Emits source-bound custom parts and paint
only; no original RGB textures, actor controls or normal runtime selection.
Geometry, source weights and native animation pivots remain unchanged. The
fringe preserves its source cutout silhouette with an explicit cutout material.
This is a development authoring tool, not an artistic acceptance gate.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import struct
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PARTY=ROOT/'integrations/charhd/production/party-reconstruction'
PARTS={'rose':{'field':[(5,'88291b4ae4c9')], 'combat':[(7,'c59ab637c60b')]},
       'meru':{'field':[(7,'4ccd681d8636'),(8,'54e8e199fe99'),(9,'0b10d48f058c')],
               'combat':[(9,'add511cd99eb'),(10,'0b9ac950e8d2'),(11,'1533d6d04a71')]}}
PALETTES={'rose':[[18,23,36],[42,51,76],[74,83,114]],
          'meru':[[65,103,152],[143,184,222],[207,229,244]]}

def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def paint(name,size=1024):
    """Authored RGB strand material; no original texture pixels are copied."""
    y,x=np.mgrid[0:size,0:size]/size
    # Two identical horizontal periods allow each face to unwrap a cylindrical
    # seam without stretching across the atlas. Gentle meanders retain strands.
    x=(x*2)%1
    phase=x+.004*np.sin(y*9+x*12)+.003*np.sin(y*19-x*7)
    bundle=.5+.5*np.sin(phase*2*np.pi*24)
    fine=.5+.5*np.sin(phase*2*np.pi*153+np.sin(x*211))
    detail=.5+.5*np.sin(phase*2*np.pi*389+y*3)
    intensity=np.clip(.12+.42*bundle+.25*fine+.10*detail,0,1)
    dark,mid,light=np.asarray(PALETTES[name],float)
    rgb=np.where((intensity<.55)[...,None],dark+(mid-dark)*(intensity/.55)[...,None],
                 mid+(light-mid)*((intensity-.55)/.45)[...,None])
    rgb*= (.94+.06*np.cos((y-.35)*2.6))[...,None]
    return np.clip(rgb,0,255).astype('uint8')

def headers(data,index):
    table=struct.unpack_from('<I',data)[0]+12
    offset,count=struct.unpack_from('<7I',data,table+index*28)[4:6]
    cursor=table+offset;result=[]
    for _ in range(count):
        value=struct.unpack_from('<I',data,cursor)[0];result.append(value)
        cursor+=4+((value>>8)&255)*4
    return result

def tim_path(name,form,record):
    if form=='combat':return Path('characters')/name/'textures/combat'
    catalog=json.loads((ROOT/'docs/definitive/model-catalog/model-catalog.json').read_text())
    match=next(a for m in catalog['models'] for a in m['appearances'] if a['source']==record['source'])
    routes={row['texture'] for row in match['mappings'] if 'texture' in row}
    if len(routes)!=1:raise ValueError('Ambiguous field material')
    return Path(next(iter(routes)))

def build(files,heads,output,strand_paints=None):
    if output.exists():raise ValueError('Use a new output')
    study=module('hair_study',ROOT/'experiments/dart-modelshd-charhd/build-study.py')
    manifest_builder=module('hair_binding',ROOT/'scripts/build-party-candidate-manifest.py')
    audit=json.loads((PARTY/'source-audit.json').read_text())
    output.mkdir(parents=True);rows=[];receipts=[]
    for name,forms in PARTS.items():
        directory=output/name;directory.mkdir();(directory/'parts').mkdir()
        if strand_paints:
            source_paint=strand_paints/(name+'-strands-v1.png')
            with Image.open(source_paint) as image:
                if image.width<1024 or image.height<1024:raise ValueError('Strand artwork must be at least 1K')
                # Technical atlas packing: two periods, no repainting of artwork.
                period=np.asarray(image.convert('RGB').resize((512,1024),Image.Resampling.LANCZOS))
            strand=np.concatenate((period,period),axis=1)
        else:strand=paint(name)
        Image.fromarray(strand).save(directory/'hair-strands.png')
        actor=next(a for a in audit['characters'] if a['character']==name)
        fits=json.loads((heads/name/'fit-receipt.json').read_text())
        for form,indices in forms.items():
            source=next(a for a in actor['models'] if a['form']==form)
            data=(files/source['source']).read_bytes()
            if hashlib.sha256(data).hexdigest()!=source['sourceSha256']:raise ValueError('Source drift')
            original,_,_,ids,baseline=study.baseline(data)
            animation=(files/source['source']).with_name(str(int(Path(source['source']).name)+1) if form=='field' else '0').read_bytes()
            if hashlib.sha256(animation).hexdigest()!=source['animationSha256']:raise ValueError('Animation drift')
            keys=study.poses.read_keyframes(animation,len(original))
            unpacked=[study.unpack(part,old) for part,old in zip(baseline,original)]
            posed=study.poses.posed_vertices(unpacked,keys[0],0)
            fit=next(a for a in fits['forms'] if a['form']==form)
            frame=np.asarray(fit.get('posedAnatomicalFrame') or np.eye(3))
            for index,prefix in indices:
                if not ids[index].startswith(prefix):raise ValueError('Hair part identity changed')
                part=baseline[index];hs=headers(data,index);old_positions=np.asarray(part['vertices']);old_normals=np.asarray(part['normals'])
                common=((posed[index]-fit['offset'])/fit['scale'])@frame
                new={'vertices':[],'normals':[],'faces':[]};coordinates=[]
                textured=form=='combat' and name=='rose'
                texture_name='combat-fringe.png' if textured else 'hair-strands.png'
                alpha='cutout' if textured else 'opaque'
                if textured:
                    w,h,cw,ch,colors,pixels=study.poses.materials.texture((files/tim_path(name,form,source)).read_bytes())
                    packed=np.frombuffer(pixels[:w*h//2],dtype=np.uint8)
                    index_map=np.column_stack((packed&15,packed>>4)).reshape(h,w)
                    palette=np.asarray(colors,dtype=np.uint16)
                    atlas=np.dstack((strand,np.zeros((1024,1024),np.uint8)))
                    source_faces=sorted({face['sourceFace'] for face in part['faces']})
                    slots={s:i for i,s in enumerate(source_faces)}
                    for s,slot in slots.items():
                        _,uvs,clut,_=original[index][1][s]
                        if uvs is None:raise ValueError('Expected source fringe UV')
                        lo,hi=np.min(uvs,axis=0),np.max(uvs,axis=0);extent=np.maximum(hi-lo,1)
                        tx=(slot%3)*320+8;ty=(slot//3)*320+8
                        yy,xx=np.mgrid[0:304,0:304]
                        ux=np.clip(np.floor(lo[0]+xx/303*extent[0]).astype(int),0,w-1)
                        vy=np.clip(np.floor(lo[1]+yy/303*extent[1]).astype(int),0,h-1)
                        ci=((clut>>6)&15)*(cw//16)+(clut&3)
                        values=palette[ci*16+index_map[vy,ux]]
                        atlas[ty:ty+304,tx:tx+304,3]=np.where(values!=0,255,0)
                    Image.fromarray(atlas).save(directory/texture_name)
                for face in part['faces']:
                    refs=face['vertices'];start=len(new['vertices']);points=old_positions[refs]
                    if textured:
                        s=face['sourceFace'];_,source_uv,_,_=original[index][1][s]
                        uv=np.floor(np.asarray(face['sourceWeights'])@source_uv+.5)
                        lo,hi=np.min(source_uv,axis=0),np.max(source_uv,axis=0)
                        slot=slots[s];corner=(uv-lo)/np.maximum(hi-lo,1)*303
                        corner+=(slot%3*320+8.5,slot//3*320+8.5)
                        uv=corner/1024
                    else:
                        q=common[refs];theta=(np.arctan2(q[:,0],q[:,2])/(2*np.pi))%1
                        if np.ptp(theta)>.5:theta=np.where(theta<.5,theta+1,theta)
                        uv=np.column_stack((theta*.5,np.clip((q[:,1]+.35)/1.75,0,1)))
                    new['vertices'].extend(points.tolist());new['normals'].extend(old_normals[face['normals']].tolist())
                    new_refs=list(range(start,start+len(refs)))
                    new['faces'].append({**copy.deepcopy(face),'vertices':new_refs,'normals':new_refs})
                    coordinates.extend(uv.tolist())
                    assert np.array_equal(np.asarray(new['vertices'])[new_refs],points)
                    assert np.array_equal(np.asarray(new['normals'])[new_refs],old_normals[face['normals']])
                geometry=manifest_builder.geometry_hash(new,hs);baseline_hash=manifest_builder.geometry_hash(part,hs)
                pack=directory/'parts'/(ids[index]+'.json');pack.write_text(json.dumps({'version':1,'sourceGeometrySha256':ids[index],'parts':[new]},separators=(',',':'),allow_nan=False))
                texture=directory/texture_name;uv_path=directory/(form+'-hair-'+str(index)+'-uv.json')
                binding={'version':1,'sourceGeometrySha256':ids[index],'geometrySha256':geometry,'uvs':coordinates,'texture':texture_name,'textureSha256':sha(texture),'alphaMode':alpha}
                uv_path.write_text(json.dumps(binding,separators=(',',':'),allow_nan=False))
                rows.append({'character':name,'form':form,'role':'auxiliary-hair','sourcePartIndex':index,'sourceGeometrySha256':ids[index],'geometrySha256':geometry,'baselineGeometrySha256':baseline_hash,
                             'pack':str(pack.relative_to(output)),'packSha256':sha(pack),'uv':str(uv_path.relative_to(output)),'uvSha256':sha(uv_path),'texture':str(texture.relative_to(output)),'textureSha256':sha(texture),'width':1024,'height':1024,'alphaMode':alpha})
                receipts.append({'character':name,'form':form,'sourcePartIndex':index,'sourceSha256':source['sourceSha256'],'animationSha256':source['animationSha256'],'sourcePartGeometrySha256':ids[index],'triangles':len(new['faces']),
                                 'positionsAndNormalsUnchanged':True,'sourceWeightsUnchanged':True,'nativePivotUnchanged':True,'originalRgbPixelsCopied':0,'method':'Custom strand material on existing animated geometry; duplicate only UV seam corners'})
    (output/'candidate-manifest.json').write_text(json.dumps({'format':1,'state':'development-not-runtime-selected','parts':rows},indent=2)+'\n')
    paint_inputs={name:sha(strand_paints/(name+'-strands-v1.png')) for name in PARTS} if strand_paints else {}
    (output/'authoring-receipt.json').write_text(json.dumps({'format':1,'state':'experimental-not-accepted','bakerSha256':sha(Path(__file__)),'palettes':PALETTES if not strand_paints else {},'customPaintInputs':paint_inputs,'parts':receipts,'nativeGameplay':False,'steamDeck':False},indent=2)+'\n')
    return len(rows)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files',type=Path,required=True);parser.add_argument('--heads',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--strand-paints',type=Path,help='Explicit custom Rose/Meru artwork directory; otherwise procedural diagnostic material')
    args=parser.parse_args();print('Authored',build(args.files,args.heads,args.output,args.strand_paints),'animated hair details')
