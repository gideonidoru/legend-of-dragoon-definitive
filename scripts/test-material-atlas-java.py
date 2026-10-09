#!/usr/bin/env python3
"""Original cross-language material preflight fixtures, AGPL v3; no game or neural assets."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import random
import struct
import subprocess
import sys
import tempfile
import unittest
from PIL import Image


spec = importlib.util.spec_from_file_location('packing_tests', Path(__file__).with_name('test-material-packing.py'))
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)
JAVA = CLASSPATH = None


def varied_source(clut_width):
    rng = random.Random(clut_width)
    width, height, rows = 64, 32, 2
    palettes = clut_width * rows // 16
    colours = [0 if i % 16 == 0 else 0x8000 if i % 16 == 2 else ((i // 16 + 1) & 31) for i in range(clut_width * rows)]
    pixels = bytes([0x21]) * (width * height // 2)
    tim = struct.pack('<3I4H', 0x10, 8, 12+clut_width*rows*2, 0, 0, clut_width, rows)
    tim += struct.pack('<' + str(len(colours)) + 'H', *colours)
    tim += struct.pack('<I4H', 12+len(pixels), 0, 0, width//4, height) + pixels
    packets = []
    for palette in range(palettes):
        clut = palette % (clut_width//16) | (palette//(clut_width//16) << 6)
        for quad in (False, True):
            coords = [(rng.randrange(width), rng.randrange(height)) for _ in range(4 if quad else 3)]
            # Include integer extremes, coincident UVs and ordinary non-axis-aligned faces.
            if palette == 0: coords = [(0, 0), (width-1, height-1), (0, 0)] + ([(width-1, 0)] if quad else [])
            body = b''.join(struct.pack('<2BH', u, v, clut if i == 0 else 0) for i, (u, v) in enumerate(coords))
            body += struct.pack('<' + str(len(coords)+1) + 'H', 0, *range(len(coords)))
            body += bytes((-len(body)) % 4)
            packets.append(struct.pack('<I', ((0x2c if quad else 0x24) << 24) | (len(body)//4 << 8)) + body)
    model = struct.pack('<6I', 12, 0, 0, 0x41, 0, 1)
    model += struct.pack('<7I', 28, 4, 60, 1, 68, len(packets), 0) + bytes(32+8) + b''.join(packets)
    return model, tim


class JavaMaterialPreflightTest(unittest.TestCase):
    def java(self, folder, success=True):
        run = subprocess.run([JAVA, '-Djava.awt.headless=true', '-cp', CLASSPATH,
                              'legend.definitive.materials.MaterialAtlasCheck',
                              str(folder/'source-model'), str(folder/'source-tim'), str(folder)],
                             capture_output=True, text=True, timeout=15)
        if not success:
            self.assertNotEqual(run.returncode, 0, 'Java accepted a corrupted material pack')
            self.assertIn('IOException', run.stderr)
            return
        self.assertEqual(run.returncode, 0, run.stderr)
        return json.loads(run.stdout)

    def control(self, folder, model=None, scale=2, tim=None):
        model = model if model is not None else fixtures.split_faces()
        tim = tim if tim is not None else fixtures.fixtures.tim()
        _, masks, crops, slots, width, height = fixtures.packing.layout(model, tim, scale)
        atlas = Image.new('RGBA', (width, height))
        for palette in masks:
            x, y, w, h = slots[palette]
            atlas.paste(fixtures.packing.clamped_crop(fixtures.packing.source_palette(tim, palette), crops[palette]).resize((w, h), Image.Resampling.NEAREST), (x, y))
        atlas.save(folder/'atlas-engine-stp.png')
        report = {'pipeline': 'definitive-private-material-pack-1', 'modelSha256': hashlib.sha256(model).hexdigest(),
                  'timSha256': hashlib.sha256(tim).hexdigest(), 'scale': scale, 'paddingSourceTexels': 8,
                  'algorithm': 'nearest', 'preservedPalettes': [], 'atlasSize': [width, height],
                  'materials': [{'palette': p, 'sourceCrop': list(crops[p]), 'atlasRect': list(slots[p])} for p in sorted(masks)],
                  'atlasEngineSha256': hashlib.sha256((folder/'atlas-engine-stp.png').read_bytes()).hexdigest()}
        (folder/'manifest.json').write_text(json.dumps(report))
        (folder/'source-model').write_bytes(model)
        (folder/'source-tim').write_bytes(tim)
        fixtures.packing.validate_pack(folder, model, tim)
        return report

    def test_same_conflicting_and_degenerate_controls_at_two_scales(self):
        for model in (fixtures.split_faces(), fixtures.fixtures.model(True)):
            for scale in (2, 4):
                with tempfile.TemporaryDirectory() as temp:
                    folder = Path(temp); report = self.control(folder, model, scale)
                    result = self.java(folder)
                    self.assertEqual(result['atlasSize'], report['atlasSize'])
                    self.assertEqual(result['atlasSha256'], report['atlasEngineSha256'])
                    self.assertEqual(result['materials'], len(report['materials']))
                    self.assertFalse(result['nativeRendering'])

    def test_both_languages_reject_changed_sources_and_mapping(self):
        for change in ('source', 'mapping', 'fractional'):
            with tempfile.TemporaryDirectory() as temp:
                folder = Path(temp); report = self.control(folder)
                if change == 'source':
                    (folder/'source-model').write_bytes((folder/'source-model').read_bytes()+b'changed')
                elif change == 'mapping':
                    report['materials'][0]['sourceCrop'][0] += 1
                else:
                    report['scale'] = 2.0
                (folder/'manifest.json').write_text(json.dumps(report))
                with self.assertRaises(ValueError):
                    fixtures.packing.validate_pack(folder, (folder/'source-model').read_bytes(), (folder/'source-tim').read_bytes())
                self.java(folder, False)

    def test_same_clut_rows_quads_and_extreme_uv_layouts(self):
        for clut_width in (16, 32, 64):
            model, tim = varied_source(clut_width)
            for scale in (2, 4):
                with tempfile.TemporaryDirectory() as temp:
                    folder = Path(temp); report = self.control(folder, model, scale, tim)
                    result = self.java(folder)
                    self.assertEqual(result['atlasSize'], report['atlasSize'])
                    self.assertEqual(result['materials'], clut_width//8)

    def test_both_languages_reject_rehashed_pixel_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp); report = self.control(folder)
            image = Image.open(folder/'atlas-engine-stp.png').convert('RGBA')
            x, y, _, _ = report['materials'][0]['atlasRect']
            image.putpixel((x, y), (255, 0, 0, 255))
            image.save(folder/'atlas-engine-stp.png')
            report['atlasEngineSha256'] = hashlib.sha256((folder/'atlas-engine-stp.png').read_bytes()).hexdigest()
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaises(ValueError):
                fixtures.packing.validate_pack(folder, (folder/'source-model').read_bytes(), (folder/'source-tim').read_bytes())
            self.java(folder, False)

    def test_all_pinned_model_descriptions_agree(self):
        for scale, _, weights, parameters in fixtures.packing.neural.MODELS.values():
            with tempfile.TemporaryDirectory() as temp:
                folder = Path(temp); report = self.control(folder, scale=scale)
                report.update({'algorithm': 'neural', 'strength': 0.5, 'weightsSha256': weights, 'paramsSha256': parameters})
                (folder/'manifest.json').write_text(json.dumps(report))
                fixtures.packing.validate_pack(folder, (folder/'source-model').read_bytes(), (folder/'source-tim').read_bytes())
                self.java(folder)
                report['paramsSha256'] = 'unrecognized'
                (folder/'manifest.json').write_text(json.dumps(report))
                with self.assertRaises(ValueError):
                    fixtures.packing.validate_pack(folder, (folder/'source-model').read_bytes(), (folder/'source-tim').read_bytes())
                self.java(folder, False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True)
    parser.add_argument('--classpath', required=True)
    args = parser.parse_args()
    JAVA, CLASSPATH = args.java, args.classpath
    unittest.main(argv=[sys.argv[0]])
