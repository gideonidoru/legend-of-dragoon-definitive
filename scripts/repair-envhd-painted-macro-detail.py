#!/usr/bin/env python3
"""Refine painted material detail while anchoring every native pixel's mean RGB.

Separate static-field candidate recipe; the pinned cutout recipe is unchanged.
Broad invented painted forms/colors are discarded. Only bounded detail smaller
than a native texel is combined with source-derived interpolation. This is a
source-fidelity constraint, not semantic, final-style or gameplay acceptance.
"""
import argparse
import importlib.util
import io
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image, ImageFilter

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


cutout = module('macro_cutout', 'repair-envhd-painted-cutout.py')
writer = module('macro_writer', 'write-envhd-painted-candidate.py')
METHOD = 'python-painted-native-cell-mean-detail-1'
SCALE = 4
MAX_DETAIL_CHANNEL_DELTA = 6.0
MAX_NATIVE_CELL_MEAN_DRIFT = 0.5


def cell_mean(rgb, width, height):
    return rgb.reshape(height, SCALE, width, SCALE, 3).mean(axis=(1, 3))


def expanded(values):
    return np.repeat(np.repeat(values, SCALE, axis=0), SCALE, axis=1)


def zero_cell_mean(rgb, width, height):
    return rgb - expanded(cell_mean(rgb, width, height))


