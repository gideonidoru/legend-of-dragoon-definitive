#!/usr/bin/env python3
"""Synthetic battle-source regressions, with no retail artwork or game window."""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

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

    def test_texture_animation_touching_referenced_clut_is_not_frozen(self):
        scene, masters = battle.scene(model(animation=(0,480,64,1)), tim(), 10)
        self.assertEqual(scene['materials'][0]['status'], 'held-native-texture-animation')
        self.assertEqual(masters, {})

    def test_animation_scratch_is_a_palette_dependency(self):
        data = bytearray(tim())
        struct.pack_into('<HH', data, 12, 960, 256)
        scene, masters = battle.scene(model(clut=(256<<6)|60,animation=(128,0,64,1)), bytes(data), 10)
        self.assertEqual(scene['materials'][0]['status'], 'held-native-texture-animation')
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

    def test_staging_subdirectories_files_and_control_temporaries_cannot_redirect(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)/'private';root.mkdir()
            elsewhere = Path(temp)/'public';elsewhere.mkdir()
            (root/'private-work').symlink_to(elsewhere, target_is_directory=True)
            with self.assertRaises(ValueError): battle.staging_path(root,'private-work/source.png')
            (root/'candidate.png').symlink_to(elsewhere/'source.png')
            with self.assertRaises(ValueError): battle.staging_path(root,'candidate.png')
            (root/'plan.json.tmp').symlink_to(elsewhere/'plan.json')
            with self.assertRaises(ValueError): battle.write_json(root/'plan.json',{})
            self.assertEqual(list(elsewhere.iterdir()),[])

    def test_resumable_controls_are_bounded(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)/'source-plan.json'
            path.write_bytes(b' '* (4 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError): battle.read_control(path)


class BattleCandidateImportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.files, self.staging, self.repo = root/'files', root/'staging', root/'repo'
        self.staging.mkdir()
        for stage in range(128):
            folder = self.files/'SECT/DRGN0.BIN'/str(2497+stage)
            (folder/'0').mkdir(parents=True)
            (folder/'0/0').write_bytes(model() if stage==10 else b'')
            if stage==10: (folder/'2').write_bytes(tim())
        spec = importlib.util.spec_from_file_location('battle_import', Path(__file__).with_name('import-envhd-battle-materials.py'))
        self.importer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.importer)
        self.importer.ROOT = self.repo
        (self.repo/'scripts').mkdir(parents=True)
        (self.repo/'scripts/batch-envhd-battle-materials.py').write_bytes(Path(__file__).with_name('batch-envhd-battle-materials.py').read_bytes())
        scenes, masters, empty = battle.census(self.files)
        self.plan = battle.plan(scenes, masters, empty)
        self.key, item = next(iter(masters.items()))
        image = item['image'].resize((item['image'].width*4,item['image'].height*4),Image.Resampling.NEAREST)
        self.png = self.staging/(self.key+'.png');image.save(self.png)
        self.record = battle.terrain.candidate_record(item,battle.terrain.digest(self.png.read_bytes()))
        self.note = dict(intent='Synthetic battle source',style='Development baseline',layout='Exact synthetic source UV/STP',
                         verdict='visual-reviewed-runtime-pending',outputSha256=self.record['outputSha256'],
                         nativeAcceptance='pending',finalQualityAcceptance='pending')
        self.production = self.repo/'integrations/envhd/production'

    def publish(self):
        (self.staging/'source-plan.json').write_text(json.dumps(self.plan))
        (self.staging/'candidates.json').write_text(json.dumps([self.record]))
        reviews = self.staging/'reviews.json'
        reviews.write_text(json.dumps(dict(reviews={self.key:self.note})))
        return self.importer.publish(self.files,self.staging,reviews)

    def test_candidate_publication_is_repeatable_and_never_selects_runtime(self):
        self.assertEqual(self.publish(),1)
        self.assertEqual(self.publish(),1)
        ledger=json.loads((self.production/'battle-material-artwork.json').read_text())
        self.assertEqual(ledger['runtimeSelected'],0)
        self.assertEqual(ledger['nativeAccepted'],0)
        self.assertFalse((self.repo/'integrations/envhd/runtime-assets').exists())
        self.assertEqual(len(list(self.production.rglob('*.*'))),3)

    def test_source_and_pipeline_drift_reject_before_publication(self):
        original = self.plan['scriptSha256'];self.plan['scriptSha256']='bad'
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse(self.production.exists())
        self.plan['scriptSha256']=original
        changed=bytearray(tim());struct.pack_into('<H',changed,24,31<<5)
        (self.files/'SECT/DRGN0.BIN/2507/2').write_bytes(changed)
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse(self.production.exists())

    def test_review_identity_and_native_acceptance_cannot_be_faked(self):
        for field in ('outputSha256','nativeAcceptance','finalQualityAcceptance'):
            value=self.note[field];self.note[field]='wrong'
            with self.assertRaises(ValueError): self.publish()
            self.assertFalse(self.production.exists());self.note[field]=value
        for field in ('bindings','engineSha256','sourceSize'):
            value=self.record[field];self.record[field]='wrong'
            with self.assertRaises(ValueError): self.publish()
            self.assertFalse(self.production.exists());self.record[field]=value

    def test_new_version_cannot_overwrite_an_immutable_candidate(self):
        self.publish()
        selected=self.production/'battle-material-candidates'/self.key/'image-v1.png'
        selected.write_bytes(b'protected-version')
        with self.assertRaises(FileExistsError): self.publish()
        self.assertEqual(selected.read_bytes(),b'protected-version')

    def test_changed_coverage_rejects_before_publication(self):
        Image.new('RGBA',(32,4),(0,0,0,0)).save(self.png)
        self.record['outputSha256']=self.note['outputSha256']=battle.terrain.digest(self.png.read_bytes())
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse(self.production.exists())

    def test_publication_redirect_cannot_write_to_another_folder(self):
        elsewhere=Path(self.temp.name)/'outside';elsewhere.mkdir()
        (self.repo/'integrations').symlink_to(elsewhere,target_is_directory=True)
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(list(elsewhere.iterdir()),[])


if __name__ == '__main__':
    unittest.main()
