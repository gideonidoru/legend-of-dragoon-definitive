#!/usr/bin/env python3
"""Private unpacked TMD/4-bit TIM material audit and comparison atlas. No game integration.
Pillow 12.3.0; output must be a new directory outside the repository.
"""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import platform
import struct
import sys
import PIL
from PIL import Image, ImageDraw

LIMIT = 16 * 1024 * 1024

def bounded_read(path):
    if path.stat().st_size > LIMIT: raise ValueError('Input exceeds the private audit limit')
    with path.open('rb') as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT: raise ValueError('Input exceeds the private audit limit')
    return data

def take(data, offset, fmt):
    if offset < 0 or offset + struct.calcsize(fmt) > len(data): raise ValueError('Truncated or invalid model/texture range')
    return struct.unpack_from(fmt, data, offset)

def texture(data):
    magic, flags = take(data, 0, '<2I')
    if magic != 0x10 or flags != 8: raise ValueError('Material audit currently requires 4-bit indexed TIM')
    clut_size, x, y, cw, ch = take(data, 8, '<I4H')
    if cw not in (16, 32, 64) or ch < 1 or ch > 16 or clut_size != 12 + cw * ch * 2: raise ValueError('Unsupported palette layout')
    image_offset = 8 + clut_size
    image_size, ix, iy, words, height = take(data, image_offset, '<I4H')
    width = words * 4
    expected = words * height * 2
    standard = image_size == 12 + expected and image_offset + image_size == len(data)
    # SC field extraction retains zero padding rows with a payload-sized block value.
    zero_padded_field = image_size == expected + cw * ch * 2 and cw == words and image_offset + 12 + image_size == len(data) and not any(data[image_offset + 12 + expected:])
    if not (1 <= width <= 512 and 1 <= height <= 512) or not (standard or zero_padded_field): raise ValueError('Invalid texture dimensions or payload')
    colours = take(data, 20, '<' + str(cw * ch) + 'H')
    indices = data[image_offset + 12:]
    return width, height, cw, ch, colours, indices

def faces(data):
    tmd_offset, animation_offset, extra_offset = take(data, 0, '<3I')
    identifier, flags, count = take(data, tmd_offset, '<3I')
    if identifier != 0x41 or flags != 0 or count < 1 or count > 256: raise ValueError('Requires an unpacked relative-offset TMD container')
    table = tmd_offset + 12; inventory = []; packets = []; total = 0
    for part in range(count):
        vertices, nv, normals, nn, primitive_offset, np, scale = take(data, table + part * 28, '<7I')
        if nv > 100000 or nn > 100000 or np > 100000 or (total := total + np) > 100000: raise ValueError('Model exceeds audit limits')
        if table + vertices + nv * 8 > len(data) or table + normals + nn * 8 > len(data): raise ValueError('Invalid vertex/normal range')
        inventory.append({'part': part, 'vertices': nv, 'faces': np})
        position = table + primitive_offset
        for _ in range(np):
            header, = take(data, position, '<I'); mode = header >> 24; size = (header >> 8 & 255) * 4
            if mode >> 5 & 3 != 1 or not (4 <= size <= 64) or position + 4 + size > len(data): raise ValueError('Unsupported or truncated polygon packet')
            vertex_count = 4 if mode & 8 else 3
            if mode & 4:
                if size < vertex_count * 4: raise ValueError('Truncated UV packet')
                packet = data[position + 4:position + 4 + size]
                clut, = take(packet, 2, '<H'); tpage, = take(packet, 6, '<H')
                if tpage >> 7 & 3 != 0: raise ValueError('Mixed texture bit depths require separate audit')
                packets.append((part, clut, [(packet[k*4], packet[k*4+1]) for k in range(vertex_count)]))
            position += 4 + size
    return inventory, packets, bool(animation_offset), bool(extra_offset)

def material_masks(width, height, cw, ch, packets):
    masks = {}; palette_faces = collections.Counter()
    for part, clut, coords in packets:
        # Existing UvAdjustmentMetrics14 retains relative CLUT x/y bits before relocation.
        palette = ((clut >> 6) & 15) * (cw // 16) + (clut & 3)
        if palette >= cw * ch // 16 or any(not (0 <= u < width and 0 <= v < height) for u,v in coords): raise ValueError('Material mapping falls outside this TIM')
        palette_faces[palette] += 1
        mask = masks.setdefault(palette, Image.new('1', (width, height)))
        draw = ImageDraw.Draw(mask); draw.polygon(coords[:3], fill=1)
        if len(coords) == 4: draw.polygon([coords[1], coords[3], coords[2]], fill=1)
    return masks, palette_faces

def audit(model, tim):
    width, height, cw, ch, colours, indices = texture(tim)
    parts, packets, animation, extra = faces(model)
    masks, palette_faces = material_masks(width, height, cw, ch, packets)
    preview = Image.new('RGBA', (width, height)); engine = Image.new('RGBA', (width, height))
    overlap = conflicts = covered = 0
    for y in range(height):
        for x in range(width):
            index = (indices[y * (width // 2) + x // 2] >> (4 * (x % 2))) & 15
            active = [palette for palette, mask in masks.items() if mask.getpixel((x,y))]
            values = {colours[palette * 16 + index] for palette in active}
            if active: covered += 1
            if len(active) > 1: overlap += 1
            if len(values) > 1: conflicts += 1
            # Unused texels stay transparent. Conflicts cannot be silently flattened.
            if len(values) == 1:
                value = next(iter(values)); rgb = ((value & 31) * 255 // 31, (value >> 5 & 31) * 255 // 31, (value >> 10 & 31) * 255 // 31)
                preview.putpixel((x,y), rgb + ((255 if value else 0),))
                engine.putpixel((x,y), rgb + ((255 if value & 0x8000 else 0),))
    report = {'modelSha256': hashlib.sha256(model).hexdigest(), 'timSha256': hashlib.sha256(tim).hexdigest(), 'pipeline': 'private-tmd-material-audit-1', 'width': width, 'height': height, 'parts': parts, 'texturedFaces': len(packets), 'usedPalettes': dict(palette_faces), 'availablePalettes': cw*ch//16, 'coveredTexels': covered, 'overlappingTexels': overlap, 'conflictingColourTexels': conflicts, 'hasClutAnimations': animation, 'hasExtraSubfile': extra, 'scope': 'Offline material comparison only. Inclusive integer UV rasterization is an audit heuristic; animation, subpixel boundaries, mip/filter footprints and runtime seams still require verification.'}
    return report, preview, engine

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('model', type=Path); parser.add_argument('tim', type=Path); parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]): parser.error('Use a new private output outside this source checkout')
    report, preview, engine = audit(bounded_read(args.model), bounded_read(args.tim))
    report.update({'environment': platform.platform(), 'pillow': PIL.__version__, 'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'command': [sys.executable, *sys.argv]})
    args.output.mkdir(exist_ok=False)
    if not report['conflictingColourTexels']:
        preview.save(args.output / 'material-preview.png'); engine.save(args.output / 'material-stp.png')
        report['previewSha256'] = hashlib.sha256((args.output / 'material-preview.png').read_bytes()).hexdigest()
        report['engineStpSha256'] = hashlib.sha256((args.output / 'material-stp.png').read_bytes()).hexdigest()
    (args.output / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Private audit:', args.output, '| palettes:', len(report['usedPalettes']), '| conflicting texels:', report['conflictingColourTexels'])
    if report['conflictingColourTexels']: raise SystemExit('Conflicting palette colours: no flat atlas emitted')

if __name__ == '__main__': main()
