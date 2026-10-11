"""Authoring-only continuous skinning. AGPL v3. No native animation inputs.

Independent implementation of approximate dual quaternion blending, following
Kavan et al. 2008: https://users.cs.utah.edu/~ladislav/kavan08geometric/
Coordinates are metres, right-handed X right, Y up, Z front.
"""
import numpy as np
from scipy.spatial.transform import Rotation


def quaternion_product(a, b):
    return np.concatenate((a[..., 3:4]*b[..., :3]+b[..., 3:4]*a[..., :3]
                           +np.cross(a[..., :3], b[..., :3]),
                           a[..., 3:4]*b[..., 3:4]-np.sum(a[..., :3]*b[..., :3], axis=-1, keepdims=True)), axis=-1)


def deform(vertices, weights, transforms):
    """Blend proper rigid transforms; equal positions must have equal weights."""
    vertices = np.asarray(vertices, float)
    weights = np.asarray(weights, float)
    transforms = np.asarray(transforms, float)
    if vertices.ndim != 2 or vertices.shape[1] != 3 or weights.ndim != 2:
        raise ValueError('Invalid vertex/weight dimensions')
    if transforms.shape != (weights.shape[1], 4, 4) or len(weights) != len(vertices):
        raise ValueError('Inconsistent skin dimensions')
    if not all(np.isfinite(x).all() for x in (vertices, weights, transforms)):
        raise ValueError('Non-finite skin input')
    if np.any(weights < 0) or not np.allclose(weights.sum(1), 1, atol=1e-7):
        raise ValueError('Weights must be nonnegative and normalized')
    rotations = transforms[:, :3, :3]
    if not np.allclose(transforms[:, 3], [0, 0, 0, 1], atol=1e-8) or not np.allclose(
            rotations.transpose(0, 2, 1)@rotations, np.eye(3), atol=1e-7) or not np.allclose(
                np.linalg.det(rotations), 1, atol=1e-7):
        raise ValueError('Skin requires proper rigid transforms, not scale/shear')
    real = Rotation.from_matrix(rotations).as_quat()
    translation = np.column_stack((transforms[:, :3, 3], np.zeros(len(transforms))))
    dual = .5*quaternion_product(translation, real)
    reference = real[weights.argmax(1)]
    signs = np.where(reference@real.T < 0, -1., 1.)
    blended_real = (weights*signs)@real
    blended_dual = (weights*signs)@dual
    length = np.linalg.norm(blended_real, axis=1, keepdims=True)
    if np.any(length < 1e-8):
        raise ValueError('Degenerate quaternion blend')
    blended_real /= length
    blended_dual /= length
    blended_dual -= blended_real*np.sum(blended_real*blended_dual, axis=1, keepdims=True)
    xyz = blended_real[:, :3]
    rotated = vertices+2*np.cross(xyz, np.cross(xyz, vertices)+blended_real[:, 3:4]*vertices)
    conjugate = blended_real*np.array([-1, -1, -1, 1])
    return rotated+2*quaternion_product(blended_dual, conjugate)[:, :3]


def align_segment(start, end, target_start, target_end):
    a = np.asarray(end)-start
    b = np.asarray(target_end)-target_start
    if min(np.linalg.norm(a), np.linalg.norm(b)) < 1e-8:
        raise ValueError('Zero-length bone')
    a /= np.linalg.norm(a)
    b /= np.linalg.norm(b)
    cross = np.cross(a, b)
    dot = np.clip(a@b, -1, 1)
    if dot > 1-1e-12:
        matrix = np.eye(3)
    elif dot < -1+1e-12:
        axis = np.cross(a, np.eye(3)[np.argmin(np.abs(a))])
        axis /= np.linalg.norm(axis)
        matrix = Rotation.from_rotvec(axis*np.pi).as_matrix()
    else:
        matrix = Rotation.from_rotvec(cross/np.linalg.norm(cross)*np.arccos(dot)).as_matrix()
    transform = np.eye(4)
    transform[:3, :3] = matrix
    transform[:3, 3] = target_start-matrix@start
    return transform


def solve_elbow(shoulder, wrist, upper_length, lower_length, pole):
    """Two-bone IK without silently stretching bones."""
    shoulder, wrist, pole = map(lambda v: np.asarray(v, float), (shoulder, wrist, pole))
    span = wrist-shoulder
    distance = np.linalg.norm(span)
    if not abs(upper_length-lower_length)+1e-6 <= distance <= upper_length+lower_length-1e-6:
        raise ValueError('Unreachable wrist target')
    direction = span/distance
    bend = pole-shoulder
    bend -= direction*(bend@direction)
    if np.linalg.norm(bend) < 1e-8:
        raise ValueError('Ambiguous elbow pole')
    bend /= np.linalg.norm(bend)
    along = (upper_length**2-lower_length**2+distance**2)/(2*distance)
    return shoulder+along*direction+np.sqrt(max(0, upper_length**2-along**2))*bend


def skin_weights(vertices, bones):
    """Dart anatomy masks keep nearby arms from claiming the torso/hips.

    The four largest influences are retained. Weights use position only, so UV
    duplicates cannot independently drift. This is an authoring rig, not native
    face lineage or an alternate texture binding.
    """
    vertices = np.asarray(vertices, float)
    raw = []
    x, y = vertices[:, 0], vertices[:, 1]
    for name, start, end in bones:
        start, end = np.asarray(start), np.asarray(end)
        axis = end-start
        t = np.clip((vertices-start)@axis/(axis@axis), 0, 1)
        distance = np.linalg.norm(vertices-(start+t[:, None]*axis), axis=1)
        value = np.exp(-(distance/.065)**2)
        if name.startswith(('right_', 'left_')):
            side = -1 if name.startswith('right_') else 1
            value *= np.clip((side*x-.025)/.055, 0, 1)
            if any(role in name for role in ('upperarm', 'forearm', 'hand')):
                value *= np.clip((abs(x)-.19)/.055, 0, 1)*np.clip((y-.83)/.08, 0, 1)
            else:
                value *= np.clip((1.00-y)/.10, 0, 1)
        else:
            value *= np.clip((.29-abs(x))/.065, 0, 1)*np.clip((y-.75)/.10, 0, 1)
        raw.append(value)
    weights = np.column_stack(raw)
    nearest = np.argmax(weights, axis=1)
    missing = weights.sum(1) < 1e-30
    weights[missing, nearest[missing]] = 1
    rank = np.argsort(weights, axis=1)[:, :-4]
    np.put_along_axis(weights, rank, 0, axis=1)
    return weights/weights.sum(1, keepdims=True)
