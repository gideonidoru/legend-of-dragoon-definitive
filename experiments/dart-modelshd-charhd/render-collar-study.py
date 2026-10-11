#!/usr/bin/env python3
"""Render actual custom joint geometry and a straight-blend control. AGPL v3.

The control connects the identical posed endpoint collars by linear vertex
interpolation; it is not a screenshot of the released game's original joints.
No original game pixels, actor meshes or animation arrays are used by this tool.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import trimesh


def area(points):
    centred = points - points.mean(0)
    return .5*np.linalg.norm(np.cross(centred, np.roll(centred, -1, axis=0)).sum(0))


def draw_mesh(mesh, right, up, camera, bounds, size):
    vertices, faces = np.asarray(mesh.vertices), np.asarray(mesh.faces)
    low, high = bounds
    scale = min((size[0]-80)/(high[0]-low[0]), (size[1]-60)/(high[1]-low[1]))
    xy = np.column_stack((vertices @ right, vertices @ up))
    xy = (xy - (low + high)*.5)*[scale, -scale] + np.array(size)*.5
    depths = vertices @ camera
    canvas = np.full((*size[::-1], 3), [26, 30, 34], dtype=np.uint8)
    buffer = np.full(size[::-1], -np.inf)
    normals = np.asarray(mesh.vertex_normals)
    light = camera + up*.5 + right*.25; light /= np.linalg.norm(light)
    diffuse = .35 + .65*np.maximum(0, normals @ light)
    colours = np.asarray(mesh.visual.vertex_colors)[:, :3]
    for references in faces:
        triangle = xy[references]
        lower = np.maximum(np.floor(triangle.min(0)).astype(int), 0)
        upper = np.minimum(np.ceil(triangle.max(0)).astype(int), np.array(size)-1)
        if np.any(upper < lower):
            continue
        a, b, c = triangle
        determinant = np.cross(b-a, c-a)
        if abs(determinant) < 1e-10:
            continue
        xx, yy = np.meshgrid(np.arange(lower[0], upper[0]+1)+.5, np.arange(lower[1], upper[1]+1)+.5)
        wa = ((b[0]-xx)*(c[1]-yy)-(b[1]-yy)*(c[0]-xx))/determinant
        wb = ((c[0]-xx)*(a[1]-yy)-(c[1]-yy)*(a[0]-xx))/determinant
        weights = np.stack((wa, wb, 1-wa-wb), axis=-1)
        depth = weights @ depths[references]
        old = buffer[lower[1]:upper[1]+1, lower[0]:upper[0]+1]
        visible = (weights.min(-1) >= -1e-8) & (depth > old)
        pixels = (weights @ colours[references])*(weights @ diffuse[references])[..., None]
        # A faint construction grid distinguishes geometry from concept art.
        grid = weights.min(-1) < .025
        pixels[grid] *= .8
        patch = canvas[lower[1]:upper[1]+1, lower[0]:upper[0]+1]
        patch[visible] = np.clip(pixels[visible], 0, 255).astype(np.uint8)
        old[visible] = depth[visible]
    return Image.fromarray(canvas)


def render(mesh_path, output, ring_vertices, font_path):
    if output.exists() or output.with_suffix('.json').exists():
        raise ValueError('Use a new comparison output')
    mesh = trimesh.load(mesh_path, force='mesh', process=False)
    if len(mesh.vertices) % ring_vertices or len(mesh.vertices)//ring_vertices < 3:
        raise ValueError('Mesh does not match the declared collar grid')
    rings = np.asarray(mesh.vertices).reshape(-1, ring_vertices, 3)
    t = np.linspace(0, 1, len(rings))[:, None, None]
    control_vertices = ((1-t)*rings[0]+t*rings[-1]).reshape(-1, 3)
    control = trimesh.Trimesh(control_vertices, mesh.faces.copy(), process=False)
    control.visual.vertex_colors = np.asarray(mesh.visual.vertex_colors).copy()
    first_axis = np.cross(rings[0][1]-rings[0][0], rings[0][2]-rings[0][1])
    second_axis = np.cross(rings[-1][1]-rings[-1][0], rings[-1][2]-rings[-1][1])
    first_axis /= np.linalg.norm(first_axis); second_axis /= np.linalg.norm(second_axis)
    camera = np.cross(first_axis, second_axis)
    if np.linalg.norm(camera) < .05:
        camera = rings[0][0]-rings[0].mean(0)
    camera /= np.linalg.norm(camera)
    up = -first_axis
    right = np.cross(up, camera); right /= np.linalg.norm(right)
    combined = np.concatenate((control_vertices, mesh.vertices))
    projected = np.column_stack((combined @ right, combined @ up))
    bounds = projected.min(0), projected.max(0)
    font = ImageFont.truetype(str(font_path), 22)
    small = ImageFont.truetype(str(font_path), 18)
    board = Image.new('RGB', (1440, 610), (26, 30, 34))
    draw = ImageDraw.Draw(board)
    midpoint = len(rings)//2
    target_area = (area(rings[0]) + area(rings[-1]))*.5
    controls = control_vertices.reshape(rings.shape)
    for column, (variant, title, ratio) in enumerate([
        (control, 'Straight-blend control', area(controls[midpoint])/target_area),
        (mesh, 'Curved collar study', area(rings[midpoint])/target_area),
    ]):
        board.paste(draw_mesh(variant, right, up, camera, bounds, (720, 490)), (column*720, 65))
        draw.text((column*720+28, 24), title, font=font, fill='#ece4d6')
        draw.text((column*720+28, 548), f'Middle cross-section area: {ratio:.1%} of endpoint average', font=small, fill='#bdc5ce')
    draw.text((28, 584), 'Same source-bound collars and camera. Actual custom geometry; offline study, not a complete actor or gameplay.', font=small, fill='#91a0af')
    board.save(output, quality=95)
    receipt = {'inputMeshSha256': hashlib.sha256(mesh_path.read_bytes()).hexdigest(),
               'comparisonSha256': hashlib.sha256(output.read_bytes()).hexdigest(),
               'rendererSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               'controlMiddleAreaRatio': float(area(controls[midpoint])/target_area),
               'candidateMiddleAreaRatio': float(area(rings[midpoint])/target_area),
               'sameCameraAndCollars': True, 'originalGamePixelsIncluded': False,
               'scope': 'Custom joint strip only; straight interpolation is a study control, not the released engine.'}
    output.with_suffix('.json').write_text(json.dumps(receipt, indent=2)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mesh', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--ring-vertices', type=int, default=32)
    parser.add_argument('--font', type=Path, required=True)
    arguments = parser.parse_args()
    render(arguments.mesh, arguments.output, arguments.ring_vertices, arguments.font)
