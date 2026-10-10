#!/usr/bin/env python3
"""Convert the pinned MIT SMAA byte tables to lossless RGBA PNGs; no dependencies."""
import argparse
from pathlib import Path
import re
import struct
import zlib


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def convert(source, target, name, width, height, channels):
    text = (source / 'Textures' / (name + '.h')).read_text(encoding='cp1252')
    table = text.split('Bytes[] = {', 1)[1].split('}', 1)[0]
    values = bytes(int(value, 16) for value in re.findall(r'0x([0-9a-fA-F]{2})', table))
    if len(values) != width * height * channels:
        raise ValueError('Unexpected upstream table dimensions: ' + name)
    rgba = bytearray()
    for i in range(0, len(values), channels):
        rgba.extend((values[i], values[i + 1] if channels == 2 else 0, 0, 255))
    rows = b''.join(b'\0' + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    (target / (name + '.png')).write_bytes(png + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path, help='iryoku/smaa checkout at 71c806a838bdd7d517df19192a20f0c61b3ca29d')
    parser.add_argument('--output', type=Path, default=Path('gfx/textures/smaa'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    convert(args.upstream, args.output, 'AreaTex', 160, 560, 2)
    convert(args.upstream, args.output, 'SearchTex', 64, 16, 1)
