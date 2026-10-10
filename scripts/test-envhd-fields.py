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
import copy
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

    def test_complete_alias_worklist_cannot_shrink_while_pixel_counts_stay_equal(self):
        ownership=dict(scenes=[dict(sourceSignature='same',mappings=[dict(cut=0,period='early',bank=21,file=4),dict(cut=1,period='early',bank=21,file=7)])])
        configs={'same':dict(sourceSignature='same',mappings=[dict(cut=0,period='early',bank=21,directory=4),dict(cut=1,period='early',bank=21,directory=7)])}
        fields.verify_complete_worklist(ownership,configs)
        configs['same']['mappings'].pop()
        with self.assertRaises(ValueError):fields.verify_complete_worklist(ownership,configs)
        configs['same']['mappings'].append(dict(cut=1,period='early',bank=22,directory=7))
        with self.assertRaises(ValueError):fields.verify_complete_worklist(ownership,configs)

    def test_present_configuration_cannot_hide_a_missing_render_or_failed_route(self):
        route=dict(cut=111,period='early',bank=21,directory=4)
        group=dict(sourceSignature='same',mappings=[route],renders=['same:111'])
        configs={'same':group};renders={'same:111':{}}
        fields.verify_complete_renders(configs,renders,[])
        group['renders']=[]
        with self.assertRaises(ValueError):fields.verify_complete_renders(configs,renders,[])
        group['renders']=['same:111']
        with self.assertRaises(ValueError):fields.verify_complete_renders(configs,{},[])
        with self.assertRaises(ValueError):fields.verify_complete_renders(configs,renders,[route|dict(status='unresolved-source-or-decode')])
        # Unused/sentinel routes remain inventoried without becoming image jobs.
        fields.verify_complete_renders(configs,renders,[dict(cut=900,period='late',status='non-retail-disc-selector')])

    def test_bounds_trailing_blocks_and_descriptor_budgets(self):
        for data in [tim()+b'x',tim()[:12],struct.pack('<II',16,2)+b'x'*64]:
            with self.assertRaises(ValueError):fields.tim(data)
        for data in [b'123',descriptor([(0,0,2,2,0,0,0)],1)+b'x']:
            with self.assertRaises(ValueError):fields.slots(data,0)
        with self.assertRaises(ValueError):fields.render({0:descriptor([(0,0,2,2,0,0,0),(0,0,1,1,4000,0,0)],1),3:tim()},0)


