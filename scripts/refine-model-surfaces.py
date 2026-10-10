#!/usr/bin/env python3
"""Private geometry feasibility study, AGPL v3; see LICENSE.
Loop subdivision of eligible original rigid parts, with pinned boundaries/creases.
This emits offline pose images, not a game model, replacement rig or native renderer.
"""
import argparse
import hashlib
import importlib.util
import json
import math
import numbers
from pathlib import Path
import sys
import numpy as np
import PIL


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


poses = load('surface_poses', 'inspect-model-poses.py')
packing = load('surface_packing', 'pack-model-materials.py')


def triangles(polygons):
    result = []
    for refs, uv, clut, colors in polygons:
        if len(refs) not in (3, 4): raise ValueError('Requires triangles or quads')
        for corners in ([(0, 1, 2), (1, 3, 2)] if len(refs) == 4 else [(0, 1, 2)]):
            result.append(([refs[i] for i in corners], None if uv is None else uv[list(corners)].copy(), clut, colors[list(corners)].copy()))
    return result


def refine_part(part, strength=1.0, crease_degrees=110, fixed_vertices=()):
    """Preserve UV/color discontinuities per face and keep open joint borders fixed."""
    if not math.isfinite(strength) or not 0 <= strength <= 1 or not math.isfinite(crease_degrees) or not 0 <= crease_degrees <= 180:
        raise ValueError('Invalid surface study parameters')
    original, polygons = part
    vertices = np.array(original, dtype=float, copy=True)
    if vertices.ndim != 2 or vertices.shape[1] != 3 or not np.isfinite(vertices).all() or len(vertices) > 20000:
        raise ValueError('Invalid or excessive part geometry')
    if (not isinstance(fixed_vertices, (tuple, list, np.ndarray))
            or isinstance(fixed_vertices, np.ndarray) and fixed_vertices.ndim != 1
            or len(fixed_vertices) > len(vertices)):
        raise ValueError('Use a bounded sequence of distinct authored joint vertex indices')
    if any(isinstance(index, (bool, np.bool_)) or not isinstance(index, numbers.Integral)
           or not 0 <= index < len(vertices) for index in fixed_vertices):
        raise ValueError('Invalid authored joint vertex index')
    authored = set(fixed_vertices)
    if len(authored) != len(fixed_vertices):
        raise ValueError('Authored joint vertex indices must be distinct')
    faces = triangles(polygons)
    if len(faces) > 10000: raise ValueError('Part exceeds subdivision budget')
    edges, neighbors, normals = {}, [set() for _ in vertices], []
    vertex_links, seen_faces = [{} for _ in vertices], set()
    for face_index, (refs, uv, _, colors) in enumerate(faces):
        if any(type(ref) is not int or not 0 <= ref < len(vertices) for ref in refs): raise ValueError('Invalid vertex reference')
        if colors.shape != (3, 3) or not np.isfinite(colors).all() or uv is not None and (uv.shape != (3, 2) or not np.isfinite(uv).all()): raise ValueError('Invalid face attributes')
        normal = np.cross(vertices[refs[1]]-vertices[refs[0]], vertices[refs[2]]-vertices[refs[0]])
        length = np.linalg.norm(normal)
        if len(set(refs)) != 3 or length < 1e-10:
            return part, {'changed': False, 'reason': 'degenerate face', 'inputTriangles': len(faces)}
        identity = tuple(sorted(refs))
        if identity in seen_faces:
            return part, {'changed': False, 'reason': 'duplicated overlapping face', 'inputTriangles': len(faces)}
        seen_faces.add(identity)
        normals.append(normal/length)
        for a, b, opposite in ((0, 1, 2), (1, 2, 0), (2, 0, 1)):
            i, j = refs[a], refs[b]
            neighbors[i].add(j); neighbors[j].add(i)
            edges.setdefault(tuple(sorted((i, j))), []).append((face_index, refs[opposite]))
            link = vertex_links[refs[opposite]]
            link.setdefault(i, set()).add(j); link.setdefault(j, set()).add(i)
    if not faces: return part, {'changed': False, 'reason': 'empty part', 'inputTriangles': 0}
    if any(len(owners) > 2 for owners in edges.values()):
        return part, {'changed': False, 'reason': 'non-manifold overlapping faces', 'inputTriangles': len(faces)}
    for link in vertex_links:
        if not link: continue
        visited, pending = set(), [next(iter(link))]
        while pending:
            current = pending.pop()
            if current not in visited:
                visited.add(current); pending.extend(link[current]-visited)
        degrees = [len(adjacent) for adjacent in link.values()]
        if len(visited) != len(link) or any(degree not in (1, 2) for degree in degrees) or degrees.count(1) not in (0, 2):
            return part, {'changed': False, 'reason': 'non-manifold vertex fan', 'inputTriangles': len(faces)}
    pinned, creases = set(authored), set()
    threshold = math.cos(math.radians(crease_degrees))
    for edge, owners in edges.items():
        if len(owners) != 2 or np.dot(normals[owners[0][0]], normals[owners[1][0]]) < threshold:
            creases.add(edge); pinned.update(edge)
        # An authored boundary on a closed part is not detectable as an open edge.
        # Preserve its complete straight edge as well as the original endpoints.
        if all(index in authored for index in edge): creases.add(edge)
    refined = vertices.copy()
    for index, adjacent in enumerate(neighbors):
        if index in pinned or len(adjacent) < 3: continue
        beta = 3/16 if len(adjacent) == 3 else 3/(8*len(adjacent))
        target = vertices[index]*(1-len(adjacent)*beta)+vertices[sorted(adjacent)].sum(axis=0)*beta
        refined[index] += (target-vertices[index])*strength
    edge_indices, added, authored_midpoints = {}, [], []
    for edge in sorted(edges):
        a, b = edge; middle = (vertices[a]+vertices[b])/2
        if edge not in creases:
            owners = edges[edge]
            target = (vertices[a]+vertices[b])*3/8+(vertices[owners[0][1]]+vertices[owners[1][1]])/8
            middle += (target-middle)*strength
        edge_indices[edge] = len(vertices)+len(added); added.append(middle)
        if all(index in authored for index in edge): authored_midpoints.append(edge_indices[edge])
    output = []
    for refs, uv, clut, colors in faces:
        a, b, c = refs; ab, bc, ca = [edge_indices[tuple(sorted(edge))] for edge in ((a, b), (b, c), (c, a))]
        weights = np.array([[1,0,0],[0,1,0],[0,0,1],[.5,.5,0],[0,.5,.5],[.5,0,.5]])
        texture = None if uv is None else weights@uv
        colour = weights@colors
        geometry = [a, b, c, ab, bc, ca]
        for corners in ((0,3,5),(3,1,4),(5,4,2),(3,4,5)):
            output.append(([geometry[i] for i in corners], None if texture is None else texture[list(corners)].copy(), clut, colour[list(corners)].copy()))
    result = np.concatenate((refined, np.array(added)), axis=0)
    return (result, output), {'changed': True, 'inputTriangles': len(faces), 'outputTriangles': len(output), 'inputVertices': len(vertices), 'outputVertices': len(result), 'pinnedOriginalVertices': len(pinned), 'maxOriginalVertexDisplacement': float(np.linalg.norm(refined-vertices, axis=1).max()),
                            'authoredPinnedVertices': len(authored), 'preservedVertexIndices': sorted(int(index) for index in authored) + authored_midpoints}


