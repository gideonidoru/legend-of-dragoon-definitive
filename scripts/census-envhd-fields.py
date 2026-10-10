#!/usr/bin/env python3
"""Decode the complete field-source worklist without publishing original pixels.

The report distinguishes source configurations, render configurations and visible
pixel jobs. Foregrounds remain independent so doors, scripts and alternate frames
are never baked into the background. Source images are decoded in memory only.
"""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
import re
import struct

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

battle = module('field_battle_helpers', 'batch-envhd-battle-materials.py')
terrain = battle.terrain
maps = module('field_source_map', 'modelshd-field-map.py')


def entries(folder):
    """Resolve ordered virtual MRG entries; never infer absent alias bytes."""
    text = terrain.bounded_read(folder / 'mrg', 65536).decode('ascii')
    raw = {}
    for line in text.splitlines():
        match = re.fullmatch(r'(\d+)=(\d*);(\d+)', line)
        if not match or int(match[1]) in raw:
            raise ValueError('Malformed or duplicate source directory entry')
        raw[int(match[1])] = (int(match[2]) if match[2] else None, int(match[3]))
    if len(raw) > 1024:
        raise ValueError('Source directory entry limit')
    result = {}
    for index, (target, size) in sorted(raw.items()):
        if target is None:
            if size:
                raise ValueError('Missing required source entry')
            continue
        seen = set()
        while target in raw and raw[target][0] != target:
            if target in seen:
                raise ValueError('Source alias cycle')
            seen.add(target)
            target = raw[target][0]
        if target not in raw or target is None:
            raise ValueError('Unresolved source alias')
        data = terrain.bounded_read(folder / str(target))
        if not data:
            raise ValueError('Empty required source file')
        result[index] = data
    return result


def tim(data):
    if len(data) < 8:
        raise ValueError('Truncated field TIM')
    magic, flags = struct.unpack_from('<II', data)
    if magic != 16 or flags not in (8, 9):
        raise ValueError('Field CPU palette path requires indexed TIM')
    palette, offset = terrain.block(data, 8)
    image, end = terrain.block(data, offset)
    if end != len(data):
        raise ValueError('Unexpected field TIM trailing bytes')
    divisor, bits = (4, 4) if flags == 8 else (2, 8)
    if palette['w'] < 1 << bits:
        raise ValueError('Incomplete field TIM first-row palette')
    indices = np.empty((image['h'], image['w'] * divisor), dtype=np.uint16)
    for component in range(divisor):
        indices[:, component::divisor] = (image['words'] >> (bits * component)) & ((1 << bits) - 1)
    colours = palette['words'][0][indices].astype(np.uint32)
    # RetailSubmap uses CPU <<3 colour expansion, then makes every nonzero
    # packed texel opaque. This is intentionally different from TMD STP alpha.
    rgba = np.stack(((colours & 31) * 8, ((colours >> 5) & 31) * 8,
                     ((colours >> 10) & 31) * 8, np.where(colours != 0, 255, 0)), axis=2).astype(np.uint8)
    return image, rgba


def slots(data, cut):
    if len(data) < 24:
        raise ValueError('Truncated environment descriptor')
    count, backgrounds, foregrounds = struct.unpack_from('3B', data, 20)
    if count != backgrounds + foregrounds or count > 64 or len(data) != 24 + count * 36:
        raise ValueError('Invalid environment descriptor size/count')
    result = []
    for index in range(count):
        offset = 24 + index * 36
        u, v, w, h, x, y = struct.unpack_from('<6h', data, offset + 8)
        if (cut, index) in ((111, 8), (288, 17), (595, 5)):
            y += 1
        if (cut, index) == (642, 2):
            w -= 1
        if u < 0 or v < 0 or w < 0 or h < 0:
            raise ValueError('Invalid environment slot rectangle')
        result.append(dict(index=index, u=u, v=v, w=w, h=h, x=x, y=y,
                           tpage=struct.unpack_from('<H', data, offset + 32)[0],
                           translucent=struct.unpack_from('<h', data, offset + 34)[0] < 0,
                           foreground=index >= backgrounds))
    return backgrounds, foregrounds, result


