#!/usr/bin/env python3
"""Headless controls for the temporary immutable format-1 source bridge."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('retain', Path(__file__).with_name('retain-fmvhd-source.py'))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)

class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve(); self.bytes = b'approved custom movie source'
        self.sha = hashlib.sha256(self.bytes).hexdigest()
        self.lock = self.root / 'lock'; self.manifest = self.root / 'manifest'
        self.lock.write_text('sha256='+self.sha+'\nurl=https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/source-fmvhd-'+self.sha+'.zip\n')
        self.manifest.write_text('legacyBundleSha256='+self.sha+'\n')
        self.local = self.root / 'local.zip'; self.local.write_bytes(self.bytes)
        self.output = self.root / 'build' / 'videos.zip'
    def run_bridge(self, **kwargs):
        return module.retain(self.lock,self.manifest,self.output,**kwargs)
    def test_local_export_and_verified_cache_reuse(self):
        self.assertFalse(self.run_bridge(local=self.local)['reused'])
        self.assertTrue(self.run_bridge(opener=lambda *a, **k: self.fail('cache contacted network'))['reused'])
        self.assertEqual(self.bytes,self.output.read_bytes())
    def test_bad_local_preserves_prior_generated_output_and_cleans_temporary(self):
        self.output.parent.mkdir(); self.output.write_bytes(b'old'); self.local.write_bytes(b'bad')
        with self.assertRaises(ValueError): self.run_bridge(local=self.local)
        self.assertEqual(b'old',self.output.read_bytes()); self.assertEqual([],list(self.output.parent.glob('.fmv-source-*')))
    def test_pin_mismatch_fails_before_transfer(self):
        self.manifest.write_text('legacyBundleSha256='+'0'*64+'\n')
        with self.assertRaises(ValueError): self.run_bridge(local=self.local)
        self.assertFalse(self.output.exists())
    def test_linked_output_preserves_owner(self):
        self.output.parent.mkdir(); self.output.symlink_to(self.local)
        with self.assertRaises(ValueError): self.run_bridge(local=self.local)
        self.assertEqual(self.bytes,self.local.read_bytes())
    def test_linked_earlier_ancestor_preserves_foreign_sentinel(self):
        foreign = self.root / 'foreign'; (foreign / 'fmvhd').mkdir(parents=True)
        sentinel = foreign / 'fmvhd' / 'videos.zip'; sentinel.write_bytes(b'owner sentinel')
        (self.root / 'build').symlink_to(foreign, target_is_directory=True)
        self.output = self.root / 'build' / 'fmvhd' / 'videos.zip'
        with self.assertRaises(ValueError): self.run_bridge(local=self.local)
        self.assertEqual(b'owner sentinel',sentinel.read_bytes())
        self.assertEqual([],list(foreign.rglob('.fmv-source-*')))
    def test_linked_ancestor_rejected_before_creating_foreign_directories(self):
        foreign = self.root / 'foreign'; foreign.mkdir()
        (self.root / 'build').symlink_to(foreign, target_is_directory=True)
        self.output = self.root / 'build' / 'new' / 'fmvhd' / 'videos.zip'
        with self.assertRaises(ValueError): self.run_bridge(local=self.local)
        self.assertEqual([],list(foreign.iterdir()))
    def test_remote_stream_is_hash_verified(self):
        def fetch(request, timeout):
            self.assertEqual('identity',request.get_header('Accept-encoding'))
            response=io.BytesIO(self.bytes); response.headers={}; return response
        self.assertFalse(self.run_bridge(opener=fetch)['reused'])
        self.assertEqual(self.bytes,self.output.read_bytes())
    def test_bad_remote_hash_or_encoding_never_publishes(self):
        for data,headers in ((b'wrong',{}),(self.bytes,{'Content-Encoding':'gzip'})):
            with self.subTest(headers=headers):
                def fetch(*args,**kwargs):
                    response=io.BytesIO(data); response.headers=headers; return response
                with self.assertRaises(ValueError): self.run_bridge(opener=fetch)
                self.assertFalse(self.output.exists()); self.assertEqual([],list(self.output.parent.glob('.fmv-source-*')))
    def test_unpinned_carrier_is_rejected(self):
        self.lock.write_text('sha256='+self.sha+'\nurl=https://example.com/source.zip\n')
        with self.assertRaises(ValueError): self.run_bridge(opener=lambda *a,**k:self.fail('unsafe URL fetched'))
    def test_https_downgrade_is_rejected(self):
        with self.assertRaises(ValueError): module.HttpsRedirect().redirect_request(None,None,302,'',{},'http://example.com')

if __name__ == '__main__': unittest.main()