batch_spec=importlib.util.spec_from_file_location('field_batch',Path(__file__).with_name('batch-envhd-fields.py'))
batch=importlib.util.module_from_spec(batch_spec);batch_spec.loader.exec_module(batch)
publication_spec=importlib.util.spec_from_file_location('field_publication',Path(__file__).with_name('import-envhd-fields.py'))
publication=importlib.util.module_from_spec(publication_spec);publication_spec.loader.exec_module(publication)
repair_spec=importlib.util.spec_from_file_location('field_repair',Path(__file__).with_name('repair-envhd-field-edges.py'))
repair=importlib.util.module_from_spec(repair_spec);repair_spec.loader.exec_module(repair)


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
            item=self.item();output=base/'private';report=dict(protectedSharedPixelImages=1,pipeline='envhd-field-visible-pixel-census-1',pipelineSha256=batch.LEGACY_DEPENDENCIES['census-envhd-fields.py'])
            calls=[]
            def inference(command,**kwargs):
                calls.append(command)
                input_dir=Path(command[command.index('-i')+1]);output_dir=Path(command[command.index('-o')+1])
                for path in input_dir.iterdir():
                    im=Image.open(path);im.resize((im.width*4,im.height*4),Image.Resampling.NEAREST).save(output_dir/path.name)
                return mock.Mock(returncode=0,stdout=b'',stderr=b'')
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])),mock.patch.object(batch.subprocess,'run',side_effect=inference):
                records=batch.execute(files,output,engine,models);self.assertEqual(1,len(records));batch.execute(files,output,engine,models);self.assertEqual(1,len(calls))
                report['changed']=True
                with self.assertRaises(ValueError):batch.execute(files,output,engine,models)
            # Reuse validates every source/output and records prior plan provenance.
            reused_output=base/'reused'
            report.pop('changed')
            directory_plan=batch.read_control(output/'source-plan.json')
            legacy_plan=directory_plan|dict(pipeline='envhd-complete-field-batch-1',scriptSha256=batch.LEGACY_SCRIPT_SHA256,dependencySha256=batch.LEGACY_DEPENDENCIES)
            batch.battle.write_json(output/'source-plan.json',legacy_plan)
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])),mock.patch.object(batch.subprocess,'run',side_effect=inference):
                before=len(calls);batch.execute(files,reused_output,engine,models,output);self.assertEqual(before,len(calls))
                plan=batch.read_control(reused_output/'source-plan.json');self.assertEqual(records[0]['outputSha256'],plan['reusedOutputs'][0]['outputSha256'])
                self.assertEqual(batch.LEGACY_GENERATION_COMMIT,plan['reusedOutputs'][0]['originGenerationCommit'])
                batch.execute(files,reused_output,engine,models,output);self.assertEqual(before,len(calls))
                altered=dict(item,sourceSize=[3,3])
                with mock.patch.object(batch,'sources',return_value=([altered],[])):
                    with self.assertRaises(ValueError):batch.execute(files,base/'bad-reuse',engine,models,output)
                # Matching local hashes cannot falsely retain old provenance.
                path=reused_output/(item['decodedRgbaSha256']+'.png')
                with Image.open(path) as im:pixels=np.asarray(im).copy()
                pixels[0,0,:3]=[120,32,8]
                Image.fromarray(pixels).save(path)
                changed_record=batch.candidate_record(item,batch.terrain.digest(path.read_bytes()))
                batch.validate(item,changed_record,path.read_bytes())
                batch.battle.write_json(reused_output/'candidates.json',[changed_record])
                controls_before=(reused_output/'candidates.json').read_bytes()
                with self.assertRaisesRegex(ValueError,'pinned reuse origin'):batch.execute(files,reused_output,engine,models,output)
                self.assertEqual(controls_before,(reused_output/'candidates.json').read_bytes())
            # A partially recorded directory chunk resumes with fixed input membership.
            import json
            batch.battle.write_json(output/'source-plan.json',directory_plan)
            (output/'candidates.json').write_text('[]')
            (output/(item['decodedRgbaSha256']+'.png')).unlink()
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])),mock.patch.object(batch.subprocess,'run',side_effect=inference):
                resumed=batch.execute(files,output,engine,models);self.assertEqual(1,len(resumed))
            redirected=base/'redirected';redirected.mkdir();(redirected/'private-work').symlink_to(base/'outside')
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=report),mock.patch.object(batch,'sources',return_value=([item],[])):
                with self.assertRaises(ValueError):batch.execute(files,redirected,engine,models)
                self.assertFalse((base/'outside').exists())

    def test_reuse_rejects_unknown_code_and_unverified_ancestry(self):
        plan=dict(schema=1,pipeline='envhd-complete-field-batch-1',scriptSha256=batch.LEGACY_SCRIPT_SHA256,
                  dependencySha256=batch.LEGACY_DEPENDENCIES,
                  sourceCensus=dict(pipeline='envhd-field-visible-pixel-census-1',pipelineSha256=batch.LEGACY_DEPENDENCIES['census-envhd-fields.py']))
        batch.verify_reuse_origin(plan)
        changes=[dict(schema=2),dict(pipeline='unreviewed'),dict(scriptSha256='0'*64),
                 dict(dependencySha256=batch.LEGACY_DEPENDENCIES|{'census-envhd-fields.py':'0'*64}),
                 dict(reusedOutputs=[]),dict(sourceCensus=plan['sourceCensus']|dict(pipelineSha256='0'*64)),
                 dict(sourceCensus=plan['sourceCensus']|dict(pipeline='unknown'))]
        for change in changes:
            with self.subTest(change=change),self.assertRaisesRegex(ValueError,'pinned predecessor'):
                batch.verify_reuse_origin(copy.deepcopy(plan)|change)

    def test_resume_after_completed_chunk_keeps_later_chunk_identity(self):
        import contextlib,json
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);files=base/'files';files.mkdir();engine=base/'engine';engine.write_bytes(b'engine');models=base/'models';models.mkdir()
            (models/'realesrgan-x4plus.bin').write_bytes(b'weights');(models/'realesrgan-x4plus.param').write_bytes(b'params')
            pins={k:batch.terrain.digest(v) for k,v in [('engineSha256',b'engine'),('weightsSha256',b'weights'),('parametersSha256',b'params')]}
            items=[]
            for i in range(129):
                item=self.item();pixels=np.asarray(item['image']).copy();pixels[0,0,0]=i+1;item['image']=Image.fromarray(pixels);item['decodedRgbaSha256']=batch.terrain.fingerprint(item['image']);items.append(item)
            calls=[]
            def inference(command,**kwargs):
                input_dir=Path(command[command.index('-i')+1]);output_dir=Path(command[command.index('-o')+1]);calls.append(input_dir.name)
                for path in input_dir.iterdir():
                    im=Image.open(path);im.resize((im.width*4,im.height*4),Image.Resampling.NEAREST).save(output_dir/path.name)
                return mock.Mock(returncode=0,stdout=b'',stderr=b'')
            output=base/'private'
            with mock.patch.dict(batch.terrain.PINS,pins),mock.patch.object(batch.fields,'census',return_value=dict(protectedSharedPixelImages=0)),mock.patch.object(batch,'sources',return_value=(items,[])),mock.patch.object(batch.subprocess,'run',side_effect=inference),contextlib.redirect_stdout(io.StringIO()):
                records=batch.execute(files,output,engine,models);self.assertEqual(['inputs-0000','inputs-0001'],calls)
                # Simulate an interruption after the first 128 candidates were recorded.
                (output/'candidates.json').write_text(json.dumps(records[:128]))
                (output/(records[128]['decodedRgbaSha256']+'.png')).unlink()
                resumed=batch.execute(files,output,engine,models);self.assertEqual(129,len(resumed));self.assertEqual(['inputs-0000','inputs-0001','inputs-0001'],calls)



