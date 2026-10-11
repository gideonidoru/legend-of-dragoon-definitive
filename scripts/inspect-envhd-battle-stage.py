#!/usr/bin/env python3
"""Private static geometry/UV comparisons; never native rendering or art acceptance.

No game process, window, authored camera, lighting, blending, animation or mip proof.
Original source pixels and mesh previews must remain outside the repository.
"""
import argparse
import importlib.util
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('battle_stage_presentation', Path(__file__).with_name('verify-envhd-battle-presentation.py'))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)
battle = verify.battle


def geometry(data):
    battle.references(data)  # Validate source container and bounded primitive ranges.
    offset = battle.take(data, 0, '<I')[0]
    count = battle.take(data, offset + 8, '<I')[0]
    table = offset + 12
    if sum(battle.take(data, table + i * 28 + 4, '<I')[0] for i in range(count)) > 50000:
        raise ValueError('Aggregate stage vertex budget exceeded')
    parts = []
    for part in range(count):
        vo, nv, no, nn, po, nf, scale = battle.take(data, table + part * 28, '<7I')
        if scale != 0 or nv > 20000 or nf > 20000:
            raise ValueError('Static preview requires bounded unscaled parts')
        vertices = np.array([battle.take(data, table + vo + i * 8, '<3h') for i in range(nv)], dtype=float).reshape(-1, 3)
        faces = []
        position = table + po
        for _ in range(nf):
            header = battle.take(data, position, '<I')[0]
            mode, size = header >> 24, (header >> 8 & 255) * 4
            textured, lit, gouraud, shaded = bool(mode & 4), not bool(mode & 1), bool(mode & 16), bool(header & 0x40000)
            n = 4 if mode & 8 else 3
            if textured and shaded or not textured and not lit:
                raise ValueError('Unsupported preview colour packet layout')
            packet = data[position + 4:position + 4 + size]
            uv = np.array([(packet[i * 4], packet[i * 4 + 1]) for i in range(n)], dtype=float) if textured else None
            material = (battle.take(packet, 6, '<H')[0] & 0x19f, battle.take(packet, 2, '<H')[0]) if textured else None
            cursor = n * 4 if textured else 0
            if shaded or not lit:
                colours = np.array([battle.take(packet, cursor + i * 4, '<3B') for i in range(n)], dtype=float)
                cursor += n * 4
            elif not textured:
                colours = np.tile(battle.take(packet, cursor, '<3B'), (n, 1)).astype(float)
                cursor += 4
            else:
                colours = np.full((n, 3), 128.)
            indices = []
            for i in range(n):
                if lit and (gouraud or i == 0):
                    normal = battle.take(packet, cursor, '<H')[0]
                    if normal >= nn:
                        raise ValueError('Invalid preview normal reference')
                    cursor += 2
                vertex = battle.take(packet, cursor, '<H')[0]
                if vertex >= nv:
                    raise ValueError('Invalid preview vertex reference')
                indices.append(vertex)
                cursor += 2
            faces.append((indices, uv, material, colours))
            position += 4 + size
        parts.append((vertices, faces))
    if not parts or not any(len(p[0]) for p in parts) or sum(len(p[1]) for p in parts) > 20000:
        raise ValueError('No bounded static stage geometry')
    return parts


def coordinates(uv, origin, scale, shape):
    # Quantize before integer source translation, matching the source-bound map.
    xy = np.floor(uv * scale).astype(int) - np.array(origin, dtype=int) * scale
    return np.clip(xy[..., 0], 0, shape[1] - 1), np.clip(xy[..., 1], 0, shape[0] - 1)


def sample(texture, uv):
    pixels, origin, scale = texture
    x, y = coordinates(uv, origin, scale, pixels.shape)
    return pixels[y, x]


def visible(pixels):
    return np.any(pixels[..., :3] != 0, axis=-1) | (pixels[..., 3] != 0)


