#!/usr/bin/env python3
"""Restore a source-palette lattice inside a reviewed painted circular material.

Original sources stay private. This writes custom candidates, never selection.
The sampled circle and motif palette must be classified from actual source usage.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


bounds = module('circle_bounds', 'repair-envhd-painted-cutout.py')
terrain = module('circle_stp', 'batch-envhd-terrain.py')
writer = module('painted_candidate_writer', 'write-envhd-painted-candidate.py')
METHOD = 'python-painted-circle-exact-source-palette-lattice-1'


def repair(source, painted, motif_rgba, source_mask_circle, lighting_radius):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode not in ('RGB', 'RGBA'):
        raise ValueError('RGBA source and RGB/RGBA painting required')
    palettes = motif_rgba
    if isinstance(palettes, (list, tuple)) and len(palettes) == 4 and all(type(v) is int for v in palettes):
        palettes = [palettes]
    if (not isinstance(palettes, (list, tuple)) or not palettes or len(palettes) > 16
            or any(not isinstance(p, (list, tuple)) or len(p) != 4
                   or any(type(v) is not int or not 0 <= v <= 255 for v in p) for p in palettes)
            or not isinstance(source_mask_circle, (list, tuple)) or len(source_mask_circle) != 3
            or any(type(v) not in (int, float) or not math.isfinite(v) for v in source_mask_circle)
            or type(lighting_radius) not in (int, float) or not math.isfinite(lighting_radius)):
        raise ValueError('Explicit finite source circle and palette required')
    cx, cy, radius = source_mask_circle
    if len({tuple(p) for p in palettes}) != len(palettes):
        raise ValueError('Duplicate source motif palette class')
    if (not 0 < radius < lighting_radius or cx - lighting_radius < 0 or cy - lighting_radius < 0
            or cx + lighting_radius >= source.width or cy + lighting_radius >= source.height):
        raise ValueError('Reviewed circle must fit inside the source canvas')
    if painted.mode == 'RGBA' and painted.getchannel('A').getextrema() != (255, 255):
        raise ValueError('Painted material must cover the entire source canvas')
    native = np.asarray(source)
    yy, xx = np.mgrid[:source.height, :source.width]
    squared = (xx - cx) ** 2 + (yy - cy) ** 2
    zone = squared <= radius ** 2
    palette_masks = [zone & np.all(native == colour, axis=2) for colour in palettes]
    motif = np.any(palette_masks, axis=0)
    if not all(mask.any() for mask in palette_masks) or not (zone & ~motif).any():
        raise ValueError('Source does not contain the reviewed lattice/background')
    size = (source.width * 4, source.height * 4)
    paint = np.asarray(painted.convert('RGB').resize(size, Image.Resampling.LANCZOS), dtype=np.float64).copy()
    target_mask = np.repeat(np.repeat(motif, 4, axis=0), 4, axis=1)
    target_zone = np.repeat(np.repeat(zone, 4, axis=0), 4, axis=1)
    light_zone = np.repeat(np.repeat(squared <= lighting_radius ** 2, 4, axis=0), 4, axis=1)
    weight = np.asarray(Image.fromarray(light_zone.astype(np.uint8) * 255).filter(ImageFilter.GaussianBlur(4)), dtype=np.float64) / 255
    # A blank painted face is required. Lift its dimmer inner portions smoothly
    # so the original grey marking remains legible without adding new strokes.
    lift = np.maximum(0, 202 - paint.mean(axis=2))
    paint += lift[:, :, None] * weight[:, :, None]
    tone = np.clip(paint.mean(axis=2) - 220, -12, 12) * 0.25
    original = np.repeat(np.repeat(native, 4, axis=0), 4, axis=1)
    carved = original[:, :, :3].astype(np.float64) + tone[:, :, None]
    padded = np.pad(target_mask, 1)
    upper_left = target_mask & (~padded[:-2, 1:-1] | ~padded[1:-1, :-2])
    lower_right = target_mask & (~padded[2:, 1:-1] | ~padded[1:-1, 2:])
    carved[upper_left] += 8
    carved[lower_right] -= 10
    paint[target_mask] = carved[target_mask]
    output, preview = terrain.preserve_stp(source, np.clip(paint, 0, 255).astype(np.uint8))
    actual = np.asarray(output)
    if not np.array_equal(actual[:, :, 3], original[:, :, 3]):
        raise ValueError('Native STP classes changed')
    coloured = np.any(original[:, :, :3] != 0, axis=2)
    if not np.array_equal(target_zone & coloured & (actual[:, :, :3].mean(axis=2) < 185), target_mask):
        raise ValueError('Painted face obscures or adds to the source lattice')
    return output, preview, dict(scale=4, sourceSize=list(source.size), targetSize=list(size),
        motifRgba=list(motif_rgba), sourceMaskCircle=list(source_mask_circle), lightingRadius=lighting_radius,
        sourceMotifPixels=int(motif.sum()), sourceMotifPalettePixels=[int(mask.sum()) for mask in palette_masks],
        motifSilhouetteExact=True, sourceStpExact=True,
        discardAndVisibleBlackExact=True, bevelInsideSourceMask=True, edgeAveraging=False,
        runtimeSelected=False, nativeAcceptance='pending', finalQualityAcceptance='pending')


def execute(source_path, painted_path, output_path, source_key, motif_rgba, circle, lighting_radius):
    source_data, raw_data = bounds.bounded_read(source_path), bounds.bounded_read(painted_path)
    source_size = bounds.validate_png_header(source_data, 'source')
    raw_size = bounds.validate_png_header(raw_data, 'painted', source_size, allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as source:
        bounds.validate_opened_png(source, source_size, 'source')
        if terrain.fingerprint(source) != source_key:
            raise ValueError('Decoded source identity changed')
        with Image.open(io.BytesIO(raw_data)) as raw:
            bounds.validate_opened_png(raw, raw_size, 'painted', source_size, 'RGB' if raw_data[25] == 2 else 'RGBA')
            output, preview, record = repair(source, raw, motif_rgba, circle, lighting_radius)
    output_data = writer.write_candidate(output, output_path)
    record.update(decodedRgbaSha256=source_key, method=METHOD,
        algorithmSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        boundsAlgorithmSha256=hashlib.sha256(Path(bounds.__file__).read_bytes()).hexdigest(),
        stpAlgorithmSha256=hashlib.sha256(Path(terrain.__file__).read_bytes()).hexdigest(),
        outputWriterSha256=hashlib.sha256(Path(writer.__file__).read_bytes()).hexdigest(),
        generatedSha256=hashlib.sha256(raw_data).hexdigest(),
        outputSha256=hashlib.sha256(output_data).hexdigest())
    return record, preview


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--painted', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--motif-rgba', required=True, type=int, nargs=4, action='append')
    parser.add_argument('--source-circle', required=True, type=float, nargs=3)
    parser.add_argument('--lighting-radius', required=True, type=float)
    args = parser.parse_args()
    receipt, _ = execute(args.source, args.painted, args.output, args.source_key, args.motif_rgba, args.source_circle, args.lighting_radius)
    print(json.dumps(receipt, indent=2))
