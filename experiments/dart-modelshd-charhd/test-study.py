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

    def test_battle_face_paint_preserves_bandana_and_hair_materials(self):
        # Native Dart battle head at the pinned model hash: these are red cloth / hair.
        for face_id in (20,21,23,110,121,122):
            source=self.triangle();source['faces'][0]['sourceFace']=face_id
            polygons=[([],None,0,np.array([[109,15,12]]*3))]*(face_id+1)
            _,painted=study.paint_face(source,(np.asarray(source['vertices']),polygons),'combat',967)
            self.assertEqual(painted[0][2],0,f'Face paint overwrites protected face {face_id}')

    def test_battle_landmarks_place_eyes_below_bandana(self):
        source=self.triangle();source['faces'][0]['sourceFace']=0
        # Bone-local landmarks: eye center qY=2.1, mouth center qY=5.4.
        source['vertices']=[[-70,38.5,-35],[-70,38.5,35],[-70,-11,0]]
        polygons=[([],None,0,np.ones((3,3))*128)]
        _,painted=study.paint_face(source,(np.asarray(source['vertices']),polygons),'combat',967)
        eye_v=painted[0][1][0,1]/study.FACE_MAPPING['faceRegionHeight'];mouth_v=painted[0][1][2,1]/study.FACE_MAPPING['faceRegionHeight']
        self.assertGreaterEqual(eye_v,.29);self.assertLessEqual(eye_v,.36)
        self.assertGreaterEqual(mouth_v,.60);self.assertLessEqual(mouth_v,.68)

    def test_rebuilt_locks_preserve_source_and_nonhair_faces(self):
        source=self.triangle();source['vertices']=[[-8,-8,0],[8,-8,0],[0,-1,2]]
        source['faces'][0]['sourceFace']=4
        source['faces'].append({**source['faces'][0],'sourceFace':0})
        saved=copy.deepcopy(source)
        native=(np.asarray(source['vertices']),[([0,1,2],None,0,np.ones((3,3))*128)]*5)
        mesh,projection,count=study.hair_builder.rebuild(source,native,'field',{4})
        self.assertEqual(source,saved)
        self.assertEqual(count,2)
        self.assertTrue(np.isfinite(mesh['vertices']).all())
        self.assertTrue(np.isfinite(mesh['normals']).all())
        self.assertIn(saved['faces'][1],mesh['faces'])
        for face in mesh['faces']:
            self.assertIn(face['sourceFace'],(0,4))
            np.testing.assert_allclose(np.sum(face['sourceWeights'],axis=1),1)
            a,b,c=np.asarray(mesh['vertices'])[face['vertices']]
            self.assertGreater(np.linalg.norm(np.cross(b-a,c-a)),1e-8)
        self.assertIn('4',projection)

    def test_hair_uvs_stay_in_own_atlas_region(self):
        source=self.triangle();source['faces'][0]['sourceFace']=75
        polygons=[([],None,0,np.ones((3,3))*128)]*76
        _,painted=study.paint_face(source,(np.asarray(source['vertices']),polygons),'combat',967)
        self.assertEqual(painted[0][2],967)
        self.assertTrue((painted[0][1][:,1]>=study.FACE_MAPPING['faceRegionHeight']).all())
        self.assertTrue((painted[0][1][:,0]<=study.FACE_MAPPING['hairRegionWidth']).all())

    def test_rectangular_detail_sampler_matches_native_normalized_axes(self):
        uv=np.array([[.5,.5],[0,1],[1,0]])
        x,y=study.detail_coordinates(uv,{'normalizedDetail':True,'atlasRect':[0,1792,1254,2022]})
        np.testing.assert_array_equal(x,[627,0,1253])
        np.testing.assert_array_equal(y,[2803,3813,1792])

if __name__ == '__main__': unittest.main()
