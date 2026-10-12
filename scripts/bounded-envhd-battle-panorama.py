#!/usr/bin/env python3
"""Independent bounds for MCQ panoramas on a fixed 384x384 authoring canvas.

Only existing 256x384 or 384x256 nonuniform panorama layouts are supported.
Prior battle-material bounds remain unchanged.
"""
import struct

MAX_IMAGE_BYTES = 32 * 1024 * 1024
MAX_TARGET_PIXELS = 1536 * 1024
MAX_PAINTED_PIXELS = 4 * 1024 * 1024
MAX_PAINTED_SIDE = 4096
CANVAS_SIZE = 384


def bounded_read(path):
    with path.open('rb') as stream:
        data = stream.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise ValueError('Input image exceeds the bounded payload')
    return data


def validate_dimensions(size, kind, source_size=None):
    if len(size) != 2 or any(type(n) is not int or n <= 0 for n in size):
        raise ValueError('Invalid panorama canvas dimensions')
    w, h = size
    if kind == 'source':
        if tuple(size) not in ((256, 384), (384, 256)) or w * h * 16 > MAX_TARGET_PIXELS:
            raise ValueError('Source exceeds the bounded native panorama layout')
    elif kind == 'painted':
        if w * h > MAX_PAINTED_PIXELS or max(size) > MAX_PAINTED_SIDE:
            raise ValueError('Generated image exceeds the bounded painted canvas')
        if w != h:
            raise ValueError('Padded panorama authoring requires an exactly square canvas')
    elif kind == 'output':
        if source_size is None or list(size) != [n * 4 for n in source_size]:
            raise ValueError('Painted output dimensions differ from the exact source canvas')
        validate_dimensions(source_size, 'source')
    else:
        raise ValueError('Unknown painted image role')


def source_rect(source_size):
    validate_dimensions(source_size, 'source')
    w, h = source_size
    return [(CANVAS_SIZE - w) // 2, (CANVAS_SIZE - h) // 2, w, h]


def validate_png_header(data, kind, source_size=None, allow_rgb=False):
    types = (2, 6) if allow_rgb else (6,)
    if (len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n'
            or data[8:12] != struct.pack('>I', 13) or data[12:16] != b'IHDR'
            or data[24] != 8 or data[25] not in types):
        raise ValueError('Panorama artwork requires an 8-bit RGB/RGBA PNG')
    size = struct.unpack_from('>2I', data, 16)
    validate_dimensions(size, kind, source_size)
    return size


def validate_opened_png(image, header_size, kind, source_size=None, expected_mode='RGBA'):
    validate_dimensions(image.size, kind, source_size)
    if image.format != 'PNG' or image.size != header_size or image.mode != expected_mode or getattr(image, 'n_frames', 1) != 1:
        raise ValueError('PNG decoder differs from the validated header')
