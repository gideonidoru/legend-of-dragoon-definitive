#!/usr/bin/env python3
"""Bind full verified build inventory to a small receipt and missing-only CI payload.

No publication occurs here. Current release metadata permits reuse only when its
uploaded asset identity exactly matches bytes independently hashed in this build.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import secrets
import stat
import subprocess
import time
import zipfile

from package_validation import file_delivery_assets, properties, verify_package, verify_installer

MAX_METADATA = 4 * 1024**2
MAX_ASSETS = 1000
MAX_ASSET_BYTES = 2 * 1024**3
PLATFORMS = ('linux-x64', 'macos-arm64')
FMV_SOURCE = 'FMVHD-v0.1.0-videos.zip'
SOURCE = re.compile(r'[a-f0-9]{40}')
SHA = re.compile(r'[a-f0-9]{64}')
REPOSITORY = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+')


def bounded_json(path):
    with Path(path).open('rb') as stream:
        data = stream.read(MAX_METADATA + 1)
    if len(data) > MAX_METADATA:
        raise ValueError('Publication metadata exceeds bound')
    return json.loads(data)


def identity(path):
    path = Path(path)
    if any(parent.is_symlink() for parent in (path, *path.parents)):
        raise ValueError('Publication input must not be linked')
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), 'rb') as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or before.st_size > MAX_ASSET_BYTES:
            raise ValueError('Invalid publication payload')
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        after = os.fstat(stream.fileno())
        if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
            raise ValueError('Publication input changed while hashing')
    return before.st_size, digest


def context(source, run, attempt, repository, platform, tag):
    if (not SOURCE.fullmatch(source) or platform not in PLATFORMS or tag != 'definitive-current'
            or not REPOSITORY.fullmatch(repository) or any(p in ('.', '..') for p in repository.split('/'))
            or type(run) is not int or run < 1
            or type(attempt) is not int or attempt < 1):
        raise ValueError('Invalid source/run/channel identity')
    return dict(format=1, sourceSha=source, runId=run, runAttempt=attempt,
                repository=repository, platform=platform, tag=tag)


def current_assets(metadata):
    if not isinstance(metadata, dict) or set(metadata) != {'release', 'assets'} or not isinstance(metadata['assets'], list):
        raise ValueError('Incomplete current release reference')
    release = metadata['release']
    if release is None:
        if metadata['assets']:
            raise ValueError('Assets without current release')
        return None, {}
    if (not isinstance(release, dict) or type(release.get('id')) is not int or release['id'] < 1
            or not isinstance(release.get('tag_name'), str) or not release['tag_name']
            or release.get('draft') is not False or release.get('prerelease') is not False
            or not isinstance(release.get('published_at'), str) or not release['published_at']):
        raise ValueError('Invalid current published release')
    if len(metadata['assets']) > MAX_ASSETS:
        raise ValueError('Too many current assets')
    assets = {}
    ids = set()
    for asset in metadata['assets']:
        if (not isinstance(asset, dict) or not isinstance(asset.get('name'), str)
                or not asset['name'] or len(asset['name']) > 255 or '/' in asset['name']
                or any(ord(c) < 32 for c in asset['name']) or asset['name'] in assets
                or type(asset.get('id')) is not int or asset['id'] < 1
                or asset['id'] in ids
                or type(asset.get('size')) is not int or not 0 <= asset['size'] <= MAX_ASSET_BYTES):
            raise ValueError('Malformed or duplicate current asset')
        assets[asset['name']] = asset
        ids.add(asset['id'])
    return dict(id=release['id'], tag=release['tag_name'], publishedAt=release['published_at']), assets


def collect(distributions, ctx):
    distributions = Path(distributions)
    platform = ctx['platform']
    package = distributions / f'Legend-of-Dragoon-Definitive-{platform}.zip'
    contents = distributions / f'Definitive-Contents-{platform}.zip'
    verify_package(package, platform, ctx['sourceSha'], ctx['tag'], allow_file_delivery=True)
    expected = file_delivery_assets(package, contents, include_env_aliases=False)
    files = []
    with zipfile.ZipFile(contents) as archive:
        hashes = properties(archive.read('definitive-files.properties'))
        sizes = properties(archive.read('definitive-sizes.properties'))
        for path in sorted(hashes):
            files.append(dict(path=path, sha256=hashes[path], size=int(sizes[path]), assetName='file-' + hashes[path]))
    paths = {}
    for name, (size, digest) in expected.items():
        if not name.startswith('file-'):
            raise ValueError('Only canonical blobs belong in delta inventory')
        path = distributions / 'files' / name
        if identity(path) != (size, digest):
            raise ValueError('Build blob differs from complete inventory')
        paths[name] = path
    for path in (package, contents):
        expected[path.name] = identity(path)
        paths[path.name] = path
    if platform == 'linux-x64':
        for name in ('Definitive-Installer.zip', 'Install-Definitive.sh', 'Install-Definitive.desktop'):
            path = distributions / name
            expected[name] = identity(path)
            paths[name] = path
        verify_installer(paths['Definitive-Installer.zip'])
        if 'bundled-mods/FMVHD-v0.1.0.jar' in hashes:
            path = distributions / FMV_SOURCE
            expected[path.name] = identity(path)
            lock = properties((Path(__file__).resolve().parents[1] / 'integrations/fmvhd/release.properties').read_bytes())
            if expected[path.name][1] != lock.get('sha256'):
                raise ValueError('FMV source input differs from exact source lock')
            paths[path.name] = path
    if len(expected) > MAX_ASSETS:
        raise ValueError('Full publication inventory exceeds asset bound')
    return expected, files, paths


def validate_reuse_name(asset):
    if 'reuseName' in asset and (asset.get('name') != FMV_SOURCE
            or asset.get('disposition') != 'reuse'
            or asset['reuseName'] != 'source-fmvhd-' + asset['sha256'] + '.zip'):
        raise ValueError('Only exact canonical FMV reuse names are permitted')


def verify_linux(receipt, ctx):
    if not isinstance(receipt, dict):
        raise ValueError('Missing Linux receipt')
    for key in ('format', 'sourceSha', 'runId', 'runAttempt', 'repository', 'tag'):
        if receipt.get(key) != ctx[key] or type(receipt.get(key)) is not type(ctx[key]):
            raise ValueError('Linux receipt belongs to another source/run')
    if receipt.get('platform') != 'linux-x64' or not isinstance(receipt.get('assets'), list) or not isinstance(receipt.get('files'), list):
        raise ValueError('Invalid Linux receipt')
    if len(receipt['assets']) > MAX_ASSETS or len(receipt['files']) > 30000:
        raise ValueError('Linux receipt exceeds inventory bounds')
    assets = {}
    for asset in receipt['assets']:
        if (not isinstance(asset, dict) or not isinstance(asset.get('name'), str) or asset['name'] in assets
                or not isinstance(asset.get('sha256'), str) or not SHA.fullmatch(asset['sha256'])
                or type(asset.get('size')) is not int or not 0 <= asset['size'] <= MAX_ASSET_BYTES
                or asset.get('disposition') not in ('upload', 'reuse')
                or asset['name'].startswith('file-') and asset['name'] != 'file-' + asset['sha256']):
            raise ValueError('Malformed Linux asset receipt')
        if asset['disposition'] == 'reuse' and (type(asset.get('assetId')) is not int or asset['assetId'] < 1
                or type(asset.get('releaseId')) is not int or asset['releaseId'] < 1):
            raise ValueError('Linux reuse lacks remote identity')
        validate_reuse_name(asset)
        assets[asset['name']] = asset
    files = {}
    for item in receipt['files']:
        if not isinstance(item, dict) or not isinstance(item.get('path'), str) or item['path'] in files:
            raise ValueError('Duplicate or malformed Linux file receipt')
        asset = assets.get(item.get('assetName'))
        if not asset or (item.get('size'), item.get('sha256')) != (asset['size'], asset['sha256']):
            raise ValueError('Linux file and blob identities disagree')
        files[item['path']] = item
    return assets, files


def platform_specific(path):
    return path == 'tools/7zz' or bool(re.fullmatch(
        r'libs/lwjgl(?:-(?:mimalloc|sdl|opengl|opengles|openal|opus|stb|assimp|shaderc|spvc))?'
        r'-[0-9]+(?:\.[0-9]+)*-natives-(?:linux|macos-arm64)\.jar', path))


def plan(ctx, expected, files, current, linux=None):
    release, existing = current_assets(current)
    linux_assets, linux_files = ({}, {}) if linux is None else verify_linux(linux, ctx)
    if ctx['platform'] == 'macos-arm64' and linux is None:
        raise ValueError('Mac delta requires verified same-run Linux receipt')
    for item in files:
        prior = linux_files.get(item['path'])
        if prior and not platform_specific(item['path']) and (item['size'], item['sha256']) != (prior['size'], prior['sha256']):
            raise ValueError('Shared platform file differs: ' + item['path'])
    assets = []
    for name, (size, digest) in sorted(expected.items()):
        asset = dict(name=name, size=size, sha256=digest)
        reuse_name = name
        if name == FMV_SOURCE:
            canonical = 'source-fmvhd-' + digest + '.zip'
            if canonical in existing:
                reuse_name = canonical
        remote = existing.get(reuse_name)
        if remote is not None:
            exact = remote.get('state') == 'uploaded' and remote.get('digest') == 'sha256:' + digest and remote['size'] == size
            if (name.startswith('file-') or reuse_name != name) and not exact:
                raise ValueError('Current immutable blob identity is corrupt: ' + reuse_name)
        else:
            exact = False
        assigned = linux_assets.get(name)
        if assigned:
            if (size, digest) != (assigned['size'], assigned['sha256']):
                raise ValueError('Same Linux asset has different identity')
            asset['disposition'] = 'linux'
        elif exact:
            asset.update(disposition='reuse', assetId=remote['id'], releaseId=release['id'])
            if reuse_name != name:
                asset['reuseName'] = reuse_name
        else:
            asset['disposition'] = 'upload'
        assets.append(asset)
    return dict(ctx, currentRelease=release, assets=assets, files=files,
                uploadPaths=['uploads/' + a['name'] for a in assets if a['disposition'] == 'upload'])


def stage(receipt, paths, output, receipt_path):
    output, receipt_path = Path(output), Path(receipt_path)
    encoded = (json.dumps(receipt, sort_keys=True, indent=2) + '\n').encode('ascii')
    if len(encoded) > MAX_METADATA - 4096:
        raise ValueError('Full receipt exceeds bounded artifact handoff')
    if output.exists() or output.is_symlink() or receipt_path.exists() or receipt_path.is_symlink():
        raise ValueError('Delta staging and receipt must be fresh')
    if any(p.is_symlink() for path in (output, receipt_path) for p in path.parents):
        raise ValueError('Delta output parents must not be linked')
    output.mkdir(parents=True)
    uploads = output / 'uploads'
    uploads.mkdir()
    created = []
    temporary_created = False
    try:
        for asset in receipt['assets']:
            if asset['disposition'] != 'upload':
                continue
            destination = uploads / asset['name']
            # Build output and delta staging share a filesystem. The full tests
            # keep their output, while a second multi-gigabyte copy is avoided.
            os.link(paths[asset['name']], destination, follow_symlinks=False)
            created.append(destination)
            if identity(destination) != (asset['size'], asset['sha256']):
                raise ValueError('Staged delta changed')
        receipt_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = receipt_path.with_name('.publication-receipt-' + secrets.token_hex(16))
        with temporary.open('xb') as stream:
            temporary_created = True
            if stream.write(encoded) != len(encoded):
                raise OSError('Short publication receipt write')
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, receipt_path, follow_symlinks=False)
    except BaseException:
        for path in created:
            path.unlink()
        uploads.rmdir()
        output.rmdir()
        raise
    finally:
        if temporary_created:
            temporary.unlink()


def verify_staged(receipt, ctx, expected, files, output):
    for key, value in ctx.items():
        if receipt.get(key) != value or type(receipt.get(key)) is not type(value):
            raise ValueError('Staged receipt identity changed')
    assets = receipt.get('assets')
    if not isinstance(assets, list) or len(assets) != len(expected):
        raise ValueError('Staged inventory changed')
    actual = {}
    uploads = set()
    for asset in assets:
        name = asset.get('name')
        if name not in expected or name in actual or asset.get('disposition') not in ('upload', 'reuse', 'linux'):
            raise ValueError('Invalid staged inventory')
        validate_reuse_name(asset)
        actual[name] = asset.get('size'), asset.get('sha256')
        if asset['disposition'] == 'upload':
            uploads.add(name)
            if identity(Path(output) / 'uploads' / name) != expected[name]:
                raise ValueError('Staged upload changed after checks')
    if actual != expected or receipt.get('files') != files or set(receipt.get('uploadPaths', [])) != {'uploads/' + n for n in uploads}:
        raise ValueError('Full build identity changed after receipt')
    if {p.name for p in (Path(output) / 'uploads').iterdir()} != uploads:
        raise ValueError('Unexpected staged upload')


def api(endpoint, missing=False):
    """Bound every gh response; never print credentials or captured API bodies."""
    with subprocess.Popen(['gh', 'api', '--include', endpoint], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as process:
        data = bytearray()
        deadline = time.monotonic() + 60
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0 or not selector.select(remaining):
                        raise ValueError('GitHub metadata request timed out')
                    chunk = os.read(process.stdout.fileno(), min(65536, MAX_METADATA + 1 - len(data)))
                    if not chunk:
                        break
                    data.extend(chunk)
                    if len(data) > MAX_METADATA:
                        raise ValueError('GitHub metadata response exceeds bound')
            code = process.wait(timeout=max(.01, deadline - time.monotonic()))
        finally:
            if process.poll() is None:
                process.kill()
    header, separator, body = data.replace(b'\r\n', b'\n').partition(b'\n\n')
    first = header.splitlines()[0] if header else b''
    status = re.fullmatch(rb'HTTP/[0-9.]+ ([0-9]{3})(?: .*)?', first)
    if not separator or status is None:
        raise ValueError('Missing GitHub response status')
    if missing and int(status[1]) == 404:
        return None
    if code != 0 or int(status[1]) != 200:
        raise ValueError('GitHub metadata request failed')
    return json.loads(body)


def fetch_current(repository):
    release = api(f'repos/{repository}/releases/latest', missing=True)
    if release is None:
        return dict(release=None, assets=[])
    if type(release.get('id')) is not int or release['id'] < 1:
        raise ValueError('Current release lacks identity')
    assets = []
    for page in range(1, 12):
        records = api(f'repos/{repository}/releases/{release["id"]}/assets?per_page=100&page={page}')
        if not isinstance(records, list) or len(records) > 100:
            raise ValueError('Malformed GitHub asset page')
        assets.extend(records)
        if len(assets) > MAX_ASSETS:
            raise ValueError('Too many current release assets')
        if len(records) < 100:
            break
    result = dict(release=release, assets=assets)
    current_assets(result)
    return result


def download_linux(ctx, directory, wait_seconds):
    directory = Path(directory)
    if directory.exists():
        raise ValueError('Linux receipt download directory must be fresh')
    deadline = time.monotonic() + wait_seconds
    name = f'publication-linux-x64-receipt-{ctx["runAttempt"]}'
    while True:
        run = api(f'repos/{ctx["repository"]}/actions/runs/{ctx["runId"]}')
        if run.get('head_sha') != ctx['sourceSha'] or run.get('run_attempt') != ctx['runAttempt']:
            raise ValueError('Linux artifact run/source changed')
        response = api(f'repos/{ctx["repository"]}/actions/runs/{ctx["runId"]}/artifacts?per_page=100')
        if not isinstance(response, dict) or response.get('total_count', 101) > 100 or not isinstance(response.get('artifacts'), list):
            raise ValueError('Ambiguous same-run artifact inventory')
        matches = [a for a in response['artifacts'] if a.get('name') == name]
        if matches:
            if len(matches) != 1 or matches[0].get('expired') is not False or not 0 < matches[0].get('size_in_bytes', 0) <= MAX_METADATA:
                raise ValueError('Invalid Linux receipt artifact')
            subprocess.run(['gh', 'run', 'download', str(ctx['runId']), '--repo', ctx['repository'], '--name', name, '--dir', str(directory)], check=True, timeout=120, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if set(p.name for p in directory.iterdir()) != {'publication-linux-x64.json'}:
                raise ValueError('Linux receipt artifact must contain only its receipt')
            receipt = bounded_json(directory / 'publication-linux-x64.json')
            verify_linux(receipt, ctx)
            return receipt
        if run.get('status') == 'completed' or time.monotonic() >= deadline:
            raise ValueError('Same-run Linux receipt did not arrive')
        time.sleep(min(10, max(0, deadline - time.monotonic())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('distributions', 'output-dir', 'receipt', 'current-metadata', 'linux-receipt', 'linux-download-dir'):
        parser.add_argument('--' + name, type=Path)
    parser.add_argument('--platform', choices=PLATFORMS, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--run-id', type=int, required=True)
    parser.add_argument('--run-attempt', type=int, required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--wait-seconds', type=int, default=900)
    parser.add_argument('--verify-staged', action='store_true')
    args = parser.parse_args()
    ctx = context(args.source_sha, args.run_id, args.run_attempt, args.repository, args.platform, args.tag)
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=Path(__file__).resolve().parents[1], text=True).strip()
    if source != ctx['sourceSha'] or not 0 <= args.wait_seconds <= 1200:
        raise ValueError('Checkout source or wait bound differs')
    if not all((args.distributions, args.output_dir, args.receipt)):
        raise ValueError('Supply distributions, staging output and receipt paths')
    if args.verify_staged:
        expected, files, paths = collect(args.distributions, ctx)
        verify_staged(bounded_json(args.receipt), ctx, expected, files, args.output_dir)
        print('Full publication receipt and staged delta remain exact after checks')
        return
    current = bounded_json(args.current_metadata) if args.current_metadata else fetch_current(ctx['repository'])
    linux = None
    if args.linux_receipt:
        linux = bounded_json(args.linux_receipt)
    elif args.platform == 'macos-arm64':
        if not args.linux_download_dir:
            raise ValueError('Mac requires a same-run Linux receipt handoff')
        linux = download_linux(ctx, args.linux_download_dir, args.wait_seconds)
    expected, files, paths = collect(args.distributions, ctx)
    receipt = plan(ctx, expected, files, current, linux)
    stage(receipt, paths, args.output_dir, args.receipt)
    print(json.dumps(dict(platform=ctx['platform'], assets=len(receipt['assets']), uploads=len(receipt['uploadPaths']), reused=sum(a['disposition'] == 'reuse' for a in receipt['assets']), linuxAssigned=sum(a['disposition'] == 'linux' for a in receipt['assets']))))


if __name__ == '__main__':
    main()
