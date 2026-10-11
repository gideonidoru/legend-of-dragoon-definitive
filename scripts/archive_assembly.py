"""Bounded exact-byte archive plans; publisher IDs and activation stay external.

Raw ZIP records and their directory/end bytes are never inflated or rewritten.
All reuse identities hash the wire bytes. Changed spans share deterministic
bundles; recipes refer to immutable whole-source ranges, not ZIP entry names.
"""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import tempfile
import zipfile

MAX_SOURCE_BYTES = 2**31 - 1
MAX_SPAN_BYTES = 16 * 1024**2
MAX_BUNDLE_BYTES = MAX_SPAN_BYTES
MAX_DIRECTORY_BYTES = 32 * 1024**2
MAX_ENTRIES = 100_000
MAX_FILES = 30_000
MAX_SOURCES = 1_000
MAX_SPANS = 100_000
MAX_RECIPE_BYTES = 16 * 1024**2
IO_BYTES = 1024**2
SHA = re.compile(r'[0-9a-f]{64}\Z')
FIELDS = {'format', 'sourceRevision', 'platform', 'releaseTag', 'sources', 'files'}


def _positive(value, maximum, label):
    if type(value) is not int or not 1 <= value <= maximum:
        raise ValueError('Invalid ' + label)


def _sha(value):
    if not isinstance(value, str) or not SHA.fullmatch(value):
        raise ValueError('Invalid SHA-256')


def managed_path(value):
    if (not isinstance(value, str) or not 1 <= len(value) <= 1024
            or any(ord(c) < 32 or ord(c) > 126 for c in value)
            or '\\' in value or ':' in value or value.startswith('/')
            or any(p in ('', '.', '..') for p in value.split('/'))
            or len(value.split('/')) > 64 or str(PurePosixPath(value)) != value):
        raise ValueError('Invalid managed path')
    return value


@dataclass(frozen=True)
class FileIdentity:
    path: Path
    sha256: str
    bytes: int

    def __post_init__(self):
        object.__setattr__(self, 'path', Path(self.path))
        _sha(self.sha256)
        _positive(self.bytes, MAX_SOURCE_BYTES, 'source size')


def _open(identity):
    descriptor = os.open(identity.path, os.O_RDONLY | os.O_NOFOLLOW)
    stream = os.fdopen(descriptor, 'rb')
    info = os.fstat(stream.fileno())
    if not stat.S_ISREG(info.st_mode) or info.st_size != identity.bytes:
        stream.close()
        raise ValueError('Source size or file type changed')
    return stream


def _blocks(identity, offset, length):
    if (type(offset) is not int or type(length) is not int or offset < 0
            or length <= 0 or offset > identity.bytes - length):
        raise ValueError('Source range exceeds bounds')
    with _open(identity) as stream:
        stream.seek(offset)
        while length:
            block = stream.read(min(IO_BYTES, length))
            if not block:
                raise ValueError('Source truncated')
            length -= len(block)
            yield block


def range_digest(identity, offset, length):
    value = hashlib.sha256()
    for block in _blocks(identity, offset, length):
        value.update(block)
    return value.hexdigest()


def verify_file(identity):
    with _open(identity) as stream:
        before = os.fstat(stream.fileno())
        value = hashlib.sha256()
        while block := stream.read(IO_BYTES):
            value.update(block)
        after = os.fstat(stream.fileno())
    if (before.st_size, before.st_mtime_ns, before.st_ino, before.st_dev) != (
            after.st_size, after.st_mtime_ns, after.st_ino, after.st_dev):
        raise ValueError('Source changed while hashing')
    if value.hexdigest() != identity.sha256:
        raise ValueError('Source SHA-256 differs')
    return identity