class FieldPublicationTest(unittest.TestCase):
    def fixture(self):
        item=FieldBatchTest().item();masters=[item]
        metadata={k:v for k,v in item.items() if k!='image'}
        report=dict(pipeline='envhd-field-visible-pixel-census-1',pipelineSha256=publication.DIRECTORY_DEPENDENCIES['census-envhd-fields.py'],sourceConfigurations=1)
        legacy=dict(schema=1,pipeline='envhd-complete-field-batch-1',scriptSha256=publication.batch.LEGACY_SCRIPT_SHA256,
                    dependencySha256=publication.batch.LEGACY_DEPENDENCIES,sourceCensus=report|dict(pipelineSha256=publication.batch.LEGACY_DEPENDENCIES['census-envhd-fields.py']),
                    retainedUniformSources=[],masters=[metadata])
        data=publication.publication.json_bytes(legacy)
        png=FieldBatchTest().png(item);record=publication.batch.candidate_record(item,publication.terrain.digest(png))
        plan=dict(schema=1,pipeline='envhd-complete-field-directory-batch-2',scriptSha256=publication.DIRECTORY_SCRIPT_SHA256,
                  dependencySha256=publication.DIRECTORY_DEPENDENCIES,scope=publication.SCOPE,sourceCensus=report,retainedUniformSources=[],masters=[metadata],
                  reusedOutputs=[dict(decodedRgbaSha256=record['decodedRgbaSha256'],outputSha256=record['outputSha256'],
                                      originPlanSha256=publication.terrain.digest(data),originScriptSha256=publication.batch.LEGACY_SCRIPT_SHA256)])
        note=dict(intent='Preserve the synthetic red surface.',style='Development baseline; final painted detail pending.',layout='Independent source pixels and mask preserved.',
                  verdict='visual-reviewed-runtime-pending',outputSha256=record['outputSha256'],nativeAcceptance='pending',finalQualityAcceptance='pending')
        return item,report,data,record,plan,png,note

    def test_publication_requires_complete_reviews_and_exact_both_generator_histories(self):
        item,report,data,record,plan,png,note=self.fixture();masters=[item];notes={item['decodedRgbaSha256']:note}
        publication.verify_plans(plan,report,masters,[],data,[record])
        publication.verify_complete_records(masters,[record],notes);publication.verify_review(note,record)
        for records,reviews in [([],notes),([record,record],notes),([record],{}),([record],notes|{'unknown':note})]:
            with self.assertRaises(ValueError):publication.verify_complete_records(masters,records,reviews)
        for changed in [plan|dict(scriptSha256='0'*64),plan|dict(reusedOutputs=[]),plan|dict(sourceCensus=report|dict(sourceConfigurations=2))]:
            with self.assertRaises(ValueError):publication.verify_plans(changed,report,masters,[],data,[record])
        for changed in [note|dict(nativeAcceptance='accepted'),note|dict(outputSha256='0'*64),note|dict(style=''),note|dict(verdict='native-accepted')]:
            with self.assertRaises(ValueError):publication.verify_review(changed,record)

    def test_publication_preflights_reused_png_and_writes_complete_ledger_last(self):
        import json
        item,report,data,record,plan,png,note=self.fixture();key=item['decodedRgbaSha256']
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);files=base/'files';files.mkdir();root=base/'repo';root.mkdir()
            staging=base/'private';legacy=base/'legacy'
            for folder,source_plan in [(staging,publication.publication.json_bytes(plan)),(legacy,data)]:
                folder.mkdir();(folder/'source-plan.json').write_bytes(source_plan)
                (folder/'candidates.json').write_text(json.dumps([record]));(folder/(key+'.png')).write_bytes(png)
            review=base/'reviews.json';review.write_text(json.dumps(dict(reviews={key:note})))
            with mock.patch.object(publication,'ROOT',root),mock.patch.object(publication.batch.fields,'census',return_value=report),mock.patch.object(publication.batch,'sources',return_value=([item],[])):
                # A valid local color edit with updated hash still cannot inherit legacy provenance.
                pixels=np.asarray(Image.open(io.BytesIO(png))).copy();pixels[0,0,:3]=[128,16,8]
                Image.fromarray(pixels).save(staging/(key+'.png'))
                altered=record|dict(outputSha256=publication.terrain.digest((staging/(key+'.png')).read_bytes()))
                (staging/'candidates.json').write_text(json.dumps([altered]));review.write_text(json.dumps(dict(reviews={key:note|dict(outputSha256=altered['outputSha256'])})))
                with self.assertRaisesRegex(ValueError,'immutable predecessor'):publication.publish(files,staging,legacy,review)
                self.assertEqual([],list(root.iterdir()))
                (staging/(key+'.png')).write_bytes(png);(staging/'candidates.json').write_text(json.dumps([record]));review.write_text(json.dumps(dict(reviews={key:note})))
                ledger=root/'integrations/envhd/production/field-artwork.json'
                actual=publication.publication.atomic_write
                def fail_manifest(path,value):
                    if path.name=='manifest-v1.json':raise OSError('Synthetic interrupted import')
                    return actual(path,value)
                with mock.patch.object(publication.publication,'atomic_write',side_effect=fail_manifest):
                    with self.assertRaises(OSError):publication.publish(files,staging,legacy,review)
                self.assertFalse(ledger.exists())
                self.assertEqual(1,publication.publish(files,staging,legacy,review))
                self.assertEqual(1,publication.publish(files,staging,legacy,review))
                result=json.loads(ledger.read_bytes());self.assertEqual(0,result['runtimeSelected']);self.assertEqual(0,result['nativeAccepted'])
                self.assertEqual(publication.batch.LEGACY_GENERATION_COMMIT,result['assets'][0]['generationCommit'])
                public_png=root/'integrations/envhd/production/field-candidates'/key/'image-v1.png'
                self.assertEqual(png,public_png.read_bytes())
                public_png.write_bytes(b'altered immutable version')
                with self.assertRaises(FileExistsError):publication.publish(files,staging,legacy,review)


