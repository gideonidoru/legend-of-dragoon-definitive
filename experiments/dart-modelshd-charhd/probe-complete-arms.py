#!/usr/bin/env python3
"""Private Dart complete-arm topology/motion probe. AGPL v3; see LICENSE.

Original actor controls remain private. Modified arm-only artifacts and bounded
receipts may be reviewed for public authoring; none are selected at runtime.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
import trimesh


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


probe = module('arm_source_probe', 'probe-native-collars.py')
arms = module('complete_arm_surface', 'complete-arm-surface.py')
crossings = module('complete_arm_crossings', 'surface-crossing-probe.py')


def matched_rings(upper, lower, axis, first_station, second_station):
    """Keep every native cut corner and add shared angular samples.

    The same ray angle orders both collars. Retaining corners is essential: a
    uniform ring alone approximates the source polygon and cannot weld its cut.
    """
    axes = [index for index in range(3) if index != axis]
    contours, centres = [], []
    for mesh, station in ((upper, first_station), (lower, second_station)):
        section = mesh.section(plane_origin=np.eye(3)[axis]*station, plane_normal=np.eye(3)[axis])
        loops = section.discrete if section is not None else []
        if len(loops) != 1 or np.linalg.norm(loops[0][0]-loops[0][-1]) > 1e-8:
            raise ValueError('Expected one closed native arm cut')
        contour = loops[0][:-1, axes]
        contours.append(contour)
        centres.append(contour.mean(0))
    angles = list(np.linspace(0, 2*np.pi, 32, endpoint=False))
    for contour, centre in zip(contours, centres):
        d = contour-centre
        angles.extend(np.mod(np.arctan2(d[:, 1], d[:, 0]), 2*np.pi))
    angles = np.array(sorted(angles))
    angles = angles[np.r_[True, np.diff(angles) > 1e-7]]
    if angles[-1]-angles[0] > 2*np.pi-1e-7:
        angles = angles[:-1]
    rings = []
    for contour, centre, station in zip(contours, centres, (first_station, second_station)):
        relative = contour-centre
        ring = []
        for angle in angles:
            direction = np.array([np.cos(angle), np.sin(angle)])
            hits = []
            for a, b in zip(relative, np.roll(relative, -1, axis=0)):
                system = np.column_stack((direction, -(b-a)))
                if abs(np.linalg.det(system)) < 1e-10:
                    continue
                radius, fraction = np.linalg.solve(system, a)
                if radius > 1e-8 and -1e-8 <= fraction <= 1+1e-8:
                    hits.append(radius)
            unique = np.unique(np.round(hits, 8))
            if len(unique) != 1:
                raise ValueError('Arm collar is not a single star-shaped outline')
            point = np.eye(3)[axis]*station
            point[axes] = centre+direction*unique[0]
            ring.append(point)
        rings.append(np.asarray(ring))
    return rings


def topology(faces):
    counts = Counter(tuple(sorted((int(a), int(b)))) for face in faces for a, b in zip(face, np.roll(face, -1)))
    return {'boundaryEdges': sum(v == 1 for v in counts.values()),
            'nonmanifoldEdges': sum(v > 2 for v in counts.values())}


def lineage_error(surface, meshes):
    errors = []
    for vertices, faces, lineage, original in ((surface.upper, surface.faces[surface.face_regions == 0], surface.upper_lineage, meshes[0]),
                                               (surface.lower, surface.faces[surface.face_regions == 1]-len(surface.upper), surface.lower_lineage, meshes[1])):
        for refs, row in zip(faces, lineage):
            rebuilt = np.asarray(row['weights'])@original.vertices[original.faces[row['sourceTriangle']]]
            errors.extend(np.linalg.norm(vertices[refs]-rebuilt, axis=1))
    return float(max(errors))


def run(files, output):
    if output.exists() or output.resolve().is_relative_to(probe.ROOT):
        raise ValueError('Use a new private output outside the checkout')
    audit = json.loads((probe.ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json').read_text())
    actor = next(a for a in audit['characters'] if a['character'] == 'dart')
    output.mkdir(parents=True)
    report = {'state': 'complete-arm-offline-experiment-not-runtime-selected', 'sourceToolSha256': probe.digest(Path(__file__)),
              'surfaceToolSha256': probe.digest(Path(__file__).with_name('complete-arm-surface.py')),
              'collarToolSha256': probe.digest(Path(__file__).with_name('joint-collar-surface.py')),
              'crossingToolSha256': probe.digest(Path(__file__).with_name('surface-crossing-probe.py')),
              'sourceDataPublished': False, 'textureBindingValidated': False, 'collisionValidated': False,
              'gameplayValidated': False, 'steamDeckValidated': False, 'arms': []}
    for record in actor['models']:
        path = files/record['source']; data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != record['sourceSha256']:
            raise ValueError('Original model differs from source audit')
        parts, _, _ = probe.study.exporter.native_parts(data)
        form = record['form']
        animations = [path.with_name(str(n)) for n in probe.MOTIONS] if form == 'combat' else [path.with_name('1')]
        for first, second, axis, first_station, second_station in probe.JOINTS[form]:
            meshes = [probe.native_mesh(parts[index]) for index in (first, second)]
            rings = matched_rings(*meshes, axis, first_station, second_station)
            surface = arms.CompleteArmSurface(*[(m.vertices, m.faces) for m in meshes], *rings, axis)
            name = f'{form}-arm-{first}-{second}'
            directory = output/name; directory.mkdir()
            row = {'name': name, 'source': record['source'], 'sourceSha256': record['sourceSha256'],
                   'sourceParts': [first, second], 'localAxis': axis, 'sectionStations': [first_station, second_station],
                   'ringVertices': surface.collar.ring_size, 'triangles': len(surface.faces),
                   'cutEdgesWelded': True, 'retainedMaterialLineageMaximumError': lineage_error(surface, meshes),
                   **topology(surface.faces), 'samples': [], 'artifacts': []}
            for animation in animations:
                animation_data = animation.read_bytes()
                sha = hashlib.sha256(animation_data).hexdigest()
                if animation == animations[0] and sha != record['animationSha256']:
                    raise ValueError('Original bind animation differs from source audit')
                keys = probe.study.poses.read_keyframes(animation_data, len(parts))
                samples = [(frame, None, probe.pose(key[first]), probe.pose(key[second])) for frame, key in enumerate(keys)]
                samples.extend((frame, fraction,
                                probe.intermediate_pose(probe.pose(keys[frame, first]), probe.pose(keys[frame+1, first]), fraction),
                                probe.intermediate_pose(probe.pose(keys[frame, second]), probe.pose(keys[frame+1, second]), fraction))
                               for frame in range(len(keys)-1) for fraction in (.25, .5, .75))
                for frame, fraction, upper_pose, lower_pose in samples:
                    vertices = surface.deform(upper_pose, lower_pose)
                    check = probe.metrics(surface.collar, vertices[surface.collar_map], upper_pose, lower_pose, *rings)
                    triangles = vertices[surface.faces]
                    check['properNoncoplanarCrossingPairs'] = len(crossings.proper_crossing_pairs(vertices, surface.faces[surface.face_regions == 2], surface.faces[surface.face_regions != 2]))
                    check['completeArmMinimumTriangleArea'] = float(np.linalg.norm(np.cross(triangles[:, 1]-triangles[:, 0], triangles[:, 2]-triangles[:, 0]), axis=1).min()/2)
                    row['samples'].append({'animation': animation.name, 'animationSha256': sha, 'storedKeyframe': frame, 'fraction': fraction, **check})
                    if fraction is None and frame in (0, len(keys)//2) and animation.name in ('0', '1', '5', '14'):
                        mesh = trimesh.Trimesh(vertices, surface.faces, process=False)
                        colours = np.tile([175, 154, 129, 255], (len(vertices), 1))
                        colours[surface.rigid_count:] = [206, 177, 132, 255]
                        mesh.visual.vertex_colors = colours
                        artifact = directory/f'motion-{animation.name}-key-{frame}.glb'; mesh.export(artifact)
                        reloaded = trimesh.load(artifact, force='mesh', process=False)
                        if not np.array_equal(reloaded.faces, surface.faces):
                            raise ValueError('GLB export changed the complete-arm faces')
                        error = float(np.linalg.norm(reloaded.vertices-vertices, axis=1).max())
                        row['artifacts'].append({'path': str(artifact.relative_to(output)), 'sha256': probe.digest(artifact), 'maximumGlbRoundtripPositionError': error})
            row['numericalGatePassed'] = (row['nonmanifoldEdges'] == 0 and row['retainedMaterialLineageMaximumError'] < 1e-6
                                         and all(s['maximumCollarError'] < 1e-6 and s['maximumRelativeCrossSectionAreaError'] < 1e-8
                                                 and s['radiallyReversedTriangles'] == 0 and s['completeArmMinimumTriangleArea'] > 1e-10 for s in row['samples'])
                                         and all(s['properNoncoplanarCrossingPairs'] == 0 for s in row['samples'])
                                         and all(a['maximumGlbRoundtripPositionError'] < 1e-4 for a in row['artifacts']))
            report['arms'].append(row)
            print(json.dumps({k: row[k] for k in ('name', 'triangles', 'ringVertices', 'boundaryEdges', 'nonmanifoldEdges', 'numericalGatePassed')}), flush=True)
    report['scope'] = ('Complete modified upper/lower arm surfaces joined at shared cut vertices; original detached trim and source attachment borders retained. '
                       'Original hands/weapon/head are unmodified. These arms retain low-poly source shapes outside the elbow; this is a continuity prototype, not an AA finish or rigged actor. '
                       'Selected stored poses and independent NLERP between-key samples only; no native animator or comprehensive collision proof. The proper-crossing probe excludes shared vertices, coplanar overlaps and boundary contacts. Clipped source triangle lineage excludes the newly authored elbow material.')
    (output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = run(args.files, args.output)
    if not all(row['numericalGatePassed'] for row in result['arms']):
        raise SystemExit('Complete-arm numerical gate failed; retained diagnostic report')
