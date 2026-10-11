#!/usr/bin/env python3
"""Fit a reviewed painted cutout to its source canvas and categorical visibility.

This operation creates a candidate only. It does not select runtime artwork.
Source inputs remain private; the generated artwork and receipt may be published.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image

MAX_IMAGE_BYTES = 32 * 1024 * 1024
MAX_TARGET_PIXELS = 1_000_000
MAX_PAINTED_PIXELS = 4 * 1024 * 1024
MAX_PAINTED_SIDE = 4096


def bounded_read(path):
    with path.open('rb') as stream:
        data = stream.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise ValueError('Input image exceeds the bounded payload')
    return data


def validate_dimensions(size, kind, source_size=None):
    """Check lazy image dimensions before allocating any decoded pixels.

    Source restoration is at most one million target pixels. Generated input
    is at most four megapixels and 4096 pixels on either side. Output must be
    exactly the source's fourfold canvas, not merely below a size ceiling.
    """
    if len(size) != 2 or any(type(n) is not int or n <= 0 for n in size):
        raise ValueError('Invalid painted image dimensions')
    w, h = size
    if kind == 'source':
        if w * h * 16 > MAX_TARGET_PIXELS:
            raise ValueError('Source image exceeds the bounded repair canvas')
    elif kind == 'painted':
        if w * h > MAX_PAINTED_PIXELS or max(size) > MAX_PAINTED_SIDE:
            raise ValueError('Generated image exceeds the bounded painted canvas')
        if source_size is not None and abs((w / h) / (source_size[0] / source_size[1]) - 1) > 0.01:
            raise ValueError('Painted cutout changed the source canvas aspect ratio')
    elif kind == 'output':
        if source_size is None or list(size) != [n * 4 for n in source_size]:
            raise ValueError('Painted output dimensions differ from the exact source canvas')
        validate_dimensions(source_size, 'source')
    else:
        raise ValueError('Unknown painted image role')


def validate_png_header(data, kind, source_size=None, allow_rgb=False):
    types = (2, 6) if allow_rgb else (6,)
    if (len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n'
            or data[8:12] != struct.pack('>I', 13) or data[12:16] != b'IHDR'
            or data[24] != 8 or data[25] not in types):
        raise ValueError('Painted artwork requires 8-bit PNG with RGBA output')
    size = struct.unpack_from('>2I', data, 16)
    validate_dimensions(size, kind, source_size)
    return size


def validate_opened_png(image, header_size, kind, source_size=None, expected_mode='RGBA'):
    """Pillow may process a later IHDR; check its lazy state before decoding."""
    validate_dimensions(image.size, kind, source_size)
    if image.format != 'PNG' or image.size != header_size or image.mode != expected_mode:
        raise ValueError('PNG decoder differs from the validated header')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def repair(source, painted):
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Source and painted cutout must be RGBA')
    validate_dimensions(source.size, 'source')
    validate_dimensions(painted.size, 'painted', source.size)
    native = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
    pixels = np.asarray(painted.resize((source.width * 4, source.height * 4), Image.Resampling.LANCZOS)).copy()
    visible = native[:, :, 3] > 0
    black = np.all(native[:, :, :3] == 0, axis=2)
    # Near-transparent generated edge pixels can contain bright matte colours.
    # Never promote that RGB into the source's fully visible alpha classes.
    usable = visible & ~black & (pixels[:, :, 3] >= 128) & np.any(pixels[:, :, :3] != 0, axis=2)
    missing = visible & ~black & ~usable
    uncovered = visible & ~black & (pixels[:, :, 3] < 16)
    if uncovered.sum() > visible.sum() * 0.10 or (missing.any() and not usable.any()):
        raise ValueError('Painted coverage differs too much from the source; revise the painting')
    # Extend the nearest painted colour into small source-visible gaps. Reusing
    # the earlier neural candidate would reintroduce its stone/plank artifacts.
    targets = np.argwhere(missing)
    anchors = np.argwhere(usable)
    distances = []
    for start in range(0, len(targets), 8):
        chunk = targets[start:start + 8]
        squared = np.sum((chunk[:, None, :] - anchors[None, :, :]) ** 2, axis=2)
        closest = np.argmin(squared, axis=1)
        for target, index, distance in zip(chunk, closest, squared[np.arange(len(chunk)), closest]):
            pixels[target[0], target[1], :3] = pixels[tuple(anchors[index])][:3]
            distances.append(int(distance))
    pixels[:, :, 3] = native[:, :, 3]
    pixels[~visible | black] = native[~visible | black]
    if not np.array_equal(pixels[:, :, 3], native[:, :, 3]):
        raise ValueError('Source alpha changed')
    if not np.array_equal(np.all(pixels[:, :, :3] == 0, axis=2) & visible, black & visible):
        raise ValueError('Visible black class changed')
    return Image.fromarray(pixels, 'RGBA'), {
        'scale': 4,
        'sourceVisiblePixelsAtTargetScale': int(visible.sum()),
        'paintedGapPixelsExtended': int(missing.sum()),
        'paintedUncoveredSourcePixels': int(uncovered.sum()),
        'minimumPaintedCoverageAlpha': 16,
        'minimumPaintedAnchorAlpha': 128,
        'maximumExtensionSquaredTargetPixels': max(distances, default=0),
        'sourceAlphaExact': True,
        'visibleBlackExact': True,
        'edgeAveraging': False,
    }


def execute(source_path, painted_path, output_path, source_key, painted_sha256):
    source_data = bounded_read(source_path)
    painted_data = bounded_read(painted_path)
    source_size = validate_png_header(source_data, 'source')
    painted_size = validate_png_header(painted_data, 'painted', source_size)
    with Image.open(io.BytesIO(source_data)) as image:
        validate_opened_png(image, source_size, 'source')
        source = image.copy()
    with Image.open(io.BytesIO(painted_data)) as image:
        validate_opened_png(image, painted_size, 'painted', source.size)
        painted = image.copy()
    if digest(struct.pack('<II', *source.size) + source.tobytes()) != source_key:
        raise ValueError('Decoded source identity changed')
    if digest(painted_data) != painted_sha256:
        raise ValueError('Generated artwork identity changed')
    candidate, operation = repair(source, painted)
    if output_path.exists() or output_path.is_symlink():
        raise ValueError('Use a fresh candidate output path')
    candidate.save(output_path, format='PNG')
    return dict(operation, decodedRgbaSha256=source_key,
                generatedSha256=painted_sha256, outputSha256=digest(output_path.read_bytes()),
                sourceSize=list(source.size), targetSize=list(candidate.size),
                algorithmSha256=digest(Path(__file__).read_bytes()),
                method='python-painted-cutout-nearest-colour-exact-visibility-1',
                nativeAcceptance='pending', finalQualityAcceptance='pending', runtimeSelected=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'painted', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--painted-sha256', required=True)
    args = parser.parse_args()
    print(json.dumps(execute(args.source, args.painted, args.output, args.source_key, args.painted_sha256)))
