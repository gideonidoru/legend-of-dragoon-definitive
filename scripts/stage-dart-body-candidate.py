#!/usr/bin/env python3
"""Prepare an explicit private CharHD resource overlay for development tests. AGPL v3.
Never modifies selected runtime assets or a normal installation.
"""
from pathlib import Path
import argparse,hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[1]
PRODUCTION=ROOT/'integrations/charhd/runtime-assets/charhd/characters'

def stage(candidate,output,field_candidate=None):
    if output.exists() or output.resolve().is_relative_to(ROOT):raise ValueError('Use a new private output')
    sources=[(PRODUCTION,'accepted',json.loads((PRODUCTION/'characters.json').read_text())['parts']),
             (candidate,'body',json.loads((candidate/'characters.json').read_text())['parts'])]
    if field_candidate:
        sources.append((field_candidate,'field-body',json.loads((field_candidate/'characters.json').read_text())['parts']))
    output.mkdir(parents=True);parts=[]
    for root,prefix,rows in sources:
        for entry in rows:
            if prefix=='accepted' and 'uv' not in entry:continue
            if entry.get('character')!='dart':raise ValueError('Only Dart belongs in this overlay')
            if field_candidate and prefix=='body' and entry['form']!='combat':continue
            if prefix=='field-body' and entry['form']!='field':raise ValueError('Field candidate contains battle parts')
            row=dict(entry)
            for resource in ('pack','uv','texture'):
                if resource not in row:continue
                source=(root/row[resource]).resolve()
                if not source.is_relative_to(root.resolve()):raise ValueError('Resource escaped root')
                if hashlib.sha256(source.read_bytes()).hexdigest()!=row[resource+'Sha256']:raise ValueError('Resource checksum differs')
                dest=output/prefix/row[resource];dest.parent.mkdir(parents=True,exist_ok=True)
                if not dest.exists():shutil.copyfile(source,dest)
                row[resource]=prefix+'/'+row[resource]
            parts.append(row)
    if len(parts)!=32 or len({row['sourceGeometrySha256'] for row in parts})!=32:raise ValueError('Expected all Dart parts with exact accepted field/battle heads')
    (output/'characters.json').write_text(json.dumps({'format':1,'state':'explicit-development-overlay-not-normal-package','parts':parts},indent=2)+'\n')
    shutil.copyfile(ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json',output/'source-audit.json')
    return output

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--field-candidate',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args();print(stage(a.candidate,a.output,a.field_candidate))
