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
if __name__=='__main__':unittest.main()
