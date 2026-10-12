#!/usr/bin/env python3
"""Pack individually authored Original HD SVG glyphs; do not infer art approval."""
import argparse
import hashlib
import importlib.util
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

spec = importlib.util.spec_from_file_location('font_reference_guards', Path(__file__).with_name('build-uihd-original-font.py'))
guards = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guards)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pack(root, candidates, output, java):
    guards.require_real_output(output)
    source_bytes = (root / 'gfx/fonts/default.json').read_bytes()
    image_bytes = (root / 'gfx/fonts/default.png').read_bytes()
    if digest(source_bytes) != guards.SOURCE_METADATA_SHA256 or digest(image_bytes) != guards.SOURCE_IMAGE_SHA256:
        raise ValueError('Original font source differs from approved pins')
    source = json.loads(source_bytes)['glyphs']
    if len(source) != 139:
        raise ValueError('Original glyph denominator differs')
    authored = {}
    inputs = []
    for candidate in candidates:
        packet_bytes = (candidate / 'review-packet.json').read_bytes()
        packet = json.loads(packet_bytes)
        if packet['sourceImageSha256'] != guards.SOURCE_IMAGE_SHA256 or packet['sourceMetadataSha256'] != guards.SOURCE_METADATA_SHA256:
            raise ValueError('Candidate source binding differs')
        inputs.append({'packet': str(candidate.relative_to(root)), 'sha256': digest(packet_bytes)})
        for entry in packet['glyphs']:
            key, name = entry['key'], entry['path']
            code = ord(key) if len(key) == 1 else int(key[2:], 16)
            if name != f'u{code:04x}.svg' or key in authored or key not in source:
                raise ValueError('Duplicate or unexpected candidate glyph')
            data = (candidate / name).read_bytes()
            if len(data) > 65536 or digest(data) != entry['sha256'] or entry['sourceMetrics'] != source[key]:
                raise ValueError('Candidate glyph identity or metrics differs')
            if b'<!' in data or b'<?' in data:
                raise ValueError('Vector declarations/entities are not accepted')
            svg = ET.fromstring(data)
            ns = '{http://www.w3.org/2000/svg}'
            paths = list(svg)
            metric = source[key]
            if svg.tag != ns+'svg' or len(paths) != 1 or paths[0].tag != ns+'path' or svg.attrib.get('viewBox') != f'0 0 {max(1,metric["texW"])} {max(1,metric["texH"])}':
                raise ValueError('Vector structure or viewBox differs')
            path = paths[0]
            if set(path.attrib) != {'fill','fill-rule','d'} or path.attrib['fill'] != 'white' or path.attrib['fill-rule'] not in {'evenodd','nonzero'}:
                raise ValueError('Unsupported vector styling')
            authored[key] = (path.attrib.copy(), entry['sha256'])
    if authored.keys() != source.keys():
        raise ValueError(f'Incomplete redraw: {len(authored)}/139; missing {sorted(source.keys()-authored.keys())}')
    extent, scale, gutter = 2048, 8, 16
    packed, groups, ledger = {}, [], []
    x = y = row = 0
    for key in sorted(source, key=lambda key:ord(key) if len(key)==1 else int(key[2:],16)):
        metric = source[key]
        width, height = metric['texW'], metric['texH']
        cell_width, cell_height = width*scale+2*gutter, height*scale+2*gutter
        if cell_width > extent or cell_height > extent:
            raise ValueError('Glyph exceeds atlas bounds')
        if x+cell_width > extent:
            x,y,row=0,y+row,0
        if y+cell_height > extent:
            raise ValueError('Complete redraw exceeds atlas bounds')
        u,v=x+gutter,y+gutter
        group = ET.Element('g', {'transform':f'translate({u} {v}) scale(8)','data-width':str(width),'data-height':str(height)})
        ET.SubElement(group,'path',authored[key][0]);groups.append(group)
        packed[key]=dict(metric,texU=u,texV=v,texW=width*scale,texH=height*scale)
        ledger.append({'key':key,'vectorSha256':authored[key][1],'sourceMetrics':metric,'packedRectangle':[u,v,width*scale,height*scale]})
        x+=cell_width;row=max(row,cell_height)
    atlas=ET.Element('svg',{'xmlns':'http://www.w3.org/2000/svg','width':str(extent),'height':str(extent),'viewBox':f'0 0 {extent} {extent}'})
    atlas.extend(groups);svg_bytes=ET.tostring(atlas,encoding='utf-8')+b'\n'
    guards.require_real_output(output);output.parent.mkdir(parents=True,exist_ok=True);guards.require_real_output(output);output.mkdir();owned=output.stat(follow_symlinks=False)
    try:
        with (output/'original-hd.svg').open('xb') as target:target.write(svg_bytes)
        subprocess.run([str(java),'-Djava.awt.headless=true',str(root/'scripts/RenderUiHdFont.java'),str(output/'original-hd.svg'),str(output/'original-hd.png')],check=True)
        png=(output/'original-hd.png').read_bytes()
        metadata={'format':1,'name':'uihd.font.original_hd.name','lineHeight':12,'glyphCount':139,'sourceImageSha256':guards.SOURCE_IMAGE_SHA256,'sourceMetadataSha256':guards.SOURCE_METADATA_SHA256,'outputSha256':digest(png),'outputSize':[extent,extent],'glyphs':packed}
        raw=(json.dumps(metadata,indent=2)+'\n').encode()
        with (output/'original-hd.json').open('xb') as target:target.write(raw)
        evidence={'status':'complete-redraw-packed-art-runtime-review-pending','glyphCount':139,'sourceImageSha256':guards.SOURCE_IMAGE_SHA256,'sourceMetadataSha256':guards.SOURCE_METADATA_SHA256,'inputs':inputs,'vectorAtlasSha256':digest(svg_bytes),'metadataSha256':digest(raw),'atlasSha256':digest(png),'atlasSize':[extent,extent],'gutterPixels':gutter,'logicalMetricsChanged':0,'runtimeSelected':False,'deviceAccepted':False,'glyphs':ledger}
        with (output/'packing-ledger.json').open('x') as target:target.write(json.dumps(evidence,indent=2)+'\n')
    except BaseException as failure:
        try:
            current=output.stat(follow_symlinks=False)
            if (current.st_dev,current.st_ino)==(owned.st_dev,owned.st_ino):shutil.rmtree(output)
        except OSError as cleanup:failure.add_note(f'Owned incomplete output cleanup failed: {cleanup}')
        raise
    print('Packed139explicitglyphs; exactOriginalmetrics; art/runtime/Deck acceptance separate')


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument('--candidate',type=Path,action='append',required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--java',type=Path,required=True);args=parser.parse_args();pack(args.root,args.candidate,args.output,args.java)
