#!/usr/bin/env python3
"""Build source-bound EnvHD/UIHD/FxHD pilot resources offline. Never download models.
Inputs are the owner's extracted files and the previously pinned Real-ESRGAN tools.
Raw source assets, model weights and binaries are not copied into the mods.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, subprocess, tempfile
from PIL import Image
import PIL, numpy as np

ROOT = Path(__file__).resolve().parents[1]
def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT/'scripts'/file)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result); return result
packer = module('hd_packer', 'pack-model-materials.py')
def sha(data): return hashlib.sha256(data).hexdigest()
def digest(path): return sha(path.read_bytes())

def infer(image, args, work, name):
    source, output = work/(name+'-input.png'), work/(name+'-output.png')
    image.convert('RGB').save(source)
    command = [str(args.engine), '-i', str(source), '-o', str(output), '-m', str(args.models), '-n', 'realesrgan-x4plus', '-s', '4', '-t', '256', '-j', '1:1:1']
    run = subprocess.run(command, capture_output=True, timeout=60)
    (work/(name+'.log')).write_bytes(run.stdout+run.stderr)
    if run.returncode: raise RuntimeError('Neural inference failed; inspect '+str(work/(name+'.log')))
    with Image.open(output) as im: candidate = im.convert('RGB')
    if candidate.size != (image.width*4, image.height*4): raise ValueError('Unexpected inference size')
    return candidate

def stage(number, args, work):
    folder = args.files/'SECT/DRGN0.BIN'/str(number)
    model, tim = (folder/'0/0').read_bytes(), (folder/'2').read_bytes()
    audit, masks, crops, slots, width, height = packer.layout(model, tim, 4)
    if int.from_bytes(model[4:8], 'little') or int.from_bytes(model[8:12], 'little'): raise ValueError('Animated stage excluded')
    atlas = Image.new('RGBA',(width,height))
    for palette, mask in sorted(masks.items()):
        source = packer.source_palette(tim,palette); tile = packer.clamped_crop(source,crops[palette])
        target = tile.resize((tile.width*4,tile.height*4),Image.Resampling.NEAREST)
        try: input_rgb,_ = packer.islands.padded_material(source,mask,8)
        except ValueError: input_rgb=None
        if input_rgb is not None:
            candidate=infer(input_rgb,args,work,f'stage{number}-palette{palette}')
            target,_ = packer.transparency.preserve(tile,Image.blend(target.convert('RGB'),candidate,0.7).convert('RGBA'))
        x,y,_,_=slots[palette];atlas.paste(target,(x,y))
    dest=args.output/'envhd/runtime-assets/envhd/stages'/sha(model);dest.mkdir(parents=True,exist_ok=False)
    atlas.save(dest/'atlas-engine-stp.png')
    report={'pipeline':'definitive-private-material-pack-1','modelSha256':sha(model),'timSha256':sha(tim),'scale':4,'paddingSourceTexels':8,'atlasSize':[width,height],'materials':[{'palette':p,'sourceCrop':list(crops[p]),'atlasRect':list(slots[p])} for p in sorted(masks)],'algorithm':'neural','strength':0.7,'preservedPalettes':[],'weightsSha256':args.weights_hash,'paramsSha256':args.parameters_hash,'engineSha256':digest(args.engine),'atlasEngineSha256':digest(dest/'atlas-engine-stp.png'),'scope':'EnvHD 0.1 pilot; static stage materials, no skybox replacement; native and Deck visual acceptance pending.'}
    (dest/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    packer.validate_pack(dest,model,tim)
    return {'stage':number-2497,'materials':len(masks),'rgbaBytes':width*height*4,'modelSha256':sha(model),'timSha256':sha(tim),'atlasSha256':report['atlasEngineSha256']}

def icon(name,args,work):
    source=args.source/'gfx/goods'/(name+'.png'); original=Image.open(source).convert('RGBA')
    candidate=infer(original,args,work,'icon-'+name).resize((64,64),Image.Resampling.LANCZOS).convert('RGBA')
    candidate = Image.blend(original.resize((64,64),Image.Resampling.NEAREST).convert('RGB'), candidate.convert('RGB'), 0.55).convert('RGBA')
    candidate.putalpha(original.getchannel('A').resize((64,64),Image.Resampling.NEAREST))
    pixels=np.array(candidate);pixels[pixels[...,3]==0]=0;candidate=Image.fromarray(pixels)
    dest=args.output/'uihd/runtime-assets/uihd/goods';dest.mkdir(parents=True,exist_ok=True)
    candidate.save(dest/(name+'.png'))
    (dest/(name+'.properties')).write_text(f'sourceSha256={digest(source)}\noutputSha256={digest(dest/(name+".png"))}\nsourceSize=32x32\noutputSize=64x64\nmethod=Real-ESRGAN-x4plus-downsample-2x-blend55-preserved-alpha\n')
    return name

def dust(args,work):
    source=args.files/'SUBMAP/dust.tim';tim=source.read_bytes();image=packer.source_palette(tim,0)
    mask=Image.fromarray(np.any(np.array(image)!=0,axis=-1))
    padded,_=packer.islands.padded_material(image,mask,8)
    candidate=infer(padded,args,work,'dust').crop((32,32,32+image.width*4,32+image.height*4)).convert('RGBA')
    candidate,_=packer.transparency.preserve(image,candidate)
    dest=args.output/'fxhd/runtime-assets/fxhd';dest.mkdir(parents=True,exist_ok=True);candidate.save(dest/'dust.png')
    (dest/'dust.properties').write_text(f'sourceSha256={sha(tim)}\noutputSha256={digest(dest/"dust.png")}\nscale=4\nmethod=Real-ESRGAN-x4plus-preserved-STP\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('files','source','output','engine','models','work'):parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args()
    for name in ('files','source','output','engine','models','work'):setattr(args,name,getattr(args,name).resolve())
    if args.output.exists():parser.error('Output must be a new staging folder')
    args.weights_hash='713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf';args.parameters_hash='35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86'
    if digest(args.models/'realesrgan-x4plus.bin')!=args.weights_hash or digest(args.models/'realesrgan-x4plus.param')!=args.parameters_hash:parser.error('Weights differ from pinned models')
    args.output.mkdir(parents=True);args.work.mkdir(parents=True,exist_ok=False)
    results={'envhd':[stage(n,args,args.work) for n in (2497,2503)],'uihd':[icon(n,args,args.work) for n in ('red_stone','blue_stone','moon_gem','vanishing_stone','magic_oil')],'fxhd':['dust'],'charhd':'Field pack adapter only; final character assets belong to the separate model workstream.','pillow':PIL.__version__,'numpy':np.__version__,'weightsSha256':args.weights_hash,'paramsSha256':args.parameters_hash,'engineSha256':digest(args.engine)}
    dust(args,args.work);(args.output/'production.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results,indent=2))
