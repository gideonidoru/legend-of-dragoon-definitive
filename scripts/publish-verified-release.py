#!/usr/bin/env python3
"""Publish only after draft-aware lookup, build correlation and every asset check succeeds.

The CI package verifier remains required. This gate prevents a failed lookup/check
from falling through into publication; it never creates or overwrites an asset.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
import tempfile
from package_validation import verify_package, verify_installer, inventory, file_delivery_assets, properties

REPO = 'gideonidoru/legend-of-dragoon-definitive'
NAMES = {'Install-Definitive.desktop', 'Install-Definitive.sh', 'Definitive-Installer.zip',
         'Legend-of-Dragoon-Definitive-linux-x64.zip', 'Legend-of-Dragoon-Definitive-macos-arm64.zip'}


def gh(*arguments):
    return subprocess.check_output(['gh', *arguments], text=True, stderr=subprocess.PIPE)


def verify_ci_artifacts(run_id, source, expected):
    pages = json.loads(gh('api', f'repos/{REPO}/actions/runs/{run_id}/artifacts?per_page=100', '--paginate', '--slurp'))
    artifacts = [a for page in pages for a in page['artifacts']]
    for artifact_name, names in {
        'delivery-ubuntu-24.04': {'Definitive-Installer.zip', 'Install-Definitive.sh', 'Install-Definitive.desktop', 'Legend-of-Dragoon-Definitive-linux-x64.zip'},
        'delivery-macos-15': {'Legend-of-Dragoon-Definitive-macos-arm64.zip'},
    }.items():
        matched = [a for a in artifacts if a['name'] == artifact_name]
        if len(matched) != 1:
            raise ValueError('Required CI artifact is missing or ambiguous: ' + artifact_name)
        artifact = matched[0]
        if artifact['expired'] or artifact['workflow_run']['id'] != int(run_id) or artifact['workflow_run']['head_sha'] != source:
            raise ValueError('CI artifact does not belong to the approved source/run')
        platform = 'linux-x64' if 'ubuntu' in artifact_name else 'macos-arm64'
        contents_name = 'Definitive-Contents-' + platform + '.zip'
        if contents_name in expected: names = names | {contents_name}
        with tempfile.TemporaryFile() as output:
            subprocess.run(['gh', 'api', f'repos/{REPO}/actions/artifacts/{artifact["id"]}/zip'],
                           stdout=output, stderr=subprocess.PIPE, check=True, timeout=1800)
            output.seek(0)
            if artifact.get('digest') != 'sha256:' + hashlib.file_digest(output, 'sha256').hexdigest():
                raise ValueError('Downloaded CI artifact digest mismatch')
            output.seek(0)
            with zipfile.ZipFile(output) as archive:
                entries = inventory(archive)
                if contents_name not in expected and any(n.split('/')[-1] == contents_name for n in entries): raise ValueError('CI file delivery inventory cannot be omitted from publication')
                if contents_name in expected:
                    candidates = [e for n, e in entries.items() if n.split('/')[-1] == contents_name and not e.is_dir()]
                    if len(candidates) != 1 or candidates[0].file_size > 8 * 1024**2: raise ValueError('Missing bounded CI contents inventory')
                    data = archive.read(candidates[0])
                    if (len(data), hashlib.sha256(data).hexdigest()) != expected[contents_name]: raise ValueError('Contents inventory differs from approved CI bytes')
                    import io
                    with zipfile.ZipFile(io.BytesIO(data)) as contents:
                        hashes = properties(contents.read('definitive-files.properties'))
                    names |= {'file-' + digest for digest in hashes.values()}
                for name in names:
                    candidates = [e for n, e in entries.items() if n.split('/')[-1] == name and not e.is_dir()]
                    if len(candidates) != 1:
                        raise ValueError('Approved CI upload is missing or ambiguous: ' + name)
                    entry = candidates[0]
                    with archive.open(entry) as stream:
                        actual = entry.file_size, hashlib.file_digest(stream, 'sha256').hexdigest()
                    if actual != expected[name]:
                        raise ValueError('Upload bytes differ from the approved CI artifact: ' + name)


def verify_release(release, tag, source, expected):
    if release['tagName'] != tag or release['targetCommitish'] != source:
        raise ValueError('Release tag/source does not match the checked build')
    assets = release['assets']
    if len(assets) != len(expected) or {a['name'] for a in assets} != set(expected):
        raise ValueError('Release assets are missing, duplicated or unexpected')
    download_tags = {tag}
    # GitHub gives an unpublished draft a temporary HTML/download tag. Bind it
    # to this exact draft's URL; published assets must use the approved tag.
    if release['isDraft']:
        temporary = re.fullmatch(r'https://github\.com/' + re.escape(REPO) + r'/releases/tag/(untagged-[a-f0-9]+)', release['url'])
        if temporary:download_tags.add(temporary[1])
    for asset in assets:
        size, digest = expected[asset['name']]
        if asset['state'] != 'uploaded' or (asset['size'], asset['digest']) != (size, 'sha256:' + digest):
            raise ValueError('Release checksum/size/state mismatch: ' + asset['name'])
        if asset['url'] not in {f'https://github.com/{REPO}/releases/download/{t}/{asset["name"]}' for t in download_tags}:
            raise ValueError('Unexpected release download URL')


def verify_tag(tag, source, required=False):
    pages = json.loads(gh('api', f'repos/{REPO}/git/matching-refs/tags/{tag}', '--paginate', '--slurp'))
    refs = [ref for page in pages for ref in page if ref['ref'] == 'refs/tags/' + tag]
    if not refs:
        if required:raise ValueError('Published tag is missing')
        return
    if len(refs) != 1:raise ValueError('Ambiguous tag reference')
    target = refs[0]['object']
    for depth in range(16):
        if target['type'] == 'commit':
            if target['sha'] != source:raise ValueError('Actual Git tag points to another source commit')
            return
        if target['type'] != 'tag' or not re.fullmatch(r'[a-f0-9]{40}', target['sha']):
            raise ValueError('Unsupported tag target')
        target = json.loads(gh('api', f'repos/{REPO}/git/tags/{target["sha"]}'))['object']
    raise ValueError('Annotated tag chain is too deep')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--run', required=True, help='Successful hosted build ID for this source')
    parser.add_argument('--assets-dir', type=Path, required=True, help='Five upload inputs and SHA256SUMS')
    parser.add_argument('--notes-file', type=Path, required=True)
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._-]+', args.tag) or not re.fullmatch(r'[a-f0-9]{40}', args.source_sha) or not args.run.isdecimal():
        raise ValueError('Invalid release tag, source SHA or build ID')
    if not args.notes_file.is_file():raise ValueError('Release notes are missing')
    names = set(NAMES)
    has_contents = any(p.name.startswith('Definitive-Contents-') for p in args.assets_dir.iterdir())
    if has_contents:
        blobs = {}
        for platform in ('linux-x64', 'macos-arm64'):
            name = f'Definitive-Contents-{platform}.zip'
            names.add(name)
            for blob, identity in file_delivery_assets(args.assets_dir / f'Legend-of-Dragoon-Definitive-{platform}.zip', args.assets_dir / name).items():
                if blob in blobs and blobs[blob] != identity: raise ValueError('Conflicting platform blob identity')
                blobs[blob] = identity
        names.update(blobs)
    if len(names) + 1 > 1000 or {p.name for p in args.assets_dir.iterdir()} != names | {'SHA256SUMS'}:
        raise ValueError('Release staging must contain exactly the complete approved upload inventory')
    expected = {}
    for name in names | {'SHA256SUMS'}:
        path = args.assets_dir / name
        if path.is_symlink() or not path.is_file():raise ValueError('Invalid upload file: ' + name)
        with path.open('rb') as stream:digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        expected[name] = (path.stat().st_size, digest)
    sums = (args.assets_dir / 'SHA256SUMS').read_text().splitlines()
    if len(sums) != len(names) or set(sums) != {expected[n][1] + '  ' + n for n in names}:
        raise ValueError('SHA256SUMS does not match the complete upload set')
    if has_contents and any(expected[name] != identity for name, identity in blobs.items()): raise ValueError('Per-file blob checksum or size mismatch')
    for platform in ('linux-x64', 'macos-arm64'):
        verify_package(args.assets_dir / f'Legend-of-Dragoon-Definitive-{platform}.zip', platform, args.source_sha, args.tag)
    verify_installer(args.assets_dir / 'Definitive-Installer.zip')
    script = (args.assets_dir / 'Install-Definitive.sh').read_text()
    if f'TAG={args.tag}\n' not in script or f'EXPECTED={expected["Definitive-Installer.zip"][1]}\n' not in script:
        raise ValueError('Portable entry point has stale tag/checksum')
    desktop = (args.assets_dir / 'Install-Definitive.desktop').read_text()
    if expected['Install-Definitive.sh'][1] not in desktop or f'/releases/download/{args.tag}/Install-Definitive.sh' not in desktop:
        raise ValueError('Desktop entry has stale source/checksum')
    if gh('api', 'user', '--jq', '.login').strip() != 'gideonidoru':raise ValueError('Wrong GitHub account')
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{args.run}'))
    if any(run.get(k) != v for k, v in {'status': 'completed', 'conclusion': 'success', 'head_sha': args.source_sha, 'path': '.github/workflows/build.yml'}.items()):
        raise ValueError('Required packaging workflow has not passed for this exact source')
    jobs = json.loads(gh('api', f'repos/{REPO}/actions/runs/{args.run}/jobs?per_page=100'))['jobs']
    required = {'Original synthetic visual fixtures', 'Linux x64 / Steam Deck package', 'macOS package'}
    checked = [j for j in jobs if j['name'] in required]
    if len(checked) != len(required) or {j['name'] for j in checked} != required or any(j['status'] != 'completed' or j['conclusion'] != 'success' for j in checked):
        raise ValueError('Required packaging/test jobs are missing or did not pass')
    verify_ci_artifacts(args.run, args.source_sha, expected)
    def read():return json.loads(gh('release', 'view', args.tag, '-R', REPO, '--json', 'tagName,targetCommitish,isDraft,url,assets'))
    release = read()
    verify_release(release, args.tag, args.source_sha, expected)
    verify_tag(args.tag, args.source_sha, required=not release['isDraft'])
    if args.check_only:
        print('PASS: build source, entry checksums and all uploaded assets; no publication requested')
        return
    if not release['isDraft']:
        print('PASS: identical verified release is already public; no changes made')
        return
    gh('release', 'edit', args.tag, '-R', REPO, '--notes-file', str(args.notes_file), '--draft=false')
    published = read()
    verify_release(published, args.tag, args.source_sha, expected)
    verify_tag(args.tag, args.source_sha, required=True)
    if published['isDraft']:raise ValueError('Publication was not confirmed')
    print(f'Published verified release: https://github.com/{REPO}/releases/tag/{args.tag}')


if __name__ == '__main__':
    try:main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, subprocess.CalledProcessError, subprocess.TimeoutExpired) as failure:
        print('Publication stopped: ' + str(failure), file=sys.stderr)
        raise SystemExit(1)
