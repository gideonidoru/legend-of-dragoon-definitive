#!/usr/bin/env python3
"""Build a complete inspectable Dart authoring actor. AGPL v3.

Only custom reconstruction and the accepted public CharHD head are read. Studio
poses are authored controls, not copied native game animation. No runtime select.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import numpy as np
import trimesh
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('continuous_rig', Path(__file__).with_name('continuous-rig.py'))
rig = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rig)


def clip_body(mesh, ceiling):
    vertices, uv, faces, lookup = [], [], [], {}
    def add(point, texcoord):
        key = tuple(np.round(np.r_[point, texcoord], 7))
        if key not in lookup:
            lookup[key] = len(vertices)
            vertices.append(point)
            uv.append(texcoord)
        return lookup[key]
    for face in mesh.faces:
        corners = [(mesh.vertices[i], mesh.visual.uv[i]) for i in face]
        polygon = []
        for a, b in zip(corners, corners[1:]+corners[:1]):
            def distance(point):
                # Preserve the high outer shoulder/pauldron contours. A global
                # horizontal head cut would amputate them along with the head.
                fraction = np.clip((abs(point[0])-.085)/.10, 0, 1)
                shoulder_height = .11*fraction*fraction*(3-2*fraction)
                return point[1]-ceiling-shoulder_height
            da, db = distance(a[0]), distance(b[0])
            inside_a, inside_b = da <= 0, db <= 0
            if inside_a:
                polygon.append(a)
            if inside_a != inside_b:
                fraction = da/(da-db)
                polygon.append(tuple(x+(z-x)*fraction for x, z in zip(a, b)))
        ids = [add(*corner) for corner in polygon]
        for i in range(1, len(ids)-1):
            face_ids = [ids[0], ids[i], ids[i+1]]
            p = np.asarray([vertices[index] for index in face_ids])
            if np.linalg.norm(np.cross(p[1]-p[0], p[2]-p[0])) > 1e-10:
                faces.append(face_ids)
    result = trimesh.Trimesh(vertices, faces, process=False)
    result.visual = trimesh.visual.TextureVisuals(uv=uv, material=mesh.visual.material)
    return result


def body_and_head(source):
    body = next(iter(trimesh.load(source, force='scene', process=False).geometry.values()))
    basis = np.array([[0, 1, 0], [0, 0, 1], [1, 0, 0]])
    transformed = np.asarray(body.vertices)@basis.T
    height = np.ptp(transformed[:, 1])
    transformed[:, 1] -= transformed[:, 1].min()
    transformed[:, 2] -= .075
    body.vertices = transformed*(1.8/height)
    body = clip_body(body, 1.505)
    # Smooth only the inferred rear/side. Preserve the face-facing projected art
    # and welded positions across atlas seams; this is geometry, not a paint edit.
    unique, inverse = np.unique(np.round(body.vertices, 6), axis=0, return_inverse=True)
    welded = trimesh.Trimesh(unique, inverse[body.faces], process=False)
    smoothed = welded.copy()
    trimesh.smoothing.filter_taubin(smoothed, lamb=.35, nu=.36, iterations=18)
    delta = smoothed.vertices-unique
    length = np.linalg.norm(delta, axis=1)
    delta *= np.minimum(1, .008/np.maximum(length, 1e-12))[:, None]
    rear = np.clip((.035-unique[:, 2])/.045, 0, 1)
    # Never alter the neck trim or floor anchors.
    rear *= np.clip((1.5-unique[:, 1])/.035, 0, 1)*np.clip(unique[:, 1]/.03, 0, 1)
    body.vertices = unique[inverse]+(delta*rear[:, None])[inverse]
    base = ROOT/'integrations/charhd/runtime-assets/charhd/characters/dart'
    packed = base/'parts/b84e13a11adbd3419e1e4c5b810c8bd797f9dc68af5d41fe3c4736ce5c4f584b.json'
    part = json.loads(packed.read_text())['parts'][0]
    uv = json.loads((base/'field-head-uv.json').read_text())['uvs']
    head_basis = np.array([.0175, -.0175, -.0175])
    neutral_rotation = rig.Rotation.from_euler('y', 20, degrees=True).as_matrix()
    head_points = (np.asarray(part['vertices'])*head_basis)@neutral_rotation.T
    # Counter-turn the head-only studio fit, then center the actual neck base
    # rather than fitting the hair bounds to the body.
    neck_slice = head_points[np.isclose(head_points[:, 1], head_points[:, 1].min(), atol=1e-6)]
    neck_center = (neck_slice[:, [0, 2]].min(0)+neck_slice[:, [0, 2]].max(0))*.5
    head_points += [-neck_center[0], 1.485+.0175*7.5, -.015-neck_center[1]]
    # Native source packets use the opposite triangle winding from glTF.
    head_faces = np.asarray([f['vertices'] for f in part['faces']])[:, ::-1]
    head_normals = (np.asarray(part['normals'])*np.sign(head_basis))@neutral_rotation.T
    head = trimesh.Trimesh(head_points, head_faces, vertex_normals=head_normals, process=False)
    material = trimesh.visual.material.PBRMaterial(baseColorTexture=Image.open(base/'head-v1.png'), metallicFactor=0, roughnessFactor=.82)
    head.visual = trimesh.visual.TextureVisuals(uv=np.asarray(uv)*[1, -1]+[0, 1], material=material)
    return body, head


def skeleton():
    bones = [('hips', [0, .88, 0], [0, 1.03, 0]),
             ('spine', [0, 1.03, 0], [0, 1.22, 0]),
             ('chest', [0, 1.22, 0], [0, 1.49, 0])]
    for side, sign in [('right', -1), ('left', 1)]:
        points = [[sign*.215, 1.40, 0], [sign*.405, 1.205, .008], [sign*.555, 1.03, .02], [sign*.625, .956, .028]]
        bones.extend((side+'_'+name, a, b) for name, a, b in zip(['upperarm', 'forearm', 'hand'], points, points[1:]))
        points = [[sign*.11, .90, 0], [sign*.12, .60, .01], [sign*.13, .215, 0], [sign*.13, .075, .10]]
        bones.extend((side+'_'+name, a, b) for name, a, b in zip(['thigh', 'shin', 'boot'], points, points[1:]))
    return [(name, np.array(a, float), np.array(b, float)) for name, a, b in bones]


def pose_transforms(bones, pose):
    transforms = [np.eye(4) for _ in bones]
    targets = {
        'relaxed': {'right': [-.23, 1.01, .08], 'left': [.25, 1.015, .10]},
        'ready': {'right': [-.10, 1.16, .26], 'left': [0, 1.065, .29]},
        'guard': {'right': [-.10, 1.25, .24], 'left': [.0, 1.145, .29]},
        'stride': {'right': [-.22, 1.01, .03], 'left': [.25, 1.05, .16]},
    }
    hands = {}
    for side, sign in [('right', -1), ('left', 1)]:
        index = next(i for i, b in enumerate(bones) if b[0] == side+'_upperarm')
        upper, lower, hand = bones[index:index+3]
        if pose == 'neutral':
            hands[side] = (hand[1]+hand[2])*.5
            continue
        wrist = np.array(targets[pose][side])
        elbow = rig.solve_elbow(upper[1], wrist, np.linalg.norm(upper[2]-upper[1]),
                               np.linalg.norm(lower[2]-lower[1]), [sign*.5, 1.10, -.04])
        transforms[index] = rig.align_segment(upper[1], upper[2], upper[1], elbow)
        transforms[index+1] = rig.align_segment(lower[1], lower[2], elbow, wrist)
        # Fists stay upright around the sword handle. Relaxed hands point down.
        direction = np.array([0, -1, 0]) if pose in ('relaxed', 'stride') else np.array([-.15, .0, .98])
        end = wrist+direction*np.linalg.norm(hand[2]-hand[1])
        transforms[index+2] = rig.align_segment(hand[1], hand[2], wrist, end)
        hands[side] = (wrist+end)*.5
        if pose == 'stride':
            leg_index = next(i for i, b in enumerate(bones) if b[0] == side+'_thigh')
            for j in range(leg_index, leg_index+3):
                angle = .12*sign
                rotation = rig.Rotation.from_rotvec([angle, 0, 0]).as_matrix()
                origin = bones[leg_index][1]
                transforms[j][:3, :3] = rotation
                transforms[j][:3, 3] = origin-rotation@origin
    return np.array(transforms), hands


def sword(hand, pose):
    """Custom faceted straight blade, ruby guard and wrapped grip; no game mesh."""
    from scipy.spatial.transform import Rotation
    pieces = []
    ring = np.array([[-.037, 0, 0], [0, 0, .009], [.037, 0, 0], [0, 0, -.009]])
    vertices = np.vstack((ring, ring*[.64, 1, 1]+[0, .79, 0], [0, .96, 0]))
    faces = [[0, 3, 2], [0, 2, 1]]
    for i in range(4):
        j = (i+1)%4
        faces += [[i, j, i+4], [j, j+4, i+4], [i+4, j+4, 8]]
    blade = trimesh.Trimesh(vertices, faces, process=False)
    blade.fix_normals()
    blade.visual = trimesh.visual.TextureVisuals(uv=np.zeros((len(blade.vertices), 2)), material=trimesh.visual.material.PBRMaterial(baseColorFactor=[.70, .76, .82, 1], metallicFactor=.85, roughnessFactor=.24))
    pieces.append(('blade', blade))
    outline = np.array([[-.075, .035], [-.052, -.055], [0, -.078], [.052, -.055], [.075, .035], [.042, .006], [0, .055], [-.042, .006]])
    vertices = np.vstack([np.column_stack((outline, np.full(len(outline), z))) for z in [-.016, .016]])
    count = len(outline)
    # Polygon is concave, so use explicit artist-authored cap triangles.
    cap = [[0, 1, 7], [1, 2, 7], [2, 6, 7], [2, 3, 6], [3, 5, 6], [3, 4, 5]]
    faces = cap+[list(np.array(face)+count) for face in cap]
    for i in range(count):
        j = (i+1)%count
        faces += [[i, j, i+count], [j, j+count, i+count]]
    guard = trimesh.Trimesh(vertices, faces, process=False)
    guard.fix_normals()
    guard.visual = trimesh.visual.TextureVisuals(uv=np.zeros((len(vertices), 2)), material=trimesh.visual.material.PBRMaterial(baseColorFactor=[.52, .022, .034, 1], metallicFactor=.6, roughnessFactor=.28))
    pieces.append(('guard', guard))
    for name, extents, center, color in [
            ('grip', [.026, .19, .026], [0, -.11, 0], [.23, .045, .025, 1]),
            ('pommel', [.046, .035, .039], [0, -.222, 0], [.58, .027, .040, 1])]:
        mesh = trimesh.creation.box(extents)
        mesh.apply_translation(center)
        mesh.visual = trimesh.visual.TextureVisuals(uv=np.zeros((len(mesh.vertices), 2)), material=trimesh.visual.material.PBRMaterial(baseColorFactor=color, metallicFactor=.5 if name != 'grip' else 0, roughnessFactor=.36))
        pieces.append((name, mesh))
    for i in range(9):
        wrap = trimesh.creation.cylinder(radius=.019, height=.006, sections=12)
        wrap.apply_transform(trimesh.transformations.rotation_matrix(np.pi/2, [1, 0, 0]))
        wrap.apply_translation([0, -.035-i*.019, 0])
        wrap.visual = trimesh.visual.TextureVisuals(uv=np.zeros((len(wrap.vertices), 2)), material=trimesh.visual.material.PBRMaterial(baseColorFactor=[.34, .055, .034, 1], metallicFactor=0, roughnessFactor=.7))
        pieces.append(('grip-wrap-'+str(i), wrap))
    direction = np.array([-.37, .91, .16]) if pose in ('ready', 'guard') else np.array([-.15, 1, .0])
    rotation = rig.align_segment(np.zeros(3), np.array([0., 1, 0]), np.zeros(3), direction)[:3, :3]
    for _, mesh in pieces:
        mesh.vertices = (mesh.vertices+[0, .11, 0])@rotation.T+hand
    return pieces


def material_regions(mesh):
    """Split render primitives by paint color while retaining the skin topology.

    This is an authoring material assignment. All regions share the same custom
    atlas; it makes no claim of original-face/alternate-mod UV compatibility.
    """
    pixels = np.asarray(mesh.visual.material.baseColorTexture.convert('RGB'))
    uv = np.asarray(mesh.visual.uv)[mesh.faces].mean(1)
    x = np.clip((uv[:, 0]*pixels.shape[1]).astype(int), 0, pixels.shape[1]-1)
    y = np.clip(((1-uv[:, 1])*pixels.shape[0]).astype(int), 0, pixels.shape[0]-1)
    rgb = pixels[y, x].astype(float)
    metal = (rgb[:, 0]>rgb[:, 1]*1.9)&(rgb[:, 0]>rgb[:, 2]*1.9)&(rgb[:, 0]>45)
    for name, mask, metallic, roughness in [('red-armor', metal, .35, .42), ('cloth-leather-skin', ~metal, 0, .88)]:
        faces = mesh.faces[mask]
        used, inverse = np.unique(faces, return_inverse=True)
        region = trimesh.Trimesh(mesh.vertices[used], inverse.reshape(-1, 3), vertex_normals=mesh.vertex_normals[used], process=False)
        material = trimesh.visual.material.PBRMaterial(baseColorTexture=mesh.visual.material.baseColorTexture, metallicFactor=metallic, roughnessFactor=roughness)
        region.visual = trimesh.visual.TextureVisuals(uv=np.asarray(mesh.visual.uv)[used], material=material)
        yield name, region


def build(source, output, previous=None):
    output.mkdir(parents=True, exist_ok=False)
    body, head = body_and_head(source)
    bones = skeleton()
    weights = rig.skin_weights(body.vertices, bones)
    # Shoulder plates follow the chest; their lower edge feathers into the arm.
    # Position-only controls preserve UV seam welds exactly.
    shoulder = np.clip((body.vertices[:, 1]-1.315)/.06, 0, 1)*np.clip((np.abs(body.vertices[:, 0])-.18)/.035, 0, 1)
    weights *= 1-shoulder[:, None]
    weights[:, 2] += shoulder
    records = []
    for pose in ['neutral', 'relaxed', 'ready', 'guard', 'stride']:
        transforms, hands = pose_transforms(bones, pose)
        actor = trimesh.Scene()
        posed = body.copy()
        posed.vertices = rig.deform(body.vertices, weights, transforms)
        # Share normals across position welds without welding authored UV seams.
        unique, inverse = np.unique(np.round(posed.vertices, 6), axis=0, return_inverse=True)
        welded = trimesh.Trimesh(unique, inverse[posed.faces], process=False)
        posed.vertex_normals = welded.vertex_normals[inverse]
        for name, region in material_regions(posed):
            actor.add_geometry(region, geom_name=name)
        actor.add_geometry(head.copy(), geom_name='accepted-charhd-head')
        if pose != 'neutral':
            for name, mesh in sword(hands['right'], pose):
                actor.add_geometry(mesh, geom_name='sword-'+name)
        path = output/(pose+'.glb')
        path.write_bytes(trimesh.exchange.gltf.export_glb(actor, include_normals=True))
        read = trimesh.load(path, force='scene', process=False)
        if set(read.geometry) != set(actor.geometry):
            raise ValueError('GLB primitive round trip failed')
        for name in actor.geometry:
            if not np.array_equal(actor.geometry[name].faces, read.geometry[name].faces) or not np.allclose(actor.geometry[name].vertices, read.geometry[name].vertices, atol=1e-6):
                raise ValueError('GLB changed topology/positions')
        records.append({'pose': pose, 'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'triangles': sum(len(m.faces) for m in actor.geometry.values()), 'nativeAnimation': False})
    np.savez_compressed(output/'authoring-skin.npz', vertices=body.vertices, faces=body.faces,
                        uv=body.visual.uv, weights=weights,
                        starts=np.array([b[1] for b in bones]), ends=np.array([b[2] for b in bones]))
    body.visual.material.baseColorTexture.save(output/'body-basecolour.png')
    if previous:
        earlier = next(iter(trimesh.load(previous, force='scene', process=False).geometry.values()))
        points = np.asarray(earlier.vertices)@np.array([[0, 1, 0], [0, 0, 1], [1, 0, 0]]).T
        height = np.ptp(points[:, 1])
        points[:, 1] -= points[:, 1].min()
        points[:, 2] -= .075
        earlier.vertices = points*(1.8/height)
        (output/'before.glb').write_bytes(trimesh.exchange.gltf.export_glb(trimesh.Scene(earlier), include_normals=True))
    report = {'status': 'complete-actor-review-candidate-not-runtime-selected', 'nativeAnimation': False,
              'skinning': 'dual-quaternion, authoring only', 'alternateTextureCompatibility': 'not yet bound; accepted runtime is unchanged',
              'head': 'accepted public CharHD field head, fitted into authored body without modifying selected bytes',
              'headTopMetres': float(head.bounds[1, 1]), 'bodyTriangles': len(body.faces), 'headTriangles': len(head.faces),
              'bones': [name for name, _, _ in bones], 'poses': records,
              'inputSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'earlierSculptSha256': hashlib.sha256(previous.read_bytes()).hexdigest() if previous else None,
              'builderSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'skinningSha256': hashlib.sha256(Path(__file__).with_name('continuous-rig.py').read_bytes()).hexdigest()}
    (output/'build-receipt.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mesh', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--previous', type=Path, help='Public earlier static sculpt for the comparison viewer')
    args = parser.parse_args()
    build(args.mesh, args.output, args.previous)