def _directory(stream, size):
    stream.seek(max(0, size - 65557))
    tail = stream.read(65557)
    # A valid comment may itself contain an EOCD signature. Select the one
    # whose declared comment terminates exactly at EOF, then check geometry.
    at = len(tail)
    while True:
        at = tail.rfind(b'PK\x05\x06', 0, at)
        if at < 0:
            raise ValueError('Missing or truncated ZIP end record')
        if at + 22 <= len(tail):
            values = struct.unpack_from('<4s4H2IH', tail, at)
            if at + 22 + values[-1] == len(tail):
                break
    _, disk, directory_disk, disk_count, count, length, offset, _ = values
    end = size - len(tail) + at
    metadata_end = end + 22
    if disk or directory_disk:
        raise ValueError('Multi-disk ZIP is unsupported')
    zip64 = count == 65535 or disk_count == 65535 or length == 0xffffffff or offset == 0xffffffff
    if zip64:
        if end < 20:
            raise ValueError('ZIP64 locator is missing')
        stream.seek(end - 20)
        locator = stream.read(20)
        signature, record_disk, record_offset, disks = struct.unpack('<4sIQI', locator)
        if signature != b'PK\x06\x07' or record_disk or disks != 1 or record_offset > end - 76:
            raise ValueError('Invalid ZIP64 locator')
        stream.seek(record_offset)
        record = stream.read(56)
        if len(record) != 56:
            raise ValueError('ZIP64 record is truncated')
        signature, extra, _, _, disk64, directory_disk64, disk_count64, count64, length64, offset64 = struct.unpack('<4sQ2H2I4Q', record)
        if (signature != b'PK\x06\x06' or extra != 44
                or record_offset + 12 + extra != end - 20 or disk64 or directory_disk64
                or disk_count64 != count64):
            raise ValueError('Invalid ZIP64 directory geometry')
        # Non-sentinel legacy fields must agree with the extended fields.
        for small, large, sentinel in [(count, count64, 65535), (disk_count, disk_count64, 65535),
                                       (length, length64, 0xffffffff), (offset, offset64, 0xffffffff)]:
            if small != sentinel and small != large:
                raise ValueError('ZIP32/ZIP64 metadata disagrees')
        count, length, offset, end = count64, length64, offset64, record_offset
    elif disk_count != count:
        raise ValueError('ZIP directory counts disagree')
    if count > MAX_ENTRIES or length > MAX_DIRECTORY_BYTES:
        raise ValueError('ZIP metadata exceeds allocation budget')
    if offset < 0 or offset + length != end or (count == 0) != (length == 0):
        raise ValueError('ZIP directory/tail geometry differs')
    return count, length, offset, metadata_end


class _MetadataReader:
    """Hide the verified comment from ZipFile's signature search, without copies.

    Only its metadata view changes. Every stored/planned byte still comes from
    the original file, including the entire unmodified ZIP comment.
    """
    def __init__(self, stream, end):
        self.stream, self.end = stream, end

    def tell(self):
        return self.stream.tell()

    def seek(self, offset, whence=0):
        position = offset if whence == 0 else self.tell() + offset if whence == 1 else self.end + offset
        if whence not in (0, 1, 2) or not 0 <= position <= self.end:
            raise OSError('ZIP metadata seek exceeds bounds')
        return self.stream.seek(position)

    def read(self, size=-1):
        position = self.tell()
        length = self.end - position if size < 0 else min(size, self.end - position)
        if length > MAX_DIRECTORY_BYTES + 65557:
            raise ValueError('ZIP parser read exceeds metadata budget')
        data = bytearray(self.stream.read(length))
        for at in (self.end - 2, self.end - 1):
            if position <= at < position + len(data):
                data[at - position] = 0
        return bytes(data)

    def seekable(self):
        return True


def _scan_directory(stream, count, length, offset):
    """Bound count and record geometry before ZipFile creates entry objects."""
    stream.seek(offset)
    remaining, seen = length, 0
    while remaining:
        if remaining < 46:
            raise ValueError('Truncated central directory')
        header = stream.read(46)
        if len(header) != 46 or header[:4] != b'PK\x01\x02':
            raise ValueError('Invalid central directory record')
        names, extra, comment = struct.unpack_from('<3H', header, 28)
        record = 46 + names + extra + comment
        if record > remaining or names == 0:
            raise ValueError('Central record exceeds directory')
        stream.seek(record - 46, 1)
        remaining -= record
        seen += 1
        if seen > count or seen > MAX_ENTRIES:
            raise ValueError('Central record count exceeds budget')
    if seen != count:
        raise ValueError('Central directory count differs')


def _zip64_local(extra, unpacked, packed):
    position, value = 0, None
    while position < len(extra):
        if position + 4 > len(extra):
            raise ValueError('Truncated local extra field')
        kind, length = struct.unpack_from('<HH', extra, position)
        position += 4
        if position + length > len(extra):
            raise ValueError('Local extra field exceeds header')
        if kind == 1:
            if value is not None:
                raise ValueError('Duplicate local ZIP64 field')
            value = extra[position:position + length]
        position += length
    cursor = 0
    result = []
    for number in (unpacked, packed):
        if number == 0xffffffff:
            if value is None or cursor + 8 > len(value):
                raise ValueError('Local ZIP64 size is missing')
            number = struct.unpack_from('<Q', value, cursor)[0]
            cursor += 8
        result.append(number)
    return result


