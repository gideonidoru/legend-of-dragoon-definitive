#!/usr/bin/env python3
"""Original synthetic FxHD fixtures; no discs, extracted artwork or inference required."""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('fx_selection', ROOT / 'scripts/select-fxhd-field.py')
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)

def tim(y=465):
    colours = [0, 31, 0x8000, 0x801f] + [i for i in range(4, 16)]
    return struct.pack('<III4H16H I4H H', 16, 8, 44, 960, y, 16, 1, *colours, 14, 976, 320, 1, 1, 0x3210)

class FieldFixtures(unittest.TestCase):
    def fixture(self, folder):
        root = folder / 'checkout'; root.mkdir()
        (root / 'integrations/fxhd/runtime-assets').mkdir(parents=True)
        files = folder / 'private-files'; (files / 'SUBMAP').mkdir(parents=True)
        candidates = folder / 'candidates'; candidates.mkdir()
        for name in selection.batch.NAMES:
            if name != 'smoke_2_dust_palette':
                (files / 'SUBMAP' / (name + '.tim')).write_bytes(tim(474 if name == 'smoke_2' else 465))
        entries = []
        for name in selection.batch.NAMES:
            source = selection.source_bytes(files, name)
            image = selection.batch.packer.source_palette(source, 0)
            entry = {'name': name, 'sourceSha256': __import__('hashlib').sha256(source).hexdigest(),
                     'recipe': selection.batch.RECIPE, 'tools': selection.batch.PINS, 'candidates': []}
            for scale in (2, 4):
                path = candidates / f'{name}-{scale}x.png'
                image.resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST).save(path)
                entry['candidates'].append({'file': path.name, 'scale': scale, 'sha256': selection.batch.digest(path)})
            entries.append(entry)
        (candidates / 'candidates.json').write_text(json.dumps({'assets': entries}))
        return root, SimpleNamespace(files=files, candidates=candidates, work=folder / 'private-work')

    def test_masks_and_native_palette_pairing(self):
        source = selection.batch.packer.source_palette(tim(), 0)
        correct = source.resize((8, 2), Image.Resampling.NEAREST)
        selection.validate(source, correct)
        for position, colour in [((0, 0), (1, 0, 0, 0)), ((2, 0), (0, 0, 0, 0)), ((4, 0), (1, 0, 0, 255)), ((6, 0), (255, 0, 0, 0))]:
            wrong = correct.copy(); wrong.putpixel(position, colour)
            with self.assertRaises(ValueError): selection.validate(source, wrong)
        with self.assertRaises(ValueError): selection.validate(source, source)

    def test_atomic_import_keeps_originals_private_and_preserves_existing_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            root, args = self.fixture(Path(directory)); original_root = selection.ROOT; selection.ROOT = root
            try:
                control = {p.name: p.read_bytes() for p in (args.files / 'SUBMAP').iterdir()}
                selection.select(args)
                runtime = root / 'integrations/fxhd/runtime-assets/fxhd'
                self.assertEqual(12, len(list(runtime.iterdir())))
                self.assertFalse((runtime / 'savepoint.png').exists()); self.assertFalse((runtime / 'smoke_2.png').exists())
                self.assertEqual(control, {p.name: p.read_bytes() for p in (args.files / 'SUBMAP').iterdir()})
                before = {p.name: p.read_bytes() for p in runtime.iterdir()}
                with self.assertRaises(ValueError): selection.select(args)
                self.assertEqual(before, {p.name: p.read_bytes() for p in runtime.iterdir()})
                self.assertFalse(any(p.suffix == '.tim' or 'comparison' in p.name for p in root.rglob('*')))
            finally: selection.ROOT = original_root

    def test_changed_candidate_or_source_cannot_partially_install(self):
        for tamper in ('candidate', 'source'):
            with tempfile.TemporaryDirectory() as directory:
                root, args = self.fixture(Path(directory)); original_root = selection.ROOT; selection.ROOT = root
                try:
                    path = args.candidates / 'right_foot-4x.png' if tamper == 'candidate' else args.files / 'SUBMAP/dust.tim'
                    path.write_bytes(path.read_bytes() + b'changed')
                    with self.assertRaises(ValueError): selection.select(args)
                    self.assertFalse((root / 'integrations/fxhd/runtime-assets/fxhd').exists())
                    self.assertFalse((root / 'integrations/fxhd/production/candidates').exists())
                finally: selection.ROOT = original_root

if __name__ == '__main__': unittest.main()
