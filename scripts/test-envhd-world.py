#!/usr/bin/env python3
import importlib.util,json,struct,tempfile,unittest,zlib
from PIL import Image
from pathlib import Path
import numpy as np
p=Path(__file__).with_name('batch-envhd-world.py');s=importlib.util.spec_from_file_location('world_batch',p);world=importlib.util.module_from_spec(s);s.loader.exec_module(world)
def source():
 data=bytearray(11344);struct.pack_into('<III',data,0,16,9,524);struct.pack_into('<HH',data,16,256,1);struct.pack_into('<I',data,532,10812);struct.pack_into('<HH',data,540,60,90);struct.pack_into('<HH',data,22,0x8000,31);data[544:]=bytes(i%3 for i in range(10800));return bytes(data)
class WorldBatchTest(unittest.TestCase):
 def test_exact_palette_and_visibility(self):
  image=world.decode_thumbnail(source());self.assertEqual(image.size,(120,90));self.assertEqual([image.getpixel((i,0)) for i in range(3)],[(0,0,0,0),(0,0,0,255),(248,0,0,255)])
 def test_bad_payload_and_palette_rejected(self):
  for offset in (0,4,8,16,18,532,540,542):
   data=bytearray(source());data[offset]^=1
   with self.assertRaises(ValueError):world.decode_thumbnail(bytes(data))
  with self.assertRaises(ValueError):world.decode_thumbnail(source()[:-1])
 def test_bounded_read_is_exact_snapshot(self):
  with tempfile.TemporaryDirectory() as temp:
   p=Path(temp)/'source';p.write_bytes(source());snapshot=world.bounded_source(p,11344);p.write_bytes(b'changed');self.assertEqual(world.decode_thumbnail(snapshot).getpixel((2,0)),(248,0,0,255));self.assertEqual(world.digest(snapshot),world.digest(source()))
   p.write_bytes(source()+b'oversized')
   with self.assertRaises(ValueError):world.bounded_source(p,11344)
 def test_neural_output_preserves_visibility_and_no_new_black(self):
  image=world.decode_thumbnail(source());candidate=np.zeros((360,480,3),dtype=np.uint8);rgba,_=world.batch.preserve_visibility(image,candidate)
  self.assertEqual(tuple(rgba[0,0]),(0,0,0,0));self.assertEqual(tuple(rgba[0,4]),(0,0,0,255));self.assertEqual(tuple(rgba[0,8]),(248,0,0,255))
 def test_private_staging_rejects_source_and_repo_paths(self):
  with tempfile.TemporaryDirectory() as temp:
   files=Path(temp)/'files';files.mkdir()
   with self.assertRaises(ValueError):world.execute(files,files/'output',Path('unused'),Path('unused'))
   with self.assertRaises(ValueError):world.execute(files,world.ROOT/'output',Path('unused'),Path('unused'))
 def test_missing_tool_identity_does_not_create_output(self):
  with tempfile.TemporaryDirectory() as temp:
   dest=Path(temp)/'output'
   # Only source census reads are skipped; pinned identities still gate execution.
   original=world.inventory;world.inventory=lambda _:[]
   try:
    with self.assertRaises(FileNotFoundError):world.execute(Path(temp)/'files',dest,Path(temp)/'missing-engine',Path(temp)/'models')
    self.assertFalse(dest.exists())
   finally:world.inventory=original
class WorldImportTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);base=Path(self.temp.name)
  p=Path(__file__).with_name('import-envhd-world.py');spec=importlib.util.spec_from_file_location('import_world',p);self.importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.importer)
  self.importer.ROOT=base/'repo';self.files=base/'files';self.staging=base/'staging';self.staging.mkdir()
  original=world.decode_thumbnail(source());key=world.digest(source());self.key=key
  self.entry=dict(kind='location-thumbnail',sourceSha256=key,decodedRgbaSha256=world.digest(struct.pack('<II',*original.size)+original.tobytes()),sourceSize=[120,90],sourceFiles=['SECT/DRGN0.BIN/5655'],locations=['Synthetic scene'])
  self.importer.world.inventory=lambda _: [dict(self.entry,image=original)]
  candidate=original.resize((480,360),Image.Resampling.NEAREST);candidate.save(self.staging/(key+'.png'))
  self.record=dict(self.entry,targetSize=[480,360],outputSha256=world.digest((self.staging/(key+'.png')).read_bytes()),method='real-esrgan-x4plus-clamped-source-layout',scale=4,reviewStatus='pending-source-intent-style-layout-review',sourceVisibility='exact-nearest-4x-discard-and-visible-black',edgeAveraging=False,**self.importer.sky.BATCH_TOOL_HASHES)
  self.notes={key:dict(intent='Synthetic layout',style='Test color',layout='Exact integer source placement',verdict='visual-reviewed-native-pending')};self.review=self.staging/'reviews.json'
 def publish(self):
  (self.staging/'candidates.json').write_text(json.dumps([self.record]));self.review.write_text(json.dumps(self.notes));return self.importer.publish(self.files,self.staging,self.review)
 def test_valid_import_is_repeatable_and_excludes_private_inputs(self):
  self.assertEqual(self.publish(),1);self.assertEqual(self.publish(),1)
  runtime=self.importer.ROOT/'integrations/envhd/runtime-assets/envhd/world/locations'/self.key
  self.assertEqual((runtime/'image-v1.png').read_bytes(),(self.staging/(self.key+'.png')).read_bytes())
  self.assertFalse(any('private-work' in str(p) for p in self.importer.ROOT.rglob('*')))
 def test_wrong_provenance_and_review_reject_before_any_write(self):
  for key in ('sourceSha256','decodedRgbaSha256','outputSha256','method','weightsSha256','sourceVisibility'):
   previous=self.record[key];self.record[key]='wrong'
   with self.assertRaises((ValueError,KeyError)):self.publish()
   self.assertFalse(self.importer.ROOT.exists());self.record[key]=previous
  self.notes[self.key]['verdict']='pending'
  with self.assertRaises(ValueError):self.publish()
  self.assertFalse(self.importer.ROOT.exists())
 def test_changed_alpha_even_with_correct_png_hash_is_rejected(self):
  path=self.staging/(self.key+'.png');candidate=Image.open(path).copy();candidate.putpixel((0,0),(1,1,1,255));candidate.save(path);self.record['outputSha256']=world.digest(path.read_bytes())
  with self.assertRaises(ValueError):self.publish()
  self.assertFalse(self.importer.ROOT.exists())
 def test_existing_version_conflict_preserves_selection(self):
  self.publish();path=self.importer.ROOT/'integrations/envhd/runtime-assets/envhd/world/locations'/self.key/'image-v1.png';previous=path.read_bytes()
  candidate=Image.open(self.staging/(self.key+'.png')).copy();candidate.putpixel((8,0),(230,0,0,255));candidate.save(self.staging/(self.key+'.png'));self.record['outputSha256']=world.digest((self.staging/(self.key+'.png')).read_bytes())
  with self.assertRaises(FileExistsError):self.publish()
  self.assertEqual(path.read_bytes(),previous)
 def test_sixteen_bit_rgba_is_rejected_despite_pillow_rgba_mode(self):
  path=self.staging/(self.key+'.png');image=Image.open(path);pixels=np.asarray(image).astype('>u2')*257
  def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
  rows=b''.join(b'\0'+row.astype('>u2').tobytes() for row in pixels)
  png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',480,360,16,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')
  path.write_bytes(png);self.record['outputSha256']=world.digest(png)
  with Image.open(path) as checked:self.assertEqual(checked.mode,'RGBA')
  with self.assertRaisesRegex(ValueError,'8-bit RGBA'):self.publish()
  self.assertFalse(self.importer.ROOT.exists())
if __name__=='__main__':unittest.main()
