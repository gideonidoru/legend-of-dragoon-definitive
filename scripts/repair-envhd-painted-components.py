#!/usr/bin/env python3
"""Replay bounded custom component transfers without copying source RGB pixels."""
import importlib.util
import io
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

spec = importlib.util.spec_from_file_location('component_cutout_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)
METHOD = 'python-painted-component-transfer-1'
PROVENANCE = 'builtin-imagegen-plus-bounded-python-component-transfer'


def box(value, size):
    if (not isinstance(value, list) or len(value) != 4
            or any(type(v) is not int for v in value)
            or not 0 <= value[0] < value[2] <= size[0]
            or not 0 <= value[1] < value[3] <= size[1]):
        raise ValueError('Component rectangle must use bounded integer image coordinates')
    return value


def contained(inner, outer):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and inner[2] <= outer[2] and inner[3] <= outer[3]


def validate_regions(regions, source_size, insert_size, allowed_box):
    box(allowed_box, source_size)
    if not isinstance(regions, list) or not 1 <= len(regions) <= 4:
        raise ValueError('Component transfer requires one to four explicit regions')
    names = set()
    for region in regions:
        if not isinstance(region, dict) or set(region) != {'name', 'nativeBox', 'insertBox', 'maskBox', 'channelRatios'}:
            raise ValueError('Component region controls changed')
        name = region['name']
        if not isinstance(name, str) or not name.strip() or len(name) > 64 or name in names:
            raise ValueError('Component names must be unique bounded strings')
        names.add(name)
        for label in ['nativeBox', 'maskBox']:
            if not contained(box(region[label], source_size), allowed_box):
                raise ValueError('Component regions must stay inside the allowed source rectangle')
        box(region['insertBox'], insert_size)
        ratios = region['channelRatios']
        if not isinstance(ratios, list) or not 1 <= len(ratios) <= 3:
            raise ValueError('Component mask needs one to three channel comparisons')
        for ratio in ratios:
            if (not isinstance(ratio, list) or len(ratio) != 3
                    or any(type(c) is not int or c not in (0, 1, 2) for c in ratio[:2])
                    or ratio[0] == ratio[1] or type(ratio[2]) not in (int, float)
                    or not math.isfinite(ratio[2]) or not 0.5 <= ratio[2] <= 4):
                raise ValueError('Component channel ratios must be finite bounded comparisons')


def transfer(source, base, insert, regions, allowed_box):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(base.size, 'output', source.size)
    bounds.validate_dimensions(insert.size, 'painted', source.size)
    if any(image.mode != 'RGBA' for image in [source, base, insert]):
        raise ValueError('Component transfer requires RGBA images')
    validate_regions(regions, source.size, insert.size, allowed_box)
    native = np.asarray(source)
    original = np.asarray(base)
    native_alpha = np.repeat(np.repeat(native[:, :, 3], 4, axis=0), 4, axis=1)
    if not np.array_equal(original[:, :, 3], native_alpha):
        raise ValueError('Component base must retain exact source alpha')
    pixels = original.copy()
    native_rgb = native[:, :, :3].astype(np.float64)
    operations = []
    for region in regions:
        mask = np.zeros(native.shape[:2], dtype=bool)
        x0, y0, x1, y1 = region['maskBox']
        mask[y0:y1, x0:x1] = native[y0:y1, x0:x1, 3] > 0
        for first, second, ratio in region['channelRatios']:
            mask &= native_rgb[:, :, first] > native_rgb[:, :, second] * ratio
        x0, y0, x1, y1 = region['nativeBox']
        target = [v * 4 for v in region['nativeBox']]
        sampled = np.asarray(insert.crop(region['insertBox']).resize(((x1 - x0) * 4, (y1 - y0) * 4), Image.Resampling.LANCZOS))
        local_mask = np.repeat(np.repeat(mask[y0:y1, x0:x1], 4, axis=0), 4, axis=1)
        if not local_mask.any():
            raise ValueError('Component mask does not cover its target rectangle')
        uncovered = int(np.count_nonzero(local_mask & (sampled[:, :, 3] < 16)))
        usable = local_mask & (sampled[:, :, 3] >= 128) & np.any(sampled[:, :, :3] != 0, axis=2)
        if uncovered > np.count_nonzero(local_mask) * 0.10 or not usable.any():
            raise ValueError('Component painted coverage differs too much from its source mask')
        rgb = sampled[:, :, :3].astype(np.float64)
        source_mean = native_rgb[mask].mean(axis=0)
        paint_mean = rgb[local_mask].mean(axis=0)
        if np.any(paint_mean <= 0):
            raise ValueError('Component exposure has a zero channel mean')
        gain = source_mean / paint_mean
        if not np.all(np.isfinite(gain)) or np.any(gain > 4):
            raise ValueError('Component exposure gain exceeds the bounded range')
        rgb = np.rint(rgb * gain).clip(0, 255).astype(np.uint8)
        weight = np.asarray(Image.fromarray((local_mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))).astype(np.float64) / 255
        local = pixels[target[1]:target[3], target[0]:target[2]]
        weight *= local[:, :, 3] > 0
        local[:, :, :3] = np.rint(local[:, :, :3] * (1 - weight[:, :, None]) + rgb * weight[:, :, None]).clip(0, 255).astype(np.uint8)
        operations.append(dict(region, sourceMaskPixels=int(mask.sum()), sourceMeanRgb=source_mean.tolist(),
                               paintMeanRgb=paint_mean.tolist(), channelGain=gain.tolist(),
                               sourceMaskHdPixels=int(local_mask.sum()), paintedUncoveredMaskPixels=uncovered))
    changed = np.any(pixels != original, axis=2)
    allowed = np.zeros(changed.shape, dtype=bool)
    x0, y0, x1, y1 = [v * 4 for v in allowed_box]
    allowed[y0:y1, x0:x1] = True
    if np.any(changed & ~allowed) or not np.array_equal(pixels[:, :, 3], original[:, :, 3]):
        raise ValueError('Component transfer changed protected pixels or alpha')
    return Image.fromarray(pixels), dict(method=METHOD, sourceSize=list(source.size), targetSize=list(base.size),
        insertSize=list(insert.size), scale=4, allowedNativeBox=allowed_box, regions=operations,
        maskResampling='NEAREST4x', maskBlurRadiusHdPixels=0.8, rgbResampling='LANCZOS',
        rounding='numpy.rintclipuint8', changedPixels=int(changed.sum()), baseAlphaExact=True,
        outsideAllowedBoxExact=True, sourceRgbPixelsCopied=False,
        minimumPaintedCoverageAlpha=16, minimumPaintedAnchorAlpha=128, maximumUncoveredMaskFraction=0.10)


def replay(source, base_input_data, insert_input_data, regions, allowed_box):
    # Validate both compressed headers and lazy decoder states before copying pixels.
    base_size = bounds.validate_png_header(base_input_data, 'painted', source.size)
    insert_size = bounds.validate_png_header(insert_input_data, 'painted', source.size)
    with Image.open(io.BytesIO(base_input_data)) as base_input, Image.open(io.BytesIO(insert_input_data)) as insert_input:
        bounds.validate_opened_png(base_input, base_size, 'painted', source.size)
        bounds.validate_opened_png(insert_input, insert_size, 'painted', source.size)
        validate_regions(regions, source.size, insert_size, allowed_box)
        base, base_operation = bounds.repair(source, base_input)
        result, operation = transfer(source, base, insert_input, regions, allowed_box)
    return result, dict(operation, baseCutoutRepair=base_operation,
                        baseInputSha256=bounds.digest(base_input_data), insertInputSha256=bounds.digest(insert_input_data))
