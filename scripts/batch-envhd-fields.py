#!/usr/bin/env python3
"""Restore every unowned visible field image in one resumable private worklist.

Skurfa-owned sources never enter inference. Each native foreground is restored
independently, with its own exact visibility and black classes. This is candidate
production only; visual review and source-bound runtime integration remain open.
"""
import argparse
import io
import importlib.util
import json
from pathlib import Path
import subprocess

import numpy as np
from PIL import Image

spec=importlib.util.spec_from_file_location('field_sources',Path(__file__).with_name('census-envhd-fields.py'))
fields=importlib.util.module_from_spec(spec);spec.loader.exec_module(fields)
terrain,battle=fields.terrain,fields.battle

# The only reusable predecessor is the published per-image generator. These
# identities were checked against this exact Git revision; a metadata match is
# insufficient evidence that an unknown generator produced a candidate.
LEGACY_GENERATION_COMMIT='75f924ac4fa95737d6e58c5559d93c82522f54c9'
LEGACY_SCRIPT_SHA256='23e2bf0d9e6482d6c3fa422c9253c767cbc6964440caa1cc76de001a3097a447'
LEGACY_DEPENDENCIES={
    'census-envhd-fields.py':'f1c1df5acd2774ff5a08fd9637a1c05854e3944d1157cf1c1668fdc601cfcc02',
    'batch-envhd-battle-materials.py':'b6e15d1183cd2920b47fcf6225d5cbbe6d2c02e7df8690324b07f53841fbd23b',
    'batch-envhd-terrain.py':'2e182287e873db6aa46cbede3bf18339c894608dc7a8610abf78fe8c8ee64751',
    'modelshd-field-map.py':'95f275c4178cf1bafca65f87082d579758eb09156d8f605f1d1d4130332194a9',
}


def verify_reuse_origin(plan):
    if (plan.get('schema')!=1 or plan.get('pipeline')!='envhd-complete-field-batch-1'
        or plan.get('scriptSha256')!=LEGACY_SCRIPT_SHA256
        or plan.get('dependencySha256')!=LEGACY_DEPENDENCIES
        or 'reusedOutputs' in plan
        or plan.get('sourceCensus',{}).get('pipeline')!='envhd-field-visible-pixel-census-1'
        or plan['sourceCensus'].get('pipelineSha256')!=LEGACY_DEPENDENCIES['census-envhd-fields.py']):
        raise ValueError('Prior field generation is not a supported pinned predecessor')


def sources(files,report):
    wanted={a['decodedRgbaSha256']:a for a in report['assets'] if a['owners']==['envhd']}
    images={}
    for scene in report['renders']:
        if scene['owner']!='envhd':
            continue
        route=scene['representative']
        data=fields.entries(files/'SECT'/f"DRGN{route['bank']}.BIN"/str(route['directory']))
        actual=[dict(index=i,sha256=terrain.digest(d),size=len(d)) for i,d in data.items()]
        if actual!=scene['sourceFiles']:
            raise ValueError('Field source changed after census')
        base,layers,metadata=fields.render(data,route['cut'])
        if metadata['canvasRect']!=scene['canvasRect'] or metadata['foregrounds']!=scene['foregrounds']:
            raise ValueError('Field rendering changed after census')
        for image,record in zip([base]+layers,scene['images']):
            if image is None or image.getchannel('A').getbbox() is None:
                if 'decodedRgbaSha256' in record:
                    raise ValueError('Field visible source disappeared')
                continue
            key=terrain.fingerprint(image)
            if key!=record['decodedRgbaSha256']:
                raise ValueError('Field pixel identity changed after census')
            if key in wanted:
                images.setdefault(key,image)
    if images.keys()!=wanted.keys():
        raise ValueError('Incomplete unowned field source worklist')
    masters,uniform=[],[]
    for key,asset in sorted(wanted.items()):
        original=images[key]
        item=dict(asset,image=original)
        pixels=np.asarray(original)
        if np.all(pixels==pixels[0,0]):
            uniform.append({k:v for k,v in item.items() if k!='image'} | dict(status='retain-native-uniform',reason='Every source RGBA texel is identical; no pictorial detail to restore.'))
        else:
            masters.append(item)
    return masters,uniform


def candidate_record(item,output_hash):
    return {k:v for k,v in item.items() if k!='image'} | dict(
        targetSize=[item['image'].width*4,item['image'].height*4],outputSha256=output_hash,
        method='real-esrgan-x4plus-clamped-source-layout',scale=4,
        sourceVisibility='exact-nearest-4x-field-alpha-discard-and-visible-black',
        edgeAveraging=False,reviewStatus='pending-source-intent-style-layout-review',**terrain.PINS)


