#!/usr/bin/env python3
"""Authored arm fixtures; no game data. AGPL v3; see LICENSE."""
from collections import Counter
import importlib.util
from pathlib import Path
import unittest

import numpy as np

spec = importlib.util.spec_from_file_location('arm', Path(__file__).with_name('complete-arm-surface.py'))
arm = importlib.util.module_from_spec(spec); spec.loader.exec_module(arm)


def tube(first, last, sides=8):
    angles = np.arange(sides)*2*np.pi/sides
    ring = np.column_stack((np.cos(angles), np.sin(angles), np.full(sides, first)))
    other = ring.copy(); other[:, 2] = last
    vertices = np.vstack((ring, other))
    faces = []
    for i in range(sides):
        j = (i+1)%sides
        faces.extend(((i, j, i+sides), (j, j+sides, i+sides)))
    return vertices, np.array(faces)


def fixture():
    upper, lower = tube(-2, 2), tube(-2, 2)
    angles = np.arange(8)*2*np.pi/8
    ring = np.column_stack((np.cos(angles), np.sin(angles), np.zeros(8)))
    ring = np.array([p for a,b in zip(ring,np.roll(ring,-1,axis=0)) for p in (a,(a+b)/2)])
    return upper, lower, ring, ring.copy(), 2


class ArmTests(unittest.TestCase):
    def test_cut_edges_have_two_owners(self):
        surface = arm.CompleteArmSurface(*fixture())
        edges = Counter(tuple(sorted((a,b))) for face in surface.faces for a,b in zip(face, np.roll(face,-1)))
        self.assertEqual(sum(v == 1 for v in edges.values()), 16) # two original shoulder/wrist rings
        self.assertTrue(all(v <= 2 for v in edges.values()))

    def test_shared_collar_has_exactly_one_vertex(self):
        surface = arm.CompleteArmSurface(*fixture())
        a, b = np.eye(4), np.eye(4); b[2,3] = 3
        vertices = surface.deform(a,b)
        self.assertTrue(np.array_equal(vertices[surface.upper_ring], surface.upper[surface.upper_ring]))
        self.assertTrue(np.array_equal(vertices[surface.lower_ring+len(surface.upper)], surface.lower[surface.lower_ring]+b[:3,3]))

    def test_clipped_material_lineage(self):
        upper, lower, first, second, axis = fixture()
        surface = arm.CompleteArmSurface(upper, lower, first, second, axis)
        for refs,row in zip(surface.faces[surface.face_regions == 0],surface.upper_lineage):
            rebuilt = np.array(row['weights'])@upper[0][upper[1][row['sourceTriangle']]]
            np.testing.assert_allclose(surface.upper[refs],rebuilt,atol=1e-12)
            np.testing.assert_allclose(np.sum(row['weights'],axis=1),1)

    def test_rigid_shells_preserve_geometry(self):
        surface=arm.CompleteArmSurface(*fixture());a=np.eye(4);b=np.eye(4);b[2,3]=3
        b[:3,:3]=[[0,-1,0],[1,0,0],[0,0,1]]
        vertices=surface.deform(a,b)
        np.testing.assert_array_equal(vertices[:len(surface.upper)],surface.upper)
        np.testing.assert_array_equal(vertices[len(surface.upper):surface.rigid_count],arm.collars.placed(surface.lower,b))

    def test_inserted_samples_split_cut_edges(self):
        upper,lower,first,second,axis=fixture()
        rings=[]
        for ring in (first,second):
            rings.append(np.array([p for a,b in zip(ring,np.roll(ring,-1,axis=0)) for p in (a,(a+b)/2)]))
        surface=arm.CompleteArmSurface(upper,lower,*rings,axis)
        self.assertEqual(len(surface.upper_ring),32)

    def test_missing_native_corners_rejected(self):
        upper,lower,first,second,axis=fixture()
        # Cutting corners produces a visually plausible strip but unwelded edges.
        first=(first+np.roll(first,-1,axis=0))/2;second=first.copy()
        with self.assertRaisesRegex(ValueError,'unwelded'):
            arm.CompleteArmSurface(upper,lower,first,second,axis)

    def test_cut_through_original_vertex_no_degenerate_faces(self):
        vertices,faces=tube(-2,2);ring=vertices[:8].copy()
        clipped,refs,indices,_=arm.clip_to_ring(vertices,faces,2,-2,False,ring)
        tri=clipped[refs]
        self.assertTrue(np.all(np.linalg.norm(np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]),axis=1)>1e-8))
        self.assertEqual(len(indices),8)

    def test_inputs_are_copied(self):
        values=fixture();surface=arm.CompleteArmSurface(*values)
        expected=surface.upper.copy();values[0][0][:]=100
        np.testing.assert_array_equal(surface.upper,expected)
        with self.assertRaises(ValueError):surface.faces[0,0]=0

    def test_bad_mesh_or_ring_rejected(self):
        v,f=tube(-2,2);ring=v[:8].copy()
        for vertices,faces in ((v*np.nan,f),(v,f+len(v)),(v,f.astype(float))):
            with self.assertRaises(ValueError):arm.clip_to_ring(vertices,faces,2,-2,False,ring)
        for axis in (True,2.,'2'):
            with self.assertRaises(ValueError):arm.clip_to_ring(v,f,axis,-2,False,ring)
        with self.assertRaises(ValueError):arm.clip_to_ring(v,f,2,np.nan,False,ring)
        with self.assertRaises(ValueError):arm.clip_to_ring(v,f,2,-2,False,ring+np.array([0,0,.1]))

    def test_bad_pose_rejected(self):
        surface=arm.CompleteArmSurface(*fixture());b=np.eye(4);b[2,3]=3;b[0,0]=2
        with self.assertRaises(ValueError):surface.deform(np.eye(4),b)


if __name__ == '__main__':unittest.main()
