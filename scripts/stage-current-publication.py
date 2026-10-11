#!/usr/bin/env python3
"""Stage source-verified CI deltas; reused files stay in the current release."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile
import shutil
import tempfile
from current_publication import REPO, TAG, PLATFORMS, digest, canonical, unique_object, descriptor_name, check_asset, index_assets
from package_validation import inventory, verify_package, verify_installer, file_delivery_assets, properties


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True, stderr=subprocess.PIPE)


def paged(endpoint):
    return json.loads(gh('api', endpoint, '--paginate', '--slurp'))


def verify_run(run_id, source):
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{run_id}'))
    if any(run.get(k) != v for k,v in {'status':'completed','conclusion':'success','head_sha':source,'path':'.github/workflows/build.yml'}.items()):
        raise ValueError('Exact source packaging workflow has not passed')
    jobs = [j for page in paged(f'repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100') for j in page['jobs']]
    required = {'Original synthetic visual fixtures','Linux x64 / Steam Deck package','macOS package'}
    selected = [j for j in jobs if j['name'] in required]
    if len(selected)!=3 or {j['name'] for j in selected}!=required or any(j['status']!='completed' or j['conclusion']!='success' for j in selected):
        raise ValueError('Required platform/test jobs did not all pass')
    return run


def remote_name(record):
    name = record['name']
    reuse = record.get('reuseName', name)
    if reuse != name and (name != 'FMVHD-v0.1.0-videos.zip' or reuse != descriptor_name('fmvSource', record['sha256'])):
        raise ValueError('Unexpected receipt alias')
    return reuse


def reused_asset(record, current, release):
    name=remote_name(record)
    asset=current.get(name)
    # An interrupted initial migration can already have renamed this exact ID.
    # The old, source-bound CI receipt remains replayable without downloading media.
    if asset is None and record['name']=='FMVHD-v0.1.0-videos.zip':
        name=descriptor_name('fmvSource',record['sha256']);asset=current.get(name)
    checked=check_asset(asset or {},name,record['size'],record['sha256'],release['tag_name'])
    if str(release['id'])!=str(record.get('releaseId')) or str(checked['id'])!=str(record.get('assetId')):
        raise ValueError('Reused asset/release ID changed')
    return checked


def materialize(record, dest, current, tag):
    """Only small descriptors/bootstrap need a local copy for archive validation."""
    if dest.exists():
        if dest.is_symlink() or (dest.stat().st_size,digest(dest))!=(record['size'],record['sha256']):
            raise ValueError('Staged bytes differ: '+record['name'])
        return
    name = remote_name(record)
    asset = current.get(name)
    if asset is None:
        raise ValueError('CI omitted required new upload: '+record['name'])
    check_asset(asset,name,record['size'],record['sha256'],tag)
    with tempfile.NamedTemporaryFile(dir=dest.parent,prefix='.descriptor-') as temporary:
        temp=Path(temporary.name)
        subprocess.run(['curl','--fail','--location','--proto','=https','--proto-redir','=https','--connect-timeout','20','--max-time','300','--retry','2','--silent','--show-error','--output',str(temp),asset['browser_download_url']],check=True)
        if (temp.stat().st_size,digest(temp))!=(record['size'],record['sha256']):
            raise ValueError('Reused public descriptor checksum differs')
        # Keep partial downloads out of the reusable staged namespace.
        os.link(temp,dest)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-sha',required=True);parser.add_argument('--run',type=int,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if not re.fullmatch('[a-f0-9]{40}',args.source_sha) or args.run<1:
        raise ValueError('Invalid source/run')
    if gh('api','user','--jq','.login').strip()!='gideonidoru':raise ValueError('Wrong GitHub account')
    run=verify_run(args.run,args.source_sha)
    root=args.output;root.mkdir(parents=True,exist_ok=True)
    raw=root/'ci-artifacts';raw.mkdir(exist_ok=True)
    staged=root/'uploads';staged.mkdir(exist_ok=True)
    artifacts=[a for page in paged(f'repos/{REPO}/actions/runs/{args.run}/artifacts?per_page=100') for a in page['artifacts']]
    latest=json.loads(gh('api',f'repos/{REPO}/releases/latest'))
    assets=[a for page in paged(f'repos/{REPO}/releases/{latest["id"]}/assets?per_page=100') for a in page]
    current=index_assets(assets);receipts={};records={};actual_uploaded={}
    for platform,artifact_name in [('linux-x64','delivery-ubuntu-24.04'),('macos-arm64','delivery-macos-15')]:
        matches=[a for a in artifacts if a['name']==artifact_name]
        if len(matches)!=1:raise ValueError('Missing/ambiguous delta artifact')
        artifact=matches[0]
        if artifact['expired'] or artifact['workflow_run']['id']!=args.run or artifact['workflow_run']['head_sha']!=args.source_sha or not re.fullmatch('sha256:[a-f0-9]{64}',artifact.get('digest','')):
            raise ValueError('Artifact provenance differs')
        archive=raw/(artifact_name+'.zip')
        if not archive.exists():
            temporary=archive.with_suffix('.partial')
            with temporary.open('wb') as stream:subprocess.run(['gh','api',f'repos/{REPO}/actions/artifacts/{artifact["id"]}/zip'],stdout=stream,check=True,timeout=1800)
            if 'sha256:'+digest(temporary)!=artifact['digest']:raise ValueError('Raw CI delta digest differs')
            temporary.replace(archive)
        if archive.is_symlink() or 'sha256:'+digest(archive)!=artifact['digest']:raise ValueError('Cached CI delta digest differs')
        with zipfile.ZipFile(archive) as z:
            entries=inventory(z)
            wanted='publication-'+platform+'.json'
            manifests=[e for n,e in entries.items() if n.split('/')[-1]==wanted and not e.is_dir()]
            if len(manifests)!=1 or manifests[0].file_size>4*1024**2:raise ValueError('Missing bounded CI receipt')
            receipt=json.loads(z.read(manifests[0]),object_pairs_hook=unique_object)
            if any(receipt.get(k)!=v or type(receipt.get(k)) is not type(v) for k,v in {'format':1,'sourceSha':args.source_sha,'runId':args.run,'repository':REPO,'tag':TAG,'platform':platform,'runAttempt':run['run_attempt']}.items()):
                raise ValueError('CI receipt source/run/platform differs')
            if not isinstance(receipt.get('assets'),list) or not 1<=len(receipt['assets'])<=1000:raise ValueError('Unbounded CI upload inventory')
            receipts[platform]=receipt;seen=set()
            for record in receipt['assets']:
                name=record['name']
                if name in seen or not re.fullmatch('[A-Za-z0-9._-]{1,160}',name) or type(record.get('size')) is not int or not 0<=record['size']<=2*1024**3 or not re.fullmatch('[a-f0-9]{64}',record.get('sha256','')):
                    raise ValueError('Unsafe/duplicate CI asset receipt')
                seen.add(name)
                if name in records and (record['size'],record['sha256'])!=(records[name]['size'],records[name]['sha256']):raise ValueError('Platform asset hashes disagree')
                records[name]=record
                remote_name(record)
                if record['disposition']=='upload':
                    found=[e for n,e in entries.items() if n.split('/')[-1]==name and not e.is_dir()]
                    if len(found)!=1 or found[0].file_size!=record['size']:raise ValueError('CI upload is missing/ambiguous')
                    target=staged/name
                    if not target.exists():
                        with tempfile.NamedTemporaryFile(dir=staged,prefix='.payload-') as temporary:
                            with z.open(found[0]) as stream:shutil.copyfileobj(stream,temporary,1024*1024)
                            temporary.flush();temp=Path(temporary.name)
                            if (temp.stat().st_size,digest(temp))!=(record['size'],record['sha256']):raise ValueError('CI upload bytes differ from receipt')
                            os.link(temp,target)
                    if target.is_symlink() or (target.stat().st_size,digest(target))!=(record['size'],record['sha256']):raise ValueError('CI upload bytes differ from receipt')
                    actual_uploaded[name]=(record['size'],record['sha256'])
                elif record['disposition']=='reuse':
                    reused_asset(record,current,latest)
                elif record['disposition']!='linux' or platform!='macos-arm64':
                    raise ValueError('Invalid delta disposition')
        print('Verified source-bound delta:',platform,flush=True)
    # A Mac assignment must resolve to the exact independently generated Linux row.
    linux={a['name']:a for a in receipts['linux-x64']['assets']}
    for record in receipts['macos-arm64']['assets']:
        if record['disposition']=='linux':
            prior=linux.get(record['name'])
            if prior is None or (record['size'],record['sha256'])!=(prior['size'],prior['sha256']):raise ValueError('Missing/different Linux shared payload')
    required={};aliases={};descriptor_inputs={}
    for platform in PLATFORMS:
        paths=[]
        for kind,prefix in [('package','Legend-of-Dragoon-Definitive-'),('contents','Definitive-Contents-')]:
            name=prefix+platform+'.zip';record=records[name];path=staged/name
            materialize(record,path,current,latest['tag_name']);paths.append(path)
            immutable=descriptor_name(kind,record['sha256'],platform)
            target=staged/immutable
            if not target.exists():os.link(path,target)
            required[immutable]=dict(bytes=record['size'],sha256=record['sha256'],path=immutable)
            aliases[name]=dict(bytes=record['size'],sha256=record['sha256'],path=name)
            descriptor_inputs[platform+'.'+kind]=immutable
        verify_package(paths[0],platform,args.source_sha,TAG,allow_file_delivery=True)
        with zipfile.ZipFile(paths[1]) as content:
            hashes=properties(content.read('definitive-files.properties'));sizes=properties(content.read('definitive-sizes.properties'))
        expected_files=[dict(path=name,sha256=sha,size=int(sizes[name]),assetName='file-'+sha) for name,sha in sorted(hashes.items())]
        if receipts[platform].get('files')!=expected_files:raise ValueError('CI complete managed file receipt differs from verified contents')
        expected=file_delivery_assets(*paths,include_env_aliases=False)
        for name,(size,sha) in expected.items():
            record=records.get(name)
            if record is None or (record['size'],record['sha256'])!=(size,sha):raise ValueError('CI receipt omits/changes required package file')
            required[name]=dict(bytes=size,sha256=sha,**({'path':name} if (staged/name).exists() else {}))
    for kind,name in [('installer','Definitive-Installer.zip'),('entry','Install-Definitive.sh')]:
        record=records[name];path=staged/name;materialize(record,path,current,latest['tag_name'])
        immutable=descriptor_name(kind,record['sha256']);target=staged/immutable
        if not target.exists():os.link(path,target)
        required[immutable]=dict(bytes=record['size'],sha256=record['sha256'],path=immutable)
        aliases[name]=dict(bytes=record['size'],sha256=record['sha256'],path=name)
        descriptor_inputs[kind]=immutable
    verify_installer(staged/'Definitive-Installer.zip')
    script=(staged/'Install-Definitive.sh').read_text()
    if 'TAG='+TAG+'\n' not in script or 'EXPECTED='+records['Definitive-Installer.zip']['sha256']+'\n' not in script:raise ValueError('Installer script source/checksum differs')
    name='Install-Definitive.desktop';record=records[name];materialize(record,staged/name,current,latest['tag_name'])
    from desktop_bootstrap import desktop_entry
    if (staged/name).read_text()!=desktop_entry():raise ValueError('Desktop does not fetch the latest verified installer')
    aliases[name]=dict(bytes=record['size'],sha256=record['sha256'],path=name)
    source_name='FMVHD-v0.1.0-videos.zip';record=records[source_name]
    lock=properties(subprocess.check_output(['git','show',args.source_sha+':integrations/fmvhd/release.properties'],cwd=Path(__file__).resolve().parents[1]))
    if record['sha256']!=lock.get('sha256'):raise ValueError('FMV build input pin differs')
    immutable=descriptor_name('fmvSource',record['sha256'])
    if (staged/source_name).exists() and not (staged/immutable).exists():os.link(staged/source_name,staged/immutable)
    required[immutable]=dict(bytes=record['size'],sha256=record['sha256'],**({'path':immutable} if (staged/immutable).exists() else {}))
    descriptor_inputs['fmvSource']=immutable
    source_asset=reused_asset(record,current,latest) if record['disposition']=='reuse' else current.get(remote_name(record))
    source_rename=None
    if source_asset is not None and source_asset['name']!=immutable:
        source_rename=dict(id=source_asset['id'],oldName=source_asset['name'],name=immutable,bytes=record['size'],sha256=record['sha256'])
    # Reject omitted files and unexpected CI payloads, not only the desired subset.
    expected_names={n for n in required if n.startswith('file-')}|set(aliases)|{source_name}
    if set(records)!=expected_names:raise ValueError('CI receipt includes unexpected or incomplete assets')
    result=dict(format=1,result='STAGED',source=args.source_sha,run=args.run,runAttempt=run['run_attempt'],tag=TAG,
                baselineRelease=dict(id=latest['id'],tag=latest['tag_name'],bodySha256=hashlib.sha256((latest.get('body') or '').encode()).hexdigest()),
                immutable=required,aliases=aliases,descriptors=descriptor_inputs,receipts=receipts,
                sourceRename=source_rename,
                rawArtifacts=[dict(id=a['id'],name=a['name'],digest=a['digest']) for a in artifacts if a['name'].startswith('delivery-')])
    (root/'publication-inputs.json').write_bytes(canonical(result)+b'\n')
    print('PASS: staged complete source-bound publication; unchanged media stays on the server.',flush=True)


if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError,subprocess.CalledProcessError,subprocess.TimeoutExpired,zipfile.BadZipFile) as error:raise SystemExit('Staging stopped: '+str(error))
