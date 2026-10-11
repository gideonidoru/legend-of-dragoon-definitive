#!/usr/bin/env python3
"""Retain the checksum-pinned format-1 source/export bridge until publication migrates."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import time
import urllib.request
from package_validation import properties

MAX_BYTES = 2 * 1024**3 - 1

class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith('https://'):
            raise ValueError('Unsafe source redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def digest(path):
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= MAX_BYTES:
        raise ValueError('Missing, linked or oversized legacy FMV source')
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def retain(lock, manifest, output, local=None, opener=None):
    for path in (lock, manifest):
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 65536:
            raise ValueError('Invalid source lock')
    pin = properties(lock.read_bytes())
    sha = pin.get('sha256', '')
    if not re.fullmatch('[a-f0-9]{64}', sha) or properties(manifest.read_bytes()).get('legacyBundleSha256') != sha:
        raise ValueError('Legacy source and approved film pins differ')
    output = output.absolute()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.is_symlink() or output.parent.is_symlink():
        raise ValueError('Linked source output')
    if output.exists() and digest(output) == sha:
        return {'sha256': sha, 'bytes': output.stat().st_size, 'reused': True}
    fd, temporary = tempfile.mkstemp(prefix='.fmv-source-', dir=output.parent)
    temporary = Path(temporary)
    try:
        os.close(fd)
        if local is not None:
            if digest(local) != sha:
                raise ValueError('Local source checksum mismatch')
            source = local.open('rb')
        else:
            url = pin.get('url', '')
            expected = 'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/source-fmvhd-' + sha + '.zip'
            if url != expected:
                raise ValueError('Source URL is not the pinned current carrier')
            fetch = opener or urllib.request.build_opener(HttpsRedirect()).open
            source = fetch(urllib.request.Request(url, headers={'Accept-Encoding': 'identity'}), timeout=30)
            if source.headers.get('Content-Encoding', 'identity').lower() != 'identity':
                source.close()
                raise ValueError('Unexpected source encoding')
        started = time.monotonic(); size = 0; value = hashlib.sha256()
        with source, temporary.open('wb') as target:
            while block := source.read(65536):
                size += len(block)
                if size > MAX_BYTES or time.monotonic() - started > 1800:
                    raise ValueError('Source transfer exceeds bounds')
                target.write(block); value.update(block)
            target.flush(); os.fsync(target.fileno())
        if value.hexdigest() != sha:
            raise ValueError('Source transfer checksum mismatch')
        temporary.replace(output)
        return {'sha256': sha, 'bytes': size, 'reused': False}
    finally:
        temporary.unlink(missing_ok=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--local', type=Path)
    args = parser.parse_args()
    print(json.dumps(retain(args.lock, args.manifest, args.output, args.local), sort_keys=True))
