#!/usr/bin/env python3
"""Original geometry fixtures, AGPL v3; no retail assets or model weights."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec = importlib.util.spec_from_file_location('refinement', Path(__file__).with_name('refine-model-surfaces.py'))
refinement = importlib.util.module_from_spec(spec); spec.loader.exec_module(refinement)


def face(refs, palette=0):
    uv = np.array([[0.,0.],[4.,0.],[0.,4.]])
    return (list(refs), uv.copy(), palette, np.array([[128.,128.,128.]]*3))


class SurfaceRefinementTest(unittest.TestCase):
    def test_authored_closed_joint_edge_and_its_descendants_stay_exact(self):
        points = np.array([[1.,1.,1.],[-1.,-1.,1.],[-1.,1.,-1.],[1.,-1.,-1.]])
        source = (points, [face(refs) for refs in ((0,2,1),(0,1,3),(0,3,2),(1,2,3))])
        first, report = refinement.refine_part(source, crease_degrees=180, fixed_vertices=[0,1])
        np.testing.assert_array_equal(first[0][:2], points[:2])
        boundary = report['preservedVertexIndices']
        self.assertEqual(3, len(boundary)); np.testing.assert_array_equal(first[0][boundary[-1]], (points[0]+points[1])/2)
        self.assertGreater(np.linalg.norm(first[0][2]-points[2]), 0)
        second, next_report = refinement.refine_part(first, crease_degrees=180, fixed_vertices=boundary)
        np.testing.assert_array_equal(second[0][boundary], first[0][boundary])
        for index in next_report['preservedVertexIndices']:
            self.assertEqual(1., second[0][index,2])
            self.assertAlmostEqual(second[0][index,0], second[0][index,1])
        np.testing.assert_array_equal(source[0], points)

    def test_authored_joint_indices_are_bounded_distinct_and_valid(self):
        points = np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.]])
        source = (points, [face((0,1,2))])
        for pins in ([True], [1.5], [-1], [3], [0,0], [0,1,2,3], '0', np.array([[0]]), np.array(0)):
            with self.assertRaises(ValueError): refinement.refine_part(source, fixed_vertices=pins)
        result, report = refinement.refine_part(source, fixed_vertices=np.array([0],dtype=np.int64))
        np.testing.assert_array_equal(result[0][0], points[0]); self.assertEqual(1, report['authoredPinnedVertices'])

    def test_open_joint_boundary_and_face_material_are_preserved(self):
        points = np.array([[0.,0.,0.],[4.,0.,0.],[0.,4.,0.]])
        source = (points, [face((0,1,2), 7)])
        (result, faces), report = refinement.refine_part(source)
        np.testing.assert_array_equal(points, result[:3])
        self.assertEqual(4, len(faces)); self.assertEqual(6, len(result)); self.assertEqual(3, report['pinnedOriginalVertices'])
        for _, uv, palette, colors in faces:
            self.assertEqual(7, palette); self.assertTrue(((uv>=0)&(uv<=4)).all())
            self.assertTrue((uv.sum(axis=1)<=4).all()); self.assertTrue((colors==128).all())
        np.testing.assert_array_equal(source[1][0][1], [[0,0],[4,0],[0,4]])

    def test_closed_surface_refines_and_zero_strength_retains_source(self):
        points = np.array([[1.,1.,1.],[-1.,-1.,1.],[-1.,1.,-1.],[1.,-1.,-1.]])
        source = (points, [face(refs) for refs in ((0,2,1),(0,1,3),(0,3,2),(1,2,3))])
        (result, faces), report = refinement.refine_part(source, crease_degrees=180)
        self.assertEqual(16, len(faces)); self.assertEqual(10, len(result)); self.assertGreater(report['maxOriginalVertexDisplacement'], 0)
        zero, _ = refinement.refine_part(source, strength=0, crease_degrees=180)
        np.testing.assert_array_equal(points, zero[0][:4])
        np.testing.assert_array_equal(source[0], points)
        sharp, sharp_report = refinement.refine_part(source, crease_degrees=0)
        np.testing.assert_array_equal(points, sharp[0][:4]); self.assertEqual(4, sharp_report['pinnedOriginalVertices'])

    def test_overlapping_and_degenerate_parts_are_returned_unchanged(self):
        points = np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.],[0.,0.,1.],[0.,0.,-1.]])
        source = (points, [face((0,1,2)),face((1,0,3)),face((0,1,4))])
        result, report = refinement.refine_part(source)
        self.assertIs(source, result); self.assertFalse(report['changed']); self.assertIn('non-manifold', report['reason'])
        degenerate = (points,[face((0,0,1))]); result, report = refinement.refine_part(degenerate)
        self.assertIs(degenerate, result); self.assertIn('degenerate', report['reason'])
        duplicate = (points,[face((0,1,2)),face((2,0,1))]);result,report=refinement.refine_part(duplicate,crease_degrees=180)
        self.assertIs(duplicate,result);self.assertIn('duplicated',report['reason'])

    def test_closed_parts_touching_at_one_vertex_are_not_smoothed_together(self):
        points=np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.],[0.,0.,1.],[-1.,0.,0.],[0.,-1.,0.],[0.,0.,-1.]])
        tetra=((0,2,1),(0,1,3),(0,3,2),(1,2,3))
        source=(points,[face(refs) for refs in tetra]+[face(tuple(0 if ref==0 else ref+3 for ref in refs)) for refs in tetra])
        result,report=refinement.refine_part(source,crease_degrees=180)
        self.assertIs(source,result);self.assertFalse(report['changed']);self.assertIn('vertex fan',report['reason'])
        np.testing.assert_array_equal(points[0],[0,0,0])

    def test_palette_seams_and_untextured_faces_keep_separate_attributes(self):
        points=np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.],[1.,1.,0.]])
        first=face((0,1,2),1);second=face((1,3,2),2);second[1][:]+=10
        (_, faces), _=refinement.refine_part((points,[first,second]))
        for _, uv, palette, _ in faces: self.assertTrue((uv<5).all() if palette==1 else (uv>=10).all())
        original=(points,[(first[0],None,None,first[3])]);(_, faces),_=refinement.refine_part(original)
        self.assertTrue(all(uv is None and palette is None for _,uv,palette,_ in faces))

    def test_quad_order_and_part_count_survive_multiple_iterations(self):
        points=np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.],[1.,1.,0.]])
        polygon=([0,1,2,3],points[:,:2].copy(),3,np.full((4,3),128.))
        result,reports=refinement.refine([(points,[polygon]),(points,[polygon])],iterations=2)
        self.assertEqual(2,len(result));self.assertEqual(2,len(reports));self.assertTrue(all(len(polys)==32 for _,polys in result))
        self.assertEqual([[0,1,2],[1,3,2]],[poly[0] for poly in refinement.triangles([polygon])])

    def test_short_animations_keep_three_distinct_camera_selections(self):
        for count,frames in ((1,[0,0,0]),(2,[0,1,1]),(12,[0,6,11])):
            views=refinement.study_views(count)
            self.assertEqual(frames,[frame for frame,_ in views]);self.assertEqual(3,len({yaw for _,yaw in views}))
        for count in (0,257,1.5):
            with self.assertRaises(ValueError): refinement.study_views(count)

    def test_nonfinite_invalid_parameters_and_references_are_rejected(self):
        points=np.array([[0.,0.,0.],[1.,0.,0.],[0.,1.,0.]])
        for strength in (float('nan'),float('inf'),-1,2):
            with self.assertRaises(ValueError): refinement.refine_part((points,[face((0,1,2))]),strength=strength)
        for crease in (float('nan'),-1,181):
            with self.assertRaises(ValueError): refinement.refine_part((points,[face((0,1,2))]),crease_degrees=crease)
        for index in (-1,3):
            with self.assertRaises(ValueError): refinement.refine_part((points,[face((0,1,index))]))
        with self.assertRaises(ValueError): refinement.refine([(points,[])],iterations=3)
        broken=points.copy();broken[0,0]=float('nan')
        with self.assertRaises(ValueError): refinement.refine_part((broken,[face((0,1,2))]))


if __name__=='__main__': unittest.main()
