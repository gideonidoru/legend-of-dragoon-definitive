#!/usr/bin/env python3
"""Produce source-faithful UIHD candidates with the approved offline batch method.
No downloads, no game launches, no installed selection changes. Original-derived
inference inputs/logs remain in an explicit private folder; only custom PNGs and
source/tool identities may enter the public mod after human/developer review.
"""
import argparse
import hashlib
import json
import struct
import subprocess
from pathlib import Path
import numpy as np
from PIL import Image

ENGINE = 'c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc'
WEIGHTS = '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf'
PARAMETERS = '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86'
SYMBOLS = 'dark divine earth fire light magic physical thunder water wind'.split()
ROOT = Path(__file__).resolve().parents[1]
PROTECTED = {"lavitzs_picture": [[7, 6, 25, 26]], "war_bulletin": [[0, 0, 32, 9]]}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read(path, limit=4 * 1024 * 1024):
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError('UI source exceeds bounds')
    return data


def native(data, palette, crop, atlas=False):
    if len(data) < 20 or struct.unpack_from('<2I', data) != (16, 8):
        raise ValueError('Native UI requires indexed TIM')
    block = struct.unpack_from('<I', data, 8)[0]
    cx, cy, cw, ch = struct.unpack_from('<4H', data, 12)
    if block != 12 + cw * ch * 2 or cw not in (16, 256) or not 1 <= ch <= 16:
        raise ValueError('Unexpected UI palette layout')
    offset = 8 + block
    size, ix, iy, words, height = struct.unpack_from('<I4H', data, offset)
    if offset + 12 + words * height * 2 > len(data) or not 1 <= words <= 128 or not 1 <= height <= 512:
        raise ValueError('Unexpected UI image layout')
    x, y, w, h = crop
    if not 0 <= palette < ch or min(x, y) < 0 or min(w, h) < 1 or x + w > words * 4 or y + h > height:
        raise ValueError('UI crop outside indexed artwork')
    packed = np.frombuffer(data[offset + 12:offset + 12 + words * height * 2], dtype=np.uint8).reshape(height, words * 2)
    indices = np.stack((packed & 15, packed >> 4), axis=-1).reshape(height, words * 4)[y:y+h, x:x+w]
    colours = np.frombuffer(data[20 + palette * cw * 2:52 + palette * cw * 2], dtype='<u2')[indices]
    scale_colour = (lambda c: c * 8) if atlas else (lambda c: c * 255 // 31)
    pixels = np.stack((scale_colour(colours & 31), scale_colour(colours >> 5 & 31),
                       scale_colour(colours >> 10 & 31), np.where(colours & 0x8000, 255, 0)), axis=-1).astype(np.uint8)
    return Image.fromarray(pixels)


def direct_tim(data, palette_data):
    flags = struct.unpack_from('<I', data,4)[0]
    bpp = flags & 3
    if flags not in (8,9): raise ValueError('Direct UI source is not indexed')
    offset = 8 + struct.unpack_from('<I',data,8)[0]
    _,_,_,words,height = struct.unpack_from('<I4H',data,offset)
    packed = np.frombuffer(data[offset+12:offset+12+words*height*2],dtype=np.uint8).reshape(height,words*2)
    indices = np.stack((packed & 15, packed >> 4),axis=-1).reshape(height,words*4) if bpp==0 else packed
    palette_width = struct.unpack_from('<H',palette_data,16)[0]
    colours = np.frombuffer(palette_data[20:20+palette_width*2],dtype='<u2')[indices]
    return Image.fromarray(np.stack(((colours&31)*8, (colours>>5&31)*8, (colours>>10&31)*8,
                                    np.where(colours&0x8000,255,0)),axis=-1).astype(np.uint8))


def mcq(data):
    _,offset,vw,vh,cx,cy,u,v,width,height=struct.unpack_from('<2I8H',data)
    words=np.frombuffer(data[offset:],dtype='<u2')
    def word(x,y):
        if min(x,y)<0 or x>=vw or y>=vh or y*vw+x>=len(words): raise ValueError('UI MCQ samples outside source')
        return int(words[y*vw+x])
    page_x=u&0x3c0;page_y=v&0x100;u=u*4&0xfc
    rgba=np.zeros((height,width,4),dtype=np.uint8)
    for x in range(0,width,16):
        for y in range(0,height,16):
            palette=[word(cx+i,cy) for i in range(16)]
            for dy in range(16):
                for dx in range(16):
                    tx=(u+dx)&255;ty=(v+dy)&255
                    c=int(palette[word(page_x+tx//4,page_y+ty)>>((tx&3)*4)&15])
                    rgba[y+dy,x+dx]=((c&31)*8,(c>>5&31)*8,(c>>10&31)*8,255 if c else 0)
            v=(v+16)&0xf0
            if v==0:
                u=(u+16)&0xfc
                if u==0: page_x+=64
            cy=(cy+1)&255
            if cy==0: cx+=16
            cy|=page_y
    return Image.fromarray(rgba)


def preserve(source, rgb, scale, indexed=False):
    original = np.asarray(source.convert('RGBA'))
    expected = np.repeat(np.repeat(original, scale, axis=0), scale, axis=1)
    output = np.asarray(rgb.convert('RGB')).copy()
    if output.shape != expected[:, :, :3].shape:
        raise ValueError('Output differs from integer source scale')
    protected = np.all(expected[:, :, :3] == 0, axis=-1) if indexed else expected[:, :, 3] == 0
    output[protected] = 0
    new_black = ~protected & np.all(output == 0, axis=-1)
    output[new_black] = expected[new_black, :3]
    return Image.fromarray(np.dstack((output, expected[:, :, 3])))


def restore(source, args, name, scale, indexed=False, tile=None, strength=0.7):
    if tile:
        out = Image.new('RGBA', (source.width * scale, source.height * scale))
        for y in range(0, source.height, tile):
            for x in range(0, source.width, tile):
                part = source.crop((x, y, x + tile, y + tile))
                restored = restore(part, args, f'{name}-{x}-{y}', scale, indexed, strength=strength)
                out.paste(restored, (x * scale, y * scale))
        return out
    pixels = np.asarray(source.convert('RGBA'))
    visible = np.any(pixels[:, :, :3] != 0, axis=-1) if indexed else pixels[:, :, 3] != 0
    nearest = source.resize((source.width * scale, source.height * scale), Image.Resampling.NEAREST)
    if not visible.any():
        return nearest
    # Fill discarded RGB from nearest visible edge before inference. This avoids
    # learning black halos; visibility is restored exactly after reconstruction.
    rgb = pixels[:, :, :3].copy()
    known = visible.copy()
    for _ in range(max(source.size)):
        if known.all():
            break
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            shifted = np.roll(known, (dy, dx), axis=(0, 1))
            if dy == -1: shifted[-1] = False
            if dy == 1: shifted[0] = False
            if dx == -1: shifted[:, -1] = False
            if dx == 1: shifted[:, 0] = False
            fill = ~known & shifted
            rgb[fill] = np.roll(rgb, (dy, dx), axis=(0, 1))[fill]
            known[fill] = True
    padded = np.pad(rgb, ((8, 8), (8, 8), (0, 0)), mode='edge')
    src, dst = args.work / f'{name}-input.png', args.work / f'{name}-inference.png'
    Image.fromarray(padded).save(src)
    run = subprocess.run([str(args.engine), '-i', str(src), '-o', str(dst), '-m', str(args.models),
                          '-n', 'realesrgan-x4plus', '-s', '4', '-t', '256', '-j', '1:1:1'], capture_output=True, timeout=180)
    (args.work / f'{name}.log').write_bytes(run.stdout + run.stderr)
    if run.returncode:
        raise RuntimeError('UI inference failed; see private log')
    with Image.open(dst) as inferred:
        if inferred.size != ((source.width + 16) * 4, (source.height + 16) * 4):
            raise ValueError('Inference dimensions differ')
        generated = inferred.crop((32, 32, 32 + source.width * 4, 32 + source.height * 4)).convert('RGB')
        if scale != 4:
            generated = generated.resize(nearest.size, Image.Resampling.LANCZOS)
    result = Image.blend(nearest.convert('RGB'), generated, strength)
    return preserve(source, result, scale, indexed)


def palette_data(data):
    return data[20:8 + struct.unpack_from('<I', data, 8)[0]]


def with_palette(data, colours):
    offset = 8 + struct.unpack_from('<I', data, 8)[0]
    size = struct.unpack_from('<I', data, offset)[0]
    return struct.pack('<3I4H', 16, 8, 12 + len(colours), 0, 0, 16, len(colours) // 32) + colours + data[offset:offset+size]


def jobs(args):
    result = []
    for source in sorted((args.source / 'gfx/goods').glob('*.png')):
        data = read(source)
        with Image.open(source) as image:
            result.append(({'id': source.stem, 'kind': 'atlas', 'registryId': 'lod:' + source.stem,
                            'sourcePath': 'gfx/goods/' + source.name, 'sourceSha256': digest(data)}, image.convert('RGBA'), 4, None))
    for name in SYMBOLS + ['battle_icons', 'ui']:
        source = args.source / 'gfx/ui' / (name + '.png')
        data = read(source)
        with Image.open(source) as image:
            result.append(({'id': name, 'kind': 'png', 'sourcePath': 'gfx/ui/' + source.name,
                            'sourceSha256': digest(data)}, image.convert('RGBA'), 4, 16 if name in ('battle_icons', 'ui') else None))
    for name in 'dart lavitz shana rose haschel albert meru kongol miranda'.split():
        path = 'characters/' + name + '/portrait.png'
        data = read(args.files / path)
        with Image.open(args.files / path) as image:
            result.append(({'id': 'portrait-' + name, 'kind': 'atlas', 'registryId': 'lod:' + name,
                            'sourcePath': path, 'sourceSha256': digest(data)}, image.convert('RGBA'), 4, None))
    image_path, palette_path = 'SECT/DRGN0.BIN/4113/0', 'SECT/DRGN0.BIN/4113/5'
    page, colours = read(args.files / image_path), read(args.files / palette_path)
    spirit_page = with_palette(page, palette_data(colours))
    for index, element in enumerate('fire wind light dark thunder water earth divine'.split()):
        for frame in range(3):
            crop = (80, 64 + frame * 16, 16, 16)
            image = native(spirit_page, 8 + index, crop, atlas=True)
            result.append(({'id': 'spirit-' + element + '-' + str(frame), 'kind': 'atlas-native',
                            'registryId': 'lod:' + element + '_' + str(frame), 'sourcePath': image_path,
                            'paletteSourcePath': palette_path, 'sourceSha256': digest(page),
                            'paletteSourceSha256': digest(colours), 'crop': list(crop), 'palette': 8 + index}, image, 4, None))
    for i in range(2):
        crop = (80 + i * 8, 112, 8, 16)
        image = native(spirit_page, 8, crop, atlas=True)
        result.append(({'id': 'spirit-divine-overlay-' + str(i), 'kind': 'atlas-native',
                        'registryId': 'lod:divine_overlay_' + str(i), 'sourcePath': image_path,
                        'paletteSourcePath': palette_path, 'sourceSha256': digest(page),
                        'paletteSourceSha256': digest(colours), 'crop': list(crop), 'palette': 8}, image, 4, None))
    menu = read(args.files / 'SECT/DRGN0.BIN/6665')
    families = []
    for family, path, offset, crop, scale in [
        ('menu', 'SECT/DRGN0.BIN/6665', 0, (0, 0, 256, 192), 2),
        ('items', 'SECT/DRGN0.BIN/6665', 0x6200, (0, 0, 256, 64), 2),
        ('menu_characters', 'SECT/DRGN0.BIN/6665', 0x83e0, (0, 0, 256, 256), 2),
        ('dialogue', 'SECT/DRGN0.BIN/6669/3', 0, (0, 0, 64, 46), 4),
        ('dialogue_arrow', 'SECT/DRGN0.BIN/6669/4', 0, (0, 0, 112, 14), 4)]:
        families.append((family, path, offset, read(args.files / path)[offset:], crop, scale, None))
    extras = palette_data(menu[0x10460:])[:4*32] + palette_data(menu[0x10580:])
    families.append(('menu_character_extras', 'SECT/DRGN0.BIN/6665', 0x83e0,
                     with_palette(menu[0x83e0:], extras), (0, 0, 256, 256), 2, 'final-menu-palette-overlay'))
    for index in range(6):
        path = 'SECT/DRGN0.BIN/4113/' + str(index)
        families.append(('battle_hud_' + str(index), image_path, 0,
                         with_palette(page, palette_data(read(args.files / path))), (0, 0, 256, 256), 2, path))
    basic_path = 'SECT/DRGN0.BIN/6669/0'
    basic_page = read(args.files / basic_path)
    for index in range(3):
        path = 'SECT/DRGN0.BIN/6669/' + str(index)
        families.append(('basic_' + str(index), basic_path, 0,
                         with_palette(basic_page, palette_data(read(args.files / path))), (0, 0, 256, 160), 2, path))
    for family, path, offset, data, crop, scale, palette_path in families:
        count = struct.unpack_from('<H', data, 18)[0]
        for palette in range(count):
            image = native(data, palette, crop)
            result.append(({'id': family + '-' + str(palette), 'kind': 'native', 'family': family,
                            'sourcePath': path, 'sourceOffset': offset, 'sourceSha256': digest(data),
                            'paletteSourcePath': palette_path, 'palette': palette, 'crop': list(crop)}, image, scale, None))
    extra = [('world_map', 'SECT/DRGN0.BIN/5695/0', 2),
             ('indicator_big_arrow', 'SUBMAP/big_arrow.tim', 4),
             ('indicator_small_arrow', 'SUBMAP/small_arrow.tim', 4),
             ('indicator_alert', 'SUBMAP/alert.tim', 4),
             ('the_end', 'SECT/DRGN0.BIN/7610', 4)]
    for chapter in range(4):
        for frame in list(range(6)) + list(range(8, 14)):
            extra.append((f'chapter_{chapter}_{frame}', f'SECT/DRGN0.BIN/{6670+chapter}/{frame}', 4))
    for source in sorted((p for p in (args.files / 'SECT/DRGN0.BIN/5720').iterdir() if p.name.isdigit()), key=lambda p: int(p.name)):
        if source.is_file() and source.stat().st_size:
            extra.append(('credit_' + source.name, 'SECT/DRGN0.BIN/5720/' + source.name, 4))
    for family, path, scale in extra:
        data = read(args.files / path)
        offset = 8 + struct.unpack_from('<I', data, 8)[0]
        _, _, _, words, height = struct.unpack_from('<I4H', data, offset)
        crop = (0, 0, words * 4, height)
        for palette in range(struct.unpack_from('<H', data, 18)[0]):
            result.append(({'id': family + '-' + str(palette), 'kind': 'native', 'family': family,
                           'sourcePath': path, 'sourceOffset': 0, 'sourceSha256': digest(data),
                           'palette': palette, 'crop': list(crop)}, native(data,palette,crop), scale, None))
    path = 'gfx/textures/loading.png'
    data = read(args.source / path)
    with Image.open(args.source / path) as image:
        result.append(({'id':'loading_eye','kind':'png','sourcePath':path,'sourceSha256':digest(data)},image.convert('RGBA'),4,None))
    for family, paths, direction, scale in [
        ('title_background',['SECT/DRGN0.BIN/5718/0','SECT/DRGN0.BIN/5718/1'],'vertical',2),
        ('title_trademark',['SECT/DRGN0.BIN/5718/4'],None,4),
        ('title_copyright',['SECT/DRGN0.BIN/5718/5','SECT/DRGN0.BIN/5718/6'],'horizontal',4)]:
        sources = [read(args.files / p) for p in paths]
        images = [direct_tim(data, sources[0]) for data in sources]
        image = images[0]
        if direction:
            size = (image.width, sum(p.height for p in images)) if direction == 'vertical' else (sum(p.width for p in images),image.height)
            image = Image.new('RGBA',size)
            x = y = 0
            for part in images:
                image.paste(part,(x,y))
                if direction == 'vertical': y += part.height
                else: x += part.width
        result.append(({'id':family,'kind':'raster','family':family,'sourcePath':paths[0],
                       'sourcePaths':paths,'sourceSha256':digest(b''.join(sources))},image,scale,None))
    path = 'SECT/DRGN0.BIN/6667'
    data = read(args.files / path)
    result.append(({'id':'game_over','kind':'raster','family':'game_over','sourcePath':path,
                   'sourceSha256':digest(data)}, mcq(data), 2, None))
    return result


def execute(args):
    if any(p.resolve().is_relative_to(ROOT) or p.resolve().is_relative_to(args.files.resolve()) for p in (args.output, args.work)):
        raise ValueError('Production staging and inference work must remain outside repository and original extraction')
    if args.output.exists() or args.work.exists():
        raise ValueError('Use new output and private work directories')
    if digest(read(args.engine, 128 * 1024 * 1024)) != ENGINE or digest(read(args.models / 'realesrgan-x4plus.bin', 128 * 1024 * 1024)) != WEIGHTS or digest(read(args.models / 'realesrgan-x4plus.param')) != PARAMETERS:
        raise ValueError('Pinned offline tool identities differ')
    args.output.mkdir(parents=True); args.work.mkdir(parents=True)
    records = []
    for entry, original, scale, tile in jobs(args):
        reuse = None
        if args.reuse:
            prior = json.loads((args.reuse / 'catalog.json').read_text())
            reuse = next((e for e in prior['assets'] if e['id'] == entry['id'] and e['sourceSha256'] == entry['sourceSha256']
                          and e['sourceSize'] == list(original.size) and e['scale'] == scale), None)
        if reuse:
            raw = read(args.reuse / 'assets' / reuse['resource'])
            if digest(raw) != reuse['outputSha256']: raise ValueError('Reused artwork identity differs')
            with Image.open(args.reuse / 'assets' / reuse['resource']) as selected: candidate = selected.convert('RGBA')
        else:
            candidate = restore(original, args, entry['id'], scale, entry['kind'] in ('native','raster'), tile)
        for box in PROTECTED.get(entry['id'], []):
            candidate.paste(original.crop(box).resize(((box[2]-box[0])*scale, (box[3]-box[1])*scale), Image.Resampling.NEAREST), (box[0]*scale, box[1]*scale))
        candidate = preserve(original, candidate, scale, entry['kind'] in ('native','raster'))
        entry['protectedSourceRegions'] = PROTECTED.get(entry['id'], [])
        coverage = np.dstack((np.asarray(original)[:,:,3], np.all(np.asarray(original)[:,:,:3] == 0, axis=2).astype(np.uint8))) if entry['kind'] in ('native','raster') else np.asarray(original)[:,:,3:4]
        entry['sourceCoverageSha256'] = digest(coverage.tobytes())
        filename = entry['id'] + '.png'
        candidate.save(args.output / filename)
        entry.update(resource=filename, sourceSize=list(original.size), outputSize=list(candidate.size), scale=scale,
                     method='Real-ESRGAN-x4plus-source-context-blend70-exact-source-coverage',
                     outputSha256=digest(read(args.output / filename)), reviewStatus='pending-visual-review',
                     engineSha256=ENGINE, weightsSha256=WEIGHTS, parametersSha256=PARAMETERS,
                     tileSize=tile, enhancedRgbaBytes=candidate.width * candidate.height * 4)
        records.append(entry)
        (args.output / 'candidates.json').write_text(json.dumps({'assets': records, 'installed': False}, indent=2) + '\n')
        print(json.dumps({'completed': entry['id'], 'count': len(records)}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'files', 'output', 'work', 'engine', 'models'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--reuse', type=Path)
    execute(parser.parse_args())
