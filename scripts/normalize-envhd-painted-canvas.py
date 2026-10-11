#!/usr/bin/env python3
"""Fit an explicitly reviewed cutout canvas, then measure silhouette alignment.

This is a separate authoring operation, not a relaxation of the existing cutout
recipe. It never applies source visibility or establishes material/motif intent.
"""
import hashlib
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('canvas_fit_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)

METHOD = 'python-painted-source-canvas-silhouette-fit-1'
MAX_ASPECT_DEVIATION = 0.20
MIN_SILHOUETTE_IOU = 0.97
MAX_ROW_BOUNDARY_ERROR_NATIVE_PIXELS = 3


def normalize(source, painted):
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Canvas fitting requires RGBA source and generated artwork')
    repair.validate_dimensions(source.size, 'source')
    repair.validate_dimensions(painted.size, 'painted')
    deviation = abs((painted.width / painted.height) / (source.width / source.height) - 1)
    if deviation > MAX_ASPECT_DEVIATION:
        raise ValueError('Generated canvas exceeds the explicit aspect-fit bound')
    native_mask = np.asarray(source)[:, :, 3] > 0
    fraction = float(native_mask.mean())
    if not 0.10 <= fraction <= 0.95:
        raise ValueError('Canvas fitting requires a substantial non-opaque source silhouette')
    size = (source.width * 4, source.height * 4)
    result = painted.resize(size, Image.Resampling.LANCZOS)
    native = np.repeat(np.repeat(native_mask, 4, axis=0), 4, axis=1)
    generated = np.asarray(result)[:, :, 3] >= 128
    intersection = int(np.count_nonzero(native & generated))
    union = int(np.count_nonzero(native | generated))
    iou = intersection / union
    if iou < MIN_SILHOUETTE_IOU:
        raise ValueError('Fitted generated silhouette differs from the native source')
    maximum = 0
    for expected, actual in zip(native, generated):
        left = np.flatnonzero(expected)
        right = np.flatnonzero(actual)
        if bool(len(left)) != bool(len(right)):
            raise ValueError('Fitted artwork changes occupied source rows')
        if len(left):
            maximum = max(maximum, abs(int(left[0]) - int(right[0])), abs(int(left[-1]) - int(right[-1])))
    if maximum > MAX_ROW_BOUNDARY_ERROR_NATIVE_PIXELS * 4:
        raise ValueError('Fitted artwork moves a source row boundary too far')
    return result, dict(method=METHOD, sourceSize=list(source.size), generatedSize=list(painted.size),
                        targetSize=list(size), scale=4, resampling='lanczos',
                        aspectDeviation=deviation, maximumAspectDeviation=MAX_ASPECT_DEVIATION,
                        sourceVisibleFraction=fraction, generatedCoverageAlpha=128,
                        silhouetteIntersectionPixels=intersection, silhouetteUnionPixels=union,
                        silhouetteIou=iou, minimumSilhouetteIou=MIN_SILHOUETTE_IOU,
                        maximumRowBoundaryErrorTargetPixels=maximum,
                        maximumAllowedRowBoundaryErrorNativePixels=MAX_ROW_BOUNDARY_ERROR_NATIVE_PIXELS,
                        sourceAlphaApplied=False,
                        boundsAlgorithmSha256=hashlib.sha256(Path(repair.__file__).read_bytes()).hexdigest())
