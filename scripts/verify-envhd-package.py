#!/usr/bin/env python3
"""Verify the selected EnvHD runtime bytes and all resource parts in normal packages."""
import argparse,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def digest(stream):return hashlib.file_digest(stream,'sha256').hexdigest()
def verify(package):
    runtime=ROOT/'integrations/envhd/runtime-assets'
    production=ROOT/'integrations/envhd/production'
    selected=json.loads((production/'field-runtime-artwork.json').read_text())
    fields={a['decodedRgbaSha256']:a for a in selected['assets']}
    expected_parts={i:set() for i in range(4)}
    expected_jar={}
    for path in runtime.rglob('*'):
        if not path.is_file():continue
        name=path.relative_to(runtime).as_posix()
        if name.startswith('envhd/fields/') and name.endswith('/image-v1.png'):
            key=path.parent.name
            if key not in fields or digest(path.open('rb'))!=fields[key]['outputSha256']:raise ValueError('Unselected or changed field PNG')
            expected_parts[int(key[0],16)//4].add(name)
        else:expected_jar[name]=(path.stat().st_size,digest(path.open('rb')))
    resources=ROOT/'integrations/envhd/src/main/resources'
    for path in resources.rglob('*'):
        if path.is_file():expected_jar[path.relative_to(resources).as_posix()]=(path.stat().st_size,digest(path.open('rb')))
    if sum(map(len,expected_parts.values()))!=len(fields):raise ValueError('Incomplete selected field resources')
    with zipfile.ZipFile(package/'bundled-mods/EnvHD-v0.1.0.jar') as jar:
        actual=[n for n in jar.namelist() if n.startswith('envhd/') and not n.endswith('/') and not n.endswith('.class')]
        if len(actual)!=len(set(actual)) or set(actual)!=set(expected_jar):raise ValueError('EnvHD JAR resource set differs')
        for name,(size,sha) in expected_jar.items():
            with jar.open(name) as stream:
                if jar.getinfo(name).file_size!=size or digest(stream)!=sha:raise ValueError('EnvHD JAR bytes differ: '+name)
    for part,names in expected_parts.items():
        path=package/f'bundled-mods/EnvHD-Fields-v0.1.0-part-{part}.zip'
        if path.stat().st_size>=2*1024**3:raise ValueError('EnvHD resource part exceeds download limit')
        with zipfile.ZipFile(path) as archive:
            entries=[n for n in archive.namelist() if not n.endswith('/')]
            if len(entries)!=len(set(entries)) or set(entries)!=names:raise ValueError('Field bundle entries differ')
            for name in entries:
                source=runtime/name
                with archive.open(name) as stream:
                    if archive.getinfo(name).file_size!=source.stat().st_size or digest(stream)!=fields[source.parent.name]['outputSha256']:raise ValueError('Field bundle bytes differ')
    print('EnvHD normal package verified:',len(fields),'field images in four parts;',len(expected_jar),'JAR resources; one mod ID; gameplay/final quality pending')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('package',type=Path);verify(parser.parse_args().package)
