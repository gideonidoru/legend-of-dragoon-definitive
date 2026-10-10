#!/usr/bin/env python3
"""Publish reviewed custom world artwork only; verify all source/output identities first."""
import argparse,importlib.util,io,json
from pathlib import Path
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
world=module('envhd_world',ROOT/'scripts/batch-envhd-world.py')
sky=module('envhd_world_import',ROOT/'scripts/import-envhd-sky.py')
def publish(files,staging,reviews):
 if staging.resolve().is_relative_to(ROOT) or staging.resolve().is_relative_to(files.resolve()):raise ValueError('Staging must be private and separate')
 expected={e['sourceSha256']:e for e in world.inventory(files)}
 records=json.loads(sky.read_bytes(staging/'candidates.json',1024*1024));notes=json.loads(sky.read_bytes(reviews,1024*1024))
 if len(records)!=len(expected) or len({r['sourceSha256'] for r in records})!=len(expected) or set(notes)!=set(expected):raise ValueError('Requires complete, unique reviewed world batch')
 writes={};public=[];production=ROOT/'integrations/envhd/production';runtime=ROOT/'integrations/envhd/runtime-assets'
 for item in records:
  key=item['sourceSha256'];source=expected[key];image=source.pop('image');review=notes[key]
  if set(review)!={'intent','style','layout','verdict'} or any(not isinstance(v,str) or not v.strip() for v in review.values()) or review['verdict'] not in {'visual-reviewed-native-pending','intent-revision-needed'}:raise ValueError('Explicit source intent/style/layout review required')
  if any(item.get(k)!=v for k,v in source.items()) or item.get('scale')!=4 or item.get('targetSize')!=[x*4 for x in image.size] or item.get('method')!='real-esrgan-x4plus-clamped-source-layout' or item.get('sourceVisibility')!='exact-nearest-4x-discard-and-visible-black' or item.get('edgeAveraging') is not False or item.get('reviewStatus')!='pending-source-intent-style-layout-review' or any(item.get(k)!=v for k,v in sky.BATCH_TOOL_HASHES.items()):raise ValueError('World source or method provenance differs')
  png=sky.read_bytes(staging/(key+'.png'))
  if sky.digest(png)!=item['outputSha256']:raise ValueError('World output bytes differ')
  if len(png)<33 or png[:8]!=b'\x89PNG\r\n\x1a\n' or png[8:16]!=b'\0\0\0\rIHDR' or png[24]!=8 or png[25]!=6:raise ValueError('Requires runtime-compatible 8-bit RGBA PNG')
  with Image.open(io.BytesIO(png)) as candidate:
   if candidate.format!='PNG' or list(candidate.size)!=item['targetSize'] or candidate.mode!='RGBA':raise ValueError('World PNG layout differs')
   pixels=np.asarray(candidate)
  original=np.repeat(np.repeat(np.asarray(image),4,axis=0),4,axis=1)
  protected=(original[:,:,3]==0)|np.all(original[:,:,:3]==0,axis=2)
  if not np.array_equal(pixels[:,:,3],original[:,:,3]) or np.any(pixels[protected,:3]!=0) or np.any(np.all(pixels[~protected,:3]==0,axis=1)):raise ValueError('World visibility or visible-black classes differ')
  metadata=dict(item,reviewStatus=review['verdict'],review=review,styleReferenceUse='Skurfa visual review only; protected assets unchanged',quality='Source-faithful development baseline; native gameplay/Steam Deck and Skurfa-equivalent bespoke detail pending',ownerApproval='Batch pipeline and Python repair explicitly authorized 2026-10-10')
  base=production/'world-candidates'/key
  writes[base/'image-v1.png']=png;writes[base/'manifest-v1.json']=sky.json_bytes(metadata)
  if review['verdict']=='visual-reviewed-native-pending':
   if item['kind']=='world-backdrop':
    runtime_meta=dict(sourceMcqSha256=key,decodedRgbaSha256=item['decodedRgbaSha256'],outputSha256=item['outputSha256'],scale=4,reviewStatus=review['verdict'],imageFile='image-v1.png')
    writes[runtime/'envhd/world/backdrops'/key/'manifest.json']=sky.json_bytes(runtime_meta)
    writes[runtime/'envhd/sky-images'/item['decodedRgbaSha256']/'image-v1.png']=png
   else:
    runtime_meta={k:metadata[k] for k in ('sourceSha256','decodedRgbaSha256','outputSha256','scale','reviewStatus')}
    writes[runtime/'envhd/world/locations'/key/'manifest.properties']=(''.join(f'{k}={v}\n' for k,v in runtime_meta.items())).encode()
    writes[runtime/'envhd/world/locations'/key/'image-v1.png']=png
  public.append(metadata)
 for path,data in writes.items():sky.check_version(data,path)
 writes[production/'world-artwork.json']=sky.json_bytes({'schema':1,'assets':public,'selected':sum(r['reviewStatus']=='visual-reviewed-native-pending' for r in public),'nativeAccepted':0})
 sky.publish(writes,set());return len(public)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--files',type=Path,required=True);p.add_argument('--staging',type=Path,required=True);p.add_argument('--reviews',type=Path,required=True);a=p.parse_args();print('Published reviewed world candidates:',publish(a.files,a.staging,a.reviews))
