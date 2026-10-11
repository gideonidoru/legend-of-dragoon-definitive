#!/usr/bin/env python3
"""Select reviewed static battle surfaces; hold animations, pilots and revision candidates native."""
import argparse,importlib.util,io,json,re
from pathlib import Path
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('battle_atlas_tools',ROOT/'scripts/pack-envhd-terrain.py');tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(tools)

spec=importlib.util.spec_from_file_location('battle_source_tools',ROOT/'scripts/batch-envhd-battle-materials.py');battle_sources=importlib.util.module_from_spec(spec);spec.loader.exec_module(battle_sources)

spec=importlib.util.spec_from_file_location('battle_painted_tools',ROOT/'scripts/verify-envhd-painted-battle.py');painted_tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(painted_tools)
spec=importlib.util.spec_from_file_location('battle_presentation_tools',ROOT/'scripts/verify-envhd-battle-presentation.py');presentation_tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(presentation_tools)

def validate_previous_selection(selection,root):
    """Fail closed on incomplete receipts before source discovery or runtime writes."""
    def invalid():
        raise ValueError('Existing battle selection is incomplete or inconsistent; refusing to overwrite it')
    def count(value):return type(value) is int and value>=0
    def sha(value):return isinstance(value,str) and re.fullmatch('[0-9a-f]{64}',value) is not None
    def vector(value,n):return isinstance(value,list) and len(value)==n and all(type(v) is int for v in value)
    if not isinstance(selection,dict) or type(selection.get('schema')) is not int or selection['schema']!=1:invalid()
    for key in ('runtimeSelectedMasters','runtimeSelectedBindings','nativeAccepted','finalQualityAccepted'):
        if not count(selection.get(key)):invalid()
    if selection['nativeAccepted'] or selection['finalQualityAccepted']:invalid()
    scenes=selection.get('scenes')
    if not isinstance(scenes,list) or not isinstance(selection.get('scope'),str) or not selection['scope']:invalid()
    models=set();stages=set();keys=set();bindings=0
    for scene in scenes:
        if not isinstance(scene,dict) or type(scene.get('stage')) is not int or scene['stage'] in stages:invalid()
        if not isinstance(scene.get('modelSha256'),str) or not scene['modelSha256']:invalid()
        if not isinstance(scene.get('timSha256'),str) or not scene['timSha256']:invalid()
        if scene.get('schema')!=1 or scene.get('pipeline')!='envhd-static-battle-atlas-1' or scene.get('scale')!=4 or not sha(scene.get('atlasSha256')):invalid()
        if not vector(scene.get('atlasSize'),2) or min(scene['atlasSize'])<=0:invalid()
        regions=scene.get('regions')
        if not isinstance(regions,list) or not regions or type(scene.get('hdBindings')) is not int or scene['hdBindings']!=len(regions):invalid()
        region_ids=set()
        for region in regions:
            if not isinstance(region,dict) or not sha(region.get('decodedRgbaSha256')):invalid()
            if any(type(region.get(k)) is not int for k in ('page','clut')):invalid()
            for k,n in (('sourceCrop',4),('sourceUvOrigin',2),('sourceSize',2),('atlasRect',4)):
                if not vector(region.get(k),n):invalid()
            w,h=region['sourceSize'];x,y,rw,rh=region['atlasRect'];aw,ah=scene['atlasSize']
            if w<=0 or h<=0 or (rw,rh)!=(w*4,h*4) or x<32 or y<32 or x+rw+32>aw or y+rh+32>ah:invalid()
            identity=(region['page'],region['clut'],tuple(region['sourceCrop']))
            if identity in region_ids:invalid()
            region_ids.add(identity);keys.add(region['decodedRgbaSha256'])
        models.add(scene['modelSha256']);stages.add(scene['stage']);bindings+=len(regions)
        folder=root/'integrations/envhd/runtime-assets/envhd/battle-materials'/scene['modelSha256']
        manifest=json.loads(battle_sources.terrain.bounded_read(folder/'manifest.json',32*1024*1024))
        if manifest!={k:v for k,v in scene.items() if k not in ('stage','hdBindings')}:invalid()
        if battle_sources.terrain.digest(battle_sources.terrain.bounded_read(folder/'atlas-v1.png',32*1024*1024))!=scene['atlasSha256']:invalid()
    if selection['runtimeSelectedMasters']!=len(keys) or selection['runtimeSelectedBindings']!=bindings:invalid()
    painted_keys=set()
    painting_fields=('paintedSelectedMasters','paintedReviewReceipts')
    if not all(k in selection for k in painting_fields):invalid()
    if any(k in selection for k in painting_fields):
        if not all(k in selection for k in painting_fields) or not count(selection['paintedSelectedMasters']):invalid()
        receipts=selection['paintedReviewReceipts']
        if not isinstance(receipts,dict) or bool(receipts)!=bool(selection['paintedSelectedMasters']):invalid()
        painted_keys=set();paths=set()
        for digest,relative in receipts.items():
            if not sha(digest) or not isinstance(relative,str) or relative in paths:invalid()
            path=battle_sources.staging_path(root,relative)
            data=battle_sources.terrain.bounded_read(path,32*1024*1024)
            if battle_sources.terrain.digest(data)!=digest:invalid()
            receipt=json.loads(data);candidates=receipt.get('candidates') if isinstance(receipt,dict) else None
            if not isinstance(candidates,list) or not candidates:invalid()
            for candidate in candidates:
                key=candidate.get('decodedRgbaSha256') if isinstance(candidate,dict) else None
                if not sha(key) or key not in keys or key in painted_keys:invalid()
                painted_keys.add(key)
            paths.add(relative)
        if len(painted_keys)!=selection['paintedSelectedMasters']:invalid()
    presentation_fields=('presentationSelectedMasters','presentationSourceKeys','presentationReviewReceipt')
    if any(k in selection for k in presentation_fields):
        if not all(k in selection for k in presentation_fields):invalid()
        sources=selection['presentationSourceKeys']
        if not isinstance(sources,list) or not sources or any(not sha(k) for k in sources) or len(set(sources))!=len(sources):invalid()
        if type(selection['presentationSelectedMasters']) is not int or selection['presentationSelectedMasters']!=len(sources) or not set(sources).issubset(keys) or not sha(selection['presentationReviewReceipt']):invalid()
        if set(sources)&painted_keys:invalid()