def refine(parts, iterations=1, strength=1.0, crease_degrees=110):
    if type(iterations) is not int or iterations not in (1, 2): raise ValueError('Use one or two study iterations')
    result, reports = [], []
    for index, part in enumerate(parts):
        current, rounds = part, []
        for _ in range(iterations):
            current, report = refine_part(current, strength, crease_degrees)
            rounds.append(report)
            if not report['changed']: break
        result.append(current); reports.append({'part': index, 'rounds': rounds})
    if sum(len(polygons) for _, polygons in result) > 40000: raise ValueError('Whole model exceeds study triangle budget')
    return result, reports


def study_views(keyframe_count):
    if type(keyframe_count) is not int or not 1 <= keyframe_count <= 256: raise ValueError('Invalid keyframe count')
    return [(0, 0), (keyframe_count//2, math.pi/3), (keyframe_count-1, -math.pi/3)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'tim', 'animation', 'packed-candidate', 'output'): parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--iterations', type=int, choices=(1, 2), default=1)
    parser.add_argument('--strength', type=float, default=1.0)
    parser.add_argument('--crease-degrees', type=float, default=110)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]): parser.error('Keep retail study output outside Git')
    if PIL.__version__ != '12.3.0' or np.__version__ != '2.5.1': parser.error('Use the pinned offline dependencies')
    model, tim, animation = [poses.materials.bounded_read(path) for path in (args.model, args.tim, args.animation)]
    image, mapping, pack = packing.validate_pack(args.packed_candidate, model, tim)
    original = poses.read_geometry(model); keyframes = poses.read_keyframes(animation, len(original))
    refined, topology = refine(original, args.iterations, args.strength, args.crease_degrees)
    selections = study_views(len(keyframes))
    frames, yaws = [item[0] for item in selections], [item[1] for item in selections]
    baseline = [poses.posed_vertices(original, keyframes[frame], yaw) for frame, yaw in zip(frames, yaws)]
    candidate = [poses.posed_vertices(refined, keyframes[frame], yaw) for frame, yaw in zip(frames, yaws)]
    bounds = poses.framing_bounds(baseline+candidate)
    args.output.mkdir(exist_ok=False)
    outputs, differences = [], []
    for view_index, (frame, before, after) in enumerate(zip(frames, baseline, candidate)):
        renders = [poses.render(original, before, tim, None, bounds), poses.render(original, before, tim, image, bounds, material_map=mapping), poses.render(refined, after, tim, image, bounds, material_map=mapping)]
        for index, raster in enumerate(renders):
            path = args.output/f'view-{view_index}-frame-{frame}-candidate-{index}.png';raster.save(path)
            outputs.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        differences.append({'view': view_index, 'frame': frame, 'cameraYawRadians': yaws[view_index], 'textureControl': poses.coverage_delta(renders[0], renders[1]), 'refinedSilhouette': poses.coverage_delta(renders[0], renders[2])})
    report = {'pipeline': 'definitive-private-surface-study-1', 'modelSha256': hashlib.sha256(model).hexdigest(), 'timSha256': hashlib.sha256(tim).hexdigest(), 'animationSha256': hashlib.sha256(animation).hexdigest(), 'atlasEngineSha256': pack['atlasEngineSha256'], 'atlasRgbaBytes': image.width*image.height*4, 'keyframes': frames, 'cameraYawRadians': list(yaws), 'topology': topology, 'iterations': args.iterations, 'strength': args.strength, 'creaseDegrees': args.crease_degrees, 'sourcePartCount': len(original), 'candidatePartCount': len(refined), 'outputs': outputs, 'coverage': differences, 'command': sys.argv, 'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'dependencies': {filename: hashlib.sha256(Path(__file__).with_name(filename).read_bytes()).hexdigest() for filename in ('inspect-model-poses.py','pack-model-materials.py')}, 'pillow': PIL.__version__, 'numpy': np.__version__, 'scope': 'Private Loop-subdivision feasibility images. Boundaries and sharp creases pinned; degenerate/non-manifold parts left unchanged. No authored replacement head, game model, normals, native interpolation, blend proof or Deck cost is delivered.'}
    (args.output/'manifest.json').write_text(json.dumps(report, indent=2)+'\n')
    print('Private surface study:', args.output, '| skipped parts:', sum(not part['rounds'][0]['changed'] for part in topology))


if __name__ == '__main__': main()
