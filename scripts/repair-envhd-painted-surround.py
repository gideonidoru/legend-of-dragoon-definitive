#!/usr/bin/env python3
"""Preserve an opaque source surround around explicitly reviewed painted features.

The polygons express author-reviewed geometry, not inferred object identity.
Interpolated source colours supply the surround; the outer native-pixel band
retains recoverable original RGB values. Inputs and original visibility repair
remain separate. This does not prove motif fidelity or native placement.
"""
import hashlib
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

spec = importlib.util.spec_from_file_location('surround_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)

METHOD = 'python-painted-opaque-source-surround-preserve-1'


def cross(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def intersects(a, b, c, d):
    turns = [cross(a, b, c), cross(a, b, d), cross(c, d, a), cross(c, d, b)]
    if turns[0] * turns[1] < 0 and turns[2] * turns[3] < 0:
        return True
    for turn, p, q, point in [(turns[0], a, b, c), (turns[1], a, b, d),
                             (turns[2], c, d, a), (turns[3], c, d, b)]:
        if turn == 0 and all(min(p[i], q[i]) <= point[i] <= max(p[i], q[i]) for i in range(2)):
            return True
    return False


def validate_polygons(polygons, size):
    if not isinstance(polygons, list) or not 1 <= len(polygons) <= 8:
        raise ValueError('Surround repair requires one to eight explicit feature polygons')
    for polygon in polygons:
        if (not isinstance(polygon, list) or not 3 <= len(polygon) <= 32
                or any(not isinstance(point, list) or len(point) != 2
                       or any(type(v) is not int for v in point) for point in polygon)):
            raise ValueError('Feature polygons require bounded integer native coordinates')
        if any(not 1 <= point[i] <= size[i] - 2 for point in polygon for i in range(2)):
            raise ValueError('Feature polygons must stay inside the preserved source border')
        if len(set(map(tuple, polygon))) != len(polygon):
            raise ValueError('Feature polygon repeats a vertex')
        edges = list(zip(polygon, polygon[1:] + polygon[:1]))
        area = sum(a[0] * b[1] - b[0] * a[1] for a, b in edges)
        if area == 0:
            raise ValueError('Feature polygon has no area')
        for i, (a, b) in enumerate(edges):
            for j in range(i + 1, len(edges)):
                if j == i + 1 or (i == 0 and j == len(edges) - 1):
                    continue
                if intersects(a, b, *edges[j]):
                    raise ValueError('Feature polygon intersects itself')


def restore_surround(source, painted, feature_polygons, feather_native_pixels=2):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Surround repair requires validated RGBA images')
    if type(feather_native_pixels) is not int or not 1 <= feather_native_pixels <= 3:
        raise ValueError('Surround feather must be one to three native pixels')
    validate_polygons(feature_polygons, source.size)
    if not np.all(np.asarray(source)[:, :, 3] == 255):
        raise ValueError('Surround repair requires an opaque source')
    # Requiring opaque generated pixels prevents this operation from silently
    # accepting a painting rejected by the existing missing-coverage gate.
    if not np.all(np.asarray(painted)[:, :, 3] == 255):
        raise ValueError('Surround repair requires opaque generated artwork')
    size = (source.width * 4, source.height * 4)
    pixels = np.asarray(painted.resize(size, Image.Resampling.LANCZOS))
    native = np.asarray(source.resize(size, Image.Resampling.NEAREST))
    base = np.asarray(source.resize(size, Image.Resampling.LANCZOS)).copy()
    mask = Image.new('L', size)
    draw = ImageDraw.Draw(mask)
    for polygon in feature_polygons:
        draw.polygon([(x * 4, y * 4) for x, y in polygon], fill=255)
    fraction = float((np.asarray(mask) > 0).mean())
    if not 0.10 <= fraction <= 0.90:
        raise ValueError('Feature coverage must leave a substantial source surround')
    feather = feather_native_pixels * 4
    weight = np.zeros((size[1], size[0]), dtype=float)
    inner = mask
    for _ in range(feather):
        weight += np.asarray(inner) > 0
        inner = inner.filter(ImageFilter.MinFilter(3))
    weight /= feather
    y, x = np.indices(weight.shape)
    edge = np.minimum.reduce((x, y, size[0] - 1 - x, size[1] - 1 - y)) < 4
    base[edge] = native[edge]
    rgb = np.rint(pixels[:, :, :3] * weight[:, :, None]
                  + base[:, :, :3] * (1 - weight[:, :, None])).astype(np.uint8)
    output = np.dstack((rgb, pixels[:, :, 3]))
    result = Image.fromarray(output, 'RGBA')
    return result, dict(method=METHOD, sourceSize=list(source.size), generatedSize=list(painted.size),
                        targetSize=list(size), scale=4, sourceFeaturePolygons=feature_polygons,
                        featherNativePixels=feather_native_pixels, featureCoverageFraction=fraction,
                        generatedDetailTargetPixels=int(np.count_nonzero(weight)),
                        pureGeneratedTargetPixels=int(np.count_nonzero(weight == 1)),
                        sourceSurroundTargetPixels=int(np.count_nonzero(weight == 0)),
                        exactSourceBorderTargetPixels=int(np.count_nonzero(edge)),
                        exactSourceBorderNativePixels=1, sourceSurroundResampling='lanczos',
                        sourceBorderResampling='nearest', featherMetric='inward-chebyshev',
                        sourceAlphaApplied=False, originalBorderRgbRecoverable=True,
                        boundsAlgorithmSha256=hashlib.sha256(Path(bounds.__file__).read_bytes()).hexdigest())
