#!/usr/bin/env python3
"""Stage exactly the approved enhanced movies, fetching each immutable object independently."""
import argparse, hashlib, json, os, shutil, tempfile, time, urllib.request, urllib.parse, zipfile
from decimal import Decimal
from pathlib import Path
from package_validation import properties

NAMES = 'OPENH DEMOH DEMO2 WAR1H TVRH GOAST ROZEH TREEH WAR2H BLACKH DRAGON1 DENIN DENIN2 DRAGON2 DEIASH MOONH ENDING1H ENDING2H'.split()
FIELDS = ('sha256','sourceSha256','bytes','frames','duration','width','height','fps')
CARRIER = 'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/'

def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()

def contract(path):
    if path.is_symlink() or path.stat().st_size > 65536: raise ValueError('Invalid FMVHD manifest file')
    p = properties(path.read_bytes())
    expected = {'format','legacyBundleSha256','provenanceSha256','noticeSha256'} | {name+'.'+field for name in NAMES for field in FIELDS}
    if set(p) != expected or p['format'] != '1': raise ValueError('Incomplete or unexpected FMVHD film closure')
    import re
    for key in [k for k in p if k.endswith('sha256') or k.endswith('Sha256')]:
        if not re.fullmatch('[a-f0-9]{64}',p[key]): raise ValueError('Invalid film digest')
    sizes = {}
    for name in NAMES:
        if any(not re.fullmatch('[1-9][0-9]{0,9}', p[name+'.'+key]) for key in ('bytes','frames','width','height','fps')): raise ValueError('Invalid film number')
        if not 0 < int(p[name+'.bytes']) <= 512*1024**2 or (p[name+'.width'],p[name+'.height'],p[name+'.fps']) != ('1280','768','15'): raise ValueError('Invalid enhanced film format')
        if not re.fullmatch(r'[0-9]{1,6}(\.[0-9]{1,16})?',p[name+'.duration']) or not 0 < int(p[name+'.frames']) <= 1000000 or abs(Decimal(p[name+'.frames'])/15-Decimal(p[name+'.duration'])) > Decimal('.001'): raise ValueError('Invalid film timing')
        digest, size = p[name+'.sha256'], int(p[name+'.bytes'])
        if digest in sizes and sizes[digest] != size: raise ValueError('Conflicting shared movie size')
        sizes[digest] = size
    return p

def copy_checked(source, destination, size, digest):
    start=time.monotonic(); total=0; h=hashlib.sha256()
    with destination.open('xb') as output:
        while True:
            data=source.read(65536)
            if not data: break
            total+=len(data)
            if total>size or time.monotonic()-start>1800: raise ValueError('Movie stream exceeds bounds')
            output.write(data);h.update(data)
    if total!=size or h.hexdigest()!=digest: raise ValueError('Movie byte identity mismatch')

