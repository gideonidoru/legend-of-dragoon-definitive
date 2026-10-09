#!/usr/bin/env python3
"""Private conservative UV-cell footprint audit, AGPL v3; see LICENSE.
Checks whole source texel cells intersecting UV triangles, including boundary touches.
Closed-cell overlap may overestimate actual fragments; it is a conservative rejection gate.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import sys
import numpy as np
import PIL

spec = importlib.util.spec_from_file_location('materials', Path(__file__).with_name('audit-model-materials.py'))
materials = importlib.util.module_from_spec(spec)
spec.loader.exec_module(materials)


def triangle_cells(width, height, triangle):
    vertices = np.array(triangle, dtype=float)
    low = np.maximum(np.floor(vertices.min(axis=0)).astype(int), 0)
    high = np.minimum(np.floor(vertices.max(axis=0)).astype(int), np.array([width, height])-1)
    result = np.zeros((height, width), dtype=bool)
    if np.any(high < low): return result
    xx, yy = np.meshgrid(np.arange(low[0], high[0]+1)+0.5, np.arange(low[1], high[1]+1)+0.5)
    valid = np.ones(xx.shape, dtype=bool)
    # The bounding box already supplies the two rectangle axes. Triangle edge normals
    # supply the remaining separating axes, so narrow/degenerate UV triangles are retained.
    for start, end in zip(vertices, np.roll(vertices, -1, axis=0)):
        delta = end-start
        axis = np.array([-delta[1], delta[0]])
        projection = vertices @ axis
        middle = xx*axis[0]+yy*axis[1]
        radius = (abs(axis[0])+abs(axis[1]))/2
        valid &= (middle+radius >= projection.min()-1e-8) & (middle-radius <= projection.max()+1e-8)
    result[low[1]:high[1]+1, low[0]:high[0]+1] = valid
    return result


def footprint_masks(model, tim):
    materials.audit(model, tim)
    width, height, cw, ch, colors, indices = materials.texture(tim)
    _, packets, _, _ = materials.faces(model)
    if len(packets) > 5000:
        raise ValueError('Too many faces for private sampling inspection')
    masks = {}
    for _, clut, coords in packets:
        palette = ((clut >> 6) & 15)*(cw//16)+(clut & 3)
        mask = masks.setdefault(palette, np.zeros((height, width), dtype=bool))
        mask |= triangle_cells(width, height, coords[:3])
        if len(coords) == 4:
            mask |= triangle_cells(width, height, [coords[1], coords[3], coords[2]])
    return masks


def footprint_report(model, tim):
    report, _, _ = materials.audit(model, tim)
    width, height, cw, ch, colors, indices = materials.texture(tim)
    _, packets, _, _ = materials.faces(model)
    integer_masks, _ = materials.material_masks(width, height, cw, ch, packets)
    masks = footprint_masks(model, tim)
    expanded = overlap = conflicts = 0
    samples = []
    for y in range(height):
        for x in range(width):
            active = [palette for palette, mask in masks.items() if mask[y, x]]
            original_active = [palette for palette, mask in integer_masks.items() if mask.getpixel((x, y))]
            expanded += bool(set(active)-set(original_active))
            overlap += len(active) > 1
            index = (indices[y*(width//2)+x//2] >> (4*(x%2))) & 15
            values = {colors[palette*16+index] for palette in active}
            if len(values) > 1:
                conflicts += 1
                if len(samples) < 32: samples.append({'x': x, 'y': y, 'palettes': active, 'psxValues': sorted(values)})
    report.update({'pipeline': 'definitive-private-uv-footprints-1', 'footprintAdditionalTexels': expanded, 'footprintOverlappingTexels': overlap, 'footprintConflictingColourTexels': conflicts, 'conflictSamples': samples, 'scope': 'Conservative closed UV-cell intersection. Boundary touches can overestimate native fragments. Conflicting colors reject a flat RGBA atlas until a per-material mapping or more exact native sampling proof resolves them.'})
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('model', type=Path)
    parser.add_argument('tim', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]): parser.error('Use a new private folder outside the source checkout')
    if PIL.__version__ != '12.3.0' or np.__version__ != '2.5.1': parser.error('Use the recorded Pillow and NumPy versions')
    report = footprint_report(materials.bounded_read(args.model), materials.bounded_read(args.tim))
    report.update({'environment': platform.platform(), 'pillow': PIL.__version__, 'numpy': np.__version__, 'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'materialAuditScriptSha256': hashlib.sha256(Path(__file__).with_name('audit-model-materials.py').read_bytes()).hexdigest(), 'command': [sys.executable, *sys.argv]})
    args.output.mkdir(exist_ok=False)
    (args.output/'manifest.json').write_text(json.dumps(report, indent=2)+'\n')
    print('Private footprint audit:', args.output, '| conflicting texels:', report['footprintConflictingColourTexels'])


if __name__ == '__main__':
    main()
