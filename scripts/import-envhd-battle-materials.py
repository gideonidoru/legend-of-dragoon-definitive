#!/usr/bin/env python3
"""Publish reviewed custom battle candidates; never change selected runtime artwork."""
import argparse
import copy
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Actual inference run from 2d7986ce2. Later safeguards change no current decode/crop.
GENERATION_PIPELINE = '7862ee67731b77e646c22f298ebcb3325206a0a38ddf2eb8bea21882ce03c153'


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


battle = module('battle_candidates', 'batch-envhd-battle-materials.py')
publication = module('battle_publication', 'import-envhd-sky.py')
terrain = battle.terrain


def publish(files, staging, review_file):
    battle.private_destination(files, staging)
    if staging.resolve().is_relative_to(ROOT):
        raise ValueError('Battle staging must remain private')
    scenes, masters, empty = battle.census(files)
    records = battle.read_control(battle.staging_path(staging, 'candidates.json'))
    reviews = battle.read_control(review_file)
    if not isinstance(records, list) or not isinstance(reviews, dict) or set(reviews) != {'reviews'} or not isinstance(reviews['reviews'], dict):
        raise ValueError('Invalid complete battle review controls')
    notes = reviews['reviews']
    if len(records) != len(masters) or any(not isinstance(r, dict) for r in records):
        raise ValueError('Requires a complete unique battle candidate batch')
    keys = [r.get('decodedRgbaSha256') for r in records]
    if any(not isinstance(key, str) for key in keys) or len(set(keys)) != len(masters) or set(keys) != set(masters) or set(notes) != set(masters):
        raise ValueError('Candidate/review identities differ from the fresh source census')
    source_plan = battle.read_control(battle.staging_path(staging, 'source-plan.json'))
    pipeline = source_plan.get('scriptSha256')
    if pipeline not in (GENERATION_PIPELINE, terrain.digest((ROOT / 'scripts/batch-envhd-battle-materials.py').read_bytes())):
        raise ValueError('Unreviewed battle generation pipeline')
    expected = battle.plan(scenes, masters, empty)
    expected['scriptSha256'] = pipeline
    if pipeline == GENERATION_PIPELINE:
        # Verify the exact historic plan, not a falsely relabelled inference run.
        # Changed exclusion STATUS, source pixels, crops or bindings still reject.
        expected = copy.deepcopy(expected)
        for scene in expected['scenes']:
            for binding in scene['materials']:
                if binding['status'] == 'held-native-texture-animation':
                    binding['reason'] = 'UV bounds or their source padding intersect an animated VRAM rectangle'
    if source_plan != expected:
        raise ValueError('Battle source plan/provenance changed; refresh the batch')
    writes, assets = {}, []
    production = ROOT / 'integrations/envhd/production'
    for record in records:
        key = record['decodedRgbaSha256']
        item, note = masters[key], notes[key]
        required = {'intent', 'style', 'layout', 'verdict', 'outputSha256', 'nativeAcceptance', 'finalQualityAcceptance'}
        if not isinstance(note, dict) or set(note) != required or any(not isinstance(value, str) or not value.strip() for value in note.values()):
            raise ValueError('Explicit source intent, style and layout review required')
        if note['verdict'] not in ('visual-reviewed-runtime-pending', 'intent-revision-needed') or note['nativeAcceptance'] != 'pending' or note['finalQualityAcceptance'] != 'pending':
            raise ValueError('Candidate import cannot establish native or final acceptance')
        png = terrain.bounded_read(battle.staging_path(staging, key + '.png'), 32 * 1024 * 1024)
        terrain.validate_completed_bytes(item, record, png)
        if note['outputSha256'] != terrain.digest(png):
            raise ValueError('Battle review differs from this candidate output')
        metadata = dict(record, reviewStatus=note['verdict'], review=note, batchPipelineSha256=pipeline,
                        batchDependencySha256=source_plan['dependencySha256'],
                        sourceRole='Static battle floor, wall and scenery surfaces; geometry and native animations remain authoritative',
                        styleReferenceUse='Skurfa visual reference only; its resources are unchanged',
                        ownerApproval='Batch pipeline and Python repair explicitly authorized 2026-10-10')
        directory = production / 'battle-material-candidates' / key
        writes[directory / 'image-v1.png'] = png
        writes[directory / 'manifest-v1.json'] = publication.json_bytes(metadata)
        assets.append(metadata)
    ledger = dict(schema=1, assets=assets, scenes=scenes, emptyModelSlots=empty,
                  runtimeSelected=0, nativeAccepted=0, finalQualityAccepted=0,
                  visualReviewed=sum(a['reviewStatus'] == 'visual-reviewed-runtime-pending' for a in assets),
                  revisionsNeeded=sum(a['reviewStatus'] == 'intent-revision-needed' for a in assets),
                  protectedPilotBindings=sum(b['status'] == 'protected-existing-pilot' for s in scenes for b in s['materials']),
                  heldBindings=sum(b['status'].startswith('held-native-') for s in scenes for b in s['materials']),
                  scope='Candidate history only; no battle runtime resources or installed selections change')
    for path, data in writes.items():
        battle.staging_path(ROOT, str(path.relative_to(ROOT)))
        publication.check_version(data, path)
    ledger_path = production / 'battle-material-artwork.json'
    battle.staging_path(ROOT, str(ledger_path.relative_to(ROOT)))
    writes[ledger_path] = publication.json_bytes(ledger)
    publication.publish(writes, set())
    return len(assets)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--staging', required=True, type=Path)
    parser.add_argument('--reviews', required=True, type=Path)
    args = parser.parse_args()
    print('Versioned custom battle-material candidates:', publish(args.files, args.staging, args.reviews))
