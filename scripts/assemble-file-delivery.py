#!/usr/bin/env python3
"""Create individually retrievable, SHA256-addressed files from a verified package."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import zipfile
from package_validation import verify_package, inventory, properties, file_delivery_assets


def property_key(value):
    return ''.join('\\' + c if c in '\\ =:#!' else c if 32 <= ord(c) < 127 else '\\u%04x' % ord(c) for c in value)


def assemble(package, output, thin_release=False, archive_limit=1800*1024**2):
    with zipfile.ZipFile(package) as archive:
        metadata = properties(archive.read('definitive-package.properties'))
    verify_package(package, metadata['platform'], metadata['sourceRevision'], metadata['releaseTag'], allow_file_delivery=thin_release)
    output.mkdir(parents=True, exist_ok=True)
    blobs = output / 'files'
    blobs.mkdir(exist_ok=True)
    with zipfile.ZipFile(package) as archive:
        if set(archive.namelist())=={'definitive-package.properties','definitive-files.properties','definitive-sizes.properties'}:
            manifest=output/('Definitive-Contents-'+metadata['platform']+'.zip')
            for name,(size,digest) in file_delivery_assets(package,manifest).items():
                path=blobs/name
                if not path.is_file() or path.is_symlink() or path.stat().st_size!=size:raise ValueError('Missing file-only release blob')
                with path.open('rb') as stream:
                    if hashlib.file_digest(stream,'sha256').hexdigest()!=digest:raise ValueError('Corrupt file-only release blob')
            return manifest
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
            if target.stat().st_size != entry.file_size:
                raise ValueError('Extracted payload size mismatch')
            with target.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != digest:
                    raise ValueError('Extracted payload checksum mismatch')
            sizes.append(property_key(name) + '=' + str(entry.file_size) + '\n')
        manifest = output / ('Definitive-Contents-' + metadata['platform'] + '.zip')
        with zipfile.ZipFile(manifest, 'w', compression=zipfile.ZIP_DEFLATED) as contents:
            for name in ('definitive-package.properties', 'definitive-files.properties'):
                contents.writestr(name, archive.read(name))
            contents.writestr('definitive-sizes.properties', ''.join(sizes).encode('ascii'))
    assets=file_delivery_assets(package, manifest)
    if thin_release and package.stat().st_size>archive_limit:
        if any(size>2*1024**3 for size,digest in assets.values()):raise ValueError('Split oversized module before publication')
        temporary=package.with_suffix('.tmp')
        shutil.copyfile(manifest,temporary);temporary.replace(package)
        verify_package(package,metadata['platform'],metadata['sourceRevision'],metadata['releaseTag'],allow_file_delivery=True)
        file_delivery_assets(package,manifest)
    if len(list(blobs.iterdir())) + 8 > 1000:
        raise ValueError('Release file count exceeds the supported publication limit')
    print('File delivery:', metadata['platform'], len(hashes), 'files;', len(list(blobs.iterdir())), 'unique blobs')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--thin-release',action='store_true',help='Use the verified per-file inventory for platform downloads too when a full ZIP exceeds GitHub limits')
    args = parser.parse_args()
    assemble(args.package, args.output, args.thin_release)
