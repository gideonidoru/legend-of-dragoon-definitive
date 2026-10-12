#!/usr/bin/env python3
"""Record an opaque RGB painting in RGBA without changing colour or geometry.

The generated RGB file remains authoritative and must be retained. This operation
adds only a constant alpha channel; the separate pinned cutout repair restores
original source visibility. It does not select or visually accept artwork.
"""
import importlib.util
import io
from pathlib import Path

from PIL import Image

REPAIR_SHA256 = 'd94733e201ae2450dabbe0a0aa3915f9c60833672ad3d06b14621c8e70adcd2a'
spec = importlib.util.spec_from_file_location('rgb_repack_bounds', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
bounds = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounds)
METHOD = 'python-opaque-rgb-container-to-rgba-1'


def repack(data, source_size):
    if len(data) > bounds.MAX_IMAGE_BYTES:
        raise ValueError('RGB painting exceeds the bounded payload')
    bounds.validate_dimensions(source_size, 'source')
    size = bounds.validate_png_header(data, 'painted', source_size, allow_rgb=True)
    if data[25] != 2:
        raise ValueError('Container repack requires an original RGB PNG')
    if bounds.digest(Path(bounds.__file__).read_bytes()) != REPAIR_SHA256:
        raise ValueError('Original painted bounds identity changed')
    with Image.open(io.BytesIO(data)) as painting:
        bounds.validate_opened_png(painting, size, 'painted', source_size, 'RGB')
        if 'transparency' in painting.info or getattr(painting, 'is_animated', False):
            raise ValueError('Container repack requires one opaque RGB painting')
        original_rgb = painting.tobytes()
        converted = painting.convert('RGBA')
    if (converted.size != size or converted.convert('RGB').tobytes() != original_rgb
            or converted.getchannel('A').getextrema() != (255, 255)):
        raise ValueError('Container repack changed RGB, geometry or opaque alpha')
    stream = io.BytesIO()
    converted.save(stream, format='PNG')
    result = stream.getvalue()
    return result, dict(method=METHOD, originalInputSha256=bounds.digest(data),
                       convertedInputSha256=bounds.digest(result),
                       originalInputMode='RGB', convertedInputMode='RGBA',
                       sourceSize=list(source_size), generatedSize=list(size),
                       rgbPixelsExact=True, opaqueAlphaAdded=255,
                       geometryChanged=False, sourceAlphaApplied=False,
                       sourceRgbApplied=False, originalBoundsAlgorithmSha256=REPAIR_SHA256)
