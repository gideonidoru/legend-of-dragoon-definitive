#!/usr/bin/env python3
"""Publication identity, reuse and commit guards using synthetic file metadata."""
import copy
import hashlib
import unittest
from current_publication import *


def asset(name, sha, size=7, id=1, tag=TAG):
    return dict(name=name, id=id, size=size, digest='sha256:'+sha, state='uploaded',
                browser_download_url=f'https://github.com/{REPO}/releases/download/{tag}/{name}')


def fixture():
    sha='a'*64
    manifest=dict(format=1,generation=1,sourceRevision='b'*40,buildRun=3,releaseId='9',
                  releaseTag=TAG,publishedAt='2026-10-11T15:00:00Z',platforms={})
    assets=[]
    for platform in PLATFORMS:
        manifest['platforms'][platform]={}
        for kind in ('package','contents'):
            record=asset(descriptor_name(kind,sha,platform),sha,id=len(assets)+1)
            assets.append(record);manifest['platforms'][platform][kind]=descriptor(record,kind,platform)
    for kind in ('installer','entry','fmvSource'):
        record=asset(descriptor_name(kind,sha),sha,id=len(assets)+1)
        assets.append(record);manifest[kind]=descriptor(record,kind)
    data=canonical(manifest);digest=hashlib.sha256(data).hexdigest()
    assets.append(asset('manifest-'+digest+'.json',digest,len(data),len(assets)+1))
    return manifest,assets


class CurrentPublication(unittest.TestCase):
    def test_complete_exact_source_manifest_roundtrip(self):
        manifest,assets=fixture()
        self.assertEqual(manifest,parse_body(body(manifest,'Human notes.'),'9'))
        self.assertEqual(hashlib.sha256(canonical(manifest)).hexdigest(),check_references(manifest,assets))

    def test_identical_second_publish_requires_zero_payload_uploads(self):
        name='file-'+'a'*64
        reused,uploads=reuse_plan({name:(7,'a'*64)},[asset(name,'a'*64)])
        self.assertEqual({name:'1'},reused);self.assertEqual({},uploads)

    def test_engine_only_delta_keeps_fmv_asset_id(self):
        movie='FMVHD-v0.1.0-videos.zip';engine='file-'+'b'*64
        reused,uploads=reuse_plan({movie:(7,'a'*64),engine:(9,'b'*64)},[asset(movie,'a'*64,id=71)])
        self.assertEqual('71',reused[movie]);self.assertEqual({engine:(9,'b'*64)},uploads)

    def test_hash_name_mismatch_is_not_overwritten(self):
        name='file-'+'a'*64
        for change in ({'digest':'sha256:'+'b'*64},{'size':8},{'state':'starter'},{'browser_download_url':'https://other.invalid/'+name}):
            with self.subTest(change=change),self.assertRaises(ValueError):
                reuse_plan({name:(7,'a'*64)},[dict(asset(name,'a'*64),**change)])

    def test_complete_asset_metadata_required(self):
        manifest,assets=fixture()
        for missing in range(len(assets)):
            with self.subTest(missing=missing),self.assertRaises(ValueError):
                check_references(manifest,assets[:missing]+assets[missing+1:])

    def test_platform_ids_and_other_release_origins_rejected(self):
        manifest,assets=fixture()
        changed=copy.deepcopy(manifest);changed['platforms']['linux-x64']['package']['id']='99'
        with self.assertRaises(ValueError):check_references(changed,assets)
        for change in ({'id':'10'},{'browser_download_url':assets[0]['browser_download_url'].replace(TAG,'old')}):
            with self.assertRaises(ValueError):check_references(manifest,[dict(assets[0],**change)]+assets[1:])

    def test_manifest_schema_framing_canonicality_and_limits(self):
        manifest,_=fixture();valid=body(manifest)
        cases=['prefix'+valid,valid+valid,valid.replace(END,''),valid.replace('"format":1','"format":1,"format":1'),
               valid.replace('"format":1','"format":1,"unknown":7'),
               valid.replace('"generation":1','"generation":true'),valid.replace('"generation":1','"generation":0'),
               valid.replace('"generation":1','"generation":9007199254740992'),
               valid.replace('"format":1','"format": 1'),valid+'x'*12000]
        for value in cases:
            with self.subTest(value=value[:100]),self.assertRaises(ValueError):parse_body(value,'9')
        with self.assertRaises(ValueError):parse_body(valid,'10')

    def test_no_missing_platform_or_arbitrary_download_name(self):
        manifest,_=fixture()
        for change in ('platform','name','source','time','carrier'):
            value=copy.deepcopy(manifest)
            if change=='platform':del value['platforms']['macos-arm64']
            if change=='name':value['installer']['name']='arbitrary.zip'
            if change=='source':value['sourceRevision']='main'
            if change=='time':value['publishedAt']='2026-02-30T01:00:00Z'
            if change=='carrier':value['releaseTag']='another'
            with self.subTest(change=change),self.assertRaises(ValueError):validate_manifest(value)

    def test_ambiguous_names_or_ids_stop_reuse(self):
        item=asset('file-'+'a'*64,'a'*64)
        with self.assertRaises(ValueError):index_assets([item,item])
        with self.assertRaises(ValueError):index_assets([item,dict(item,name='another')])


if __name__=='__main__':unittest.main()
