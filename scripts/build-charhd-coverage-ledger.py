#!/usr/bin/env python3
"""Account for all discovered CharHD sources and unfinished acceptance stages. AGPLv3."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def build(census, candidates, runtime, catalog, source_root):
    candidate_rows = {row['packId']: row for row in candidates['records']}
    selected = {}
    for path in runtime.glob('**/manifest.json'):
        manifest = json.loads(path.read_text())
        pair = (manifest['modelSha256'], manifest['timSha256'])
        if pair in selected: raise ValueError('Ambiguous selected runtime pair')
        selected[pair] = manifest['atlasEngineSha256']
    records = []
    for row in census['records']:
        candidate = candidate_rows.get(row['packId'])
        pair = (row['modelSha256'], row['timSha256'])
        record = {key: row[key] for key in ('packId', 'modelSha256', 'timSha256', 'model', 'texture')}
        categories = sorted({c['category'] for c in row['consumers']})
        record.update(categories=categories, consumerCount=len(row['consumers']),
            ownership=sorted({c['ownership'] for c in row['consumers']}))
        if candidate:
            record['reconstruction'] = candidate['status']
            record['candidatePath'] = candidate.get('path')
        else:
            record['reconstruction'] = row['status']
        record.update(runtimeDelivery='selected-development' if pair in selected else 'not-selected',
            selectedAtlasSha256=selected.get(pair), artAccepted=False, nativeVisualAccepted=False,
            deckAccepted=False, remaining=[])
        if 'unresolved' in record['ownership']: record['remaining'].append('Resolve character versus environment/prop ownership')
        if record['reconstruction'] == 'hold-animated-or-extra-data':
            record['remaining'].append('Preserve and reconstruct animated palette/texture or extra-data behavior')
        elif record['reconstruction'] == 'hold-format-or-mapping':
            record['remaining'].append('Resolve exact texture consumer and nonstandard source mapping')
            record['reason'] = row.get('reason')
        elif record['reconstruction'] != 'no-textured-faces':
            if 'party-field-world' in categories: record['remaining'].append('Wire palette-aware field and world character routes')
            elif any(c in categories for c in ('npc-field', 'unresolved-field-actor')):
                record['remaining'].append('Wire palette-aware field character route')
            else: record['remaining'].append('Verify battle adapter and all scripted source transitions')
        if record['reconstruction'] != 'no-textured-faces':
            record['remaining'].extend(['Complete faithful AA art/material pass',
                'Validate native rendering, animation, transitions and cleanup',
                'Validate physical Steam Deck memory, performance and appearance'])
            if pair not in selected: record['remaining'].append('Select and include verified custom revision in normal installer')
        records.append(record)
    unresolved = census['unbound']
    failures = [{**row, 'reason': row['reason'].replace(str(source_root), '<source-root>')} for row in census['sourceFailures']]
    return {'pipeline': 'charhd-complete-coverage-ledger-1',
        'scope': 'Entire character texture program: all party forms/field/world, NPCs, story variants, enemies, bosses and scripted appearances; geometry/portraits/environment/effects retain separate owners',
        'fullGameDenominator': None,
        'denominatorStatus': 'Incomplete: unresolved ownership, unmapped/scripted consumers and source failures prevent a complete game population',
        'discoveredModelTimPairs': len(records), 'discoveredBindings': census['bindings'],
        'catalogGeometryContainers': len(catalog['models']),
        'originalRestorationQueue': candidates['originalQueueCount'],
        'reconstructionCounts': dict(Counter(row['reconstruction'] for row in records)),
        'selectedRuntimePairs': sum(row['runtimeDelivery'] == 'selected-development' for row in records),
        'artAccepted': 0, 'nativeVisualAccepted': 0, 'deckAccepted': 0,
        'unboundRoutes': len(unresolved), 'sourceFailures': len(failures),
        'publicationReady': False,
        'publicationBlockers': ['Complete source/consumer/ownership denominator remains unresolved',
            'Animated/extra-data and format/mapping routes remain unsupported',
            'Full field/world/runtime delivery remains incomplete',
            'AA art, native visual/gameplay and physical Steam Deck acceptance remain pending'],
        'records': records, 'unbound': unresolved, 'failedSources': failures}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--census', type=Path, required=True)
    parser.add_argument('--source-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    result = build(json.loads(args.census.read_text()),
        json.loads((root/'integrations/charhd/production/full-candidate-coverage.json').read_text()),
        root/'integrations/charhd/runtime-assets',
        json.loads((root/'docs/definitive/model-catalog/model-catalog.json').read_text()), args.source_root)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key not in ('records', 'unbound', 'failedSources')}))


if __name__ == '__main__': main()
