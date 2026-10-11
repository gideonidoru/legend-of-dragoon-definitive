"""Immutable release files and a small, committed current-revision manifest.

The carrier release is storage, not a build identity. A generation is made current
only by replacing its body after the complete referenced file closure is verified.
"""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import re
import zipfile
from package_validation import properties, file_delivery_assets, verify_package, verify_installer

REPO = 'gideonidoru/legend-of-dragoon-definitive'
TAG = 'definitive-current'
START = 'DEFINITIVE_CURRENT_V1\n'
END = '\nEND_DEFINITIVE_CURRENT_V1\n'
MAX_MANIFEST = 8192
MAX_NUMBER = 2**53 - 1
PLATFORMS = ('linux-x64', 'macos-arm64')
FIELDS = {'format', 'generation', 'sourceRevision', 'buildRun', 'releaseId',
          'releaseTag', 'publishedAt', 'platforms', 'installer', 'entry', 'fmvSource'}


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate manifest key: ' + key)
        result[key] = value
    return result


def number(value, label):
    if type(value) is not int or not 1 <= value <= MAX_NUMBER:
        raise ValueError('Invalid ' + label)
    return value


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r'[1-9][0-9]{0,15}', value) or int(value) > MAX_NUMBER:
        raise ValueError('Invalid release/asset ID')
    return value