def refine(source, painted):
    # This exact pinned operation continues to own aspect, coverage, alpha,
    # visible black and bounded nearest-colour repair. No gate is relaxed.
    detail, cutout_receipt = cutout.repair(source, painted)
    native = np.asarray(source, dtype=np.uint8)
    nearest = expanded(native)
    rgb = native[:, :, :3].astype(np.float64)
    source_rgb = expanded(rgb)
    width, height = source.size
    target_size = (width * SCALE, height * SCALE)
    visible = (nearest[:, :, 3] != 0)
    black = np.all(nearest[:, :, :3] == 0, axis=2)
    valid = visible & ~black
    interpolated = np.asarray(source.resize(target_size, Image.Resampling.LANCZOS), dtype=np.uint8)[:, :, :3].astype(np.float64)
    interpolated[~valid] = source_rgb[~valid]
    source_detail = zero_cell_mean(interpolated, width, height) * 0.5

    painted_rgb = np.asarray(detail, dtype=np.uint8)[:, :, :3].astype(np.float64)
    weighted = np.where(valid[:, :, None], painted_rgb, 0).astype(np.uint8)
    blurred_colour = np.asarray(Image.fromarray(weighted).filter(ImageFilter.GaussianBlur(SCALE)), dtype=np.float64)
    blurred_mask = np.asarray(Image.fromarray((valid * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(SCALE)), dtype=np.float64) / 255.0
    local_mean = np.divide(blurred_colour, blurred_mask[:, :, None],
                           out=painted_rgb.copy(), where=blurred_mask[:, :, None] > 0)
    grain = zero_cell_mean(np.where(valid[:, :, None], painted_rgb - local_mean, 0), width, height)
    peak = np.max(np.abs(grain.reshape(height, SCALE, width, SCALE, 3)), axis=(1, 3, 4))
    limit = np.minimum(MAX_DETAIL_CHANNEL_DELTA, np.maximum(1.0, rgb.max(axis=2) * 0.08))
    factor = np.divide(limit, np.maximum(peak, limit))
    grain *= expanded(factor[:, :, None])
    deviation = source_detail + grain

    # A single symmetric factor per native cell/channel keeps the source mean
    # exact in floating point and avoids saturation or newly black pixels.
    # Saturated/zero native channels remain fixed; other positive channels keep
    # at least one integer level after rounding. Hidden/black cells are restored.
    peak = np.max(np.abs(deviation.reshape(height, SCALE, width, SCALE, 3)), axis=(1, 3))
    margin = np.maximum(0.0, np.minimum(rgb - 0.51, 255.0 - rgb - 0.51))
    factor = np.minimum(1.0, np.divide(margin, peak, out=np.ones_like(peak), where=peak > 0))
    deviation *= expanded(factor)
    result = nearest.copy()
    result[:, :, :3] = np.rint(source_rgb + deviation).astype(np.uint8)
    result[~valid] = nearest[~valid]
    if not np.array_equal(result[:, :, 3], nearest[:, :, 3]):
        raise ValueError('Native alpha changed')
    if not np.array_equal(np.all(result[:, :, :3] == 0, axis=2) & visible, black & visible):
        raise ValueError('Native visible-black class changed')
    drift = np.abs(cell_mean(result[:, :, :3].astype(np.float64), width, height) - rgb)
    source_visible = (native[:, :, 3] != 0) & np.any(native[:, :, :3] != 0, axis=2)
    maximum_drift = float(drift[source_visible].max(initial=0.0))
    if maximum_drift > MAX_NATIVE_CELL_MEAN_DRIFT:
        raise ValueError('Native pixel mean colour drift exceeded recipe bound')
    return Image.fromarray(result), dict(method=METHOD, scale=SCALE,
        cutoutOperation=cutout_receipt,
        gaussianDetailRadiusTargetPixels=SCALE,
        maximumPaintedDetailChannelDelta=MAX_DETAIL_CHANNEL_DELTA,
        maximumNativeCellMeanChannelDrift=maximum_drift,
        maximumAllowedNativeCellMeanChannelDrift=MAX_NATIVE_CELL_MEAN_DRIFT,
        broadPaintedColorAndReliefDiscarded=True,
        sourceAlphaExact=True, visibleBlackExact=True,
        nativeAcceptance='pending', finalQualityAcceptance='pending', runtimeSelected=False)


def execute(source_path, painted_path, output_path, source_key, painted_sha256):
    source_data, painted_data = cutout.bounded_read(source_path), cutout.bounded_read(painted_path)
    source_sha, initial_painted_sha = cutout.digest(source_data), cutout.digest(painted_data)
    if initial_painted_sha != painted_sha256:
        raise ValueError('Painted artwork identity changed')
    source_size = cutout.validate_png_header(source_data, 'source')
    painted_size = cutout.validate_png_header(painted_data, 'painted', source_size, allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as image:
        cutout.validate_opened_png(image, source_size, 'source')
        source = image.copy()
    with Image.open(io.BytesIO(painted_data)) as image:
        cutout.validate_opened_png(image, painted_size, 'painted', source.size, expected_mode=image.mode)
        if image.mode == 'RGB' and source.getchannel('A').getextrema() != (255, 255):
            raise ValueError('RGB artwork cannot prove nonopaque source coverage')
        painted = image.convert('RGBA')
    if cutout.digest(struct.pack('<II', *source.size) + source.tobytes()) != source_key:
        raise ValueError('Decoded source identity changed')
    candidate, receipt = refine(source, painted)
    if cutout.digest(cutout.bounded_read(source_path)) != source_sha or cutout.digest(cutout.bounded_read(painted_path)) != initial_painted_sha:
        raise ValueError('Source or artwork changed before candidate publication')
    output = writer.write_candidate(candidate, output_path)
    return dict(receipt, decodedRgbaSha256=source_key, sourceFileSha256=source_sha,
                generatedSha256=initial_painted_sha, outputSha256=cutout.digest(output),
                sourceSize=list(source.size), targetSize=list(candidate.size),
                algorithmSha256=cutout.digest(Path(__file__).read_bytes()),
                dependencySha256={p.name:cutout.digest(p.read_bytes()) for p in
                    [Path(cutout.__file__), Path(writer.__file__)]})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'painted', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--painted-sha256', required=True)
    args = parser.parse_args()
    print(json.dumps(execute(args.source, args.painted, args.output, args.source_key, args.painted_sha256)))


if __name__ == '__main__':
    main()
