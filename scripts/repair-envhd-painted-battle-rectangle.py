#!/usr/bin/env python3
"""Restore reviewed native palette markings over a blank painted battle panel.

Only the explicitly nominated palette cells inside the reviewed rectangle are
locked. Other material boundaries require an individual source comparison.
Native sources remain private; this writes custom candidates, not selection.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


bounds = module('battle_rectangle_bounds', 'repair-envhd-painted-cutout.py')
terrain = module('battle_rectangle_stp', 'batch-envhd-terrain.py')
METHOD = 'python-painted-rect-exact-source-palette-marking-1'


def repair(source, painted, palette_rgba, source_rect):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode not in ('RGB', 'RGBA'):
        raise ValueError('RGBA source and RGB/RGBA painted material required')
    if (not isinstance(source_rect, (list, tuple)) or len(source_rect) != 4
            or any(type(v) is not int for v in source_rect)):
        raise ValueError('Reviewed integer source rectangle required')
    x0, y0, x1, y1 = source_rect
    if not (0 <= x0 < x1 <= source.width and 0 <= y0 < y1 <= source.height):
        raise ValueError('Reviewed rectangle must be inside the source')
    if (not isinstance(palette_rgba, (list, tuple)) or not palette_rgba
            or any(not isinstance(c, (list, tuple)) or len(c) != 4
                   or any(type(v) is not int or not 0 <= v <= 255 for v in c)
                   or c[3] not in (0, 255) or not any(c[:3]) for c in palette_rgba)
            or len({tuple(c) for c in palette_rgba}) != len(palette_rgba)):
        raise ValueError('Distinct visible native RGBA palette classes required')
    if painted.mode == 'RGBA' and painted.getchannel('A').getextrema() != (255, 255):
        raise ValueError('Painted material must cover the entire source canvas')
    native = np.asarray(source)
    roi = np.zeros(native.shape[:2], dtype=bool)
    roi[y0:y1, x0:x1] = True
    mask = np.zeros_like(roi)
    counts = []
    for colour in palette_rgba:
        cells = np.all(native == colour, axis=2) & roi
        if not cells.any():
            raise ValueError('Reviewed palette class absent from source rectangle')
        mask |= cells
        counts.append(dict(rgba=list(colour), sourcePixels=int(cells.sum())))
    if mask[roi].all():
        raise ValueError('Reviewed marking requires background inside its rectangle')
    size = (source.width * 4, source.height * 4)
    canvas = painted.convert('RGB').resize(size, Image.Resampling.LANCZOS)
    rgb = np.asarray(canvas).astype(np.float32)
    # A material-only high-frequency grain cannot add independent motif cells.
    grain = rgb - np.asarray(canvas.filter(ImageFilter.GaussianBlur(2))).astype(np.float32)
    native_4x = np.repeat(np.repeat(native, 4, axis=0), 4, axis=1)
    target_mask = np.repeat(np.repeat(mask, 4, axis=0), 4, axis=1)
    marked = native_4x[:, :, :3].astype(np.float32) + np.clip(grain, -10, 10) * 0.3
    padded = np.pad(target_mask, 1, mode='constant')
    upper_left = target_mask & (~padded[:-2, 1:-1] | ~padded[1:-1, :-2])
    lower_right = target_mask & (~padded[2:, 1:-1] | ~padded[1:-1, 2:])
    marked[upper_left] += 7
    marked[lower_right] -= 9
    rgb[target_mask] = marked[target_mask]
    output, preview = terrain.preserve_stp(source, np.clip(rgb, 0, 255).astype(np.uint8))
    actual = np.asarray(output)
    if (not np.array_equal(actual[:, :, 3], native_4x[:, :, 3])
            or not np.array_equal(np.all(actual[:, :, :3] == 0, axis=2),
                                  np.all(native_4x[:, :, :3] == 0, axis=2))):
        raise ValueError('Native STP/discard/visible-black classes changed')
    return output, preview, dict(scale=4, sourceSize=list(source.size), targetSize=list(size),
        paletteRgba=[list(c) for c in palette_rgba], sourceMaskRectangle=list(source_rect),
        sourcePalettePixels=counts, sourceMarkingPixels=int(mask.sum()),
        sourceMarkingMaskSha256=hashlib.sha256(np.packbits(mask).tobytes()).hexdigest(),
        listedPaletteCellCoverageExact=True, bevelInsideSourceMask=True,
        sourceStpExact=True, discardAndVisibleBlackExact=True, edgeAveraging=False,
        internalFeatureAlignment='listed-palette-cells-locked; other-features-individually-reviewed',
        runtimeSelected=False, nativeAcceptance='pending', finalQualityAcceptance='pending')


def execute(source_path, painted_path, output_path, source_key, palette_rgba, source_rect):
    source_data, raw_data = bounds.bounded_read(source_path), bounds.bounded_read(painted_path)
    source_size = bounds.validate_png_header(source_data, 'source')
    raw_size = bounds.validate_png_header(raw_data, 'painted', source_size, allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as source:
        bounds.validate_opened_png(source, source_size, 'source')
        if terrain.fingerprint(source) != source_key:
            raise ValueError('Decoded source identity changed')
        with Image.open(io.BytesIO(raw_data)) as raw:
            bounds.validate_opened_png(raw, raw_size, 'painted', source_size,
                                       'RGB' if raw_data[25] == 2 else 'RGBA')
            output, preview, record = repair(source, raw, palette_rgba, source_rect)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output.save(output_path)
    record.update(decodedRgbaSha256=source_key, method=METHOD,
        algorithmSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        boundsAlgorithmSha256=hashlib.sha256(Path(bounds.__file__).read_bytes()).hexdigest(),
        stpAlgorithmSha256=hashlib.sha256(Path(terrain.__file__).read_bytes()).hexdigest(),
        generatedSha256=hashlib.sha256(raw_data).hexdigest(),
        outputSha256=hashlib.sha256(output_path.read_bytes()).hexdigest())
    return record, preview


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--painted', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--palette-rgba', required=True, type=json.loads)
    parser.add_argument('--source-rect', required=True, type=int, nargs=4)
    args = parser.parse_args()
    record, _ = execute(args.source, args.painted, args.output, args.source_key, args.palette_rgba, args.source_rect)
    print(json.dumps(record, indent=2))
