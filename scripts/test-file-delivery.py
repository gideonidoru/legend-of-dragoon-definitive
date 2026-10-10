#!/usr/bin/env python3
"""Exercise file inventory generation, deduplication and publication binding."""
import importlib.util
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile
from package_validation import ROOT, file_delivery_assets

spec = importlib.util.spec_from_file_location('assembler', Path(__file__).with_name('assemble-file-delivery.py'))
assembler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assembler)

class FileDeliveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / 'package.zip'
        self.output = self.root / 'output'
        self.payload = {name: b'shared fixture' for name in ROOT | {'lod-game-fixture.jar', 'libs/library.jar', 'bundled-mods/FMVHD-v0.1.0.jar'}}
        self.payload['gfx/path with spaces.txt'] = b'different content'
        hashes = {name: hashlib.sha256(data).hexdigest() for name, data in self.payload.items()}
        metadata = {'format': '1', 'java': '25', 'platform': 'linux-x64', 'sourceRevision': 'a'*40, 'releaseTag': 'fixture', 'gameJar': 'lod-game-fixture.jar'}
        canonical = ''.join(k+'='+metadata[k]+'\n' for k in sorted(metadata)) + ''.join(k+'='+hashes[k]+'\n' for k in sorted(hashes))
        metadata['id'] = 'alpha-' + hashlib.sha256(canonical.encode()).hexdigest()[:16]
        with zipfile.ZipFile(self.package, 'w') as archive:
            for name, data in self.payload.items(): archive.writestr(name, data)
            archive.writestr('definitive-package.properties', ''.join(k+'='+v+'\n' for k,v in metadata.items()))
            archive.writestr('definitive-files.properties', ''.join(assembler.property_key(k)+'='+v+'\n' for k,v in hashes.items()))
    def test_generated_inventory_binds_every_length_and_deduplicates_blobs(self):
        contents = assembler.assemble(self.package, self.output)
        assets = file_delivery_assets(self.package, contents)
        self.assertEqual(2, len(assets))
        self.assertEqual(set(assets), {path.name for path in (self.output/'files').iterdir()})
        for name,(size,digest) in assets.items():
            data = (self.output/'files'/name).read_bytes()
            self.assertEqual((len(data),hashlib.sha256(data).hexdigest()), (size,digest))
        assembler.assemble(self.package, self.output)
    def test_corrupt_existing_blob_fails_closed(self):
        assembler.assemble(self.package,self.output)
        blob = next((self.output/'files').iterdir()); blob.write_bytes(b'wrong')
        with self.assertRaises(ValueError): assembler.assemble(self.package,self.output)
    def test_documents_above_client_limit_cannot_be_published(self):
        contents = assembler.assemble(self.package,self.output)
        with zipfile.ZipFile(contents) as source: documents={n:source.read(n) for n in source.namelist()}
        documents['definitive-sizes.properties'] += b'#'+b'x'*(4*1024**2)
        with zipfile.ZipFile(contents,'w',compression=zipfile.ZIP_DEFLATED) as target:
            for name,data in documents.items(): target.writestr(name,data)
        with self.assertRaises(ValueError): file_delivery_assets(self.package,contents)
    def test_manifest_cannot_be_substituted_from_another_package(self):
        contents = assembler.assemble(self.package,self.output)
        with zipfile.ZipFile(contents) as source:
            documents = {n:source.read(n) for n in source.namelist()}
        documents['definitive-sizes.properties'] = documents['definitive-sizes.properties'].replace(b'=14\n',b'=15\n')
        with zipfile.ZipFile(contents,'w') as target:
            for name,data in documents.items():target.writestr(name,data)
        with self.assertRaises(ValueError): file_delivery_assets(self.package,contents)

if __name__ == '__main__': unittest.main()
