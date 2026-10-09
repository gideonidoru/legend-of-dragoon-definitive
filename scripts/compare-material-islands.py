#!/usr/bin/env python3
"""Private, palette-isolated neural atlas experiment. AGPL v3; see LICENSE.
Preserves coverage/STP, fills tile borders from that material only, and records provenance.
This is an offline UV rasterization experiment, not an accepted runtime pack.
"""
import argparse
from collections import deque
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import subprocess
import sys
import time
import PIL
from PIL import Image


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


materials = load_module('materials', 'audit-model-materials.py')
neural = load_module('neural', 'compare-neural-textures.py')
transparency = load_module('transparency', 'preserve-texture-transparency.py')


def padded_material(source, mask, padding=8):
    """Deterministic Manhattan nearest-color extension using only the chosen palette region.
    A palette may have disconnected islands; neighboring palettes never supply tile pixels.
    """
    bounds = mask.getbbox()
    if not bounds:
        raise ValueError('Material has no texel coverage')
    left, top, right, bottom = bounds
    crop = (left - padding, top - padding, right + padding, bottom + padding)
    width, height = right - left + 2 * padding, bottom - top + 2 * padding
    pixels = [None] * (width * height)
    queue = deque()
    for y in range(top, bottom):
        for x in range(left, right):
            if mask.getpixel((x, y)):
                rgb = source.getpixel((x, y))[:3]
                # Discarded black must not pollute the RGB border context.
                if rgb != (0, 0, 0) or source.getpixel((x, y))[3]:
                    index = (y - crop[1]) * width + x - crop[0]
                    pixels[index] = rgb
                    queue.append(index)
    if not queue:
        raise ValueError('Material has no visible source pixels')
    while queue:
        index = queue.popleft()
        x, y = index % width, index // width
        neighbors = []
        if x: neighbors.append(index - 1)
        if x + 1 < width: neighbors.append(index + 1)
        if y: neighbors.append(index - width)
        if y + 1 < height: neighbors.append(index + width)
        for neighbor in neighbors:
            if pixels[neighbor] is None:
                pixels[neighbor] = pixels[index]
                queue.append(neighbor)
    image = Image.new('RGB', (width, height))
    image.putdata(pixels)
    return image, crop


