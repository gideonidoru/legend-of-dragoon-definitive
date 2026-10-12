"""Source-bound member plans for the optional format-2 production publisher.

CI stages ID-free plans. Only the serialized publisher binds final carrier IDs.
Existing whole-file delivery remains available for format-1 publication.
"""
from pathlib import Path
import hashlib
import os
import subprocess
import tempfile
import zipfile
from archive_assembly import (FileIdentity, AssemblyPlan, plan_files,
    load_recipe, verified_recipe_ranges, prove_plan, MAX_RECIPE_BYTES, managed_path, verify_file)
from current_publication import (canonical, digest, parse_body, check_asset,
    index_assets, TAG, fmv_contract, unique_object)
from package_validation import properties, inventory
import json

MIN_ARCHIVE_BYTES = 1024**2
PLAN_FIELDS = {'format', 'sourceRevision', 'platform', 'releaseTag', 'sources', 'files'}


def selected_paths(files):
    result = set()
    for row in files:
        path = row['path']
        if row['size'] >= MIN_ARCHIVE_BYTES and path.lower().endswith(('.jar', '.zip')):
            try: managed_path(path)
            except ValueError: continue  # First wire format uses printable ASCII.
            result.add(path)
    return result


def validate_plan(value, source, platform, files):
    if not isinstance(value, dict) or set(value) != PLAN_FIELDS or type(value['format']) is not int or value['format'] != 1:
        raise ValueError('Invalid ID-free assembly plan')
    if (value['sourceRevision'], value['platform'], value['releaseTag']) != (source, platform, TAG):
        raise ValueError('Assembly plan provenance differs')
    if not isinstance(value['sources'], dict) or not value['sources']:
        raise ValueError('Missing assembly plan sources')
    if any(not isinstance(row, dict) or set(row) != {'bytes'} for row in value['sources'].values()):
        raise ValueError('Unexpected ID in CI assembly plan')
    # The already approved recipe validator enforces all geometry/wire limits.
    bound = dict(value, sources={sha:dict(row, id=str(i + 1)) for i,(sha,row) in enumerate(sorted(value['sources'].items()))})
    from archive_assembly import validate_recipe
    validate_recipe(bound)
    inventory = {row['path']:row for row in files}
    if len(inventory) != len(files) or set(value['files']) != selected_paths(files):
        raise ValueError('Incomplete or unexpected large archive plan')
    for name, rows in value['files'].items():
        if sum(row['bytes'] for row in rows) != inventory[name]['size']:
            raise ValueError('Assembly plan length differs from contents')
    return value


def load_plan(data, source, platform, files):
    if not isinstance(data, bytes) or len(data) > MAX_RECIPE_BYTES:
        raise ValueError('Assembly plan exceeds wire budget')
    value = json.loads(data, object_pairs_hook=unique_object)
    validate_plan(value, source, platform, files)
    if canonical(value) != data:
        raise ValueError('Assembly plan must be canonical')
    return value


def fetch_asset(asset, directory):
    """Download a verified immutable input; no release mutation or alias trust."""
    directory=Path(directory)
    if any(parent.is_symlink() for parent in (directory,*directory.parents)):raise ValueError('Linked publication cache')
    path = directory / asset['name']
    if path.exists() or path.is_symlink():
        if path.is_symlink() or (path.stat().st_size, digest(path)) != (asset['size'], asset['digest'][7:]):
            raise ValueError('Cached publication source differs')
        return path
    with tempfile.NamedTemporaryFile(dir=directory, prefix='.member-source-') as temp:
        subprocess.run(['curl','--fail','--location','--proto','=https','--proto-redir','=https',
            '--connect-timeout','20','--max-time','1800','--max-filesize',str(asset['size']),
            '--retry','2','--silent','--show-error','--output',temp.name,asset['browser_download_url']], check=True, timeout=1810)
        if (Path(temp.name).stat().st_size, digest(temp.name)) != (asset['size'], asset['digest'][7:]):
            raise ValueError('Downloaded publication source differs')
        os.link(temp.name, path)
    return path


