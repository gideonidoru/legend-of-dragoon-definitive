#!/usr/bin/env python3
"""Keep one verified public release; delete superseded downloads, preserving Git history."""
from pathlib import Path
import argparse, importlib.util, json, re, subprocess
from datetime import datetime

REPO='gideonidoru/legend-of-dragoon-definitive'
def gh(*args):return subprocess.check_output(['gh',*args],text=True,stderr=subprocess.PIPE)
def pages(endpoint):return json.loads(gh('api',endpoint,'--paginate','--slurp'))
def plan(releases, keep):
    matched=[r for r in releases if r['tag_name']==keep]
    if len(matched)!=1 or matched[0]['draft']:raise ValueError('Keep exactly one existing public release')
    current=matched[0]
    created=datetime.fromisoformat(current['created_at'].replace('Z','+00:00'))
    if any(datetime.fromisoformat(r['created_at'].replace('Z','+00:00'))>created for r in releases if r['id']!=current['id']):raise ValueError('A newer release exists; refresh the keep decision before deletion')
    return current,[r for r in releases if r['id']!=current['id']]
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
    artifacts=[x for page in pages(f'repos/{REPO}/actions/artifacts?per_page=100') for x in page['artifacts']]
    removable=[];statuses={}
    for artifact in artifacts:
        run_id=artifact['workflow_run']['id']
        if run_id==int(a.run):continue
        if run_id not in statuses:statuses[run_id]=json.loads(gh('api',f'repos/{REPO}/actions/runs/{run_id}'))['status']
        if statuses[run_id]=='completed':removable.append(artifact)
    result={'keptTag':a.keep_tag,'keptTemporaryArtifactRun':a.run,'deletedReleases':[], 'deletedArtifacts':[], 'releaseBytes':sum(x['size'] for r in old for x in r['assets']),'artifactBytes':sum(x['size_in_bytes'] for x in removable)}
    if a.execute:
        for release in old:
            verify_current()
            # Recheck identity/draft/chronology so a changing release is never deleted.
            fresh=json.loads(gh('api',f'repos/{REPO}/releases/{release["id"]}'))
            if any(fresh[k]!=release[k] for k in ('id','tag_name','draft','created_at','updated_at')):raise ValueError('Historical release changed during cleanup')
            gh('api','--method','DELETE',f'repos/{REPO}/releases/{release["id"]}');result['deletedReleases'].append(release['tag_name'])
        for artifact in removable:
            verify_current()
            if json.loads(gh('api',f'repos/{REPO}/actions/runs/{artifact["workflow_run"]["id"]}'))['status']!='completed':continue
            gh('api','--method','DELETE',f'repos/{REPO}/actions/artifacts/{artifact["id"]}');result['deletedArtifacts'].append(artifact['id'])
        verify_current()
        remaining=[r for page in pages(f'repos/{REPO}/releases?per_page=100') for r in page]
        if len(remaining)!=1 or remaining[0]['tag_name']!=a.keep_tag:raise ValueError('Unexpected release remains after cleanup')
    else:result['plannedTags']=[r['tag_name'] for r in old];result['plannedArtifacts']=[x['id'] for x in removable]
    print(json.dumps(result,indent=2))
if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError,subprocess.CalledProcessError) as error:raise SystemExit('Retention stopped: '+str(error))
