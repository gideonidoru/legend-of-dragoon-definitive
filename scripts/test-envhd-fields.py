#!/usr/bin/env python3
"""Meaningful field decoder regressions using synthetic descriptors and palettes."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock
from PIL import Image
import io
import numpy as np

spec=importlib.util.spec_from_file_location('fields',Path(__file__).with_name('census-envhd-fields.py'))
fields=importlib.util.module_from_spec(spec);spec.loader.exec_module(fields)


def block(x,y,w,h,words):
    return struct.pack('<I4H',12+w*h*2,x,y,w,h)+struct.pack('<'+'H'*(w*h),*words)


def tim(x=0,y=0,w=2,h=2,words=None,palette=None):
    palette=palette or [0,31,0x8000]+[0]*13
    return struct.pack('<II',16,8)+block(768,496,16,1,palette)+block(x,y,w,h,words or [0x1111]*(w*h))


def descriptor(slots,backgrounds):
    data=bytearray(24+36*len(slots));data[20:23]=bytes((len(slots),backgrounds,len(slots)-backgrounds))
    for i,values in enumerate(slots):
        u,v,w,h,x,y,page=values;off=24+36*i
        struct.pack_into('<6h',data,off+8,u,v,w,h,x,y);struct.pack_into('<Hh',data,off+32,page,496)
    return bytes(data)


class FieldsTest(unittest.TestCase):
    def test_cpu_palette_visibility_preserves_stp_black_and_zero(self):
        _,rgba=fields.tim(tim(words=[0x2010]*4))
        self.assertEqual([[0,0,0,0],[248,0,0,255],[0,0,0,0],[0,0,0,255]],rgba[0,:4].tolist())

    def test_first_palette_row_not_descriptor_clut_or_final_vram(self):
        data=tim();self.assertEqual([248,0,0,255],fields.tim(data)[1][0,0].tolist())
        _,rgba=fields.tim(data);self.assertEqual(np.uint8,rgba.dtype)

    def test_canvas_includes_foreground_and_signed_placement(self):
        sources={0:descriptor([(0,0,4,2,-2,-1,0),(0,0,2,2,3,4,0)],1),3:tim()}
        bg,layers,meta=fields.render(sources,0)
        self.assertEqual([ -2,-1,7,7],meta['canvasRect']);self.assertEqual((7,7),bg.size)
        self.assertEqual([5,5,2,2],meta['foregrounds'][0]['canvasPlacement']);self.assertFalse(meta['foregrounds'][0]['canDeriveFromBackground'])

    def test_transparent_background_upload_overwrites_previous_tile(self):
        sources={0:descriptor([(0,0,2,2,0,0,0),(4,0,2,2,0,0,0)],2),3:tim(words=[0x1111,0x0000]*2)}
        bg,_,_=fields.render(sources,0)
        self.assertEqual((0,0,0,0),bg.getpixel((0,0)))

    def test_first_matching_tim_is_used_in_directory_order(self):
        sources={0:descriptor([(0,0,2,2,0,0,0)],1),3:tim(),4:tim(words=[0x2222]*4)}
        bg,_,_=fields.render(sources,0);self.assertEqual((248,0,0,255),bg.getpixel((0,0)))

    def test_oversized_neet_foreground_clamps_only_foreground(self):
        sources={0:descriptor([(0,0,2,2,0,0,0),(0,0,4,5,0,0,0)],1),3:tim()}
        _,layers,meta=fields.render(sources,0);self.assertEqual((4,2),layers[0].size);self.assertEqual([0,0,4,2],meta['foregrounds'][0]['canvasPlacement'])
        sources[0]=descriptor([(0,0,4,5,0,0,0)],1)
        with self.assertRaises(ValueError):fields.render(sources,0)

    def test_zero_and_missing_native_foreground_keep_slot_identity(self):
        sources={0:descriptor([(0,0,2,2,0,0,0),(0,0,0,0,0,0,0),(0,0,2,2,0,0,1)],1),3:tim()}
        _,layers,meta=fields.render(sources,0);self.assertEqual([None,None],layers);self.assertEqual([2],meta['missingNativeTextureSlots'])
        self.assertTrue(meta['foregrounds'][1]['missingNativeTexture']);self.assertEqual(2,meta['foregroundSlots'])

    def test_native_cut_fixes_are_part_of_render_coordinates(self):
        data=descriptor([(0,0,2,2,0,0,0)]*18,1)
        for cut,index in [(111,8),(288,17),(595,5)]:
            _,_,slots=fields.slots(data,cut);self.assertEqual(1,slots[index]['y'])
        _,_,slots=fields.slots(data,642);self.assertEqual(1,slots[2]['w'])
        _,_,slots=fields.slots(data,0);self.assertEqual(2,slots[2]['w'])

    def test_independent_foreground_is_derived_only_for_exact_visible_pixels(self):
        sources={0:descriptor([(0,0,2,2,0,0,0),(0,0,2,2,0,0,0)],1),3:tim()}
        _,_,meta=fields.render(sources,0);self.assertTrue(meta['foregrounds'][0]['canDeriveFromBackground'])
        sources[0]=descriptor([(0,0,2,2,0,0,0),(4,0,2,2,0,0,0)],1);sources[3]=tim(words=[0x1111,0x2222]*2)
        _,_,meta=fields.render(sources,0);self.assertFalse(meta['foregrounds'][0]['canDeriveFromBackground'])

    def test_mrg_aliases_are_ordered_and_cycle_missing_duplicate_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);(p/'0').write_bytes(b'desc');(p/'3').write_bytes(b'tim')
            (p/'mrg').write_text('4=3;3\n0=0;4\n3=3;3\n5=;0\n');self.assertEqual([0,3,4],list(fields.entries(p)));self.assertEqual(b'tim',fields.entries(p)[4])
            for text in ['0=1;4\n1=0;4\n','0=99;4\n','0=;4\n','0=0;4\n0=0;4\n']:
                (p/'mrg').write_text(text)
                with self.assertRaises(ValueError):fields.entries(p)

    def test_bounds_trailing_blocks_and_descriptor_budgets(self):
        for data in [tim()+b'x',tim()[:12],struct.pack('<II',16,2)+b'x'*64]:
            with self.assertRaises(ValueError):fields.tim(data)
        for data in [b'123',descriptor([(0,0,2,2,0,0,0)],1)+b'x']:
            with self.assertRaises(ValueError):fields.slots(data,0)
        with self.assertRaises(ValueError):fields.render({0:descriptor([(0,0,2,2,0,0,0),(0,0,1,1,4000,0,0)],1),3:tim()},0)


batch_spec=importlib.util.spec_from_file_location('field_batch',Path(__file__).with_name('batch-envhd-fields.py'))
batch=importlib.util.module_from_spec(batch_spec);batch_spec.loader.exec_module(batch)


class FieldBatchTest(unittest.TestCase):
    def item(self):
        image=Image.fromarray(np.array([[[248,0,0,255],[0,0,0,0]],[[0,0,0,255],[8,8,8,255]]],dtype=np.uint8))
        return dict(image=image,decodedRgbaSha256=batch.terrain.fingerprint(image),sourceSize=[2,2],owners=['envhd'],bindings=[dict(kind='foreground',slot=0,renderKey='synthetic')])

    def png(self,item):
        source=np.asarray(item['image']);rgb=np.repeat(np.repeat(source[:,:,:3],4,axis=0),4,axis=1)
        restored,_=batch.terrain.preserve_stp(item['image'],rgb)
        stream=io.BytesIO();restored.save(stream,format='PNG');return stream.getvalue()

    def test_candidate_requires_exact_alpha_black_dimensions_and_output_hash(self):
        item=self.item();data=self.png(item);record=batch.candidate_record(item,batch.terrain.digest(data));batch.validate(item,record,data)
        image=Image.open(io.BytesIO(data));pixels=np.asarray(image).copy();pixels[0,4,3]=255
        stream=io.BytesIO();Image.fromarray(pixels).save(stream,format='PNG');bad=stream.getvalue()
        with self.assertRaises(ValueError):batch.validate(item,batch.candidate_record(item,batch.terrain.digest(bad)),bad)
        pixels=np.asarray(image).copy();pixels[4,0,:3]=[248,0,0];stream=io.BytesIO();Image.fromarray(pixels).save(stream,format='PNG');bad=stream.getvalue()
        with self.assertRaises(ValueError):batch.validate(item,batch.candidate_record(item,batch.terrain.digest(bad)),bad)
        with self.assertRaises(ValueError):batch.validate(item,record|dict(outputSha256='0'*64),data)
        with self.assertRaises(ValueError):batch.validate(item,record|dict(nativeAccepted=True),data)

    def test_protected_source_never_read_and_unowned_missing_job_fails(self):
        protected=dict(owner='skurfa',representative=dict(bank=21,directory=999,cut=0))
        masters,uniform=batch.sources(Path('/does/not/exist'),dict(assets=[],renders=[protected]));self.assertEqual([],masters);self.assertEqual([],uniform)
        item=self.item();asset={k:v for k,v in item.items() if k!='image'}
        with self.assertRaises(ValueError):batch.sources(Path('/does/not/exist'),dict(assets=[asset],renders=[protected]))

    def test_sources_verify_current_descriptor_pixels_and_uniform_exclusion(self):
        with tempfile.TemporaryDirectory() as tmp:
            files=Path(tmp);folder=files/'SECT/DRGN21.BIN/4';folder.mkdir(parents=True)
            values={0:descriptor([(0,0,2,2,0,0,0)],1),3:tim()}
            for i,data in values.items():(folder/str(i)).write_bytes(data)
            (folder/'mrg').write_text('0=0;60\n3=3;64\n')
            bg,layers,meta=fields.render(values,0);key=fields.terrain.fingerprint(bg)
            scene=dict(owner='envhd',representative=dict(bank=21,directory=4,cut=0),sourceFiles=[dict(index=i,sha256=fields.terrain.digest(d),size=len(d)) for i,d in values.items()],images=[dict(decodedRgbaSha256=key)],**meta)
            report=dict(assets=[dict(decodedRgbaSha256=key,sourceSize=list(bg.size),owners=['envhd'],bindings=[])],renders=[scene])
            masters,uniform=batch.sources(files,report);self.assertEqual([],masters);self.assertEqual(1,len(uniform));self.assertEqual('retain-native-uniform',uniform[0]['status'])
            (folder/'3').write_bytes(tim(words=[0x2222]*4))
            with self.assertRaises(ValueError):batch.sources(files,report)

    def test_generation_is_resumable_and_rejects_symlinked_staging_child(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);files=base/'files';files.mkdir();engine=base/'engine';engine.write_bytes(b'engine');models=base/'models';models.mkdir()
            (models/'realesrgan-x4plus.bin').write_bytes(b'weights');(models/'realesrgan-x4plus.param').write_bytes(b'params')
            pins={k:batch.terrain.digest(v) for k,v in [('engineSha256',b'engine'),('weightsSha256',b'weights'),('parametersSha256',b'params')]}
            item=self.item();output=base/'private';report=dict(protectedSharedPixelImages=1)
            calls=[]
            def inference(command,**kwargs):
                calls.append(command);im=Image.open(command[command.index('-i')+1]);im.resize((im.width*4,im.height*4),Image.Resampling.NEAREST).save(command[command.index('-o')+1]);return mock.Mock(returncode=0,stdout=b'',stderr=b'')
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])),mock.patch.object(batch.subprocess,'run',side_effect=inference):
                records=batch.execute(files,output,engine,models);self.assertEqual(1,len(records));batch.execute(files,output,engine,models);self.assertEqual(1,len(calls))
                report['changed']=True
                with self.assertRaises(ValueError):batch.execute(files,output,engine,models)
            redirected=base/'redirected';redirected.mkdir();(redirected/'private-work').symlink_to(base/'outside')
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])):
                with self.assertRaises(ValueError):batch.execute(files,redirected,engine,models)
                self.assertFalse((base/'outside').exists())


if __name__=='__main__':unittest.main()
