#!/usr/bin/env python3
"""Synthetic experimental shape/UV invariants, AGPL v3."""
import copy
import importlib.util
from pathlib import Path
import unittest
import numpy as np
spec = importlib.util.spec_from_file_location('dart_study', Path(__file__).with_name('build-study.py'))
study = importlib.util.module_from_spec(spec)
spec.loader.exec_module(study)

class StudyTest(unittest.TestCase):
    def triangle(self):
        return {'vertices': [[-2,0,-5],[2,0,-5],[0,5,-5]], 'normals':[[0,0,1]]*3,
                'faces':[{'sourceFace':7,'vertices':[0,1,2],'normals':[0,1,2],'sourceWeights':np.eye(3).tolist()}]}

    def test_shared_edges_and_lineage_survive_subdivision(self):
        source = self.triangle()
        source['vertices'].append([4,5,-5])
        source['normals'].append([0,0,1])
        source['faces'].append({'sourceFace':8,'vertices':[1,3,2],'normals':[1,3,2],'sourceWeights':np.eye(3).tolist()})
        saved = copy.deepcopy(source)
        result = study.subdivide(source)
        self.assertEqual(source,saved)
        self.assertEqual(len(result['vertices']),9)  # One shared midpoint, not two.
        self.assertEqual(len(result['faces']),8)
        for face in result['faces']:
            self.assertIn(face['sourceFace'],(7,8))
            np.testing.assert_allclose(np.sum(face['sourceWeights'],axis=1),1)
            self.assertTrue((np.asarray(face['sourceWeights'])>=0).all())

    def test_attachment_boundary_stays_exact_and_shapes_do_not_fold(self):
        source = self.triangle()
        result, report = study.shape(source,'field','face')
        np.testing.assert_array_equal(result['vertices'][:3],source['vertices'])
        for face in result['faces']:
            a,b,c = np.asarray(result['vertices'])[face['vertices']]
            self.assertGreater(np.cross(b-a,c-a)[2],0)
        self.assertGreater(report['maximumDisplacement'],0)
        self.assertTrue(np.isfinite(result['normals']).all())

    def test_face_paint_uses_texture_uvs_and_preserves_unselected_material(self):
        source = self.triangle()
        source['faces'][0]['sourceFace']=19
        polygons=[([],np.asarray([[0,0],[1,0],[0,1]]),0,np.ones((3,3))*128)]*20
        native=(np.asarray(source['vertices']),polygons)
        _, painted=study.paint_face(source,native,'field',967)
        self.assertEqual(painted[0][2],967)
        self.assertTrue(((painted[0][1]>=0)&(painted[0][1]<=1)).all())
        np.testing.assert_array_equal(painted[0][3],np.full((3,3),127.5))
        source['faces'][0]['sourceFace']=0
        _, original=study.paint_face(source,native,'field',967)
        self.assertEqual(original[0][2],0)
        np.testing.assert_array_equal(original[0][1],polygons[0][1])

if __name__ == '__main__': unittest.main()
