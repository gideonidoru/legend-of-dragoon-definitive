#!/usr/bin/env python3
"""Original synthetic GLB/pose/material fixtures, AGPL v3; no retail assets."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import numpy as np
from PIL import Image


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


exporter = load('exporter', 'export-model-reference.py')
fixtures = load('packing_fixtures', 'test-material-packing.py')


def decode(payload):
    magic, version, length, json_length, json_type = struct.unpack_from('<5I', payload)
    assert (magic, version, length, json_type) == (0x46546C67, 2, len(payload), 0x4E4F534A)
    document = json.loads(payload[20:20+json_length])
    binary_length, binary_type = struct.unpack_from('<2I', payload, 20+json_length)
    assert binary_type == 0x004E4942 and binary_length == len(payload)-28-json_length
    return document, payload[28+json_length:]


def attribute(document, binary, index):
    accessor = document['accessors'][index]
    view = document['bufferViews'][accessor['bufferView']]
    components = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[accessor['type']]
    dtype = {5126: '<f4', 5125: '<u4'}[accessor['componentType']]
    return np.frombuffer(binary, dtype=dtype, count=accessor['count']*components,
                         offset=view['byteOffset']).reshape(accessor['count'], components)


def quaternion_matrix(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
                     [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
                     [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])


def sample():
    vertices = np.array([[-10., -10., 0.], [10., -10., 0.], [-10., 10., 0.], [10., 10., 3.]])
    quad = ([0, 1, 2, 3], vertices[:, :2]+10, 0, np.full((4, 3), 128.))
    colored = ([0, 2, 1], None, None, np.array([[255.,0.,0.],[0.,255.,0.],[0.,0.,255.]]))
    second = ([0, 1, 2], np.array([[0.,0.],[20.,0.],[0.,20.]]), 1, np.full((3, 3), 128.))
    parts = [(vertices, [quad, colored, second]), (vertices+np.array([0,0,5]), [second])]
    frames = np.array([[[0,0,1024,10,20,30], [0,1024,0,-15,20,0]],
                       [[-1024,2048,3072,10,-100,30], [512,-512,1536,-15,20,50]],
                       [[2048,0,-1024,0,20,100], [0,0,0,0,0,0]]], dtype=float)
    atlas = Image.new('RGBA', (128, 64), (255, 0, 0, 0))
    mapping = {index: {'sourceCrop': [-8,-8,28,28], 'atlasRect': [index*64,0,64,64]} for index in (0,1)}
    return parts, frames, atlas, mapping


class ModelReferenceTest(unittest.TestCase):
    def test_all_source_corners_and_sampled_transforms_survive_independent_decode(self):
        parts, frames, atlas, mapping = sample()
        glb, inventory = exporter.export(parts, frames, atlas, mapping, 32)
        document, binary = decode(glb.finish())
        self.assertEqual([0,1], [part['sourcePart'] for part in inventory])
        self.assertNotIn('skins', document)
        root = document['nodes'][0]
        basis = quaternion_matrix(root['rotation'])
        np.testing.assert_array_equal(basis, np.diag([1,-1,-1]))
        animation = document['animations'][0]
        for part_index, mesh in enumerate(document['meshes']):
            node = document['nodes'][part_index+1]
            np.testing.assert_array_equal(node['translation'], frames[0,part_index,3:])
            channels = {channel['target']['path']: animation['samplers'][channel['sampler']]
                        for channel in animation['channels'] if channel['target']['node'] == part_index+1}
            quaternions = attribute(document, binary, channels['rotation']['output'])
            translations = attribute(document, binary, channels['translation']['output'])
            for primitive in mesh['primitives']:
                refs = primitive['extras']['sourceVertices']
                original = parts[part_index][0][refs]
                exported = attribute(document, binary, primitive['attributes']['POSITION'])
                np.testing.assert_array_equal(original, exported)
                for key in range(len(frames)):
                    actual = (exported @ quaternion_matrix(quaternions[key]).T + translations[key]) @ basis.T * root['scale']
                    expected = exporter.poses.posed_vertices(parts, frames[key], 0)[part_index][refs] @ basis.T * root['scale']
                    np.testing.assert_allclose(actual, expected, atol=1e-6)

    def test_quad_order_palette_groups_colors_and_uvs_remain_separate(self):
        parts, frames, atlas, mapping = sample()
        glb, _ = exporter.export(parts, frames, atlas, mapping, 32)
        document, binary = decode(glb.finish())
        primitives = document['meshes'][0]['primitives']
        self.assertEqual([0,None,1], [document['materials'][p['material']]['extras']['sourcePalette'] for p in primitives])
        np.testing.assert_array_equal(attribute(document,binary,primitives[0]['indices']).ravel(), [0,1,2,1,3,2])
        for primitive in primitives:
            material = document['materials'][primitive['material']]
            palette = material['extras']['sourcePalette']
            self.assertTrue(material['doubleSided'])
            self.assertIn('KHR_materials_unlit', material['extensions'])
            if palette is None:
                self.assertNotIn('TEXCOORD_0', primitive['attributes'])
            else:
                self.assertEqual('MASK', material['alphaMode'])
                uv = attribute(document,binary,primitive['attributes']['TEXCOORD_0'])
                self.assertTrue((uv[:,0] >= palette/2).all())
                self.assertTrue((uv[:,0] < (palette+1)/2).all())
            self.assertEqual(len(primitive['extras']['sourceVertices']), len(primitive['extras']['sourceColors']))

    def test_stp_visible_black_and_discard_are_conventional_visibility_alpha(self):
        parts, frames, atlas, mapping = sample()
        atlas.putpixel((0,0),(0,0,0,0))
        atlas.putpixel((1,0),(0,0,0,255))
        glb, _ = exporter.export(parts,frames,atlas,mapping,32)
        document,binary = decode(glb.finish())
        view = document['bufferViews'][document['images'][0]['bufferView']]
        with Image.open(io.BytesIO(binary[view['byteOffset']:view['byteOffset']+view['byteLength']])) as image:
            self.assertEqual([(0,0,0,0),(0,0,0,255),(255,0,0,255)], [image.getpixel((x,0)) for x in (0,1,2)])

    def test_short_samples_have_explicit_step_timing_and_all_parts(self):
        parts, frames, atlas, mapping = sample()
        for count in (1,2,3):
            glb,_ = exporter.export(parts,frames[:count],atlas,mapping,32)
            document,binary = decode(glb.finish())
            animation = document['animations'][0]
            self.assertEqual(2*len(parts), len(animation['channels']))
            for sampler in animation['samplers']:
                self.assertEqual('STEP', sampler['interpolation'])
                np.testing.assert_array_equal(attribute(document,binary,sampler['input']).ravel(), np.arange(count))

    def test_invalid_identity_missing_material_and_bad_keyframes_fail_before_output(self):
        parts, frames, atlas, mapping = sample()
        for bad_frames in (frames[:,:1],frames[:0],np.full_like(frames,float('nan'))):
            with self.assertRaises(ValueError): exporter.export(parts,bad_frames,atlas,mapping,32)
        with self.assertRaisesRegex(ValueError,'incomplete'): exporter.export(parts,frames,atlas,{0:mapping[0]},32)
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); model=fixtures.split_faces();tim=fixtures.fixtures.tim()
            fixtures.write_control(root,model,tim)
            (root/'model').write_bytes(model+b'changed');(root/'tim').write_bytes(tim)
            (root/'animation').write_bytes(struct.pack('<4I6h',12,0,0,1|2<<16,0,0,0,0,0,0))
            command=[sys.executable,str(Path(exporter.__file__)), '--model',str(root/'model'), '--tim',str(root/'tim'),
                     '--animation',str(root/'animation'),'--packed-control',str(root),'--output',str(root/'output')]
            result=subprocess.run(command,capture_output=True,text=True)
            self.assertNotEqual(0,result.returncode);self.assertFalse((root/'output').exists())
            (root/'model').write_bytes(model)
            result=subprocess.run(command,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            receipt=json.loads((root/'output/manifest.json').read_bytes())
            self.assertEqual(hashlib.sha256((root/'output/source-reference.glb').read_bytes()).hexdigest(),receipt['glbSha256'])


if __name__ == '__main__':
    unittest.main()
