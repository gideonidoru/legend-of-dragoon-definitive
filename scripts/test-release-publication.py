#!/usr/bin/env python3
"""Run the real publication gate with an isolated CLI; failed checks must never publish."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import zipfile
from package_validation import ROOT

repo = Path(__file__).resolve().parents[1]
source = 'a' * 40
for scenario in ('success', 'lookup-failure', 'build-failure', 'account-mismatch', 'digest-mismatch', 'missing-asset', 'size-mismatch', 'already-public', 'tag-mismatch', 'tag-lookup-failure', 'wrong-workflow', 'missing-build-job', 'annotated-tag', 'draft-temporary-urls', 'wrong-draft-url', 'temporary-public-url', 'ci-bytes-mismatch', 'missing-ci-artifact', 'expired-ci-artifact', 'wrong-artifact-source', 'artifact-digest-mismatch', 'nonzip-installer', 'missing-payload', 'corrupt-payload', 'duplicate-entry', 'traversal-entry', 'linked-entry', 'file-delivery-success', 'file-delivery-ci-mismatch', 'file-delivery-missing-blob', 'file-delivery-many-assets-draft', 'file-delivery-many-assets-omitted-page', 'ci-source-input-omitted'):
    with tempfile.TemporaryDirectory(prefix='definitive-release-fixture-') as directory:
        root = Path(directory); assets = root / 'assets'; assets.mkdir(); tools = root / 'tools'; tools.mkdir()
        with zipfile.ZipFile(assets / 'Definitive-Installer.zip', 'w') as archive:
            for name in ('Install.sh', 'bootstrap-java', 'definitive-manager.jar', 'LICENSE', 'CREDITS', 'manager-libs/library.jar'):
                archive.writestr(name, b'original synthetic installer fixture')
        sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        tag = 'fixture-recovery'
        (assets / 'Install-Definitive.sh').write_text(f'TAG={tag}\nEXPECTED={sha(assets / "Definitive-Installer.zip")}\n')
        (assets / 'Install-Definitive.desktop').write_text(f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag}/Install-Definitive.sh {sha(assets / "Install-Definitive.sh")}')
        for platform in ('linux-x64', 'macos-arm64'):
            metadata = {'format': '1', 'java': '25', 'sourceRevision': source, 'releaseTag': tag,
                        'platform': platform, 'gameJar': 'lod-game-fixture.jar'}
            payload = {n: b'original synthetic package fixture' for n in ROOT | {'lod-game-fixture.jar', 'libs/library.jar', 'bundled-mods/art.jar'}}
            if scenario.startswith('file-delivery-many-assets'): payload.update({f'gfx/item-{i}.txt': ('unique-'+str(i)).encode() for i in range(120)})
            hashes = {n: hashlib.sha256(b).hexdigest() for n, b in payload.items()}
            canonical = ''.join(k + '=' + metadata[k] + '\n' for k in sorted(metadata))
            canonical += ''.join(k + '=' + hashes[k] + '\n' for k in sorted(hashes))
            metadata['id'] = 'alpha-' + hashlib.sha256(canonical.encode()).hexdigest()[:16]
            with zipfile.ZipFile(assets / f'Legend-of-Dragoon-Definitive-{platform}.zip', 'w') as archive:
                archive.writestr('definitive-package.properties', ''.join(k + '=' + v + '\n' for k, v in metadata.items()))
                archive.writestr('definitive-files.properties', ''.join(k + '=' + v + '\n' for k, v in hashes.items()))
                for name, data in payload.items():
                    if scenario == 'missing-payload' and name == 'bootstrap-java':continue
                    archive.writestr(name, b'corrupt' if scenario == 'corrupt-payload' and name == 'bootstrap-java' else data)
                if scenario == 'duplicate-entry':archive.writestr('bootstrap-java', b'duplicate')
                if scenario == 'traversal-entry':archive.writestr('../outside', b'unsafe')
                if scenario == 'linked-entry':
                    entry = zipfile.ZipInfo('tools/link'); entry.create_system = 3; entry.external_attr = 0o120777 << 16
                    archive.writestr(entry, b'outside')
        if scenario == 'nonzip-installer':(assets / 'Definitive-Installer.zip').write_bytes(b'not an archive')
        if scenario.startswith('file-delivery'):
            import importlib.util
            spec=importlib.util.spec_from_file_location('file_assembler',repo/'scripts/assemble-file-delivery.py'); assembler=importlib.util.module_from_spec(spec);spec.loader.exec_module(assembler)
            for platform in ('linux-x64','macos-arm64'):
                assembler.assemble(assets/f'Legend-of-Dragoon-Definitive-{platform}.zip',assets)
            for blob in (assets/'files').iterdir():blob.rename(assets/blob.name)
            (assets/'files').rmdir()
            if scenario=='file-delivery-missing-blob':next(assets.glob('file-*')).unlink()
        artifact_records = []
        for id, artifact_name, selected in ((1, 'delivery-ubuntu-24.04', ('Definitive-Installer.zip', 'Install-Definitive.sh', 'Install-Definitive.desktop', 'Legend-of-Dragoon-Definitive-linux-x64.zip')),
                                             (2, 'delivery-macos-15', ('Legend-of-Dragoon-Definitive-macos-arm64.zip',))):
            if scenario.startswith('file-delivery'):
                platform='linux-x64' if id==1 else 'macos-arm64'
                selected=tuple(selected)+(f'Definitive-Contents-{platform}.zip',)+tuple(p.name for p in assets.glob('file-*'))
            path = root / f'artifact-{id}.zip'
            with zipfile.ZipFile(path, 'w') as artifact:
                if scenario == 'ci-source-input-omitted': artifact.writestr('distributions/FMVHD-v0.1.0-videos.zip', b'approved synthetic source input')
                for name in selected:
                    artifact.writestr('distributions/' + name, b'unapproved bytes' if (scenario == 'ci-bytes-mismatch' and name == 'Definitive-Installer.zip') or (scenario=='file-delivery-ci-mismatch' and name.startswith('file-')) else (assets / name).read_bytes())
            artifact_records.append({'id': id, 'name': artifact_name, 'expired': scenario == 'expired-ci-artifact',
                                     'digest': 'sha256:' + ('0'*64 if scenario == 'artifact-digest-mismatch' else sha(path)),
                                     'workflow_run': {'id': 123, 'head_sha': 'b'*40 if scenario == 'wrong-artifact-source' else source}})
        if scenario == 'missing-ci-artifact':artifact_records.pop()
        (root / 'artifacts.json').write_text(json.dumps([{'artifacts': artifact_records}]))
        names = sorted(p.name for p in assets.iterdir())
        (assets / 'SHA256SUMS').write_text(''.join(sha(assets / n) + '  ' + n + '\n' for n in names))
        temporary_tag = 'untagged-e8c0b3b8be797c205abe'
        release = {'databaseId':456, 'tagName': tag, 'targetCommitish': source, 'isDraft': scenario not in ('already-public', 'temporary-public-url'), 'url': f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/{temporary_tag if scenario in ("draft-temporary-urls", "wrong-draft-url", "temporary-public-url", "file-delivery-many-assets-draft", "file-delivery-many-assets-omitted-page") else tag}', 'assets': []}
        for path in assets.iterdir():
            release['assets'].append({'name': path.name, 'state': 'uploaded', 'size': path.stat().st_size, 'digest': 'sha256:' + sha(path), 'url': f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag}/{path.name}'})
        if scenario == 'digest-mismatch':release['assets'][0]['digest'] = 'sha256:' + '0' * 64
        if scenario == 'size-mismatch':release['assets'][0]['size'] += 1
        if scenario == 'missing-asset':release['assets'].pop()
        if scenario in ('draft-temporary-urls', 'wrong-draft-url', 'temporary-public-url', 'ci-bytes-mismatch', 'missing-ci-artifact', 'expired-ci-artifact', 'wrong-artifact-source', 'artifact-digest-mismatch', 'nonzip-installer', 'missing-payload', 'corrupt-payload', 'duplicate-entry', 'traversal-entry', 'linked-entry', 'file-delivery-many-assets-draft', 'file-delivery-many-assets-omitted-page'):
            for asset in release['assets']:asset['url'] = asset['url'].replace('/' + tag + '/', '/' + temporary_tag + '/')
        if scenario == 'wrong-draft-url':release['assets'][0]['url'] = release['assets'][0]['url'].replace(temporary_tag, 'untagged-aaaaaaaaaaaaaaaaaaaa')
        (root / 'release.json').write_text(json.dumps(release)); (root / 'notes.md').write_text('Fixture only')
        gh = tools / 'gh'
        gh.write_text('#!' + sys.executable + '\n' + '''import json,os,sys
from pathlib import Path
root=Path(os.environ['FIXTURE_ROOT']);a=sys.argv[1:];scenario=os.environ['FIXTURE_SCENARIO']
with (root/'calls.jsonl').open('a') as stream:stream.write(json.dumps(a)+'\\n')
if a[:2]==['api','user']:print('wrong-account' if scenario=='account-mismatch' else 'gideonidoru')
elif a[0]=='api' and '/releases/456/assets?' in a[1]:
    data=json.loads((root/'release.json').read_text());assets=[dict(asset,browser_download_url=asset['url']) for asset in data['assets']]
    print(json.dumps([assets[:100]] if scenario=='file-delivery-many-assets-omitted-page' else [assets[:100],assets[100:]]))
elif a[0]=='api' and '/actions/artifacts/' in a[1]:
    id=a[1].split('/')[-2];sys.stdout.buffer.write((root/f'artifact-{id}.zip').read_bytes())
elif a[0]=='api' and '/actions/runs/' in a[1]:
    if '/artifacts?' in a[1]:print((root/'artifacts.json').read_text());sys.exit(0)
    if '/jobs?' in a[1]:
        names=['Original synthetic visual fixtures','Linux x64 / Steam Deck package','macOS package']
        if scenario=='missing-build-job':names.pop()
        print(json.dumps({'jobs':[{'name':n,'status':'completed','conclusion':'success'} for n in names]}))
    else:print(json.dumps({'status':'completed','conclusion':'failure' if scenario=='build-failure' else 'success','head_sha':'a'*40,'path':'.github/workflows/unrelated.yml' if scenario=='wrong-workflow' else '.github/workflows/build.yml'}))
elif a[0]=='api' and '/git/matching-refs/' in a[1]:
    if scenario=='tag-lookup-failure':sys.exit(1)
    public=not json.loads((root/'release.json').read_text())['isDraft']
    exists=public or scenario in ('tag-mismatch','annotated-tag')
    obj={'type':'tag' if scenario=='annotated-tag' else 'commit','sha':'b'*40 if scenario in ('tag-mismatch','annotated-tag') else 'a'*40}
    print(json.dumps([[{'ref':'refs/tags/fixture-recovery','object':obj}] if exists else []]))
elif a[0]=='api' and '/git/tags/' in a[1]:print(json.dumps({'object':{'type':'commit','sha':'a'*40}}))
elif a[:2]==['release','view']:
    if scenario=='lookup-failure':sys.exit(1)
    print((root/'release.json').read_text())
elif a[:2]==['release','edit']:
    data=json.loads((root/'release.json').read_text());data['isDraft']=False
    if scenario in ('draft-temporary-urls','file-delivery-many-assets-draft'):
        data['url']=data['url'].replace('untagged-e8c0b3b8be797c205abe','fixture-recovery')
        for asset in data['assets']:asset['url']=asset['url'].replace('untagged-e8c0b3b8be797c205abe','fixture-recovery')
    (root/'release.json').write_text(json.dumps(data));print('published fixture')
else:raise AssertionError(a)
''')
        gh.chmod(0o755)
        env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'], FIXTURE_ROOT=str(root), FIXTURE_SCENARIO=scenario)
        result = subprocess.run([sys.executable, str(repo / 'scripts/publish-verified-release.py'), '--tag', tag, '--source-sha', source, '--run', '123', '--assets-dir', str(assets), '--notes-file', str(root / 'notes.md')], env=env, capture_output=True, text=True, timeout=10)
        calls = [json.loads(line) for line in (root / 'calls.jsonl').read_text().splitlines()] if (root / 'calls.jsonl').exists() else []
        publications = [a for a in calls if a[:2] == ['release', 'edit']]
        if scenario in ('success', 'annotated-tag', 'draft-temporary-urls', 'file-delivery-success', 'file-delivery-many-assets-draft'):assert result.returncode == 0 and len(publications) == 1 and '--draft=false' in publications[0], result.stderr
        elif scenario == 'already-public':assert result.returncode == 0 and not publications, result.stderr
        else:assert result.returncode != 0 and not publications, 'Failed gate published: ' + scenario
        print('PASS', scenario)
print('PASS: release lookup/build/account/checksum/size/completeness failures cannot publish')
