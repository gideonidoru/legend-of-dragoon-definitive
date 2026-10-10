#!/usr/bin/env python3
"""Keep one verified public release; delete superseded downloads, preserving Git history."""
from pathlib import Path
import argparse, importlib.util, json, re, subprocess
from datetime import datetime

REPO='gideonidoru/legend-of-dragoon-definitive'
def gh(*args):return subprocess.check_output(['gh',*args],text=True,stderr=subprocess.PIPE)
def pages(endpoint):return json.loads(gh('api',endpoint,'--paginate','--slurp'))
def timestamp(value):return datetime.fromisoformat(value.replace('Z','+00:00'))
def release_time(release):return max(timestamp(release['created_at']),timestamp(release.get('published_at') or release['created_at']))
def verify_draft_sources(releases, verify_tag):
    for release in releases:
        if not release['draft']:continue
        source=release.get('target_commitish','')
        if not re.fullmatch(r'[a-f0-9]{40}',source):raise ValueError('Cannot identify active draft source')
        verify_tag(release['tag_name'],source,required=False)
def plan(releases, keep):
    matched=[r for r in releases if r['tag_name']==keep]
    if len(matched)!=1 or matched[0]['draft']:raise ValueError('Keep exactly one existing public release')
    current=matched[0]
    created=release_time(current)
    if any(release_time(r)>created for r in releases if r['id']!=current['id'] and not r['draft']):raise ValueError('A newer public release exists; refresh the keep decision before deletion')
    return current,[r for r in releases if r['id']!=current['id'] and not r['draft']]
def eligible_artifact(run, keep_run, releases, artifact=None):
    # Protect future candidates even after CI completes, and all draft source builds.
    # Draft tags may still be moving; incomplete source discovery stops deletion.
    drafts=[r for r in releases if r['draft']]
    if any(not re.fullmatch(r'[a-f0-9]{40}', r.get('target_commitish','')) for r in drafts):
        raise ValueError('Cannot identify an active draft build; artifact cleanup requires exact draft sources')
    protected={r['target_commitish'] for r in drafts}|{keep_run['head_sha']}
    if run['status']!='completed' or run['id']==keep_run['id'] or run['head_sha'] in protected:return False
    times=[run['created_at']]
    times.extend(run[key] for key in ('run_started_at','updated_at') if run.get(key))
    if artifact is not None:times.append(artifact['created_at'])
    return max(map(timestamp,times)) < timestamp(keep_run['created_at'])