def render(sources, cut):
    """Mirror RetailSubmap.prepareEnv canvas/first TIM/first palette semantics."""
    backgrounds, foregrounds, records = slots(sources[0], cut)
    textures = [tim(data) for index, data in sources.items() if index >= 3]
    if not records:
        raise ValueError('Empty environment descriptor')
    for slot in records:
        px, py = (slot['tpage'] & 15) * 64, 256 if slot['tpage'] & 16 else 0
        match = next((rgba for rect, rgba in textures
                      if rect['x'] <= px < rect['x'] + rect['w'] and rect['y'] <= py < rect['y'] + rect['h']), None)
        slot['pixels'] = match
    matched = [s for s in records if s['pixels'] is not None]
    if not matched:
        raise ValueError('Environment has no matching TIM slots')
    left = min(s['x'] for s in matched)
    top = min(s['y'] for s in matched)
    width = max(s['x'] + s['w'] for s in matched) - left
    height = max(s['y'] + s['h'] for s in matched) - top
    if width <= 0 or height <= 0 or width > 2048 or height > 2048 or width * height > 2097152:
        raise ValueError('Field canvas budget/dimensions')
    base = np.zeros((height, width, 4), dtype=np.uint8)
    layers = []
    metadata = []
    for slot in records:
        array = slot['pixels']
        if array is None:
            if slot['foreground']:
                layers.append(None)
                metadata.append(dict(index=slot['index'], missingNativeTexture=True, canDeriveFromBackground=False))
            continue
        w, h = slot['w'], slot['h']
        if slot['foreground']:
            w, h = min(w, array.shape[1] - slot['u']), min(h, array.shape[0] - slot['v'])
        if w < 0 or h < 0 or slot['u'] + w > array.shape[1] or slot['v'] + h > array.shape[0]:
            raise ValueError('Field source crop outside TIM')
        crop = array[slot['v']:slot['v'] + h, slot['u']:slot['u'] + w].copy()
        x, y = slot['x'] - left, slot['y'] - top
        if slot['foreground']:
            # Crop jobs preserve independent layer pixels. Runtime must place
            # them in this full canvas, retaining every script-indexed slot.
            layers.append(Image.fromarray(crop) if w and h else None)
            metadata.append({k: slot[k] for k in ('index', 'x', 'y', 'u', 'v', 'w', 'h', 'tpage', 'translucent')}
                            | dict(canvasPlacement=[x, y, w, h]))
        else:
            # Native texture uploads overwrite zero texels as well; alpha
            # compositing would incorrectly expose a previous overlapping tile.
            base[y:y + h, x:x + w] = crop
    for image, meta in zip(layers, metadata):
        meta['canDeriveFromBackground'] = False
        if image is not None:
            x,y,w,h = meta['canvasPlacement']
            pixels = np.asarray(image)
            mask = pixels[:,:,3] != 0
            meta['canDeriveFromBackground'] = bool(mask.any() and np.array_equal(pixels[mask], base[y:y+h,x:x+w][mask]))
    return Image.fromarray(base), layers, dict(canvasRect=[left, top, width, height],
                                             backgroundPieces=backgrounds, foregroundSlots=foregrounds,
                                             foregrounds=metadata, missingNativeTextureSlots=[s['index'] for s in records if s['pixels'] is None])


def verify_complete_worklist(ownership, configurations):
    expected={(s['sourceSignature'],m['cut'],m['period'],m['bank'],m['file'])
              for s in ownership['scenes'] for m in s['mappings']}
    actual={(s['sourceSignature'],m['cut'],m['period'],m['bank'],m['directory'])
            for s in configurations.values() for m in s['mappings']}
    if actual!=expected or configurations.keys()!={s['sourceSignature'] for s in ownership['scenes']}:
        raise ValueError('Field source/alias worklist changed; refresh the Skurfa ownership audit before generating')


def verify_complete_renders(configurations, renders, failures):
    expected_keys=set()
    known_routes=set()
    for group in configurations.values():
        group_keys=set()
        for route in group['mappings']:
            corrected=route['cut'] if route['cut'] in (111,288,595,642) else -1
            key=group['sourceSignature']+':'+str(corrected)
            group_keys.add(key)
            known_routes.add((route['cut'],route['period'],route['bank'],route['directory']))
        expected_keys.update(group_keys)
        if set(group['renders'])!=group_keys:
            raise ValueError('Known field configuration has missing or partial rendered source data')
    if renders.keys()!=expected_keys:
        raise ValueError('Field render worklist is incomplete')
    if any((f['cut'],f['period'],f.get('bank'),f.get('directory')) in known_routes for f in failures):
        raise ValueError('Known field route failed decoding; resolve it before generating')


