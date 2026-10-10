"""Closed, bounded validation of release archives before publication.

The release gate also compares these bytes to hosted CI artifacts; self-reported
checksums alone are never treated as provenance.
"""
import hashlib
import re
import stat
import zipfile

ROOT = {'definitive-manager.jar', 'bootstrap-java', 'Install.sh', 'LICENSE', 'CREDITS',
        'credits.txt', 'README.md', 'log4j2.xml', 'gamecontrollerdb.txt'}
SUPPORT = {'gfx', 'patches', 'lang', 'libs', 'bundled-mods', 'tools'}
META = {'definitive-package.properties', 'definitive-files.properties'}


def inventory(archive, size_limit=32 * 1024**3, entry_limit=100000):
    entries = {}
    total = 0
    for entry in archive.infolist():
        name = entry.filename
        parts = name.rstrip('/').split('/')
        mode = entry.external_attr >> 16
        if (not name or name.startswith('/') or any(p in ('', '.', '..') for p in parts)
                or any(c in name for c in '\\:\n\r\0') or name in entries
                or stat.S_ISLNK(mode) or entry.flag_bits & 1):
            raise ValueError('Unsafe, duplicate or encrypted archive entry: ' + repr(name))
        if entry.file_size > 8 * 1024**3:
            raise ValueError('Archive entry exceeds the release size limit')
        total += entry.file_size
        if total > size_limit or len(entries) >= entry_limit:
            raise ValueError('Archive exceeds release inventory limits')
        entries[name] = entry
    return entries


def digest_entry(archive, entry):
    with archive.open(entry) as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def properties(data):
    # java.util.Properties.store(OutputStream) uses ISO-8859-1 and escaped keys.
    def decode(value):
        def replace(match):
            raw = match[1]
            if raw.startswith('u'):
                return chr(int(raw[1:], 16))
            return {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(raw, raw)
        return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', replace, value)
    result = {}
    for line in data.decode('iso-8859-1').splitlines():
        line = line.lstrip()
        if not line or line[0] in '#!':
            continue
        match = re.match(r'((?:\\.|[^=:\s])+)[\s=:]+(.*)', line)
        if not match:
            raise ValueError('Malformed package properties')
        key, value = decode(match[1]), decode(match[2])
        if key in result:
            raise ValueError('Duplicate package property')
        result[key] = value
    return result


def verify_package(path, platform, source, tag):
    with zipfile.ZipFile(path) as archive:
        entries = inventory(archive, 8 * 1024**3, 30000)
        files = {n for n, e in entries.items() if not e.is_dir()}
        if not META <= files or any(entries[n].file_size > 16 * 1024**2 for n in META):
            raise ValueError('Missing or oversized package metadata')
        metadata = properties(archive.read('definitive-package.properties'))
        hashes = properties(archive.read('definitive-files.properties'))
        expected = {'format': '1', 'java': '25', 'platform': platform,
                    'sourceRevision': source, 'releaseTag': tag}
        if any(metadata.get(k) != v for k, v in expected.items()):
            raise ValueError('Package source/tag/platform mismatch: ' + platform)
        game = metadata.get('gameJar', '')
        if not re.fullmatch(r'lod-game-[A-Za-z0-9._-]+\.jar', game):
            raise ValueError('Invalid game archive identity')
        payload = files - META
        if payload != set(hashes) or not ROOT | {game} <= payload:
            raise ValueError('Package inventory is incomplete')
        for name, entry in entries.items():
            if entry.is_dir():
                if name.rstrip('/').split('/')[0] not in SUPPORT:
                    raise ValueError('Unexpected package directory')
                continue
            if name in META:
                continue
            if not (name in ROOT or name == game or '/' in name and name.split('/')[0] in SUPPORT):
                raise ValueError('Unexpected package input: ' + name)
            if not re.fullmatch('[a-f0-9]{64}', hashes[name]) or digest_entry(archive, entry) != hashes[name]:
                raise ValueError('Package checksum mismatch: ' + name)
        if any(not any(n.startswith(folder + '/') and n.endswith('.jar') for n in payload)
               for folder in ('libs', 'bundled-mods')):
            raise ValueError('Missing engine dependencies or bundled artwork')
        canonical = ''.join(k + '=' + metadata[k] + '\n' for k in sorted(metadata) if k != 'id')
        canonical += ''.join(k + '=' + hashes[k] + '\n' for k in sorted(hashes))
        if metadata.get('id') != 'alpha-' + hashlib.sha256(canonical.encode()).hexdigest()[:16]:
            raise ValueError('Package metadata identity mismatch')


def verify_installer(path):
    with zipfile.ZipFile(path) as archive:
        entries = inventory(archive, 8 * 1024**3, 30000)
        files = {n for n, e in entries.items() if not e.is_dir()}
        if not {'Install.sh', 'bootstrap-java', 'definitive-manager.jar', 'LICENSE', 'CREDITS'} <= files:
            raise ValueError('Portable installer payload is incomplete')
        if not any(n.startswith('manager-libs/') and n.endswith('.jar') for n in files):
            raise ValueError('Portable installer dependencies are missing')
        allowed = {'Install.sh', 'bootstrap-java', 'definitive-manager.jar', 'LICENSE', 'CREDITS'}
        if any(n not in allowed and not n.startswith('manager-libs/') for n in files):
            raise ValueError('Unexpected portable installer input')
        for entry in entries.values():
            if not entry.is_dir():
                digest_entry(archive, entry)  # Read every byte, validating CRC and decompression.


def file_delivery_assets(package, contents):
    """Bind per-file inventory byte-for-byte to the already verified complete ZIP."""
    with zipfile.ZipFile(package) as full, zipfile.ZipFile(contents) as manifest:
        entries = inventory(manifest, 8 * 1024**2, 3)
        if set(entries) != META | {'definitive-sizes.properties'}:
            raise ValueError('Invalid per-file manifest inventory')
        for name in META:
            if manifest.read(name) != full.read(name):
                raise ValueError('Per-file inventory differs from complete package')
        hashes = properties(manifest.read('definitive-files.properties'))
        sizes = properties(manifest.read('definitive-sizes.properties'))
        if set(sizes) != set(hashes):
            raise ValueError('Incomplete per-file sizes inventory')
        result = {}
        for name, digest in hashes.items():
            if not re.fullmatch(r'0|[1-9][0-9]{0,10}', sizes[name]) or int(sizes[name]) != full.getinfo(name).file_size:
                raise ValueError('Per-file length mismatch')
            asset = 'file-' + digest
            value = int(sizes[name]), digest
            if asset in result and result[asset] != value:
                raise ValueError('Conflicting per-file blob identity')
            result[asset] = value
        return result
