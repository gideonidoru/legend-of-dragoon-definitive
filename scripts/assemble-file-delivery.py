#!/usr/bin/env python3
"""Create individually retrievable, SHA256-addressed files from a verified package."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import zipfile
from package_validation import verify_package, inventory, properties


def property_key(value):
    return ''.join('\\' + c if c in '\\ =:#!' else c if 32 <= ord(c) < 127 else '\\u%04x' % ord(c) for c in value)


def assemble(package, output):
    with zipfile.ZipFile(package) as archive:
        metadata = properties(archive.read('definitive-package.properties'))
    verify_package(package, metadata['platform'], metadata['sourceRevision'], metadata['releaseTag'])
    output.mkdir(parents=True, exist_ok=True)
    blobs = output / 'files'
    blobs.mkdir(exist_ok=True)
    with zipfile.ZipFile(package) as archive:
        entries = inventory(archive, 8 * 1024**3, 30000)
        hashes = properties(archive.read('definitive-files.properties'))
        sizes = []
        for name in sorted(hashes):
            entry = entries[name]
            digest = hashes[name]
            target = blobs / ('file-' + digest)
            if target.exists():
                if target.is_symlink() or target.stat().st_size != entry.file_size:
                    raise ValueError('Conflicting existing payload blob')
                with target.open('rb') as stream:
                    if hashlib.file_digest(stream, 'sha256').hexdigest() != digest:
                        raise ValueError('Existing payload blob checksum mismatch')
            else:
                with archive.open(entry) as source, target.open('xb') as destination:
                    shutil.copyfileobj(source, destination, 1024 * 1024)
            sizes.append(property_key(name) + '=' + str(entry.file_size) + '\n')
        manifest = output / ('Definitive-Contents-' + metadata['platform'] + '.zip')
        with zipfile.ZipFile(manifest, 'w', compression=zipfile.ZIP_DEFLATED) as contents:
            for name in ('definitive-package.properties', 'definitive-files.properties'):
                contents.writestr(name, archive.read(name))
            contents.writestr('definitive-sizes.properties', ''.join(sizes).encode('ascii'))
    if len(list(blobs.iterdir())) + 8 > 1000:
        raise ValueError('Release file count exceeds the supported publication limit')
    print('File delivery:', metadata['platform'], len(hashes), 'files;', len(list(blobs.iterdir())), 'unique blobs')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assemble(args.package, args.output)
