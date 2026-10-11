#!/usr/bin/env python3
"""Version reviewed custom terrain candidates without selecting a runtime atlas."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Reviewed production run from commit 90a2dc037, before the byte-validation seam.
REVIEWED_BATCH_PIPELINE_SHA256 = '23ce08235491afd853f1cc15cc63b965928054f7f70ccd88c9c2085b9d51d948'


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


terrain = module('terrain_candidate_batch', ROOT / 'scripts/batch-envhd-terrain.py')
publication = module('terrain_candidate_publication', ROOT / 'scripts/import-envhd-sky.py')


def publish(files, staging, review_file):
    if staging.resolve().is_relative_to(ROOT) or staging.resolve().is_relative_to(files.resolve()):
        raise ValueError('Staging must stay separate from the checkout and extraction')
    scenes, masters = terrain.census(files)
    records = json.loads(terrain.bounded_read(staging / 'candidates.json', 4 * 1024 * 1024))
    reviews = json.loads(terrain.bounded_read(review_file, 4 * 1024 * 1024))
    notes = reviews['reviews']
    if len(records) != len(masters) or len({r['decodedRgbaSha256'] for r in records}) != len(masters) or set(notes) != set(masters):
        raise ValueError('Requires a complete unique reviewed batch')
    plan = json.loads(terrain.bounded_read(staging / 'source-plan.json', 4 * 1024 * 1024))
    pipeline = plan.get('scriptSha256')
    if pipeline not in (REVIEWED_BATCH_PIPELINE_SHA256, terrain.digest((ROOT / 'scripts/batch-envhd-terrain.py').read_bytes())):
        raise ValueError('Unreviewed batch pipeline identity')
    expected_plan = dict(schema=1, scriptSha256=pipeline,
                         scenes=scenes, masters=[{k:v for k,v in m.items() if k != 'image'} for m in masters.values()])
    if plan != expected_plan:
        raise ValueError('Batch source plan or pipeline identity differs')
    writes, assets = {}, []
    production = ROOT / 'integrations/envhd/production'
    for record in records:
        key = record['decodedRgbaSha256']
        source = masters[key]
        note = notes[key]
        required = {'intent', 'style', 'layout', 'verdict', 'outputSha256', 'nativeAcceptance', 'finalQualityAcceptance'}
        if set(note) != required or any(not isinstance(v, str) or not v.strip() for v in note.values()):
            raise ValueError('Explicit source intent/style/layout review required')
        if note['verdict'] not in ('visual-reviewed-runtime-pending', 'retain-native-uniform', 'intent-revision-needed') or note['nativeAcceptance'] != 'pending' or note['finalQualityAcceptance'] != 'pending':
            raise ValueError('Candidate import does not establish native or final acceptance')
        if note['verdict'] == 'retain-native-uniform' and source['image'].getextrema() != tuple((v,v) for v in source['image'].getpixel((0,0))):
            raise ValueError('Native uniform exemption requires a verified constant source')
        path = staging / (key + '.png')
        # Decode the same bounded snapshot whose hash is checked and then published.
        png = terrain.bounded_read(path, 32 * 1024 * 1024)
        terrain.validate_completed_bytes(source, record, png)
        if note['outputSha256'] != terrain.digest(png):
            raise ValueError('Review is not bound to this output version')
        metadata = dict(record, reviewStatus=note['verdict'], review=note, batchPipelineSha256=pipeline,
                        sourceRole='Static world-map model parts; original geometry and coordinates remain authoritative',
                        styleReferenceUse='Skurfa visual reference only; its resources remain unchanged',
                        ownerApproval='Batch pipeline and Python repair explicitly authorized 2026-10-10')
        base = production / 'terrain-candidates' / key
        writes[base / 'image-v1.png'] = png
        writes[base / 'manifest-v1.json'] = publication.json_bytes(metadata)
        assets.append(metadata)
    ledger = dict(schema=1, assets=assets, scenes=scenes, runtimeSelected=0, nativeAccepted=0,
                  visualReviewed=sum(a['reviewStatus'] == 'visual-reviewed-runtime-pending' for a in assets),
                  retainedUniform=sum(a['reviewStatus'] == 'retain-native-uniform' for a in assets),
                  heldBindings=sum(m['status'] == 'held-native-uv-mapping' for s in scenes for m in s['materials']),
                  scope='Candidate history only. No terrain runtime resources or installed selections are changed.')
    for path, data in writes.items():
        publication.check_version(data, path)
    writes[production / 'terrain-artwork.json'] = publication.json_bytes(ledger)
    publication.publish(writes, set())
    return len(assets)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--staging', type=Path, required=True)
    parser.add_argument('--reviews', type=Path, required=True)
    args = parser.parse_args()
    print('Versioned custom terrain candidates:', publish(args.files, args.staging, args.reviews))
