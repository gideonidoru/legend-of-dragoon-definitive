#!/usr/bin/env python3
"""Synthetic deletion-plan guards; never contact GitHub."""
import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('retention',Path(__file__).with_name('prune-release-history.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def release(id,tag,date,draft=False):return {'id':id,'tag_name':tag,'created_at':date+'T00:00:00Z','draft':draft}
class Retention(unittest.TestCase):
    def test_only_old_releases_selected(self):
        current=release(2,'new','2026-10-10');old=release(1,'old','2026-10-09');keep,removed=m.plan([old,current],'new');self.assertEqual(current,keep);self.assertEqual([old],removed)
    def test_missing_or_draft_replacement_refused(self):
        with self.assertRaises(ValueError):m.plan([],'new')
        with self.assertRaises(ValueError):m.plan([release(2,'new','2026-10-10',True)],'new')
    def test_newer_or_ambiguous_release_refused(self):
        current=release(2,'new','2026-10-10')
        with self.assertRaises(ValueError):m.plan([current,release(3,'next','2026-10-11')],'new')
        with self.assertRaises(ValueError):m.plan([current,current],'new')
    def test_drafts_remain_protected_regardless_of_age(self):
        current=release(2,'new','2026-10-10')
        for date in ('2026-10-09','2026-10-11'):
            draft=release(3,'working',date,True)
            keep,removed=m.plan([current,draft],'new')
            self.assertEqual(current,keep);self.assertEqual([],removed)
    def test_artifacts_protect_active_future_and_draft_sources(self):
        kept={'id':2,'head_sha':'a'*40,'status':'completed','created_at':'2026-10-10T10:00:00Z'}
        historical={'id':1,'head_sha':'b'*40,'status':'completed','created_at':'2026-10-09T10:00:00Z'}
        self.assertTrue(m.eligible_artifact(historical,kept,[]))
        for change in ({'status':'in_progress'},{'id':2},{'head_sha':'a'*40},{'created_at':'2026-10-10T10:00:00Z'},{'created_at':'2026-10-11T10:00:00Z'}):
            self.assertFalse(m.eligible_artifact(dict(historical,**change),kept,[]))
        draft=dict(release(3,'working','2026-10-11',True),target_commitish='b'*40)
        self.assertFalse(m.eligible_artifact(historical,kept,[draft]))
        with self.assertRaises(ValueError):m.eligible_artifact(historical,kept,[dict(draft,target_commitish='main')])
    def test_new_publication_from_old_commit_stops_cleanup(self):
        kept=dict(release(2,'current','2026-10-10'),published_at='2026-10-10T12:00:00Z')
        newer=dict(release(3,'new-prerelease','2026-10-09'),published_at='2026-10-10T13:00:00Z',prerelease=True)
        with self.assertRaises(ValueError):m.plan([kept,newer],'current')
    def test_fresh_attempt_and_artifact_creation_are_protected(self):
        kept={'id':2,'head_sha':'a'*40,'created_at':'2026-10-10T10:00:00Z'}
        old={'id':1,'head_sha':'b'*40,'status':'completed','created_at':'2026-10-09T10:00:00Z'}
        for key in ('run_started_at','updated_at'):
            self.assertFalse(m.eligible_artifact(dict(old,**{key:'2026-10-10T11:00:00Z'}),kept,[]))
        self.assertFalse(m.eligible_artifact(old,kept,[],{'created_at':'2026-10-10T11:00:00Z'}))
    def test_draft_source_must_match_actual_tag(self):
        draft=dict(release(3,'working','2026-10-11',True),target_commitish='b'*40)
        calls=[]
        def mismatch(tag,source,required):
            calls.append((tag,source,required));raise ValueError('Actual Git tag points to another source commit')
        with self.assertRaises(ValueError):m.verify_draft_sources([draft],mismatch)
        self.assertEqual([('working','b'*40,False)],calls)
        m.verify_draft_sources([dict(draft,draft=False)],mismatch)
    def test_latest_must_be_exact_public_release(self):
        kept={'databaseId':2,'tagName':'new'};latest={'id':2,'tag_name':'new','draft':False,'prerelease':False};m.verify_latest(kept,latest)
        for changed in ({'id':1},{'tag_name':'old'},{'draft':True},{'prerelease':True}):
            with self.assertRaises(ValueError):m.verify_latest(kept,dict(latest,**changed))
if __name__=='__main__':unittest.main()
