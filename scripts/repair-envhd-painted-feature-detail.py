#!/usr/bin/env python3
"""Restore explicitly identified ambiguous features while retaining painted detail.

The unchanged cutout recipe first validates the original painting. Within
reviewed source-coordinate polygons, source-derived colour and bounded detail
replace invented markings. This operation remains an unselected authoring
recipe until its actual source/output comparison passes independent review.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


macro = module('feature_macro', 'repair-envhd-painted-macro-detail.py')
geometry = module('feature_geometry', 'repair-envhd-painted-surround.py')
cutout = macro.cutout
writer = macro.writer
METHOD = 'python-painted-source-feature-detail-1'


def validate_features(polygons, size):
    # Reuse the existing explicit-polygon geometry checks. The source border
    # stays outside local restoration; opacity is not part of this validator.
    geometry.validate_polygons(polygons, size)


def restore_features(source, painted, polygons, feather_native_pixels=2):
    if type(feather_native_pixels) is not int or not 1 <= feather_native_pixels <= 3:
        raise ValueError('Feature feather must be one to three native pixels')
    validate_features(polygons, source.size)
    # Both operations use the original painting. Restoring source pixels cannot
    # make a coverage/aspect failure pass the unchanged cutout gate.
    base, cutout_operation = cutout.repair(source, painted)
    refined, macro_operation = macro.refine(source, painted)
    target = np.asarray(base).copy()
    source_target = np.asarray(source.resize(base.size, Image.Resampling.NEAREST))
    mask = Image.new('L', base.size)
    draw = ImageDraw.Draw(mask)
    for polygon in polygons:
        draw.polygon([(x * 4, y * 4) for x, y in polygon], fill=255)
    selected = np.asarray(mask) > 0
    fraction = float(selected.mean())
    if not 0 < fraction <= 0.35:
        raise ValueError('Feature restoration must remain a local part of the canvas')
    feather = feather_native_pixels * 4
    weight = np.zeros(selected.shape, dtype=np.float64)
    inner = mask
    for _ in range(feather):
        weight += np.asarray(inner) > 0
        inner = inner.filter(ImageFilter.MinFilter(3))
    weight /= feather
    refined_pixels = np.asarray(refined)
    target[:, :, :3] = np.rint(target[:, :, :3] * (1 - weight[:, :, None])
                              + refined_pixels[:, :, :3] * weight[:, :, None]).astype(np.uint8)
    visible = source_target[:, :, 3] != 0
    original_black = np.all(source_target[:, :, :3] == 0, axis=2)
    target[original_black & visible] = source_target[original_black & visible]
    rounded_black = visible & ~original_black & np.all(target[:, :, :3] == 0, axis=2)
    target[rounded_black, :3] = source_target[rounded_black, :3]
    if (not np.array_equal(target[:, :, 3], source_target[:, :, 3])
            or not np.array_equal(np.all(target[:, :, :3] == 0, axis=2) & visible,
                                  original_black & visible)):
        raise ValueError('Original alpha or visible-black footprint changed')
    if not np.array_equal(target[~selected], np.asarray(base)[~selected]):
        raise ValueError('Painted pixels outside the feature region changed')
    return Image.fromarray(target), dict(
        method=METHOD, scale=4, sourceSize=list(source.size), targetSize=list(base.size),
        sourceFeaturePolygons=polygons, featherNativePixels=feather_native_pixels,
        featherMetric='inward-chebyshev', featureCoverageFraction=fraction,
        restoredTargetPixels=int(np.count_nonzero(weight)),
        fullyRestoredTargetPixels=int(np.count_nonzero(weight == 1)),
        blackRoundingCorrections=int(np.count_nonzero(rounded_black)),
        outsideFeaturePixelsExact=True, sourceAlphaExact=True, visibleBlackExact=True,
        cutoutOperation=cutout_operation, sourceDetailOperation=macro_operation,
        runtimeSelected=False, nativeAcceptance='pending', finalQualityAcceptance='pending')


def execute(source_path, painted_path, output_path, source_key, painted_sha256, polygons, feather=2):
    source_data = cutout.bounded_read(source_path)
    painted_data = cutout.bounded_read(painted_path)
    source_sha = cutout.digest(source_data)
    if cutout.digest(painted_data) != painted_sha256:
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
    candidate, operation = restore_features(source, painted, polygons, feather)
    if (cutout.digest(cutout.bounded_read(source_path)) != source_sha
            or cutout.digest(cutout.bounded_read(painted_path)) != painted_sha256):
        raise ValueError('Source or artwork changed before candidate publication')
    output = writer.write_candidate(candidate, output_path)
    return dict(operation, decodedRgbaSha256=source_key, sourceFileSha256=source_sha,
                generatedSha256=painted_sha256, outputSha256=cutout.digest(output),
                algorithmSha256=cutout.digest(Path(__file__).read_bytes()),
                dependencySha256={p.name: cutout.digest(p.read_bytes()) for p in
                                  [Path(macro.__file__), Path(geometry.__file__),
                                   Path(cutout.__file__), Path(writer.__file__)]})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'painted', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--painted-sha256', required=True)
    parser.add_argument('--features', type=Path, required=True)
    parser.add_argument('--feather', type=int, default=2)
    args = parser.parse_args()
    polygons = json.loads(args.features.read_text())
    print(json.dumps(execute(args.source, args.painted, args.output, args.source_key,
                             args.painted_sha256, polygons, args.feather)))


if __name__ == '__main__':
    main()
