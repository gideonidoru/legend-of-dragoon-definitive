#!/usr/bin/env python3
"""Validate and deterministically package enhanced videos, never raw originals."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

NAMES = 'OPENH DEMOH DEMO2 WAR1H TVRH GOAST ROZEH TREEH WAR2H BLACKH DRAGON1 DENIN DENIN2 DRAGON2 DEIASH MOONH ENDING1H ENDING2H'.split()

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''): digest.update(block)
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    records, properties = [], []
    for name in NAMES:
        record = json.loads((args.production/'records'/(name+'.json')).read_text())
        video = args.production/'videos'/(name+'.mp4')
        if record['name'] != name or record['sha256'] != sha(video) or record['bytes'] != video.stat().st_size:
            raise ValueError('Identity/size mismatch: '+name)
        if not 0 < record['bytes'] <= 512*1024*1024 or (record['width'],record['height'],record['fps']) != (1280,768,15):
            raise ValueError('Unsupported output: '+name)
        if record['frames'] <= 0 or abs(record['duration']-record['frames']/15) > 0.001:
            raise ValueError('Timing mismatch: '+name)
        records.append(record)
        properties.extend(f'{name}.{key}={record[key]}' for key in ('sha256','sourceSha256','bytes'))
    provenance = json.loads((args.production/'toolchain.json').read_text())
    provenance['videos'] = records
    provenance['assetNotice'] = 'Enhanced from The Legend of Dragoon. Original footage copyright belongs to Sony Computer Entertainment and is not relicensed under the repository AGPL code license.'
    provenance['limitations'] = ['Framewise neural restoration can smooth detail or flicker.', 'Source extraction timing warnings are recorded per video.', 'Physical Steam Deck acceptance remains pending.']
    args.output.parent.mkdir(parents=True, exist_ok=True)
    def write(archive, name, data):
        info = zipfile.ZipInfo(name, (2026,10,10,0,0,0))
        info.external_attr = 0o644 << 16
        with archive.open(info, 'w', force_zip64=True) as destination:
            if isinstance(data, bytes): destination.write(data)
            else:
                with data.open('rb') as source:
                    for block in iter(lambda: source.read(1048576), b''): destination.write(block)
    with zipfile.ZipFile(args.output, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        write(archive,'fmvhd/NOTICE.txt', (provenance['assetNotice']+'\nExternal production tools and notice links are recorded in provenance.json.\n').encode())
        write(archive,'fmvhd/assets.properties', ('\n'.join(properties)+'\n').encode())
        write(archive,'fmvhd/provenance.json', (json.dumps(provenance,indent=2)+'\n').encode())
        for name in NAMES: write(archive,'fmvhd/videos/'+name+'.mp4', args.production/'videos'/(name+'.mp4'))
    print(json.dumps({'file':args.output.name,'sha256':sha(args.output),'bytes':args.output.stat().st_size,'videos':len(records)}))

if __name__ == '__main__': main()
