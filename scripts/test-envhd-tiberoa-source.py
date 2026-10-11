#!/usr/bin/env python3
"""Synthetic seam, ordered-upload and palette guards for authoring only."""
import importlib.util
import io
import struct
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('audit', Path(__file__).with_name('audit-envhd-tiberoa-source.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
repair_spec = importlib.util.spec_from_file_location('repair', Path(__file__).with_name('repair-envhd-tiberoa-candidate.py'))
repair = importlib.util.module_from_spec(repair_spec)
repair_spec.loader.exec_module(repair)
UV = [(248, 0), (7, 0), (248, 31), (7, 31)]


def tim_bytes(x=382, width=4, bpp=0, colour=0x4210):
    colours = 16 if bpp == 0 else 256
    palette = struct.pack('<I4H', 12 + colours * 2, 848 if bpp == 0 else 0, 488, colours, 1)
    palette += struct.pack('<H', colour) * colours
    image = struct.pack('<I4H', 12 + width * 32 * 2, x, 256, width, 32)
    image += struct.pack('<H', 0x1111 if bpp == 0 else 0x0101) * (width * 32)
    return struct.pack('<II', 16, 8 + bpp) + palette + image


class CrossingSourceTests(unittest.TestCase):
    def fixture(self, sources=None):
        return audit.terrain.upload_bank(sources or [(14, tim_bytes())])

    def test_lifts_native_corners_before_interpolation(self):
        v, w, tims = self.fixture()
        image, _, corners, owners = audit.decode_crossing(v, w, tims, 21, 31285, UV)
        self.assertEqual(image.size, (16, 32))
        self.assertEqual(corners, [(248, 0), (263, 0), (248, 31), (263, 31)])
        self.assertEqual(owners, {'14': 16})
        self.assertEqual((corners[0][0] + corners[1][0]) / 2, 255.5)
        self.assertNotEqual((UV[0][0] + UV[1][0]) / 2, 255.5)

    def test_plain_rectangle_is_not_reinterpreted(self):
        _, _, tims = self.fixture([(14, tim_bytes(x=320))])
        with self.assertRaises(ValueError):
            audit.crossing_source(tims, 21, [(0, 0), (15, 31)])

    def test_both_sides_of_seam_required(self):
        _, _, tims = self.fixture()
        with self.assertRaises(ValueError):
            audit.crossing_source(tims, 21, [(248, 0), (255, 31)])

    def test_signed_and_preinterpolated_values_rejected(self):
        _, _, tims = self.fixture()
        for uv in [[(-8, 0), (7, 31)], [(248, 0), (127, 31)], [(248, 0), (263, 31)], [(248, 0), (7.5, 31)]]:
            with self.subTest(uv=uv), self.assertRaises(ValueError):
                audit.crossing_source(tims, 21, uv)

    def test_ambiguous_crossing_upload_held(self):
        _, _, tims = self.fixture([(14, tim_bytes()), (15, tim_bytes())])
        with self.assertRaises(ValueError):
            audit.crossing_source(tims, 21, UV)

    def test_missing_palette_and_image_words_held(self):
        for kind in ['palette', 'image']:
            v, w, tims = self.fixture()
            w[488, 848] = kind != 'palette'
            w[256, 384] = kind != 'image'
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                audit.decode_crossing(v, w, tims, 21, 31285, UV)

    def test_later_image_overlap_held(self):
        v, w, tims = self.fixture([(14, tim_bytes()), (15, tim_bytes(x=384, width=1))])
        with self.assertRaisesRegex(ValueError, 'overwritten'):
            audit.decode_crossing(v, w, tims, 21, 31285, UV)

    def test_final_ordered_palette_used(self):
        v, w, tims = self.fixture([(14, tim_bytes()), (15, tim_bytes(x=100, colour=0x001f))])
        image, _, _, owners = audit.decode_crossing(v, w, tims, 21, 31285, UV)
        self.assertEqual(owners, {'15': 16})
        self.assertTrue(np.all(np.asarray(image) == [255, 0, 0, 0]))

    def test_eight_bit_crossing_source(self):
        v, w, tims = self.fixture([(14, tim_bytes(x=444, width=8, bpp=1))])
        image, _, corners, owners = audit.decode_crossing(v, w, tims, 149, 31232, UV)
        self.assertEqual(image.size, (16, 32))
        self.assertEqual(corners[1], (263, 0))
        self.assertEqual(owners, {'14': 256})

    def test_truncated_source_packet_held(self):
        malformed = struct.pack('<III', 65, 0, 2) + bytes(56)
        model = bytearray(malformed)
        struct.pack_into('<II', model, 56, 56, 1)
        with self.assertRaises(ValueError):
            audit.face_records(bytes(model), 21, 31285)


class CandidateRepairTests(unittest.TestCase):
    @staticmethod
    def png(mode='RGB', size=(16, 32), colour=(100, 120, 110)):
        output = io.BytesIO()
        Image.new(mode, size, colour).save(output, format='PNG')
        return output.getvalue()

    def test_source_stp_discard_and_visible_black_preserved(self):
        source = Image.fromarray(np.array([[[0, 0, 0, 0]], [[0, 0, 0, 255]],
                                          [[120, 140, 130, 0]], [[80, 90, 70, 255]]], dtype=np.uint8))
        result, _ = repair.repair(source, self.png(size=(16, 64)))
        expected = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
        actual = np.asarray(result)
        self.assertTrue(np.array_equal(actual[:, :, 3], expected[:, :, 3]))
        self.assertTrue(np.array_equal(np.all(actual == 0, axis=2), np.all(expected == 0, axis=2)))
        self.assertTrue(np.array_equal(np.all(actual[:, :, :3] == 0, axis=2), np.all(expected[:, :, :3] == 0, axis=2)))

    def test_generated_transparency_rejected(self):
        with self.assertRaisesRegex(ValueError, 'opaque'):
            repair.repair(Image.new('RGBA', (16, 32)), self.png('RGBA', colour=(100, 120, 110, 254)))

    def test_changed_aspect_rejected(self):
        with self.assertRaisesRegex(ValueError, 'aspect'):
            repair.repair(Image.new('RGBA', (16, 32)), self.png(size=(32, 32)))

    def test_deterministic_output(self):
        source = Image.new('RGBA', (16, 32), (120, 140, 130, 0))
        raw = self.png()
        a, ra = repair.repair(source, raw)
        b, rb = repair.repair(source, raw)
        self.assertEqual(a.tobytes(), b.tobytes())
        self.assertEqual(ra, rb)


if __name__ == '__main__':
    unittest.main()
