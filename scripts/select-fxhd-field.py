#!/usr/bin/env python3
"""Reproduce the reviewed field selection; validate every input before atomic import.

Original decoded controls and blend review boards remain in the private work folder.
Only custom candidates, their provenance and selected runtime resources enter Git.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('field_batch', ROOT / 'scripts/batch-fxhd-field.py')
batch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(batch)
SELECTED = ('dust', 'smoke_1', 'left_foot', 'right_foot', 'smoke_2_dust_palette', 'savepoint_big_circle')
SOFT_RECIPE = 'source-bicubic-exact-stp-v1'

def source_bytes(files, name):
    image = 'smoke_2' if name == 'smoke_2_dust_palette' else name
    data = (files / 'SUBMAP' / (image + '.tim')).read_bytes()
    if name == 'smoke_2_dust_palette':
        data = data[:8] + (files / 'SUBMAP/dust.tim').read_bytes()[8:52] + data[52:]
    return data

def validate(source, image):
    if image.size not in [(source.width * s, source.height * s) for s in (2, 4)]:
        raise ValueError('Candidate dimensions differ')
    control = np.asarray(source.resize(image.size, Image.Resampling.NEAREST).convert('RGBA'))
    pixels = np.asarray(image.convert('RGBA'))
    if not np.array_equal(control[:, :, 3], pixels[:, :, 3]):
        raise ValueError('Candidate STP changed')
    empty = np.all(control[:, :, :3] == 0, axis=2)
    if np.any(pixels[empty, :3] != 0):
        raise ValueError('Discarded or visible black changed')
    if np.any(np.all(pixels[:, :, :3] == 0, axis=2) & (pixels[:, :, 3] == 0) & ~empty):
        raise ValueError('Created discarded pixels')

def blend_board(source, selected, mode, destination):
    board = Image.new('RGB', (640, 420), (35, 38, 45))
    draw = ImageDraw.Draw(board)
    for col, (name, image) in enumerate((('Original', source), ('Selected 2x', selected))):
        draw.text((col * 320 + 12, 8), name + ' / ' + mode, fill='white')
        for row, bg in enumerate(((35, 40, 50), (130, 120, 90), (210, 210, 200))):
            # Approximate the original shader's two passes at unchanged monochrome.
            rgba = np.asarray(image.convert('RGBA'))
            rgb = rgba[:, :, :3].astype(np.float32)
            stp = rgba[:, :, 3] != 0
            visible = stp | np.any(rgb != 0, axis=2)
            back = np.broadcast_to(np.array(bg, np.float32), rgb.shape)
            mixed = back + rgb if mode == 'additive' else back - rgb
            result = np.where(visible[:, :, None], np.where(stp[:, :, None], mixed, rgb), back)
            preview = Image.fromarray(np.clip(result, 0, 255).astype(np.uint8))
            board.paste(preview.resize(source.size, Image.Resampling.BOX), (col * 320 + 12, row * 130 + 35))
            board.paste(preview.resize((source.width * 3, source.height * 3), Image.Resampling.NEAREST), (col * 320 + 115, row * 130 + 35))
    board.save(destination)

def select(args):
    if args.work.resolve().is_relative_to(ROOT) or args.work.resolve().is_relative_to(args.files.resolve()):
        raise ValueError('Private comparisons must remain outside checkout/extraction')
    args.work.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((args.candidates / 'candidates.json').read_text())
    by_name = {entry['name']: entry for entry in manifest['assets']}
    if set(by_name) != set(batch.NAMES):
        raise ValueError('Incomplete field batch')
    target = ROOT / 'integrations/fxhd/runtime-assets/fxhd'
    if target.exists() and any(target.iterdir()):
        raise ValueError('Existing selection is preserved; import into a new reviewed revision')
    production = ROOT / 'integrations/fxhd/production'
    if (production / 'candidates').exists():
        raise ValueError('Existing candidate archive is preserved')
    records = []
    with tempfile.TemporaryDirectory(dir=target.parent) as scratch:
        staged = Path(scratch) / 'fxhd'
        staged.mkdir()
        candidates = Path(scratch) / 'candidates'
        candidates.mkdir()
        for name, entry in by_name.items():
            data = source_bytes(args.files, name)
            if hashlib.sha256(data).hexdigest() != entry['sourceSha256'] or entry['tools'] != batch.PINS or entry['recipe'] != batch.RECIPE:
                raise ValueError('Production inputs changed: ' + name)
            source = batch.packer.source_palette(data, 0)
            for candidate in entry['candidates']:
                path = args.candidates / candidate['file']
                if batch.digest(path) != candidate['sha256']:
                    raise ValueError('Candidate changed: ' + name)
                with Image.open(path) as image:
                    validate(source, image)
                shutil.copyfile(path, candidates / path.name)
            record = dict(entry)
            record['selected'] = name in SELECTED
            record['reviewStatus'] = 'selected-offline-review' if name in SELECTED else 'deferred-original-retained'
            record['nativeValidated'] = False
            record['deckValidated'] = False
            record['reason'] = ('Static verified binding; 2x preferred at field-effect display size' if name in SELECTED else
                'Wrong palette for native cloud' if name == 'smoke_2' else 'Sheet/model consumer scrolls and overlaps VRAM; retain original animation')
            if name in SELECTED:
                method = batch.RECIPE
                if name == 'smoke_1':
                    method = SOFT_RECIPE
                    image = source.convert('RGB').resize((source.width * 2, source.height * 2), Image.Resampling.BICUBIC).convert('RGBA')
                    image, _ = batch.transparency.preserve(source, image)
                    image.save(candidates / 'smoke_1-soft-2x.png')
                    record['reason'] = 'Neural shading added grain; one source-faithful smooth repair selected'
                    record['repair'] = {'recipe': method, 'predecessor': 'smoke_1-2x.png', 'sha256': batch.digest(candidates / 'smoke_1-soft-2x.png')}
                else:
                    image = Image.open(args.candidates / (name + '-2x.png')).convert('RGBA')
                validate(source, image)
                output = staged / (name + '.png')
                image.save(output)
                (staged / (name + '.properties')).write_text('sourceSha256=' + entry['sourceSha256'] + '\noutputSha256=' + batch.digest(output) + '\nscale=2\nrecipe=' + method + '\n')
                record['runtime'] = {'file': name + '.png', 'sha256': batch.digest(output), 'scale': 2, 'rgbaBytes': image.width * image.height * 4, 'recipe': method}
                blend_board(source, image, 'subtractive' if 'foot' in name else 'additive', args.work / (name + '-blend-review.png'))
            records.append(record)
        # All controls/candidates are verified before modifying the selected directory.
        if target.exists():
            target.rmdir()
        os.replace(staged, target)
        production.mkdir(parents=True, exist_ok=True)
        os.replace(candidates, production / 'candidates')
    ledger = {'version': '0.2.0', 'pipeline': batch.RECIPE, 'selectedBindings': len(SELECTED),
        'activeTextureBytes': sum(r.get('runtime', {}).get('rgbaBytes', 0) for r in records),
        'provisionalTextureBudgetBytes': 16 * 1024 * 1024, 'scriptSha256': batch.digest(Path(__file__)),
        'status': 'Offline reviewed; native and physical Deck acceptance pending', 'assets': records}
    (production / 'field-selection.json').write_text(json.dumps(ledger, indent=2) + '\n')
    print(json.dumps({k: ledger[k] for k in ('selectedBindings', 'activeTextureBytes', 'status')}))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('files', 'candidates', 'work'):
        parser.add_argument('--' + name, type=Path, required=True)
    select(parser.parse_args())