def verify_latest(release,latest):
    if latest['id']!=release['databaseId'] or latest['tag_name']!=release['tagName'] or latest['draft'] or latest['prerelease']:raise ValueError('Current build-input URL does not resolve to the retained public release')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--keep-tag',required=True);p.add_argument('--source-sha',required=True);p.add_argument('--run',required=True);p.add_argument('--inventory',type=Path,required=True);p.add_argument('--execute',action='store_true');a=p.parse_args()
    if not re.fullmatch(r'[a-f0-9]{40}',a.source_sha) or not a.run.isdecimal() or not re.fullmatch(r'[A-Za-z0-9._-]+',a.keep_tag):raise ValueError('Invalid release/build identity')
    if gh('api','user','--jq','.login').strip()!='gideonidoru':raise ValueError('Wrong GitHub account')
    spec=importlib.util.spec_from_file_location('publisher',Path(__file__).with_name('publish-verified-release.py'));publisher=importlib.util.module_from_spec(spec);spec.loader.exec_module(publisher)
    audit=json.loads(a.inventory.read_text())
    if (audit['source'],audit['tag'],audit['result'])!=(a.source_sha,a.keep_tag,'PASS'):raise ValueError('Wrong checked upload inventory')
    expected={n:(v['bytes'],v['sha256']) for n,v in audit['uploads'].items()}
    if 'SHA256SUMS' not in expected:raise ValueError('Inventory must include the sums asset')
    lock=publisher.properties(subprocess.check_output(['git','show',a.source_sha+':integrations/fmvhd/release.properties'],cwd=Path(__file__).resolve().parents[1]))
    if expected.get('FMVHD-v0.1.0-videos.zip',(None,None))[1]!=lock.get('sha256') or not re.fullmatch(r'[a-f0-9]{64}',lock.get('sha256','')):raise ValueError('Current release must retain the exact pinned FMV build input')
    def verify_current():
        release=json.loads(gh('release','view',a.keep_tag,'-R',REPO,'--json','databaseId,tagName,targetCommitish,isDraft,url,assets'))
        release['assets']=[dict(x,url=x['browser_download_url']) for page in pages(f'repos/{REPO}/releases/{release["databaseId"]}/assets?per_page=100') for x in page]
        if release['isDraft']:raise ValueError('Replacement has not been published')
        publisher.verify_release(release,a.keep_tag,a.source_sha,expected);publisher.verify_tag(a.keep_tag,a.source_sha,required=True)
        latest=json.loads(gh('api',f'repos/{REPO}/releases/latest'))
        verify_latest(release,latest)
    verify_current()
    run=json.loads(gh('api',f'repos/{REPO}/actions/runs/{a.run}'))
    if (run['status'],run['conclusion'],run['head_sha'])!=('completed','success',a.source_sha):raise ValueError('Replacement build did not pass')
    releases=[r for page in pages(f'repos/{REPO}/releases?per_page=100') for r in page]
    current,old=plan(releases,a.keep_tag)
    verify_draft_sources(releases,publisher.verify_tag)
    artifacts=[x for page in pages(f'repos/{REPO}/actions/artifacts?per_page=100') for x in page['artifacts']]
    removable=[];statuses={}
    for artifact in artifacts:
        run_id=artifact['workflow_run']['id']
        if run_id==int(a.run):continue
        if run_id not in statuses:statuses[run_id]=json.loads(gh('api',f'repos/{REPO}/actions/runs/{run_id}'))
        if eligible_artifact(statuses[run_id],run,releases,artifact):removable.append(artifact)
    result={'keptTag':a.keep_tag,'keptTemporaryArtifactRun':a.run,'protectedDraftTags':[r['tag_name'] for r in releases if r['draft']], 'protectedArtifactIds':[x['id'] for x in artifacts if x not in removable], 'deletedReleases':[], 'deletedArtifacts':[], 'releaseBytes':sum(x['size'] for r in old for x in r['assets']),'artifactBytes':sum(x['size_in_bytes'] for x in removable)}
    if a.execute:
        for release in old:
            verify_current()
            # Recheck identity/draft/chronology so a changing release is never deleted.
            plan([r for page in pages(f'repos/{REPO}/releases?per_page=100') for r in page],a.keep_tag)
            fresh=json.loads(gh('api',f'repos/{REPO}/releases/{release["id"]}'))
            if any(fresh[k]!=release[k] for k in ('id','tag_name','draft','created_at','updated_at')):raise ValueError('Historical release changed during cleanup')
            gh('api','--method','DELETE',f'repos/{REPO}/releases/{release["id"]}');result['deletedReleases'].append(release['tag_name'])
        for artifact in removable:
            verify_current()
            fresh_releases=[r for page in pages(f'repos/{REPO}/releases?per_page=100') for r in page]
            plan(fresh_releases,a.keep_tag)
            verify_draft_sources(fresh_releases,publisher.verify_tag)
            fresh_run=json.loads(gh('api',f'repos/{REPO}/actions/runs/{artifact["workflow_run"]["id"]}'))
            if not eligible_artifact(fresh_run,run,fresh_releases,artifact):continue
            gh('api','--method','DELETE',f'repos/{REPO}/actions/artifacts/{artifact["id"]}');result['deletedArtifacts'].append(artifact['id'])
        verify_current()
        remaining=[r for page in pages(f'repos/{REPO}/releases?per_page=100') for r in page]
        public=[r for r in remaining if not r['draft']]
        if len(public)!=1 or public[0]['tag_name']!=a.keep_tag:raise ValueError('Unexpected public release remains after cleanup')
        result['protectedDraftTags']=[r['tag_name'] for r in remaining if r['draft']]
    else:result['plannedTags']=[r['tag_name'] for r in old];result['plannedArtifacts']=[x['id'] for x in removable]
    print(json.dumps(result,indent=2))
if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError,subprocess.CalledProcessError) as error:raise SystemExit('Retention stopped: '+str(error))
