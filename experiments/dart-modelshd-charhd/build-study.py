#!/usr/bin/env python3
"""Gated Dart shape/CharHD study. AGPL v3; no normal build or release integration.

Original inputs and unchanged controls are read locally, never emitted into Git.
This is a landmark-based shape experiment, not a finished bespoke reconstruction.
"""
import argparse
from collections import Counter
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


world = module('dart_world', 'build-modelshd-world-pass.py')
exporter = world.exporter
poses = exporter.surface.poses
packing = exporter.surface.packing


def triangulate(part):
    result = []
    for face in part['faces']:
        for corners in ((0, 1, 2), (1, 3, 2)) if len(face['vertices']) == 4 else ((0, 1, 2),):
            result.append({**face, 'vertices': [face['vertices'][i] for i in corners],
                           'normals': [face['normals'][i] for i in corners],
                           'sourceWeights': [face['sourceWeights'][i] for i in corners]})
    return {**part, 'faces': result}


def borders(part):
    edges = Counter(tuple(sorted((a, b))) for face in part['faces']
                    for a, b in zip(face['vertices'], face['vertices'][1:] + face['vertices'][:1]))
    return {vertex for edge, owners in edges.items() if owners == 1 for vertex in edge}


def subdivide(part):
    """Linear tessellation retains source-face attributes, pivots and shared edges."""
    result = triangulate(copy.deepcopy(part))
    points = result['vertices']
    midpoints = {}
    faces = []
    for face in result['faces']:
        references = list(face['vertices'])
        weights = np.asarray(face['sourceWeights'])
        for a, b in ((0, 1), (1, 2), (2, 0)):
            edge = tuple(sorted((references[a], references[b])))
            if edge not in midpoints:
                midpoints[edge] = len(points)
                points.append(((np.array(points[edge[0]]) + points[edge[1]]) * .5).tolist())
            references.append(midpoints[edge])
        weights = np.concatenate((weights, (weights[[0, 1, 2]] + weights[[1, 2, 0]]) * .5))
        for child in ((0, 3, 5), (3, 1, 4), (5, 4, 2), (3, 4, 5)):
            faces.append({'sourceFace': face['sourceFace'], 'vertices': [references[i] for i in child],
                          'normals': [references[i] for i in child], 'sourceWeights': weights[list(child)].tolist()})
    result['faces'] = faces
    return result


def head_delta(points, form):
    """Common facial landmarks in field units; bone-local coordinate systems differ."""
    if form == 'field':
        q = points.copy()
    else:
        q = np.column_stack((points[:, 2] / 15, (70 - points[:, 1]) / 15, -points[:, 0] / 15))
    x, y, z = q.T
    front = np.clip((-z - 3.5) / 1.5, 0, 1)
    neck = np.clip((7 - y) / 1.5, 0, 1)
    gauss = lambda cx, cy, sx, sy: np.exp(-((x-cx)/sx)**2 - ((y-cy)/sy)**2)
    nose = .85 * gauss(0, 2.3, .9, 1.15)
    cheeks = .27 * (gauss(-2.8, 2.4, 1.7, 1.4) + gauss(2.8, 2.4, 1.7, 1.4))
    brows = .18 * (gauss(-2.5, .15, 1.7, .7) + gauss(2.5, .15, 1.7, .7))
    orbits = .15 * (gauss(-2.5, 1, 1.1, .65) + gauss(2.5, 1, 1.1, .65))
    chin = .23 * gauss(0, 5.7, 1.8, .9)
    delta = np.zeros_like(q)
    delta[:, 2] = (-nose - cheeks - brows + orbits - chin) * front * neck
    delta[:, 0] = -.06 * x * gauss(0, 4.7, 5, 2) * front * neck
    if form == 'field':
        return delta
    return np.column_stack((-delta[:, 2] * 15, -delta[:, 1] * 15, delta[:, 0] * 15))


def volume_delta(points, strength):
    """Small joint-pinned fullness adjustment; not fingers, cloth simulation or new rigging."""
    center = (points.max(0) + points.min(0)) * .5
    span = points.max(0) - points.min(0)
    axis = int(span.argmax())
    station = np.clip((points[:, axis] - points[:, axis].min()) / max(span[axis], 1e-6), 0, 1)
    radial = points - center
    radial[:, axis] = 0
    return radial * (strength * np.sin(np.pi * station)**2)[:, None]


