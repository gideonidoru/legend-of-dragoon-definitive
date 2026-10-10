#!/usr/bin/env python3
"""Private palette-aware atlas prototype, AGPL v3; see LICENSE.
Separates conflicting palettes, binds exact model/TIM identities, and preserves PSX STP.
No game adapter or runtime acceptance is implied. Requires the pinned offline dependencies.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import platform
import subprocess
import sys
import time
import numpy as np
import PIL
from PIL import Image


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


islands = load_module('islands', 'compare-material-islands.py')
footprints = load_module('footprints', 'audit-uv-footprints.py')
materials = islands.materials
transparency = islands.transparency
neural = islands.neural
PADDING = 8
MAX_ATLAS = 4096


def source_palette(tim, palette):
    width, height, cw, ch, colors, indices = materials.texture(tim)
    if not 0 <= palette < cw*ch//16:
        raise ValueError('Palette is outside the source CLUT')
    packed = np.frombuffer(indices[:width*height//2], dtype=np.uint8)
    lookup = np.column_stack((packed & 15, packed >> 4)).reshape(height, width)
    colors = np.array(colors[palette*16:(palette+1)*16], dtype=np.uint16)
    values = colors[lookup]
    rgba = np.stack(((values & 31)*255//31, (values >> 5 & 31)*255//31, (values >> 10 & 31)*255//31, np.where(values & 0x8000, 255, 0)), axis=-1).astype(np.uint8)
    return Image.fromarray(rgba)


def clamped_crop(source, crop):
    left, top, right, bottom = crop
    pixels = np.array(source)
    xs = np.clip(np.arange(left, right), 0, source.width-1)
    ys = np.clip(np.arange(top, bottom), 0, source.height-1)
    return Image.fromarray(pixels[ys[:, None], xs[None, :]])


def pack_rectangles(sizes):
    """Deterministic shelves, no rotation; choose the least allocated area across widths."""
    if not sizes or len(sizes) > 64 or any(not (1 <= w <= MAX_ATLAS and 1 <= h <= MAX_ATLAS) for w, h in sizes.values()):
        raise ValueError('Material tiles exceed packing limits')
    order = sorted(sizes, key=lambda palette: (-sizes[palette][1], -sizes[palette][0], palette))
    best = None
    for exponent in range(4, 13):
        width = 1 << exponent
        if width < max(w for w, _ in sizes.values()): continue
        slots = {}
        x = y = row_height = 0
        for palette in order:
            w, h = sizes[palette]
            if x+w > width:
                y += row_height
                x = row_height = 0
            slots[palette] = (x, y, w, h)
            x += w
            row_height = max(row_height, h)
        height = math.ceil((y+row_height)/4)*4
        if height > MAX_ATLAS: continue
        key = (width*height, max(width, height), width)
        if best is None or key < best[0]: best = (key, width, height, slots)
    if best is None: raise ValueError('Materials do not fit the bounded atlas')
    return best[1:]


def layout(model, tim, scale):
    if scale not in (2, 4): raise ValueError('Packing scale must be 2x or 4x')
    audit, _, _ = materials.audit(model, tim)
    if audit['hasClutAnimations'] or audit['hasExtraSubfile']:
        raise ValueError('Animated palettes or extra container dependencies are not supported')
    masks = {palette: Image.fromarray(mask.astype(np.uint8)*255).convert('1') for palette, mask in footprints.footprint_masks(model, tim).items()}
    crops = {}
    for palette, mask in masks.items():
        left, top, right, bottom = mask.getbbox()
        crops[palette] = (left-PADDING, top-PADDING, right+PADDING, bottom+PADDING)
    sizes = {palette: ((crop[2]-crop[0])*scale, (crop[3]-crop[1])*scale) for palette, crop in crops.items()}
    width, height, slots = pack_rectangles(sizes)
    return audit, masks, crops, slots, width, height


def rgba_preview(engine):
    pixels = np.array(engine)
    visible = np.any(pixels != 0, axis=-1)
    pixels[..., 3] = np.where(visible, 255, 0)
    return Image.fromarray(pixels)


def validate_pack(folder, model, tim):
    manifest = folder/'manifest.json'
    png = folder/'atlas-engine-stp.png'
    if manifest.is_symlink() or png.is_symlink() or manifest.stat().st_size > 65536:
        raise ValueError('Invalid packed material paths or manifest size')
    with manifest.open('rb') as stream: data = stream.read(65537)
    if len(data) > 65536: raise ValueError('Packed manifest exceeds its limit')
    report = json.loads(data)
    if not isinstance(report, dict): raise ValueError('Packed manifest must be an object')
    if report.get('pipeline') != 'definitive-private-material-pack-1' or report.get('modelSha256') != hashlib.sha256(model).hexdigest() or report.get('timSha256') != hashlib.sha256(tim).hexdigest():
        raise ValueError('Packed materials do not match the original model and TIM')
    scale = report.get('scale')
    if type(scale) is not int or scale not in (2, 4): raise ValueError('Invalid packed material scale')
    _, masks, crops, slots, width, height = layout(model, tim, scale)
    expected = [{'palette': palette, 'sourceCrop': list(crops[palette]), 'atlasRect': list(slots[palette])} for palette in sorted(masks)]
    algorithm = report.get('algorithm')
    preserved = report.get('preservedPalettes', [])
    if algorithm not in ('nearest', 'neural', 'authored') or not isinstance(preserved, list) or any(type(value) is not int for value in preserved) or len(set(preserved)) != len(preserved) or not set(preserved).issubset(masks):
        raise ValueError('Invalid material processing description')
    if algorithm == 'authored':
        provenance = report.get('authoring', {})
        if not isinstance(provenance, dict) or any(not isinstance(provenance.get(key), str) or len(provenance[key]) != 64 or any(c not in '0123456789abcdef' for c in provenance[key]) for key in ('baseAtlasSha256', 'generatedImageSha256', 'promptSha256')):
            raise ValueError('Missing authored material provenance')
    if algorithm == 'neural' and (type(report.get('strength')) not in (int, float) or not 0 <= report['strength'] <= 1):
        raise ValueError('Invalid neural material strength')
    if algorithm == 'neural' and not any(scale == item[0] and report.get('weightsSha256') == item[2] and report.get('paramsSha256') == item[3] for item in neural.MODELS.values()):
        raise ValueError('Neural material weights differ from the pinned comparison models')
    entries = report.get('materials')
    if not isinstance(entries, list) or any(not isinstance(entry, dict) or set(entry) != {'palette', 'sourceCrop', 'atlasRect'} or type(entry['palette']) is not int or not isinstance(entry['sourceCrop'], list) or not isinstance(entry['atlasRect'], list) or any(type(value) is not int for value in entry['sourceCrop']+entry['atlasRect']) for entry in entries):
        raise ValueError('Invalid material map fields')
    atlas_size = report.get('atlasSize')
    if not isinstance(atlas_size, list) or any(type(value) is not int for value in atlas_size) or type(report.get('paddingSourceTexels')) is not int or report.get('materials') != expected or atlas_size != [width, height] or report.get('paddingSourceTexels') != PADDING:
        raise ValueError('Material mapping differs from the reproducible original UV layout')
    image, digest = transparency.read_png(png, MAX_ATLAS)
    if digest != report.get('atlasEngineSha256') or image.size != (width, height):
        raise ValueError('Packed atlas dimensions or hash differ')
    for palette in sorted(masks):
        source = clamped_crop(source_palette(tim, palette), crops[palette]).resize((slots[palette][2], slots[palette][3]), Image.Resampling.NEAREST)
        x, y, w, h = slots[palette]
        candidate = image.crop((x, y, x+w, y+h))
        a, b = np.array(source), np.array(candidate)
        if not np.array_equal(a[..., 3], b[..., 3]) or not np.array_equal(np.all(a == 0, axis=-1), np.all(b == 0, axis=-1)):
            raise ValueError('Packed STP or discard coverage differs from the original material')
        black = np.all(a[..., :3] == 0, axis=-1)
        if np.any(b[..., :3][black] != 0): raise ValueError('Visible original black changed')
        if (algorithm == 'nearest' or palette in preserved) and not np.array_equal(a, b):
            raise ValueError('Original material pixels changed in a preserved region')
    return image, {entry['palette']: entry for entry in expected}, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--tim', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--algorithm', choices=['nearest', 'neural'], default='nearest')
    parser.add_argument('--scale', type=int, choices=[2, 4], default=4)
    parser.add_argument('--strength', type=float, default=0.5)
    parser.add_argument('--preserve-palette', type=int, action='append', default=[])
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    parser.add_argument('--neural-model', choices=list(neural.MODELS), default='realesrgan-x4plus-anime')
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]): parser.error('Use a new private output outside the source checkout')
    if PIL.__version__ != '12.3.0' or np.__version__ != '2.5.1': parser.error('Use the pinned offline dependencies')
    if not 0 <= args.strength <= 1: parser.error('Strength must be between zero and one')
    model, tim = materials.bounded_read(args.model), materials.bounded_read(args.tim)
    audit, masks, crops, slots, width, height = layout(model, tim, args.scale)
    if not set(args.preserve_palette).issubset(masks): parser.error('Preserved palette is not used by this model')
    weights_hash = params_hash = None
    if args.algorithm == 'neural':
        if not args.engine or not args.models: parser.error('Neural processing requires pinned external engine and model folders')
        scale, stem, weights_hash, params_hash = neural.MODELS[args.neural_model]
        if scale != args.scale or neural.sha(args.models/(stem+'.bin')) != weights_hash or neural.sha(args.models/(stem+'.param')) != params_hash:
            parser.error('Neural scale or weight identities differ')
    args.output.mkdir(exist_ok=False)
    atlas = Image.new('RGBA', (width, height))
    steps = []
    started = time.monotonic()
    for palette in sorted(masks):
        source = source_palette(tim, palette)
        crop = crops[palette]
        tile = clamped_crop(source, crop)
        target = tile.resize((tile.width*args.scale, tile.height*args.scale), Image.Resampling.NEAREST)
        step = {'palette': palette, 'processing': 'nearest'}
        if args.algorithm == 'neural' and palette not in args.preserve_palette:
            try:
                rgb_tile, _ = islands.padded_material(source, masks[palette], PADDING)
            except ValueError:
                rgb_tile = None  # Entirely discarded material retains original pixels.
            if rgb_tile is not None:
                source_path, output_path = args.output/f'palette-{palette}-input.png', args.output/f'palette-{palette}-neural.png'
                rgb_tile.save(source_path)
                command = [str(args.engine.resolve()), '-i', str(source_path.resolve()), '-o', str(output_path.resolve()), '-m', str(args.models.resolve()), '-n', args.neural_model, '-s', str(args.scale), '-t', '256', '-j', '1:1:1']
                remaining = 300-(time.monotonic()-started)
                if remaining <= 0: raise RuntimeError('Packed neural processing exceeded five minutes')
                run = subprocess.run(command, capture_output=True, text=True, timeout=min(30, remaining))
                (args.output/f'palette-{palette}.log').write_text(run.stdout+run.stderr)
                if run.returncode: raise RuntimeError('Neural material failed; inspect the retained private log')
                candidate, digest = transparency.read_png(output_path, MAX_ATLAS)
                if candidate.size != target.size: raise ValueError('Neural tile dimensions differ')
                blended = Image.blend(target.convert('RGB'), candidate.convert('RGB'), args.strength).convert('RGBA')
                target, _ = transparency.preserve(tile, blended)
                step.update({'processing': 'neural', 'command': command, 'inputSha256': neural.sha(source_path), 'neuralOutputSha256': digest})
        x, y, _, _ = slots[palette]
        atlas.paste(target, (x, y))
        steps.append(step)
    atlas.save(args.output/'atlas-engine-stp.png')
    rgba_preview(atlas).save(args.output/'atlas-preview.png')
    report = {'pipeline': 'definitive-private-material-pack-1', 'modelSha256': hashlib.sha256(model).hexdigest(), 'timSha256': hashlib.sha256(tim).hexdigest(), 'sourceSize': [audit['width'], audit['height']], 'scale': args.scale, 'paddingSourceTexels': PADDING, 'atlasSize': [width, height], 'atlasRgbaBytes': width*height*4, 'fullUsedPaletteLayerRgbaBytes': len(masks)*audit['width']*audit['height']*args.scale*args.scale*4, 'materials': [{'palette': palette, 'sourceCrop': list(crops[palette]), 'atlasRect': list(slots[palette])} for palette in sorted(masks)], 'algorithm': args.algorithm, 'strength': args.strength, 'preservedPalettes': sorted(set(args.preserve_palette)), 'weightsSha256': weights_hash, 'paramsSha256': params_hash, 'engineSha256': neural.sha(args.engine) if args.algorithm == 'neural' else None, 'atlasEngineSha256': neural.sha(args.output/'atlas-engine-stp.png'), 'atlasPreviewSha256': neural.sha(args.output/'atlas-preview.png'), 'steps': steps, 'seconds': round(time.monotonic()-started, 3), 'environment': platform.platform(), 'pillow': PIL.__version__, 'numpy': np.__version__, 'scriptSha256': neural.sha(__file__), 'dependencyScriptHashes': {file: neural.sha(Path(__file__).with_name(file)) for file in ('audit-model-materials.py', 'audit-uv-footprints.py', 'compare-material-islands.py', 'compare-neural-textures.py', 'preserve-texture-transparency.py')}, 'command': [sys.executable, *sys.argv], 'scope': 'Private per-palette atlas and affine UV-map prototype. No native loader, filtering, blending, gameplay, Deck residency or art acceptance is established.'}
    (args.output/'manifest.json').write_text(json.dumps(report, indent=2)+'\n')
    validate_pack(args.output, model, tim)
    print('Verified private material atlas:', args.output, '| RGBA bytes:', width*height*4)


if __name__ == '__main__':
    main()
