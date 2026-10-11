#!/usr/bin/env python3
"""Reference preservation and fail-closed current-asset retention controls."""
from pathlib import Path
import importlib.util
import copy
import unittest

def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

gc=module('retention','prune-current-assets.py')
fixtures=module('fixture','test-current-publication.py')


class Retention(unittest.TestCase):
    def setUp(self):
        self.manifest,self.assets=fixtures.fixture()
        for asset in self.assets:asset['created_at']='2026-10-11T12:00:00Z'
        self.required={a['name']:dict(bytes=a['size'],sha256=a['digest'][7:]) for a in self.assets}
        self.aliases={}
        for name in gc.ALIASES:
            a=self.add(name,when='2026-10-11T12:01:00Z');self.aliases[name]=dict(bytes=a['size'],sha256=a['digest'][7:])
    def add(self,name,when='2026-10-10T12:00:00Z'):
        a=fixtures.asset(name,'a'*64,id=len(self.assets)+1);a['created_at']=when;self.assets.append(a);return a
    def plan(self):return gc.plan(self.manifest,self.assets,self.required,self.aliases)
    def test_keep_all_referenced_files_source_and_entrypoints_delete_old_only(self):
        old=self.add('file-'+'c'*64);self.add('Env1HD-v0.1.0.zip')
        self.add('source-fmvhd-'+'d'*64+'.zip')
        result=self.plan();self.assertEqual(3,len(result['remove']))
        self.assertIn(old['name'],[a['name'] for a in result['remove']])
        self.assertTrue(set(self.required).issubset(result['kept']));self.assertTrue(gc.ALIASES.issubset(result['kept']))
    def test_newer_candidate_and_unknown_asset_preserved(self):
        new=self.add('file-'+'b'*64,when='2026-10-11T13:00:00Z');unknown=self.add('author-work-in-progress.zip')
        equal=self.add('file-'+'c'*64,when='2026-10-11T12:00:00Z')
        result=self.plan();self.assertFalse(result['remove'])
        self.assertEqual({new['name'],unknown['name'],equal['name']},set(result['protected']))
    def test_any_missing_referenced_file_stops_all_deletion(self):
        self.add('file-'+'d'*64);self.assets=self.assets[1:]
        with self.assertRaises(ValueError):self.plan()
    def test_referenced_checksum_or_origin_mismatch_stops(self):
        for update in ({'digest':'sha256:'+'d'*64},{'browser_download_url':'https://wrong.invalid/file'}):
            prior=copy.deepcopy(self.assets);self.assets[0].update(update)
            with self.assertRaises(ValueError):self.plan()
            self.assets=prior
    def test_incomplete_bootstrap_or_unknown_candidate_chronology_stops(self):
        del self.aliases['SHA256SUMS']
        with self.assertRaises(ValueError):self.plan()
        self.setUp();candidate=self.add('file-'+'e'*64);del candidate['created_at']
        with self.assertRaises(ValueError):self.plan()


if __name__=='__main__':unittest.main()