class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlparse(newurl).scheme!='https': raise ValueError('Unsafe movie redirect')
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def stage(manifest, output, local=None):
    p=contract(manifest); resources=manifest.parent
    for name,key in [('provenance.json','provenanceSha256'),('NOTICE.txt','noticeSha256')]:
        file=resources/name
        if file.is_symlink() or not file.is_file() or file.stat().st_size>1024**2 or sha(file)!=p[key]: raise ValueError('FMVHD attribution/provenance mismatch')
    output=output.absolute(); output.parent.mkdir(parents=True,exist_ok=True)
    if output.is_symlink() or output.parent.is_symlink(): raise ValueError('Linked FMVHD output')
    # Existing output is generated build data only; refuse unknown files instead
    # of treating a caller-selected directory as safe to recursively delete.
    if output.exists() and any(output.iterdir()):
        old=contract(output/'manifest.properties')
        expected={'manifest.properties','NOTICE.txt','provenance.json'} | {'videos/'+old[n+'.sha256']+'.mp4' for n in NAMES}
        actual=set()
        for file in output.rglob('*'):
            if file.is_symlink(): raise ValueError('Linked old FMVHD build output')
            if file.is_dir() and file.relative_to(output).as_posix()!='videos': raise ValueError('Unexpected old FMVHD build directory')
            if file.is_file(): actual.add(file.relative_to(output).as_posix())
        if actual!=expected: raise ValueError('Unexpected old FMVHD build output')
    staging=Path(tempfile.mkdtemp(prefix='.fmv-stage-',dir=output.parent)); backup=None; archive=None; promoted=False
    try:
        (staging/'videos').mkdir()
        for name in ('NOTICE.txt','provenance.json'): shutil.copyfile(resources/name,staging/name)
        shutil.copyfile(manifest,staging/'manifest.properties')
        if local is not None and local.is_file():
            if local.is_symlink() or sha(local)!=p['legacyBundleSha256']: raise ValueError('Local legacy source ZIP hash mismatch')
            archive=zipfile.ZipFile(local)
            entries=archive.infolist();expected={'fmvhd/assets.properties','fmvhd/provenance.json','fmvhd/NOTICE.txt'}|{'fmvhd/videos/'+n+'.mp4' for n in NAMES}
            if len(entries)!=len(expected) or {e.filename for e in entries}!=expected: raise ValueError('Unexpected or duplicate legacy source ZIP entry')
            if archive.getinfo('fmvhd/assets.properties').file_size > 65536: raise ValueError('Oversized legacy source manifest')
            legacy=properties(archive.read('fmvhd/assets.properties'))
            if set(legacy)!={n+'.'+k for n in NAMES for k in FIELDS[:3]} or any(legacy[n+'.'+k]!=p[n+'.'+k] for n in NAMES for k in FIELDS[:3]): raise ValueError('Legacy source/movie identity mismatch')
        for name in NAMES:
            digest=p[name+'.sha256']; size=int(p[name+'.bytes']); destination=staging/'videos'/(digest+'.mp4')
            if destination.exists():
                if destination.stat().st_size!=size or sha(destination)!=digest: raise ValueError('Conflicting shared movie identity')
                print("FMVHD verified shared movie", name, flush=True)
                continue
            prior=output/'videos'/(digest+'.mp4')
            if prior.is_file() and not prior.is_symlink() and prior.stat().st_size==size and sha(prior)==digest:
                with prior.open('rb') as stream: copy_checked(stream,destination,size,digest)
            elif archive is not None:
                with archive.open('fmvhd/videos/'+name+'.mp4') as stream: copy_checked(stream,destination,size,digest)
            elif local is not None:
                source=local/(digest+'.mp4')
                if source.is_symlink() or not source.is_file(): raise ValueError('Missing or linked local enhanced movie')
                with source.open('rb') as stream: copy_checked(stream,destination,size,digest)
            else:
                opener=urllib.request.build_opener(HttpsRedirect())
                with opener.open(CARRIER+'file-'+digest, timeout=30) as stream: copy_checked(stream,destination,size,digest)
            print('FMVHD verified movie',name,flush=True)
        if archive is not None: archive.close();archive=None
        if output.exists():
            backup=Path(tempfile.mkdtemp(prefix='.fmv-old-',dir=output.parent));backup.rmdir();output.rename(backup)
        try:
            staging.rename(output)
            promoted=True
        except BaseException:
            if backup is not None:
                try:
                    backup.rename(output)
                    backup=None
                except OSError as recovery:
                    raise OSError('FMVHD output restoration failed; verified previous output preserved at '+str(backup)) from recovery
            raise
        return {'films':18,'bytes':sum(int(p[n+'.bytes']) for n in NAMES),'movieSha256':{n:p[n+'.sha256'] for n in NAMES}}
    finally:
        if archive is not None:archive.close()
        if staging.exists():shutil.rmtree(staging)
        if backup is not None and promoted:shutil.rmtree(backup)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--manifest',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--local',type=Path)
    args=parser.parse_args();print(json.dumps(stage(args.manifest,args.output,args.local),sort_keys=True))
