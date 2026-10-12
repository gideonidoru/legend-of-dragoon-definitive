#!/usr/bin/env python3
"""Production-format activation, exact-byte delta and transitive GC controls."""
from pathlib import Path
import copy
import hashlib
import importlib.util
import io
import tempfile
import unittest
import zipfile
from unittest.mock import patch
import member_publication as member
from current_publication import (canonical, body, descriptor, descriptor_name,
    PLATFORMS, TAG, digest, closure, check_assembly_references, parse_body)
from package_validation import ROOT
from archive_assembly import load_recipe, range_digest, FileIdentity


def module(name, filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

publisher=module('publisher_member','publish-current-release.py')
delta=module('delta_member','prepare-publication-delta.py')
gc=module('gc_member','prune-current-assets.py')
assembler=module('assembler_member','assemble-file-delivery.py')
transaction=module('transaction_member','test-current-publication-transaction.py')
fixture=module('fixture_member','test-current-publication.py')
film_stage=module('film_stage_member','stage-current-fmvhd-assets.py')


def sha(data):return hashlib.sha256(data).hexdigest()


def archive(changed=False):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_STORED) as z:
        for name,data in [('stable',bytes(range(256))*4500),('changed',b'new content' if changed else b'old content'),('other',b'another stable entry')]:
            z.writestr(zipfile.ZipInfo(name,(2026,10,11,0,0,0)),data)
    return stream.getvalue()


class Server(transaction.Server):
    def __init__(self):super().__init__();self.data={}
    def add(self,name,data):
        super().add(name,data);self.data[name]=data
        self.files[name]['created_at']='2026-10-10T12:00:00Z'
    def upload(self,path):
        super().upload(path)
        self.files[path.name]['created_at']='2026-10-11T12:00:00Z'


