#!/usr/bin/env python3
"""Pack reviewed custom terrain into source-bound static world atlases.

Uses public custom candidates and a fresh private source census. Original pixels
are used for validation only and are never written to the checkout.
"""
import argparse
import importlib.util
import io
import json
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


terrain = module('terrain_atlas_batch', ROOT / 'scripts/batch-envhd-terrain.py')
publication = module('terrain_atlas_publication', ROOT / 'scripts/import-envhd-sky.py')
candidate_import = module('terrain_atlas_candidate_import', ROOT / 'scripts/import-envhd-terrain.py')
SCALE, PADDING, WIDTH = 4, 8, 4096


def layout(items):
    """Deterministic shelves of padded complete decoded TIM rectangles."""
    positions = {}
    x = y = shelf = 0
    for key, size in sorted(items.items(), key=lambda item: (-item[1][1], -item[1][0], item[0])):
        w, h = ((n + PADDING * 2) * SCALE for n in size)
        if w > WIDTH or h > 4096:
            raise ValueError('Material exceeds bounded world atlas')
        if x + w > WIDTH:
            x, y, shelf = 0, y + shelf, 0
        positions[key] = [x + PADDING * SCALE, y + PADDING * SCALE,
                          size[0] * SCALE, size[1] * SCALE]
        x, shelf = x + w, max(shelf, h)
    height = max(4, y + shelf)
    if height > 4096:
        raise ValueError('Scene exceeds bounded world atlas')
    return [WIDTH, height], positions


def pack(files, destination):
    if destination.resolve().is_relative_to(ROOT.resolve()) or destination.resolve().is_relative_to(files.resolve()):
        raise ValueError('Atlas staging must remain private and separate')
    scenes, masters = terrain.census(files)
    production = ROOT / 'integrations/envhd/production'
    ledger = json.loads(terrain.bounded_read(production / 'terrain-artwork.json', 4 * 1024 * 1024))
    if ledger['scenes'] != scenes:
        raise ValueError('Public candidate census differs from current source')
    records = {a['decodedRgbaSha256']: a for a in ledger['assets']}
    if len(records) != len(ledger['assets']) or set(records) != set(masters):
        raise ValueError('Requires a complete unique public candidate census')
    images = {}
    for key, source in masters.items():
        record = records[key]
        png = terrain.bounded_read(production / 'terrain-candidates' / key / 'image-v1.png', 32 * 1024 * 1024)
        expected = terrain.candidate_record(source, terrain.digest(png))
        if any(record.get(k) != v for k,v in expected.items() if k != 'reviewStatus'):
            raise ValueError('Public candidate metadata changed')
        terrain.validate_completed_bytes(source, expected, png)
        review = record['review']
        fields = {'intent','style','layout','verdict','outputSha256','nativeAcceptance','finalQualityAcceptance'}
        if set(review) != fields or any(not isinstance(v,str) or not v.strip() for v in review.values()) or review['nativeAcceptance'] != 'pending' or review['finalQualityAcceptance'] != 'pending':
            raise ValueError('Explicit development review required')
        if review['outputSha256'] != terrain.digest(png) or review['verdict'] != record['reviewStatus']:
            raise ValueError('Review does not bind to candidate bytes')
        if record['batchPipelineSha256'] not in (candidate_import.REVIEWED_BATCH_PIPELINE_SHA256, terrain.digest(Path(terrain.__file__).read_bytes())):
            raise ValueError('Unknown candidate pipeline')
        version = json.loads(terrain.bounded_read(production / 'terrain-candidates' / key / 'manifest-v1.json', 65536))
        if version != record:
            raise ValueError('Candidate ledger and version manifest disagree')
        if record['reviewStatus'] not in ('visual-reviewed-runtime-pending', 'retain-native-uniform'):
            raise ValueError('Candidate is not reviewed for packing')
        if record['reviewStatus'] == 'retain-native-uniform':
            original = source['image']
            if original.getextrema() != tuple((v,v) for v in original.getpixel((0,0))):
                raise ValueError('Uniform retention is not a constant source')
        else:
            images[key] = np.array(Image.open(io.BytesIO(png)))
    writes, inventory = {}, []
    for scene in scenes:
        selected = {m['decodedRgbaSha256']: tuple(m['sourceSize']) for m in scene['materials']
                    if m['status'] == 'candidate-pending' and m['decodedRgbaSha256'] in images}
        size, positions = layout(selected)
        atlas = np.zeros((size[1], size[0], 4), dtype=np.uint8)
        for key, (x,y,w,h) in positions.items():
            pad = PADDING * SCALE
            atlas[y-pad:y+h+pad, x-pad:x+w+pad] = np.pad(images[key], ((pad,pad),(pad,pad),(0,0)), mode='edge')
        buffer = io.BytesIO()
        Image.fromarray(atlas).save(buffer, format='PNG')
        png = buffer.getvalue()
        materials = []
        for binding in scene['materials']:
            item = {k: binding[k] for k in ('page','clut')}
            if binding['status'] == 'held-native-uv-mapping':
                item['status'] = 'held-native-uv-mapping'
            else:
                key = binding['decodedRgbaSha256']
                item.update(decodedRgbaSha256=key, sourceSize=binding['sourceSize'], sourceUvOrigin=binding['sourceUvOrigin'])
                item['status'] = 'hd' if key in positions else 'retain-native-uniform'
                if key in positions:
                    item['atlasRect'] = positions[key]
            materials.append(item)
        manifest = dict(schema=1, pipeline='envhd-static-world-atlas-1', modelSha256=scene['modelSha256'],
                        bankSha256=scene['bankSha256'], scale=SCALE, paddingSourceTexels=PADDING,
                        atlasSize=size, atlasSha256=terrain.digest(png), materials=materials)
        folder = destination / scene['modelSha256']
        writes[folder / 'atlas-v1.png'] = png
        writes[folder / 'manifest.json'] = publication.json_bytes(manifest)
        inventory.append(dict(bank=scene['bank'], region=scene['region'], **manifest))
    # Complete validation precedes publication; failure cannot leave partial atlases.
    writes[destination / 'inventory.json'] = publication.json_bytes(dict(schema=1, scenes=inventory, runtimeSelected=0,
                                                                       nativeAcceptance='pending', finalQualityAcceptance='pending'))
    publication.publish(writes, set())
    return inventory