def publish(files,painted_manifest=None,presentation_manifest=None):
    battle=battle_sources
    production=ROOT/'integrations/envhd/production'
    painted=None;manifest_checks=[];presentation=None;previous_selection=None
    selection_path=production/'battle-material-runtime-artwork.json'
    if selection_path.exists():
        selection_data=battle.terrain.bounded_read(selection_path,32*1024*1024)
        previous_selection=json.loads(selection_data)
        validate_previous_selection(previous_selection,ROOT)
        manifest_checks.append((selection_path,battle.terrain.digest(selection_data)))
        if previous_selection.get('presentationSourceKeys') and presentation_manifest is None:
            raise ValueError('Existing bespoke battle presentation requires --presentation-manifest; refusing to restore neural artwork')
        if painted_manifest is None:
            painted_manifest=[ROOT/p for p in previous_selection.get('paintedReviewReceipts',{}).values()] or None
    if presentation_manifest is not None and previous_selection is None:
        raise ValueError('Existing battle selection required for presentation repairs')
    scenes,masters,empty=battle.census(files)
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
    selection=dict(schema=1,paintedSelectedMasters=0,paintedReviewReceipts={},runtimeSelectedMasters=len(keys),runtimeSelectedBindings=sum(s['hdBindings'] for s in inventory),nativeAccepted=0,finalQualityAccepted=0,scenes=inventory,scope='Reviewed static development surfaces only; pilots, animation and revision holds retain their original sampling')
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
