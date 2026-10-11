#!/usr/bin/env python3
"""Register painted detail to reviewed source geometry before visibility repair.

Control points are in native canvas units and ordered around a convex shape.
This operation does not apply the source alpha or select runtime artwork.
"""
import hashlib
from pathlib import Path

import numpy as np
from PIL import Image


METHOD = 'python-painted-cutout-perspective-registration-1'
ALGORITHM_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()


def quad(points, width, height):
    if (not isinstance(points, list) or len(points) != 4
            or any(not isinstance(p, list) or len(p) != 2 for p in points)
            or any(type(v) not in (int, float) for p in points for v in p)):
        raise ValueError('Registration needs four numeric canvas control points')
    result = np.asarray(points, dtype=float)
    if not np.isfinite(result).all() or np.any(result < 0) or np.any(result > [width, height]):
        raise ValueError('Registration control point is outside the source canvas')
    edges = np.roll(result, -1, axis=0) - result
    next_edges = np.roll(edges, -1, axis=0)
    crosses = edges[:, 0] * next_edges[:, 1] - edges[:, 1] * next_edges[:, 0]
    if not (np.all(crosses > 0) or np.all(crosses < 0)):
        raise ValueError('Registration control points must form a convex ordered shape')
    return result, np.sign(crosses[0])


def register(source, painted, source_quad, painted_quad):
    if source.mode != 'RGBA' or painted.mode != 'RGBA':
        raise ValueError('Registration source and painted cutout must be RGBA')
    if source.width * source.height * 16 > 1_000_000:
        raise ValueError('Registration exceeds the bounded repair canvas')
    if abs((painted.width / painted.height) / (source.width / source.height) - 1) > 0.01:
        raise ValueError('Registration cannot change the canvas aspect ratio')
    source_points, source_order = quad(source_quad, *source.size)
    painted_points, painted_order = quad(painted_quad, *source.size)
    if source_order != painted_order:
        raise ValueError('Registration cannot reflect or reverse the painted shape')
    source_points /= source.size
    painted_points /= source.size
    matrix, values = [], []
    for (x, y), (u, v) in zip(source_points, painted_points):
        matrix.extend([[x, y, 1, 0, 0, 0, -u*x, -u*y], [0, 0, 0, x, y, 1, -v*x, -v*y]])
        values.extend([u, v])
    matrix = np.asarray(matrix)
    if np.linalg.cond(matrix) > 1e8:
        raise ValueError('Registration control geometry is unstable')
    a, b, c, d, e, f, g, h = np.linalg.solve(matrix, np.asarray(values))
    if min(1, 1 + g, 1 + h, 1 + g + h) <= 0.1:
        raise ValueError('Registration crosses a perspective pole in the canvas')
    width, height = source.width * 4, source.height * 4
    coefficients = [a, b * width / height, c * width, d * height / width, e, f * height, g / width, h / height]
    normalized = painted.resize((width, height), Image.Resampling.LANCZOS)
    result = normalized.transform((width, height), Image.Transform.PERSPECTIVE, coefficients, Image.Resampling.BICUBIC)
    return result, dict(method=METHOD, algorithmSha256=ALGORITHM_SHA256,
                        sourceQuad=source_quad, paintedQuad=painted_quad,
                        sourceSize=list(source.size), targetSize=[width, height], scale=4,
                        resampling='bicubic', sourceAlphaApplied=False)
