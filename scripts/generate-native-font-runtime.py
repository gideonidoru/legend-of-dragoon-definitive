#!/usr/bin/env python3
"""Build prepared native-font coverage PNGs against local, checksum-pinned game sources.

The output directory must be new. Only custom PNGs and the source-bound catalog are
runtime deliverables; palette previews and the build receipt remain diagnostic files.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_bytes(path):
    # Read at most the source-probe limit before allocating or parsing caller inputs.
    with path.open('rb') as stream:
        data = stream.read(1024 * 1024 + 1)
    if len(data) > 1024 * 1024:
        raise ValueError('Original source exceeds 1 MiB')
    return data


def tim(data, offset=0):
    if struct.unpack_from('<II', data, offset) != (16, 8):
        raise ValueError('Expected indexed 4bpp TIM with palette')
    clut_length = struct.unpack_from('<I', data, offset + 8)[0]
    image_offset = offset + 8 + clut_length
    image_length = struct.unpack_from('<I', data, image_offset)[0]
    if clut_length < 12 or image_length < 12 or image_offset + image_length > len(data):
        raise ValueError('Invalid TIM bounds')
    palette_rect = struct.unpack_from('<4H', data, offset + 12)
    image_rect = struct.unpack_from('<4H', data, image_offset + 4)
    palette = data[offset + 20:image_offset]
    pixels = data[image_offset + 12:image_offset + image_length]
    if len(palette) != palette_rect[2] * palette_rect[3] * 2 or len(pixels) != image_rect[2] * image_rect[3] * 2:
        raise ValueError('Invalid TIM geometry')
    return palette, image_rect, pixels


def combined(image, palette):
    _, rect, pixels = tim(image)
    if not palette or len(palette) % 32 or len(palette) > 512:
        raise ValueError('Invalid combined palette')
    return (struct.pack('<III4H', 16, 8, 12 + len(palette), 0, 0, 16, len(palette) // 32)
            + palette + struct.pack('<I4H', 12 + len(pixels), *rect) + pixels)


def indices(source, crop):
    _, rect, pixels = tim(source)
    u, v, w, h = crop
    if min(u, v) < 0 or min(w, h) < 1 or u + w > rect[2] * 4 or v + h > rect[3]:
        raise ValueError('Crop outside source')
    return bytes((pixels[(v + y) * rect[2] * 2 + (u + x) // 2] >> ((u + x) % 2 * 4)) & 15
                 for y in range(h) for x in range(w))


def colours(source, row):
    palette, _, _ = tim(source)
    result = []
    for word in struct.unpack_from('<16H', palette, row * 32):
        components = [(word >> shift) & 31 for shift in (0, 5, 10)]
        result.append(''.join(f'{(value << 3) | (value >> 2):02x}' for value in components))
    return ','.join(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True, help='Private extracted files directory (read only)')
    parser.add_argument('--output', type=Path, required=True, help='New staging/proof directory')
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = args.output.absolute()
    if any(path.is_symlink() for path in [output, *output.parents]):
        raise ValueError('Linked output path')
    output.mkdir(exist_ok=False)
    resources = output / 'resources'
    previews = output / 'previews'
    resources.mkdir()
    previews.mkdir()
    source_dir = args.files / 'SECT/DRGN0.BIN'
    pages = {i: source_bytes(source_dir / f'4113/{i}') for i in range(6)}
    menu = source_bytes(source_dir / '6665')
    source_events = {f'battle_hud_{i}': combined(pages[0], tim(pages[i])[0]) for i in pages}
    extra = tim(menu, 0x10460)[0][:4 * 32] + tim(menu, 0x10580)[0][:9 * 32]
    source_events.update(menu=menu, menu_numeric_extras=combined(menu, extra))
    catalog = json.loads((root / 'integrations/uihd/src/main/resources/uihd/font/native-sprites.json').read_text())
    # This builder starts from the accepted small-digit catalog, never its own expanded output.
    catalog['sprites'] = [entry for entry in catalog['sprites']
                          if entry['id'].startswith('native-small-') and entry['family'] == 'battle_hud_4']
    if len(catalog['sprites']) != 20:
        raise ValueError('Expected twenty accepted small-digit selectors')
    postbattle_small = []
    for entry in list(catalog['sprites']):
        if entry['palette'] != 0:
            continue
        alias = dict(entry, id=entry['resource'].removesuffix('.png') + '-postbattle', family='battle_hud_2',
                     palette=1, sourceSha256=digest(source_events['battle_hud_2']),
                     sourcePaletteSha256=digest(pages[2]))
        # The exact indexed crop is shared; only the live physical palette differs.
        if indices(source_events['battle_hud_2'], alias['crop']) != indices(source_events['battle_hud_4'], entry['crop']):
            raise ValueError('Postbattle small-digit source differs')
        catalog['sprites'].append(alias)
        postbattle_small.append(alias['id'])
    packets = ['native-battle-labels-v1', 'native-battle-large-v1', 'native-addition-name-v1',
               'native-battle-level-perfect-v1', 'native-menu-numeric-v1']
    art_receipt = []
    java = str(args.java_home / 'bin/java') if args.java_home else 'java'
    javac = str(args.java_home / 'bin/javac') if args.java_home else 'javac'
    with tempfile.TemporaryDirectory(prefix='definitive-native-font-') as classes:
        subprocess.run([javac, '-d', classes, str(root / 'scripts/RenderNativeSpriteFont.java')], check=True)
        for packet_name in packets:
            packet_dir = root / 'integrations/uihd/production/font' / packet_name
            packet = json.loads((packet_dir / 'review-packet.json').read_text())
            for pin in packet.get('sources', []):
                if digest(source_bytes(args.files / pin['path'].removeprefix('files/'))) != pin['sha256']:
                    raise ValueError('Original source pin differs')
            for glyph in packet['glyphs']:
                svg = packet_dir / glyph['path']
                if digest(svg.read_bytes()) != glyph.get('sha256', glyph.get('svgSha256')):
                    raise ValueError(f'Authored vector pin differs: {svg.name}')
                crop = glyph.get('crop', glyph.get('metricCrop'))
                if packet_name == 'native-menu-numeric-v1':
                    if digest(menu) != packet['sourcePins']['menuTimSha256']:
                        raise ValueError('Menu source pin differs')
                    bindings = [('menu', row) for row in range(15)] + [('menu_numeric_extras', row) for row in range(3)]
                elif packet_name == 'native-addition-name-v1':
                    if digest(pages[0]) != packet['source']['sha256']:
                        raise ValueError('Addition source pin differs')
                    bindings = [('battle_hud_0', 10)]
                elif packet_name == 'native-battle-labels-v1':
                    if digest(pages[0]) != packet['sourceImageSha256'] or digest(pages[2]) != packet['sourcePaletteSha256']:
                        raise ValueError('HUD label source pin differs')
                    bindings = [('battle_hud_2', row) for row in glyph['paletteRows']]
                else:
                    aliases = glyph.get('sourcePaletteAliases', [{'file': glyph['paletteFile'], 'row': glyph['paletteRow']}])
                    bindings = [(f"battle_hud_{alias['file']}", alias['row']) for alias in aliases]
                    if packet_name == 'native-battle-large-v1' and glyph['id'] != 'MISS' and not glyph['id'].startswith('word-'):
                        # SP trail scripts vary packed CLUT values; enumerate only real uploaded banks.
                        # Each alias still requires its own exact source hash and physical draw coordinates.
                        bindings = [(f'battle_hud_{bank}', row) for bank in range(6) for row in range(16)]
                original = indices(source_events[bindings[0][0]], crop)
                pin = glyph.get('sourceIndexCropSha256', glyph.get('sourceIndexSha256', glyph.get('cropSha256')))
                if pin and digest(original) != pin:
                    raise ValueError(f'Indexed source crop differs: {glyph["id"]}')
                name = packet_name.removeprefix('native-').removesuffix('-v1') + '-' + glyph['id'].lower()
                resource = name + '.png'
                subprocess.run([java, '-Djava.awt.headless=true', '-cp', classes, 'RenderNativeSpriteFont',
                                str(svg), str(resources / resource), str(previews / resource), '8',
                                colours(source_events[bindings[0][0]], bindings[0][1])], check=True)
                png = (resources / resource).read_bytes()
                size = list(struct.unpack_from('>2I', png, 16))
                for family, palette in dict.fromkeys(bindings):
                    catalog['sprites'].append({'id': f'{name}-{family}-row{palette}', 'family': family,
                        'sourceSha256': digest(source_events[family]), 'crop': crop, 'palette': palette,
                        'outputSha256': digest(png), 'outputSize': size, 'resource': resource})
                art_receipt.append({'packet': packet_name, 'glyph': glyph['id'], 'svgSha256': digest(svg.read_bytes()),
                                    'resource': resource, 'outputSha256': digest(png), 'outputSize': size,
                                    'sourceIndexCropSha256': digest(original), 'selectors': len(dict.fromkeys(bindings))})
                if packet_name == 'native-battle-level-perfect-v1' and glyph['id'].startswith('level-'):
                    # Postbattle UiFile glyphs65..70 retain an 8px crop around the same 7px lettering.
                    # Retain native right-column shadow; never stretch approved strokes or caller metrics.
                    svg_bytes = svg.read_bytes()
                    marker = b'viewBox="0 0 7 16"'
                    if svg_bytes.count(marker) != 1 or crop[2:] != [7, 16]:
                        raise ValueError('Postbattle LEVEL canvas source differs')
                    variant_name = name + '-postbattle'
                    variant_svg = previews / (variant_name + '.svg')
                    variant_resource = variant_name + '.png'
                    variant_crop = [*crop[:2], 8, 16]
                    variant_indices = indices(source_events['battle_hud_2'], variant_crop)
                    right_column = variant_indices[7::8]
                    if set(right_column) - {0, 15}:
                        raise ValueError('Postbattle right column is not transparent/native shadow')
                    shadow = ''.join(f'M7 {y} L8 {y} L8 {y + 1} L7 {y + 1} Z '
                                     for y, index in enumerate(right_column) if index == 15)
                    extension = f'<path data-palette-index="15" fill="#fff" d="{shadow}"/>'.encode() if shadow else b''
                    if svg_bytes.count(b'</svg>') != 1:
                        raise ValueError('Postbattle vector root differs')
                    variant_svg.write_bytes(svg_bytes.replace(marker, b'viewBox="0 0 8 16"').replace(b'</svg>', extension + b'</svg>'))
                    subprocess.run([java, '-Djava.awt.headless=true', '-cp', classes, 'RenderNativeSpriteFont',
                                    str(variant_svg), str(resources / variant_resource), str(previews / variant_resource),
                                    '8', colours(source_events['battle_hud_2'], 1)], check=True)
                    variant_png = (resources / variant_resource).read_bytes()
                    catalog['sprites'].append({'id': variant_name, 'family': 'battle_hud_2',
                        'sourceSha256': digest(source_events['battle_hud_2']), 'crop': variant_crop, 'palette': 1,
                        'outputSha256': digest(variant_png), 'outputSize': [64, 128], 'resource': variant_resource})
                    art_receipt[-1]['postbattleCanvasAlias'] = {'resource': variant_resource,
                        'outputSha256': digest(variant_png), 'crop': variant_crop,
                        'sourceIndexCropSha256': digest(variant_indices),
                        'rightColumnSourceRolesSha256': digest(right_column),
                        'derivation': 'unchanged 7px strokes on 8px canvas with exact native right-column shadow; crop/advance retained'}
    if len(art_receipt) != 119:
        raise ValueError('Prepared artwork inventory differs')
    identities = {(entry['family'], entry['palette'], tuple(entry['crop'])) for entry in catalog['sprites']}
    if len(identities) != len(catalog['sprites']) or len(identities) > 2048:
        raise ValueError('Duplicate or excessive runtime selectors')
    (resources / 'native-sprites.json').write_text(json.dumps(catalog, indent=2) + '\n')
    (output / 'receipt.json').write_text(json.dumps({'customResources': art_receipt, 'selectorCount': len(identities),
        'sharedSmallPostbattleAliases': postbattle_small,
        'generatorSha256': digest(Path(__file__).read_bytes()),
        'rendererSha256': digest((root / 'scripts/RenderNativeSpriteFont.java').read_bytes()),
        'catalogSha256': digest((resources / 'native-sprites.json').read_bytes())}, indent=2) + '\n')
    print(f'Prepared {len(art_receipt)} custom resources; {len(identities)} source/palette/crop selectors')


if __name__ == '__main__':
    main()
