#!/usr/bin/env python3
"""Exercise native RAR/7z import using original synthetic disc headers, never retail assets."""
from pathlib import Path
import os
import struct
import subprocess
import tempfile
import zlib

repo = Path(__file__).resolve().parents[1]
package = repo / 'build/definitive/package'
helper = package / 'tools/7zz'
java = Path(os.environ['JAVA_HOME']) / 'bin/java'
manager = repo / 'build/libs/definitive-manager.jar'
ids = ('SCUS94491', 'SCUS94584', 'SCUS94585', 'SCUS94586')

def disc(identifier):
    data = bytearray(17 * 2352)
    p = 16 * 2352 + 24
    data[p:p + 7] = b'\x01CD001\x01'
    data[p + 8:p + 72] = b' ' * 64
    data[p + 8:p + 19] = b'PLAYSTATION'
    data[p + 40:p + 49] = identifier.encode()
    return bytes(data)

def header(data):
    return struct.pack('<H', zlib.crc32(data) & 0xffff) + data

with tempfile.TemporaryDirectory(prefix='definitive-archive-fixture-') as folder:
    root = Path(folder).resolve()
    inputs = root / 'inputs'
    inputs.mkdir()
    for identifier in ids:
        (inputs / (identifier + '.bin')).write_bytes(disc(identifier))
    (inputs / 'emulator.exe').write_bytes(b'UNRELATED: must not be installed')
    archive = root / 'original-fixture.rar'
    # Original stored RAR4 fixture; CRCs, sizes, names and payloads supplied by this test.
    content = bytearray(b'Rar!\x1a\x07\x00')
    content += header(struct.pack('<BHH', 0x73, 0, 13) + b'\0' * 6)
    for file in inputs.iterdir():
        data = file.read_bytes()
        name = file.name.encode()
        file_header = struct.pack('<BHHIIBIIBBHI', 0x74, 0x8000, 32 + len(name), len(data), len(data), 3, zlib.crc32(data), 0, 20, 0x30, len(name), 0x81a4) + name
        content += header(file_header) + data
    content += header(struct.pack('<BHH', 0x7b, 0, 7))
    archive.write_bytes(content)
    seven = root / 'original-fixture.7z'
    subprocess.run([str(helper), 'a', '-t7z', str(seven), str(inputs / '*')], check=True, stdout=subprocess.DEVNULL)
    for archive in (archive, seven):
        install = root / archive.suffix[1:]
        command = [str(java), '-Ddefinitive.installerLog=' + str(root / 'installer.log'),
                   '-Ddefinitive.locationRegistry=' + str(root / 'locations.properties'), '-jar', str(manager)]
        subprocess.run(command + ['--install', str(package), str(install)], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(command + ['--import', str(install), str(archive)], check=True)
        assert sorted(p.name for p in (install / 'isos').iterdir()) == sorted([i + '.bin' for i in ids] + ['disc-import-checksums.properties', '.definitive-disc-stage'])
        # A verified physical copy is not yet an accepted prepared-disc baseline.
        assert not (install / 'isos/disc-checksums.properties').exists()
        for identifier in ids:
            assert (install / 'isos' / (identifier + '.bin')).read_bytes() == disc(identifier)
        assert archive.exists()
print('RAR4 and 7z native imports passed; four images preserved, unrelated files excluded.')
