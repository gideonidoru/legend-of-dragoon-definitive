#!/usr/bin/env python3
"""Repair colored field-placeholder edges without inventing hidden scenery.

The categorical source-colored regions and their one-source-pixel perimeter are
copied exactly at 4x. Other neural detail is retained. Foregrounds remain separate.
This creates private candidates for review, never runtime selections.
"""
import argparse
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image


def module(name,filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result)
    return result


batch=module('field_edge_batch','batch-envhd-fields.py')
terrain,battle=batch.terrain,batch.battle
COLORS=((0,248,0),(0,248,248),(248,248,0),(248,0,248))


def guard(item):
    source=np.asarray(item['image'])
    mask=np.zeros(source.shape[:2],dtype=bool)
    if any(b['kind']=='background' for b in item['bindings']):
        for color in COLORS:
            selected=np.all(source[:,:,:3]==color,axis=2)&(source[:,:,3]!=0)
            if np.count_nonzero(selected)>=16:
                mask|=selected
    padded=np.pad(mask,1)
    perimeter=np.zeros_like(mask)
    for y in range(3):
        for x in range(3):
            perimeter|=padded[y:y+mask.shape[0],x:x+mask.shape[1]]
    return perimeter


def repaired_pixels(item,input_data):
    with Image.open(io.BytesIO(input_data)) as image:
        pixels=np.asarray(image).copy()
    source=np.asarray(item['image'])
    if pixels.shape!=(source.shape[0]*4,source.shape[1]*4,4):
        raise ValueError('Field repair input dimensions or channels changed')
    selected=guard(item)
    if np.any(selected):
        enlarged=np.repeat(np.repeat(selected,4,axis=0),4,axis=1)
        native=np.repeat(np.repeat(source,4,axis=0),4,axis=1)
        pixels[enlarged]=native[enlarged]
    return pixels


def record(item,input_record,output_hash):
    return dict(schema=1,decodedRgbaSha256=item['decodedRgbaSha256'],outputSha256=output_hash,
                method='python-field-source-color-perimeter-repair',algorithmSha256=terrain.digest(Path(__file__).read_bytes()),
                sourceSize=item['sourceSize'],targetSize=input_record['targetSize'],scale=4,
                guardedNativePixels=int(np.count_nonzero(guard(item))),guardColors=[list(c) for c in COLORS],
                minimumColorPixels=16,sourcePerimeterPixels=1,edgeAveraging=False,
                inputCandidate=input_record,reviewStatus='pending-source-intent-style-layout-review')


def validate(item,input_record,input_data,repair_record,output_data):
    batch.validate(item,input_record,input_data)
    if repair_record!=record(item,input_record,terrain.digest(output_data)):
        raise ValueError('Field repair provenance changed')
    if len(output_data)>32*1024*1024 or len(output_data)<33 or output_data[:8]!=b'\x89PNG\r\n\x1a\n' or output_data[24:26]!=bytes((8,6)):
        raise ValueError('Field repair requires bounded 8-bit RGBA PNG')
    with Image.open(io.BytesIO(output_data)) as image:
        if image.mode!='RGBA' or list(image.size)!=input_record['targetSize'] or not np.array_equal(np.asarray(image),repaired_pixels(item,input_data)):
            raise ValueError('Field repair differs from exact source-color perimeter operation')


