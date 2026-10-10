#!/usr/bin/env python3
"""Private, unchanged rigid-part GLB references, AGPL v3; see LICENSE.
Requires pinned Pillow/NumPy. Not a replacement mesh or a native animation export.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import platform
import struct
import numpy as np
import PIL

import importlib.util


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


poses = load('reference_poses', 'inspect-model-poses.py')
packing = load('reference_packing', 'pack-model-materials.py')
METERS_PER_SOURCE_UNIT = 0.01  # Authoring convenience, not a measured character height.


def quaternion(rx, ry, rz):
    """XYZW for Rz * Ry * Rx; local coordinates remain in the source basis."""
    cx, sx = math.cos(rx/2), math.sin(rx/2)
    cy, sy = math.cos(ry/2), math.sin(ry/2)
    cz, sz = math.cos(rz/2), math.sin(rz/2)
    return [cz*cy*sx-sz*sy*cx, cz*sy*cx+sz*cy*sx,
            sz*cy*cx-cz*sy*sx, cz*cy*cx+sz*sy*sx]


class Glb:
    def __init__(self):
        self.binary = bytearray()
        self.document = {'asset': {'version': '2.0', 'generator': 'Definitive source-reference export 1'},
                         'bufferViews': [], 'accessors': [], 'buffers': []}

    def view(self, data, target=None):
        self.binary.extend(bytes(-len(self.binary) % 4))
        view = {'buffer': 0, 'byteOffset': len(self.binary), 'byteLength': len(data)}
        if target is not None:
            view['target'] = target
        self.binary.extend(data)
        self.document['bufferViews'].append(view)
        return len(self.document['bufferViews'])-1

    def accessor(self, values, kind, bounds=False, indices=False, vertex=False):
        values = np.asarray(values, dtype='<u4' if indices else '<f4')
        if not values.size or not np.isfinite(values).all():
            raise ValueError('Empty or nonfinite reference attribute')
        item = {'bufferView': self.view(values.tobytes(), 34963 if indices else 34962 if vertex else None),
                'componentType': 5125 if indices else 5126, 'count': len(values), 'type': kind}
        if bounds:
            item['min'] = np.min(values, axis=0).reshape(-1).tolist()
            item['max'] = np.max(values, axis=0).reshape(-1).tolist()
        self.document['accessors'].append(item)
        return len(self.document['accessors'])-1

    def finish(self):
        self.document['buffers'] = [{'byteLength': len(self.binary)}]
        encoded = json.dumps(self.document, separators=(',', ':'), allow_nan=False).encode()
        encoded += b' ' * (-len(encoded) % 4)
        binary = bytes(self.binary) + bytes(-len(self.binary) % 4)
        length = 12+8+len(encoded)+8+len(binary)
        if length > 64*1024*1024:
            raise ValueError('Reference exceeds the 64 MiB GLB limit')
        return (struct.pack('<5I', 0x46546C67, 2, length, len(encoded), 0x4E4F534A)
                + encoded + struct.pack('<2I', len(binary), 0x004E4942) + binary)


def export(parts, keyframes, atlas, material_map, clut_width):
    """Keep original vertices/pivots; duplicate only face corners for material seams."""
    if not 1 <= len(parts) <= 256 or keyframes.shape != (len(keyframes), len(parts), 6):
        raise ValueError('Reference requires matching rigid-part keyframes')
    if not 1 <= len(keyframes) <= 256 or not np.isfinite(keyframes).all():
        raise ValueError('Invalid bounded keyframes')
    glb = Glb()
    doc = glb.document
    doc.update({'extensionsUsed': ['KHR_materials_unlit'], 'extensionsRequired': ['KHR_materials_unlit'],
                'samplers': [{'magFilter': 9728, 'minFilter': 9728, 'wrapS': 33071, 'wrapT': 33071}],
                'textures': [{'sampler': 0, 'source': 0}], 'meshes': [], 'materials': [], 'nodes': [],
                'scene': 0, 'scenes': [{'name': 'Original rigid-part reference', 'nodes': [0]}]})
    png = io.BytesIO()
    packing.rgba_preview(atlas).save(png, format='PNG')
    doc['images'] = [{'bufferView': glb.view(png.getvalue()), 'mimeType': 'image/png',
                      'name': 'Original palette atlas; conventional visibility alpha'}]
    doc['nodes'].append({'name': 'Reference basis and authoring scale', 'rotation': [1, 0, 0, 0],
                         'scale': [METERS_PER_SOURCE_UNIT]*3, 'children': list(range(1, len(parts)+1)),
                         'extras': {'basis': 'root maps source (x,y,z) to (x,-y,-z)',
                                    'scalePurpose': 'authoring convenience; physical size unknown'}})
    material_indices = {}
    inventory = []
    for part_index, (vertices, polygons) in enumerate(parts):
        if not len(vertices) or not np.isfinite(vertices).all():
            raise ValueError('Reference part has empty or nonfinite vertices')
        groups = {}
        for face_index, (references, uv, clut, colors) in enumerate(polygons):
            palette = None if uv is None else ((clut >> 6) & 15)*(clut_width//16)+(clut & 3)
            if palette is not None and palette not in material_map:
                raise ValueError('Reference material map is incomplete')
            if len(references) not in (3, 4) or any(ref < 0 or ref >= len(vertices) for ref in references):
                raise ValueError('Invalid source face references')
            group = groups.setdefault(palette, {'positions': [], 'uvs': [], 'colors': [], 'indices': [],
                                                 'sourceFaces': [], 'sourceVertices': [], 'sourceColors': []})
            first = len(group['positions'])
            group['positions'].extend(vertices[references].tolist())
            group['sourceVertices'].extend(references)
            group['sourceFaces'].append(face_index)
            group['sourceColors'].extend(colors.tolist())
            group['colors'].extend(np.clip(colors*(2/255 if uv is not None else 1/255), 0, 1).tolist())
            if uv is not None:
                left, top, right, bottom = material_map[palette]['sourceCrop']
                x, y, width, height = material_map[palette]['atlasRect']
                scale = width/(right-left)
                coordinates = ((uv-np.array([left, top]))*scale+np.array([x, y]))/np.array(atlas.size)
                group['uvs'].extend(coordinates.tolist())
            for triangle in ((0, 1, 2), (1, 3, 2)) if len(references) == 4 else ((0, 1, 2),):
                group['indices'].extend(first+corner for corner in triangle)
        primitives = []
        for palette, group in groups.items():
            if palette not in material_indices:
                material = {'name': 'Untextured colors' if palette is None else f'Original palette {palette}',
                            'extensions': {'KHR_materials_unlit': {}}, 'doubleSided': True,
                            'pbrMetallicRoughness': {'metallicFactor': 0, 'roughnessFactor': 1},
                            'extras': {'sourcePalette': palette, 'nativeBlendModeNotExported': True}}
                if palette is not None:
                    material.update({'alphaMode': 'MASK', 'alphaCutoff': 0.5})
                    material['pbrMetallicRoughness']['baseColorTexture'] = {'index': 0}
                material_indices[palette] = len(doc['materials'])
                doc['materials'].append(material)
            attributes = {'POSITION': glb.accessor(group['positions'], 'VEC3', bounds=True, vertex=True),
                          'COLOR_0': glb.accessor(group['colors'], 'VEC3', vertex=True)}
            if palette is not None:
                attributes['TEXCOORD_0'] = glb.accessor(group['uvs'], 'VEC2', vertex=True)
            primitives.append({'attributes': attributes, 'indices': glb.accessor(group['indices'], 'SCALAR', indices=True),
                               'material': material_indices[palette], 'mode': 4,
                               'extras': {key: group[key] for key in ('sourceFaces', 'sourceVertices', 'sourceColors')}})
        if not primitives:
            raise ValueError('Reference part has no faces')
        doc['meshes'].append({'name': f'Source part {part_index:03d}', 'primitives': primitives})
        transform = keyframes[0, part_index]
        doc['nodes'].append({'name': f'Original part {part_index:03d}', 'mesh': part_index,
                             'translation': transform[3:].tolist(),
                             'rotation': quaternion(*(transform[:3]*(2*math.pi/4096))),
                             'extras': {'sourcePart': part_index, 'sourceLocalPivot': [0, 0, 0],
                                        'rig': 'independent rigid part; no inferred bones or skin weights'}})
        inventory.append({'sourcePart': part_index, 'originalVertices': len(vertices), 'originalFaces': len(polygons),
                          'referenceTriangles': sum(len(group['indices'])//3 for group in groups.values()),
                          'palettes': sorted(palette for palette in groups if palette is not None),
                          'sourceBounds': [vertices.min(axis=0).tolist(), vertices.max(axis=0).tolist()]})
    times = glb.accessor(np.arange(len(keyframes), dtype=float), 'SCALAR', bounds=True)
    animation = {'name': 'Source keyframe samples; one second per key; NOT native timing', 'samplers': [], 'channels': [],
                 'extras': {'timing': 'artificial one-second intervals', 'interpolation': 'STEP for inspection only'}}
    for part_index in range(len(parts)):
        rotations = [quaternion(*(frame[part_index, :3]*(2*math.pi/4096))) for frame in keyframes]
        for path, values, kind in (('translation', keyframes[:, part_index, 3:], 'VEC3'), ('rotation', rotations, 'VEC4')):
            sampler = len(animation['samplers'])
            animation['samplers'].append({'input': times, 'output': glb.accessor(values, kind), 'interpolation': 'STEP'})
            animation['channels'].append({'sampler': sampler, 'target': {'node': part_index+1, 'path': path}})
    doc['animations'] = [animation]
    return glb, inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for argument in ('model', 'tim', 'animation', 'packed-control', 'output'):
        parser.add_argument('--'+argument, type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]):
        parser.error('Use a new private output folder outside the source checkout')
    if PIL.__version__ != '12.3.0' or np.__version__ != '2.5.1':
        parser.error('Use the pinned offline dependencies')
    model, tim, animation = [poses.materials.bounded_read(path) for path in (args.model, args.tim, args.animation)]
    atlas, material_map, report = packing.validate_pack(args.packed_control, model, tim)
    if report['algorithm'] != 'nearest':
        parser.error('Export references from an unchanged nearest material control')
    parts = poses.read_geometry(model)
    keyframes = poses.read_keyframes(animation, len(parts))
    glb, inventory = export(parts, keyframes, atlas, material_map, poses.materials.texture(tim)[2])
    identities = {'modelSha256': hashlib.sha256(model).hexdigest(), 'timSha256': hashlib.sha256(tim).hexdigest(),
                  'animationSha256': hashlib.sha256(animation).hexdigest(), 'atlasEngineSha256': report['atlasEngineSha256']}
    glb.document['asset']['extras'] = identities
    payload = glb.finish()
    args.output.mkdir(exist_ok=False)
    (args.output/'source-reference.glb').write_bytes(payload)
    receipt = {'pipeline': 'definitive-private-source-reference-1', **identities, 'parts': inventory,
               'keyframeCount': len(keyframes), 'glbSha256': hashlib.sha256(payload).hexdigest(), 'glbBytes': len(payload),
               'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               'environment': platform.platform(), 'numpy': np.__version__, 'pillow': PIL.__version__,
               'scope': 'Unchanged geometry/pivots and sampled transforms; not authored art or a game-compatible mod',
               'limitations': ['Artificial STEP timing; no native interpolation proof', 'Unlit reference, not native lighting',
                               'STP converted to visibility alpha; primitive blending not reproduced',
                               'Source normals not exported; raw face colors retained, display colors clipped to glTF range',
                               'No inferred hierarchy, skin weights, new faces or physical-size claim']}
    (args.output/'manifest.json').write_text(json.dumps(receipt, indent=2)+'\n')
    print(json.dumps({'parts': len(parts), 'keyframes': len(keyframes), 'glbBytes': len(payload), 'glbSha256': receipt['glbSha256']}))


if __name__ == '__main__':
    main()
