#!/usr/bin/env python3
"""Publish only changed files, committing the current manifest last.

Run the source-bound stager again before publication. A local exclusive lock and
fresh release identity checks serialize this publisher; other project chats must
never independently publish this carrier.
"""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import fcntl
import hashlib
import importlib.util
import json
import subprocess
import tempfile
import os
import stat
from current_publication import (REPO, TAG, PLATFORMS, canonical, digest, body,
    parse_body, index_assets, check_asset, check_references, descriptor, closure)


def load_stager():
    spec = importlib.util.spec_from_file_location('current_stager', Path(__file__).with_name('stage-current-publication.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def publication_lock():
    """One local carrier writer regardless of checkout or staging directory."""
    directory=Path(tempfile.gettempdir())/('definitive-current-publisher-'+str(os.getuid()))
    directory.mkdir(mode=0o700,exist_ok=True)
    info=directory.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid!=os.getuid() or stat.S_IMODE(info.st_mode)!=0o700:
        raise ValueError('Unsafe publisher lock directory')
    descriptor=os.open(directory/'carrier.lock',os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
    info=os.fstat(descriptor)
    if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.getuid():
        os.close(descriptor);raise ValueError('Unsafe publisher lock file')
    stream=os.fdopen(descriptor,'a')
    try:fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BaseException:stream.close();raise
    return stream


def snapshot(release):
    return dict(id=release['id'], tag=release['tag_name'],
                bodySha256=hashlib.sha256((release.get('body') or '').encode()).hexdigest())


def validate_inputs(inputs, root):
    if inputs.get('format') != 1 or inputs.get('result') != 'STAGED' or inputs.get('tag') != TAG:
        raise ValueError('Wrong staged publication format')
    for group in ('immutable', 'aliases'):
        for name, record in inputs[group].items():
            if '/' in name or name in ('.', '..'):
                raise ValueError('Unsafe staged name')
            if 'path' in record:
                if record['path'] != name:
                    raise ValueError('Staged path must equal the exact asset name')
                path = root / 'uploads' / name
                if path.is_symlink() or not path.is_file() or (path.stat().st_size, digest(path)) != (record['bytes'], record['sha256']):
                    raise ValueError('Staged publication bytes changed: ' + name)


def publish(inputs, root, server, now=None):
    """Transport-independent transaction, exercised with failure-injection tests."""
    validate_inputs(inputs, root)
    baseline = server.release()
    if baseline.get('draft') or baseline.get('prerelease') or snapshot(baseline) != inputs['baselineRelease']:
        raise ValueError('Current release changed since staging')
    original = parse_body(baseline.get('body') or '', baseline['id'], required=False)
    if baseline['tag_name'] == TAG and original is None:
        raise ValueError('Current carrier lacks a valid committed manifest')
    if original is not None and baseline['tag_name'] != TAG:
        raise ValueError('Manifest is on the wrong carrier')
    uploaded = {}; reused = {}; renamed = []

    def guard():
        current = server.release()
        if snapshot(current) != snapshot(baseline) or current.get('draft') or current.get('prerelease'):
            raise ValueError('Concurrent current-release publication detected')

    def ensure(name, record):
        guard()
        asset = index_assets(server.assets()).get(name)
        if asset is None:
            if 'path' not in record:
                raise ValueError('Missing immutable server asset without verified upload: ' + name)
            path = root / 'uploads' / record['path']
            if (path.stat().st_size, digest(path)) != (record['bytes'], record['sha256']):
                raise ValueError('Upload bytes changed')
            server.upload(path)
            uploaded[name] = record['bytes']
            asset = index_assets(server.assets()).get(name, {})
        else:
            reused[name] = dict(id=asset['id'], bytes=record['bytes'])
        return check_asset(asset, name, record['bytes'], record['sha256'], baseline['tag_name'])

    # Migration moves the already pinned source ZIP; its ID and bytes stay intact.
    rename = inputs.get('sourceRename')
    if rename:
        guard()
        assets = index_assets(server.assets())
        new = assets.get(rename['name'])
        if new is None:
            old = check_asset(assets.get(rename['oldName'], {}), rename['oldName'], rename['bytes'], rename['sha256'], baseline['tag_name'])
            if old['id'] != rename['id'] or original is not None:
                raise ValueError('Source rename is only allowed during initial verified migration')
            server.rename(old['id'], rename['name'])
            renamed.append(dict(id=old['id'], oldName=old['name'], name=rename['name']))
        else:
            check_asset(new, rename['name'], rename['bytes'], rename['sha256'], baseline['tag_name'])
            if new['id'] != rename['id']:
                raise ValueError('Source asset rename did not preserve its identity')
    selected = {name: ensure(name, record) for name, record in sorted(inputs['immutable'].items())}
    descriptors = {}
    for platform in PLATFORMS:
        descriptors[platform] = {kind: descriptor(selected[inputs['descriptors'][platform+'.'+kind]], kind, platform)
                                 for kind in ('package', 'contents')}
    fixed = {kind: descriptor(selected[inputs['descriptors'][kind]], kind) for kind in ('installer', 'entry', 'fmvSource')}
    unchanged = bool(original and original['sourceRevision'] == inputs['source'] and original['buildRun'] == inputs['run'] and original['platforms'] == descriptors and all(original[k] == v for k,v in fixed.items()))
    manifest = original if unchanged else dict(format=1, generation=(original['generation']+1 if original else 1),
                    sourceRevision=inputs['source'], buildRun=inputs['run'], releaseId=str(baseline['id']),
                    releaseTag=TAG, publishedAt=now or datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
                    platforms=descriptors, **fixed)
    data = canonical(manifest); sha = hashlib.sha256(data).hexdigest()
    name = 'manifest-'+sha+'.json'; (root/'uploads'/name).write_bytes(data)
    ensure(name, dict(bytes=len(data), sha256=sha, path=name))
    check_references(manifest, server.assets(), baseline['tag_name'])
    # Small bootstrap aliases may change. The committed runtime pointer still
    # names immutable files; incomplete aliases fail their existing checksum gate.
    aliases = dict(inputs['aliases'])
    sums = {name: record['sha256'] for name,record in inputs['immutable'].items()}
    sums.update({name: record['sha256'] for name,record in aliases.items()})
    sums[name] = sha
    sums_path = root/'uploads'/'SHA256SUMS'
    sums_path.write_text(''.join(sums[n]+'  '+n+'\n' for n in sorted(sums)), encoding='ascii')
    aliases['SHA256SUMS'] = dict(bytes=sums_path.stat().st_size, sha256=digest(sums_path), path='SHA256SUMS')
    for name, record in sorted(aliases.items()):
        guard()
        asset = index_assets(server.assets()).get(name)
        if asset and asset.get('state') == 'uploaded' and asset.get('size') == record['bytes'] and asset.get('digest') == 'sha256:'+record['sha256']:
            check_asset(asset, name, record['bytes'], record['sha256'], baseline['tag_name'])
            reused[name] = dict(id=asset['id'], bytes=record['bytes'])
            continue
        path = root/'uploads'/name
        if path.is_symlink() or (path.stat().st_size, digest(path)) != (record['bytes'], record['sha256']):
            raise ValueError('Alias upload changed')
        server.replace(name, path, asset)
        uploaded[name] = record['bytes']
        check_asset(index_assets(server.assets()).get(name, {}), name, record['bytes'], record['sha256'], baseline['tag_name'])
    guard()
    check_references(manifest, server.assets(), baseline['tag_name'])
    notes = 'Legend of Dragoon: Definitive\n\nCurrent test download for Steam Deck and macOS.\nRendering, viewport and input fixes; verified incremental installation and repair.\n'
    if not unchanged:server.commit(manifest, body(manifest, notes))
    fresh = server.release()
    if fresh['id'] != baseline['id'] or fresh['tag_name'] != TAG or parse_body(fresh.get('body') or '', fresh['id']) != manifest or fresh.get('draft') or fresh.get('prerelease'):
        raise ValueError('Publication commit could not be verified; do not clean up')
    check_references(manifest, server.assets())
    for name, record in inputs['immutable'].items():
        check_asset(index_assets(server.assets()).get(name, {}), name, record['bytes'], record['sha256'])
    return dict(result='PASS', unchanged=unchanged, manifest=manifest, manifestSha256=sha,
                buildAttempt=inputs['runAttempt'],
                uploaded=uploaded, reused=reused, renamed=renamed,
                uploadedBytes=sum(uploaded.values()), reusedBytes=sum(v['bytes'] for v in reused.values()),
                closure={**inputs['immutable'], 'manifest-'+sha+'.json':dict(bytes=len(data),sha256=sha)}, aliases=aliases)


class GitHub:
    def __init__(self, release_id): self.id = release_id
    def api(self, endpoint, method='GET', value=None):
        args = ['gh','api',endpoint,'--method',method]
        if value is not None:
            with tempfile.NamedTemporaryFile(mode='w', suffix='.json') as stream:
                json.dump(value,stream);stream.flush()
                return json.loads(subprocess.check_output(args+['--input',stream.name],text=True))
        raw = subprocess.check_output(args,text=True)
        return json.loads(raw) if raw.strip() else None
    def release(self): return self.api(f'repos/{REPO}/releases/{self.id}')
    def assets(self):
        raw = subprocess.check_output(['gh','api',f'repos/{REPO}/releases/{self.id}/assets?per_page=100','--paginate','--slurp'],text=True)
        return [a for page in json.loads(raw) for a in page]
    def upload(self, path):
        subprocess.run(['gh','release','upload',self.release()['tag_name'],str(path),'-R',REPO],check=True,timeout=1800)
    def rename(self, asset_id, name): self.api(f'repos/{REPO}/releases/assets/{asset_id}','PATCH',dict(name=name))
    def replace(self, name, path, asset):
        if asset:
            fresh = self.api(f'repos/{REPO}/releases/assets/{asset["id"]}')
            if any(fresh.get(k) != asset.get(k) for k in ('id','name','size','digest','state')):
                raise ValueError('Alias identity changed before replacement')
            self.api(f'repos/{REPO}/releases/assets/{asset["id"]}','DELETE')
        self.upload(path)
    def commit(self, manifest, text):
        if self.release()['tag_name'] != TAG:
            # This tag is only a carrier locator; manifest sourceRevision identifies
            # each subsequent build. Never move the tag as a build-version proxy.
            raw = subprocess.run(['gh','api',f'repos/{REPO}/git/ref/tags/{TAG}'],capture_output=True,text=True)
            if raw.returncode:
                if 'HTTP 404' not in raw.stderr: raise ValueError('Cannot inspect current carrier tag')
                self.api(f'repos/{REPO}/git/refs','POST',dict(ref='refs/tags/'+TAG,sha=manifest['sourceRevision']))
            else:
                ref=json.loads(raw.stdout)
                if ref['object']['type']!='commit' or ref['object']['sha']!=manifest['sourceRevision']:
                    raise ValueError('Existing current carrier tag differs from the first build')
        self.api(f'repos/{REPO}/releases/{self.id}','PATCH',dict(tag_name=TAG,
                 target_commitish=manifest['sourceRevision'],name='Legend of Dragoon: Definitive — Current',
                 body=text,draft=False,prerelease=False,make_latest='true'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-sha',required=True);parser.add_argument('--run',type=int,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--initialize-release-id',type=int)
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    with publication_lock():
        subprocess.run(['python3',str(Path(__file__).with_name('stage-current-publication.py')),
                        '--source-sha',args.source_sha,'--run',str(args.run),'--output',str(args.output)],check=True)
        inputs=json.loads((args.output/'publication-inputs.json').read_text())
        server=GitHub(inputs['baselineRelease']['id'])
        latest=server.api(f'repos/{REPO}/releases/latest')
        if latest['id']!=server.id:raise ValueError('Carrier is no longer the latest release')
        if inputs['baselineRelease']['tag']!=TAG and args.initialize_release_id!=server.id:
            raise ValueError('Initial migration requires the exact current release ID')
        # Archive identity and its closed managed inventory are checked again by
        # the stager against fresh successful CI and the server artifact digests.
        result=publish(inputs,args.output,server)
        if server.api(f'repos/{REPO}/releases/latest')['id']!=server.id:
            raise ValueError('Published carrier is not latest; do not clean up')
        (args.output/'publication-result.json').write_bytes(canonical(result)+b'\n')
        print(json.dumps({k:result[k] for k in ('result','unchanged','uploadedBytes','reusedBytes') if k in result},indent=2))


if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError,subprocess.SubprocessError) as error:
        raise SystemExit('Publication stopped: '+str(error))
