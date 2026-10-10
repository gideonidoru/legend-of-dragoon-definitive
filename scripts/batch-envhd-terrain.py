#!/usr/bin/env python3
"""Census and restore static world materials from their final uploaded VRAM state.

Private source images, inference inputs and logs never enter the public checkout.
This creates review candidates, not a runtime selection or gameplay acceptance.
"""
import argparse
import hashlib
import io
import json
import struct
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
REGIONS = ['South Serdio', 'North Serdio', 'Tiberoa', 'Illisa Bay',
           'Mille Seseau', 'Gloriano', 'Death Frontier', 'Endiness']
PINS = {
    'engineSha256': 'c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc',
    'weightsSha256': '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf',
    'parametersSha256': '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def bounded_read(path, limit=1024 * 1024):
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Oversized source')
    return data


def block(data, offset):
    if offset + 12 > len(data):
        raise ValueError('Truncated TIM block')
    size, x, y, w, h = struct.unpack_from('<I4H', data, offset)
    if not w or not h or size != 12 + w * h * 2 or offset + size > len(data):
        raise ValueError('Invalid TIM block size')
    if x + w > 1024 or y + h > 512:
        raise ValueError('TIM block outside VRAM')
    words = np.frombuffer(data, dtype='<u2', count=w * h, offset=offset + 12).reshape(h, w)
    return dict(x=x, y=y, w=w, h=h, words=words), offset + size


def decode_tim(data):
    if len(data) < 8:
        raise ValueError('Truncated TIM header')
    magic, flags = struct.unpack_from('<II', data)
    if magic != 16 or flags not in (8, 9):
        raise ValueError('World material requires a paletted 4/8-bit TIM')
    palette, offset = block(data, 8)
    image, end = block(data, offset)
    if end != len(data):
        raise ValueError('Unexpected TIM trailing bytes')
    return dict(bpp=flags & 3, palette=palette, image=image)


def upload_bank(sources):
    vram = np.zeros((512, 1024), dtype=np.uint16)
    written = np.zeros(vram.shape, dtype=bool)
    tims = []
    for index, data in sources:
        if not data:
            continue
        tim = decode_tim(data)
        tim.update(file=index, sourceSha256=digest(data))
        # Tim.uploadToGpu uploads image first, then CLUT; directory order matters.
        for rect in (tim['image'], tim['palette']):
            selection = np.s_[rect['y']:rect['y'] + rect['h'], rect['x']:rect['x'] + rect['w']]
            vram[selection] = rect['words']
            written[selection] = True
        tims.append(tim)
    return vram, written, tims


def static_materials(data):
    if len(data) < 12:
        raise ValueError('Truncated world TMD')
    magic, flags, count = struct.unpack_from('<III', data)
    if magic != 65 or flags != 0 or not 2 <= count <= 128 or 12 + count * 28 > len(data):
        raise ValueError('Unsupported world TMD header')
    materials = {}
    # Part zero is animated ocean, including its scrolling texture and changing CLUT.
    for part in range(1, count):
        offset, faces = struct.unpack_from('<II', data, 12 + 28 * part + 16)
        position = 12 + offset
        if faces > 100000:
            raise ValueError('Unbounded TMD primitive count')
        for _ in range(faces):
            if position + 4 > len(data):
                raise ValueError('Truncated TMD primitive')
            header = struct.unpack_from('<I', data, position)[0]
            mode = header >> 24
            payload = (header >> 8 & 255) * 4
            end = position + 4 + payload
            if not payload or end > len(data):
                raise ValueError('Invalid TMD primitive length')
            if mode & 4:
                vertices = 4 if mode & 8 else 3
                if mode & 0xe0 != 0x20 or payload < vertices * 4:
                    raise ValueError('Unsupported textured primitive')
                clut = struct.unpack_from('<H', data, position + 6)[0]
                page = struct.unpack_from('<H', data, position + 10)[0] & 0x19f
                if page >> 7 & 3 not in (0, 1):
                    raise ValueError('Unsupported world material BPP')
                item = materials.setdefault((page, clut), dict(parts=set(), uv=[]))
                item['parts'].add(part)
                item['uv'].extend((data[position + 4 + j * 4], data[position + 5 + j * 4]) for j in range(vertices))
            position = end
    return materials


def decode_material(vram, written, tim, page, clut):
    divisor = 4 if (page >> 7 & 3) == 0 else 2
    rect = tim['image']
    image_selection = np.s_[rect['y']:rect['y'] + rect['h'], rect['x']:rect['x'] + rect['w']]
    cx, cy, colours = (clut & 63) * 16, clut >> 6, 16 if divisor == 4 else 256
    if cy >= 512 or cx + colours > 1024 or not written[cy, cx:cx + colours].all() or not written[image_selection].all():
        raise ValueError('Material references uninitialized VRAM')
    words = vram[image_selection]
    indices = np.empty((rect['h'], rect['w'] * divisor), dtype=np.uint16)
    bits = 4 if divisor == 4 else 8
    for component in range(divisor):
        indices[:, component::divisor] = (words >> (component * bits)) & ((1 << bits) - 1)
    values = vram[cy, cx:cx + colours][indices].astype(np.uint32)
    # Indexed TMD shader uses /31, unlike CPU thumbnail decoding's <<3.
    rgba = np.stack(((values & 31) * 255 // 31, ((values >> 5) & 31) * 255 // 31,
                     ((values >> 10) & 31) * 255 // 31, (values >> 15) * 255), axis=2).astype(np.uint8)
    return Image.fromarray(rgba)


def fingerprint(image):
    return digest(struct.pack('<II', *image.size) + image.tobytes())


def census(files):
    base = files / 'SECT/DRGN0.BIN'
    scenes, masters = [], {}
    for bank, region in enumerate(REGIONS):
        directory = base / str(5697 + bank)
        paths = sorted((p for p in directory.iterdir() if p.name.isdigit()), key=lambda p: int(p.name))
        if [int(p.name) for p in paths] != list(range(len(paths))) or not 1 <= len(paths) <= 256:
            raise ValueError('World bank must use complete ordered numeric files')
        sources = [(int(p.name), bounded_read(p)) for p in paths]
        model = bounded_read(base / str(5705 + bank))
        vram, written, tims = upload_bank(sources)
        bank_hash = digest(b''.join(struct.pack('<II', index, len(data)) + data for index, data in sources))
        scene = dict(bank=bank, region=region, modelSourceFile=f'SECT/DRGN0.BIN/{5705 + bank}',
                     modelSha256=digest(model), bankSourceDirectory=f'SECT/DRGN0.BIN/{5697 + bank}',
                     bankSha256=bank_hash, bankFiles=[dict(file=index, sha256=digest(data), size=len(data)) for index, data in sources],
                     materials=[], retainedAnimatedParts=[0])
        for (page, clut), item in sorted(static_materials(model).items()):
            divisor = 4 if (page >> 7 & 3) == 0 else 2
            px, py = (page & 15) * 64, (page & 16) * 16
            matches = [t for t in tims if t['bpp'] == (page >> 7 & 3) and
                       all(t['image']['x'] <= px + u // divisor < t['image']['x'] + t['image']['w'] and
                           t['image']['y'] <= py + v < t['image']['y'] + t['image']['h'] for u, v in item['uv'])]
            uv = item['uv']
            binding = dict(page=page, clut=clut, parts=sorted(item['parts']),
                           uvBounds=[min(u for u,v in uv), min(v for u,v in uv), max(u for u,v in uv), max(v for u,v in uv)])
            if len(matches) != 1:
                binding.update(status='held-native-uv-mapping', reason='UVs do not fit exactly one uploaded source rectangle')
            else:
                tim = matches[0]
                image = decode_material(vram, written, tim, page, clut)
                key = fingerprint(image)
                rect = tim['image']
                binding.update(status='candidate-pending', decodedRgbaSha256=key, imageFile=tim['file'], imageSourceSha256=tim['sourceSha256'],
                               sourceSize=list(image.size), sourceUvOrigin=[(rect['x'] - px) * divisor, rect['y'] - py])
                master = masters.setdefault(key, dict(decodedRgbaSha256=key, sourceSize=list(image.size), image=image, bindings=[]))
                master['bindings'].append(dict(bank=bank, region=region, page=page, clut=clut, imageFile=tim['file'], parts=binding['parts']))
            scene['materials'].append(binding)
        scenes.append(scene)
    return scenes, masters


def preserve_stp(source, pixels):
    original = np.asarray(source.convert('RGBA'))
    if pixels.dtype != np.uint8 or pixels.shape != (source.height * 4, source.width * 4, 3):
        raise ValueError('Incorrect source-bound 4x RGB candidate')
    if not np.isin(original[:, :, 3], (0, 255)).all():
        raise ValueError('Source alpha must represent binary STP')
    original = np.repeat(np.repeat(original, 4, axis=0), 4, axis=1)
    result = np.dstack((pixels, original[:, :, 3])).astype(np.uint8)
    source_black = np.all(original[:, :, :3] == 0, axis=2)
    result[source_black, :3] = 0
    new_black = ~source_black & np.all(result[:, :, :3] == 0, axis=2)
    result[new_black, :3] = original[new_black, :3]
    preview = result.copy()
    preview[:, :, 3] = np.where(np.any(result != 0, axis=2), 255, 0)
    return Image.fromarray(result), Image.fromarray(preview)


def candidate_record(item, output_hash):
    original = item['image']
    record = {k:v for k,v in item.items() if k != 'image'}
    record.update(targetSize=[original.width * 4, original.height * 4], outputSha256=output_hash,
                  method='real-esrgan-x4plus-clamped-source-layout', scale=4,
                  sourceVisibility='exact-nearest-4x-stp-discard-and-visible-black', edgeAveraging=False,
                  reviewStatus='pending-source-intent-style-layout-review', **PINS)
    return record


def validate_completed(item, record, path):
    data = bounded_read(path, 32 * 1024 * 1024)
    validate_completed_bytes(item, record, data)


def validate_completed_bytes(item, record, data):
    if len(data) > 32 * 1024 * 1024:
        raise ValueError('Oversized candidate')
    if record != candidate_record(item, digest(data)):
        raise ValueError('Previously completed candidate metadata changed')
    if len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[24:26] != bytes((8,6)):
        raise ValueError('Candidate requires 8-bit RGBA PNG')
    with Image.open(io.BytesIO(data)) as decoded:
        original = item['image']
        if decoded.mode != 'RGBA' or decoded.size != (original.width * 4, original.height * 4):
            raise ValueError('Previously completed candidate dimensions changed')
        actual = np.asarray(decoded)
        preserved, _ = preserve_stp(original, actual[:,:,:3])
        if not np.array_equal(actual, np.asarray(preserved)):
            raise ValueError('Previously completed candidate coverage changed')


def execute(files, output, engine, models):
    destination = output.resolve()
    if destination.is_relative_to(ROOT) or destination.is_relative_to(files.resolve()):
        raise ValueError('Use private staging outside Git and extraction')
    for path, key in [(engine, 'engineSha256'), (models / 'realesrgan-x4plus.bin', 'weightsSha256'),
                      (models / 'realesrgan-x4plus.param', 'parametersSha256')]:
        if digest(path.read_bytes()) != PINS[key]:
            raise ValueError('Pinned inference identity differs')
    scenes, masters = census(files)
    plan = dict(schema=1, scriptSha256=digest(Path(__file__).read_bytes()), scenes=scenes,
                masters=[{k:v for k,v in item.items() if k != 'image'} for item in masters.values()])
    plan_bytes = (json.dumps(plan, indent=2) + '\n').encode()
    output.mkdir(parents=True, exist_ok=True)
    plan_path = output / 'source-plan.json'
    if plan_path.exists() and plan_path.read_bytes() != plan_bytes:
        raise ValueError('Private batch plan changed; use a fresh output directory')
    plan_path.write_bytes(plan_bytes)
    work = output / 'private-work'
    work.mkdir(exist_ok=True)
    previews = output / 'private-previews'
    previews.mkdir(exist_ok=True)
    records_path = output / 'candidates.json'
    records = json.loads(records_path.read_text()) if records_path.exists() else []
    if len({r['decodedRgbaSha256'] for r in records}) != len(records):
        raise ValueError('Duplicate private candidates')
    completed = {r['decodedRgbaSha256']: r for r in records}
    for key in completed:
        if key not in masters:
            raise ValueError('Previously completed candidate source changed')
        validate_completed(masters[key], completed[key], output / (key + '.png'))
    for key, item in masters.items():
        if key in completed:
            continue
        original = item['image']
        original.save(work / (key + '-source-stp.png'))
        Image.fromarray(np.pad(np.asarray(original.convert('RGB')), ((16,16),(16,16),(0,0)), mode='edge')).save(work / (key + '-input.png'))
        run = subprocess.run([str(engine), '-i', str(work / (key + '-input.png')), '-o', str(work / (key + '-inference.png')),
                              '-m', str(models), '-n', 'realesrgan-x4plus', '-s', '4', '-t', '256', '-j', '1:1:1'], capture_output=True, timeout=180)
        (work / (key + '.log')).write_bytes(run.stdout + run.stderr)
        if run.returncode:
            raise RuntimeError('Inference failed; see private log')
        with Image.open(work / (key + '-inference.png')) as inferred:
            if inferred.size != ((original.width + 32) * 4, (original.height + 32) * 4):
                raise ValueError('Inference dimensions changed')
            pixels = np.asarray(inferred.convert('RGB').crop((64,64,64 + original.width * 4,64 + original.height * 4)))
        restored, preview = preserve_stp(original, pixels)
        restored.save(output / (key + '.png'))
        preview.save(previews / (key + '.png'))
        record = candidate_record(item, digest((output / (key + '.png')).read_bytes()))
        records.append(record)
        temporary = records_path.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(records, indent=2) + '\n')
        temporary.replace(records_path)
        print(json.dumps(dict(completed=len(records), total=len(masters), regions=sorted({b['region'] for b in item['bindings']}))), flush=True)
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    parser.add_argument('--execute', action='store_true')
    args = parser.parse_args()
    if args.execute:
        if not all((args.output, args.engine, args.models)):
            parser.error('Execution requires private output, engine and models')
        execute(args.files, args.output, args.engine, args.models)
    else:
        scenes, masters = census(args.files)
        report = dict(schema=1, scenes=scenes, masters=[{k:v for k,v in m.items() if k != 'image'} for m in masters.values()])
        data = json.dumps(report, indent=2) + '\n'
        if args.output:
            if args.output.resolve().is_relative_to(ROOT) or args.output.resolve().is_relative_to(args.files.resolve()):
                parser.error('Census output must stay private')
            args.output.write_text(data)
        else:
            print(data)
