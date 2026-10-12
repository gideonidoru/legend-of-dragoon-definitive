#!/usr/bin/env python3
"""Replay an original opaque RGB patch through pinned container and cutout steps."""
import importlib.util
import io
from pathlib import Path

from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


local = module('rgb_patch_local', 'repair-envhd-painted-local-patch.py')
rgb = module('rgb_patch_container', 'repack-envhd-painted-rgb.py')
bounds = local.bounds
LOCAL_SHA256 = 'a775b1e22b9b6e24ad93e72fe4f0bee0279be524bcb3e80ca1206a499d3eee07'
RGB_SHA256 = 'fce3e10260df107249fe85ec0583b3edbf0581c9f5ecc21dc1c94f77508a83ba'
METHOD = 'python-painted-source-bound-opaque-rgb-local-patch-transfer-1'
PROVENANCE = 'builtin-imagegen-plus-source-bound-rgb-local-patch-transfer'


def replay(source, base_data, patch_data, box):
    if source.mode != 'RGBA':
        raise ValueError('RGB local patch source must be RGBA')
    bounds.validate_dimensions(source.size, 'source')
    local.source_box(box, source.size)
    patch_size = (box[2] - box[0], box[3] - box[1])
    for helper, pin in ((local, LOCAL_SHA256), (rgb, RGB_SHA256)):
        if bounds.digest(Path(helper.__file__).read_bytes()) != pin:
            raise ValueError('RGB local patch dependency identity changed')
    # Validate both headers and lazy decoder states before repack loads pixels.
    base_size = bounds.validate_png_header(base_data, 'painted', source.size)
    painted_size = bounds.validate_png_header(patch_data, 'painted', patch_size, allow_rgb=True)
    if patch_data[25] != 2:
        raise ValueError('RGB local patch requires the original opaque RGB painting')
    with Image.open(io.BytesIO(base_data)) as base, Image.open(io.BytesIO(patch_data)) as painting:
        bounds.validate_opened_png(base, base_size, 'painted', source.size)
        bounds.validate_opened_png(painting, painted_size, 'painted', patch_size, 'RGB')
    converted, container = rgb.repack(patch_data, patch_size)
    output, operation = local.replay(source, base_data, converted, box)
    return output, dict(operation, method=METHOD,
        patchInputSha256=bounds.digest(patch_data),
        rgbPatchRepack=dict(container, algorithmPath='scripts/repack-envhd-painted-rgb.py',
                           algorithmSha256=RGB_SHA256),
        underlyingLocalPatchAlgorithmPath='scripts/repair-envhd-painted-local-patch.py',
        underlyingLocalPatchAlgorithmSha256=LOCAL_SHA256)