def composite_material(target, source, candidate, mask, crop, scale, strength):
    """Blend inferred RGB inside the original material coverage only. STP is restored later."""
    nearest = source.resize(target.size, Image.Resampling.NEAREST)
    left, top, right, bottom = mask.getbbox()
    for y in range(top * scale, bottom * scale):
        for x in range(left * scale, right * scale):
            if mask.getpixel((x // scale, y // scale)):
                original = nearest.getpixel((x, y))
                inferred = candidate.getpixel((x - crop[0] * scale, y - crop[1] * scale))[:3]
                rgb = tuple(round(a * (1 - strength) + b * strength) for a, b in zip(original[:3], inferred))
                target.putpixel((x, y), rgb + (original[3],))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True, help='Private unpacked TMD container')
    parser.add_argument('--tim', type=Path, required=True)
    parser.add_argument('--engine', type=Path, required=True)
    parser.add_argument('--models', type=Path, required=True)
    parser.add_argument('--neural-model', choices=list(neural.MODELS), default='realesrgan-x4plus-anime')
    parser.add_argument('--strength', type=float, default=0.5, help='RGB blend 0..1; experiment only')
    parser.add_argument('--sampling-footprint', choices=['integer', 'uv-cells'], default='integer', help='Conservative source texel coverage; uv-cells also requires NumPy 2.5.1')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]):
        parser.error('Use a new private output outside the source checkout')
    if not 0 <= args.strength <= 1:
        parser.error('Strength must be between zero and one')
    if PIL.__version__ != '12.3.0':
        parser.error('Use Pillow 12.3.0 for reproducible comparisons')
    scale, stem, weights_hash, params_hash = neural.MODELS[args.neural_model]
    if neural.sha(args.models / (stem + '.bin')) != weights_hash or neural.sha(args.models / (stem + '.param')) != params_hash:
        parser.error('Neural weights differ from the pinned publisher files')
    model = materials.bounded_read(args.model)
    tim = materials.bounded_read(args.tim)
    report, original_preview, original_stp = materials.audit(model, tim)
    if report['overlappingTexels'] or report['hasClutAnimations'] or report['hasExtraSubfile']:
        parser.error('This comparison requires disjoint, non-animated palette regions')
    width, height, cw, ch, _, _ = materials.texture(tim)
    _, packets, _, _ = materials.faces(model)
    masks, _ = materials.material_masks(width, height, cw, ch, packets)
    if args.sampling_footprint == 'uv-cells':
        footprints = load_module('footprints', 'audit-uv-footprints.py')
        if footprints.np.__version__ != '2.5.1':
            parser.error('UV-cell comparisons require NumPy 2.5.1')
        masks = {palette: Image.fromarray(mask.astype('uint8')*255).convert('1') for palette, mask in footprints.footprint_masks(model, tim).items()}
        report, original_preview, original_stp = materials.audit(model, tim, masks)
        if report['overlappingTexels']:
            parser.error('Sampling footprints need a per-material mapping; a flat atlas is ambiguous')
    if not masks or len(masks) > 64:
        parser.error('Material count exceeds the comparison limit')
    args.output.mkdir(exist_ok=False)
    original_preview.save(args.output / 'original-preview.png')
    original_stp.save(args.output / 'original-stp.png')
    target = original_stp.resize((width * scale, height * scale), Image.Resampling.NEAREST)
    started = time.monotonic()
    steps = []
    for palette, mask in sorted(masks.items()):
        tile, crop = padded_material(original_stp, mask)
        source_path = args.output / f'palette-{palette}-source.png'
        target_path = args.output / f'palette-{palette}-neural.png'
        tile.save(source_path)
        command = [str(args.engine.resolve()), '-i', str(source_path.resolve()), '-o', str(target_path.resolve()), '-m', str(args.models.resolve()), '-n', args.neural_model, '-s', str(scale), '-t', '256', '-j', '1:1:1']
        remaining = 300 - (time.monotonic() - started)
        if remaining <= 0:
            raise RuntimeError('Private comparison exceeded five minutes; no final candidate emitted')
        run = subprocess.run(command, capture_output=True, text=True, timeout=min(30, remaining))
        (args.output / f'palette-{palette}.log').write_text(run.stdout + run.stderr)
        if run.returncode:
            raise RuntimeError('Neural material failed; inspect its retained private log')
        candidate, candidate_hash = transparency.read_png(target_path, 2112)
        if candidate.size != (tile.width * scale, tile.height * scale):
            raise RuntimeError('Neural tile dimensions differ')
        composite_material(target, original_stp, candidate, mask, crop, scale, args.strength)
        steps.append({'palette': palette, 'maskSha256': hashlib.sha256(mask.tobytes()).hexdigest(), 'crop': list(crop), 'sourceSha256': neural.sha(source_path), 'candidateSha256': candidate_hash, 'command': command})
    engine, preview = transparency.preserve(original_stp, target)
    engine.save(args.output / 'candidate-engine-stp.png')
    preview.save(args.output / 'candidate-preview.png')
    original_preview.resize(preview.size, Image.Resampling.NEAREST).save(args.output / 'nearest-preview.png')
    report.update({'pipeline': 'definitive-palette-isolated-neural-1', 'environment': platform.platform(), 'pillow': PIL.__version__, 'scriptSha256': neural.sha(__file__), 'dependencyScriptHashes': {file: neural.sha(Path(__file__).with_name(file)) for file in ('audit-model-materials.py', 'compare-neural-textures.py', 'preserve-texture-transparency.py')}, 'engineSha256': neural.sha(args.engine), 'weightsSha256': weights_hash, 'paramsSha256': params_hash, 'scale': scale, 'strength': args.strength, 'paddingSourceTexels': 8, 'steps': steps, 'seconds': round(time.monotonic() - started, 3), 'command': [sys.executable, *sys.argv], 'engineStpSha256': neural.sha(args.output / 'candidate-engine-stp.png'), 'previewSha256': neural.sha(args.output / 'candidate-preview.png'), 'scope': 'Private palette-isolated experiment. Binary STP/discard coverage restored; UV rasterization, inferred detail, disconnected same-palette islands, filtering, animated seams and runtime compatibility still need verification.'})
    report['samplingFootprint'] = args.sampling_footprint
    if args.sampling_footprint == 'uv-cells':
        report['numpy'] = footprints.np.__version__
        report['dependencyScriptHashes']['audit-uv-footprints.py'] = neural.sha(Path(__file__).with_name('audit-uv-footprints.py'))
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Private palette-isolated comparison:', args.output)


if __name__ == '__main__':
    main()