def census(files, root=ROOT):
    source = terrain.bounded_read(files / 'SUBMAP/NEWROOT.RDT')
    if len(source) < 8192:
        raise ValueError('Truncated NEWROOT table')
    retail = (root / maps.RETAIL).read_text()
    types = maps._numbers(retail, 'submapTypes_800f5cd4')
    locations = maps._numbers(retail, 'cutToSubmap_800d610c')
    names = maps._strings((root / maps.SITEM).read_text(), 'submapNames_8011c108')
    ownership = json.loads((root / 'integrations/envhd/production/field-ownership.json').read_text())
    protected = {s['sourceSignature']: s for s in ownership['scenes'] if s['owner'] == 'skurfa'}
    folders, configurations, failures, assets, renders = {}, {}, [], {}, {}
    for cut in range(1024):
        for period, value in zip(('early', 'late'), struct.unpack_from('<HH', source, cut * 8)):
            disk = value >> 13
            route = dict(cut=cut, period=period, rawDisk=disk,
                         fieldType=types[cut] if cut < len(types) else None,
                         location=names[locations[cut]] if cut < len(locations) and 0 <= locations[cut] < len(names) else '')
            if disk > 3:
                failures.append(route | dict(status='non-retail-disc-selector', packedValue=value))
                continue
            bank, directory = 21 + disk, (value & 8191) * 3 + 4
            route.update(bank=bank, directory=directory)
            try:
                key = bank, directory
                if key not in folders:
                    data = entries(files / 'SECT' / f'DRGN{bank}.BIN' / str(directory))
                    if 0 not in data:
                        raise ValueError('Missing environment descriptor')
                    signature = terrain.digest(data[0] + b''.join(bytes.fromhex(terrain.digest(d)) for i, d in data.items() if i >= 3))
                    folders[key] = data, signature
                data, signature = folders[key]
                group = configurations.setdefault(signature, dict(sourceSignature=signature,
                     owner='skurfa' if signature in protected else 'envhd', mappings=[], renders=[]))
                group['mappings'].append(route)
                corrected = cut if cut in (111, 288, 595, 642) else -1
                render_key = signature + ':' + str(corrected)
                if render_key not in renders:
                    base, layers, meta = render(data, cut)
                    image_records = []
                    for index, image in enumerate([base] + layers):
                        if image is None or image.getchannel('A').getbbox() is None:
                            image_records.append(dict(kind='background' if index == 0 else 'foreground', slot=index - 1, status='missing-native-texture-slot' if index and meta['foregrounds'][index-1].get('missingNativeTexture') else 'empty-native-slot'))
                            continue
                        pixel_key = terrain.fingerprint(image)
                        asset = assets.setdefault(pixel_key, dict(decodedRgbaSha256=pixel_key, sourceSize=list(image.size), owners=[], bindings=[]))
                        if group['owner'] not in asset['owners']:
                            asset['owners'].append(group['owner'])
                        asset['bindings'].append(dict(renderKey=render_key, kind='background' if index == 0 else 'foreground', slot=index - 1, canDeriveFromBackground=bool(index and meta['foregrounds'][index-1]['canDeriveFromBackground'])))
                        image_records.append(dict(kind='background' if index == 0 else 'foreground', slot=index - 1, status='protected-upstream-reuse' if group['owner']=='skurfa' else 'unrestored', decodedRgbaSha256=pixel_key, canDeriveFromBackground=bool(index and meta['foregrounds'][index-1]['canDeriveFromBackground'])))
                    renders[render_key] = dict(renderKey=render_key, sourceSignature=signature, owner=group['owner'], representative=route,
                                              sourceFiles=[dict(index=i, sha256=terrain.digest(d), size=len(d)) for i,d in data.items()], images=image_records, **meta)
                    group['renders'].append(render_key)
            except (ValueError, OSError, KeyError) as failure:
                failures.append(route | dict(status='unresolved-source-or-decode', reason=str(failure).split(str(files))[-1]))
    verify_complete_worklist(ownership,configurations)
    verify_complete_renders(configurations,renders,failures)
    images = [i for r in renders.values() for i in r['images']]
    successful_routes = sum(len(g['mappings']) for g in configurations.values())
    independent_jobs = [a for a in assets.values() if a['owners']==['envhd'] and any(not b['canDeriveFromBackground'] for b in a['bindings'])]
    return dict(schema=1, pipeline='envhd-field-visible-pixel-census-1',
                pipelineSha256=terrain.digest(Path(__file__).read_bytes()), newRootSha256=terrain.digest(source),
                sourceFolders=len(folders), sourceConfigurations=len(configurations), renderConfigurations=len(renders),
                successfulCutPeriodMappings=successful_routes, unresolvedOrNonRetailRoutes=len(failures),
                uniqueVisibleImages=len(assets), unownedUniqueVisibleImages=sum(a['owners']==['envhd'] for a in assets.values()),
                protectedSharedPixelImages=sum('skurfa' in a['owners'] for a in assets.values()),
                unownedIndependentInferenceImages=len(independent_jobs),
                derivableForegroundBindings=sum(i.get('canDeriveFromBackground',False) for i in images),
                missingNativeTextureSlots=sum(len(r['missingNativeTextureSlots']) for r in renders.values()),
                emptySlots=sum(i['status']=='empty-native-slot' for i in images),
                nativeAccepted=0, runtimeSelected=0, sourceConfigurationsWithoutRender=sum(not g['renders'] for g in configurations.values()),
                limitations=['All available source routes remain inventoried, including non-field/unused aliases; field-type table is metadata, not an exclusion.',
                             'Pixel identity includes image dimensions and exact CPU-visible RGBA. Foreground jobs preserve their own pixels and canvas placement.',
                             'Independent Java pixel validation, script reachability, state/timing families and visual restoration remain pending.'],
                assets=sorted(assets.values(), key=lambda a:a['decodedRgbaSha256']),
                configurations=sorted(configurations.values(), key=lambda g:g['sourceSignature']),
                renders=sorted(renders.values(), key=lambda r:r['renderKey']), failures=failures)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    battle.private_destination(args.files, args.output.parent)
    report = census(args.files)
    target = battle.staging_path(args.output.parent, args.output.name)
    battle.write_json(target, report)
    print(json.dumps({k:v for k,v in report.items() if k not in ('assets','configurations','renders','failures','limitations')}, indent=2))