def timestamp(value):
    if not isinstance(value, str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', value):
        raise ValueError('Invalid logical publication time')
    datetime.fromisoformat(value.replace('Z', '+00:00'))
    return value


def descriptor_name(kind, sha, platform=None):
    if not isinstance(sha, str) or not re.fullmatch(r'[a-f0-9]{64}', sha):
        raise ValueError('Invalid asset checksum')
    if kind in ('package', 'contents') and platform in PLATFORMS:
        return f'{kind}-{platform}-{sha}.zip'
    if kind == 'installer':
        return f'installer-{sha}.zip'
    if kind == 'entry':
        return f'entry-{sha}.sh'
    if kind == 'fmvSource':
        return f'source-fmvhd-{sha}.zip'
    raise ValueError('Invalid asset kind/platform')


def descriptor(asset, kind, platform=None):
    result = {'name': asset['name'], 'id': str(asset['id']), 'bytes': asset['size'],
              'sha256': asset['digest'].removeprefix('sha256:')}
    check_descriptor(result, kind, platform)
    return result


def check_descriptor(value, kind, platform=None):
    if not isinstance(value, dict) or set(value) != {'name', 'id', 'bytes', 'sha256'}:
        raise ValueError('Invalid asset descriptor')
    identifier(value['id'])
    number(value['bytes'], 'asset size')
    limit = 8 * 1024**2 if kind == 'contents' else 2 * 1024**3
    if value['bytes'] > limit or value['name'] != descriptor_name(kind, value['sha256'], platform):
        raise ValueError('Unbounded or incorrectly named asset')


def validate_manifest(value, release_id=None):
    if not isinstance(value, dict) or set(value) != FIELDS or type(value['format']) is not int or value['format'] != 1:
        raise ValueError('Unknown/incomplete current manifest schema')
    number(value['generation'], 'generation')
    number(value['buildRun'], 'build run')
    identifier(value['releaseId'])
    if release_id is not None and value['releaseId'] != str(release_id):
        raise ValueError('Manifest belongs to another release')
    if value['releaseTag'] != TAG or not isinstance(value['sourceRevision'], str) or not re.fullmatch(r'[a-f0-9]{40}', value['sourceRevision']):
        raise ValueError('Invalid carrier/source identity')
    timestamp(value['publishedAt'])
    if not isinstance(value['platforms'], dict) or set(value['platforms']) != set(PLATFORMS):
        raise ValueError('Both supported platform inventories are required')
    for platform in PLATFORMS:
        records = value['platforms'][platform]
        if not isinstance(records, dict) or set(records) != {'package', 'contents'}:
            raise ValueError('Incomplete platform descriptor')
        for kind in ('package', 'contents'):
            check_descriptor(records[kind], kind, platform)
    for kind in ('installer', 'entry', 'fmvSource'):
        check_descriptor(value[kind], kind)
    if len(canonical(value)) > MAX_MANIFEST:
        raise ValueError('Current manifest is too large')
    return value


def parse_body(body, release_id=None, required=True):
    if not isinstance(body, str) or not body.startswith(START):
        if required or 'DEFINITIVE_CURRENT_V1' in str(body):
            raise ValueError('Current manifest is absent or misplaced')
        return None
    if body.splitlines().count(START.rstrip('\n')) != 1 or body.count(END) != 1:
        raise ValueError('Ambiguous current manifest framing')
    text, notes = body[len(START):].split(END, 1)
    if not text.isascii() or len(text) > MAX_MANIFEST or len(body) > 12000:
        raise ValueError('Oversized/non-ASCII current manifest')
    value = json.loads(text, object_pairs_hook=unique_object)
    validate_manifest(value, release_id)
    if text.encode('ascii') != canonical(value):
        raise ValueError('Manifest JSON is not canonical')
    return value


def body(value, notes=''):
    validate_manifest(value)
    result = START + canonical(value).decode('ascii') + END + notes
    if len(result) > 12000 or START in notes or END in notes:
        raise ValueError('Release notes conflict with the current manifest')
    return result


def index_assets(assets):
    result = {}
    ids = set()
    for asset in assets:
        if not isinstance(asset, dict) or asset.get('name') in result or str(asset.get('id')) in ids:
            raise ValueError('Ambiguous release asset identity')
        identifier(str(asset.get('id')))
        result[asset['name']] = asset
        ids.add(str(asset['id']))
    return result


def check_asset(asset, name, size, sha, tag=TAG):
    if asset.get('name') != name or asset.get('state') != 'uploaded' or asset.get('size') != size or asset.get('digest') != 'sha256:' + sha:
        raise ValueError('Release file metadata differs: ' + name)
    identifier(str(asset.get('id')))
    if asset.get('browser_download_url') != f'https://github.com/{REPO}/releases/download/{tag}/{name}':
        raise ValueError('Release file origin differs: ' + name)
    return asset


def check_references(value, assets, tag=TAG):
    validate_manifest(value)
    indexed = index_assets(assets)
    data = canonical(value)
    sha = hashlib.sha256(data).hexdigest()
    name = 'manifest-' + sha + '.json'
    check_asset(indexed.get(name, {}), name, len(data), sha, tag)
    for platform in PLATFORMS:
        for record in value['platforms'][platform].values():
            check_asset(indexed.get(record['name'], {}), record['name'], record['bytes'], record['sha256'], tag)
            if str(indexed[record['name']]['id']) != record['id']:
                raise ValueError('Platform asset ID differs')
    for kind in ('installer', 'entry', 'fmvSource'):
        record = value[kind]
        check_asset(indexed.get(record['name'], {}), record['name'], record['bytes'], record['sha256'], tag)
        if str(indexed[record['name']]['id']) != record['id']:
            raise ValueError('Current asset ID differs')
    return sha


def reuse_plan(expected, assets, tag=TAG):
    """Missing files upload; an existing immutable hash name never gets overwritten."""
    indexed = index_assets(assets)
    reuse, upload = {}, {}
    for name, record in expected.items():
        size, sha = record
        asset = indexed.get(name)
        if asset is None:
            upload[name] = record
        else:
            check_asset(asset, name, size, sha, tag)
            reuse[name] = str(asset['id'])
    return reuse, upload


def closure(value, packages):
    """All references, including every module/video/Env pack, before deletion."""
    validate_manifest(value)
    required = {}
    for platform in PLATFORMS:
        package, contents = packages[platform]
        verify_package(package, platform, value['sourceRevision'], TAG, allow_file_delivery=True)
        for name, identity in file_delivery_assets(package, contents, include_env_aliases=False).items():
            if name in required and required[name] != identity:
                raise ValueError('Cross-platform payload identity conflict')
            required[name] = identity
        for kind in ('package', 'contents'):
            record = value['platforms'][platform][kind]
            path = package if kind == 'package' else contents
            if (Path(path).stat().st_size, digest(path)) != (record['bytes'], record['sha256']):
                raise ValueError('Platform descriptor differs from verified inventory')
            required[record['name']] = (record['bytes'], record['sha256'])
    for kind in ('installer', 'entry', 'fmvSource'):
        record = value[kind]
        required[record['name']] = (record['bytes'], record['sha256'])
    data = canonical(value)
    sha = hashlib.sha256(data).hexdigest()
    required['manifest-' + sha + '.json'] = (len(data), sha)
    return required