def shape(part, form, role):
    result = triangulate(copy.deepcopy(part))
    if role == 'face':
        result = subdivide(subdivide(result))
    original = np.asarray(result['vertices'], dtype=float)
    fixed = sorted(borders(result))
    delta = head_delta(original, form) if role == 'face' else volume_delta(original, .075 if role == 'glove' else .035)
    if fixed:
        delta[fixed] = 0
    refs = np.array([face['vertices'] for face in result['faces']])
    before = original[refs]
    crosses = np.cross(before[:, 1] - before[:, 0], before[:, 2] - before[:, 0])
    amplitude = 1.
    for _ in range(9):
        target = (original + delta * amplitude).astype(np.float32).astype(float)
        after = target[refs]
        new_crosses = np.cross(after[:, 1] - after[:, 0], after[:, 2] - after[:, 0])
        if np.all(np.linalg.norm(new_crosses, axis=1) > 1e-10) and np.all(np.sum(crosses * new_crosses, axis=1) > 0):
            break
        amplitude *= .5
    else:
        raise ValueError('Shape would fold or collapse a triangle')
    if fixed and not np.array_equal(target[fixed], original[fixed]):
        raise ValueError('Shape moved an open attachment boundary')
    normals = np.zeros_like(target)
    # Keep the input lighting orientation while replacing the geometry normals.
    old_normals = np.asarray(part['normals'])
    orientation = {}
    for face in part['faces']:
        orientation.setdefault(face['sourceFace'], []).append(old_normals[face['normals']].mean(0))
    orientation = {key: np.mean(value, axis=0) for key, value in orientation.items()}
    for face, cross in zip(result['faces'], new_crosses):
        old = orientation[face['sourceFace']]
        if np.dot(cross, old) < 0:
            cross = -cross
        np.add.at(normals, face['vertices'], cross)
        face['normals'] = face['vertices']
    length = np.linalg.norm(normals, axis=1)
    normals[length > 1e-10] /= length[length > 1e-10, None]
    normals[length <= 1e-10] = [0, 0, 1]
    result['vertices'], result['normals'] = target.tolist(), normals.tolist()
    return result, {'role': role, 'amplitude': amplitude, 'fixedBorderVertices': len(fixed),
                    'maximumDisplacement': float(np.linalg.norm(target-original, axis=1).max()),
                    'triangles': len(result['faces']), 'vertices': len(target)}


def unpack(part, source):
    polygons = []
    for face in part['faces']:
        refs, uv, clut, colors = source[1][face['sourceFace']]
        weights = np.asarray(face['sourceWeights'])
        # Mirror native byte interpolation, including UV rounding at each corner.
        polygons.append((face['vertices'], None if uv is None else np.floor(weights @ uv + .5),
                         clut, np.floor(weights @ colors + .5)))
    return np.array(part['vertices']), polygons


PAINT_FACES = {'field': {19,24,25,65,81,82,83,84,85,86},
               'combat': {0,1,2,3,4,5,6,11,12,13,21,23,24,25,29,30,31,32,35,36,52,54,57,61,75,94,96,97,98,99,104,105,106,136,137,138,147}}


