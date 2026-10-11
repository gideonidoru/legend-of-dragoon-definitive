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
spec=importlib.util.spec_from_file_location('battle_presentation_tools',ROOT/'scripts/verify-envhd-battle-presentation.py');presentation_tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(presentation_tools)

def publish(files,painted_manifest=None,presentation_manifest=None):
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
    painted=None;manifest_checks=[];presentation=None;previous_selection=None
    if presentation_manifest is not None:
        selection_path=production/'battle-material-runtime-artwork.json'
        selection_data=battle.terrain.bounded_read(selection_path,32*1024*1024)
        previous_selection=json.loads(selection_data)
        manifest_checks.append((selection_path,battle.terrain.digest(selection_data)))
        if painted_manifest is None:
            painted_manifest=[ROOT/p for p in previous_selection.get('paintedReviewReceipts',{}).values()] or None
    if painted_manifest is not None:
        paths=list(painted_manifest) if isinstance(painted_manifest,(list,tuple)) else [painted_manifest]
        if not paths:raise ValueError('Explicit painted battle manifest required')
        receipts={};verified=[]
        for path in paths:
            path=battle.staging_path(ROOT,str(Path(path).absolute().relative_to(ROOT)))
            relative=str(path.relative_to(ROOT))
            result=painted_tools.preflight(files,path,ROOT)
            if result['manifestSha256'] in receipts:raise ValueError('Duplicate painted battle manifest')
            receipts[result['manifestSha256']]=relative
            manifest_checks.append((path,result['manifestSha256']))
            verified.extend(result['verified'])
        painted=dict(verified=verified,receipts=receipts)
        verify_manifest_identities(manifest_checks)
    if painted is not None:
        for item in painted['verified']:
            key=item['candidate']['decodedRgbaSha256']
            if key in images or key not in masters:raise ValueError('Painted battle cannot replace an already selected/protected source')
            images[key]=item['image']
    if presentation_manifest is not None:
        path=battle.staging_path(ROOT,str(Path(presentation_manifest).absolute().relative_to(ROOT)))
        presentation=presentation_tools.preflight(files,path,ROOT)
        manifest_checks.append((path,presentation['manifestSha256']))
        manifest_checks.append((production/'battle-material-artwork.json',presentation['sourceArtworkLedgerSha256']))
        for item in presentation['verified']:
            key=item['candidate']['decodedRgbaSha256']
            if key not in images or records[key]['reviewStatus']!='visual-reviewed-runtime-pending':
                raise ValueError('Presentation repair must replace an already reviewed static neural material')
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
        selection.update(paintedSelectedMasters=len(painted['verified']),paintedReviewReceipts=painted['receipts'],scope='Reviewed neural static surfaces plus separately source-verified painted repairs; pilots, animation and remaining revision holds retain original sampling')
    if presentation is not None:
        presentation_keys=sorted(item['candidate']['decodedRgbaSha256'] for item in presentation['verified'])
        if not set(previous_selection.get('presentationSourceKeys',[])).issubset(presentation_keys):
            raise ValueError('Battle presentation dropped an earlier bespoke repair')
        before={s['modelSha256']:s for s in previous_selection['scenes']}
        after={s['modelSha256']:s for s in inventory}
        if before.keys()!=after.keys() or any(
            any(old.get(k)!=after[key].get(k) for k in ('stage','hdBindings','modelSha256','timSha256','scale','atlasSize','regions'))
            for key,old in before.items()):
            raise ValueError('Battle presentation changed geometry, mapping, coverage or atlas allocation')
        if any(previous_selection[k]!=selection[k] for k in ('runtimeSelectedMasters','runtimeSelectedBindings')):
            raise ValueError('Battle presentation dropped selected coverage')
        selection.update(presentationSelectedMasters=len(presentation['verified']),presentationSourceKeys=presentation_keys,presentationReviewReceipt=presentation['manifestSha256'],
                         scope='Existing static coverage plus source-verified bespoke presentation; atlas footprints, pilots and all dynamic holds preserved')
    writes[production/'battle-material-runtime-artwork.json']=tools.publication.json_bytes(selection)
    runtime=ROOT/'integrations/envhd/runtime-assets/envhd/battle-materials'
    removals={p for p in runtime.rglob('*') if p.is_file() and p not in writes}
    verify_manifest_identities(manifest_checks)
    tools.publication.publish(writes,removals);return selection

def verify_manifest_identities(checks):
    for path,expected in checks:
        if tools.terrain.digest(tools.terrain.bounded_read(path,32*1024*1024))!=expected:
            raise ValueError('Painted battle manifest changed after preflight')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--files',required=True,type=Path);parser.add_argument('--painted-manifest',type=Path,action='append');parser.add_argument('--presentation-manifest',type=Path);args=parser.parse_args();print('Selected battle material masters:',publish(args.files,args.painted_manifest,args.presentation_manifest)['runtimeSelectedMasters'])
