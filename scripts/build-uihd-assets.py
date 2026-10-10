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


def native(data, palette, crop):
    if len(data) < 20 or struct.unpack_from('<2I', data) != (16, 8):
        raise ValueError('Native UI requires indexed TIM')
    block = struct.unpack_from('<I', data, 8)[0]
    cx, cy, cw, ch = struct.unpack_from('<4H', data, 12)
    if block != 12 + cw * ch * 2 or cw != 16 or not 1 <= ch <= 16:
        raise ValueError('Unexpected UI palette layout')
    offset = 8 + block
    size, ix, iy, words, height = struct.unpack_from('<I4H', data, offset)
    if size != 12 + words * height * 2 or offset + size > len(data) or not 1 <= words <= 128 or not 1 <= height <= 512:
        raise ValueError('Unexpected UI image layout')
    x, y, w, h = crop
    if not 0 <= palette < ch or min(x, y) < 0 or min(w, h) < 1 or x + w > words * 4 or y + h > height:
        raise ValueError('UI crop outside indexed artwork')
    packed = np.frombuffer(data[offset + 12:offset + size], dtype=np.uint8).reshape(height, words * 2)
    indices = np.stack((packed & 15, packed >> 4), axis=-1).reshape(height, words * 4)[y:y+h, x:x+w]
    colours = np.frombuffer(data[20 + palette * 32:52 + palette * 32], dtype='<u2')[indices]
    pixels = np.stack(((colours & 31)*255//31, (colours >> 5 & 31)*255//31,
                       (colours >> 10 & 31)*255//31, np.where(colours & 0x8000, 255, 0)), axis=-1).astype(np.uint8)
    return Image.fromarray(pixels)


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
    for family, path, offset, crop, scale in [
        ('menu', 'SECT/DRGN0.BIN/6665', 0, (128, 48, 48, 32), 4),
        ('items', 'SECT/DRGN0.BIN/6665', 0x6200, (0, 0, 128, 64), 2),
        ('dialogue', 'SECT/DRGN0.BIN/6669/3', 0, (0, 0, 64, 46), 4),
        ('dialogue_arrow', 'SECT/DRGN0.BIN/6669/4', 0, (0, 0, 112, 14), 4)]:
        data = read(args.files / path)[offset:]
        count = struct.unpack_from('<H', data, 18)[0]
        for palette in range(count):
            image = native(data, palette, crop)
            result.append(({'id': family + '-' + str(palette), 'kind': 'native', 'family': family,
                            'sourcePath': path, 'sourceOffset': offset, 'sourceSha256': digest(data),
                            'palette': palette, 'crop': list(crop)}, image, scale, None))
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
        candidate = restore(original, args, entry['id'], scale, entry['kind'] == 'native', tile)
        for box in PROTECTED.get(entry['id'], []):
            candidate.paste(original.crop(box).resize(((box[2]-box[0])*scale, (box[3]-box[1])*scale), Image.Resampling.NEAREST), (box[0]*scale, box[1]*scale))
        candidate = preserve(original, candidate, scale, entry['kind'] == 'native')
        entry['protectedSourceRegions'] = PROTECTED.get(entry['id'], [])
        coverage = np.dstack((np.asarray(original)[:,:,3], np.all(np.asarray(original)[:,:,:3] == 0, axis=2).astype(np.uint8))) if entry['kind'] == 'native' else np.asarray(original)[:,:,3:4]
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
    execute(parser.parse_args())
