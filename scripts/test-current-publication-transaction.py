#!/usr/bin/env python3
"""Failure and replay controls for the current-release publication transaction."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import tempfile
import unittest
from current_publication import REPO, TAG, PLATFORMS, descriptor_name, digest, parse_body

spec=importlib.util.spec_from_file_location('publisher',Path(__file__).with_name('publish-current-release.py'))
publisher=importlib.util.module_from_spec(spec);spec.loader.exec_module(publisher)


class Server:
    def __init__(self):
        self.record=dict(id=77,tag_name='previous-test',body='Previous complete download',draft=False,prerelease=False)
        self.files={};self.events=[];self.fail=None;self.next_id=100;self.inventory_reads=0
    def release(self):return copy.deepcopy(self.record)
    def assets(self):
        self.inventory_reads+=1
        return copy.deepcopy(list(self.files.values()))
    def add(self,name,data):
        self.next_id+=1
        self.files[name]=dict(id=self.next_id,name=name,size=len(data),digest='sha256:'+hashlib.sha256(data).hexdigest(),state='uploaded',browser_download_url=f'https://github.com/{REPO}/releases/download/{self.record["tag_name"]}/{name}')
    def upload(self,path):
        self.events.append(('upload',path.name))
        if self.fail==path.name:raise OSError('injected upload failure')
        self.add(path.name,path.read_bytes())
    def rename(self,identifier,name):
        old=next(n for n,a in self.files.items() if a['id']==identifier)
        asset=self.files.pop(old);asset['name']=name
        asset['browser_download_url']=f'https://github.com/{REPO}/releases/download/{self.record["tag_name"]}/{name}'
        self.files[name]=asset;self.events.append(('rename',old,name))
    def replace(self,name,path,asset):
        self.files.pop(name,None);self.upload(path)
    def commit(self,manifest,text):
        self.events.append(('commit',manifest['generation']))
        if self.fail=='commit':raise OSError('injected commit failure')
        self.record.update(tag_name=TAG,body=text)
        for a in self.files.values():a['browser_download_url']=f'https://github.com/{REPO}/releases/download/{TAG}/{a["name"]}'


class Transaction(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);(self.root/'uploads').mkdir()
        self.server=Server();self.inputs=dict(format=1,result='STAGED',tag=TAG,source='a'*40,run=18,runAttempt=1,
            baselineRelease=publisher.snapshot(self.server.release()),immutable={},aliases={},descriptors={},sourceRename=None)
        for platform in PLATFORMS:
            for kind in ('package','contents'):self.descriptor(kind,platform)
        for kind in ('installer','entry','fmvSource'):self.descriptor(kind)
        self.payload('file-'+hashlib.sha256(b'unchanged movie').hexdigest(),b'unchanged movie',existing=True)
        self.alias('Install-Definitive.desktop',b'latest desktop')
        self.alias('Install-Definitive.sh',b'new bootstrap')
    def tearDown(self):self.tmp.cleanup()
    def payload(self,name,data,existing=False):
        path=self.root/'uploads'/name
        self.inputs['immutable'][name]=dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        if existing:self.server.add(name,data)
        else:path.write_bytes(data);self.inputs['immutable'][name]['path']=name
        return name
    def descriptor(self,kind,platform=None):
        data=(kind+str(platform)).encode();name=descriptor_name(kind,hashlib.sha256(data).hexdigest(),platform)
        self.payload(name,data)
        self.inputs['descriptors'][(platform+'.' if platform else '')+kind]=name
    def alias(self,name,data):
        (self.root/'uploads'/name).write_bytes(data)
        self.inputs['aliases'][name]=dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),path=name)
    def run_publish(self):return publisher.publish(self.inputs,self.root,self.server,'2026-10-11T12:00:00Z')
    def test_pointer_commits_last_and_unchanged_movie_never_uploads(self):
        result=self.run_publish();self.assertEqual(('commit',1),self.server.events[-1])
        self.assertEqual('PASS',result['result']);self.assertEqual(1,parse_body(self.server.record['body'])['generation'])
        movies=[n for n in self.inputs['immutable'] if n.startswith('file-')]
        self.assertTrue(all(n in result['reused'] for n in movies));self.assertFalse(any(n in result['uploaded'] for n in movies))
    def test_interrupted_upload_keeps_previous_pointer_and_retry_reuses(self):
        self.server.fail=self.inputs['descriptors']['installer'];old=self.server.record['body']
        with self.assertRaises(OSError):self.run_publish()
        self.assertEqual(old,self.server.record['body']);already=set(self.server.files)
        self.server.fail=None;result=self.run_publish()
        self.assertTrue(already.issubset(result['reused']));self.assertEqual(1,result['manifest']['generation'])
    def test_failure_during_alias_keeps_old_pointer(self):
        old=self.server.record['body'];self.server.fail='SHA256SUMS'
        with self.assertRaises(OSError):self.run_publish()
        self.assertEqual(old,self.server.record['body']);self.assertFalse(any(e[0]=='commit' for e in self.server.events))
    def test_repeat_exact_publication_is_zero_upload_zero_generation_bump(self):
        first=self.run_publish();self.inputs['baselineRelease']=publisher.snapshot(self.server.release())
        self.server.events.clear();second=self.run_publish()
        self.assertTrue(second['unchanged']);self.assertFalse(second['uploaded']);self.assertFalse(self.server.events)
        self.assertEqual(first['manifest'],second['manifest'])
    def test_new_build_same_carrier_increments_generation_reuses_movie_id(self):
        first=self.run_publish();movie=next(n for n in self.inputs['immutable'] if n.startswith('file-'))
        original_id=self.server.files[movie]['id'];self.inputs['baselineRelease']=publisher.snapshot(self.server.release())
        self.inputs.update(source='b'*40,run=19);second=self.run_publish()
        self.assertEqual(2,second['manifest']['generation']);self.assertEqual(original_id,self.server.files[movie]['id'])
        self.assertNotIn(movie,second['uploaded'])
    def test_corrupt_immutable_metadata_stops_without_clobber(self):
        movie=next(n for n in self.inputs['immutable'] if n.startswith('file-'));self.server.files[movie]['size']+=1
        with self.assertRaises(ValueError):self.run_publish()
        self.assertNotIn(('upload',movie),self.server.events);self.assertEqual('Previous complete download',self.server.record['body'])
    def test_source_rename_preserves_id_and_skips_video_upload(self):
        name=self.inputs['descriptors']['fmvSource'];data=(self.root/'uploads'/name).read_bytes()
        self.server.add('FMVHD-v0.1.0-videos.zip',data);identifier=self.server.files['FMVHD-v0.1.0-videos.zip']['id']
        self.inputs['sourceRename']=dict(id=identifier,oldName='FMVHD-v0.1.0-videos.zip',name=name,**{k:self.inputs['immutable'][name][k] for k in ('bytes','sha256')})
        result=self.run_publish();self.assertEqual(identifier,self.server.files[name]['id']);self.assertNotIn(name,result['uploaded'])
    def test_changed_baseline_or_staged_bytes_prevent_any_mutation(self):
        self.server.record['body']='Someone published newer'
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(self.server.events)
        self.inputs['baselineRelease']=publisher.snapshot(self.server.release())
        (self.root/'uploads'/self.inputs['descriptors']['entry']).write_bytes(b'changed')
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(self.server.events)
    def test_concurrent_revision_mid_upload_stops_before_commit(self):
        upload=self.server.upload
        def race(path):upload(path);self.server.record['body']='Concurrent complete build'
        self.server.upload=race
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(any(e[0]=='commit' for e in self.server.events))
    def test_missing_reused_file_never_advances_manifest(self):
        movie=next(n for n in self.inputs['immutable'] if n.startswith('file-'));del self.server.files[movie]
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(any(e[0]=='commit' for e in self.server.events))
    def test_reuse_inventory_reads_do_not_scale_with_unchanged_files(self):
        for number in range(550):
            data=('unchanged '+str(number)).encode()
            self.payload('file-'+hashlib.sha256(data).hexdigest(),data,existing=True)
        self.run_publish()
        self.assertLess(self.server.inventory_reads,25)
    def test_reused_file_removed_during_alias_upload_prevents_commit(self):
        movie=next(n for n in self.inputs['immutable'] if n.startswith('file-'))
        upload=self.server.upload
        def remove(path):
            upload(path)
            if path.name=='SHA256SUMS':del self.server.files[movie]
        self.server.upload=remove
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(any(e[0]=='commit' for e in self.server.events))
    def test_reused_file_changed_during_alias_upload_prevents_commit(self):
        movie=next(n for n in self.inputs['immutable'] if n.startswith('file-'))
        upload=self.server.upload
        def corrupt(path):
            upload(path)
            if path.name=='SHA256SUMS':self.server.files[movie]['digest']='sha256:'+'f'*64
        self.server.upload=corrupt
        with self.assertRaises(ValueError):self.run_publish()
        self.assertFalse(any(e[0]=='commit' for e in self.server.events))
    def test_local_lock_is_shared_across_different_staging_directories(self):
        with publisher.publication_lock():
            with self.assertRaises(BlockingIOError):publisher.publication_lock()
        with publisher.publication_lock():pass


if __name__=='__main__':unittest.main()
