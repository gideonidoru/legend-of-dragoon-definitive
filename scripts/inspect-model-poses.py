#!/usr/bin/env python3
"""Private orthographic TMD pose comparisons, AGPL v3; see LICENSE.
Uses original per-face palettes versus candidate STP PNGs with identical geometry/keyframes.
Not the game renderer: no PS1 projection, native lighting, blend modes or interpolation proof.
Requires Pillow 12.3.0 and NumPy 2.5.1. All output must remain outside the source checkout.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import platform
import struct
import sys
import numpy as np
import PIL
from PIL import Image, ImageDraw, ImageFont


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


materials = load_module('materials', 'audit-model-materials.py')
transparency = load_module('transparency', 'preserve-texture-transparency.py')


def rotation(rx, ry, rz):
    """Column-vector Rz * Ry * Rx, matching Keyframe0c.rotationZYX."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    return np.array([[cz*cy, cz*sy*sx-sz*cx, cz*sy*cx+sz*sx], [sz*cy, sz*sy*sx+cz*cx, sz*sy*cx-cz*sx], [-sy, cy*sx, cy*cx]])


def read_geometry(data):
    materials.faces(data)  # Shared bounded TMD/UV validation.
    offset, = materials.take(data, 0, '<I')
    _, _, count = materials.take(data, offset, '<3I')
    table = offset + 12
    if sum(materials.take(data, table + index * 28 + 4, '<I')[0] for index in range(count)) > 50000:
        raise ValueError('Aggregate vertex count exceeds the private pose budget')
    parts = []
    for index in range(count):
        vertex_offset, nv, _, nn, primitive_offset, face_count, scale = materials.take(data, table + index * 28, '<7I')
        if nv > 20000 or scale != 0:
            raise ValueError('Pose inspector requires bounded, unscaled model parts')
        vertices = np.array([materials.take(data, table + vertex_offset + i * 8, '<3h') for i in range(nv)], dtype=float).reshape(-1, 3)
        polygons = []
        position = table + primitive_offset
        for _ in range(face_count):
            header, = materials.take(data, position, '<I')
            mode, size = header >> 24, (header >> 8 & 255) * 4
            textured, lit, gouraud, shaded = bool(mode & 4), not mode & 1, bool(mode & 16), bool(header & 0x40000)
            count_vertices = 4 if mode & 8 else 3
            if textured and shaded or not textured and not lit:
                raise ValueError('Unsupported polygon lighting layout')
            packet = data[position + 4:position + 4 + size]
            cursor = count_vertices * 4 if textured else 0
            uvs = np.array([(packet[i*4], packet[i*4+1]) for i in range(count_vertices)], dtype=float) if textured else None
            clut = materials.take(packet, 2, '<H')[0] if textured else None
            if shaded or not lit:
                colors = [materials.take(packet, cursor + i*4, '<3B') for i in range(count_vertices)]
                cursor += count_vertices * 4
            elif not textured:
                colors = [materials.take(packet, cursor, '<3B')] * count_vertices
                cursor += 4
            else:
                colors = [(128, 128, 128)] * count_vertices
            indices = []
            for i in range(count_vertices):
                if lit and (gouraud or i == 0):
                    normal_index, = materials.take(packet, cursor, '<H')
                    if normal_index >= nn:
                        raise ValueError('Invalid normal reference')
                    cursor += 2
                vertex_index, = materials.take(packet, cursor, '<H')
                cursor += 2
                if vertex_index >= nv:
                    raise ValueError('Invalid vertex reference')
                indices.append(vertex_index)
            polygons.append((indices, uvs, clut, np.array(colors, dtype=float)))
            position += 4 + size
        parts.append((vertices, polygons))
    if sum(len(polygons) for _, polygons in parts) > 5000:
        raise ValueError('Too many polygons for private pose inspection')
    return parts


