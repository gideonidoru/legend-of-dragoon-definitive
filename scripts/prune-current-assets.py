#!/usr/bin/env python3
"""Remove only obsolete recognized assets after verified current publication.

Unknown assets and assets staged after the committed manifest are protected.
Newer/equal competing manifests require a fresh complete reference audit before
cleanup, so a pending publisher's payload is never guessed disposable.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import re
from datetime import datetime
from current_publication import TAG, REPO, PLATFORMS, canonical, parse_body, check_references, index_assets, check_asset, closure

ALIASES={'Install-Definitive.desktop','Install-Definitive.sh','Definitive-Installer.zip','SHA256SUMS',
         'Legend-of-Dragoon-Definitive-linux-x64.zip','Legend-of-Dragoon-Definitive-macos-arm64.zip',
         'Definitive-Contents-linux-x64.zip','Definitive-Contents-macos-arm64.zip'}
RECOGNIZED=re.compile(r'(?:file-[a-f0-9]{64}|manifest-[a-f0-9]{64}\.json|(?:package|contents)-(?:linux-x64|macos-arm64)-[a-f0-9]{64}\.zip|installer-[a-f0-9]{64}\.zip|entry-[a-f0-9]{64}\.sh|source-fmvhd-[a-f0-9]{64}\.zip|Env[1-4]HD-[A-Za-z0-9._-]+\.zip|FMVHD-v0\.1\.0-videos\.zip)')


def time(value):
    if not isinstance(value,str):raise ValueError('Missing asset chronology')
    return datetime.fromisoformat(value.replace('Z','+00:00'))


def plan(manifest, assets, required, aliases):
    indexed=index_assets(assets);sha=check_references(manifest,assets)
    if not ALIASES.issubset(aliases):raise ValueError('Incomplete bootstrap retention inventory')
    keep=set(required)|set(aliases);marker=indexed['manifest-'+sha+'.json']
    boundary=time(marker.get('created_at'));remove=[];protected=[]
    for name, record in {**required,**aliases}.items():
        check_asset(indexed.get(name,{}),name,record['bytes'],record['sha256'])
    # A complete, fresh publication receipt binds every current managed file.
    if manifest['fmvSource']['name'] not in keep:raise ValueError('Pinned source input omitted')
    for name,asset in indexed.items():
        if name in keep:continue
        if not RECOGNIZED.fullmatch(name) or time(asset.get('created_at'))>=boundary:
            protected.append(name);continue
        remove.append(dict(id=asset['id'],name=name,bytes=asset['size'],digest=asset.get('digest'),created_at=asset['created_at']))
    return dict(manifestSha256=sha,kept=sorted(keep),protected=sorted(protected),remove=remove)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publication',type=Path,required=True);parser.add_argument('--execute',action='store_true')
    args=parser.parse_args();proof=json.loads(args.publication.read_text())
    if proof.get('result')!='PASS' or proof.get('unchanged') is not False or 'closure' not in proof:
        raise ValueError('A newly verified complete publication receipt is required')
    packages={platform:tuple(args.publication.parent/'uploads'/proof['manifest']['platforms'][platform][kind]['name'] for kind in ('package','contents')) for platform in PLATFORMS}
    complete=closure(proof['manifest'],packages)
    if complete!={name:(record['bytes'],record['sha256']) for name,record in proof['closure'].items()}:
        raise ValueError('Publication receipt differs from the verified complete managed inventory')
    module=importlib.util.spec_from_file_location('publisher',Path(__file__).with_name('publish-current-release.py'))
    publisher=importlib.util.module_from_spec(module);module.loader.exec_module(publisher)
    server=publisher.GitHub(int(proof['manifest']['releaseId']))
    def verify():
        latest=server.api(f'repos/{REPO}/releases/latest');current=server.release()
        if latest['id']!=server.id or current['tag_name']!=TAG or current.get('draft') or current.get('prerelease') or parse_body(current.get('body') or '',server.id)!=proof['manifest']:
            raise ValueError('The verified current revision changed; cleanup stopped')
        return plan(proof['manifest'],server.assets(),proof['closure'],proof['aliases'])
    initial=verify();deleted=[]
    if args.execute:
        for asset in initial['remove']:
            fresh=verify();candidates={a['id']:a for a in fresh['remove']}
            if candidates.get(asset['id'])!=asset:raise ValueError('Obsolete asset identity changed')
            server.api(f'repos/{REPO}/releases/assets/{asset["id"]}','DELETE');deleted.append(asset['name'])
        verify()
    result=dict(initial,deleted=deleted)
    args.publication.with_name('asset-retention-result.json').write_bytes(canonical(result)+b'\n')
    print(json.dumps(dict(planned=len(initial['remove']),deleted=len(deleted),protected=len(initial['protected'])),indent=2))


if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError) as error:raise SystemExit('Retention stopped: '+str(error))
