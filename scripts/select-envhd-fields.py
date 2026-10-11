#!/usr/bin/env python3
"""Select the reviewed development subset; missing/held images and Skurfa stay native."""
import argparse, hashlib, importlib.util, json, tempfile, os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('field_select_tools',ROOT/'scripts/import-envhd-fields.py');tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(tools)

def select():
    production=ROOT/'integrations/envhd/production';runtime=ROOT/'integrations/envhd/runtime-assets/envhd/fields'
    ledger=json.loads((production/'field-artwork.json').read_text());reviews=json.loads((production/'field-review-receipt.json').read_text())['reviews']
    records=ledger['assets'];writes={};copies=[];selected=[]
    if len({a['decodedRgbaSha256'] for a in records})!=len(records):raise ValueError('Unique reviewed subset required')
    for a in records:
        key=a['decodedRgbaSha256'];note=a['review'];tools.verify_review(note,a)
        if note!=reviews.get(key) or a['owners']!=['envhd']:raise ValueError('Unreviewed or upstream-owned artwork cannot be selected')
        if note['verdict']=='intent-revision-needed':continue
        folder=production/'field-candidates'/key
        if json.loads((folder/'manifest-v1.json').read_text())!=a:raise ValueError('Versioned candidate differs')
        png=tools.terrain.bounded_read(folder/'image-v1.png',32*1024*1024)
        if tools.terrain.digest(png)!=a['outputSha256']:raise ValueError('Candidate bytes changed')
        w,h=a['inputCandidate']['sourceSize'];metadata=f"schema=1\nsourceRgbaSha256={key}\nsourceWidth={w}\nsourceHeight={h}\nscale=4\noutputSha256={a['outputSha256']}\n".encode()
        target=runtime/key/'image-v1.png';manifest=runtime/key/'manifest.properties'
        tools.publication.check_version(png,target);tools.publication.check_version(metadata,manifest)
        copies.append((folder/'image-v1.png',target,manifest,metadata,a['outputSha256']))
        selected.append(dict(decodedRgbaSha256=key,outputSha256=a['outputSha256'],sourceSize=[w,h],bindings=a['inputCandidate']['bindings']))
    ownership=json.loads((production/'field-ownership.json').read_text());cuts=sorted({m['cut'] for s in ownership['scenes'] if s['owner']=='skurfa' for m in s['mappings']})
    if not cuts:raise ValueError('Missing Skurfa protection')
    writes[runtime/'protected.properties']=('cuts='+','.join(map(str,cuts))+'\n').encode()
    selection=dict(schema=1,scope='Owner-requested reviewed development installation; Skurfa, held and unreviewed sources remain native',runtimeSelected=len(selected),nativeAccepted=0,finalQualityAccepted=0,protectedCuts=cuts,assets=selected,reviewReceiptSha256=tools.terrain.digest((production/'field-review-receipt.json').read_bytes()))
    writes[production/'field-runtime-artwork.json']=tools.publication.json_bytes(selection)
    # Replace the complete owned tree. A withdrawn selection cannot remain executable.
    # Candidate history is immutable; only this generated runtime tree is reconciled.
    ledger_path=production/'field-runtime-artwork.json';previous=ledger_path.read_bytes() if ledger_path.exists() else None
    runtime.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.field-selection-',dir=runtime.parent) as temporary:
        temp=Path(temporary);fresh=temp/'fresh';fresh.mkdir();old=temp/'previous'
        for source,target,manifest,metadata,expected in copies:
            png=tools.terrain.bounded_read(source,32*1024*1024)
            if tools.terrain.digest(png)!=expected:raise ValueError('Source changed after preflight')
            tools.publication.atomic_write(fresh/target.relative_to(runtime),png)
            tools.publication.atomic_write(fresh/manifest.relative_to(runtime),metadata)
        tools.publication.atomic_write(fresh/'protected.properties',writes[runtime/'protected.properties'])
        moved=False
        try:
            if runtime.exists():os.replace(runtime,old)
            os.replace(fresh,runtime);moved=True
            tools.publication.atomic_write(ledger_path,writes[ledger_path])
        except Exception:
            if moved:os.replace(runtime,fresh)
            if old.exists():os.replace(old,runtime)
            if previous is None:ledger_path.unlink(missing_ok=True)
            else:tools.publication.atomic_write(ledger_path,previous)
            raise
    return selection

if __name__=='__main__':print('Selected field images:',select()['runtimeSelected'])
