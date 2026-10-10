#!/usr/bin/env python3
"""Behavior checks for ordered palette uploads, native coverage and material mapping."""
import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('terrain', Path(__file__).with_name('batch-envhd-terrain.py'))
terrain = importlib.util.module_from_spec(spec)
spec.loader.exec_module(terrain)


def tim(bpp=0, colour=31):
    palette = np.zeros((1, 16 if bpp == 0 else 256), dtype='<u2')
    palette[0, :4] = [0, 0x8000, colour, colour | 0x8000]
    image = np.array([[0x3210 if bpp == 0 else 0x0100, 0x3210 if bpp == 0 else 0x0302]], dtype='<u2')
    def block(x, y, words):
        h, w = words.shape
        return struct.pack('<I4H', 12 + words.nbytes, x, y, w, h) + words.tobytes()
    return struct.pack('<II', 16, 8 | bpp) + block(0, 480, palette) + block(64, 0, image)


def model(uv=((0,0),(7,0),(0,0)), part=1):
    data = bytearray(12 + 2 * 28)
    struct.pack_into('<III', data, 0, 65, 0, 2)
    struct.pack_into('<II', data, 12 + part * 28 + 16, len(data) - 12, 1)
    packet = bytearray(16)
    struct.pack_into('<I', packet, 0, (0x24 << 24) | (3 << 8))
    for i,(u,v) in enumerate(uv):
        packet[4 + i * 4:6 + i * 4] = bytes((u,v))
    struct.pack_into('<H', packet, 6, 480 << 6)
    struct.pack_into('<H', packet, 10, 1)
    return bytes(data + packet)


class TerrainTest(unittest.TestCase):
    def test_four_and_eight_bit_shader_colours(self):
        for bpp in (0, 1):
            vram, written, tims = terrain.upload_bank([(0, tim(bpp))])
            image = terrain.decode_material(vram, written, tims[0], 1 | bpp << 7, 480 << 6)
            self.assertEqual(list(image.get_flattened_data())[:4], [(0,0,0,0),(0,0,0,255),(255,0,0,0),(255,0,0,255)])

    def test_final_uploaded_palette_wins_over_embedded_palette(self):
        vram, written, tims = terrain.upload_bank([(0, tim(colour=31)), (1, tim(colour=31 << 5))])
        image = terrain.decode_material(vram, written, tims[0], 1, 480 << 6)
        self.assertEqual(image.getpixel((2,0)), (0,255,0,0))

    def test_uninitialized_palette_rejected(self):
        vram, written, tims = terrain.upload_bank([(0, tim())])
        with self.assertRaises(ValueError):
            terrain.decode_material(vram, written, tims[0], 1, 479 << 6)

    def test_stp_is_not_conventional_transparency(self):
        source = Image.fromarray(np.array([[(0,0,0,0),(0,0,0,255),(255,0,0,0),(255,0,0,255)]], dtype=np.uint8))
        candidate = np.full((4,16,3), 80, dtype=np.uint8)
        restored, preview = terrain.preserve_stp(source, candidate)
        self.assertEqual([restored.getpixel((x,0)) for x in (0,4,8,12)], [(0,0,0,0),(0,0,0,255),(80,80,80,0),(80,80,80,255)])
        self.assertEqual([preview.getpixel((x,0))[3] for x in (0,4,8,12)], [0,255,255,255])

    def test_neural_black_cannot_create_new_discard(self):
        for stp in (0,255):
            source = Image.new('RGBA', (1,1), (255,0,0,stp))
            restored, _ = terrain.preserve_stp(source, np.zeros((4,4,3), dtype=np.uint8))
            self.assertEqual(restored.getpixel((0,0)), (255,0,0,stp))

    def test_resume_validates_metadata_dimensions_and_coverage(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'candidate.png'
            original = Image.new('RGBA', (2,1), (255,0,0,0))
            item = dict(image=original, decodedRgbaSha256=terrain.fingerprint(original), sourceSize=[2,1], bindings=[])
            candidate = original.resize((8,4), Image.Resampling.NEAREST)
            candidate.save(path)
            record = terrain.candidate_record(item, terrain.digest(path.read_bytes()))
            terrain.validate_completed(item, record, path)
            for key in ('sourceSize','bindings','method','engineSha256'):
                changed = dict(record);changed[key] = 'wrong'
                with self.assertRaises(ValueError):
                    terrain.validate_completed(item, changed, path)
            for changed in (original, Image.new('RGBA',(8,4),(0,0,0,0)), Image.new('RGBA',(8,4),(255,0,0,255))):
                changed.save(path)
                record = terrain.candidate_record(item, terrain.digest(path.read_bytes()))
                with self.assertRaises(ValueError):
                    terrain.validate_completed(item, record, path)

    def test_invalid_candidate_and_stp_rejected(self):
        for source, pixels in [(Image.new('RGBA',(1,1),(1,1,1,128)), np.zeros((4,4,3),dtype=np.uint8)),
                               (Image.new('RGBA',(1,1)), np.zeros((8,8,3),dtype=np.uint8))]:
            with self.assertRaises(ValueError):
                terrain.preserve_stp(source, pixels)

    def test_malformed_tim_is_rejected(self):
        for data in (tim()[:-1], tim() + b'extra', b'bad', struct.pack('<II',16,2)):
            with self.assertRaises(ValueError):
                terrain.decode_tim(data)
        data = bytearray(tim())
        struct.pack_into('<H', data, 12, 1020)
        with self.assertRaises(ValueError):
            terrain.decode_tim(data)

    def test_static_parts_only_and_translucency_bits_normalized(self):
        self.assertEqual(terrain.static_materials(model(part=0)), {})
        data = bytearray(model())
        struct.pack_into('<H', data, len(data) - 6, 1 | 0x60)
        material = terrain.static_materials(bytes(data))
        self.assertEqual(set(material), {(1,480 << 6)})
        self.assertEqual(material[(1,480 << 6)]['parts'], {1})

    def test_truncated_tmd_rejected(self):
        for data in (model()[:-1], model()[:30], b'bad'):
            with self.assertRaises(ValueError):
                terrain.static_materials(data)

    def test_shared_pixels_deduplicated_and_wrapped_uv_held(self):
        with tempfile.TemporaryDirectory() as temp:
            files = Path(temp)
            base = files / 'SECT/DRGN0.BIN'
            base.mkdir(parents=True)
            for bank in range(8):
                directory = base / str(5697 + bank)
                directory.mkdir()
                (directory / '0').write_bytes(tim())
                (base / str(5705 + bank)).write_bytes(model(uv=((248,0),(7,0),(0,0))) if bank == 2 else model())
            scenes, masters = terrain.census(files)
            self.assertEqual(len(masters), 1)
            self.assertEqual(len(next(iter(masters.values()))['bindings']), 7)
            self.assertEqual(scenes[2]['materials'][0]['status'], 'held-native-uv-mapping')
            self.assertEqual(scenes[0]['materials'][0]['sourceUvOrigin'], [0,0])
            (base / '5697/0').rename(base / '5697/1')
            with self.assertRaises(ValueError):
                terrain.census(files)

    def test_private_output_guard(self):
        with self.assertRaises(ValueError):
            terrain.execute(Path('/private/tmp/sources'), terrain.ROOT / 'output', Path('missing'), Path('missing'))


if __name__ == '__main__':
    unittest.main()
