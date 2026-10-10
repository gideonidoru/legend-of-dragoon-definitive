#!/usr/bin/env python3
"""Source-bound, resumable field-effects candidates. Original controls/logs stay private."""
import argparse
import csv
import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('dust', 'smoke_1', 'smoke_2', 'left_foot', 'right_foot', 'smoke_2_dust_palette', 'savepoint', 'savepoint_big_circle')
PINS = {'engine': 'c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc',
        'weights': '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf',
        'parameters': '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86'}
RECIPE = 'real-esrgan-x4plus-clamped16-blend65-exact-stp-v1'

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result

packer = module('fx_packer', 'pack-model-materials.py')
transparency = module('fx_visibility', 'preserve-texture-transparency.py')

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def compare(original, variants, destination):
    board = Image.new('RGB', (720, 390), (35, 38, 45))
    draw = ImageDraw.Draw(board)
    for col, (label, image) in enumerate([('Original', original), ('2x', variants[2]), ('4x', variants[4])]):
        draw.text((col * 240 + 15, 8), label, fill='white')
        pixels = np.asarray(image.convert('RGBA')).copy()
        visible = (pixels[:, :, 3] != 0) | np.any(pixels[:, :, :3] != 0, axis=2)
        pixels[:, :, 3] = visible.astype(np.uint8) * 255
        preview = Image.fromarray(pixels)
        for row, bg in enumerate(((15, 17, 22), (190, 180, 160))):
            canvas = Image.new('RGBA', (220, 170), bg + (255,))
            # Equal native-size and enlarged views; preview opacity is not engine STP.
            canvas.alpha_composite(preview.resize(original.size, Image.Resampling.BOX), (10, 10))
            canvas.alpha_composite(preview.resize((original.width * 4, original.height * 4), Image.Resampling.NEAREST), (70, 28))
            board.paste(canvas.convert('RGB'), (col * 240 + 10, row * 180 + 30))
    board.save(destination)

def produce(args):
    for path in (args.output, args.work):
        if path.resolve().is_relative_to(ROOT) or path.resolve().is_relative_to(args.files.resolve()):
            raise ValueError('Outputs and source controls must stay outside checkout/extraction')
    args.output.mkdir(parents=True, exist_ok=True)
    args.work.mkdir(parents=True, exist_ok=True)
    for key, path in [('engine', args.engine), ('weights', args.models / 'realesrgan-x4plus.bin'), ('parameters', args.models / 'realesrgan-x4plus.param')]:
        if digest(path) != PINS[key]:
            raise ValueError('Pinned inference tool changed: ' + key)
    with args.inventory.open() as stream:
        inventory = {r['path']: r for r in csv.DictReader(stream)}
    manifest = args.output / 'candidates.json'
    previous = json.loads(manifest.read_text()) if manifest.exists() else {'assets': []}
    prior = {a['name']: a for a in previous['assets']}
    result = []
    for name in NAMES:
        source_name = 'smoke_2' if name == 'smoke_2_dust_palette' else name
        path = args.files / 'SUBMAP' / (source_name + '.tim')
        data = path.read_bytes()
        row = inventory['SUBMAP/' + source_name + '.tim']
        if digest(path) != row['sha256'] or row['palettes'] != '1':
            raise ValueError('Source identity or palette changed: ' + name)
        if name == 'smoke_2_dust_palette':
            palette = args.files / 'SUBMAP/dust.tim'
            if digest(palette) != inventory['SUBMAP/dust.tim']['sha256']:
                raise ValueError('Cloud palette changed')
            data = data[:8] + palette.read_bytes()[8:52] + data[52:]
        source_hash = hashlib.sha256(data).hexdigest()
        source = packer.source_palette(data, 0)
        if source.size != (int(row['width']), int(row['height'])):
            raise ValueError('Decoded dimensions changed')
        old = prior.get(name)
        if old:
            if old['sourceSha256'] != source_hash or old['recipe'] != RECIPE or old['tools'] != PINS:
                raise ValueError('Cannot resume changed production inputs')
            for candidate in old['candidates']:
                if digest(args.output / candidate['file']) != candidate['sha256']:
                    raise ValueError('Saved candidate changed')
            result.append(old)
            print(json.dumps({'asset': name, 'state': 'retained-verified-candidates'}), flush=True)
            continue
        padded = np.pad(np.asarray(source.convert('RGB')), ((16, 16), (16, 16), (0, 0)), mode='edge')
        input_path = args.work / (name + '-input.png')
        inferred = args.work / (name + '-inference.png')
        Image.fromarray(padded).save(input_path)
        run = subprocess.run([str(args.engine), '-i', str(input_path), '-o', str(inferred), '-m', str(args.models), '-n', 'realesrgan-x4plus', '-s', '4', '-t', '128', '-j', '1:1:1'], capture_output=True, timeout=120)
        (args.work / (name + '.log')).write_bytes(run.stdout + run.stderr)
        if run.returncode:
            raise RuntimeError('Inference failed; inspect private log for ' + name)
        with Image.open(inferred) as image:
            if image.size != ((source.width + 32) * 4, (source.height + 32) * 4):
                raise ValueError('Inference dimensions changed')
            rgb = image.convert('RGB').crop((64, 64, 64 + source.width * 4, 64 + source.height * 4))
        rgb = Image.blend(source.convert('RGB').resize(rgb.size, Image.Resampling.NEAREST), rgb, 0.65)
        entry = {'name': name, 'sourceSha256': source_hash, 'sourceSize': list(source.size), 'palette': 0,
                 'recipe': RECIPE, 'tools': PINS, 'reviewStatus': 'pending-visual-review', 'candidates': []}
        variants = {}
        for scale in (2, 4):
            candidate = rgb.resize((source.width * scale, source.height * scale), Image.Resampling.BOX).convert('RGBA')
            engine, preview = transparency.preserve(source, candidate)
            output = args.output / f'{name}-{scale}x.png'
            engine.save(output)
            variants[scale] = engine
            entry['candidates'].append({'scale': scale, 'file': output.name, 'sha256': digest(output), 'rgbaBytes': engine.width * engine.height * 4})
        compare(source, variants, args.work / (name + '-comparison.png'))
        result.append(entry)
        manifest.write_text(json.dumps({'pipeline': RECIPE, 'assets': result, 'installed': False}, indent=2) + '\n')
        print(json.dumps({'asset': name, 'state': 'generated-for-review', 'variants': 2}), flush=True)
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('files', 'inventory', 'output', 'work', 'engine', 'models'):
        parser.add_argument('--' + name, type=Path, required=True)
    produce(parser.parse_args())