def render(parts, textures, sources, yaw, pitch, size=(800, 600)):
    if (len(size) != 2 or any(type(v) is not int or not 64 <= v <= 1600 for v in size)
            or size[0] * size[1] > 1600000 or not all(math.isfinite(v) for v in (yaw, pitch))):
        raise ValueError('Invalid preview camera or resolution')
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    rotation = np.array([[cy, 0, sy], [sp * sy, cp, -sp * cy], [-cp * sy, sp, cp * cy]])
    poses = [vertices @ rotation.T for vertices, _ in parts]
    all_vertices = np.concatenate(poses)
    low, high = all_vertices[:, :2].min(axis=0), all_vertices[:, :2].max(axis=0)
    fit = min((size[0] - 48) / max(1, high[0] - low[0]), (size[1] - 48) / max(1, high[1] - low[1]))
    centre = (np.array(size) - (low + high) * fit) / 2
    canvas = np.full((size[1], size[0], 3), (29, 35, 37), dtype=np.uint8)
    depth_buffer = np.full((size[1], size[0]), np.inf)
    for (_, faces), vertices in zip(parts, poses):
        projected = vertices[:, :2] * fit + centre
        for indices, uv, material, colours in faces:
            triangles = [(0, 1, 2), (1, 3, 2)] if len(indices) == 4 else [(0, 1, 2)]
            for triangle in triangles:
                ti = list(triangle)
                vi = np.array(indices)[ti]
                points = projected[vi]
                a, b, c = points
                divisor = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(divisor) < 1e-8:
                    continue
                lo = np.maximum(np.floor(points.min(axis=0)).astype(int), 0)
                hi = np.minimum(np.ceil(points.max(axis=0)).astype(int), np.array(size) - 1)
                if np.any(hi < lo):
                    continue
                xx, yy = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
                wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / divisor
                wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / divisor
                weights = np.stack((wa, wb, 1 - wa - wb), axis=-1)
                depth = weights @ vertices[vi, 2]
                old_depth = depth_buffer[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
                mask = (weights.min(axis=-1) >= -1e-8) & (depth < old_depth)
                rgb = weights @ colours[ti]
                if material is not None:
                    coords = weights @ uv[ti]
                    original = sample(sources[material], coords)
                    colour = sample(textures[material], coords)
                    mask &= visible(original)  # Source ownership/class controls discard.
                    rgb = colour[..., :3] * rgb / 128.
                target = canvas[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
                target[mask] = np.clip(rgb[mask], 0, 255).astype(np.uint8)
                old_depth[mask] = depth[mask]
    return Image.fromarray(canvas)


def stage_textures(model, tim, scene, selected, paintings):
    vram, written, tims = battle.terrain.upload_bank([(0, tim)])
    rect = tims[0]['image']
    sources = {}
    for m in scene['materials']:
        page, clut = m['page'], m['clut']
        divisor = 4 if (page >> 7 & 3) == 0 else 2
        origin = ((rect['x'] - (page & 15) * 64) * divisor, rect['y'] - (page & 16) * 16)
        # Incomplete palettes/unsupported sources fail explicitly, never invent pixels.
        image = battle.terrain.decode_material(vram, written, tims[0], page, clut)
        sources[page, clut] = (np.array(image), origin, 1)
    existing = dict(sources)
    if selected:
        size=selected.get('atlasSize')
        if (not isinstance(size,list) or len(size)!=2
                or any(type(v) is not int or not 1<=v<=4096 for v in size)):
            raise ValueError('Selected stage atlas exceeds bounded dimensions')
        if any(selected[k] != scene[k] for k in ('stage', 'modelSha256', 'timSha256')) or selected['scale'] != 4:
            raise ValueError('Selected stage identity changed')
        path = ROOT / 'integrations/envhd/runtime-assets/envhd/battle-materials' / scene['modelSha256'] / 'atlas-v1.png'
        data = battle.terrain.bounded_read(path, 32 * 1024 * 1024)
        if battle.terrain.digest(data) != selected['atlasSha256']:
            raise ValueError('Selected stage atlas changed')
        if (len(data)<33 or data[:8]!=b'\x89PNG\r\n\x1a\n'
                or data[8:16]!=struct.pack('>I',13)+b'IHDR' or data[24:26]!=bytes((8,6))
                or list(struct.unpack_from('>2I',data,16))!=size):
            raise ValueError('Selected stage atlas header differs from bounded dimensions')
        with Image.open(io.BytesIO(data)) as im:
            if im.format!='PNG' or im.mode != 'RGBA' or list(im.size) != size:
                raise ValueError('Selected stage atlas layout changed')
            for region in selected['regions']:
                x, y, w, h = region['atlasRect']
                binding = next((m for m in scene['materials'] if (m['page'], m['clut']) == (region['page'], region['clut'])), None)
                if (binding is None or binding['status'] != 'candidate-pending'
                        or any(region[k] != binding[k] for k in ('sourceCrop', 'sourceUvOrigin', 'sourceSize', 'decodedRgbaSha256'))
                        or (w, h) != tuple(v * 4 for v in binding['sourceSize'])
                        or min(x, y) < 0 or x + w > im.width or y + h > im.height):
                    raise ValueError('Selected stage region was rebound or exceeds its atlas')
                existing[region['page'], region['clut']] = (np.array(im.crop((x, y, x + w, y + h))), region['sourceUvOrigin'], 4)
    proposed = dict(existing)
    for m in scene['materials']:
        key = m.get('decodedRgbaSha256')
        if key in paintings:
            if m['status'] != 'candidate-pending':
                raise ValueError('A proposed texture is not an eligible static surface')
            proposed[m['page'], m['clut']] = (paintings[key], m['sourceUvOrigin'], 4)
    return sources, existing, proposed


def inspect(files, output, stages):
    if (output.resolve().is_relative_to(ROOT) or output.exists()
            or any((parent / '.git').exists() for parent in (output.resolve(), *output.resolve().parents))):
        raise ValueError('Use a new private output outside the source checkout')
    production = ROOT / 'integrations/envhd/production'
    checked = verify.preflight(files, production / 'battle-presentation-artwork-v1.json')
    paintings = {v['candidate']['decodedRgbaSha256']: v['image'] for v in checked['verified']}
    scenes, _, _ = battle.census(files)
    selection_path = production / 'battle-material-runtime-artwork.json'
    selection_data = battle.terrain.bounded_read(selection_path,32*1024*1024)
    selection_digest = battle.terrain.digest(selection_data)
    selected = json.loads(selection_data)
    entries = {s['stage']: s for s in scenes}
    allocations = {s['stage']: s for s in selected['scenes']}
    if len(stages) != len(set(stages)) or not stages or any(s not in entries for s in stages):
        raise ValueError('Choose unique nonempty source stages')
    output.mkdir(parents=True)
    reports = []
    for stage in stages:
        scene = entries[stage]
        model, tim = [battle.terrain.bounded_read(files / scene[k]) for k in ('modelSourceFile', 'timSourceFile')]
        if any(battle.terrain.digest(data) != scene[key] for data, key in ((model, 'modelSha256'), (tim, 'timSha256'))):
            raise ValueError('Stage source changed after census')
        parts = geometry(model)
        variants = stage_textures(model, tim, scene, allocations.get(stage), paintings)
        boards = []
        for view, yaw in enumerate((0., .65, - .65)):
            board = Image.new('RGB', (2400, 630), (24, 28, 28))
            draw = ImageDraw.Draw(board)
            for col, textures in enumerate(variants):
                board.paste(render(parts, textures, variants[0], yaw, -.4), (col * 800, 30))
                draw.text((col * 800 + 8, 8), f'Stage {stage} | view {view} | ' + ['Native first TIM', 'Existing EnvHD selection', 'Unselected bespoke proposal'][col], fill='white')
            name = f'stage-{stage:03}-view-{view}.png'
            board.save(output / name)
            boards.append(dict(path=name, sha256=battle.terrain.digest((output / name).read_bytes())))
        reports.append(dict(stage=stage, modelSha256=scene['modelSha256'], timSha256=scene['timSha256'], parts=len(parts), faces=sum(len(p[1]) for p in parts), outputs=boards))
    checks = [(selection_path,selection_digest),
        (production / 'battle-presentation-artwork-v1.json',checked['manifestSha256']),
        (production / 'battle-material-artwork.json',checked['sourceArtworkLedgerSha256'])]
    for path,digest in checks:
        if battle.terrain.digest(battle.terrain.bounded_read(path,32*1024*1024)) != digest:
            raise ValueError('Static comparison inputs changed during inspection')
    report = dict(schema=1, selectionSha256=selection_digest, pipeline='private-battle-stage-static-uv-inspection-1', presentationReceipt=checked['manifestSha256'],
        scope='Orthographic software rasterization of untransformed stage geometry and first TIM state only. No native cameras, lighting, blend passes, animations, UV offsets, filtering/mips, GPU/Deck or art acceptance.',
        nativeAccepted=False, finalQualityAccepted=False, stages=reports)
    battle.write_json(output / 'manifest.json', report)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--stage', type=int, action='append', required=True)
    args = parser.parse_args()
    print('Private static stage comparisons:', len(inspect(args.files, args.output, args.stage)['stages']))
