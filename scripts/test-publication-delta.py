#!/usr/bin/env python3
"""Offline behavioral checks for source-bound, missing-only publication artifacts."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest
from unittest.mock import patch
import zipfile

from package_validation import ROOT


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


delta = module('publication_delta', 'prepare-publication-delta.py')
assembler = module('file_assembler', 'assemble-file-delivery.py')


def digest(data):
    return hashlib.sha256(data).hexdigest()


class DeltaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.ctx = delta.context('a' * 40, 123, 2, 'owner/repo', 'linux-x64', 'definitive-current')
        self.data = b'shared bytes'
        self.blob = 'file-' + digest(self.data)
        self.expected = {self.blob: (len(self.data), digest(self.data)), 'Install-Definitive.sh': (3, digest(b'new'))}
        self.files = [dict(path='bundled-mods/Env1HD-v0.1.0.zip', sha256=digest(self.data), size=len(self.data), assetName=self.blob)]
        self.empty = dict(release=None, assets=[])

    def remote(self, name=None, data=None, **overrides):
        data = self.data if data is None else data
        asset = dict(id=88, name=name or self.blob, state='uploaded', digest='sha256:' + digest(data), size=len(data))
        asset.update(overrides)
        return dict(release=dict(id=7, tag_name='old-release', published_at='2026-10-11T00:00:00Z', draft=False, prerelease=False), assets=[asset])

    def test_no_current_full_stages_unique_blobs_without_environment_aliases(self):
        receipt = delta.plan(self.ctx, self.expected, self.files, self.empty)
        self.assertEqual(set(self.expected), {a['name'] for a in receipt['assets']})
        self.assertEqual(2, len(receipt['uploadPaths']))
        self.assertFalse(any(a['name'].startswith('Env1HD') for a in receipt['assets']))

    def test_exact_reuse_records_actual_server_asset_and_release(self):
        receipt = delta.plan(self.ctx, self.expected, self.files, self.remote())
        reused = next(a for a in receipt['assets'] if a['name'] == self.blob)
        self.assertEqual(('reuse', 88, 7), (reused['disposition'], reused['assetId'], reused['releaseId']))
        self.assertEqual(['uploads/Install-Definitive.sh'], receipt['uploadPaths'])

    def test_hash_named_corruption_never_becomes_a_reupload_or_reuse(self):
        for changes in (dict(size=1), dict(digest=None), dict(digest='sha256:' + 'b'*64), dict(state='starter'), dict(state=None)):
            with self.subTest(changes=changes), self.assertRaisesRegex(ValueError, 'immutable blob'):
                delta.plan(self.ctx, self.expected, self.files, self.remote(**changes))

    def test_mutable_entry_changes_are_delta_and_exact_entry_can_reuse(self):
        for data, expected in ((b'old', 'upload'), (b'new', 'reuse')):
            receipt = delta.plan(self.ctx, self.expected, self.files, self.remote('Install-Definitive.sh', data))
            self.assertEqual(expected, next(a for a in receipt['assets'] if a['name'] == 'Install-Definitive.sh')['disposition'])

    def test_canonical_fmv_reuse_binds_server_identity_without_upload(self):
        name=delta.FMV_SOURCE;sha=digest(self.data);canonical='source-fmvhd-'+sha+'.zip'
        metadata=self.remote(canonical)
        metadata['assets'].append(dict(self.remote(name)['assets'][0],id=99))
        receipt=delta.plan(self.ctx,{name:(len(self.data),sha)},[],metadata)
        self.assertEqual([],receipt['uploadPaths'])
        self.assertEqual([dict(name=name,size=len(self.data),sha256=sha,disposition='reuse',assetId=88,releaseId=7,reuseName=canonical)],receipt['assets'])
        delta.verify_linux(receipt,self.ctx)

    def test_initial_fmv_fixed_name_migration_retains_id_without_upload(self):
        name=delta.FMV_SOURCE;sha=digest(self.data)
        receipt=delta.plan(self.ctx,{name:(len(self.data),sha)},[],self.remote(name))
        self.assertEqual([],receipt['uploadPaths'])
        self.assertEqual([dict(name=name,size=len(self.data),sha256=sha,disposition='reuse',assetId=88,releaseId=7)],receipt['assets'])
        delta.verify_linux(receipt,self.ctx)
        fresh=delta.plan(self.ctx,{name:(len(self.data),sha)},[],self.empty)
        self.assertEqual(['uploads/'+name],fresh['uploadPaths'])

    def test_corrupt_canonical_fmv_cannot_fall_back_to_identical_fixed_name(self):
        name=delta.FMV_SOURCE;sha=digest(self.data);canonical='source-fmvhd-'+sha+'.zip'
        for changes in (dict(size=1),dict(digest=None),dict(digest='sha256:'+'b'*64),dict(state='starter')):
            metadata=self.remote(canonical,**changes)
            metadata['assets'].append(dict(self.remote(name)['assets'][0],id=99))
            with self.subTest(changes=changes),self.assertRaisesRegex(ValueError,'immutable blob'):delta.plan(self.ctx,{name:(len(self.data),sha)},[],metadata)

    def test_reuse_name_is_only_allowed_for_exact_canonical_reused_fmv(self):
        name=delta.FMV_SOURCE;sha=digest(self.data);canonical='source-fmvhd-'+sha+'.zip'
        receipt=delta.plan(self.ctx,{name:(len(self.data),sha)},[],self.remote(canonical))
        for changes in (dict(name=self.blob),dict(disposition='upload'),dict(reuseName=name),dict(reuseName='source-fmvhd-'+'b'*64+'.zip'),dict(reuseName=None)):
            altered=json.loads(json.dumps(receipt));altered['assets'][0].update(changes)
            with self.subTest(changes=changes),self.assertRaises(ValueError):delta.verify_linux(altered,self.ctx)
        stage=self.root/'stage';out=self.root/'receipt.json';delta.stage(receipt,{},stage,out)
        expected={name:(len(self.data),sha)}
        delta.verify_staged(receipt,self.ctx,expected,[],stage)
        receipt['assets'][0]['reuseName']=name
        with self.assertRaisesRegex(ValueError,'canonical FMV'):delta.verify_staged(receipt,self.ctx,expected,[],stage)

    def test_current_reference_is_closed_and_duplicate_names_rejected(self):
        for metadata in ({}, dict(release=None, assets=[{}]), dict(release={}, assets=[])):
            with self.subTest(metadata=metadata), self.assertRaises(ValueError):delta.current_assets(metadata)
        metadata = self.remote();metadata['assets'] *= 2
        with self.assertRaises(ValueError):delta.current_assets(metadata)
        metadata = self.remote();metadata['release']['draft'] = True
        with self.assertRaises(ValueError):delta.current_assets(metadata)

    def test_mac_excludes_shared_blob_assigned_to_linux(self):
        linux = delta.plan(self.ctx, self.expected, self.files, self.remote())
        ctx = dict(self.ctx, platform='macos-arm64')
        receipt = delta.plan(ctx, self.expected, self.files, self.remote(), linux)
        self.assertEqual([], receipt['uploadPaths'])
        self.assertTrue(all(a['disposition'] == 'linux' for a in receipt['assets']))

    def test_mac_requires_same_source_run_attempt_repo_tag_and_linux_platform(self):
        linux = delta.plan(self.ctx, self.expected, self.files, self.empty)
        ctx = dict(self.ctx, platform='macos-arm64')
        for key, value in [('sourceSha','b'*40),('runId',124),('runAttempt',3),('repository','other/repo'),('tag','other'),('platform','macos-arm64')]:
            with self.subTest(key=key), self.assertRaises(ValueError):delta.plan(ctx,self.expected,self.files,self.empty,dict(linux,**{key:value}))
        with self.assertRaises(ValueError):delta.plan(ctx,self.expected,self.files,self.empty)

    def test_common_source_path_change_fails_except_exact_native_binary(self):
        linux = delta.plan(self.ctx,self.expected,self.files,self.empty)
        data=b'changed';name='file-'+digest(data);expected={name:(len(data),digest(data))}
        for path in ['bundled-mods/Env1HD-v0.1.0.zip','lod-game.jar','libs/engine-natives-evil.jar','libs/engine-3.4.3-natives-linux.jar','libs/lwjgl-3.4.3-natives-windows.jar','libs/lwjgl-3.4.3-natives-linux-arm64.jar']:
            old=dict(self.files[0],path=path); linux['files']=[old]
            new=[dict(path=path,size=len(data),sha256=digest(data),assetName=name)]
            with self.subTest(path=path),self.assertRaisesRegex(ValueError,'Shared platform file'):delta.plan(dict(self.ctx,platform='macos-arm64'),expected,new,self.empty,linux)
        for path in ('tools/7zz','libs/lwjgl-sdl-3.4.3-natives-linux.jar','libs/lwjgl-3.4.3-natives-macos-arm64.jar'):
            linux['files']=[dict(self.files[0],path=path)]
            receipt=delta.plan(dict(self.ctx,platform='macos-arm64'),expected,[dict(path=path,size=len(data),sha256=digest(data),assetName=name)],self.empty,linux)
            self.assertEqual('upload',receipt['assets'][0]['disposition'])

    def test_same_asset_name_different_linux_size_fails_even_platform_binary(self):
        linux=delta.plan(self.ctx,self.expected,self.files,self.empty)
        expected=dict(self.expected);expected[self.blob]=(1,digest(self.data))
        with self.assertRaisesRegex(ValueError,'Same Linux asset'):delta.plan(dict(self.ctx,platform='macos-arm64'),expected,[],self.empty,linux)

    def test_malformed_linux_receipt_cannot_supply_reuse(self):
        linux=delta.plan(self.ctx,self.expected,self.files,self.remote())
        for modify in ('missing-id','duplicate','wrong-file'):
            altered=json.loads(json.dumps(linux))
            if modify=='missing-id':next(a for a in altered['assets'] if a['disposition']=='reuse').pop('assetId')
            elif modify=='duplicate':altered['assets'].append(altered['assets'][0])
            else:altered['files'][0]['size']=1
            with self.subTest(modify=modify),self.assertRaises(ValueError):delta.verify_linux(altered,self.ctx)

    def test_real_contents_inventory_and_actual_blob_hashes_bind_collector(self):
        output=self.root/'distributions';output.mkdir()
        payload={name:b'fixture' for name in ROOT|{'lod-game-fixture.jar','libs/library.jar','bundled-mods/UIHD.jar'}}
        payload['bundled-mods/Env1HD-v0.1.0.zip']=b'environment payload'
        hashes={name:digest(data) for name,data in payload.items()}
        metadata=dict(format='1',java='25',platform='macos-arm64',sourceRevision=self.ctx['sourceSha'],releaseTag=self.ctx['tag'],gameJar='lod-game-fixture.jar')
        canonical=''.join(k+'='+metadata[k]+'\n' for k in sorted(metadata))+''.join(k+'='+hashes[k]+'\n' for k in sorted(hashes))
        metadata['id']='alpha-'+digest(canonical.encode())[:16]
        package=output/'Legend-of-Dragoon-Definitive-macos-arm64.zip'
        with zipfile.ZipFile(package,'w') as archive:
            for name,data in payload.items():archive.writestr(name,data)
            archive.writestr('definitive-package.properties',''.join(k+'='+v+'\n' for k,v in metadata.items()))
            archive.writestr('definitive-files.properties',''.join(assembler.property_key(k)+'='+v+'\n' for k,v in hashes.items()))
        assembler.assemble(package,output)
        expected,files,paths=delta.collect(output,dict(self.ctx,platform='macos-arm64'))
        self.assertEqual(2,len([n for n in expected if n.startswith('file-')]))
        self.assertFalse(any(n.startswith('Env1HD') for n in expected))
        self.assertEqual(set(payload),{f['path'] for f in files})
        paths[next(n for n in paths if n.startswith('file-'))].write_bytes(b'corrupt')
        with self.assertRaises(ValueError):delta.collect(output,dict(self.ctx,platform='macos-arm64'))

    def test_stage_only_missing_bytes_and_preserve_existing_files(self):
        source=self.root/'blob';source.write_bytes(self.data)
        receipt=delta.plan(self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,self.empty)
        stage=self.root/'stage';out=self.root/'receipts'/'linux.json'
        delta.stage(receipt,{self.blob:source},stage,out)
        self.assertEqual(self.data,(stage/'uploads'/self.blob).read_bytes())
        self.assertEqual(receipt,json.loads(out.read_text()))
        delta.verify_staged(receipt,self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,stage)
        (stage/'uploads'/self.blob).write_bytes(b'changed after checks')
        with self.assertRaises(ValueError):delta.verify_staged(receipt,self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,stage)
        (stage/'uploads'/self.blob).write_bytes(self.data)
        with self.assertRaises(ValueError):delta.stage(receipt,{self.blob:source},stage,out)
        self.assertEqual(self.data,source.read_bytes())

    def test_stage_link_failure_removes_only_owned_stage_and_no_receipt(self):
        source=self.root/'blob';source.write_bytes(self.data)
        receipt=delta.plan(self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,self.empty)
        stage=self.root/'stage';out=self.root/'receipt.json'
        with patch.object(delta.os,'link',side_effect=OSError('disk full')),self.assertRaises(OSError):delta.stage(receipt,{self.blob:source},stage,out)
        self.assertFalse(stage.exists());self.assertFalse(out.exists());self.assertEqual(self.data,source.read_bytes())

    def test_linked_inputs_outputs_and_oversized_metadata_fail(self):
        source=self.root/'source';source.write_bytes(self.data);linked=self.root/'linked';linked.symlink_to(source)
        with self.assertRaises(ValueError):delta.identity(linked)
        directory=self.root/'directory';directory.mkdir();parent=self.root/'parent';parent.symlink_to(directory,target_is_directory=True)
        receipt=delta.plan(self.ctx,{},[],self.empty)
        with self.assertRaises(ValueError):delta.stage(receipt,{},parent/'stage',self.root/'receipt')
        metadata=self.root/'metadata';metadata.write_bytes(b'x'*100)
        with patch.object(delta,'MAX_METADATA',10),self.assertRaises(ValueError):delta.bounded_json(metadata)

    def test_api_latest404_is_only_absence_and_paginated_assets_are_bounded(self):
        with patch.object(delta,'api',return_value=None) as call:
            self.assertEqual(self.empty,delta.fetch_current('owner/repo'))
            self.assertEqual(True,call.call_args.kwargs['missing'])
        release=self.remote()['release']
        records=[dict(id=i+1,name='asset-'+str(i),state='uploaded',digest='sha256:'+'a'*64,size=1) for i in range(101)]
        with patch.object(delta,'api',side_effect=[release,records[:100],records[100:]]):
            self.assertEqual(101,len(delta.fetch_current('owner/repo')['assets']))
        with patch.object(delta,'api',side_effect=ValueError('403')), self.assertRaises(ValueError):
            delta.fetch_current('owner/repo')

    def api_bytes(self, data, status=0):
        real_popen = subprocess.Popen
        def fixture_process(command, **kwargs):
            return real_popen([sys.executable, '-c', 'import sys;sys.stdout.buffer.write('+repr(data)+');sys.exit('+str(status)+')'], **kwargs)
        return patch.object(delta.subprocess, 'Popen', side_effect=fixture_process)

    def test_actual_bounded_api_reader_accepts200_and_only404_absence(self):
        with self.api_bytes(b'HTTP/2.0 200 OK\r\nX-Test: fixture\r\n\r\n{"value":1}'):
            self.assertEqual({'value':1},delta.api('fixture'))
        with self.api_bytes(b'HTTP/2.0 404 Not Found\n\n{}',1):
            self.assertIsNone(delta.api('fixture',missing=True))
        for status in (401,403,500):
            with self.subTest(status=status),self.api_bytes(('HTTP/2.0 '+str(status)+' Error\n\n{}').encode(),1),self.assertRaises(ValueError):delta.api('fixture',missing=True)
        with self.api_bytes(b'HTTP/2.0 200 OK\n\n'+b'x'*100),patch.object(delta,'MAX_METADATA',30),self.assertRaisesRegex(ValueError,'exceeds bound'):delta.api('fixture')

    def test_receipt_handoff_checks_run_and_closed_download_contents(self):
        linux=delta.plan(self.ctx,self.expected,self.files,self.empty);ctx=dict(self.ctx,platform='macos-arm64')
        run=dict(head_sha=self.ctx['sourceSha'],run_attempt=2,status='in_progress')
        artifact=dict(total_count=1,artifacts=[dict(name='publication-linux-x64-receipt-2',expired=False,size_in_bytes=500)])
        destination=self.root/'download'
        def download(command,**kwargs):
            destination.mkdir();(destination/'publication-linux-x64.json').write_text(json.dumps(linux))
        with patch.object(delta,'api',side_effect=[run,artifact]),patch.object(delta.subprocess,'run',side_effect=download):
            self.assertEqual(linux,delta.download_linux(ctx,destination,0))
        bad_run=dict(run,run_attempt=1)
        with patch.object(delta,'api',return_value=bad_run),self.assertRaisesRegex(ValueError,'run/source'):delta.download_linux(ctx,self.root/'wrong-run',0)
        with patch.object(delta,'api',side_effect=[dict(run,status='completed'),dict(total_count=0,artifacts=[])]),self.assertRaisesRegex(ValueError,'did not arrive'):delta.download_linux(ctx,self.root/'absent',0)

    def test_partial_receipt_failure_removes_staged_links_and_owned_temporary(self):
        source=self.root/'blob';source.write_bytes(self.data)
        receipt=delta.plan(self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,self.empty)
        stage=self.root/'stage';out=self.root/'receipt.json'
        with patch.object(delta.os,'fsync',side_effect=OSError('disk full')),self.assertRaises(OSError):delta.stage(receipt,{self.blob:source},stage,out)
        self.assertFalse(stage.exists());self.assertFalse(out.exists());self.assertEqual([],list(self.root.glob('.publication-receipt-*')));self.assertEqual(self.data,source.read_bytes())

    def test_receipt_temporary_collision_preserves_foreign_sentinel(self):
        source=self.root/'blob';source.write_bytes(self.data)
        receipt=delta.plan(self.ctx,{self.blob:(len(self.data),digest(self.data))},self.files,self.empty)
        stage=self.root/'stage';out=self.root/'receipt.json'
        sentinel=self.root/'.publication-receipt-collision';sentinel.write_bytes(b'foreign receipt')
        with patch.object(delta.secrets,'token_hex',return_value='collision'),self.assertRaises(FileExistsError):delta.stage(receipt,{self.blob:source},stage,out)
        self.assertEqual(b'foreign receipt',sentinel.read_bytes());self.assertFalse(stage.exists());self.assertFalse(out.exists());self.assertEqual(self.data,source.read_bytes())


if __name__ == '__main__':
    unittest.main()
