#!/usr/bin/env python3
"""Match an opaque, uniform native border without changing source feature pixels."""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('opaque_border_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)
METHOD = 'python-painted-opaque-uniform-source-border-1'


def repair_border(source, painted):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Opaque border repair requires validated RGBA images')
    native = np.asarray(source)
    if not np.all(native[:, :, 3] == 255):
        raise ValueError('Opaque border repair cannot replace a transparent source')
    border = np.concatenate((native[0], native[-1], native[:, 0], native[:, -1]))
    colour = border[0, :3]
    if not np.all(border[:, :3] == colour):
        raise ValueError('Opaque source border is not one exact uniform colour')
    target = (source.width * 4, source.height * 4)
    pixels = np.asarray(painted.resize(target, Image.Resampling.LANCZOS)).copy()
    if not np.all(pixels[:, :, 3] == 255):
        raise ValueError('Opaque border repair requires an opaque painting')
    native_rgb = np.repeat(np.repeat(native[:, :, :3], 4, axis=0), 4, axis=1)
    y, x = np.indices(pixels.shape[:2])
    depth = np.minimum.reduce((x, y, target[0] - 1 - x, target[1] - 1 - y))
    # The exact outer native pixel is uniformly empty background. Feather only
    # into identical native background pixels; source feature/ground-shadow
    # pixels are excluded even when they are close to an edge.
    eligible = (depth < 8) & np.all(native_rgb == colour, axis=2)
    weight = np.clip((depth.astype(float) - 3) / 5, 0, 1)
    rgb = pixels[:, :, :3].astype(float)
    blended = np.rint(rgb * weight[:, :, None] + colour * (1 - weight[:, :, None])).astype(np.uint8)
    changed = eligible & np.any(pixels[:, :, :3] != blended, axis=2)
    pixels[eligible, :3] = blended[eligible]
    result = Image.fromarray(pixels, 'RGBA')
    return result, dict(method=METHOD, sourceBorderRgb=[int(v) for v in colour],
                       exactSourceBorderPixels=1, featherTargetPixels=4,
                       modifiedTargetPixels=int(changed.sum()),
                       sourceFeaturePixelsExcluded=True, workingSize=list(target))
