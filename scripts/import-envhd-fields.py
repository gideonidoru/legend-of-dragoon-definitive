#!/usr/bin/env python3
"""Version a complete, individually reviewed field batch without selecting it.

Original pixels and private review boards stay outside the repository. Immutable
candidate files are preflighted before any writes; the public ledger is written
last, so an interrupted import never advertises missing artwork as complete.
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def module(name,filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result)
    return result


batch=module('field_candidates','batch-envhd-fields.py')
publication=module('field_publication','import-envhd-sky.py')
terrain,battle=batch.terrain,batch.battle
DIRECTORY_GENERATION_COMMIT='8b9fc0b6d3ae373ae3061359ff729ecd67de20a9'
DIRECTORY_SCRIPT_SHA256='fc8ab4285afeb6181d0d17dcf8eeb5a7ab508858f8b39720d5ecfabd9cb216ba'
DIRECTORY_DEPENDENCIES=batch.LEGACY_DEPENDENCIES|{
    'census-envhd-fields.py':'061d791e6af5d3c3cf62ae814442e658ab5c2dcd7b5761b9756963669fee543b',
}
SCOPE='All unowned nonuniform visible field images; candidate-only, no runtime/native/final acceptance.'
REVIEW_KEYS={'intent','style','layout','verdict','outputSha256','nativeAcceptance','finalQualityAcceptance'}


def verify_plans(plan,report,masters,uniform,legacy_data,legacy_records):
    legacy=json.loads(legacy_data)
    batch.verify_reuse_origin(legacy)
    legacy_census=copy.deepcopy(report)
    legacy_census['pipelineSha256']=batch.LEGACY_DEPENDENCIES['census-envhd-fields.py']
    master_records=[{k:v for k,v in m.items() if k!='image'} for m in masters]
    if (legacy.get('sourceCensus')!=legacy_census or legacy.get('retainedUniformSources')!=uniform
        or {m['decodedRgbaSha256']:m for m in legacy.get('masters',[])}!={m['decodedRgbaSha256']:m for m in master_records}
        or len(legacy.get('masters',[]))!=len(master_records)):
        raise ValueError('Legacy field sources differ from the complete fresh census')
    legacy_by_key={r['decodedRgbaSha256']:r for r in legacy_records}
    keys={m['decodedRgbaSha256'] for m in masters}
    if len(legacy_by_key)!=len(legacy_records) or not legacy_by_key.keys()<=keys:
        raise ValueError('Duplicate or unknown predecessor candidates')
    expected_census=copy.deepcopy(report)
    expected_census['pipelineSha256']=DIRECTORY_DEPENDENCIES['census-envhd-fields.py']
    expected=dict(schema=1,pipeline='envhd-complete-field-directory-batch-2',scriptSha256=DIRECTORY_SCRIPT_SHA256,
                  dependencySha256=DIRECTORY_DEPENDENCIES,scope=SCOPE,sourceCensus=expected_census,
                  retainedUniformSources=uniform,masters=master_records,
                  reusedOutputs=[dict(decodedRgbaSha256=r['decodedRgbaSha256'],outputSha256=r['outputSha256'],
                                      originPlanSha256=terrain.digest(legacy_data),originScriptSha256=batch.LEGACY_SCRIPT_SHA256)
                                 for r in legacy_records])
    if plan!=expected:
        raise ValueError('Directory field generation or reuse history differs from its pinned source plan')
    return legacy_by_key


def verify_complete_records(masters,records,notes):
    keys={m['decodedRgbaSha256'] for m in masters}
    if not isinstance(records,list) or any(not isinstance(r,dict) for r in records) or not isinstance(notes,dict):
        raise ValueError('Invalid field candidate/review controls')
    record_keys=[r.get('decodedRgbaSha256') for r in records]
    if len(record_keys)!=len(keys) or set(record_keys)!=keys or set(notes)!=keys:
        raise ValueError('Publication requires every unique field candidate and individual review')


def verify_review(note,record):
    if not isinstance(note,dict) or set(note)!=REVIEW_KEYS or any(not isinstance(v,str) or not v.strip() for v in note.values()):
        raise ValueError('Explicit field intent, style and layout review required')
    if (note['verdict'] not in ('visual-reviewed-runtime-pending','intent-revision-needed')
        or note['nativeAcceptance']!='pending' or note['finalQualityAcceptance']!='pending'
        or note['outputSha256']!=record['outputSha256']):
        raise ValueError('Field review differs from candidate output or exceeds candidate acceptance')


def publish(files,staging,legacy_staging,review_file):
    for path in (staging,legacy_staging):
        battle.private_destination(files,path)
        if path.is_symlink() or path.resolve().is_relative_to(ROOT):
            raise ValueError('Field staging must remain private and unredirected')
    report=batch.fields.census(files)
    masters,uniform=batch.sources(files,report)
    masters.sort(key=lambda m:(not any(b['kind']=='background' for b in m['bindings']),m['decodedRgbaSha256']))
    by_key={m['decodedRgbaSha256']:m for m in masters}
    plan=batch.read_control(battle.staging_path(staging,'source-plan.json'))
    legacy_data=terrain.bounded_read(battle.staging_path(legacy_staging,'source-plan.json'),32*1024*1024)
    legacy_records=batch.read_control(battle.staging_path(legacy_staging,'candidates.json'))
    legacy_by_key=verify_plans(plan,report,masters,uniform,legacy_data,legacy_records)
    records=batch.read_control(battle.staging_path(staging,'candidates.json'))
    reviews=batch.read_control(review_file)
    if not isinstance(reviews,dict) or set(reviews)!={'reviews'}:
        raise ValueError('Invalid field review controls')
    notes=reviews['reviews'];verify_complete_records(masters,records,notes)
    production=ROOT/'integrations/envhd/production'
    assets=[];copies=[]
    # Bounded per-image validation avoids loading gigabytes of PNGs at once.
    for record in records:
        key=record['decodedRgbaSha256'];item=by_key[key];note=notes[key]
        source=battle.staging_path(staging,key+'.png')
        png=terrain.bounded_read(source,32*1024*1024);batch.validate(item,record,png);verify_review(note,record)
        origin=legacy_by_key.get(key)
        if origin is not None:
            previous=terrain.bounded_read(battle.staging_path(legacy_staging,key+'.png'),32*1024*1024)
            batch.validate(item,origin,previous)
            if record!=origin or png!=previous:
                raise ValueError('Reused field pixels or record differ from their immutable predecessor')
        metadata=dict(record,reviewStatus=note['verdict'],review=note,batchPipelineSha256=plan['scriptSha256'],
                      batchDependencySha256=plan['dependencySha256'],generationCommit=DIRECTORY_GENERATION_COMMIT,
                      sourceRole='Independent field background or foreground; native placement, depth and script state remain authoritative',
                      styleReferenceUse='Skurfa visual reference only; its resources are unchanged',
                      ownerApproval='Batch pipeline and Python repair explicitly authorized 2026-10-10')
        if origin is not None:
            metadata['generationCommit']=batch.LEGACY_GENERATION_COMMIT
            metadata['generationScriptSha256']=batch.LEGACY_SCRIPT_SHA256
            metadata['reuseOriginPlanSha256']=terrain.digest(legacy_data)
        directory=production/'field-candidates'/key
        target=battle.staging_path(ROOT,str((directory/'image-v1.png').relative_to(ROOT)))
        manifest=battle.staging_path(ROOT,str((directory/'manifest-v1.json').relative_to(ROOT)))
        metadata_bytes=publication.json_bytes(metadata)
        publication.check_version(png,target);publication.check_version(metadata_bytes,manifest)
        copies.append((source,target,manifest,metadata_bytes,record['outputSha256']))
        assets.append(metadata)
    ledger=dict(schema=1,assets=assets,retainedUniformSources=uniform,runtimeSelected=0,nativeAccepted=0,finalQualityAccepted=0,
                sourceConfigurations=report['sourceConfigurations'],
                visualReviewed=sum(a['reviewStatus']=='visual-reviewed-runtime-pending' for a in assets),
                revisionsNeeded=sum(a['reviewStatus']=='intent-revision-needed' for a in assets),
                scope='Complete candidate history only; field runtime adapter and installed selections remain pending')
    ledger_path=battle.staging_path(ROOT,'integrations/envhd/production/field-artwork.json')
    ledger_bytes=publication.json_bytes(ledger)
    for source,target,manifest,data,expected_hash in copies:
        png=terrain.bounded_read(source,32*1024*1024)
        if terrain.digest(png)!=expected_hash:
            raise ValueError('Field candidate changed after preflight')
        publication.check_version(png,target);publication.check_version(data,manifest)
        publication.atomic_write(target,png);publication.atomic_write(manifest,data)
    publication.atomic_write(ledger_path,ledger_bytes)
    return len(assets)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('files','staging','legacy-staging','reviews'):
        parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args()
    print('Versioned custom field candidates:',publish(args.files,args.staging,args.legacy_staging,args.reviews))