class FieldRepairTest(unittest.TestCase):
    def test_source_color_region_and_one_pixel_perimeter_are_exact_without_averaging(self):
        source=np.full((10,12,4),[40,32,24,255],dtype=np.uint8);source[2:6,3:7]=[0,248,0,255]
        source[0,0]=[0,0,0,0];source[9,11]=[0,0,0,255]
        item=dict(image=Image.fromarray(source),decodedRgbaSha256=repair.terrain.fingerprint(Image.fromarray(source)),sourceSize=[12,10],owners=['envhd'],bindings=[dict(kind='background')])
        rgb=np.full((40,48,3),[56,40,32],dtype=np.uint8)
        rgb[8:24,12:28]=[8,240,8]
        inferred,_=repair.terrain.preserve_stp(item['image'],rgb);stream=io.BytesIO();inferred.save(stream,format='PNG');data=stream.getvalue()
        pixels=repair.repaired_pixels(item,data)
        expected_guard=np.zeros((10,12),dtype=bool);expected_guard[1:7,2:8]=True
        self.assertTrue(np.array_equal(expected_guard,repair.guard(item)))
        enlarged=np.repeat(np.repeat(expected_guard,4,axis=0),4,axis=1)
        native=np.repeat(np.repeat(source,4,axis=0),4,axis=1)
        self.assertTrue(np.array_equal(pixels[enlarged],native[enlarged]))
        self.assertTrue(np.array_equal(pixels[~enlarged],np.asarray(inferred)[~enlarged]))
        input_record=repair.batch.candidate_record(item,repair.terrain.digest(data))
        stream=io.BytesIO();Image.fromarray(pixels).save(stream,format='PNG');output=stream.getvalue()
        r=repair.record(item,input_record,repair.terrain.digest(output));repair.validate(item,input_record,data,r,output)
        self.assertNotIn('engineSha256',r);self.assertEqual(input_record,r['inputCandidate'])
        pixels[0,4,:3]=[72,40,32];stream=io.BytesIO();Image.fromarray(pixels).save(stream,format='PNG');changed=stream.getvalue()
        with self.assertRaisesRegex(ValueError,'exact source-color'):repair.validate(item,input_record,data,repair.record(item,input_record,repair.terrain.digest(changed)),changed)

    def test_small_saturated_accents_and_independent_foregrounds_are_not_guarded(self):
        source=np.full((10,12,4),[40,32,24,255],dtype=np.uint8);source[2:5,3:7]=[0,248,0,255]
        item=dict(image=Image.fromarray(source),bindings=[dict(kind='background')])
        self.assertFalse(repair.guard(item).any())
        source[2:6,3:7]=[0,248,0,255];item['image']=Image.fromarray(source);item['bindings']=[dict(kind='foreground')]
        self.assertFalse(repair.guard(item).any())
        item['bindings']=[dict(kind='background')];source[2:6,3:7,3]=0;item['image']=Image.fromarray(source)
        self.assertFalse(repair.guard(item).any())

    def test_repair_batch_requires_whole_source_history_and_resumes_without_mutating_input(self):
        import json,contextlib
        item,report,data,input_record,plan,png,note=FieldPublicationTest().fixture();key=item['decodedRgbaSha256']
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);files=base/'files';files.mkdir();staging=base/'neural';legacy=base/'legacy';output=base/'repaired'
            for path,control in [(staging,publication.publication.json_bytes(plan)),(legacy,data)]:
                path.mkdir();(path/'source-plan.json').write_bytes(control);(path/'candidates.json').write_text(json.dumps([input_record]));(path/(key+'.png')).write_bytes(png)
            with mock.patch.object(repair,'module',return_value=publication),mock.patch.object(repair.batch.fields,'census',return_value=report),mock.patch.object(repair.batch,'sources',return_value=([item],[])),contextlib.redirect_stdout(io.StringIO()):
                records=repair.execute(files,staging,legacy,output);self.assertEqual(1,len(records))
                self.assertEqual(records,repair.execute(files,staging,legacy,output));self.assertEqual(png,(staging/(key+'.png')).read_bytes())
                self.assertEqual(png,(output/(key+'.png')).read_bytes())
                self.assertEqual(0,records[0]['guardedNativePixels'])
                for overlap in (staging,legacy,staging/'nested-repair',base):
                    with self.assertRaisesRegex(ValueError,'separate from both input'):repair.execute(files,staging,legacy,overlap)
                self.assertFalse((staging/'repair-plan.json').exists());self.assertFalse((legacy/'repair-plan.json').exists())
                original_validate=repair.batch.validate
                original_controls={name:(staging/name).read_bytes() for name in ('source-plan.json','candidates.json')}
                for name in original_controls:
                    def drift(*args,**kwargs):
                        original_validate(*args,**kwargs)
                        (staging/name).write_bytes(b'[]' if name=='candidates.json' else b'{}')
                    with mock.patch.object(repair.batch,'validate',side_effect=drift):
                        with self.assertRaisesRegex(ValueError,'controls changed during preflight'):repair.execute(files,staging,legacy,base/('drift-'+name))
                    self.assertFalse((base/('drift-'+name)).exists());(staging/name).write_bytes(original_controls[name])
                (staging/'candidates.json').write_text('[]')
                with self.assertRaisesRegex(ValueError,'complete unique'):repair.execute(files,staging,legacy,base/'bad')
                self.assertFalse((base/'bad').exists())


if __name__=='__main__':unittest.main()
