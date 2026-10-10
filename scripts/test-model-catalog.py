#!/usr/bin/env python3
"""Original synthetic catalog fixtures; AGPL v3, see LICENSE."""
import copy
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest

import importlib.util


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


catalog = load('catalog_fixture', 'catalog-modelshd-sources.py')
battle = load('battle_fixture', 'modelshd-battle-map.py')
material = load('material_fixture', 'test-model-material-audit.py')


def model(x=1, normal=1):
    value = bytearray(material.model())
    struct.pack_into('<3h', value, 52, 0, 0, 0)
    struct.pack_into('<3h', value, 60, x, 0, 0)
    struct.pack_into('<3h', value, 68, 0, 2, 0)
    struct.pack_into('<3h', value, 76, 0, 0, normal)
    return bytes(value)


class CatalogTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo, self.files = self.root/'repo', self.root/'files'
        self.repo.mkdir(); self.files.mkdir()
        core = [catalog.exporter.native_parts(model(x))[2] for x in range(10, 29)]
        manifest = self.repo/'integrations/modelshd/src/main/resources/modelshd/models/roster.json'
        manifest.parent.mkdir(parents=True)
        manifest.write_text(json.dumps({'models':[{'sourceGeometrySha256':x} for x in core]}))
        self.data = model()
        self.geometry = catalog.exporter.native_parts(self.data)[2]
        (self.files/'a').write_bytes(self.data); (self.files/'b').write_bytes(self.data)
        self.inventory = {'geometry':[{'sourceGeometrySha256':x,'sources':[], 'parts':1} for x in core] +
                          [{'sourceGeometrySha256':self.geometry,'sources':['a','b'], 'parts':1}],
                          'summary':{'additionalSupportedGeometryContainers':1}}

    def test_normals_and_materials_do_not_turn_shape_reuse_into_identity(self):
        first = catalog.analyze(self.data, self.geometry)
        second_data = model(normal=2)
        second_id = catalog.exporter.native_parts(second_data)[2]
        self.assertNotEqual(second_id, self.geometry)
        second = catalog.analyze(second_data, second_id)
        self.assertEqual(first['shapeSha256'], second['shapeSha256'])
        third_data = model(x=3)
        third = catalog.analyze(third_data, catalog.exporter.native_parts(third_data)[2])
        self.assertNotEqual(first['shapeSha256'], third['shapeSha256'])
        self.assertEqual(first['topologySha256'], third['topologySha256'])

    def test_every_alias_is_verified_not_only_representative(self):
        rows, _ = catalog.make_catalog(self.inventory, self.files, self.repo, [])
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['referenceCount'], 2)
        self.assertEqual(rows[0]['appearances'][1]['sourceSha256'], hashlib.sha256(self.data).hexdigest())
        (self.files/'b').write_bytes(model(x=3))
        with self.assertRaisesRegex(ValueError, 'alias no longer matches'):
            catalog.make_catalog(self.inventory, self.files, self.repo, [])

    def test_paths_duplicates_and_symlinks_cannot_escape_inventory(self):
        for sources in [['a','../outside'], ['a','/outside'], ['a','a']]:
            bad = copy.deepcopy(self.inventory); bad['geometry'][-1]['sources'] = sources
            with self.assertRaises(ValueError):
                catalog.make_catalog(bad, self.files, self.repo, [])
        outside = self.root/'outside'; outside.write_bytes(self.data)
        (self.files/'b').unlink(); (self.files/'b').symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'escapes'):
            catalog.make_catalog(self.inventory, self.files, self.repo, [])

    def test_candidates_stay_candidates_and_private_fields_are_removed(self):
        record = {'kind':'field-unresolved','label':'Knight candidate','confidence':'unresolved',
                  'candidateNames':['Kaiser'],'privateBytes':'must not be published','evidence':[]}
        rows, _ = catalog.make_catalog(self.inventory,self.files,self.repo,[{'a':[record]}])
        self.assertEqual(rows[0]['entityNames'], [])
        self.assertEqual(rows[0]['candidateEntityNames'], ['Kaiser'])
        self.assertNotIn('privateBytes', json.dumps(rows))
        with self.assertRaisesRegex(ValueError, 'Unproved'):
            catalog.clean_record({**record,'entityName':'Kaiser'})
        with self.assertRaisesRegex(ValueError, 'Private path'):
            catalog.clean_record({**record,'evidence':['/Users/owner/private']})

    def test_component_leads_ignore_small_helpers_and_preserve_multiset_weight(self):
        def row(id, parts):
            return {'catalogId':id,'geometry':{'partShapeSha256':[x for x,_ in parts],
                                             'partTriangles':[n for _,n in parts]}}
        rows = [row('a',[('body',120),('helper',2)]), row('b',[('body',120),('other',100)])]
        leads = catalog.component_candidates(rows,[])
        self.assertEqual(len(leads),1)
        self.assertEqual(leads[0]['sharedTriangles'],120)
        self.assertEqual(leads[0]['weightedCoverage']['a'],1)
        self.assertNotIn('_reuseWeight', rows[0])
        self.assertEqual(catalog.component_candidates([row('c',[('helper',2)]),row('d',[('helper',2)])],[]),[])

    def test_explorer_escapes_script_termination_and_is_offline(self):
        html = catalog.explorer({'label':'</script><script>alert(1)</script>'})
        self.assertIn('\\u003c/script>', html)
        self.assertNotIn('<script>alert(1)',html)
        self.assertNotIn('fetch(',html)

    def test_role_proof_is_hash_bound_and_requires_documented_call(self):
        scanner=self.repo/'scripts/modelshd/BattleRoleScan.java'; scanner.parent.mkdir(parents=True)
        scanner.write_text('synthetic scanner')
        engine=self.repo/'src/main/java/legend/game/combat/Battle.java'; engine.parent.mkdir(parents=True)
        engine.write_text('synthetic engine')
        script=self.files/'SECT/DRGN1.BIN/263'; script.parent.mkdir(parents=True)
        script.write_bytes(b'synthetic script')
        digest=lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        proof={'toolSha256':digest(scanner),'engineSourceSha256':digest(engine),'metadataSha256':{},
               'bossKillScripts':[{'monsterId':262,'script':'SECT/DRGN1.BIN/263',
                 'scriptSha256':digest(script),'disassemblerWarningCount':0,
                 'bossKillCalls':[{'opcode':'CALL','functionIndex':172}]}]}
        path=self.repo/'docs/definitive/model-catalog/combatant-role-evidence.json'
        path.parent.mkdir(parents=True); path.write_text(json.dumps(proof))
        self.assertEqual(set(battle._boss_role_evidence(self.files,self.repo)),{262})
        proof['bossKillScripts'][0]['bossKillCalls'][0]['functionIndex']=171
        path.write_text(json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Unproved'):
            battle._boss_role_evidence(self.files,self.repo)
        script.write_bytes(b'changed synthetic script')
        with self.assertRaisesRegex(ValueError,'hash mismatch'):
            battle._boss_role_evidence(self.files,self.repo)


if __name__ == '__main__':
    unittest.main()
