#!/usr/bin/env python3
"""Replay one source-bound painted patch while preserving its reviewed base."""
import importlib.util
import io
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('local_patch_cutout', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)
METHOD = 'python-painted-source-bound-local-patch-transfer-1'
PROVENANCE = 'builtin-imagegen-plus-source-bound-local-patch-transfer'
MAX_SOURCE_AREA_FRACTION = 0.25


def source_box(value, size):
    if (not isinstance(value, list) or len(value) != 4
            or any(type(v) is not int for v in value)
            or not 0 <= value[0] < value[2] <= size[0]
            or not 0 <= value[1] < value[3] <= size[1]):
        raise ValueError('Local patch rectangle must use bounded integer source coordinates')
    if (value[2] - value[0]) * (value[3] - value[1]) > size[0] * size[1] * MAX_SOURCE_AREA_FRACTION:
        raise ValueError('Local patch exceeds the quarter-canvas source budget')
    return value


def replay(source, base_input_data, patch_input_data, box):
    if source.mode != 'RGBA':
        raise ValueError('Local patch source must be RGBA')
    bounds.validate_dimensions(source.size, 'source')
    source_box(box, source.size)
    patch_source_size = (box[2] - box[0], box[3] - box[1])
    # Both headers and lazy decoder states are bounded before either pixel decode.
    base_size = bounds.validate_png_header(base_input_data, 'painted', source.size)
    patch_size = bounds.validate_png_header(patch_input_data, 'painted', patch_source_size)
    with Image.open(io.BytesIO(base_input_data)) as base_input, Image.open(io.BytesIO(patch_input_data)) as patch_input:
        bounds.validate_opened_png(base_input, base_size, 'painted', source.size)
        bounds.validate_opened_png(patch_input, patch_size, 'painted', patch_source_size)
        patch_source = source.crop(box)
        base, base_operation = bounds.repair(source, base_input)
        patch, patch_operation = bounds.repair(patch_source, patch_input)
    composite = base.copy()
    composite.paste(patch, (box[0] * 4, box[1] * 4))
    before, after = np.asarray(base), np.asarray(composite)
    allowed = np.zeros(before.shape[:2], dtype=bool)
    allowed[box[1] * 4:box[3] * 4, box[0] * 4:box[2] * 4] = True
    if not np.array_equal(before[~allowed], after[~allowed]) or not np.array_equal(before[:, :, 3], after[:, :, 3]):
        raise ValueError('Local patch changed protected pixels or source alpha')
    return composite, dict(method=METHOD, sourceSize=list(source.size), targetSize=list(base.size),
        sourceBox=box, patchSourceSize=list(patch_source.size), patchGeneratedSize=list(patch_size),
        scale=4, maximumSourceAreaFraction=MAX_SOURCE_AREA_FRACTION,
        baseCutoutRepair=base_operation, patchCutoutRepair=patch_operation,
        baseInputSha256=bounds.digest(base_input_data), patchInputSha256=bounds.digest(patch_input_data),
        changedPixels=int(np.count_nonzero(np.any(before != after, axis=2))),
        baseAlphaExact=True, outsideSourceBoxExact=True, sourceRgbPixelsCopied=False,
        transfer='replace-exact-4x-native-rectangle-after-two-pinned-cutouts')
