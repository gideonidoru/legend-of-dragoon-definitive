#!/usr/bin/env python3
"""Selection withdrawal and transaction rollback using synthetic artwork only."""
import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest import mock
from PIL import Image

def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file));result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
fields=module('field_selector_test','select-envhd-fields.py');battle=module('battle_selector_test','pack-envhd-battle-materials.py');fixture=module('battle_selector_fixture','test-envhd-battle-materials.py')
def note(output,verdict='visual-reviewed-runtime-pending'):
    return dict(intent='Original synthetic colors',style='Development baseline',layout='Exact source canvas',verdict=verdict,outputSha256=output,nativeAcceptance='pending',finalQualityAcceptance='pending')
class SelectionTest(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup);self.root=Path(temp.name);self.addCleanup(setattr,fields,'ROOT',fields.ROOT);fields.ROOT=self.root;self.addCleanup(setattr,battle,'ROOT',battle.ROOT);battle.ROOT=self.root
        self.prod=self.root/'integrations/envhd/production';self.prod.mkdir(parents=True)
    def field_fixture(self):
        key='a'*64;folder=self.prod/'field-candidates'/key;folder.mkdir(parents=True)
        Image.new('RGBA',(8,4),(123,32,1,255)).save(folder/'image-v1.png');output=fields.tools.terrain.digest((folder/'image-v1.png').read_bytes())
        a=dict(decodedRgbaSha256=key,outputSha256=output,review=note(output),owners=['envhd'],inputCandidate=dict(sourceSize=[2,1],bindings=[]));(folder/'manifest-v1.json').write_text(json.dumps(a))
        (self.prod/'field-artwork.json').write_text(json.dumps(dict(assets=[a])));(self.prod/'field-review-receipt.json').write_text(json.dumps(dict(reviews={key:a['review']})));(self.prod/'field-ownership.json').write_text(json.dumps(dict(scenes=[dict(owner='skurfa',mappings=[dict(cut=37)])])))
        return key,a
    def test_field_withdrawal_removes_executable_resources_but_keeps_candidate_history(self):
        key,a=self.field_fixture();self.assertEqual(1,fields.select()['runtimeSelected']);runtime=self.root/'integrations/envhd/runtime-assets/envhd/fields'
        a['review']=note(a['outputSha256'],'intent-revision-needed');(self.prod/'field-artwork.json').write_text(json.dumps(dict(assets=[a])));(self.prod/'field-review-receipt.json').write_text(json.dumps(dict(reviews={key:a['review']})))
        self.assertEqual(0,fields.select()['runtimeSelected']);self.assertFalse((runtime/key/'manifest.properties').exists());self.assertTrue((self.prod/'field-candidates'/key/'image-v1.png').exists())
    def test_field_failed_swap_restores_prior_tree_and_ledger(self):
        key,a=self.field_fixture();fields.select();ledger=self.prod/'field-runtime-artwork.json';prior=ledger.read_bytes();actual=fields.tools.publication.atomic_write
        def fail(path,data):
            if path==ledger:raise OSError('Synthetic ledger write failure')
            return actual(path,data)
        with mock.patch.object(fields.tools.publication,'atomic_write',side_effect=fail):
            with self.assertRaises(OSError):fields.select()
        self.assertEqual(prior,ledger.read_bytes());self.assertTrue((self.root/'integrations/envhd/runtime-assets/envhd/fields'/key/'image-v1.png').exists())
    def test_battle_last_held_material_removes_stage_resource(self):
        source,masters=fixture.battle.scene(fixture.model(),fixture.tim(),10);source['modelSourceFile']='fixture';source['timSourceFile']='fixture'
        key,item=next(iter(masters.items()));folder=self.prod/'battle-material-candidates'/key;folder.mkdir(parents=True)
        item['image'].resize((item['image'].width*4,item['image'].height*4),Image.Resampling.NEAREST).save(folder/'image-v1.png');output=fixture.battle.terrain.digest((folder/'image-v1.png').read_bytes());a=fixture.battle.terrain.candidate_record(item,output);a.update(reviewStatus='visual-reviewed-runtime-pending',review=note(output))
        def save():
            (folder/'manifest-v1.json').write_text(json.dumps(a));(self.prod/'battle-material-artwork.json').write_text(json.dumps(dict(scenes=[source],emptyModelSlots=[],assets=[a])))
        save()
        with mock.patch.object(battle.battle_sources,'census',return_value=([source],masters,[])):
            self.assertEqual(1,battle.publish(self.root/'private')['runtimeSelectedMasters'])
            a['reviewStatus']='intent-revision-needed';a['review']=note(output,'intent-revision-needed');save()
            self.assertEqual(0,battle.publish(self.root/'private')['runtimeSelectedMasters'])
        runtime=self.root/'integrations/envhd/runtime-assets/envhd/battle-materials';self.assertFalse(any(runtime.rglob('manifest.json')));self.assertTrue((folder/'image-v1.png').exists())
if __name__=='__main__':unittest.main()