def read_keyframes(data, part_count):
    magic, = materials.take(data, 0, '<I')
    parts, frames = materials.take(data, 12, '<2H')
    if magic != 12 or parts != part_count or not (2 <= frames <= 512) or frames % 2:
        raise ValueError('Requires matching standard TMD keyframes')
    expected = 16 + frames // 2 * parts * 12
    if len(data) != expected:
        raise ValueError('Animation length differs from its keyframe declaration')
    return np.array([materials.take(data, 16 + i*12, '<6h') for i in range(frames // 2 * parts)], dtype=float).reshape(frames // 2, parts, 6)


def posed_vertices(parts, keyframe, yaw):
    camera = rotation(0, yaw, 0)
    return [(vertices @ rotation(*(transform[:3] * (2 * math.pi / 4096))).T + transform[3:]) @ camera.T for (vertices, _), transform in zip(parts, keyframe)]


def framing_bounds(poses):
    vertices = np.concatenate([part for pose in poses for part in pose])
    if not len(vertices):
        raise ValueError('No model vertices to frame')
    return (vertices[:, 0].min(), vertices[:, 0].max(), vertices[:, 1].min(), vertices[:, 1].max())


def render(parts, positions, tim, candidate, bounds, size=(384, 448)):
    width, height, cw, ch, colors, indices = materials.texture(tim)
    palette = np.array(colors, dtype=np.uint16)
    packed = np.frombuffer(indices[:width * height // 2], dtype=np.uint8)
    index_map = np.column_stack((packed & 15, packed >> 4)).reshape(height, width).astype(np.uint16)
    candidate_pixels = np.array(candidate) if candidate else None
    canvas = np.zeros((size[1], size[0], 3), dtype=np.uint8)
    canvas[:] = (31, 39, 36)
    zbuffer = np.full((size[1], size[0]), np.inf)
    min_x, max_x, min_y, max_y = bounds
    fit = min((size[0]-48) / max(1, max_x-min_x), (size[1]-48) / max(1, max_y-min_y))
    center = np.array(((size[0]-(min_x+max_x)*fit)/2, (size[1]-(min_y+max_y)*fit)/2))
    for (_, polygons), vertices in zip(parts, positions):
        projected = vertices[:, :2] * fit + center
        for references, uvs, clut, colors_rgb in polygons:
            triangles = [(0, 1, 2), (1, 3, 2)] if len(references) == 4 else [(0, 1, 2)]
            for triangle in triangles:
                ref = np.array(references)[list(triangle)]
                coords = projected[ref]
                low = np.maximum(np.floor(coords.min(axis=0)).astype(int), 0)
                high = np.minimum(np.ceil(coords.max(axis=0)).astype(int), np.array(size)-1)
                if np.any(high < low): continue
                xx, yy = np.meshgrid(np.arange(low[0], high[0]+1)+0.5, np.arange(low[1], high[1]+1)+0.5)
                a, b, c = coords
                divisor = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(divisor) < 1e-8: continue
                wa = ((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/divisor
                wb = ((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/divisor
                weights = np.stack((wa, wb, 1-wa-wb), axis=-1)
                depth = weights @ vertices[ref, 2]
                old_depth = zbuffer[low[1]:high[1]+1, low[0]:high[0]+1]
                valid = (weights.min(axis=-1) >= -1e-8) & (depth < old_depth)
                rgb = weights @ colors_rgb[list(triangle)]
                if uvs is not None:
                    uv = weights @ uvs[list(triangle)]
                    if candidate_pixels is None:
                        # Original colors follow each polygon's own CLUT, not a flattened atlas.
                        ux = np.clip(uv[..., 0].astype(int), 0, width-1)
                        vy = np.clip(uv[..., 1].astype(int), 0, height-1)
                        clut_index = ((clut >> 6) & 15)*(cw//16)+(clut & 3)
                        value = palette[clut_index*16+index_map[vy, ux]]
                        tex_rgb = np.stack((value & 31, (value >> 5) & 31, (value >> 10) & 31), axis=-1).astype(float)*255/31
                        valid &= value != 0
                    else:
                        ux = np.clip((uv[..., 0]*candidate.width/width).astype(int), 0, candidate.width-1)
                        vy = np.clip((uv[..., 1]*candidate.height/height).astype(int), 0, candidate.height-1)
                        tex = candidate_pixels[vy, ux]
                        tex_rgb = tex[..., :3]
                        valid &= np.any(tex != 0, axis=-1)
                    rgb = rgb * tex_rgb * (2/255)
                canvas[low[1]:high[1]+1, low[0]:high[0]+1][valid] = np.clip(rgb[valid], 0, 255).astype(np.uint8)
                old_depth[valid] = depth[valid]
    coverage = np.where(np.isfinite(zbuffer), 255, 0).astype(np.uint8)
    return Image.fromarray(np.dstack((canvas, coverage)))


def coverage_delta(original, candidate):
    source = np.array(original.getchannel('A')) != 0
    result = np.array(candidate.getchannel('A')) != 0
    return {'missingPixels': int(np.count_nonzero(source & ~result)), 'extraPixels': int(np.count_nonzero(~source & result))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--tim', type=Path, required=True)
    parser.add_argument('--animation', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, action='append', default=[], help='Private engine-STP PNG; up to three')
    parser.add_argument('--label', action='append', default=[], help='One display label per candidate')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]): parser.error('Use a new private output outside the source checkout')
    if PIL.__version__ != '12.3.0' or np.__version__ != '2.5.1': parser.error('Use the recorded Pillow and NumPy versions')
    if len(args.candidate) > 3: parser.error('Compare at most three candidates')
    if args.label and len(args.label) != len(args.candidate): parser.error('Provide one label for each candidate')
    model, tim, animation = [materials.bounded_read(path) for path in (args.model, args.tim, args.animation)]
    materials.audit(model, tim)
    parts = read_geometry(model)
    keyframes = read_keyframes(animation, len(parts))
    width, height, *_ = materials.texture(tim)
    candidates = []
    for index, path in enumerate(args.candidate):
        image, digest = transparency.read_png(path, 2048)
        if image.width % width or image.height % height or image.width//width not in (2, 4) or image.width//width != image.height//height:
            parser.error('Candidate must match the original TIM aspect and 2x/4x scale')
        if any(alpha not in (0, 255) for alpha in image.getchannel('A').get_flattened_data()):
            parser.error('Candidate must retain binary STP encoding')
        candidates.append((args.label[index] if args.label else path.parent.name, image, digest))
    frame_indices = sorted(set([0, len(keyframes)//2, len(keyframes)-1]))
    poses = [posed_vertices(parts, keyframes[index], yaw) for index, yaw in zip(frame_indices, [0, math.pi/3, -math.pi/3])]
    animation_frames = sorted(set(np.linspace(0, len(keyframes)-1, min(len(keyframes), 16)).astype(int)))
    animation_positions = [posed_vertices(parts, keyframes[frame], 0) for frame in animation_frames]
    bounds = framing_bounds([*poses, *animation_positions])
    args.output.mkdir(exist_ok=False)
    columns = [('Original per-face CLUT', None, None), *candidates]
    board = Image.new('RGB', (32+400*len(columns), 128+490*len(poses)), '#f6f4ef')
    draw = ImageDraw.Draw(board)
    try: font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 18)
    except OSError: font = ImageFont.load_default(size=18)
    draw.text((32, 24), 'Definitive | Private pose inspection', font=font, fill='#244e40')
    draw.text((32, 54), 'Original geometry and keyframes. Offline orthographic inspection.', font=font, fill='#68716b')
    draw.text((32, 78), 'Native rendering and Deck performance remain unverified.', font=font, fill='#68716b')
    hashes = []
    coverage = []
    for row, (frame, positions) in enumerate(zip(frame_indices, poses)):
        original_render = render(parts, positions, tim, None, bounds)
        for col, (name, candidate, _) in enumerate(columns):
            image = render(parts, positions, tim, candidate, bounds)
            filename = f'pose-{frame}-candidate-{col}.png'
            image.save(args.output/filename)
            hashes.append({'path': filename, 'sha256': hashlib.sha256((args.output/filename).read_bytes()).hexdigest()})
            if col:
                coverage.append({'keyframe': frame, 'candidate': col, **coverage_delta(original_render, image)})
            draw.text((32+400*col, 103+490*row), f'Keyframe {frame} / {name[:30]}', font=font, fill='#202b28')
            board.paste(image, (32+400*col, 133+490*row))
    board.save(args.output/'comparison.png')
    animation_images = []
    animation_coverage = []
    for frame, positions in zip(animation_frames, animation_positions):
        sheet = Image.new('RGB', (32+400*len(columns), 512), '#f6f4ef')
        caption = ImageDraw.Draw(sheet)
        original_render = render(parts, positions, tim, None, bounds)
        for col, (name, candidate, _) in enumerate(columns):
            image = render(parts, positions, tim, candidate, bounds)
            caption.text((32+400*col, 16), f'Keyframe {frame} / {name[:30]}', font=font, fill='#244e40')
            sheet.paste(image, (32+400*col, 48))
            if col:
                animation_coverage.append({'keyframe': int(frame), 'candidate': col, **coverage_delta(original_render, image)})
        animation_images.append(sheet)
    animation_images[0].save(args.output/'keyframe-inspection.gif', save_all=True, append_images=animation_images[1:], duration=100, loop=0)
    hashes.extend({'path': filename, 'sha256': hashlib.sha256((args.output/filename).read_bytes()).hexdigest()} for filename in ('comparison.png', 'keyframe-inspection.gif'))
    report = {'pipeline': 'definitive-private-pose-inspection-1', 'environment': platform.platform(), 'pillow': PIL.__version__, 'numpy': np.__version__, 'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'dependencyScriptHashes': {file: hashlib.sha256(Path(__file__).with_name(file).read_bytes()).hexdigest() for file in ('audit-model-materials.py', 'preserve-texture-transparency.py')}, 'modelSha256': hashlib.sha256(model).hexdigest(), 'timSha256': hashlib.sha256(tim).hexdigest(), 'animationSha256': hashlib.sha256(animation).hexdigest(), 'candidates': [{'label': name, 'sha256': digest} for name, _, digest in candidates], 'keyframes': frame_indices, 'cameraYawRadians': [0, math.pi/3, -math.pi/3][:len(poses)], 'outputs': hashes, 'coverage': coverage, 'animationCoverage': animation_coverage, 'animationKeyframes': [int(frame) for frame in animation_frames], 'gifSamplingMilliseconds': 100, 'command': [sys.executable, *sys.argv], 'scope': 'Offline orthographic, affine-UV, nearest-sampling inspection only. Coverage is a raster-preview check. GIF shows sampled original keyframes at an inspection speed, not native animation timing. No native lights, blending, PS1 projection, culling, interpolation, occlusion scripts or gameplay/Deck evidence.'}
    (args.output/'manifest.json').write_text(json.dumps(report, indent=2)+'\n')
    print('Private pose comparison:', args.output)


if __name__ == '__main__':
    main()
