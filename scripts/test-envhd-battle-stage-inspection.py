#!/usr/bin/env python3
"""Behavior checks for private stage geometry/UV diagnostics, no original pixels."""
import importlib.util
from pathlib import Path
import struct
import unittest
import json
import tempfile
from unittest import mock

import numpy as np


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


preview = module('battle_stage_preview_tests', 'inspect-envhd-battle-stage.py')
fixtures = module('battle_stage_source_fixtures', 'test-envhd-battle-materials.py')


def model(bpp=0, vertex=2, normal=0, scale=0):
    data = bytearray(84)
    struct.pack_into('<3I', data, 0, 12, 0, 0)
    struct.pack_into('<3I', data, 12, 65, 0, 1)
    struct.pack_into('<7I', data, 24, 28, 3, 52, 1, 60, 1, scale)
    for i, xyz in enumerate(((-10, -10, 0), (10, -10, 0), (0, 10, 0))):
        struct.pack_into('<4h', data, 52 + i * 8, *xyz, 0)
    packet = bytearray(24)
    struct.pack_into('<I', packet, 0, 0x24000500)
    for i, uv in enumerate(((0, 0), (3, 0), (0, 0))):
        packet[4 + i * 4:6 + i * 4] = bytes(uv)
    struct.pack_into('<H', packet, 6, 480 << 6)
    struct.pack_into('<H', packet, 10, 1 | bpp << 7)
    struct.pack_into('<4H', packet, 16, normal, 0, 1, vertex)
    return bytes(data + packet)


class StageInspectionTests(unittest.TestCase):
    def test_real_page_clut_and_mixed_bit_depth_are_not_flattened(self):
        for bpp in (0, 1):
            parts = preview.geometry(model(bpp))
            self.assertEqual(parts[0][1][0][2], (1 | bpp << 7, 480 << 6))
            self.assertEqual(parts[0][1][0][0], [0, 1, 2])
            np.testing.assert_array_equal(parts[0][0], [[-10, -10, 0], [10, -10, 0], [0, 10, 0]])

    def test_bad_vertex_normal_and_scale_fail_closed(self):
        for kwargs in ({'vertex': 3}, {'normal': 1}, {'scale': 1}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                preview.geometry(model(**kwargs))

    def test_truncated_primitive_cannot_be_previewed(self):
        with self.assertRaises(ValueError):
            preview.geometry(model()[:-1])

    def test_hd_quantization_precedes_source_origin_translation(self):
        uv = np.array([[np.nextafter(8., -np.inf), np.nextafter(4., -np.inf)], [8., 4.]])
        x, y = preview.coordinates(uv, (7, 3), 4, (12, 12, 4))
        np.testing.assert_array_equal(x, [3, 4])
        np.testing.assert_array_equal(y, [3, 4])

    def test_discard_and_visible_black_and_alpha_zero_colour_remain_distinct(self):
        pixels = np.array([[0, 0, 0, 0], [0, 0, 0, 255], [255, 0, 0, 0], [255, 0, 0, 255]])
        np.testing.assert_array_equal(preview.visible(pixels), [False, True, True, True])

    def test_first_tim_decode_respects_four_and_eight_bit_palettes(self):
        for bpp in (0, 1):
            data, tim = model(bpp), fixtures.tim(bpp=bpp, colours=256 if bpp else 16)
            scene, _ = preview.battle.scene(data, tim, 10)
            original, existing, proposed = preview.stage_textures(data, tim, scene, None, {})
            self.assertIs(existing[1 | bpp << 7, 480 << 6], original[1 | bpp << 7, 480 << 6])
            self.assertIs(proposed[1 | bpp << 7, 480 << 6], original[1 | bpp << 7, 480 << 6])
            np.testing.assert_array_equal(preview.sample(original[1 | bpp << 7, 480 << 6], np.array([[0, 0], [1, 0], [2, 0], [3, 0]])),
                                          [[0, 0, 0, 0], [0, 0, 0, 255], [255, 0, 0, 0], [255, 0, 0, 255]])

    def test_software_diagnostic_draws_and_rejects_unbounded_or_nan_views(self):
        data, tim = model(), fixtures.tim()
        scene, _ = preview.battle.scene(data, tim, 10)
        textures, _, _ = preview.stage_textures(data, tim, scene, None, {})
        image = preview.render(preview.geometry(data), textures, textures, 0., 0., (128, 128))
        self.assertGreater(np.count_nonzero(np.array(image)[..., 0] == 255), 0)
        for yaw, size in ((float('nan'), (128, 128)), (0., (4096, 4096))):
            with self.assertRaises(ValueError):
                preview.render(preview.geometry(data), textures, textures, yaw, 0., size)

    def test_oversized_selected_atlas_fails_before_file_or_pixel_decode(self):
        data,tim=model(),fixtures.tim();scene,_=preview.battle.scene(data,tim,10)
        with mock.patch.object(preview.Image,'open',side_effect=AssertionError('Unbounded atlas decoded')):
            with self.assertRaisesRegex(ValueError,'bounded dimensions'):
                preview.stage_textures(data,tim,scene,dict(atlasSize=[8256,8256]),{})

    def test_private_preview_cannot_write_original_geometry_inside_checkout(self):
        with self.assertRaisesRegex(ValueError, 'private output'):
            preview.inspect(Path('/unused'), preview.ROOT / 'public-original-preview', [1])

    def test_original_previews_refuse_sibling_git_checkouts(self):
        with tempfile.TemporaryDirectory() as folder:
            sibling=Path(folder)/'sibling';sibling.mkdir();(sibling/'.git').write_text('gitdir: /example')
            with self.assertRaisesRegex(ValueError,'private output'):
                preview.inspect(Path('/unused'),sibling/'preview',[1])

    def test_mutation_during_render_cannot_publish_comparison_manifest(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'checkout';production=root/'integrations/envhd/production';production.mkdir(parents=True)
            files=Path(folder)/'source';files.mkdir();data,tim=model(),fixtures.tim()
            (files/'model').write_bytes(data);(files/'tim').write_bytes(tim)
            scene,_=preview.battle.scene(data,tim,10)
            scene.update(modelSourceFile='model',timSourceFile='tim')
            selection=production/'battle-material-runtime-artwork.json';selection.write_text(json.dumps(dict(scenes=[])))
            paths=[production/'battle-presentation-artwork-v1.json',production/'battle-material-artwork.json']
            for path in paths:path.write_text('{}')
            checked=dict(verified=[],manifestSha256=preview.battle.terrain.digest(paths[0].read_bytes()),
                         sourceArtworkLedgerSha256=preview.battle.terrain.digest(paths[1].read_bytes()))
            original=preview.render
            def mutate(*args):
                result=original(*args);selection.write_text(selection.read_text()+' ');return result
            output=Path(folder)/'private-preview'
            with mock.patch.object(preview,'ROOT',root),mock.patch.object(preview.verify,'preflight',return_value=checked), \
                    mock.patch.object(preview.battle,'census',return_value=([scene],{},[])),mock.patch.object(preview,'render',side_effect=mutate):
                with self.assertRaisesRegex(ValueError,'changed during inspection'):preview.inspect(files,output,[10])
            self.assertFalse((output/'manifest.json').exists())


if __name__ == '__main__':
    unittest.main()
