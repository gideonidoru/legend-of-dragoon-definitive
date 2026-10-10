#!/usr/bin/env python3
"""Original broad-pass fixtures; AGPL v3. No retail assets."""
import importlib.util
from pathlib import Path
import struct
import unittest
import numpy as np

spec=importlib.util.spec_from_file_location('world',Path(__file__).with_name('build-modelshd-world-pass.py'))
world=importlib.util.module_from_spec(spec);spec.loader.exec_module(world)


def fixture(unlit=False,flat=False):
    points=[(0,0,0),(10,0,0),(10,10,0),(0,10,0),(5,5,0 if flat else 2)]
    faces=[(0,1,4),(1,2,4),(2,3,4),(3,0,4)]
    vertices=b''.join(struct.pack('<4h',*p,0) for p in points)
    normals=b'' if unlit else struct.pack('<4h',0,0,4096,0)
    packets=[]
    for f in faces:
        uv=struct.pack('<2BH2BH2BH',0,0,0,4,0,0,0,4,0)
        body=uv+(struct.pack('<3B B',128,128,128,0)*3 if unlit else b'')
        body+=struct.pack('<3H',*f) if unlit else struct.pack('<4H',0,*f)
        body+=bytes(-len(body)%4)
        packets.append(bytes([0,len(body)//4,0,0x25 if unlit else 0x24])+body)
    table=struct.pack('<7I',28,len(points),28+len(vertices),0 if unlit else 1,
                      28+len(vertices)+len(normals),len(faces),0)
    return struct.pack('<6I',12,0,0,0x41,0,1)+table+vertices+normals+b''.join(packets)


class WorldPassTest(unittest.TestCase):
    def candidate(self,data):
        parts,native,_=world.exporter.native_parts(data)
        identity=world.part_identities(data,parts,native)[0]
        self.assertEqual(identity,world.exporter.native_parts(data)[2])
        return world.refine_candidate(identity,parts[0],native[0]),parts

    def test_curved_part_refines_with_exact_joint_borders(self):
        import json
        (payload,report),parts=self.candidate(fixture())
        self.assertTrue(report['changed'])
        points=np.array(json.loads(payload)['parts'][0]['vertices'])
        np.testing.assert_array_equal(points[:4],parts[0][0][:4])
        self.assertGreater(report['maxSurfaceDisplacement'],0)
        self.assertEqual(report['outputTriangles'],16)

    def test_flat_helpers_do_not_pay_for_useless_tessellation(self):
        (payload,report),_=self.candidate(fixture(flat=True))
        self.assertIsNone(payload)
        self.assertEqual(report['reason'],'no geometric smoothing benefit')

    def test_unlit_textured_geometry_with_no_source_normals_is_supported(self):
        import json
        (payload,report),_=self.candidate(fixture(unlit=True))
        self.assertTrue(report['changed'])
        normals=np.array(json.loads(payload)['parts'][0]['normals'])
        self.assertTrue(np.isfinite(normals).all())
        np.testing.assert_allclose(np.linalg.norm(normals,axis=1),1)

    def test_material_relocation_keeps_part_identity(self):
        data=bytearray(fixture()); parts,native,_=world.exporter.native_parts(data)
        identity=world.part_identities(data,parts,native)[0]
        primitive=24+struct.unpack_from('<I',data,40)[0]
        struct.pack_into('<H',data,primitive+6,128)
        other_parts,other_native,_=world.exporter.native_parts(data)
        self.assertEqual(world.part_identities(data,other_parts,other_native)[0],identity)


if __name__=='__main__': unittest.main()
