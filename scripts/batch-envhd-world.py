#!/usr/bin/env python3
"""Produce source-faithful world backdrop/location candidates; private originals stay private."""
import argparse,hashlib,importlib.util,json,re,struct,subprocess,sys
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);sys.modules[name]=m;s.loader.exec_module(m);return m
sky=module('envhd_world_sky',ROOT/'scripts/export-envhd-skies.py')
batch=module('envhd_world_batch',ROOT/'scripts/batch-envhd-skies.py')
def digest(data):return hashlib.sha256(data).hexdigest()
def bounded_source(path,limit):
 with path.open('rb') as stream:data=stream.read(limit+1)
 if len(data)>limit:raise ValueError('Oversized private source')
 return data
def decode_thumbnail(data):
 if len(data)!=11344 or struct.unpack_from('<III',data)!=(16,9,524) or struct.unpack_from('<HH',data,16)!=(256,1) or struct.unpack_from('<I',data,532)[0]!=10812 or struct.unpack_from('<HH',data,540)!=(60,90):raise ValueError('Unsupported location thumbnail layout')
 palette=np.frombuffer(data,dtype='<u2',count=256,offset=20);colours=palette[np.frombuffer(data,dtype=np.uint8,count=10800,offset=544)].reshape((90,120))
 rgba=np.stack(((colours&31)<<3,((colours>>5)&31)<<3,((colours>>10)&31)<<3,np.where(colours==0,0,255)),axis=2).astype(np.uint8)
 return Image.fromarray(rgba)
def inventory(files):
 text=(ROOT/'src/main/java/legend/game/wmap/WmapStatics.java').read_text()
 names={}
 for name,index in re.findall(r'new Place0c\(\s*"([^"]*)",\s*(\d+),',text):names.setdefault(int(index),set()).add(name.replace('\\n',' ').strip())
 groups={}
 for index,labels in sorted(names.items()):
  path=files/f'SECT/DRGN0.BIN/{5655+index}';data=bounded_source(path,11344)
  image=decode_thumbnail(data);key=digest(data)
  item=groups.setdefault(key,dict(kind='location-thumbnail',sourceSha256=key,decodedRgbaSha256=digest(struct.pack('<II',*image.size)+image.tobytes()),sourceSize=list(image.size),sourceFiles=[],locations=[],image=image))
  item['sourceFiles'].append(str(path.relative_to(files)));item['locations']=sorted(set(item['locations'])|labels)
 path=files/'SECT/DRGN0.BIN/5696';data=bounded_source(path,262188);image=sky.decode(data);key=digest(data)
 groups[key]=dict(kind='world-backdrop',sourceSha256=key,decodedRgbaSha256=digest(struct.pack('<II',*image.size)+image.tobytes()),sourceSize=list(image.size),sourceFiles=[str(path.relative_to(files))],locations=['World-map parchment and continent drawing'],image=image)
 return list(groups.values())
def execute(files,output,engine,models):
 if output.resolve().is_relative_to(ROOT) or output.resolve().is_relative_to(files.resolve()):raise ValueError('Use private staging outside Git and extraction')
 entries=inventory(files)
 for path,expected in [(engine,batch.ENGINE),(models/'realesrgan-x4plus.bin',batch.WEIGHTS),(models/'realesrgan-x4plus.param',batch.PARAMETERS)]:
  if batch.file_digest(path)!=expected:raise ValueError('Pinned inference tool identity differs')
 output.mkdir(parents=True,exist_ok=False);work=output/'private-work';work.mkdir();records=[]
 for item in entries:
  original=item.pop('image');key=item['sourceSha256'];original.save(work/(key+'-source.png'))
  padded=np.pad(np.asarray(original.convert('RGB')),((16,16),(16,16),(0,0)),mode='edge');Image.fromarray(padded).save(work/(key+'-input.png'))
  run=subprocess.run([str(engine),'-i',str(work/(key+'-input.png')),'-o',str(work/(key+'-inference.png')),'-m',str(models),'-n','realesrgan-x4plus','-s','4','-t','256','-j','1:1:1'],capture_output=True,timeout=180);(work/(key+'.log')).write_bytes(run.stdout+run.stderr)
  if run.returncode:raise RuntimeError('Inference failed; see private log')
  with Image.open(work/(key+'-inference.png')) as inferred:
   if inferred.size!=((original.width+32)*4,(original.height+32)*4):raise ValueError('Inference dimensions changed')
   pixels=np.asarray(inferred.convert('RGB').crop((64,64,64+original.width*4,64+original.height*4)))
  rgba,_=batch.preserve_visibility(original,pixels);Image.fromarray(rgba).save(output/(key+'.png'))
  item.update(targetSize=[original.width*4,original.height*4],outputSha256=batch.file_digest(output/(key+'.png')),method='real-esrgan-x4plus-clamped-source-layout',scale=4,reviewStatus='pending-source-intent-style-layout-review',engineSha256=batch.ENGINE,weightsSha256=batch.WEIGHTS,parametersSha256=batch.PARAMETERS,sourceVisibility='exact-nearest-4x-discard-and-visible-black',edgeAveraging=False)
  records.append(item);(output/'candidates.json').write_text(json.dumps(records,indent=2)+'\n');print(json.dumps({'completed':item['kind'],'locations':item['locations']}),flush=True)
 return records
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--files',type=Path,required=True);p.add_argument('--output',type=Path);p.add_argument('--engine',type=Path);p.add_argument('--models',type=Path);p.add_argument('--execute',action='store_true');a=p.parse_args()
 if a.execute:
  if not all((a.output,a.engine,a.models)):p.error('Execution requires private output, engine and models')
  execute(a.files,a.output,a.engine,a.models)
 else:
  entries=inventory(a.files);print(json.dumps([{k:v for k,v in e.items() if k!='image'} for e in entries],indent=2))