class MemberPublication(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve();(self.root/'uploads').mkdir();self.server=Server()
        self.server.record.update(tag_name=TAG,published_at='2026-10-10T00:00:00Z')
        manifest,assets=fixture.fixture();manifest['releaseId']='77'
        self.old=archive();self.new=archive(True);self.archive_path='bundled-mods/Env1HD-v0.1.0.zip'
        self.old_name='file-'+sha(self.old);self.server.add(self.old_name,self.old)
        old_contents={}
        for platform in PLATFORMS:
            stream=io.BytesIO()
            with zipfile.ZipFile(stream,'w') as z:
                z.writestr('definitive-package.properties',f'platform={platform}\nsourceRevision='+manifest['sourceRevision']+'\nreleaseTag='+TAG+'\n')
                z.writestr('definitive-files.properties',self.archive_path+'='+sha(self.old)+'\n')
                z.writestr('definitive-sizes.properties',self.archive_path+'='+str(len(self.old))+'\n')
            data=stream.getvalue();name=descriptor_name('contents',sha(data),platform);self.server.add(name,data)
            manifest['platforms'][platform]['contents']=descriptor(self.server.files[name],'contents',platform)
        self.server.record['body']=body(manifest)
        self.manifest_path,self.payload=self.payloads()
        self.inputs=dict(format=1,result='STAGED',tag=TAG,source='a'*40,run=18,runAttempt=1,
            baselineRelease=publisher.snapshot(self.server.release()),immutable={},aliases={},descriptors={},
            deliveryFormat=2,assemblyPlans={},receipts={},fmvSources=None,sourceRename=None)
        self.plans={};self.receipts={}
        for platform in PLATFORMS:self.stage_platform(platform)
        self.fixed('installer',b'installer fixture');self.fixed('entry',b'entry fixture')
        for name in gc.ALIASES-{'SHA256SUMS'}:
            data=('alias '+name).encode();(self.root/'uploads'/name).write_bytes(data)
            self.inputs['aliases'][name]=dict(bytes=len(data),sha256=sha(data),path=name)
    def payloads(self):
        result={name:b'shared fixture' for name in ROOT|{'lod-game-fixture.jar','libs/library.jar','bundled-mods/FMVHD-v0.1.0.jar'}}
        result[self.archive_path]=self.new;rows={'format':'1','legacyBundleSha256':'a'*64,'noticeSha256':sha(b'notice'),'provenanceSha256':sha(b'{}')}
        (self.root/'NOTICE.txt').write_bytes(b'notice');(self.root/'provenance.json').write_bytes(b'{}')
        for film in member.fmv_contract.NAMES:
            data=('movie '+('DEMOH' if film=='DEMO2' else film)).encode();h=sha(data);result['gfx/fmvhd/videos/'+h+'.mp4']=data
            for field,value in dict(sha256=h,sourceSha256=sha(('native '+film).encode()),bytes=str(len(data)),frames='15',duration='1',width='1280',height='768',fps='15').items():rows[film+'.'+field]=value
        data=''.join(k+'='+v+'\n' for k,v in sorted(rows.items())).encode();path=self.root/'film.properties';path.write_bytes(data)
        result['gfx/fmvhd/manifest.properties']=data
        result['gfx/fmvhd/NOTICE.txt']=b'notice';result['gfx/fmvhd/provenance.json']=b'{}'
        return path,result
    def fetch(self,asset):
        path=self.root/'cache'/asset['name'];path.parent.mkdir(exist_ok=True)
        if not path.exists():path.write_bytes(self.server.data[asset['name']])
        return path
    def stage_platform(self,platform):
        directory=self.root/platform;directory.mkdir()
        package=directory/('Legend-of-Dragoon-Definitive-'+platform+'.zip')
        hashes={name:sha(data) for name,data in self.payload.items()}
        metadata=dict(format='1',java='25',platform=platform,sourceRevision=self.inputs['source'],releaseTag=TAG,gameJar='lod-game-fixture.jar')
        text=''.join(k+'='+v+'\n' for k,v in sorted(metadata.items()))+''.join(k+'='+v+'\n' for k,v in sorted(hashes.items()))
        metadata['id']='alpha-'+sha(text.encode())[:16]
        with zipfile.ZipFile(package,'w') as z:
            for name,data in self.payload.items():z.writestr(name,data)
            z.writestr('definitive-package.properties',''.join(k+'='+v+'\n' for k,v in metadata.items()))
            z.writestr('definitive-files.properties',''.join(assembler.property_key(k)+'='+v+'\n' for k,v in hashes.items()))
        with patch('builtins.print'):contents=assembler.assemble(package,directory,thin_release=True,archive_limit=0)
        files=[dict(path=name,sha256=hashes[name],size=len(data),assetName='file-'+hashes[name]) for name,data in sorted(self.payload.items())]
        expected={row['assetName']:(row['size'],row['sha256']) for row in files}
        paths={name:directory/'files'/name for name in expected}
        ctx=delta.context(self.inputs['source'],18,1,member.fmv_contract.CARRIER.split('/')[3]+'/'+member.fmv_contract.CARRIER.split('/')[4],platform,TAG)
        current=dict(release=self.server.release(),assets=self.server.assets())
        expected,paths,record,draft=member.prepare(ctx,expected,files,paths,current,directory/'bundles',self.fetch)
        self.plans[platform]=draft
        receipt=delta.plan(ctx,expected,files,current,self.receipts.get('linux-x64'))
        receipt.update(deliveryFormat=2,assemblyPlan=record,fmvSources=member.film_map(self.manifest_path,files))
        self.receipts[platform]=receipt
        self.inputs['receipts'][platform]=receipt;self.inputs['assemblyPlans'][platform]=dict(record,path=record['name'])
        self.inputs['fmvSources']=receipt['fmvSources']
        for name,(size,h) in expected.items():
            destination=self.root/'uploads'/name
            if not destination.exists():destination.write_bytes(paths[name].read_bytes())
            if name!=record['name']:
                self.inputs['immutable'][name]=dict(bytes=size,sha256=h,path=name)
                if name in self.server.files:self.inputs['immutable'][name]['id']=str(self.server.files[name]['id'])
        for kind,path in [('package',package),('contents',contents)]:
            name=descriptor_name(kind,digest(path),platform);data=path.read_bytes()
            (self.root/'uploads'/name).write_bytes(data);self.inputs['immutable'][name]=dict(bytes=len(data),sha256=sha(data),path=name)
            self.inputs['descriptors'][platform+'.'+kind]=name
    def fixed(self,kind,data):
        name=descriptor_name(kind,sha(data));(self.root/'uploads'/name).write_bytes(data)
        self.inputs['immutable'][name]=dict(bytes=len(data),sha256=sha(data),path=name);self.inputs['descriptors'][kind]=name
    def publish(self):return publisher.publish(self.inputs,self.root,self.server,'2026-10-11T12:00:00Z')
    def test_member_delta_uploads_only_changed_archive_bytes_and_commits_exact_format2_last(self):
        result=self.publish();self.assertEqual(2,result['manifest']['format']);self.assertEqual(('commit',2),self.server.events[-1])
        self.assertNotIn('file-'+sha(self.new),result['closure'])
        self.assertIn(self.old_name,result['closure']);self.assertNotIn(self.old_name,result['uploaded'])
        changed=[size for name,size in result['uploaded'].items() if name.startswith('file-') and name in {'file-'+h for recipe in result['recipes'].values() for h in recipe['sources']}]
        self.assertLess(sum(changed),len(self.new)//100)
        self.assertNotIn('fmvSource',result['manifest']);self.assertEqual(18,len(result['manifest']['fmvSources']));self.assertEqual(17,len({row['id'] for row in result['manifest']['fmvSources'].values()}))
        for recipe in result['recipes'].values():
            rebuilt=b''.join(self.server.data['file-'+row['source']][row['offset']:row['offset']+row['bytes']] for row in recipe['files'][self.archive_path])
            self.assertEqual(self.new,rebuilt)
        self.assertEqual(result['manifest'],parse_body(self.server.record['body']))
    def test_repeat_exact_generation_does_not_upload_or_advance(self):
        first=self.publish();self.inputs['baselineRelease']=publisher.snapshot(self.server.release());self.server.events.clear()
        second=self.publish();self.assertTrue(second['unchanged']);self.assertEqual({},second['uploaded']);self.assertEqual([],self.server.events)
        self.assertEqual(first['manifest'],second['manifest'])
    def test_second_generation_reuses_transitive_bundle_sources(self):
        first=self.publish()
        current=dict(release=self.server.release(),assets=self.server.assets())
        old,ranges=member.baselines(current,'linux-x64',{self.archive_path},self.fetch)
        self.assertIn(sha(self.old),old);self.assertGreater(len(old),1)
        from archive_assembly import plan_files
        target=self.root/'approved.zip';target.write_bytes(self.new)
        plan=plan_files({self.archive_path:FileIdentity(target,sha(self.new),len(self.new))},old,self.root/'next-bundles',ranges)
        self.assertEqual({},plan.uploads)
        self.assertEqual(set(first['recipes']['linux-x64']['sources']),set(plan.sources))
    def test_gc_preserves_old_baseline_and_collects_only_unreferenced_old_sources(self):
        result=self.publish();old='file-'+sha(b'obsolete archive');self.server.add(old,b'obsolete archive')
        retained=gc.plan(result['manifest'],self.server.assets(),result['closure'],result['aliases'],result['recipes'])
        self.assertIn(self.old_name,retained['kept']);self.assertIn(old,{row['name'] for row in retained['remove']})
        broken=copy.deepcopy(result['closure']);del broken[self.old_name]
        with self.assertRaisesRegex(ValueError,'Transitive'):gc.plan(result['manifest'],self.server.assets(),broken,result['aliases'],result['recipes'])
    def test_missing_or_changed_transitive_source_prevents_gc(self):
        result=self.publish()
        for field,value in [('id',99999),('size',1),('digest','sha256:'+'0'*64),('state','starter'),('browser_download_url','https://other.invalid/file')]:
            assets=copy.deepcopy(self.server.assets());next(row for row in assets if row['name']==self.old_name)[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):gc.plan(result['manifest'],assets,result['closure'],result['aliases'],result['recipes'])
    def test_bundle_failure_leaves_original_pointer_and_retry_reuses_uploaded_objects(self):
        old=self.server.record['body'];new_source=next('file-'+h for h in self.plans['linux-x64']['sources'] if 'file-'+h!=self.old_name)
        self.server.fail=new_source
        with self.assertRaises(OSError):self.publish()
        self.assertEqual(old,self.server.record['body']);self.server.fail=None;self.publish()
    def test_wrong_baseline_id_or_corrupt_raw_source_fails_planning(self):
        current=dict(release=self.server.release(),assets=self.server.assets())
        target=next(row for row in current['assets'] if row['name']==self.old_name);target['size']+=1
        with self.assertRaises(ValueError):member.baselines(current,'linux-x64',{self.archive_path},self.fetch)
        current=dict(release=self.server.release(),assets=self.server.assets());self.fetch(self.server.files[self.old_name]).write_bytes(b'corrupt')
        with self.assertRaises(ValueError):
            old,ranges=member.baselines(current,'linux-x64',{self.archive_path},self.fetch)
            from archive_assembly import plan_files
            path=self.root/'new.zip';path.write_bytes(self.new)
            plan_files({self.archive_path:FileIdentity(path,sha(self.new),len(self.new))},old,self.root/'bad',ranges)
    def test_source_removed_during_alias_upload_prevents_pointer_commit(self):
        old=self.server.record['body'];upload=self.server.upload
        def race(path):
            upload(path)
            if path.name=='SHA256SUMS':self.server.files.pop(self.old_name)
        self.server.upload=race
        with self.assertRaises(ValueError):self.publish()
        self.assertEqual(old,self.server.record['body'])
    def test_corrupt_or_wrong_source_recipe_stops_before_upload(self):
        original=self.server.record['body'];platform='linux-x64';record=self.inputs['assemblyPlans'][platform]
        path=self.root/'uploads'/record['name'];draft=copy.deepcopy(self.plans[platform]);draft['files'][self.archive_path][0]['sha256']='0'*64
        data=canonical(draft);new_name='assembly-plan-'+platform+'-'+sha(data)+'.json';path=self.root/'uploads'/new_name;path.write_bytes(data)
        self.inputs['assemblyPlans'][platform]=dict(name=new_name,path=new_name,sha256=sha(data),bytes=len(data))
        with self.assertRaises(ValueError):self.publish()
        self.assertEqual([],self.server.events);self.assertEqual(original,self.server.record['body'])
    def test_film_source_disagreement_stops_before_upload(self):
        film=member.fmv_contract.NAMES[0];self.inputs['fmvSources'][film]['sha256']='0'*64
        with self.assertRaises(ValueError):self.publish()
        self.assertEqual([],self.server.events)
    def test_wire_bounds_geometry_duplicates_and_noncanonical_plan_fail(self):
        draft=self.plans['linux-x64'];files=self.receipts['linux-x64']['files']
        for change in ('offset','size','source','platform','extra','omit'):
            changed=copy.deepcopy(draft);row=changed['files'][self.archive_path][0]
            if change=='offset':row['offset']=2**63
            if change=='size':row['bytes']=16*1024**2+1
            if change=='source':row['source']='0'*64
            if change=='platform':changed['platform']='macos-arm64'
            if change=='extra':changed['unknown']=1
            if change=='omit':changed['files']={}
            with self.subTest(change=change),self.assertRaises(ValueError):member.load_plan(canonical(changed),self.inputs['source'],'linux-x64',files)
        with self.assertRaises(ValueError):member.load_plan(canonical(draft).replace(b'"format":1',b'"format":1,"format":1'),self.inputs['source'],'linux-x64',files)
        with self.assertRaises(ValueError):member.load_plan(canonical(draft)+b'\n',self.inputs['source'],'linux-x64',files)
    def test_current_carrier_budget_fails_without_pointer_commit(self):
        for i in range(1000-len(self.server.files)):self.server.add('unknown-'+str(i),str(i).encode())
        old=self.server.record['body']
        with self.assertRaisesRegex(ValueError,'budget'):self.publish()
        self.assertEqual(old,self.server.record['body'])
    def test_clean_film_staging_works_before_and_after_format2_with_17_verified_objects(self):
        for path,data in self.payload.items():
            if path.startswith('gfx/fmvhd/videos/'):self.server.add('file-'+sha(data),data)
        for version in (1,2):
            if version==2:self.publish()
            current=dict(release=self.server.release(),assets=self.server.assets());reads=[]
            def fetch(asset,directory):
                reads.append(asset['id']);path=directory/asset['name'];path.write_bytes(self.server.data[asset['name']]);return path
            with patch('builtins.print'):
                result=film_stage.stage(self.manifest_path,self.root/('film-output-'+str(version)),lambda:current,fetch)
            self.assertEqual(17,len(reads));self.assertEqual(18,result['films'])
            self.assertEqual(17,len(list((self.root/('film-output-'+str(version))/'videos').iterdir())))
    def test_build_film_api_bindings_and_concurrent_pointer_fail_closed(self):
        for path,data in self.payload.items():
            if path.startswith('gfx/fmvhd/videos/'):self.server.add('file-'+sha(data),data)
        current=dict(release=self.server.release(),assets=self.server.assets());contract=member.fmv_contract.contract(self.manifest_path)
        for field,value in [('size',1),('digest','sha256:'+'0'*64),('state','starter'),('browser_download_url','https://other.invalid/file')]:
            bad=copy.deepcopy(current);next(row for row in bad['assets'] if row['name']=='file-'+contract['OPENH.sha256'])[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):film_stage.sources(contract,bad)
        next_current=copy.deepcopy(current);pointer=parse_body(next_current['release']['body']);pointer['generation']+=1;next_current['release']['body']=body(pointer)
        snapshots=iter((current,next_current))
        def fetch(asset,directory):
            path=directory/asset['name'];path.write_bytes(self.server.data[asset['name']]);return path
        with self.assertRaisesRegex(ValueError,'changed'):
            film_stage.stage(self.manifest_path,self.root/'no-partial-film-output',lambda:next(snapshots),fetch)
        self.assertFalse((self.root/'no-partial-film-output').exists())
        self.assertFalse(list(self.root.glob('.current-film-inputs-*')))
    def test_receipt_inventory_and_split_source_need_no_legacy_zip(self):
        for platform in PLATFORMS:
            expected,files,paths=delta.collect(self.root/platform,delta.context(self.inputs['source'],18,1,'gideonidoru/legend-of-dragoon-definitive',platform,TAG),include_fmv_source=False) if platform!='linux-x64' else ({},[],{})
            self.assertNotIn(delta.FMV_SOURCE,expected)
        delta.verify_linux(self.receipts['linux-x64'],delta.context(self.inputs['source'],18,1,'gideonidoru/legend-of-dragoon-definitive','linux-x64',TAG))


if __name__=='__main__':unittest.main()
