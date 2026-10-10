#!/usr/bin/env python3
"""Synthetic battle-source regressions, with no retail artwork or game window."""
import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path

import numpy as np

spec = importlib.util.spec_from_file_location('battle', Path(__file__).with_name('batch-envhd-battle-materials.py'))
battle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(battle)


def tim(bpp=0, colours=16):
    palette = np.zeros((1, colours), dtype='<u2')
    palette[0, :4] = [0, 0x8000, 31, 0x801f]
    image = np.array([[0x3210, 0x3210]], dtype='<u2') if bpp == 0 else np.array([[0x0100, 0x0302]], dtype='<u2')
    def block(x, y, words):
        h, w = words.shape
        return struct.pack('<I4H', 12 + words.nbytes, x, y, w, h) + words.tobytes()
    return struct.pack('<II', 16, 8) + block(0, 480, palette) + block(64, 0, image)


def model(bpp=0, abr=0, clut=480 << 6, uv=((0,0),(3,0),(0,0)), animation=None, clut_animation=False):
    data = bytearray(12 + 12 + 28)
    struct.pack_into('<III', data, 0, 12, 12 if clut_animation else 0, 0)
    struct.pack_into('<III', data, 12, 65, 0, 1)
    struct.pack_into('<II', data, 24 + 16, len(data) - 24, 1)
    packet = bytearray(16)
    struct.pack_into('<I', packet, 0, 0x24000300)
    for i,(u,v) in enumerate(uv):
        packet[4+i*4:6+i*4] = bytes((u,v))
    struct.pack_into('<H', packet, 6, clut)
    struct.pack_into('<H', packet, 10, 1 | bpp << 7 | abr)
    data.extend(packet)
    if animation:
        extra = len(data)
        struct.pack_into('<I', data, 8, extra)
        values = [*animation, 2, 16, -1]
        data.extend(struct.pack('<10I', 40, *([40 + len(values)*2]*9)))
        data.extend(struct.pack('<7h', *values))
        data.extend(struct.pack('<h', -1))
    return bytes(data)


class BattleMaterialTest(unittest.TestCase):
    def test_static_part_zero_and_real_page_clut_coordinates(self):
        scene, masters = battle.scene(model(abr=0x60), tim(), 10)
        binding = scene['materials'][0]
        self.assertEqual(binding['page'], 1)
        self.assertEqual(binding['clut'], 480 << 6)
        self.assertEqual(binding['parts'], [0])
        self.assertEqual(binding['sourceSize'], [8,1])
        self.assertEqual(len(masters), 1)
        self.assertEqual(list(next(iter(masters.values()))['image'].get_flattened_data())[:4],
                         [(0,0,0,0),(0,0,0,255),(255,0,0,0),(255,0,0,255)])

    def test_four_bit_uploaded_words_can_be_sampled_as_eight_bit(self):
        scene, masters = battle.scene(model(bpp=1), tim(bpp=1, colours=256), 10)
        self.assertEqual(scene['materials'][0]['sourceSize'], [4,1])
        self.assertEqual(list(next(iter(masters.values()))['image'].get_flattened_data()),
                         [(0,0,0,0),(0,0,0,255),(255,0,0,0),(255,0,0,255)])

    def test_eight_bit_incomplete_palette_remains_native(self):
        scene, masters = battle.scene(model(bpp=1), tim(bpp=1), 10)
        self.assertEqual(scene['materials'][0]['status'], 'held-native-incomplete-source')
        self.assertEqual(masters, {})

    def test_pilot_stages_are_protected(self):
        for stage in (0,6):
            scene, masters = battle.scene(model(), tim(), stage)
            self.assertEqual(scene['materials'][0]['status'], 'protected-existing-pilot')
            self.assertEqual(masters, {})

    def test_animated_texture_sampling_and_source_padding_are_held(self):
        for rect in ((64,0,4,1),(65,0,4,1)):
            scene, masters = battle.scene(model(uv=((0,0),(0,0),(0,0)), animation=rect), tim(), 10)
            self.assertEqual(scene['materials'][0]['status'], 'held-native-texture-animation')
            self.assertEqual(masters, {})

    def test_disjoint_animation_does_not_exclude_static_material(self):
        scene, masters = battle.scene(model(animation=(66,0,4,1)), tim(), 10)
        self.assertEqual(scene['materials'][0]['status'], 'candidate-pending')
        self.assertEqual(len(masters), 1)

    def test_palette_animation_is_not_frozen(self):
        scene, masters = battle.scene(model(clut_animation=True), tim(), 10)
        self.assertEqual(scene['materials'][0]['status'], 'held-native-clut-animation')
        self.assertEqual(masters, {})

    def test_out_of_upload_uv_and_nonindexed_bpp_are_held(self):
        for data, status in [(model(uv=((0,0),(8,0),(0,0))), 'held-native-uv-mapping'),
                             (model(bpp=2), 'held-native-bpp')]:
            scene, masters = battle.scene(data, tim(), 10)
            self.assertEqual(scene['materials'][0]['status'], status)
            self.assertEqual(masters, {})

    def test_uniform_material_does_not_create_redundant_upscale(self):
        data = bytearray(tim())
        data[-4:] = b'\0'*4
        scene, masters = battle.scene(model(), bytes(data), 10)
        self.assertEqual(scene['materials'][0]['status'], 'uniform-native-exemption')
        self.assertEqual(masters, {})

    def test_malformed_container_packet_and_animation_fail_closed(self):
        cases = [model()[:20], model()[:-1], model(animation=(64,0,4,1))[:-2]]
        bad = bytearray(model());struct.pack_into('<I',bad,12,64);cases.append(bytes(bad))
        bad = bytearray(model(animation=(64,0,4,1)));struct.pack_into('<h',bad,len(bad)-12,3);cases.append(bytes(bad))
        for data in cases:
            with self.assertRaises(ValueError):
                battle.references(data)

    def test_census_deduplicates_sources_and_refuses_missing_nonempty_texture(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for stage in range(128):
                folder=root/'SECT/DRGN0.BIN'/str(2497+stage)
                (folder/'0').mkdir(parents=True)
                (folder/'0/0').write_bytes(model() if stage in (10,11) else b'')
                if stage in (10,11): (folder/'2').write_bytes(tim())
            scenes, masters, empty = battle.census(root)
            self.assertEqual(len(scenes),2)
            self.assertEqual(len(masters),1)
            self.assertEqual(len(next(iter(masters.values()))['bindings']),2)
            self.assertEqual(len(empty),126)
            (root/'SECT/DRGN0.BIN/2507/2').unlink()
            with self.assertRaises(FileNotFoundError): battle.census(root)

    def test_originals_and_private_plan_cannot_be_written_into_checkout(self):
        with self.assertRaises(ValueError): battle.private_destination(Path('/private/tmp/extraction'), battle.ROOT/'private-output')
        with self.assertRaises(ValueError): battle.private_destination(Path('/private/tmp/extraction'), Path('/private/tmp/extraction/output'))


if __name__ == '__main__':
    unittest.main()