def _descriptor(stream, offset, length, item, zip64):
    width = 20 if zip64 else 12
    if length not in (width, width + 4):
        raise ValueError('Data descriptor or local tail length differs')
    stream.seek(offset)
    data = stream.read(length)
    if length == width + 4:
        if data[:4] != b'PK\x07\x08':
            raise ValueError('Data descriptor signature differs')
        data = data[4:]
    values = struct.unpack('<IQQ' if zip64 else '<III', data)
    if values != (item.CRC, item.compress_size, item.file_size):
        raise ValueError('Data descriptor identity differs')


def archive_ranges(identity, require_archive=False):
    """Return contiguous bounded spans; ordinary files use fixed raw blocks."""
    ranges = []
    def append(start, count):
        while count:
            length = min(count, MAX_SPAN_BYTES)
            ranges.append((start, length))
            start += length
            count -= length
            if len(ranges) > MAX_SPANS:
                raise ValueError('Archive span count exceeds budget')
    with _open(identity) as stream:
        magic = stream.read(4)
        if magic not in (b'PK\x03\x04', b'PK\x05\x06', b'PK\x06\x06'):
            if require_archive:
                raise ValueError('Required ZIP/JAR header is missing')
            append(0, identity.bytes)
            return ranges
        count, length, directory, metadata_end = _directory(stream, identity.bytes)
        _scan_directory(stream, count, length, directory)
        stream.seek(0)
        try:
            with zipfile.ZipFile(_MetadataReader(stream, metadata_end)) as archive:
                items = sorted(archive.infolist(), key=lambda item: item.header_offset)
                if len(items) != count or archive.start_dir != directory:
                    raise ValueError('ZIP parser differs from bounded directory')
                if len({item.orig_filename for item in items}) != len(items):
                    raise ValueError('Duplicate ZIP names')
                offsets = [item.header_offset for item in items]
                if items and (offsets[0] != 0 or len(set(offsets)) != count):
                    raise ValueError('ZIP prefix or duplicate offsets')
                for item, start, stop in zip(items, offsets, offsets[1:] + [directory]):
                    if not 0 <= start < stop <= directory:
                        raise ValueError('Overlapping local offsets')
                    stream.seek(start)
                    header = stream.read(30)
                    if len(header) != 30 or header[:4] != b'PK\x03\x04':
                        raise ValueError('Local ZIP header differs')
                    flags, method = struct.unpack_from('<HH', header, 6)
                    crc, packed, unpacked = struct.unpack_from('<III', header, 14)
                    names, extras = struct.unpack_from('<HH', header, 26)
                    if flags != item.flag_bits or method != item.compress_type or flags & 1:
                        raise ValueError('Encrypted or inconsistent local header')
                    data = start + 30 + names + extras
                    if data + item.compress_size > stop:
                        raise ValueError('Local payload exceeds span')
                    name = stream.read(names).decode('utf-8' if flags & 2048 else 'cp437')
                    extra = stream.read(extras)
                    if name != item.orig_filename or '\x00' in name:
                        raise ValueError('Local ZIP name differs')
                    is64 = unpacked == 0xffffffff or packed == 0xffffffff
                    unpacked, packed = _zip64_local(extra, unpacked, packed)
                    if flags & 8:
                        if crc not in (0, item.CRC) or packed not in (0, item.compress_size) or unpacked not in (0, item.file_size):
                            raise ValueError('Local descriptor sizes differ')
                        _descriptor(stream, data + item.compress_size, stop - data - item.compress_size, item, is64)
                    elif (crc, packed, unpacked) != (item.CRC, item.compress_size, item.file_size) or data + packed != stop:
                        raise ValueError('Local sizes, CRC or tail differ')
                    append(start, stop - start)
        except (zipfile.BadZipFile, struct.error, UnicodeError, OSError) as error:
            raise ValueError('Malformed ZIP metadata') from error
    append(directory, identity.bytes - directory)
    if sum(count for _, count in ranges) != identity.bytes:
        raise ValueError('Archive is not completely partitioned')
    return ranges


@dataclass(frozen=True)
class AssemblyPlan:
    sources: dict
    files: dict
    uploads: dict
    identities: dict


