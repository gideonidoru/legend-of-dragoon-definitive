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

repo = Path(__file__).resolve().parents[1]
source = 'a' * 40
for scenario in ('success', 'lookup-failure', 'build-failure', 'account-mismatch', 'digest-mismatch', 'missing-asset', 'size-mismatch', 'already-public', 'tag-mismatch', 'tag-lookup-failure', 'wrong-workflow', 'missing-build-job', 'annotated-tag', 'draft-temporary-urls', 'wrong-draft-url', 'temporary-public-url'):
    with tempfile.TemporaryDirectory(prefix='definitive-release-fixture-') as directory:
        root = Path(directory); assets = root / 'assets'; assets.mkdir(); tools = root / 'tools'; tools.mkdir()
        (assets / 'Definitive-Installer.zip').write_bytes(b'original installer fixture')
        sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        tag = 'fixture-recovery'
        (assets / 'Install-Definitive.sh').write_text(f'TAG={tag}\nEXPECTED={sha(assets / "Definitive-Installer.zip")}\n')
        (assets / 'Install-Definitive.desktop').write_text(f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag}/Install-Definitive.sh {sha(assets / "Install-Definitive.sh")}')
        for platform in ('linux-x64', 'macos-arm64'):
            with zipfile.ZipFile(assets / f'Legend-of-Dragoon-Definitive-{platform}.zip', 'w') as archive:
                archive.writestr('definitive-package.properties', f'format=1\njava=25\nsourceRevision={source}\nreleaseTag={tag}\nplatform={platform}\n')
        names = sorted(p.name for p in assets.iterdir())
        (assets / 'SHA256SUMS').write_text(''.join(sha(assets / n) + '  ' + n + '\n' for n in names))
        temporary_tag = 'untagged-e8c0b3b8be797c205abe'
        release = {'tagName': tag, 'targetCommitish': source, 'isDraft': scenario not in ('already-public', 'temporary-public-url'), 'url': f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/{temporary_tag if scenario in ("draft-temporary-urls", "wrong-draft-url", "temporary-public-url") else tag}', 'assets': []}
        for path in assets.iterdir():
            release['assets'].append({'name': path.name, 'state': 'uploaded', 'size': path.stat().st_size, 'digest': 'sha256:' + sha(path), 'url': f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag}/{path.name}'})
        if scenario == 'digest-mismatch':release['assets'][0]['digest'] = 'sha256:' + '0' * 64
        if scenario == 'size-mismatch':release['assets'][0]['size'] += 1
        if scenario == 'missing-asset':release['assets'].pop()
        if scenario in ('draft-temporary-urls', 'wrong-draft-url', 'temporary-public-url'):
            for asset in release['assets']:asset['url'] = asset['url'].replace('/' + tag + '/', '/' + temporary_tag + '/')
        if scenario == 'wrong-draft-url':release['assets'][0]['url'] = release['assets'][0]['url'].replace(temporary_tag, 'untagged-aaaaaaaaaaaaaaaaaaaa')
        (root / 'release.json').write_text(json.dumps(release)); (root / 'notes.md').write_text('Fixture only')
        gh = tools / 'gh'
        gh.write_text('#!' + sys.executable + '\n' + '''import json,os,sys
from pathlib import Path
root=Path(os.environ['FIXTURE_ROOT']);a=sys.argv[1:];scenario=os.environ['FIXTURE_SCENARIO']
with (root/'calls.jsonl').open('a') as stream:stream.write(json.dumps(a)+'\\n')
if a[:2]==['api','user']:print('wrong-account' if scenario=='account-mismatch' else 'gideonidoru')
elif a[0]=='api' and '/actions/runs/' in a[1]:
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
    if scenario=='draft-temporary-urls':
        data['url']=data['url'].replace('untagged-e8c0b3b8be797c205abe','fixture-recovery')
        for asset in data['assets']:asset['url']=asset['url'].replace('untagged-e8c0b3b8be797c205abe','fixture-recovery')
    (root/'release.json').write_text(json.dumps(data));print('published fixture')
else:raise AssertionError(a)
''')
        gh.chmod(0o755)
        env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'], FIXTURE_ROOT=str(root), FIXTURE_SCENARIO=scenario)
        result = subprocess.run([sys.executable, str(repo / 'scripts/publish-verified-release.py'), '--tag', tag, '--source-sha', source, '--run', '123', '--assets-dir', str(assets), '--notes-file', str(root / 'notes.md')], env=env, capture_output=True, text=True, timeout=5)
        calls = [json.loads(line) for line in (root / 'calls.jsonl').read_text().splitlines()]
        publications = [a for a in calls if a[:2] == ['release', 'edit']]
        if scenario in ('success', 'annotated-tag', 'draft-temporary-urls'):assert result.returncode == 0 and len(publications) == 1 and '--draft=false' in publications[0], result.stderr
        elif scenario == 'already-public':assert result.returncode == 0 and not publications, result.stderr
        else:assert result.returncode != 0 and not publications, 'Failed gate published: ' + scenario
        print('PASS', scenario)
print('PASS: release lookup/build/account/checksum/size/completeness failures cannot publish')