def baselines(current, platform, paths, fetch):
    """Only the committed pointer and fresh exact source IDs authorize reuse."""
    release = current['release']
    if release is None: return {}, None
    pointer = parse_body(release.get('body') or '', release['id'], required=False)
    if pointer is None: return {}, None
    if release['tag_name'] != TAG: raise ValueError('Member baseline must use current carrier')
    assets = index_assets(current['assets'])
    record = pointer['platforms'][platform]['contents']
    asset = check_asset(assets.get(record['name'], {}), record['name'], record['bytes'], record['sha256'])
    if str(asset['id']) != record['id']: raise ValueError('Baseline contents ID differs')
    with zipfile.ZipFile(fetch(asset)) as archive:
        entries=inventory(archive,8*1024**2,3)
        if set(entries)!={'definitive-package.properties','definitive-files.properties','definitive-sizes.properties'} or any(row.file_size>4*1024**2 for row in entries.values()):raise ValueError('Unbounded baseline contents')
        hashes = properties(archive.read('definitive-files.properties'))
        sizes = properties(archive.read('definitive-sizes.properties'))
        metadata = properties(archive.read('definitive-package.properties'))
    if (metadata.get('sourceRevision'), metadata.get('platform'), metadata.get('releaseTag')) != (pointer['sourceRevision'], platform, TAG) or set(hashes) != set(sizes):
        raise ValueError('Baseline contents provenance differs')
    if pointer['format'] == 2:
        record = pointer['platforms'][platform]['assembly']
        asset = check_asset(assets.get(record['name'], {}), record['name'], record['bytes'], record['sha256'])
        if str(asset['id']) != record['id']: raise ValueError('Baseline recipe ID differs')
        recipe = load_recipe(Path(fetch(asset)).read_bytes())
        if (recipe['sourceRevision'],recipe['platform'],recipe['releaseTag']) != (pointer['sourceRevision'],platform,TAG):
            raise ValueError('Baseline recipe provenance differs')
        for name, rows in recipe['files'].items():
            if name not in hashes or sum(row['bytes'] for row in rows) != int(sizes[name]):
                raise ValueError('Baseline recipe/contents disagree')
        identities, ids = {}, {}
        for sha, row in recipe['sources'].items():
            name = 'file-' + sha
            asset = check_asset(assets.get(name, {}), name, row['bytes'], sha)
            if str(asset['id']) != row['id']: raise ValueError('Baseline source ID differs')
            identities[sha] = FileIdentity(fetch(asset), sha, row['bytes']); ids[sha] = str(asset['id'])
        catalog=verified_recipe_ranges(recipe, identities, ids)
        files=[dict(path=name,sha256=sha,size=int(sizes[name]),assetName='file-'+sha) for name,sha in hashes.items()]
        prove_draft(recipe,files,{sha:identity.path for sha,identity in identities.items()})
        return identities, catalog
    identities = {}
    for name in sorted(paths & set(hashes)):
        sha, size = hashes[name], int(sizes[name]); asset_name = 'file-' + sha
        asset = check_asset(assets.get(asset_name, {}), asset_name, size, sha)
        identities[sha] = verify_file(FileIdentity(fetch(asset), sha, size))
    return identities, None


def prepare(ctx, expected, files, paths, current, directory, fetch=None):
    directory = Path(directory)
    if any(parent.is_symlink() for parent in (directory,*directory.parents)):raise ValueError('Linked member source cache')
    directory.mkdir(parents=True, exist_ok=True)
    selected = selected_paths(files)
    if not selected: raise ValueError('Format 2 requires a bounded large archive plan')
    fetch = fetch or (lambda asset: fetch_asset(asset, directory))
    old, ranges = baselines(current, ctx['platform'], selected, fetch)
    targets = {row['path']:FileIdentity(paths[row['assetName']], row['sha256'], row['size']) for row in files if row['path'] in selected}
    plan = plan_files(targets, old, directory, ranges)
    draft = dict(format=1, sourceRevision=ctx['sourceSha'], platform=ctx['platform'], releaseTag=TAG,
        sources={sha:dict(bytes=size) for sha,size in plan.sources.items()}, files=plan.files)
    validate_plan(draft, ctx['sourceSha'], ctx['platform'], files)
    data = canonical(draft); sha = hashlib.sha256(data).hexdigest()
    name = 'assembly-plan-' + ctx['platform'] + '-' + sha + '.json'
    path = directory / name
    if path.exists():
        if path.is_symlink() or path.read_bytes() != data: raise ValueError('Plan cache differs')
    else: path.write_bytes(data)
    expected, paths = dict(expected), dict(paths)
    ordinary = {row['assetName'] for row in files if row['path'] not in selected}
    for row in files:
        if row['path'] in selected and row['assetName'] not in ordinary:
            expected.pop(row['assetName']); paths.pop(row['assetName'])
    for source, size in plan.sources.items():
        asset_name = 'file-' + source; expected[asset_name] = (size, source)
        paths[asset_name] = plan.uploads.get(source, old.get(source)).path
    expected[name] = (len(data),sha); paths[name] = path
    # The 18 managed film blobs are also the source inputs, without a ZIP alias.
    expected.pop('FMVHD-v0.1.0-videos.zip', None); paths.pop('FMVHD-v0.1.0-videos.zip', None)
    return expected, paths, dict(name=name, bytes=len(data), sha256=sha), draft


def film_map(manifest_path, files):
    contract = fmv_contract.contract(Path(manifest_path))
    inventory = {row['path']:row for row in files}
    for path,key in [('gfx/fmvhd/NOTICE.txt','noticeSha256'),('gfx/fmvhd/provenance.json','provenanceSha256')]:
        row=inventory.get(path)
        if row is None or row['sha256']!=contract[key] or row['size']>1024**2:raise ValueError('Film attribution/provenance differs from source contract')
    result = {}
    expected_videos = set()
    for name in fmv_contract.NAMES:
        sha = contract[name+'.sha256']; size = int(contract[name+'.bytes'])
        path = 'gfx/fmvhd/videos/' + sha + '.mp4'; expected_videos.add(path)
        row = inventory.get(path)
        if row is None or (row['sha256'],row['size']) != (sha,size):
            raise ValueError('Film contract differs from complete contents')
        result[name] = dict(sha256=sha, bytes=size, name='file-' + sha)
    if {path for path in inventory if path.startswith('gfx/fmvhd/videos/')} != expected_videos:
        raise ValueError('Unexpected managed movie')
    return result


def prove_draft(draft, files, source_paths):
    targets = {row['path']:FileIdentity(Path('/unopened-inventory-target'), row['sha256'], row['size']) for row in files if row['path'] in draft['files']}
    sources = {sha:FileIdentity(source_paths[sha],sha,row['bytes']) for sha,row in draft['sources'].items()}
    plan = AssemblyPlan({sha:row['bytes'] for sha,row in draft['sources'].items()},draft['files'],{},targets)
    return prove_plan(plan, sources)