def plan_files(targets, baselines, bundle_directory, baseline_ranges=None):
    """Verified targets and old whole files -> ID-free plan and new bundle files.

    targets maps managed paths to FileIdentity; baselines maps source SHA to
    FileIdentity. Optional baseline_ranges is the prior verified recipe's raw
    source catalog; it permits reuse from bundles that are not complete ZIPs.
    A failing plan removes only its own temporary/new bundle files.
    The caller supplies a dedicated writable bundle directory and owns it after
    success. No inputs, existing uploads or managed installations are changed.
    """
    if not isinstance(targets, dict) or not 1 <= len(targets) <= MAX_FILES:
        raise ValueError('Invalid target file set')
    if not isinstance(baselines, dict) or len(baselines) > MAX_SOURCES:
        raise ValueError('Invalid baseline set')
    for name in targets:
        managed_path(name)
    _path_conflicts(targets)
    for sha, identity in baselines.items():
        if not isinstance(identity, FileIdentity) or sha != identity.sha256:
            raise ValueError('Baseline source identity differs')
    if any(not isinstance(identity, FileIdentity) for identity in targets.values()):
        raise ValueError('Target identity required')
    if baseline_ranges is not None:
        if not isinstance(baseline_ranges, dict) or not set(baseline_ranges) <= set(baselines):
            raise ValueError('Baseline range sources are not bound')
        range_count = 0
        for sha, rows in baseline_ranges.items():
            if not isinstance(rows, list) or not rows:
                raise ValueError('Invalid baseline range catalog')
            intervals = []
            for row in rows:
                if not isinstance(row, dict) or set(row) != {'sha256', 'bytes', 'offset'}:
                    raise ValueError('Invalid baseline range descriptor')
                _sha(row['sha256'])
                _positive(row['bytes'], MAX_SPAN_BYTES, 'baseline span size')
                if (type(row['offset']) is not int or row['offset'] < 0
                        or row['offset'] > baselines[sha].bytes - row['bytes']):
                    raise ValueError('Baseline range exceeds source')
                intervals.append((row['offset'], row['offset'] + row['bytes'], row['sha256']))
                range_count += 1
                if range_count > MAX_SPANS:
                    raise ValueError('Baseline span inventory exceeds budget')
            _check_overlaps(intervals)
    bundle_directory = Path(bundle_directory)
    bundle_directory.mkdir(parents=True, exist_ok=True)
    if bundle_directory.is_symlink() or not bundle_directory.is_dir():
        raise ValueError('Unsafe bundle directory')
    reusable, records, pending, uploads, sources = {}, {}, [], {}, {}
    temporary_sources = {}
    current = None
    try:
        baseline_spans = 0
        for sha, identity in sorted(baselines.items()):
            verify_file(identity)
            catalog = baseline_ranges.get(sha) if baseline_ranges is not None else None
            if catalog is None:
                catalog = [dict(offset=offset, bytes=length, sha256=None) for offset, length
                           in archive_ranges(identity, identity.path.suffix.lower() in ('.zip', '.jar'))]
            for item in catalog:
                offset, length = item['offset'], item['bytes']
                baseline_spans += 1
                if baseline_spans > MAX_SPANS:
                    raise ValueError('Baseline span inventory exceeds budget')
                key = (range_digest(identity, offset, length), length)
                if item['sha256'] is not None and key[0] != item['sha256']:
                    raise ValueError('Baseline range SHA-256 differs')
                reusable.setdefault(key, dict(sha256=key[0], bytes=length, source=sha, offset=offset))
        changed = {}
        total = 0
        for name, identity in sorted(targets.items()):
            verify_file(identity)
            rows = []
            for offset, length in archive_ranges(identity, name.lower().endswith(('.zip', '.jar'))):
                key = (range_digest(identity, offset, length), length)
                row = reusable.get(key)
                if row is not None:
                    row = dict(row)
                    sources[row['source']] = baselines[row['source']].bytes
                else:
                    if key not in changed:
                        if current is None or current['bytes'] + length > MAX_BUNDLE_BYTES:
                            if current is not None:
                                current['stream'].close()
                            fd, path = tempfile.mkstemp(prefix='.archive-bundle-', dir=bundle_directory)
                            current = dict(path=Path(path), stream=os.fdopen(fd, 'wb'), bytes=0, rows=[])
                            pending.append(current)
                        row = dict(sha256=key[0], bytes=length, source=None, offset=current['bytes'])
                        check = hashlib.sha256()
                        for block in _blocks(identity, offset, length):
                            current['stream'].write(block)
                            check.update(block)
                        if check.hexdigest() != key[0]:
                            raise ValueError('Target span changed while bundling')
                        current['bytes'] += length
                        current['rows'].append(row)
                        changed[key] = row
                    row = changed[key]
                rows.append(row)
                total += 1
                if total > MAX_SPANS:
                    raise ValueError('Plan exceeds span budget')
            records[name] = rows
        if current is not None:
            current['stream'].close()
        for bundle in pending:
            path, length = bundle['path'], bundle['bytes']
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                while block := stream.read(IO_BYTES):
                    digest.update(block)
            sha = digest.hexdigest()
            destination = bundle_directory / ('file-' + sha)
            if sha in baselines:
                if baselines[sha].bytes != length:
                    raise ValueError('Bundle and baseline identities conflict')
            else:
                uploads[sha] = FileIdentity(destination, sha, length)
                temporary_sources[sha] = FileIdentity(path, sha, length)
            sources[sha] = length
            for row in bundle['rows']:
                row['source'] = sha
        if len(sources) > MAX_SOURCES:
            raise ValueError('Plan source count exceeds budget')
        plan = AssemblyPlan(sources, records, uploads, dict(targets))
        # Check hashes again during the actual mixed-source concatenation.
        # Prove private temporary bytes before making any shared immutable object
        # available to another successful planner.
        prove_plan(plan, {**baselines, **temporary_sources})
        for sha, identity in uploads.items():
            try:
                os.link(temporary_sources[sha].path, identity.path)
            except FileExistsError:
                verify_file(identity)
        # Published valid immutable objects may already be referenced by another
        # successful plan. A later publication failure cannot delete them.
        return plan
    finally:
        for bundle in pending:
            bundle['stream'].close()
            bundle['path'].unlink(missing_ok=True)


