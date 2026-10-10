#!/usr/bin/env python3
"""Decode private MCQ sky art using McqBuilder's tile/palette walk; no game launch.

Sources and previews stay outside Git. A new destination is required.
"""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import re
import struct
from PIL import Image, ImageDraw


def decode(data):
    if len(data) < 40:
        raise ValueError('Truncated MCQ')
    magic, offset = struct.unpack_from('<II', data)
    vw, vh, cx, cy, u, v, width, height = struct.unpack_from('<8H', data, 8)
    if magic not in (0x151434d, 0x251434d) or offset < 40 or offset + vw * vh * 2 != len(data):
        raise ValueError('Unsupported MCQ payload')
    if not 16 <= width <= 2048 or not 16 <= height <= 2048 or width % 16 or height % 16:
        raise ValueError('MCQ is not a drawable tile image')
    if not 1 <= vw <= 256 or not 1 <= vh <= 512:
        raise ValueError('Unsupported MCQ VRAM rectangle')
    words = struct.unpack_from('<' + str(vw * vh) + 'H', data, offset)

    def word(x, y):
        if not 0 <= x < vw or not 0 <= y < vh:
            raise ValueError('MCQ samples outside its uploaded VRAM rectangle')
        return words[y * vw + x]

    # McqBuilder receives a VRAM X offset divisible by 64; decode relative to it.
    page_x, page_y = u & 0x3c0, v & 0x100
    u = u * 4 & 0xfc
    image = Image.new('RGBA', (width, height))
    pixels = image.load()
    for x in range(0, width, 16):
        for y in range(0, height, 16):
            palette = [word(cx + i, cy) for i in range(16)]
            for dy in range(16):
                for dx in range(16):
                    tx, ty = (u + dx) & 255, (v + dy) & 255
                    packed = word(page_x + tx // 4, page_y + ty)
                    colour = palette[packed >> ((tx & 3) * 4) & 15]
                    pixels[x + dx, y + dy] = ((colour & 31) << 3, (colour >> 5 & 31) << 3, (colour >> 10 & 31) << 3, 255 if colour else 0)
            v = v + 16 & 0xf0
            if not v:
                u = u + 16 & 0xfc
                if not u:
                    page_x += 64
            cy = cy + 1 & 255
            if not cy:
                cx += 16
            cy |= page_y
    return image


def field_usage(source):
    text = (source / 'src/main/java/legend/game/submap/RetailSubmap.java').read_text()
    def ints(name):
        body = re.search(name + r'\s*=\s*\{(.*?)\};', text, re.S)[1]
        return list(map(int, re.findall(r'-?\d+', re.sub(r'//[^\n]*', '', body))))
    locations = ints('cutToSubmap_800d610c')
    item = (source / 'src/main/java/legend/game/SItem.java').read_text()
    names = re.findall(r'"([^"]*)"', re.search(r'submapNames_8011c108\s*=\s*\{(.*?)\};', item, re.S)[1])
    body = re.search(r'encounterData_800f64c4\s*=\s*\{(.*?)\};', text, re.S)[1]
    usage = collections.defaultdict(list)
    for cut, values in enumerate(re.findall(r'new SubmapEncounterData_04\((\d+),\s*(\d+),\s*(\d+)\)', body)):
        _, rate, stage = map(int, values)
        if rate:
            usage[stage].append({'cut': cut, 'location': names[locations[cut]], 'randomEncounterRate': rate})
    return usage


def export(files, source, output):
    output.mkdir(parents=True, exist_ok=False)
    usage = field_usage(source)
    assets = {}
    for stage in range(128):
        path = files / 'SECT/DRGN0.BIN' / str(2497 + stage) / '1'
        if not path.is_file() or path.stat().st_size == 0:
            continue
        data = path.read_bytes()
        width, height = struct.unpack_from('<HH', data, 20)
        if min(width, height) < 16:
            continue
        key = hashlib.sha256(data).hexdigest()
        if key not in assets:
            image = decode(data)
            image.save(output / (key + '.png'))
            artwork_key = hashlib.sha256(struct.pack('<II', *image.size) + image.tobytes()).hexdigest()
            assets[key] = {'id': 'battle-sky-' + key, 'sourceMcqSha256': key, 'decodedRgbaSha256': artwork_key, 'uniformColour': image.getpixel((0, 0)) if len(image.getcolors(image.width * image.height)) == 1 else None, 'sourceFiles': [], 'stages': [], 'fieldUsage': [], 'sourceSize': list(image.size), 'targetSize': [image.width * 4, image.height * 4], 'status': 'source-decoded', 'review': None}
        assets[key]['sourceFiles'].append(str(path.relative_to(files)))
        assets[key]['stages'].append(stage)
        assets[key]['fieldUsage'].extend(usage.get(stage, []))
    (output / 'inventory.json').write_text(json.dumps(list(assets.values()), indent=2) + '\n')
    rows = (len(assets) + 5) // 6
    board = Image.new('RGB', (6 * 224, rows * 178), (25, 28, 28))
    draw = ImageDraw.Draw(board)
    for index, asset in enumerate(assets.values()):
        x, y = index % 6 * 224, index // 6 * 178
        with Image.open(output / (asset['sourceMcqSha256'] + '.png')) as im:
            im.thumbnail((214, 140))
            board.paste(im, (x + (224 - im.width) // 2, y), im)
        locations = ', '.join(sorted({u['location'] for u in asset['fieldUsage']}))
        draw.text((x + 5, y + 142), 'Stage ' + ','.join(map(str, asset['stages'])), fill='white')
        draw.text((x + 5, y + 157), locations[:32] or 'Script/world-map use needs mapping', fill=(190, 207, 192))
    board.save(output / 'sources-contact-sheet.png')
    return len(assets)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print('Decoded', export(args.files, args.source, args.output), 'unique battle sky images')