def publish(files, staging):
    """Rebuild from fresh sources/reviews and compare bytes before runtime selection."""
    if staging.resolve().is_relative_to(ROOT.resolve()) or staging.resolve().is_relative_to(files.resolve()):
        raise ValueError('Atlas staging must remain separate')
    with tempfile.TemporaryDirectory(prefix='envhd-atlas-preflight-') as temporary:
        expected = Path(temporary)
        inventory = pack(files, expected)
        writes, selections = {}, []
        for scene in inventory:
            key = scene['modelSha256']
            for name in ('atlas-v1.png','manifest.json'):
                data = terrain.bounded_read(staging / key / name, 32 * 1024 * 1024 if name.endswith('.png') else 65536)
                if data != (expected / key / name).read_bytes():
                    raise ValueError('Staged atlas differs from reviewed source-bound packing')
                target = ROOT / 'integrations/envhd/runtime-assets/envhd/world/terrain' / key / name
                publication.check_version(data,target)
                writes[target] = data
            selections.append(dict(bank=scene['bank'],region=scene['region'],modelSha256=key,bankSha256=scene['bankSha256'],
                                   atlasSha256=scene['atlasSha256'],atlasSize=scene['atlasSize'],hdBindings=sum(m['status']=='hd' for m in scene['materials'])))
        selection = dict(schema=1,pipeline='envhd-static-world-atlas-1',scenes=selections,
                         runtimeSelectedMasters=len({m['decodedRgbaSha256'] for s in inventory for m in s['materials'] if m['status']=='hd'}),
                         runtimeSelectedBindings=sum(s['hdBindings'] for s in selections),retainedAnimatedParts=8,
                         retainedUniformBindings=sum(m['status']=='retain-native-uniform' for s in inventory for m in s['materials']),
                         heldNativeBindings=sum(m['status']=='held-native-uv-mapping' for s in inventory for m in s['materials']),
                         nativeAcceptance='pending',finalQualityAcceptance='pending')
        writes[ROOT / 'integrations/envhd/production/terrain-runtime-artwork.json'] = publication.json_bytes(selection)
        publication.publish(writes,set())
        return selection


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--publish', action='store_true', help='Validate existing private packing and select matching runtime atlases')
    args = parser.parse_args()
    if args.publish:
        result = publish(args.files,args.output)
        print('Selected runtime terrain masters:',result['runtimeSelectedMasters'],'HD bindings:',result['runtimeSelectedBindings'])
    else:
        result = pack(args.files, args.output)
        print('Private packed scenes:', len(result), 'HD bindings:', sum(m['status'] == 'hd' for s in result for m in s['materials']))
