#!/usr/bin/env python3
"""Private original-source collar probe; AGPL v3, see LICENSE.

Writes only authored strips and bounded source receipts. No original meshes,
textures, keyframe arrays or game screenshots are exported. Outputs are offline
development evidence and are never selected by CharHD or its normal build.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
import trimesh
from scipy.spatial.transform import Rotation


ROOT = Path(__file__).resolve().parents[2]
MOTIONS = (0, 1, 5, 10, 11, 14, 29)
JOINTS = {
    'field': ((11, 0, 1, -1., -2.), (12, 1, 1, -1., -2.)),
    # The armored elbow needs a longer span at its sharpest stored bend.
    # These stations are development controls, not accepted costume cut lines.
    'combat': ((12, 0, 0, 125., 115.), (13, 1, 0, 155., 85.)),
}


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


study = module('collar_source_study', Path(__file__).with_name('build-study.py'))
collars = module('collar_surface', Path(__file__).with_name('joint-collar-surface.py'))


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def native_mesh(part):
    vertices, polygons = part
    faces = []
    for references, *_ in polygons:
        for corners in ((0, 1, 2), (1, 3, 2)) if len(references) == 4 else ((0, 1, 2),):
            faces.append([references[index] for index in corners])
    return trimesh.Trimesh(vertices, faces, process=False)


def section_ring(part, axis, station, samples=32):
    """Ray-resample the actual closed native section, retaining its outline.

    Only star-shaped single contours are supported. Multiple contours, missing
    ray hits or competing hits are failures, not permission to invent a collar.
    """
    origin = np.zeros(3); origin[axis] = station
    normal = np.eye(3)[axis]
    section = native_mesh(part).section(plane_origin=origin, plane_normal=normal)
    paths = section.discrete if section is not None else []
    if len(paths) != 1 or np.linalg.norm(paths[0][0] - paths[0][-1]) > 1e-8:
        raise ValueError('Native collar section must be one closed contour')
    axes = [index for index in range(3) if index != axis]
    polygon = paths[0][:-1, axes]
    centre = polygon.mean(0)
    relative = polygon - centre
    next_points = np.roll(relative, -1, axis=0)
    points = []
    for angle in np.linspace(0, 2*np.pi, samples, endpoint=False):
        direction = np.array([np.cos(angle), np.sin(angle)])
        intersections = []
        for first, second in zip(relative, next_points):
            edge = second - first
            system = np.column_stack((direction, -edge))
            if abs(np.linalg.det(system)) <= 1e-10:
                continue
            radius, fraction = np.linalg.solve(system, first)
            if radius > 1e-8 and -1e-8 <= fraction <= 1 + 1e-8:
                intersections.append(radius)
        unique = np.unique(np.round(intersections, 8))
        if len(unique) != 1:
            raise ValueError('Native section is not star-shaped around its authored centre')
        point = origin.copy(); point[axes] = centre + direction * unique[0]
        points.append(point)
    return np.asarray(points)


def pose(key):
    value = np.eye(4)
    value[:3, :3] = study.poses.rotation(*(key[:3] * (2*np.pi/4096)))
    value[:3, 3] = key[3:]
    return value


def intermediate_pose(first, second, fraction):
    """Independent short-arc normalized blend, plus linear translation.

    This samples geometry between stored poses. It does not execute the game
    animator or reproduce its stateful quaternion updates at high refresh rates.
    """
    if not np.isfinite(fraction) or not 0 <= fraction <= 1:
        raise ValueError('Expected a convex interpolation fraction')
    first, second = collars.rigid_pose(first), collars.rigid_pose(second)
    a = Rotation.from_matrix(first[:3, :3]).as_quat()
    b = Rotation.from_matrix(second[:3, :3]).as_quat()
    if a @ b < 0:
        b = -b
    quaternion = (1-fraction)*a + fraction*b
    quaternion /= np.linalg.norm(quaternion)
    result = np.eye(4)
    result[:3, :3] = Rotation.from_quat(quaternion).as_matrix()
    result[:3, 3] = (1-fraction)*first[:3, 3] + fraction*second[:3, 3]
    return result


def metrics(surface, vertices, first, second, first_ring, second_ring):
    rings = vertices.reshape(surface.stations, surface.ring_size, 3)
    error = max(np.linalg.norm(rings[0] - collars.placed(first_ring, first), axis=1).max(),
                np.linalg.norm(rings[-1] - collars.placed(second_ring, second), axis=1).max())
    ring_centres = rings.mean(1)
    vectors = rings - ring_centres[:, None, :]
    area = .5*np.linalg.norm(np.cross(vectors, np.roll(vectors, -1, axis=1)).sum(1), axis=1)
    target = np.linspace(collars.ring_area(first_ring, surface.axis), collars.ring_area(second_ring, surface.axis), surface.stations)
    triangles = vertices[surface.faces]
    cross = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    centres = np.repeat((ring_centres[:-1]*2 + ring_centres[1:])/3, surface.ring_size, axis=0)
    other = np.repeat((ring_centres[:-1] + ring_centres[1:]*2)/3, surface.ring_size, axis=0)
    face_centres = np.stack((centres, other), axis=1).reshape(-1, 3)
    outward = triangles.mean(1) - face_centres
    reversed_faces = int(np.sum(np.einsum('ij,ij->i', cross, outward) <= 1e-8))
    return {'maximumCollarError': float(error),
            'maximumRelativeCrossSectionAreaError': float(np.max(np.abs(area/target - 1))),
            'minimumTriangleArea': float(np.linalg.norm(cross, axis=1).min()/2),
            'radiallyReversedTriangles': reversed_faces}


def run(files, output):
    if output.exists() or output.resolve().is_relative_to(ROOT):
        raise ValueError('Use a new private output outside the checkout')
    audit = json.loads((ROOT / 'integrations/charhd/production/party-reconstruction/source-audit.json').read_text())
    actor = next(actor for actor in audit['characters'] if actor['character'] == 'dart')
    output.mkdir(parents=True)
    report = {'format': 1, 'character': 'dart', 'state': 'offline-joint-experiment-not-runtime-selected',
              'toolSha256': digest(Path(__file__)), 'surfaceToolSha256': digest(Path(__file__).with_name('joint-collar-surface.py')),
              'riggedActor': False, 'newSculptFitted': False, 'collisionValidated': False,
              'nativeGameplayValidated': False, 'steamDeckValidated': False, 'joints': []}
    for record in actor['models']:
        form = record['form']
        model = files / record['source']
        model_data = model.read_bytes()
        if hashlib.sha256(model_data).hexdigest() != record['sourceSha256']:
            raise ValueError('Original model differs from the pinned source audit')
        parts, _, _ = study.exporter.native_parts(model_data)
        animations = [model.with_name(str(number)) for number in MOTIONS] if form == 'combat' else [model.with_name(str(int(model.name) + 1))]
        if digest(animations[0]) != record['animationSha256']:
            raise ValueError('Original bind animation differs from the pinned source audit')
        for first_part, second_part, axis, first_station, second_station in JOINTS[form]:
            first_ring = section_ring(parts[first_part], axis, first_station)
            second_ring = section_ring(parts[second_part], axis, second_station)
            surface = collars.JointCollarSurface(first_ring, second_ring, axis)
            joint_name = f'{form}-elbow-{first_part}-{second_part}'
            joint_directory = output / joint_name
            joint_directory.mkdir()
            row = {'name': joint_name, 'form': form, 'source': record['source'],
                   'modelSha256': record['sourceSha256'], 'sourceParts': [first_part, second_part],
                   'localAxis': axis, 'sectionStations': [first_station, second_station],
                   'ringVertices': surface.ring_size, 'longitudinalStations': surface.stations,
                   'triangles': len(surface.faces), 'expectedOpenCollarEdges': surface.ring_size*2,
                   'sourceDesignDetailAccepted': False, 'samples': [], 'intermediateSamples': [], 'artifacts': []}
            for animation in animations:
                animation_data = animation.read_bytes()
                animation_sha = hashlib.sha256(animation_data).hexdigest()
                if animation == animations[0] and animation_sha != record['animationSha256']:
                    raise ValueError('Original bind animation changed during the probe')
                keys = study.poses.read_keyframes(animation_data, len(parts))
                for frame, key in enumerate(keys):
                    first, second = pose(key[first_part]), pose(key[second_part])
                    vertices = surface.deform(first, second)
                    check = metrics(surface, vertices, first, second, first_ring, second_ring)
                    row['samples'].append({'animation': animation.name, 'animationSha256': animation_sha,
                                           'storedKeyframe': frame, **check})
                    if frame in (0, 1, len(keys)//2, len(keys)-1):
                        mesh = trimesh.Trimesh(vertices, surface.faces, process=False)
                        mesh.visual.vertex_colors = np.tile([192, 165, 124, 255], (len(vertices), 1))
                        mesh.visual.vertex_colors[:surface.ring_size] = [70, 170, 218, 255]
                        mesh.visual.vertex_colors[-surface.ring_size:] = [70, 170, 218, 255]
                        path = joint_directory / f'motion-{animation.name}-key-{frame}.glb'
                        mesh.export(path)
                        reloaded = trimesh.load(path, force='mesh', process=False)
                        export_error = np.linalg.norm(reloaded.vertices - vertices, axis=1).max()
                        if export_error > 1e-4 or not np.array_equal(reloaded.faces, surface.faces):
                            raise ValueError('Custom GLB export changed the authored strip beyond its budget')
                        row['artifacts'].append({'path': str(path.relative_to(output)), 'sha256': digest(path),
                                                 'maximumGlbRoundtripPositionError': float(export_error)})
                for frame in range(len(keys)-1):
                    for fraction in (.25, .5, .75):
                        first = intermediate_pose(pose(keys[frame, first_part]), pose(keys[frame+1, first_part]), fraction)
                        second = intermediate_pose(pose(keys[frame, second_part]), pose(keys[frame+1, second_part]), fraction)
                        vertices = surface.deform(first, second)
                        check = metrics(surface, vertices, first, second, first_ring, second_ring)
                        row['intermediateSamples'].append({'animation': animation.name, 'animationSha256': animation_sha,
                                                           'afterStoredKeyframe': frame, 'fraction': fraction, **check})
            row['numericalGatePassed'] = all(sample['maximumCollarError'] <= 1e-8
                                             and sample['maximumRelativeCrossSectionAreaError'] <= 1e-8
                                             and sample['minimumTriangleArea'] > 1e-8
                                             and sample['radiallyReversedTriangles'] == 0
                                             for sample in row['samples'] + row['intermediateSamples'])
            report['joints'].append(row)
            print(json.dumps({'joint': joint_name, 'storedPoses': len(row['samples']),
                              'intermediatePoses': len(row['intermediateSamples']), 'numericalGatePassed': row['numericalGatePassed']}), flush=True)
    report['interpolationScope'] = ('Independent normalized short-arc quaternion blends and linear translation at 0.25, 0.5 and 0.75 '
                                    'between stored poses. No native animator execution or stateful high-refresh interpolation validation.')
    report['scope'] = ('Authored collar strips driven by selected original stored poses. Endpoint and cross-section checks '
                       'do not prove collision freedom, complete actor continuity, native renderer compatibility or gameplay.')
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    result = run(arguments.files, arguments.output)
    if not all(joint['numericalGatePassed'] for joint in result['joints']):
        raise SystemExit('Joint numerical gate failed; see the retained report')
