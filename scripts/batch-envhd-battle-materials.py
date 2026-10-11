#!/usr/bin/env python3
"""Restore unowned static battle surfaces; private sources and candidates stay outside Git.

Uses original page/CLUT coordinates and the stage's actual animated VRAM rectangles.
This produces review candidates, never a runtime selection or gameplay acceptance.
"""
import argparse
import importlib.util
import json
import struct
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('terrain', Path(__file__).with_name('batch-envhd-terrain.py'))
terrain = importlib.util.module_from_spec(spec)
spec.loader.exec_module(terrain)
PADDING = 8
PILOT_STAGES = (0, 6)


def take(data, offset, fmt):
    if offset < 0 or offset + struct.calcsize(fmt) > len(data):
        raise ValueError('Truncated battle container')
    return struct.unpack_from(fmt, data, offset)


def references(data):
    model_offset, clut_animation, extra = take(data, 0, '<III')
    magic, flags, parts = take(data, model_offset, '<III')
    if model_offset < 12 or magic != 65 or flags != 0 or not 1 <= parts <= 256:
        raise ValueError('Unsupported battle TMD container')
    base = model_offset + 12
    take(data, base, '<' + 'I' * (parts * 7))
    materials = {}
    total_faces = 0
    for part in range(parts):
        vertices, nv, normals, nn, packets, count, scale = take(data, base + part * 28, '<7I')
        total_faces += count
        if total_faces > 100000 or nv > 100000 or nn > 100000:
            raise ValueError('Unbounded battle geometry')
        if base + vertices + nv * 8 > len(data) or base + normals + nn * 8 > len(data):
            raise ValueError('Battle geometry outside container')
        position = base + packets
        for _ in range(count):
            header, = take(data, position, '<I')
            mode, size = header >> 24, (header >> 8 & 255) * 4
            if mode & 0xe0 != 0x20 or not 4 <= size <= 64 or position + 4 + size > len(data):
                raise ValueError('Unsupported battle polygon packet')
            if mode & 4:
                vertices = 4 if mode & 8 else 3
                if size < vertices * 4:
                    raise ValueError('Truncated battle UV packet')
                clut, = take(data, position + 6, '<H')
                page, = take(data, position + 10, '<H')
                page &= 0x19f  # ABR is a face blend mode, not a different texture.
                material = materials.setdefault((page, clut), dict(parts=set(), uv=[], faces=0))
                material['parts'].add(part)
                material['faces'] += 1
                material['uv'].extend((data[position + 4 + i * 4], data[position + 5 + i * 4]) for i in range(vertices))
            position += 4 + size
    rectangles = []
    if extra:
        if extra < 12:
            raise ValueError('Invalid battle animation subfile')
        for slot in range(10):
            offset, = take(data, extra + slot * 4, '<I')
            if offset < 40:
                raise ValueError('Battle animation overlaps its pointer table')
            position = extra + offset
            values = []
            for i in range(4096):
                value, = take(data, position + i * 2, '<h')
                values.append(value)
                if value == -1:
                    break
            else:
                raise ValueError('Unterminated battle texture animation')
            if values[0] == -1:
                continue
            if len(values) < 7 or len(values) % 2 != 1:
                raise ValueError('Invalid battle texture animation sequence')
            x, y, width, height = values[:4]
            if width < 4 or width % 4 or height < 1 or x < 0 or y < 0 or x + width // 4 > 1024 or y + height > 512:
                raise ValueError('Battle animated rectangle outside VRAM')
            # Battle.applyBattleStageTextureAnimations divides its width by four.
            rectangles.append(dict(slot=slot, rect=[x, y, width // 4, height], sequenceSha256=terrain.digest(struct.pack('<' + 'h' * len(values), *values))))
    return parts, materials, rectangles, bool(clut_animation)


def overlaps(a, b):
    x, y, w, h = a
    bx, by, bw, bh = b
    return x < bx + bw and bx < x + w and y < by + bh and by < y + h


def padded_bounds(sampled, image_rect, divisor):
    x, y, w, h = sampled
    rect = image_rect
    margin = (PADDING + divisor - 1) // divisor
    left, top = max(rect['x'], x - margin), max(rect['y'], y - PADDING)
    right, bottom = min(rect['x'] + rect['w'], x + w + margin), min(rect['y'] + rect['h'], y + h + PADDING)
    return [left, top, right - left, bottom - top]


def scene(model, tim, stage):
    parts, materials, animations, clut_animation = references(model)
    vram, written, tims = terrain.upload_bank([(0, tim)])
    texture = tims[0]
    rect = texture['image']
    bindings, masters = [], {}
    for (page, clut), material in sorted(materials.items()):
        uv = material['uv']
        u0, v0, u1, v1 = min(u for u, v in uv), min(v for u, v in uv), max(u for u, v in uv), max(v for u, v in uv)
        binding = dict(page=page, clut=clut, parts=sorted(material['parts']), faces=material['faces'], uvBounds=[u0, v0, u1, v1])
        bpp = page >> 7 & 3
        if bpp not in (0, 1):
            binding.update(status='held-native-bpp', reason='Only source-bound indexed 4/8-bit materials are supported')
        else:
            divisor = 4 if bpp == 0 else 2
            px, py = (page & 15) * 64, (page & 16) * 16
            sampled = [px + u0 // divisor, py + v0, u1 // divisor - u0 // divisor + 1, v1 - v0 + 1]
            palette_bounds = [(clut & 63) * 16, clut >> 6, 16 if bpp == 0 else 256, 1]
            x, y, w, h = sampled
            binding['sampledVramBounds'] = sampled
            if not (rect['x'] <= x and x + w <= rect['x'] + rect['w'] and rect['y'] <= y and y + h <= rect['y'] + rect['h']):
                binding.update(status='held-native-uv-mapping', reason='UV bounds do not fit the uploaded image rectangle')
            elif clut_animation:
                binding.update(status='held-native-clut-animation', reason='Container has palette animation')
            elif any(overlaps(dependency, touched)
                     for dependency in (padded_bounds(sampled, rect, divisor), palette_bounds)
                     for a in animations for touched in (a['rect'], [960, 256, a['rect'][2], a['rect'][3]])):
                binding.update(status='held-native-texture-animation', reason='Image/padding or referenced palette intersects animated VRAM or its scratch writes')
            else:
                try:
                    source = terrain.decode_material(vram, written, texture, page, clut)
                except ValueError:
                    binding.update(status='held-native-incomplete-source', reason='The stage TIM does not initialize the entire referenced palette/image')
                else:
                    origin_u, origin_v = (rect['x'] - px) * divisor, rect['y'] - py
                    crop = [max(0, u0 - origin_u - PADDING), max(0, v0 - origin_v - PADDING),
                            min(source.width, u1 - origin_u + PADDING + 1), min(source.height, v1 - origin_v + PADDING + 1)]
                    image = source.crop(crop)
                    key = terrain.fingerprint(image)
                    uniform = bool(np.all(np.asarray(image) == np.asarray(image)[0, 0]))
                    status = 'protected-existing-pilot' if stage in PILOT_STAGES else 'uniform-native-exemption' if uniform else 'candidate-pending'
                    binding.update(status=status, sourceCrop=crop, sourceUvOrigin=[origin_u + crop[0], origin_v + crop[1]],
                                   sourceSize=list(image.size), decodedRgbaSha256=key)
                    if status == 'candidate-pending':
                        master = masters.setdefault(key, dict(decodedRgbaSha256=key, sourceSize=list(image.size), image=image, bindings=[]))
                        master['bindings'].append(dict(stage=stage, page=page, clut=clut, parts=binding['parts'], sourceCrop=crop, sourceUvOrigin=binding['sourceUvOrigin']))
        bindings.append(binding)
    return dict(stage=stage, modelSha256=terrain.digest(model), timSha256=terrain.digest(tim), parts=parts,
                imageRect={k:v for k,v in rect.items() if k != 'words'}, textureAnimations=animations,
                hasClutAnimation=clut_animation, materials=bindings), masters


def census(files):
    base = files / 'SECT/DRGN0.BIN'
    scenes, masters, empty = [], {}, []
    for stage in range(128):
        directory = base / str(2497 + stage)
        model_path = directory / '0/0'
        if not model_path.exists():
            raise ValueError(f'Missing stage model slot {stage}; refresh extraction')
        model = terrain.bounded_read(model_path)
        if not model:
            empty.append(stage)
            continue
        tim = terrain.bounded_read(directory / '2')
        if not tim:
            raise ValueError(f'Nonempty battle model has no texture: {stage}')
        current, items = scene(model, tim, stage)
        current['modelSourceFile'] = f'SECT/DRGN0.BIN/{2497 + stage}/0/0'
        current['timSourceFile'] = f'SECT/DRGN0.BIN/{2497 + stage}/2'
        scenes.append(current)
        for key, item in items.items():
            if key in masters:
                if masters[key]['sourceSize'] != item['sourceSize'] or masters[key]['image'].tobytes() != item['image'].tobytes():
                    raise ValueError('Battle decoded hash collision')
                masters[key]['bindings'].extend(item['bindings'])
            else:
                masters[key] = item
    return scenes, masters, empty


def plan(scenes, masters, empty):
    return dict(schema=1, scope='Private battle surface candidates, no runtime/native/final acceptance',
                scriptSha256=terrain.digest(Path(__file__).read_bytes()),
                dependencySha256=terrain.digest(Path(__file__).with_name('batch-envhd-terrain.py').read_bytes()),
                paddingSourceTexels=PADDING, protectedPilotStages=list(PILOT_STAGES), scenes=scenes, emptyModelSlots=empty,
                masters=[{k:v for k,v in m.items() if k != 'image'} for m in masters.values()])


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    if path.is_symlink() or temporary.is_symlink():
        raise ValueError('Refusing a redirected private control file')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def private_destination(files, output):
    if output.resolve().is_relative_to(ROOT) or output.resolve().is_relative_to(files.resolve()):
        raise ValueError('Source plans, originals and unreviewed candidates must stay outside Git and extraction')


def staging_path(root, relative):
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Private staging child redirects outside its root')
    current = root
    for component in Path(relative).parts:
        current = current / component
        if current.is_symlink():
            raise ValueError('Refusing a symlinked private staging child')
    return path


def read_control(path):
    return json.loads(terrain.bounded_read(path, 4 * 1024 * 1024))


def execute(files, output, engine, models):
    private_destination(files, output)
    for path, key in [(engine, 'engineSha256'), (models / 'realesrgan-x4plus.bin', 'weightsSha256'),
                      (models / 'realesrgan-x4plus.param', 'parametersSha256')]:
        if terrain.digest(path.read_bytes()) != terrain.PINS[key]:
            raise ValueError('Pinned inference identity differs')
    scenes, masters, empty = census(files)
    source_plan = plan(scenes, masters, empty)
    output.mkdir(parents=True, exist_ok=True)
    plan_path = staging_path(output, 'source-plan.json')
    if plan_path.exists() and read_control(plan_path) != source_plan:
        raise ValueError('Private battle plan changed; use fresh staging')
    write_json(plan_path, source_plan)
    work, previews = staging_path(output, 'private-work'), staging_path(output, 'private-previews')
    work.mkdir(exist_ok=True)
    previews.mkdir(exist_ok=True)
    records_path = staging_path(output, 'candidates.json')
    records = read_control(records_path) if records_path.exists() else []
    completed = {r['decodedRgbaSha256']: r for r in records}
    if len(completed) != len(records) or not completed.keys() <= masters.keys():
        raise ValueError('Duplicate or unknown completed battle candidate')
    for key, record in completed.items():
        terrain.validate_completed(masters[key], record, staging_path(output, key + '.png'))
    for key, item in masters.items():
        if key in completed:
            continue
        original = item['image']
        source_path = staging_path(output, 'private-work/' + key + '-source-stp.png')
        input_path = staging_path(output, 'private-work/' + key + '-input.png')
        inferred_path = staging_path(output, 'private-work/' + key + '-inference.png')
        log_path = staging_path(output, 'private-work/' + key + '.log')
        png_path = staging_path(output, key + '.png')
        preview_path = staging_path(output, 'private-previews/' + key + '.png')
        original.save(source_path)
        Image.fromarray(np.pad(np.asarray(original.convert('RGB')), ((16,16),(16,16),(0,0)), mode='edge')).save(input_path)
        run = subprocess.run([str(engine), '-i', str(input_path), '-o', str(inferred_path),
                              '-m', str(models), '-n', 'realesrgan-x4plus', '-s', '4', '-t', '256', '-j', '1:1:1'], capture_output=True, timeout=180)
        log_path.write_bytes(run.stdout + run.stderr)
        if run.returncode:
            raise RuntimeError('Battle inference failed; retained private log explains why')
        with Image.open(inferred_path) as inferred:
            if inferred.size != ((original.width + 32) * 4, (original.height + 32) * 4):
                raise ValueError('Battle inference dimensions changed')
            pixels = np.asarray(inferred.convert('RGB').crop((64,64,64 + original.width * 4,64 + original.height * 4)))
        restored, preview = terrain.preserve_stp(original, pixels)
        restored.save(png_path)
        preview.save(preview_path)
        record = terrain.candidate_record(item, terrain.digest(terrain.bounded_read(png_path, 32 * 1024 * 1024)))
        terrain.validate_completed(item, record, png_path)
        records.append(record)
        write_json(records_path, records)
        print(json.dumps(dict(completed=len(records), total=len(masters), stages=sorted({b['stage'] for b in item['bindings']}))), flush=True)
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    parser.add_argument('--execute', action='store_true')
    args = parser.parse_args()
    if args.execute:
        if not (args.engine and args.models):
            parser.error('Execution needs pinned engine and models')
        execute(args.files, args.output, args.engine, args.models)
    else:
        private_destination(args.files, args.output)
        scenes, masters, empty = census(args.files)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        write_json(args.output, plan(scenes, masters, empty))
        counts = {}
        for s in scenes:
            for binding in s['materials']:
                status = binding['status']
                counts[status] = counts.get(status, 0) + 1
        print(json.dumps(dict(scenes=len(scenes), masters=len(masters), bindings=counts, emptySlots=len(empty))))
