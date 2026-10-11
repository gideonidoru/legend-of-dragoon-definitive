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


def digest(data):
    return hashlib.sha256(data).hexdigest()


def repair(source, painted):
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Source and painted cutout must be RGBA')
    if source.width * source.height * 16 > 1_000_000:
        raise ValueError('Painted cutout exceeds the bounded repair canvas')
    if abs((painted.width / painted.height) / (source.width / source.height) - 1) > 0.01:
        raise ValueError('Painted cutout changed the source canvas aspect ratio')
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
    source_data = source_path.read_bytes()
    painted_data = painted_path.read_bytes()
    if len(source_data) > 32 * 1024 * 1024 or len(painted_data) > 32 * 1024 * 1024:
        raise ValueError('Input image exceeds the bounded payload')
    with Image.open(io.BytesIO(source_data)) as image:
        source = image.copy()
    with Image.open(io.BytesIO(painted_data)) as image:
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
