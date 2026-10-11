#!/usr/bin/env python3
"""Fit an individually reviewed painted battle material to exact native STP.

Internal feature alignment is established by source comparison, not this repair.
This creates custom candidates only; original source files remain private.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


bounds = module('painted_material_bounds', 'repair-envhd-painted-cutout.py')
terrain = module('painted_material_stp', 'batch-envhd-terrain.py')
writer = module('painted_candidate_writer', 'write-envhd-painted-candidate.py')
METHOD = 'python-reviewed-painted-battle-material-exact-stp-1'


def repair(source, painted):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode not in ('RGB', 'RGBA'):
        raise ValueError('RGBA source and RGB/RGBA painted material required')
    if painted.mode == 'RGBA' and painted.getchannel('A').getextrema() != (255, 255):
        raise ValueError('Painted material must cover the entire source canvas')
    size = (source.width * 4, source.height * 4)
    rgb = np.asarray(painted.convert('RGB').resize(size, Image.Resampling.LANCZOS))
    output, preview = terrain.preserve_stp(source, rgb)
    return output, preview, dict(scale=4, sourceSize=list(source.size), targetSize=list(size),
        sourceStpExact=True, discardAndVisibleBlackExact=True, edgeAveraging=False,
        internalFeatureAlignment='individual-source-comparison-required',
        runtimeSelected=False, nativeAcceptance='pending', finalQualityAcceptance='pending')


def execute(source_path, painted_path, output_path, source_key):
    source_data, raw_data = bounds.bounded_read(source_path), bounds.bounded_read(painted_path)
    source_size = bounds.validate_png_header(source_data, 'source')
    raw_size = bounds.validate_png_header(raw_data, 'painted', source_size, allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as source:
        bounds.validate_opened_png(source, source_size, 'source')
        if terrain.fingerprint(source) != source_key:
            raise ValueError('Decoded source identity changed')
        with Image.open(io.BytesIO(raw_data)) as raw:
            bounds.validate_opened_png(raw, raw_size, 'painted', source_size, 'RGB' if raw_data[25] == 2 else 'RGBA')
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--painted', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source-key', required=True)
    args = parser.parse_args()
    receipt, _ = execute(args.source, args.painted, args.output, args.source_key)
    print(json.dumps(receipt, indent=2))
