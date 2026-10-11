#!/usr/bin/env python3
"""Resume the initial FMV source rename with the same immutable CI receipt."""
from pathlib import Path
import importlib.util
import unittest
from current_publication import REPO, descriptor_name

spec=importlib.util.spec_from_file_location('stager',Path(__file__).with_name('stage-current-publication.py'))
stager=importlib.util.module_from_spec(spec);spec.loader.exec_module(stager)


class SourceReceipt(unittest.TestCase):
    def setUp(self):
        self.record=dict(name='FMVHD-v0.1.0-videos.zip',size=42,sha256='a'*64,disposition='reuse',releaseId=8,assetId=9)
        self.release=dict(id=8,tag_name='previous')
        name=descriptor_name('fmvSource',self.record['sha256'])
        self.asset=dict(id=9,name=name,size=42,digest='sha256:'+'a'*64,state='uploaded',browser_download_url=f'https://github.com/{REPO}/releases/download/previous/{name}')
    def test_original_ci_receipt_accepts_exact_renamed_asset_without_rebuild(self):
        self.assertEqual(self.asset,stager.reused_asset(self.record,{self.asset['name']:self.asset},self.release))
    def test_renamed_counterpart_must_preserve_every_identity_field(self):
        for change in ({'id':10},{'size':43},{'digest':'sha256:'+'b'*64},{'state':'starter'}):
            asset=dict(self.asset,**change)
            with self.assertRaises(ValueError):stager.reused_asset(self.record,{asset['name']:asset},self.release)
    def test_no_other_file_can_fall_back_to_a_different_asset_name(self):
        record=dict(self.record,name='Definitive-Installer.zip')
        with self.assertRaises(ValueError):stager.reused_asset(record,{self.asset['name']:self.asset},self.release)
    def test_arbitrary_reuse_name_rejected(self):
        with self.assertRaises(ValueError):stager.remote_name(dict(self.record,reuseName='other.zip'))


if __name__=='__main__':unittest.main()
