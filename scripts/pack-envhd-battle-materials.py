#!/usr/bin/env python3
"""Select reviewed static battle surfaces; hold animations, pilots and revision candidates native."""
import argparse,importlib.util,io,json
from pathlib import Path
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('battle_atlas_tools',ROOT/'scripts/pack-envhd-terrain.py');tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(tools)

spec=importlib.util.spec_from_file_location('battle_source_tools',ROOT/'scripts/batch-envhd-battle-materials.py');battle_sources=importlib.util.module_from_spec(spec);spec.loader.exec_module(battle_sources)

spec=importlib.util.spec_from_file_location('battle_painted_tools',ROOT/'scripts/verify-envhd-painted-battle.py');painted_tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(painted_tools)

def publish(files,painted_manifest=None):
    battle=battle_sources
    scenes,masters,empty=battle.census(files);production=ROOT/'integrations/envhd/production'
    ledger=json.loads((production/'battle-material-artwork.json').read_text())
    if ledger['scenes']!=scenes or ledger['emptyModelSlots']!=empty:raise ValueError('Fresh battle source differs')
    records={a['decodedRgbaSha256']:a for a in ledger['assets']};images={}
    if len(records)!=len(ledger['assets']) or records.keys()!=masters.keys():raise ValueError('Complete source census required')
    for key,a in records.items():
        folder=production/'battle-material-candidates'/key
        if json.loads((folder/'manifest-v1.json').read_text())!=a:raise ValueError('Versioned candidate differs')
        png=tools.terrain.bounded_read(folder/'image-v1.png',32*1024*1024)
        expected=battle.terrain.candidate_record(masters[key],tools.terrain.digest(png))
        if any(a.get(k)!=v for k,v in expected.items() if k!='reviewStatus'):raise ValueError('Candidate source metadata differs')
        battle.terrain.validate_completed_bytes(masters[key],expected,png)
        note=a['review']
        if note['outputSha256']!=tools.terrain.digest(png) or note['verdict']!=a['reviewStatus'] or note['nativeAcceptance']!='pending' or note['finalQualityAcceptance']!='pending':raise ValueError('Review differs')
        if a['reviewStatus']=='visual-reviewed-runtime-pending':images[key]=np.array(Image.open(io.BytesIO(png)))
        elif a['reviewStatus']!='intent-revision-needed':raise ValueError('Unknown review')
    painted=painted_tools.preflight(files,painted_manifest) if painted_manifest is not None else None
    if painted is not None:
        for item in painted['verified']:
            key=item['candidate']['decodedRgbaSha256']
            if key in images or key not in masters:raise ValueError('Painted battle cannot replace an already selected/protected source')
            images[key]=item['image']
    writes={};inventory=[];keys=set()
    for scene in scenes:
        bindings=[m for m in scene['materials'] if m['status']=='candidate-pending' and m['decodedRgbaSha256'] in images]
        if not bindings:continue
        selected={m['decodedRgbaSha256']:tuple(m['sourceSize']) for m in bindings};size,positions=tools.layout(selected);atlas=np.zeros((size[1],size[0],4),dtype=np.uint8)
        for key,(x,y,w,h) in positions.items():atlas[y-32:y+h+32,x-32:x+w+32]=np.pad(images[key],((32,32),(32,32),(0,0)),mode='edge')
        buffer=io.BytesIO();Image.fromarray(atlas).save(buffer,format='PNG');png=buffer.getvalue()
        regions=[{k:m[k] for k in ('page','clut','sourceCrop','sourceUvOrigin','sourceSize','decodedRgbaSha256')}|dict(atlasRect=positions[m['decodedRgbaSha256']]) for m in bindings]
        manifest=dict(schema=1,pipeline='envhd-static-battle-atlas-1',modelSha256=scene['modelSha256'],timSha256=scene['timSha256'],scale=4,atlasSize=size,atlasSha256=tools.terrain.digest(png),regions=regions)
        folder=ROOT/'integrations/envhd/runtime-assets/envhd/battle-materials'/scene['modelSha256'];writes[folder/'atlas-v1.png']=png;writes[folder/'manifest.json']=tools.publication.json_bytes(manifest)
        inventory.append(dict(stage=scene['stage'],hdBindings=len(regions),**manifest));keys.update(selected)
    selection=dict(schema=1,runtimeSelectedMasters=len(keys),runtimeSelectedBindings=sum(s['hdBindings'] for s in inventory),nativeAccepted=0,finalQualityAccepted=0,scenes=inventory,scope='Reviewed static development surfaces only; pilots, animation and revision holds retain their original sampling')
    if painted is not None:
        selection.update(paintedSelectedMasters=len(painted['verified']),paintedReviewReceiptSha256=painted['manifestSha256'],scope='Reviewed neural static surfaces plus separately source-verified painted repairs; pilots, animation and remaining revision holds retain original sampling')
    writes[production/'battle-material-runtime-artwork.json']=tools.publication.json_bytes(selection)
    runtime=ROOT/'integrations/envhd/runtime-assets/envhd/battle-materials'
    removals={p for p in runtime.rglob('*') if p.is_file() and p not in writes}
    tools.publication.publish(writes,removals);return selection

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--files',required=True,type=Path);parser.add_argument('--painted-manifest',type=Path);args=parser.parse_args();print('Selected battle material masters:',publish(args.files,args.painted_manifest)['runtimeSelectedMasters'])
