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


def execute(files,output,engine,models):
    battle.private_destination(files,output)
    if output.is_symlink():
        raise ValueError('Refusing a redirected field staging root')
    for path,key in [(engine,'engineSha256'),(models/'realesrgan-x4plus.bin','weightsSha256'),(models/'realesrgan-x4plus.param','parametersSha256')]:
        if terrain.digest(terrain.bounded_read(path,128*1024*1024))!=terrain.PINS[key]:
            raise ValueError('Pinned field inference identity differs')
    report=fields.census(files)
    masters,uniform=sources(files,report)
    plan=dict(schema=1,pipeline='envhd-complete-field-batch-1',scriptSha256=terrain.digest(Path(__file__).read_bytes()),
              dependencySha256={n:terrain.digest(Path(__file__).with_name(n).read_bytes()) for n in ('census-envhd-fields.py','batch-envhd-battle-materials.py','batch-envhd-terrain.py','modelshd-field-map.py')},
              scope='All unowned nonuniform visible field images; candidate-only, no runtime/native/final acceptance.',
              sourceCensus=report,retainedUniformSources=uniform,
              masters=[{k:v for k,v in m.items() if k!='image'} for m in masters])
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
    for key,record in completed.items():
        validate(by_key[key],record,terrain.bounded_read(battle.staging_path(output,key+'.png'),32*1024*1024))
    print(json.dumps(dict(wholeWorklist=len(masters),uniformNative=len(uniform),protectedUpstream=report['protectedSharedPixelImages'],completed=len(records))),flush=True)
    for key,item in by_key.items():
        if key in completed:
            continue
        original=item['image']
        source_path=battle.staging_path(output,'private-work/'+key+'-source.png')
        input_path=battle.staging_path(output,'private-work/'+key+'-input.png')
        inferred_path=battle.staging_path(output,'private-work/'+key+'-inference.png')
        log_path=battle.staging_path(output,'private-work/'+key+'.log')
        png_path=battle.staging_path(output,key+'.png')
        original.save(source_path)
        Image.fromarray(np.pad(np.asarray(original.convert('RGB')),((16,16),(16,16),(0,0)),mode='edge')).save(input_path)
        run=subprocess.run([str(engine),'-i',str(input_path),'-o',str(inferred_path),'-m',str(models),'-n','realesrgan-x4plus','-s','4','-t','256','-j','1:1:1'],capture_output=True,timeout=240)
        log_path.write_bytes(run.stdout+run.stderr)
        if run.returncode:
            raise RuntimeError('Field inference failed; private log retained')
        with Image.open(inferred_path) as inferred:
            if inferred.size!=((original.width+32)*4,(original.height+32)*4):
                raise ValueError('Field inference dimensions changed')
            pixels=np.asarray(inferred.convert('RGB').crop((64,64,64+original.width*4,64+original.height*4)))
        restored,_=terrain.preserve_stp(original,pixels)
        restored.save(png_path)
        data=terrain.bounded_read(png_path,32*1024*1024)
        record=candidate_record(item,terrain.digest(data));validate(item,record,data)
        records.append(record);battle.write_json(records_path,records)
        print(json.dumps(dict(completed=len(records),total=len(masters),kinds=sorted({b['kind'] for b in item['bindings']}))),flush=True)
    return records


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--engine',type=Path,required=True)
    parser.add_argument('--models',type=Path,required=True)
    args=parser.parse_args()
    execute(args.files,args.output,args.engine,args.models)
