#!/usr/bin/env python3
"""Remove explicit transparent authoring padding without stretching source art.

The source crop is placed at integer native coordinates in a larger canvas.
Generated art must keep that canvas aspect and source silhouette. Only painted
pixels are cropped; the unchanged cutout repair restores categorical visibility.
This operation does not authorize selection or establish internal source intent.
"""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image

REPAIR_SHA256 = 'd94733e201ae2450dabbe0a0aa3915f9c60833672ad3d06b14621c8e70adcd2a'
spec = importlib.util.spec_from_file_location('authoring_crop_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)
METHOD = 'python-explicit-padded-authoring-crop-1'


def validate_layout(source_size, canvas_size, origin):
    bounds.validate_dimensions(source_size, 'source')
    if (not isinstance(canvas_size, (list, tuple)) or len(canvas_size) != 2
            or any(type(n) is not int for n in canvas_size)
            or not isinstance(origin, (list, tuple)) or len(origin) != 2
            or any(type(n) is not int for n in origin)):
        raise ValueError('Authoring canvas and origin require explicit integer pairs')
    bounds.validate_dimensions(canvas_size, 'source')
    width, height = canvas_size
    x, y = origin
    sw, sh = source_size
    if x < 1 or y < 1 or x + sw >= width or y + sh >= height:
        raise ValueError('Source crop needs transparent padding on every side')
    fraction = sw * sh / (width * height)
    if not 0.05 <= fraction <= 0.90:
        raise ValueError('Source crop occupies an invalid authoring canvas fraction')
    return [x * 4, y * 4, (x + sw) * 4, (y + sh) * 4]


def crop(source, painted, canvas_size, origin):
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Padded authoring crop requires RGBA inputs')
    rect = validate_layout(source.size, canvas_size, origin)
    bounds.validate_dimensions(painted.size, 'painted', canvas_size)
    if bounds.digest(Path(bounds.__file__).read_bytes()) != REPAIR_SHA256:
        raise ValueError('Original painted bounds identity changed')
    source_alpha = np.asarray(source)[:, :, 3] > 0
    if not 0.05 <= float(source_alpha.mean()) <= 0.95:
        raise ValueError('Padded authoring crop requires a meaningful cutout silhouette')
    target_size = tuple(n * 4 for n in canvas_size)
    normalized = painted.resize(target_size, Image.Resampling.LANCZOS)
    generated_alpha = np.asarray(normalized)[:, :, 3] >= 128
    expected_alpha = np.zeros((target_size[1], target_size[0]), dtype=bool)
    expected_alpha[rect[1]:rect[3], rect[0]:rect[2]] = np.repeat(np.repeat(source_alpha, 4, axis=0), 4, axis=1)
    union = int(np.count_nonzero(expected_alpha | generated_alpha))
    overlap = int(np.count_nonzero(expected_alpha & generated_alpha))
    iou = overlap / union if union else 0.0
    if iou < 0.97:
        raise ValueError('Padded generated silhouette differs from source placement')
    outside = generated_alpha.copy()
    outside[rect[1]:rect[3], rect[0]:rect[2]] = False
    leaked = int(np.count_nonzero(outside))
    source_visible = int(np.count_nonzero(expected_alpha))
    if leaked > source_visible * 0.01:
        raise ValueError('Generated content would be clipped outside source crop')
    result = normalized.crop(tuple(rect))
    bounds.validate_dimensions(result.size, 'output', source.size)
    return result, dict(method=METHOD, sourceCanvasSize=list(canvas_size),
                        sourceOriginNative=list(origin), cropRectAtTargetScale=rect,
                        sourceSize=list(source.size), targetSize=list(result.size),
                        sourceSilhouetteIoU=iou, minimumSourceSilhouetteIoU=0.97,
                        generatedVisibleOutsideSourceCrop=leaked,
                        maximumOutsideSourceCropFraction=0.01,
                        sourceVisiblePixelsAtAuthoringTarget=source_visible,
                        originalAspectDeviationPercent=abs((painted.width / painted.height) / (canvas_size[0] / canvas_size[1]) - 1) * 100,
                        sourceAlphaApplied=False, sourceRgbApplied=False,
                        originalBoundsAlgorithmSha256=REPAIR_SHA256)
