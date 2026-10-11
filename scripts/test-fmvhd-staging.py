#!/usr/bin/env python3
"""Per-movie source reuse, failure isolation and exact output closure controls."""
import importlib.util
import io
import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

spec = importlib.util.spec_from_file_location('fmv_stage', Path(__file__).with_name('stage-fmvhd-assets.py'))
stager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stager)

class Staging(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.resources = self.root / 'resources'
        self.resources.mkdir()
        self.output = self.root / 'output'
        self.movies = {n: ('synthetic movie ' + n).encode() for n in stager.NAMES}
        self.props = {'format':'1', 'legacyBundleSha256':'a'*64}
        for name, key in [('NOTICE.txt','noticeSha256'),('provenance.json','provenanceSha256')]:
            payload = ('synthetic ' + name).encode()
            (self.resources/name).write_bytes(payload)
            self.props[key] = hashlib.sha256(payload).hexdigest()
        self.manifest = self.resources/'assets.properties'
        self.write_manifest()
        self.requests = []

    def write_manifest(self):
        for name, payload in self.movies.items():
            for key,value in dict(sha256=hashlib.sha256(payload).hexdigest(), sourceSha256=hashlib.sha256(('native '+name).encode()).hexdigest(), bytes=len(payload),frames=15,duration=1,width=1280,height=768,fps=15).items():
                self.props[name+'.'+key] = str(value)
        self.save()

    def save(self):
        self.manifest.write_text(''.join(k+'='+v+'\n' for k,v in sorted(self.props.items())))

    def opener(self, url, timeout):
        self.assertEqual(30, timeout)
        self.assertTrue(url.startswith(stager.CARRIER+'file-'))
        digest = url.removeprefix(stager.CARRIER+'file-')
        self.requests.append(digest)
        return io.BytesIO(next(b for b in self.movies.values() if hashlib.sha256(b).hexdigest()==digest))

    def stage(self):
        with mock.patch.object(stager.urllib.request,'build_opener') as builder:
            builder.return_value.open.side_effect = self.opener
            return stager.stage(self.manifest,self.output)

    def test_clean_build_individual_carriers_and_code_only_zero_requests(self):
        self.output.mkdir() # Gradle precreates its declared output directory.
        self.assertEqual(18,self.stage()['films'])
        self.assertEqual(18,len(self.requests))
        self.requests.clear()
        self.stage()
        self.assertEqual([],self.requests)
        self.assertEqual(21,len([p for p in self.output.rglob('*') if p.is_file()]))
        self.assertFalse(any(p.suffix in ('.zip','.jar') for p in self.output.rglob('*')))

    def test_one_film_change_and_corrupt_or_missing_repair_request_one(self):
        self.stage()
        self.movies['OPENH'] += b' changed'
        self.write_manifest()
        self.requests.clear()
        self.stage()
        self.assertEqual([self.props['OPENH.sha256']],self.requests)
        film = self.output/'videos'/(self.props['OPENH.sha256']+'.mp4')
        film.write_bytes(b'corrupt')
        self.requests.clear()
        self.stage()
        self.assertEqual([self.props['OPENH.sha256']],self.requests)
        # An incomplete generated tree is refused; it is not safe to erase.
        film.unlink()
        self.requests.clear()
        with self.assertRaises(ValueError): self.stage()
        self.assertEqual([],self.requests)

    def test_shared_payload_retains_18_names_but_fetches_17_objects(self):
        self.movies['DEMOH'] = self.movies['OPENH']
        self.write_manifest()
        self.stage()
        self.assertEqual(17,len(self.requests))
        self.assertNotEqual(self.props['OPENH.sourceSha256'],self.props['DEMOH.sourceSha256'])
        self.props['DEMOH.bytes'] = '1'
        self.save()
        with self.assertRaises(ValueError): stager.contract(self.manifest)

    def test_truncated_oversized_wrong_hash_preserve_previous_complete_tree(self):
        self.stage()
        before = {str(p.relative_to(self.output)):p.read_bytes() for p in self.output.rglob('*') if p.is_file()}
        self.movies['OPENH'] += b' changed'
        self.write_manifest()
        for bad in (b'',b'x'*100,b'Z'*len(self.movies['OPENH'])):
            with mock.patch.object(stager.urllib.request,'build_opener') as builder:
                builder.return_value.open.return_value = io.BytesIO(bad)
                with self.assertRaises(ValueError): stager.stage(self.manifest,self.output)
            self.assertEqual(before,{str(p.relative_to(self.output)):p.read_bytes() for p in self.output.rglob('*') if p.is_file()})
            self.assertEqual([],list(self.root.glob('.fmv-*')))

    def test_unknown_content_links_duplicate_or_missing_manifest_fail_before_network(self):
        self.stage()
        unknown = self.output/'owner-save'
        unknown.write_text('keep me')
        with self.assertRaises(ValueError): self.stage()
        self.assertEqual('keep me',unknown.read_text())
        unknown.unlink()
        target = self.output/'videos'/(self.props['OPENH.sha256']+'.mp4')
        target.unlink(); target.symlink_to(self.manifest)
        with self.assertRaises(ValueError): self.stage()
        self.manifest.write_text(self.manifest.read_text()+'OPENH.bytes=1\n')
        with self.assertRaises(ValueError): stager.contract(self.manifest)

    def test_local_zip_requires_pin_complete_inventory_and_exact_source_identity(self):
        archive = self.root/'local.zip'
        def make(extra=False):
            with zipfile.ZipFile(archive,'w') as z:
                legacy={k:v for k,v in self.props.items() if k.endswith(('.sha256','.sourceSha256','.bytes'))}
                z.writestr('fmvhd/assets.properties',''.join(k+'='+v+'\n' for k,v in sorted(legacy.items())))
                for name in ('NOTICE.txt','provenance.json'): z.writestr('fmvhd/'+name,(self.resources/name).read_bytes())
                for name,payload in self.movies.items(): z.writestr('fmvhd/videos/'+name+'.mp4',payload)
                if extra: z.writestr('../unsafe',b'original/private')
            self.props['legacyBundleSha256']=hashlib.sha256(archive.read_bytes()).hexdigest();self.save()
        make()
        with mock.patch.object(stager.urllib.request,'build_opener') as builder:
            stager.stage(self.manifest,self.output,archive)
            builder.assert_not_called()
        make(True)
        with self.assertRaises(ValueError): stager.stage(self.manifest,self.output,archive)
        self.props['legacyBundleSha256']='0'*64;self.save()
        with self.assertRaises(ValueError): stager.stage(self.manifest,self.output,archive)

if __name__=='__main__': unittest.main()
