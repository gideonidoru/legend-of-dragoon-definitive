#!/usr/bin/env python3
"""Extract a preregistered MCQ panorama rectangle from square-painted artwork.

Padding is established before authoring, never guessed from generated pixels.
This technical crop restores exact native STP and produces unselected candidates.
"""
import argparse
import hashlib
import importlib.util
import io
from pathlib import Path

import numpy as np
from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


bounds = module('battle_panorama_bounds', 'bounded-envhd-battle-panorama.py')
terrain = module('battle_panorama_coverage', 'batch-envhd-terrain.py')
writer = module('battle_panorama_writer', 'write-envhd-painted-candidate.py')
METHOD = 'python-reviewed-painted-battle-panorama-source-canvas-1'


def repair(source, painted):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted')
    if source.mode != 'RGBA' or painted.mode not in ('RGB', 'RGBA'):
        raise ValueError('RGBA source and RGB/RGBA painted material required')
    if painted.mode == 'RGBA' and painted.getchannel('A').getextrema() != (255, 255):
        raise ValueError('Painted material must cover the entire authoring canvas')
    rect = bounds.source_rect(source.size)
    x, y, w, h = rect
    side = painted.width
    box = [round(v * side / bounds.CANVAS_SIZE) for v in (x, y, x + w, y + h)]
    crop_size = (box[2] - box[0], box[3] - box[1])
    if min(crop_size) <= 0 or abs((crop_size[0] / crop_size[1]) / (w / h) - 1) > 0.01:
        raise ValueError('Preregistered rectangle cannot preserve the source aspect ratio')
    size = (w * 4, h * 4)
    rgb = np.asarray(painted.convert('RGB').crop(box).resize(size, Image.Resampling.LANCZOS))
    output, preview = terrain.preserve_stp(source, rgb)
    return output, preview, dict(scale=4, sourceSize=list(source.size), targetSize=list(size),
        sourceCanvasSize=[bounds.CANVAS_SIZE, bounds.CANVAS_SIZE], sourceCanvasRect=rect,
        paintedCropPixels=box, sourceStpExact=True, discardAndVisibleBlackExact=True,
        edgeAveraging=False, internalFeatureAlignment='individual-source-comparison-required',
        runtimeSelected=False, wrapAcceptance='pending', nativeAcceptance='pending', finalQualityAcceptance='pending')


def execute(source_path, painted_path, output_path, source_key):
    source_data, raw_data = bounds.bounded_read(source_path), bounds.bounded_read(painted_path)
    source_size = bounds.validate_png_header(source_data, 'source')
    raw_size = bounds.validate_png_header(raw_data, 'painted', allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as source:
        bounds.validate_opened_png(source, source_size, 'source')
        if terrain.fingerprint(source) != source_key:
            raise ValueError('Decoded source identity changed')
        with Image.open(io.BytesIO(raw_data)) as raw:
            bounds.validate_opened_png(raw, raw_size, 'painted', expected_mode='RGB' if raw_data[25] == 2 else 'RGBA')
            output, preview, record = repair(source, raw)
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
    import json
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'painted', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--source-key', required=True)
    args = parser.parse_args()
    print(json.dumps(execute(args.source, args.painted, args.output, args.source_key)[0], indent=2))
