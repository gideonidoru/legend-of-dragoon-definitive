#!/usr/bin/env python3
"""Exact-wire, metadata, recipe and failure controls; optional private Env replay."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import threading
import unittest
from unittest import mock
import warnings
import zipfile

import archive_assembly as assembly


def identity(path):
    data = path.read_bytes()
    return assembly.FileIdentity(path, hashlib.sha256(data).hexdigest(), len(data))


class ForwardOnly(io.BytesIO):
    def seek(self, *args):
        raise io.UnsupportedOperation('forward only')


def write_zip(path, values, descriptor=False, force64=False, comment=b''):
    stream = ForwardOnly() if descriptor else io.BytesIO()
    with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in values:
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            with archive.open(info, 'w', force_zip64=force64) as output:
                output.write(data)
        archive.comment = comment
    path.write_bytes(stream.getvalue())
    return identity(path)


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old = write_zip(self.root/'old.zip', [('same', b'a'*5000), ('changed', b'old')])
        self.new = write_zip(self.root/'new.zip', [('same', b'a'*5000), ('changed', b'new'), ('added', b'pixels')])

    def plan(self, directory='bundles', targets=None, baselines=None):
        return assembly.plan_files(targets or {'bundled-mods/Env1HD.zip':self.new},
                                   baselines if baselines is not None else {self.old.sha256:self.old}, self.root/directory)

    def recipe(self):
        plan = self.plan()
        return assembly.bind_recipe(plan, 'a'*40, 'macos-arm64', 'definitive-current',
                                    {sha:str(i+1) for i,sha in enumerate(sorted(plan.sources))})

    def mutate(self, change):
        data = bytearray(self.new.path.read_bytes()); change(data)
        self.new.path.write_bytes(data)
        return identity(self.new.path)

    def test_real_compressed_bytes_are_reused_and_final_sha_exact(self):
        plan = self.plan()
        rows = plan.files['bundled-mods/Env1HD.zip']
        self.assertTrue(any(r['source'] == self.old.sha256 for r in rows))
        proof = assembly.prove_plan(plan, {self.old.sha256:self.old, **plan.uploads})
        self.assertEqual(self.new.sha256, proof['bundled-mods/Env1HD.zip']['sha256'])
        self.assertLess(sum(x.bytes for x in plan.uploads.values()), self.new.bytes)

    def test_noop_has_no_upload_and_preserves_whole_bytes(self):
        plan = self.plan(targets={'bundled-mods/control.jar':self.old})
        self.assertFalse(plan.uploads)
        self.assertEqual(self.old.bytes, sum(r['bytes'] for r in plan.files['bundled-mods/control.jar']))

    def test_identical_new_spans_are_stored_once_across_files(self):
        plan = self.plan(targets={'a.jar':self.new, 'b.jar':self.new}, baselines={})
        self.assertEqual(self.new.bytes, sum(x.bytes for x in plan.uploads.values()))
        self.assertEqual(plan.files['a.jar'], plan.files['b.jar'])

    def test_previous_recipe_reuses_raw_bundles_across_generations(self):
        first=self.plan();sources={self.old.sha256:self.old,**first.uploads}
        ids={sha:str(i+1) for i,sha in enumerate(sorted(first.sources))}
        recipe=assembly.bind_recipe(first,'a'*40,'macos-arm64','definitive-current',ids)
        catalog=assembly.verified_recipe_ranges(recipe,sources,ids)
        noop=assembly.plan_files({'mod.jar':self.new},sources,self.root/'next',catalog)
        self.assertFalse(noop.uploads)
        third=write_zip(self.root/'third.zip',[('same',b'a'*5000),('changed',b'next'),('added',b'pixels')])
        plan=assembly.plan_files({'mod.jar':third},sources,self.root/'third-bundles',catalog)
        reused={row['source'] for row in plan.files['mod.jar'] if row['source'] in sources}
        self.assertEqual(set(sources),reused)
        self.assertEqual(third.sha256,assembly.prove_plan(plan,{**sources,**plan.uploads})['mod.jar']['sha256'])

    def test_prior_recipe_rejects_rebound_ids_and_source_sizes(self):
        plan=self.plan();sources={self.old.sha256:self.old,**plan.uploads}
        ids={sha:str(i+1) for i,sha in enumerate(sorted(plan.sources))}
        recipe=assembly.bind_recipe(plan,'a'*40,'macos-arm64','definitive-current',ids)
        wrong=dict(ids);wrong[self.old.sha256]='777'
        with self.assertRaisesRegex(ValueError,'source binding'):
            assembly.verified_recipe_ranges(recipe,sources,wrong)
        with self.assertRaisesRegex(ValueError,'inventory is not closed'):
            assembly.verified_recipe_ranges(recipe,sources,{})
        wrong_recipe=copy.deepcopy(recipe)
        next(iter(wrong_recipe['files'].values()))[0]['sha256']='f'*64
        with self.assertRaisesRegex(ValueError,'range SHA-256'):
            assembly.verified_recipe_ranges(wrong_recipe,sources,ids)
        sources[self.old.sha256]=self.new
        with self.assertRaisesRegex(ValueError,'source binding'):
            assembly.verified_recipe_ranges(recipe,sources,ids)

    def test_prior_catalog_hash_offsets_overlap_and_unbound_source_rejected(self):
        row=dict(offset=0,bytes=1,sha256='f'*64)
        with self.assertRaisesRegex(ValueError,'range SHA-256'):
            assembly.plan_files({'mod.jar':self.new},{self.old.sha256:self.old},self.root/'bad',
                                {self.old.sha256:[row]})
        for changed in [dict(row,offset=-1),dict(row,bytes=assembly.MAX_SPAN_BYTES+1),dict(row,extra=True)]:
            with self.subTest(changed=changed),self.assertRaises(ValueError):
                assembly.plan_files({'mod.jar':self.new},{self.old.sha256:self.old},self.root/'bad',
                                    {self.old.sha256:[changed]})
        with self.assertRaisesRegex(ValueError,'not bound'):
            assembly.plan_files({'mod.jar':self.new},{},self.root/'bad',{'f'*64:[row]})
        with self.assertRaisesRegex(ValueError,'overlapping'):
            assembly.plan_files({'mod.jar':self.new},{self.old.sha256:self.old},self.root/'bad',
                                {self.old.sha256:[dict(row,bytes=3),dict(row,offset=1)]})

    def test_bundle_determinism_and_safe_retry(self):
        first = self.plan(); second = self.plan(); third = self.plan(directory='other')
        self.assertEqual(first.files, second.files); self.assertEqual(first.sources, third.sources)
        self.assertEqual(set(first.uploads), set(third.uploads))

    def test_changed_source_pin_blocks_all_bundle_output(self):
        bad = assembly.FileIdentity(self.new.path, 'f'*64, self.new.bytes)
        with self.assertRaisesRegex(ValueError, 'SHA-256 differs'):
            self.plan(targets={'mod.jar':bad})
        self.assertFalse(list((self.root/'bundles').iterdir()))

    def test_baseline_key_cannot_rebind_source(self):
        with self.assertRaisesRegex(ValueError, 'Baseline source identity'):
            self.plan(baselines={'f'*64:self.old})

    def test_symlink_source_is_rejected(self):
        link = self.root/'link.jar'; link.symlink_to(self.new.path)
        bad = assembly.FileIdentity(link,self.new.sha256,self.new.bytes)
        with self.assertRaises(OSError): self.plan(targets={'mod.jar':bad})

    def test_existing_corrupt_bundle_is_never_overwritten(self):
        plan = self.plan(); upload = next(iter(plan.uploads.values()))
        upload.path.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'size or file type'):
            self.plan()
        self.assertEqual(b'corrupt',upload.path.read_bytes())

    def test_failure_removes_only_files_created_by_this_plan(self):
        first = self.plan(); existing = {x.path:x.path.read_bytes() for x in first.uploads.values()}
        with mock.patch.object(assembly, 'prove_plan', side_effect=ValueError('injected proof failure')):
            with self.assertRaisesRegex(ValueError, 'proof failure'): self.plan()
        for path, data in existing.items(): self.assertEqual(data,path.read_bytes())
        self.assertFalse(list((self.root/'bundles').glob('.archive-bundle-*')))

    def test_failed_new_plan_retires_its_created_bundle(self):
        with mock.patch.object(assembly, 'prove_plan', side_effect=ValueError('injected proof failure')):
            with self.assertRaisesRegex(ValueError, 'proof failure'): self.plan()
        self.assertFalse(list((self.root/'bundles').iterdir()))

    def test_failed_concurrent_proof_cannot_remove_successful_plan_bundle(self):
        entered = threading.Event(); release = threading.Event(); errors = []
        original = assembly.prove_plan
        def prove(plan, sources):
            if threading.current_thread().name == 'failing-plan':
                entered.set()
                if not release.wait(5): raise AssertionError('Concurrent proof did not finish')
                raise ValueError('injected proof failure')
            return original(plan, sources)
        def failing():
            try: self.plan()
            except BaseException as error: errors.append(error)
        with mock.patch.object(assembly, 'prove_plan', side_effect=prove):
            thread = threading.Thread(target=failing, name='failing-plan')
            thread.start()
            try:
                self.assertTrue(entered.wait(5))
                succeeded = self.plan()
                expected = {item.path:item.path.read_bytes() for item in succeeded.uploads.values()}
            finally:
                release.set(); thread.join(5)
            self.assertFalse(thread.is_alive())
        self.assertEqual(1,len(errors)); self.assertIsInstance(errors[0],ValueError)
        for path, data in expected.items(): self.assertEqual(data,path.read_bytes())
        self.assertFalse(list((self.root/'bundles').glob('.archive-bundle-*')))

    def test_later_link_failure_retains_already_published_immutable_bundle(self):
        real_link = assembly.os.link; calls = []
        def link(source, destination):
            if calls: raise OSError('injected later publication failure')
            real_link(source,destination); calls.append(Path(destination))
        with mock.patch.object(assembly, 'MAX_BUNDLE_BYTES', 50), mock.patch.object(assembly.os, 'link', side_effect=link):
            with self.assertRaisesRegex(OSError, 'later publication failure'): self.plan()
        self.assertEqual(1,len(calls))
        content=calls[0].read_bytes()
        self.assertEqual('file-'+hashlib.sha256(content).hexdigest(),calls[0].name)
        self.assertFalse(list((self.root/'bundles').glob('.archive-bundle-*')))

    def test_aggregate_baseline_span_budget_is_bounded(self):
        other = write_zip(self.root/'other.zip', [('one',b'one'),('two',b'two')])
        with mock.patch.object(assembly, 'MAX_SPANS', 5):
            with self.assertRaisesRegex(ValueError, 'Baseline span inventory'):
                self.plan(baselines={self.old.sha256:self.old,other.sha256:other})
        self.assertFalse(list((self.root/'bundles').iterdir()))

    def test_mutated_bundle_blocks_roundtrip(self):
        plan = self.plan(); item = next(iter(plan.uploads.values()))
        data = bytearray(item.path.read_bytes()); data[0] ^= 1; item.path.write_bytes(data)
        with self.assertRaisesRegex(ValueError, 'SHA-256 differs'):
            assembly.prove_plan(plan,{self.old.sha256:self.old, **plan.uploads})

    def test_roundtrip_rejects_wrong_final_identity_and_source_identity(self):
        plan = self.plan(); sources = {self.old.sha256:self.old, **plan.uploads}
        wrong = assembly.AssemblyPlan(plan.sources,plan.files,plan.uploads,
                                     {'bundled-mods/Env1HD.zip':assembly.FileIdentity(self.new.path,'f'*64,self.new.bytes)})
        with self.assertRaisesRegex(ValueError, 'managed inventory'): assembly.prove_plan(wrong,sources)
        sources[self.old.sha256] = self.new
        with self.assertRaisesRegex(ValueError, 'source identity differs'): assembly.prove_plan(plan,sources)
        sources={sha:item for sha,item in plan.uploads.items()}
        sources['f'*64]=self.new
        with self.assertRaisesRegex(ValueError, 'source files are missing'): assembly.prove_plan(plan,sources)

    def test_signed_and_unsigned_descriptors_are_validated(self):
        for force64 in (False, True):
            value = write_zip(self.root/'descriptor.zip',[('one',b'data'*1000)],True,force64)
            self.assertEqual(value.bytes,sum(n for _,n in assembly.archive_ranges(value)))
        # Omit the optional signature while preserving both central geometry and
        # exact descriptor CRC/sizes; offsets before the directory remain fixed.
        value = write_zip(self.root/'descriptor.zip',[('one',b'data'*1000)],True)
        data=bytearray(value.path.read_bytes());at=data.find(b'PK\x07\x08');del data[at:at+4]
        end=len(data)-22;directory=struct.unpack_from('<I',data,end+16)[0];struct.pack_into('<I',data,end+16,directory-4)
        value.path.write_bytes(data);value=identity(value.path)
        self.assertEqual(value.bytes,sum(n for _,n in assembly.archive_ranges(value)))

    def test_descriptor_crc_mismatch_is_rejected(self):
        value=write_zip(self.new.path,[('one',b'data'*1000)],True)
        data=bytearray(value.path.read_bytes());at=data.find(b'PK\x07\x08');data[at+4]^=1;value.path.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'descriptor identity'):assembly.archive_ranges(identity(value.path))

    def test_local_zip64_sizes_and_full_zip64_tail(self):
        value=write_zip(self.new.path,[('one',b'data'*1000)],force64=True)
        self.assertEqual(value.bytes,sum(n for _,n in assembly.archive_ranges(value)))
        data=bytearray(value.path.read_bytes());end=len(data)-22
        _,_,_,per_disk,count,size,offset,_=struct.unpack_from('<4s4H2IH',data,end)
        extended=struct.pack('<4sQ2H2I4Q',b'PK\x06\x06',44,45,45,0,0,per_disk,count,size,offset)
        locator=struct.pack('<4sIQI',b'PK\x06\x07',0,end,1)
        ordinary=bytearray(data[end:]);struct.pack_into('<2H2I',ordinary,8,65535,65535,0xffffffff,0xffffffff)
        value.path.write_bytes(data[:end]+extended+locator+ordinary);value=identity(value.path)
        self.assertEqual(value.bytes,sum(n for _,n in assembly.archive_ranges(value)))

    def test_zip64_locator_and_local_extra_truncation_rejected(self):
        value=write_zip(self.new.path,[('one',b'data'*1000)],force64=True)
        data=bytearray(value.path.read_bytes());names=struct.unpack_from('<H',data,26)[0]
        struct.pack_into('<H',data,30+names+2,100);value.path.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'extra field exceeds'):assembly.archive_ranges(identity(value.path))
        self.new=write_zip(self.new.path,[('one',b'data')]);bad=self.mutate(lambda b:struct.pack_into('<2H',b,len(b)-22+8,65535,65535))
        with self.assertRaisesRegex(ValueError,'ZIP64 locator'):assembly.archive_ranges(bad)

    def test_valid_comment_with_end_signature_is_preserved(self):
        value=write_zip(self.new.path,[('one',b'data')],comment=b'comment-PK\x05\x06-suffix')
        plan=self.plan(targets={'mod.jar':value},baselines={})
        self.assertEqual(value.bytes,sum(s['bytes'] for s in plan.files['mod.jar']))

    def test_directory_budget_checked_before_metadata_objects(self):
        bad=self.mutate(lambda b:struct.pack_into('<I',b,len(b)-22+12,assembly.MAX_DIRECTORY_BYTES+1))
        with mock.patch.object(assembly.zipfile,'ZipFile',side_effect=AssertionError('unchecked allocation')):
            with self.assertRaisesRegex(ValueError,'allocation budget'):assembly.archive_ranges(bad)

    def test_directory_count_budget_checked_before_metadata_objects(self):
        bad=self.mutate(lambda b:struct.pack_into('<2H',b,len(b)-22+8,1,1))
        with mock.patch.object(assembly.zipfile,'ZipFile',side_effect=AssertionError('unchecked allocation')):
            with self.assertRaisesRegex(ValueError,'record count'):assembly.archive_ranges(bad)

    def test_duplicate_names_offsets_and_crossing_payload_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore'); value=write_zip(self.new.path,[('same',b'one'),('same',b'two')])
        with self.assertRaisesRegex(ValueError,'Duplicate ZIP names'):assembly.archive_ranges(value)
        self.new=write_zip(self.new.path,[('one',b'data'),('two',b'data')])
        def duplicate(b):
            first=b.find(b'PK\x01\x02');second=b.find(b'PK\x01\x02',first+4);struct.pack_into('<I',b,second+42,0)
        with self.assertRaisesRegex(ValueError,'duplicate offsets'):assembly.archive_ranges(self.mutate(duplicate))
        self.new=write_zip(self.new.path,[('one',b'data')])
        with self.assertRaisesRegex(ValueError,'payload exceeds'):
            assembly.archive_ranges(self.mutate(lambda b:struct.pack_into('<I',b,b.find(b'PK\x01\x02')+20,100000)))

    def test_local_name_crc_header_and_tail_rejected(self):
        for change,message in [(lambda b:b.__setitem__(30,ord('X')),'name differs'),
                               (lambda b:b.__setitem__(14,b[14]^1),'CRC or tail'),
                               (lambda b:b.__setitem__(0,ord('X')),'header is missing')]:
            self.new=write_zip(self.new.path,[('one',b'data')]);bad=self.mutate(change)
            with self.subTest(message=message),self.assertRaisesRegex(ValueError,message):
                assembly.archive_ranges(bad,True)
        self.new=write_zip(self.new.path,[('one',b'data')])
        raw=self.new.path.read_bytes();self.new.path.write_bytes(raw[:-1])
        with self.assertRaisesRegex(ValueError,'truncated ZIP end'):assembly.archive_ranges(identity(self.new.path))
        self.new.path.write_bytes(raw+b'junk')
        with self.assertRaisesRegex(ValueError,'truncated ZIP end'):assembly.archive_ranges(identity(self.new.path))

    def test_unknown_bytes_between_payload_and_directory_rejected(self):
        self.new=write_zip(self.new.path,[('one',b'data')]);data=bytearray(self.new.path.read_bytes());end=len(data)-22;directory=struct.unpack_from('<I',data,end+16)[0]
        data[directory:directory]=b'x';struct.pack_into('<I',data,len(data)-22+16,directory+1);self.new.path.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'CRC or tail'):assembly.archive_ranges(identity(self.new.path))

    def test_large_raw_record_and_generic_file_have_bounded_exact_spans(self):
        with zipfile.ZipFile(self.new.path,'w',compression=zipfile.ZIP_STORED) as z:z.writestr('large',b'x'*(assembly.MAX_SPAN_BYTES+1))
        value=identity(self.new.path);ranges=assembly.archive_ranges(value)
        self.assertTrue(all(n<=assembly.MAX_SPAN_BYTES for _,n in ranges));self.assertEqual(value.bytes,sum(n for _,n in ranges))
        raw=self.root/'generic.bin';raw.write_bytes(b'x'*(assembly.MAX_SPAN_BYTES+1));value=identity(raw)
        self.assertEqual([(0,assembly.MAX_SPAN_BYTES),(assembly.MAX_SPAN_BYTES,1)],assembly.archive_ranges(value))

    def test_empty_archive_is_exact_and_multidisk_rejected(self):
        value=write_zip(self.new.path,[]);self.assertEqual([(0,22)],assembly.archive_ranges(value))
        self.new=value
        with self.assertRaisesRegex(ValueError,'Multi-disk'):
            assembly.archive_ranges(self.mutate(lambda b:struct.pack_into('<H',b,len(b)-22+4,1)))

    def test_recipe_roundtrip_and_closed_source_ids(self):
        recipe=self.recipe();data=json.dumps(recipe).encode()
        self.assertEqual(recipe,assembly.load_recipe(data))
        plan=self.plan()
        with self.assertRaisesRegex(ValueError,'close planned sources'):
            assembly.bind_recipe(plan,'a'*40,'linux-x64','definitive-current',{})

    def test_recipe_source_id_size_and_schema_controls(self):
        original=self.recipe();sha=next(iter(original['sources']))
        for key,value in [('id','01'),('id',True),('id','9007199254740992'),('bytes',0),('bytes',True),('bytes',2**31)]:
            bad=copy.deepcopy(original);bad['sources'][sha][key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):assembly.validate_recipe(bad)
        for key,value in [('format',True),('sourceRevision','a'*39),('platform','other'),('releaseTag','old')]:
            bad=copy.deepcopy(original);bad[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):assembly.validate_recipe(bad)
        bad=copy.deepcopy(original);bad['extra']=True
        with self.assertRaises(ValueError):assembly.validate_recipe(bad)

    def test_recipe_duplicate_ids_missing_unused_sources(self):
        original=self.recipe();keys=list(original['sources']);self.assertGreater(len(keys),1)
        bad=copy.deepcopy(original);bad['sources'][keys[1]]['id']=bad['sources'][keys[0]]['id']
        with self.assertRaisesRegex(ValueError,'duplicate recipe asset ID'):assembly.validate_recipe(bad)
        bad=copy.deepcopy(original);del bad['sources'][keys[0]]
        with self.assertRaisesRegex(ValueError,'bound source'):assembly.validate_recipe(bad)
        bad=copy.deepcopy(original);bad['sources']['f'*64]=dict(id='777',bytes=1)
        with self.assertRaisesRegex(ValueError,'unreferenced sources'):assembly.validate_recipe(bad)

    def test_recipe_path_traversal_and_file_directory_overlap(self):
        original=self.recipe();rows=next(iter(original['files'].values()))
        for name in ['../mod.jar','/mod.jar','a//b','a/./b','a/../b','a\\b','a:b','a\x00b']:
            bad=copy.deepcopy(original);bad['files']={name:rows}
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'managed path'):assembly.validate_recipe(bad)
        bad=copy.deepcopy(original);bad['files']={'mod':rows,'mod/inside':rows}
        with self.assertRaisesRegex(ValueError,'paths overlap'):assembly.validate_recipe(bad)

    def test_recipe_offset_length_hash_and_overlap_controls(self):
        original=self.recipe();name=next(iter(original['files']))
        for key,value in [('offset',-1),('offset',True),('offset',2**63),('bytes',0),('bytes',True),('bytes',assembly.MAX_SPAN_BYTES+1),('sha256','A'*64),('source','f'*64)]:
            bad=copy.deepcopy(original);bad['files'][name][0][key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):assembly.validate_recipe(bad)
        bad=copy.deepcopy(original);span=copy.deepcopy(bad['files'][name][0]);span['offset']+=1;span['bytes']-=1;bad['files'][name].append(span)
        with self.assertRaisesRegex(ValueError,'overlapping source spans'):assembly.validate_recipe(bad)

    def test_duplicate_json_and_oversized_recipe_rejected(self):
        with self.assertRaisesRegex(ValueError,'Duplicate recipe JSON'):
            assembly.load_recipe(b'{"format":1,"format":1}')
        with self.assertRaisesRegex(ValueError,'wire budget'):
            assembly.load_recipe(b' '*(assembly.MAX_RECIPE_BYTES+1))
        with self.assertRaisesRegex(ValueError,'Malformed recipe JSON'):
            assembly.load_recipe(b'['*10000+b']'*10000)

    def test_range_and_identity_bounds(self):
        for offset,length in [(-1,1),(0,0),(True,1),(0,self.new.bytes+1)]:
            with self.subTest(offset=offset,length=length),self.assertRaises(ValueError):
                assembly.range_digest(self.new,offset,length)
        for size in [0,True,2**31]:
            with self.assertRaises(ValueError):assembly.FileIdentity(self.new.path,self.new.sha256,size)


def real_env(root, output, baseline=None):
    root,output=Path(root),Path(output)
    inputs=json.loads((root/'publication-inputs.json').read_text())
    descriptor=inputs['descriptors']['macos-arm64.contents'];record=inputs['immutable'][descriptor]
    contents=assembly.verify_file(assembly.FileIdentity(root/'uploads'/descriptor,record['sha256'],record['bytes']))
    with zipfile.ZipFile(contents.path) as archive:
        new_props=dict(l.split('=',1) for l in archive.read('definitive-files.properties').decode().splitlines() if '=' in l)
    prior=Path(baseline) if baseline else root/'public-update-qa/releases/alpha-fbcecd1cce8fcc25'
    old_props=dict(l.split('=',1) for l in (prior/'definitive-files.properties').read_text().splitlines() if '=' in l)
    targets,baselines={},{}
    for number in range(1,5):
        name=f'bundled-mods/Env{number}HD-v0.1.0.zip'
        old,new=prior/name,root/'uploads'/('file-'+new_props[name])
        baselines[old_props[name]]=assembly.FileIdentity(old,old_props[name],old.stat().st_size)
        targets[name]=assembly.FileIdentity(new,new_props[name],inputs['immutable'][new.name]['bytes'])
    plan=assembly.plan_files(targets,baselines,output/'uploads')
    recipe=assembly.bind_recipe(plan,inputs['source'],'macos-arm64','definitive-current',
                                {sha:str(i+1) for i,sha in enumerate(sorted(plan.sources))})
    proof=assembly.prove_plan(plan,{**baselines,**plan.uploads})
    report=dict(kind='private-exact-wire-archive-replay',files=proof,
                baselineManagedPropertiesSha256=hashlib.sha256((prior/'definitive-files.properties').read_bytes()).hexdigest(),
                baselineSources={sha:dict(bytes=source.bytes) for sha,source in baselines.items()},
                wholeFileBytes=sum(x.bytes for x in targets.values()),
                newUploadBytes=sum(x.bytes for x in plan.uploads.values()),newUploadSources=len(plan.uploads),
                orderedSpans=sum(len(x) for x in plan.files.values()),
                reusedSpans=sum(r['source'] in baselines for rows in plan.files.values() for r in rows),
                reusedBytes=sum(r['bytes'] for rows in plan.files.values() for r in rows if r['source'] in baselines),
                idsAreSyntheticFixtureBindings=True,productionPublished=False)
    (output/'recipe-fixture.json').write_text(json.dumps(recipe,sort_keys=True,separators=(',',':'))+'\n')
    (output/'proof.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--real-env',type=Path);parser.add_argument('--output',type=Path)
    parser.add_argument('--baseline',type=Path)
    args,remaining=parser.parse_known_args()
    result=unittest.main(argv=['test-archive-assembly.py',*remaining],exit=False).result
    if not result.wasSuccessful():raise SystemExit(1)
    if args.real_env:
        if args.output is None:parser.error('--real-env requires a private --output directory')
        real_env(args.real_env,args.output,args.baseline)
