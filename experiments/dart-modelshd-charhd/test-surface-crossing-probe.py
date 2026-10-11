#!/usr/bin/env python3
"""Independent authored crossing fixtures; AGPL v3, see LICENSE."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec=importlib.util.spec_from_file_location('crossing',Path(__file__).with_name('surface-crossing-probe.py'))
crossing=importlib.util.module_from_spec(spec);spec.loader.exec_module(crossing)


class CrossingTests(unittest.TestCase):
    def test_proper_crossing_and_scale(self):
        vertices=np.array([[-1,-1,0],[1,-1,0],[0,1,0],[-.2,0,-1],[.2,0,1],[.5,.5,-1]],float)
        for scale in (.001,1,1000):
            self.assertEqual(crossing.proper_crossing_pairs(vertices*scale,[[0,1,2]],[[3,4,5]]),[(0,0)])

    def test_disjoint(self):
        vertices=np.array([[0,0,0],[1,0,0],[0,1,0],[0,0,1],[1,0,1],[0,1,1]],float)
        self.assertEqual(crossing.proper_crossing_pairs(vertices,[[0,1,2]],[[3,4,5]]),[])

    def test_shared_vertex_excluded(self):
        vertices=np.array([[-1,-1,0],[1,-1,0],[0,1,0],[.2,0,1],[.5,.5,-1]],float)
        self.assertEqual(crossing.proper_crossing_pairs(vertices,[[0,1,2]],[[0,3,4]]),[])

    def test_endpoint_contact_excluded(self):
        vertices=np.array([[-1,-1,0],[1,-1,0],[0,1,0],[0,0,0],[.2,0,1],[.5,.5,1]],float)
        self.assertEqual(crossing.proper_crossing_pairs(vertices,[[0,1,2]],[[3,4,5]]),[])

    def test_coplanar_overlap_outside_scope(self):
        vertices=np.array([[-1,-1,0],[1,-1,0],[0,1,0],[-.2,0,0],[.2,0,0],[.5,.5,0]],float)
        self.assertEqual(crossing.proper_crossing_pairs(vertices,[[0,1,2]],[[3,4,5]]),[])

    def test_invalid_and_budget(self):
        vertices=np.zeros((6,3))
        for faces in ([[0,1,6]],[[0.,1.,2.]],[[0,1]]):
            with self.assertRaises(ValueError):crossing.proper_crossing_pairs(vertices,faces,[[3,4,5]])
        with self.assertRaises(ValueError):crossing.proper_crossing_pairs(vertices*np.nan,[[0,1,2]],[[3,4,5]])
        with self.assertRaises(ValueError):crossing.proper_crossing_pairs(vertices,np.tile([0,1,2],(1001,1)),np.tile([3,4,5],(1001,1)))


if __name__=='__main__':unittest.main()