def execute(files,staging,legacy_staging,output):
    publication=module('field_edge_publication','import-envhd-fields.py')
    for path in (staging,legacy_staging,output):
        battle.private_destination(files,path)
        if path.is_symlink() or path.resolve().is_relative_to(publication.ROOT):
            raise ValueError('Field repair staging must remain private and unredirected')
    for source_root in (staging.resolve(),legacy_staging.resolve()):
        if output.resolve().is_relative_to(source_root) or source_root.is_relative_to(output.resolve()):
            raise ValueError('Field repair output must be separate from both input batches')
    report=batch.fields.census(files);masters,uniform=batch.sources(files,report)
    masters.sort(key=lambda m:(not any(b['kind']=='background' for b in m['bindings']),m['decodedRgbaSha256']))
    plan_data=terrain.bounded_read(battle.staging_path(staging,'source-plan.json'),32*1024*1024)
    plan=json.loads(plan_data)
    legacy_data=terrain.bounded_read(battle.staging_path(legacy_staging,'source-plan.json'),32*1024*1024)
    predecessor=publication.verify_plans(plan,report,masters,uniform,legacy_data,batch.read_control(battle.staging_path(legacy_staging,'candidates.json')))
    inputs_data=terrain.bounded_read(battle.staging_path(staging,'candidates.json'),32*1024*1024)
    inputs=json.loads(inputs_data)
    by_key={m['decodedRgbaSha256']:m for m in masters}
    input_by_key={r['decodedRgbaSha256']:r for r in inputs}
    if len(inputs)!=len(masters) or len(input_by_key)!=len(inputs) or input_by_key.keys()!=by_key.keys():
        raise ValueError('Field repair requires the complete unique neural batch')
    # Validate the entire input history before recording a repair batch.
    for key,r in input_by_key.items():
        data=terrain.bounded_read(battle.staging_path(staging,key+'.png'),32*1024*1024)
        batch.validate(by_key[key],r,data)
        if key in predecessor:
            prior=terrain.bounded_read(battle.staging_path(legacy_staging,key+'.png'),32*1024*1024)
            batch.validate(by_key[key],predecessor[key],prior)
            if r!=predecessor[key] or data!=prior:
                raise ValueError('Field repair predecessor pixels or record changed')
    if (terrain.bounded_read(battle.staging_path(staging,'source-plan.json'),32*1024*1024)!=plan_data
        or terrain.bounded_read(battle.staging_path(staging,'candidates.json'),32*1024*1024)!=inputs_data):
        raise ValueError('Field repair input controls changed during preflight')
    repair_plan=dict(schema=1,pipeline='envhd-field-source-color-perimeter-repair-1',algorithmSha256=terrain.digest(Path(__file__).read_bytes()),
                     inputGenerationPlanSha256=terrain.digest(plan_data),inputCandidatesSha256=terrain.digest(inputs_data),
                     sourceCensus=report,masters=[{k:v for k,v in m.items() if k!='image'} for m in masters],
                     nativeAcceptance='pending',finalQualityAcceptance='pending')
    output.mkdir(parents=True,exist_ok=True)
    target_plan=battle.staging_path(output,'repair-plan.json')
    if target_plan.exists() and batch.read_control(target_plan)!=repair_plan:
        raise ValueError('Field repair plan changed; use fresh staging')
    battle.write_json(target_plan,repair_plan)
    target_records=battle.staging_path(output,'candidates.json')
    records=batch.read_control(target_records) if target_records.exists() else []
    completed={r['decodedRgbaSha256']:r for r in records}
    if len(completed)!=len(records) or not completed.keys()<=by_key.keys():
        raise ValueError('Duplicate or unknown field repair candidate')
    for key,r in completed.items():
        validate(by_key[key],input_by_key[key],terrain.bounded_read(battle.staging_path(staging,key+'.png'),32*1024*1024),r,
                 terrain.bounded_read(battle.staging_path(output,key+'.png'),32*1024*1024))
    for key,item in by_key.items():
        if key in completed:
            continue
        data=terrain.bounded_read(battle.staging_path(staging,key+'.png'),32*1024*1024)
        pixels=repaired_pixels(item,data)
        with Image.open(io.BytesIO(data)) as image:unchanged=np.array_equal(pixels,np.asarray(image))
        if not unchanged:
            stream=io.BytesIO();Image.fromarray(pixels).save(stream,format='PNG');data=stream.getvalue()
        destination=battle.staging_path(output,key+'.png');destination.write_bytes(data)
        r=record(item,input_by_key[key],terrain.digest(data))
        validate(item,input_by_key[key],terrain.bounded_read(battle.staging_path(staging,key+'.png'),32*1024*1024),r,data)
        records.append(r);completed[key]=r;battle.write_json(target_records,records)
        if len(records)%128==0:
            print(json.dumps(dict(repaired=len(records),total=len(masters))),flush=True)
    print(json.dumps(dict(repaired=len(records),total=len(masters),guarded=sum(r['guardedNativePixels']>0 for r in records))),flush=True)
    return records


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('files','staging','legacy-staging','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args();execute(args.files,args.staging,args.legacy_staging,args.output)
