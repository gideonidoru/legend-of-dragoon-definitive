#!/usr/bin/env python3
"""Verify source-bound custom animated hair and explicit alpha previews. AGPL v3.

Reads original inputs privately. Reports checksums/counts, never source pixels.
Construction and offline silhouette checks do not prove gameplay or artistic fit.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import numpy as np
from PIL import Image

from importlib.util import spec_from_file_location,module_from_spec
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
    spec=spec_from_file_location(name,path);value=module_from_spec(spec);spec.loader.exec_module(value);return value

def verify(files,hair,report):
    baker=module('hair_baker',ROOT/'scripts/bake-party-hair-details.py')
    staging=module('hair_stage',ROOT/'scripts/stage-party-head-preview.py')
    hashes=module('hair_hash',ROOT/'scripts/build-party-candidate-manifest.py')
    renderer=module('hair_renderer',ROOT/'scripts/render-party-candidates.py');study=renderer.study
    rows=json.loads((hair/'candidate-manifest.json').read_text())['parts']
    expected={(name,form,index) for name,forms in baker.PARTS.items() for form,indices in forms.items() for index,_ in indices}
    assert len(rows)==8 and {(r['character'],r['form'],r['sourcePartIndex']) for r in rows}==expected
    assert len({r['sourceGeometrySha256'] for r in rows})==8
    audit=json.loads((baker.PARTY/'source-audit.json').read_text());checks=[]
    for row in rows:
        name,form,index=row['character'],row['form'],row['sourcePartIndex']
        actor=next(a for a in audit['characters'] if a['character']==name)
        source=next(m for m in actor['models'] if m['form']==form)
        data=(files/source['source']).read_bytes()
        assert hashlib.sha256(data).hexdigest()==source['sourceSha256']
        originals,_,_,ids,baseline=study.baseline(data)
        assert ids[index]==row['sourceGeometrySha256']
        part=json.loads(staging.checked(hair,row,'pack'))['parts'][0]
        binding=json.loads(staging.checked(hair,row,'uv'))
        texture=Image.open(io.BytesIO(staging.checked(hair,row,'texture'))).convert('RGBA')
        assert texture.size==(1024,1024)==(row['width'],row['height'])
        assert binding['sourceGeometrySha256']==ids[index] and binding['textureSha256']==row['textureSha256']
        assert row['geometrySha256']==binding['geometrySha256']==hashes.geometry_hash(part,baker.headers(data,index))
        assert row['baselineGeometrySha256']==hashes.geometry_hash(baseline[index],baker.headers(data,index))
        points=np.asarray(part['vertices']);normals=np.asarray(part['normals']);uv=np.asarray(binding['uvs'])
        assert points.shape==normals.shape and uv.shape==(len(points),2)
        assert np.isfinite(points).all() and np.isfinite(normals).all() and np.isfinite(uv).all()
        assert uv.min()>=0 and uv.max()<=1
        assert len(part['faces'])==len(baseline[index]['faces'])
        for face,prior in zip(part['faces'],baseline[index]['faces']):
            assert face['sourceFace']==prior['sourceFace'] and face['sourceWeights']==prior['sourceWeights']
            assert np.array_equal(points[face['vertices']],np.asarray(baseline[index]['vertices'])[prior['vertices']])
            assert np.array_equal(normals[face['normals']],np.asarray(baseline[index]['normals'])[prior['normals']])
        record={'character':name,'form':form,'sourcePartIndex':index,'positionsNormalsWeightsAndPivotPreserved':True,'geometryUvTextureHashesVerified':True}
        if name=='rose' and form=='combat':
            assert row['alphaMode']==binding['alphaMode']=='cutout'
            alpha=np.asarray(texture)[...,3];assert np.any(alpha==0) and np.any(alpha==255) and np.all((alpha==0)|(alpha==255))
            model=(files/source['source']);animation=model.with_name('0').read_bytes()
            assert hashlib.sha256(animation).hexdigest()==source['animationSha256']
            key=study.poses.read_keyframes(animation,len(originals))[0][index]
            rotation=study.poses.rotation(*(key[:3]*(2*np.pi/4096)))
            old=study.unpack(baseline[index],originals[index]);old_pos=old[0]@rotation.T+key[3:]
            new=(points,[(face['vertices'],uv[face['vertices']],0,np.full((len(face['vertices']),3),127.5)) for face in part['faces']]);new_pos=points@rotation.T+key[3:]
            bounds=(old_pos[:,0].min(),old_pos[:,0].max(),old_pos[:,1].min(),old_pos[:,1].max())
            tim=(files/baker.tim_path(name,form,source)).read_bytes()
            before=renderer.render([old],[old_pos],tim,None,bounds,size=(512,512))
            after=renderer.render([new],[new_pos],tim,None,bounds,size=(512,512),detail_parts={0:texture},detail_cutouts={0})
            a=np.asarray(before)[...,3]>0;b=np.asarray(after)[...,3]>0
            overlap=float((a&b).sum()/(a|b).sum())
            assert overlap>=.98,'Fringe cutout silhouette diverged: '+str(overlap)
            transparent=Image.new('RGBA',texture.size,(0,0,0,0))
            cut=renderer.render([new],[new_pos],tim,None,bounds,size=(512,512),detail_parts={0:transparent},detail_cutouts={0})
            opaque=renderer.render([new],[new_pos],tim,None,bounds,size=(512,512),detail_parts={0:transparent})
            black=renderer.render([new],[new_pos],tim,None,bounds,size=(512,512),detail_parts={0:Image.new('RGBA',texture.size,(0,0,0,255))},detail_cutouts={0})
            assert np.asarray(cut)[...,3].sum()==0
            assert np.asarray(opaque)[...,3].sum()>0 and np.array_equal(np.asarray(opaque)[...,3],np.asarray(black)[...,3])
            record.update(fringeSourceSilhouetteJaccard=overlap,transparentCutoutEmpty=True,opaqueAlphaIgnored=True,visibleBlackPreserved=True)
        else:assert row['alphaMode']==binding['alphaMode']=='opaque'
        checks.append(record)
    result={'status':'passed','scope':'Exact custom part binding and offline silhouette checks only','parts':checks,'nativeGameplay':False,'steamDeck':False}
    report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(result,indent=2)+'\n')
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('files','hair','report'):parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args();result=verify(args.files,args.hair,args.report)
    print(json.dumps({'status':result['status'],'partsVerified':len(result['parts']),'fringeSilhouetteJaccard':next(r['fringeSourceSilhouetteJaccard'] for r in result['parts'] if 'fringeSourceSilhouetteJaccard' in r),'nativeGameplay':False,'steamDeck':False}))