def paint_atlas(atlas, mapping, tim):
    paint = Image.open(ROOT / 'experiments/dart-modelshd-charhd/resources/charhd-experiment/dart-face-paint-v1.png').convert('RGBA')
    if paint.width != paint.height:
        raise ValueError('Offline detail sampler requires a square paint tile')
    columns = poses.materials.texture(tim)[2] // 16
    palette = 16 * columns - 1
    if palette in mapping:
        raise ValueError('No reserved offline detail palette')
    combined = Image.new('RGBA', (max(atlas.width, paint.width), atlas.height + paint.height))
    combined.paste(atlas, (0, 0))
    combined.paste(paint, (0, atlas.height))
    materials = {**mapping, palette: {'sourceCrop': [0, 0, 1, 1], 'atlasRect': [0, atlas.height, paint.width, paint.height]}}
    clut = (palette // columns) << 6 | (palette % columns)
    return combined, materials, clut


def paint_face(part, source, form, clut):
    points, polygons = unpack(part, source)
    q = points if form == 'field' else np.column_stack((points[:,2]/15, (70-points[:,1])/15, -points[:,0]/15))
    u = .5 + q[:,0] * (464/11) / 512
    v = .5 + (q[:,1]-2.75) * (464/11) / 512
    uv = np.clip(np.column_stack((u, v)), 0, 1).astype(np.float32)
    result = []
    for face, polygon in zip(part['faces'], polygons):
        if face['sourceFace'] in PAINT_FACES[form]:
            result.append((polygon[0], uv[polygon[0]], clut, np.full((len(polygon[0]), 3), 127.5)))
        else: result.append(polygon)
    return points, result


def baseline(data):
    parts, native, identity = exporter.native_parts(data)
    control, _ = exporter.export(data, control=True)
    identifiers = world.part_identities(data, parts, native)
    result = []
    for identifier, original in zip(identifiers, control['parts']):
        path = ROOT / 'integrations/modelshd/src/main/resources/modelshd/models/parts' / (identifier + '.json')
        result.append(json.loads(path.read_text())['parts'][0] if path.exists() else original)
    return parts, native, identity, identifiers, result


def make_form(files, record, form, destination, charhd):
    data, tim, animation = [(files / record[k]).read_bytes() for k in ('model', 'tim', 'animation')]
    parts, native, identity, ids, current = baseline(data)
    atlas, mapping, texture_report = packing.validate_pack(charhd, data, tim)
    detail_atlas, detail_mapping, detail_clut = paint_atlas(atlas, mapping, tim)
    target = copy.deepcopy(current)
    roles = {7: 'face', 2: 'armor', 5: 'glove', 6: 'glove', 13: 'cloth', 14: 'cloth'} if form == 'field' else {7: 'face', 2: 'armor', 5: 'glove', 6: 'glove', 15: 'cloth', 16: 'cloth'}
    reports = []
    for index, role in roles.items():
        target[index], report = shape(current[index], form, role)
        report.update(part=index, sourcePartGeometrySha256=ids[index])
        reports.append(report)
        if report['maximumDisplacement'] > 1e-8:
            payload = {'version': 1, 'sourceGeometrySha256': ids[index], 'parts': [target[index]]}
            (destination / 'parts' / (ids[index] + '.json')).write_text(json.dumps(payload, separators=(',', ':'), allow_nan=False))
        else:
            target[index] = copy.deepcopy(current[index])
    keys = poses.read_keyframes(animation, len(parts))
    candidates = [[unpack(p, source) for p, source in zip(pack, parts)] for pack in (current, target)]
    for key in keys:
        for variant in candidates:
            if any(not np.isfinite(p).all() for p in poses.posed_vertices(variant, key, 0)):
                raise ValueError('Nonfinite posed candidate')
    all_positions = [poses.posed_vertices(variant, keys[0], yaw) for yaw in (0, -45, 90) for variant in candidates]
    bounds = poses.framing_bounds(all_positions)
    renders = []
    for view, yaw in [('front', 0), ('three-quarter', -45), ('rear-side', 90)]:
        for variant, candidate in zip(('current', 'experiment'), candidates):
            positions = poses.posed_vertices(candidate, keys[0], yaw)
            for texture, image, materials in [('stock', None, None), ('charhd', atlas, mapping)]:
                path = destination / f'{form}-{view}-{variant}-{texture}.png'
                poses.render(candidate, positions, tim, image, bounds, size=(768, 900), material_map=materials).save(path)
                renders.append(path.name)
            painted = copy.deepcopy(candidate)
            painted[7] = paint_face((current if variant == 'current' else target)[7], parts[7], form, detail_clut)
            path = destination / f'{form}-{view}-{variant}-painted.png'
            poses.render(painted, positions, tim, detail_atlas, bounds, size=(768,900), material_map=detail_mapping).save(path)
            renders.append(path.name)
        # Preserve a closer facial comparison with identical crop and pose.
        head_positions = [poses.posed_vertices(candidate,keys[0],yaw)[7] for candidate in candidates]
        close = poses.framing_bounds([[p] for p in head_positions])
        for variant, candidate in zip(('current','experiment'),candidates):
            for texture in ('stock','charhd','painted'):
                head = paint_face((current if variant == 'current' else target)[7],parts[7],form,detail_clut) if texture == 'painted' else candidate[7]
                position = poses.posed_vertices([head],keys[0,7:8],yaw)
                path = destination/f'{form}-{view}-{variant}-{texture}-close.png'
                image = None if texture == 'stock' else detail_atlas if texture == 'painted' else atlas
                materials = None if texture == 'stock' else detail_mapping if texture == 'painted' else mapping
                poses.render([head],position,tim,image,close,size=(768,768),material_map=materials).save(path)
                renders.append(path.name)
    sampled = sorted(set((0, len(keys)//2, len(keys)-1)))
    pose_bounds = poses.framing_bounds([poses.posed_vertices(candidate, keys[key], 0)
                                      for key in sampled for candidate in candidates])
    for key in sampled:
        for variant, candidate in zip(('current', 'experiment'), candidates):
            posed = copy.deepcopy(candidate)
            image, materials = atlas, mapping
            if variant == 'experiment':
                posed[7] = paint_face(target[7], parts[7], form, detail_clut)
                image, materials = detail_atlas, detail_mapping
            path = destination / f'{form}-pose-{key}-{variant}.png'
            positions = poses.posed_vertices(posed, keys[key], 0)
            poses.render(posed, positions, tim, image, pose_bounds, size=(512,600), material_map=materials).save(path)
            renders.append(path.name)
    if len(renders) != 42 or len(set(renders)) != len(renders):
        raise ValueError('Incomplete or duplicated comparison matrix')
    # Local whole-actor pack is only for the opt-in native reader probe.
    # It includes unchanged controls; intentionally kept outside the repository.
    (destination / (form+'-native-candidate.json')).write_text(json.dumps({'version': 1, 'sourceGeometrySha256': identity, 'parts': target}, separators=(',', ':')))
    return {'form': form, 'sourceGeometrySha256': identity, 'modelSha256': world.sha(data),
            'timSha256': world.sha(tim), 'animationSha256': world.sha(animation), 'keyframesSampled': len(keys),
            'comparisonPoseKeys': sampled,
            'charHdManifestSha256': world.sha((charhd/'manifest.json').read_bytes()),
            'charHdAtlasSha256': texture_report['atlasEngineSha256'], 'parts': reports, 'renders': renders,
            'sourceTriangles': sum(len(exporter.surface.triangles([p])) for _, polygons in parts for p in polygons),
            'currentTriangles': sum(len(triangulate(p)['faces']) for p in current),
            'candidateTriangles': sum(len(triangulate(p)['faces']) for p in target)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('files', 'output', 'combat-charhd', 'field-charhd'):
        parser.add_argument('--'+key, type=Path, required=True)
    args = parser.parse_args()
    destination = args.output.resolve()
    if destination.exists() or destination.is_relative_to(ROOT) or destination.is_relative_to(args.files.resolve()):
        parser.error('Use a new local diagnostic output outside the checkout and original extraction')
    destination.mkdir(parents=True)
    (destination/'parts').mkdir()
    records = [('combat', {'model':'characters/dart/models/combat/32', 'tim':'characters/dart/textures/combat', 'animation':'characters/dart/models/combat/0'}, args.combat_charhd),
               ('field', {'model':'SECT/DRGN21.BIN/101/0', 'tim':'SECT/DRGN21.BIN/101/textures/0', 'animation':'SECT/DRGN21.BIN/101/1'}, args.field_charhd)]
    results = [make_form(args.files, record, form, destination, charhd) for form, record, charhd in records]
    receipt = {'status':'experimental-not-approved', 'promotion':'explicit user approval required before main/release',
               'scope':'Offline source-bound shape and CharHD study; no gameplay, GPU or physical Deck evidence',
               'builderSha256':world.sha(Path(__file__).read_bytes()), 'forms':results}
    (destination/'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
    print(json.dumps({r['form']: {k:r[k] for k in ('currentTriangles','candidateTriangles','keyframesSampled')} for r in results}))


if __name__ == '__main__':
    main()
