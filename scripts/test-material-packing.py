#!/usr/bin/env python3
"""Original synthetic packed-palette fixtures, AGPL v3; see LICENSE."""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import numpy as np
from PIL import Image


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


packing = load('packing', 'pack-model-materials.py')
poses = load('poses', 'inspect-model-poses.py')
fixtures = load('fixtures', 'test-model-material-audit.py')


def split_faces():
    vertices = b''.join(struct.pack('<4h', *point, 0) for point in [(-20,-10,0),(-2,-10,0),(-20,10,0),(2,-10,0),(20,-10,0),(2,10,0)])
    def packet(clut, refs):
        return bytes([7,5,0,0x24])+struct.pack('<2BH2BH2BH4H',0,0,clut,3,0,0,0,3,0,0,*refs)
    return struct.pack('<6I',12,0,0,0x41,0,1)+struct.pack('<7I',28,6,76,1,84,2,0)+vertices+bytes(8)+packet(0,(0,1,2))+packet(1,(3,4,5))


def write_control(folder, model, tim):
    _, masks, crops, slots, width, height = packing.layout(model, tim, 2)
    atlas = Image.new('RGBA', (width, height))
    for palette in masks:
        x,y,w,h=slots[palette]
        tile=packing.clamped_crop(packing.source_palette(tim,palette),crops[palette]).resize((w,h),Image.Resampling.NEAREST)
        atlas.paste(tile,(x,y))
    atlas.save(folder/'atlas-engine-stp.png')
    report={'pipeline':'definitive-private-material-pack-1','modelSha256':hashlib.sha256(model).hexdigest(),'timSha256':hashlib.sha256(tim).hexdigest(),'scale':2,'paddingSourceTexels':8,'algorithm':'nearest','preservedPalettes':[],'atlasSize':[width,height],'materials':[{'palette':p,'sourceCrop':list(crops[p]),'atlasRect':list(slots[p])} for p in sorted(masks)],'atlasEngineSha256':hashlib.sha256((folder/'atlas-engine-stp.png').read_bytes()).hexdigest()}
    (folder/'manifest.json').write_text(json.dumps(report))
    return report


class MaterialPackingTest(unittest.TestCase):
    def test_deterministic_nonoverlapping_shelves_and_limit(self):
        sizes={0:(40,24),1:(24,40),2:(16,12)}
        width,height,slots=packing.pack_rectangles(sizes)
        self.assertEqual((width,height,slots),packing.pack_rectangles(dict(reversed(list(sizes.items())))))
        occupancy=np.zeros((height,width),dtype=bool)
        for x,y,w,h in slots.values():
            self.assertFalse(occupancy[y:y+h,x:x+w].any())
            occupancy[y:y+h,x:x+w]=True
        with self.assertRaises(ValueError):packing.pack_rectangles({i:(4096,4096) for i in range(64)})

    def test_conflicting_palettes_render_separately_with_identical_controls(self):
        model,tim=split_faces(),fixtures.tim()
        report,_,_=packing.materials.audit(model,tim)
        self.assertGreater(report['conflictingColourTexels'],0)
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);write_control(folder,model,tim)
            atlas,mapping,_=packing.validate_pack(folder,model,tim)
            parts=poses.read_geometry(model);positions=[parts[0][0]]
            bounds=(-20,20,-10,10)
            original=poses.render(parts,positions,tim,None,bounds,(144,96))
            candidate=poses.render(parts,positions,tim,atlas,bounds,(144,96),mapping)
            np.testing.assert_array_equal(np.array(original),np.array(candidate))

    def test_fractional_uv_boundaries_do_not_cross_original_texels(self):
        source=Image.new('RGBA',(4,4),(255,255,255,0))
        source.putpixel((0,0),(255,0,0,0));source.putpixel((1,0),(0,255,0,0));source.putpixel((0,1),(0,0,255,0))
        below=np.nextafter(1.0,0.0)
        uv=np.array([(below,0),(0,below),(below,below),(1,0),(0,1)])
        for scale in (2,4):
            crop=(-8,-8,12,12);size=20*scale
            tile=packing.clamped_crop(source,crop).resize((size,size),Image.Resampling.NEAREST)
            atlas=Image.new('RGBA',(size+32,size+64));atlas.paste(tile,(16,32))
            x,y=poses.packed_coordinates(uv,{'sourceCrop':list(crop),'atlasRect':[16,32,size,size]})
            actual=[atlas.getpixel((int(a),int(b))) for a,b in zip(x,y)]
            self.assertEqual(actual,[(255,0,0,0)]*3+[(0,255,0,0),(0,0,255,0)])

    def test_source_mapping_and_preserved_pixels_are_checked(self):
        model,tim=split_faces(),fixtures.tim()
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);report=write_control(folder,model,tim)
            with self.assertRaises(ValueError):packing.validate_pack(folder,model+b'changed',tim)
            report['materials'][0]['atlasRect'][0]+=1
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'mapping'):packing.validate_pack(folder,model,tim)
            report=write_control(folder,model,tim)
            with Image.open(folder/'atlas-engine-stp.png') as image:
                atlas=image.convert('RGBA')
            x,y,_,_=report['materials'][0]['atlasRect'];atlas.putpixel((x,y),(0,255,0,0));atlas.save(folder/'atlas-engine-stp.png')
            report['atlasEngineSha256']=hashlib.sha256((folder/'atlas-engine-stp.png').read_bytes()).hexdigest()
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'preserved'):packing.validate_pack(folder,model,tim)

    def test_neural_provenance_and_stp_tampering_are_rejected(self):
        model,tim=split_faces(),fixtures.tim()
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);report=write_control(folder,model,tim)
            _,_,weights,params=packing.neural.MODELS['realesr-animevideov3']
            report.update(algorithm='neural',strength=0.5,weightsSha256=weights,paramsSha256=params)
            (folder/'manifest.json').write_text(json.dumps(report))
            packing.validate_pack(folder,model,tim)
            report['weightsSha256']='0'*64
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'weights'):packing.validate_pack(folder,model,tim)
            report['weightsSha256']=weights;report['scale']=True
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'scale'):packing.validate_pack(folder,model,tim)
            report['scale']=2
            x,y,_,_=report['materials'][0]['atlasRect']
            with Image.open(folder/'atlas-engine-stp.png') as image:atlas=image.convert('RGBA')
            pixel=atlas.getpixel((x,y));atlas.putpixel((x,y),(*pixel[:3],255-pixel[3]));atlas.save(folder/'atlas-engine-stp.png')
            report['atlasEngineSha256']=hashlib.sha256((folder/'atlas-engine-stp.png').read_bytes()).hexdigest()
            (folder/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'STP'):packing.validate_pack(folder,model,tim)


if __name__=='__main__':unittest.main()