def _path_conflicts(files):
    names = set(files)
    for name in names:
        parts = name.split('/')
        if any('/'.join(parts[:i]) in names for i in range(1, len(parts))):
            raise ValueError('Managed file/directory paths overlap')


def _check_overlaps(ranges):
    previous = None
    for current in sorted(ranges):
        if previous is not None and current[0] < previous[1] and current != previous:
            raise ValueError('Conflicting overlapping source spans')
        previous = current


def validate_recipe(recipe):
    if not isinstance(recipe, dict) or set(recipe) != FIELDS or type(recipe['format']) is not int or recipe['format'] != 1:
        raise ValueError('Unknown archive recipe schema')
    if not isinstance(recipe['sourceRevision'], str) or not re.fullmatch('[0-9a-f]{40}', recipe['sourceRevision']):
        raise ValueError('Invalid recipe source revision')
    if recipe['platform'] not in ('linux-x64', 'macos-arm64') or recipe['releaseTag'] != 'definitive-current':
        raise ValueError('Invalid recipe platform or carrier')
    sources, files = recipe['sources'], recipe['files']
    if not isinstance(sources, dict) or not 1 <= len(sources) <= MAX_SOURCES or not isinstance(files, dict) or not 1 <= len(files) <= MAX_FILES:
        raise ValueError('Recipe inventory exceeds bounds')
    ids = set()
    for sha, source in sources.items():
        _sha(sha)
        if not isinstance(source, dict) or set(source) != {'id', 'bytes'}:
            raise ValueError('Invalid recipe source descriptor')
        identifier = source['id']
        if (not isinstance(identifier, str) or not re.fullmatch('[1-9][0-9]{0,15}', identifier)
                or int(identifier) > 2**53 - 1 or identifier in ids):
            raise ValueError('Invalid or duplicate recipe asset ID')
        ids.add(identifier)
        _positive(source['bytes'], MAX_SOURCE_BYTES, 'recipe source size')
    total, used, intervals = 0, set(), {}
    for name, spans in files.items():
        managed_path(name)
        if not isinstance(spans, list) or not spans:
            raise ValueError('Missing ordered file spans')
        size = 0
        for span in spans:
            if not isinstance(span, dict) or set(span) != {'sha256', 'bytes', 'source', 'offset'}:
                raise ValueError('Invalid recipe span')
            _sha(span['sha256'])
            _sha(span['source'])
            _positive(span['bytes'], MAX_SPAN_BYTES, 'span size')
            if (span['source'] not in sources or type(span['offset']) is not int or span['offset'] < 0
                    or span['offset'] > sources[span['source']]['bytes'] - span['bytes']):
                raise ValueError('Span exceeds bound source')
            size += span['bytes']
            total += 1
            if size > MAX_SOURCE_BYTES or total > MAX_SPANS:
                raise ValueError('Assembled file/span count exceeds bounds')
            used.add(span['source'])
            intervals.setdefault(span['source'], []).append((span['offset'], span['offset'] + span['bytes'], span['sha256']))
    _path_conflicts(files)
    if used != set(sources):
        raise ValueError('Recipe contains unreferenced sources')
    for ranges in intervals.values():
        _check_overlaps(ranges)
    if len(json.dumps(recipe, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')) > MAX_RECIPE_BYTES:
        raise ValueError('Recipe exceeds wire budget')
    return recipe


def bind_recipe(plan, source_revision, platform, release_tag, asset_ids):
    _check_plan_inventory(plan)
    if set(asset_ids) != set(plan.sources):
        raise ValueError('Asset binding does not close planned sources')
    return validate_recipe(dict(format=1, sourceRevision=source_revision, platform=platform,
                                releaseTag=release_tag, sources={sha:dict(id=asset_ids[sha], bytes=size)
                                for sha, size in plan.sources.items()}, files=plan.files))


def recipe_source_ranges(recipe):
    """Verified catalog shape for later generations; bytes are rehashed on use."""
    validate_recipe(recipe)
    catalog = {sha:{} for sha in recipe['sources']}
    for rows in recipe['files'].values():
        for row in rows:
            catalog[row['source']][(row['offset'], row['bytes'], row['sha256'])] = {
                key:row[key] for key in ('offset', 'bytes', 'sha256')}
    return {sha:[rows[key] for key in sorted(rows)] for sha, rows in catalog.items()}


def verified_recipe_ranges(recipe, source_files, asset_ids):
    """Bind a prior recipe to fresh publisher IDs and verified whole sources.

    This checks local byte/ID bindings; the publisher still verifies that these
    IDs belong to the current carrier's immutable inventory before supplying
    them. Every catalog span is verified, including unused reuse candidates.
    """
    validate_recipe(recipe)
    if set(asset_ids) != set(recipe['sources']):
        raise ValueError('Prior recipe asset inventory is not closed')
    for sha, source in recipe['sources'].items():
        identity = source_files.get(sha)
        if (asset_ids[sha] != source['id'] or not isinstance(identity, FileIdentity)
                or (identity.sha256, identity.bytes) != (sha, source['bytes'])):
            raise ValueError('Prior recipe source binding differs')
        verify_file(identity)
    catalog = recipe_source_ranges(recipe)
    for sha, rows in catalog.items():
        for row in rows:
            if range_digest(source_files[sha], row['offset'], row['bytes']) != row['sha256']:
                raise ValueError('Prior recipe range SHA-256 differs')
    return catalog


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate recipe JSON key')
        result[key] = value
    return result


def load_recipe(data):
    if not isinstance(data, bytes) or len(data) > MAX_RECIPE_BYTES:
        raise ValueError('Recipe input exceeds wire budget')
    try:
        return validate_recipe(json.loads(data, object_pairs_hook=_unique))
    except (UnicodeError, RecursionError, json.JSONDecodeError) as error:
        raise ValueError('Malformed recipe JSON') from error


def _check_plan_inventory(plan):
    if set(plan.files) != set(plan.identities):
        raise ValueError('Plan file set differs from managed inventory')


def prove_plan(plan, source_files):
    """Read-only exact concatenation proof; final identity comes from inventory."""
    _check_plan_inventory(plan)
    if not set(plan.sources) <= set(source_files):
        raise ValueError('Plan source files are missing')
    for sha, size in plan.sources.items():
        identity = source_files.get(sha)
        if not isinstance(identity, FileIdentity) or (identity.sha256, identity.bytes) != (sha, size):
            raise ValueError('Plan source identity differs')
        verify_file(identity)
    results = {}
    for name, spans in plan.files.items():
        expected = plan.identities[name]
        digest, size = hashlib.sha256(), 0
        for span in spans:
            one = hashlib.sha256()
            for block in _blocks(source_files[span['source']], span['offset'], span['bytes']):
                one.update(block)
                digest.update(block)
                size += len(block)
            if one.hexdigest() != span['sha256']:
                raise ValueError('Assembly span SHA-256 differs')
        if (digest.hexdigest(), size) != (expected.sha256, expected.bytes):
            raise ValueError('Assembled file differs from managed inventory')
        results[name] = dict(sha256=expected.sha256, bytes=size, spans=len(spans))
    return results
