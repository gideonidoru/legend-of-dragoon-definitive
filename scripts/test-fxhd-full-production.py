#!/usr/bin/env python3
"""Original fixtures for the full FX census, palette constraints and atomic import."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('production', Path(__file__).with_name('fxhd-full-production.py'))
p = importlib.util.module_from_spec(spec); spec.loader.exec_module(p)

def tim(padding=0, x=4):
    palette = [0, 0x8000, 31, 0x83e0] + list(range(4, 16))
    return struct.pack('<III4H16H', 16, 8, 44 + padding, 0, 300, 16, 1, *palette) + bytes(padding) + struct.pack('<I4HH', 14, x, 10, 1, 1, 0x3210)

class FullFixtures(unittest.TestCase):
    def test_padded_palette_and_address_independent_deduplication(self):
        a = p.decode(tim()); b = p.decode(tim(64, 30))
        np.testing.assert_array_equal(a[0], [[0, 1, 2, 3]])
        self.assertEqual(a[3], b[3]); self.assertNotEqual(p.sha(tim()), p.sha(tim(64, 30)))
        broken = bytearray(tim()); struct.pack_into('<I', broken, 8, 12)
        with self.assertRaises((ValueError, struct.error)): p.decode(broken)

    def test_census_discovers_valid_tims_missing_from_prior_csv_and_fails_missing_controls(self):
        with tempfile.TemporaryDirectory() as folder:
            files = Path(folder) / 'files'; files.mkdir()
            for name in p.FIELD:
                path = files / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(tim())
            root = files / 'SECT/DRGN0.BIN'; (root / '4114/3').mkdir(parents=True)
            for n in (5695, 5718): (root / str(n)).mkdir()
            (root / '4114/3/19').write_bytes(tim(64)); (root / '4114/3/mrg').write_bytes(b'')
            (root / '5695/0').write_bytes(tim()); (root / '5718/7').write_bytes(tim())
            (root / '4139').mkdir(); (root / '4140/0').mkdir(parents=True); (root / '4139/1').write_bytes(tim())
            inventory = Path(folder) / 'inventory.csv'; inventory.write_text('path,sha256\n')
            ledger = p.census(files, inventory)
            self.assertEqual(len(p.FIELD) + 4, ledger['sourceCount']); self.assertEqual(1, ledger['uniqueJobs'])
            self.assertEqual(1, ledger['nonTextureSupportFiles']['empty-or-directory-metadata'])
            (files / 'shadow.tim').unlink()
            with self.assertRaisesRegex(ValueError, 'Incomplete source extraction'): p.census(files, inventory)

    def test_weights_stay_local_and_native_indices_remain_exact(self):
        indices, palettes, _, _ = p.decode(tim()); _, palette = p.reference(indices, palettes)
        target = np.repeat(np.repeat(palette[indices], 2, axis=0), 2, axis=1).copy()
        target[0, 4] = (palette[2] + palette[3]) / 2
        result = p.weights(indices, palette, target); p.validate(indices, result)
        self.assertGreater(result[0, 4, 2], 0)
        self.assertTrue(set(result[..., 1].ravel()) <= set(indices.ravel()))
        result[0, 0, 0] = 15
        with self.assertRaises(ValueError): p.validate(indices, result)

    def bundle(self, folder):
        output = folder / 'output'; (output / 'detail').mkdir(parents=True)
        indices, palettes, _, job = p.decode(tim()); _, palette = p.reference(indices, palettes)
        rgba = p.weights(indices, palette, np.repeat(np.repeat(palette[indices], 2, axis=0), 2, axis=1))
        image = output / 'detail' / (job + '.png'); Image.fromarray(rgba).save(image)
        source = {'source':'shadow.tim','sourceSha256':p.sha(tim()),'job':job,'sourceSize':[4,1]}
        digest = p.sha(image.read_bytes())
        public = {'pipeline':p.RECIPE,'tools':p.PINS,'state':'all-census-sources-generated-and-bound',
                  'sourceCount':1,'uniqueJobs':1,'sources':[source],'outputs':{job:{'outputSha256':digest,'sourceSize':[4,1]}}}
        p.save(output / 'full-coverage.json', public)
        (output / 'bindings.tsv').write_text('\t'.join([source['sourceSha256'],job,digest,'4','1']) + '\n')
        return output

    def test_complete_import_contains_only_custom_detail_and_rolls_back_publication_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder); output = self.bundle(folder); root = folder / 'checkout'
            p.publish(output, root)
            module = root / 'integrations/fxhd'
            before = {str(f.relative_to(module)):f.read_bytes() for f in module.rglob('*') if f.is_file()}
            self.assertFalse(any(f.endswith('.tim') for f in before))
            with patch.object(p, 'save', side_effect=OSError('fixture write failure')):
                with self.assertRaises(OSError): p.publish(output, root)
            self.assertEqual(before, {str(f.relative_to(module)):f.read_bytes() for f in module.rglob('*') if f.is_file()})
            (output / 'bindings.tsv').write_text('')
            with self.assertRaises(ValueError): p.publish(output, root)
            self.assertEqual(before, {str(f.relative_to(module)):f.read_bytes() for f in module.rglob('*') if f.is_file()})

    def test_failed_rollback_keeps_previous_payload_recoverable(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder); output = self.bundle(folder); root = folder / 'checkout'
            p.publish(output, root)
            module = root / 'integrations/fxhd'; runtime = module / 'runtime-assets/fxhd/full'
            before = {str(f.relative_to(runtime)):f.read_bytes() for f in runtime.rglob('*') if f.is_file()}
            replace = Path.replace
            def reject_restore(source, target):
                if source.name == 'previous': raise OSError('fixture rollback rename failure')
                return replace(source, target)
            with patch.object(p,'save',side_effect=OSError('fixture publication failure')), patch.object(Path,'replace',reject_restore):
                with self.assertRaisesRegex(RuntimeError,'recovery retained at'): p.publish(output, root)
            recoveries = list(module.glob('.fxhd-import-*')); self.assertEqual(1,len(recoveries))
            backup = recoveries[0] / 'previous'
            self.assertEqual(before,{str(f.relative_to(backup)):f.read_bytes() for f in backup.rglob('*') if f.is_file()})
            self.assertTrue((recoveries[0] / 'previous-coverage.json').is_file())

    def test_payload_rejects_mutation_extra_file_and_incomplete_denominator(self):
        with tempfile.TemporaryDirectory() as folder:
            output = self.bundle(Path(folder)); p.validate_bundle(output)
            extra = output / 'detail/source.tim'; extra.write_bytes(tim())
            with self.assertRaises(ValueError): p.validate_bundle(output)
            extra.unlink(); ledger = output / 'full-coverage.json'; data = json.loads(ledger.read_text()); data['sourceCount'] = 2; p.save(ledger,data)
            with self.assertRaises(ValueError): p.validate_bundle(output)
            data['sourceCount'] = 1; p.save(ledger,data)
            image = next((output / 'detail').glob('*.png')); image.write_bytes(b'corrupt')
            with self.assertRaises(ValueError): p.validate_bundle(output)

if __name__ == '__main__': unittest.main()