def validate(item,record,data):
    if record!=candidate_record(item,terrain.digest(data)):
        raise ValueError('Field candidate metadata changed')
    if len(data)>32*1024*1024 or len(data)<33 or data[:8]!=b'\x89PNG\r\n\x1a\n' or data[24:26]!=bytes((8,6)):
        raise ValueError('Field candidate requires bounded 8-bit RGBA PNG')
    with Image.open(io.BytesIO(data)) as image:
        if image.mode!='RGBA' or list(image.size)!=record['targetSize']:
            raise ValueError('Field candidate dimensions changed')
        pixels=np.asarray(image)
        restored,_=terrain.preserve_stp(item['image'],pixels[:,:,:3])
        if not np.array_equal(pixels,np.asarray(restored)):
            raise ValueError('Field visibility or visible-black class changed')


def read_control(path):
    return json.loads(terrain.bounded_read(path,32*1024*1024))


def execute(files,output,engine,models,reuse_batch=None):
    battle.private_destination(files,output)
    if output.is_symlink():
        raise ValueError('Refusing a redirected field staging root')
    for path,key in [(engine,'engineSha256'),(models/'realesrgan-x4plus.bin','weightsSha256'),(models/'realesrgan-x4plus.param','parametersSha256')]:
        if terrain.digest(terrain.bounded_read(path,128*1024*1024))!=terrain.PINS[key]:
            raise ValueError('Pinned field inference identity differs')
    report=fields.census(files)
    masters,uniform=sources(files,report)
    masters.sort(key=lambda m:(not any(b['kind']=='background' for b in m['bindings']),m['decodedRgbaSha256']))
    plan=dict(schema=1,pipeline='envhd-complete-field-directory-batch-2',scriptSha256=terrain.digest(Path(__file__).read_bytes()),
              dependencySha256={n:terrain.digest(Path(__file__).with_name(n).read_bytes()) for n in ('census-envhd-fields.py','batch-envhd-battle-materials.py','batch-envhd-terrain.py','modelshd-field-map.py')},
              scope='All unowned nonuniform visible field images; candidate-only, no runtime/native/final acceptance.',
              sourceCensus=report,retainedUniformSources=uniform,
              masters=[{k:v for k,v in m.items() if k!='image'} for m in masters])
    reusable=[]
    if reuse_batch is not None:
        battle.private_destination(files,reuse_batch)
        if reuse_batch.is_symlink():
            raise ValueError('Refusing redirected prior field staging')
        previous_plan_data=terrain.bounded_read(battle.staging_path(reuse_batch,'source-plan.json'),32*1024*1024)
        previous_plan=json.loads(previous_plan_data)
        verify_reuse_origin(previous_plan)
        prior_census=dict(previous_plan['sourceCensus']);current_census=dict(report)
        prior_census.pop('pipelineSha256',None);current_census.pop('pipelineSha256',None)
        prior_masters={m['decodedRgbaSha256']:m for m in previous_plan['masters']}
        current_masters={m['decodedRgbaSha256']:{k:v for k,v in m.items() if k!='image'} for m in masters}
        if prior_census!=current_census or prior_masters!=current_masters or previous_plan['retainedUniformSources']!=uniform:
            raise ValueError('Prior field batch has different source/worklist identity')
        reusable=read_control(battle.staging_path(reuse_batch,'candidates.json'))
        by_key={m['decodedRgbaSha256']:m for m in masters}
        if len({r['decodedRgbaSha256'] for r in reusable})!=len(reusable):
            raise ValueError('Duplicate prior field candidates')
        for record in reusable:
            key=record['decodedRgbaSha256']
            if key not in by_key:
                raise ValueError('Unknown prior field candidate')
            validate(by_key[key],record,terrain.bounded_read(battle.staging_path(reuse_batch,key+'.png'),32*1024*1024))
        plan['reusedOutputs']=[dict(decodedRgbaSha256=r['decodedRgbaSha256'],outputSha256=r['outputSha256'],originPlanSha256=terrain.digest(previous_plan_data),originScriptSha256=previous_plan['scriptSha256'],originGenerationCommit=LEGACY_GENERATION_COMMIT) for r in reusable]
    output.mkdir(parents=True,exist_ok=True)
    plan_path=battle.staging_path(output,'source-plan.json')
    if plan_path.exists() and read_control(plan_path)!=plan:
        raise ValueError('Field plan changed; use fresh staging')
    battle.write_json(plan_path,plan)
    work=battle.staging_path(output,'private-work');work.mkdir(exist_ok=True)
    records_path=battle.staging_path(output,'candidates.json')
    records=read_control(records_path) if records_path.exists() else []
    by_key={m['decodedRgbaSha256']:m for m in masters}
    completed={r['decodedRgbaSha256']:r for r in records}
    if len(completed)!=len(records) or not completed.keys()<=by_key.keys():
        raise ValueError('Duplicate or unknown field candidate')
    # Check all resumed provenance before copying or rewriting any candidates.
    # A modified PNG plus matching local hash must not retain the old origin.
    for record in reusable:
        key=record['decodedRgbaSha256']
        if key in completed and completed[key]!=record:
            raise ValueError('Resumed field candidate differs from its pinned reuse origin')
    for record in reusable:
        key=record['decodedRgbaSha256']
        if key not in completed:
            destination=battle.staging_path(output,key+'.png')
            data=terrain.bounded_read(battle.staging_path(reuse_batch,key+'.png'),32*1024*1024)
            validate(by_key[key],record,data)
            destination.write_bytes(data)
            records.append(record);completed[key]=record
    if reusable:
        battle.write_json(records_path,records)
    for key,record in completed.items():
        validate(by_key[key],record,terrain.bounded_read(battle.staging_path(output,key+'.png'),32*1024*1024))
    print(json.dumps(dict(wholeWorklist=len(masters),uniformNative=len(uniform),protectedUpstream=report['protectedSharedPixelImages'],completed=len(records))),flush=True)
    reused_keys={r['decodedRgbaSha256'] for r in reusable}
    worklist=[(key,item) for key,item in by_key.items() if key not in reused_keys]
    for chunk_index in range(0,len(worklist),128):
        chunk=worklist[chunk_index:chunk_index+128]
        if all(key in completed for key,item in chunk):
            continue
        label=f'{chunk_index//128:04d}'
        input_dir=battle.staging_path(output,'private-work/inputs-'+label)
        inferred_dir=battle.staging_path(output,'private-work/inference-'+label)
        input_dir.mkdir(exist_ok=True);inferred_dir.mkdir(exist_ok=True)
        expected_names={key+'.png' for key,item in chunk}
        if any(p.is_symlink() or p.name not in expected_names for p in input_dir.iterdir()):
            raise ValueError('Unexpected or redirected field inference input')
        if any(p.is_symlink() or p.name not in expected_names for p in inferred_dir.iterdir()):
            raise ValueError('Unexpected or redirected field inference output')
        for key,item in chunk:
            original=item['image']
            original.save(battle.staging_path(output,'private-work/'+key+'-source.png'))
            Image.fromarray(np.pad(np.asarray(original.convert('RGB')),((16,16),(16,16),(0,0)),mode='edge')).save(battle.staging_path(output,'private-work/inputs-'+label+'/'+key+'.png'))
        log_path=battle.staging_path(output,'private-work/inference-'+label+'.log')
        print(json.dumps(dict(phase='persistent-directory-inference',completed=len(records),total=len(masters),chunk=len(chunk))),flush=True)
        run=subprocess.run([str(engine),'-i',str(input_dir),'-o',str(inferred_dir),'-m',str(models),'-n','realesrgan-x4plus','-s','4','-t','256','-j','1:1:1'],capture_output=True,timeout=240*len(chunk))
        log_path.write_bytes(run.stdout+run.stderr)
        if run.returncode:
            raise RuntimeError('Field directory inference failed; private inputs/log/outputs retained')
        if {p.name for p in inferred_dir.iterdir()}!=expected_names:
            raise ValueError('Incomplete field directory inference output')
        for key,item in chunk:
            if key in completed:
                continue
            original=item['image'];inferred_path=battle.staging_path(output,'private-work/inference-'+label+'/'+key+'.png')
            png_path=battle.staging_path(output,key+'.png')
            with Image.open(inferred_path) as inferred:
                if inferred.size!=((original.width+32)*4,(original.height+32)*4):
                    raise ValueError('Field inference dimensions changed')
                pixels=np.asarray(inferred.convert('RGB').crop((64,64,64+original.width*4,64+original.height*4)))
            restored,_=terrain.preserve_stp(original,pixels);restored.save(png_path)
            data=terrain.bounded_read(png_path,32*1024*1024)
            record=candidate_record(item,terrain.digest(data));validate(item,record,data)
            records.append(record);completed[key]=record;battle.write_json(records_path,records)
        print(json.dumps(dict(completed=len(records),total=len(masters),chunkCompleted=len(chunk))),flush=True)
    return records


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--engine',type=Path,required=True)
    parser.add_argument('--models',type=Path,required=True)
    parser.add_argument('--reuse-batch',type=Path)
    args=parser.parse_args()
    execute(args.files,args.output,args.engine,args.models,args.reuse_batch)
