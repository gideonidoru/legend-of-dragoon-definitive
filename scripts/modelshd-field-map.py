#!/usr/bin/env python3
"""Source-backed field and world-map routes. Metadata only; see LICENSE.

Reads NEWROOT locally and derives relative asset identifiers. It never writes or
embeds extracted geometry, scripts, textures, or absolute extraction paths.
Patch descriptions are candidate labels, not verified actor assignments.
"""
import argparse
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import re
import struct

RETAIL = 'src/main/java/legend/game/submap/RetailSubmap.java'
ROOT = 'src/main/java/legend/game/types/NewRootStruct.java'
SITEM = 'src/main/java/legend/game/SItem.java'
WMAP = 'src/main/java/legend/game/wmap/WMap.java'
UNPACKER = 'src/main/java/legend/game/unpacker/Unpacker.java'
PARTY = ('Dart', 'Lavitz', 'Shana', 'Rose', 'Haschel', 'Albert', 'Meru', 'Kongol', 'Miranda')
# Exact actor-like comma clauses in upstream's patch metadata. A scene name
# mentioning a character is deliberately insufficient.
NAMED_CANDIDATES = PARTY + ('Emille', 'Lisa', 'Libria', 'Kaiser', 'Coolon', 'Bulgus', 'Puler', 'Kayla')


def _array(text, name):
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*\{(.*?)\};', text, re.S)
    if match is None:
        raise ValueError(f'Missing source table {name}')
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', match.group(1), flags=re.S)


def _numbers(text, name):
    return [int(n, 0) for n in re.findall(r'-?(?:0x[0-9a-fA-F]+|\d+)', _array(text, name))]


def _strings(text, name):
    return re.findall(r'"([^"\\]*)"', _array(text, name))


def _candidate_labels(description):
    result = []
    # Only standalone actor clauses; e.g. "Twin Castle, throne room cutscene,
    # Meru, widescreen patch". "Queen Theresa Kidnap" is scene context only.
    for clause in description.split(',')[1:]:
        clause = clause.strip()
        if clause in NAMED_CANDIDATES:
            result.append(clause)
        elif re.fullmatch(r'(?:Warden|Knight)(?: #[12])?', clause):
            result.append(clause)
        elif clause in ('Arrow', 'Elite', 'Sandora soldier', 'Serdio knight',
                        'Serdio wall knight', 'left wall knight', 'right wall knight',
                        'right tower knight', 'right tower knight #1', 'right tower knight #2',
                        'left balcony knight', 'right balcony knight', 'entrance knight',
                        'knight', 'wounded knight', 'fallen knight', 'reporting knight'):
            result.append(clause)
    return result


def build_source_map(files: Path, repo: Path):
    """Return relative source path -> metadata records for source-proved routes.

    The caller selects recognized containers, combines exact geometry identities,
    and excludes already-shipped core models. NEWROOT early/late alternatives
    are both recorded; no claim is made that both are reachable at every chapter.
    """
    files, repo = Path(files), Path(repo)
    retail = (repo / RETAIL).read_text()
    sitem = (repo / SITEM).read_text()
    cuts = _numbers(retail, 'cutToSubmap_800d610c')
    overlays = _numbers(retail, 'smapFileIndices_800f982c')
    names = _strings(sitem, 'submapNames_8011c108')
    world_names = _strings(sitem, 'worldMapNames_8011c1ec')
    if not cuts or not overlays or len(cuts) > 0x400:
        raise ValueError('Invalid source cut tables')
    data = (files / 'SUBMAP/NEWROOT.RDT').read_bytes()
    if len(data) < 0x2000:
        raise ValueError('NEWROOT is shorter than its 1024-entry table')
    result = defaultdict(list)
    routes = defaultdict(list)
    for cut, location in enumerate(cuts):
        context = {'cut': cut, 'locationId': location,
                   'location': names[location] if 0 <= location < len(names) else ''}
        for version, packed in zip(('early', 'late'), struct.unpack_from('<HH', data, cut * 8)):
            raw_disc = packed >> 13
            if raw_disc > 3:
                continue
            disc = raw_disc + 1
            environment = (packed & 0x1fff) * 3 + 4
            entry = dict(context, routeVariant=version, disc=disc,
                         environmentDirectory=environment, modelDirectory=environment + 1,
                         scriptDirectory=environment + 2)
            if entry not in routes[(disc, environment + 1)]:
                routes[(disc, environment + 1)].append(entry)
        if cut < len(overlays) and overlays[cut]:
            path = f'SECT/DRGN0.BIN/{overlays[cut]}/0'
            result[path].append({'kind': 'prop', 'label': 'Field 3D scene overlay',
                                 'confidence': 'context-verified',
                                 'evidence': [RETAIL + ':280', RETAIL + ':1981'],
                                 'contexts': [context], 'routeRole': 'field-scene-overlay'})
    for (disc, directory), contexts in sorted(routes.items()):
        asset_dir = files / f'SECT/DRGN2{disc}.BIN/{directory}'
        if not asset_dir.is_dir():
            continue
        for path in sorted(asset_dir.iterdir()):
            if not path.is_file() or not path.name.isdecimal() or int(path.name) % 33:
                continue
            index = int(path.name) // 33
            relative = path.relative_to(files).as_posix()
            script = f'SECT/DRGN2{disc}.BIN/{directory + 1}/{index + 1}'
            record = {'kind': 'field-unresolved', 'label': f'Field object {index}',
                      'confidence': 'context-verified',
                      'evidence': [ROOT + ':39', RETAIL + ':238', RETAIL + ':554', RETAIL + ':1790', SITEM + ':220'],
                      'contexts': [dict(c, objectIndex=index, script=script) for c in contexts],
                      'routeRole': 'field-sobj', 'objectIndex': index, 'script': script,
                      'texture': f'SECT/DRGN2{disc}.BIN/{directory}/textures/{index}'}
            result[relative].append(record)
    # Exact template load list binds named party members to source geometry.
    for name in PARTY:
        template = f'src/main/java/legend/lodmod/characters/{name}Template.java'
        text = (repo / template).read_text()
        body = re.search(r'void loadWorldMapModel\(.*?\n  }', text, re.S)
        if body is None:
            raise ValueError(f'Missing world-map source mapping for {name}')
        paths = re.findall(r'"(SECT/DRGN2[1-4]\.BIN/\d+/\d+)"', body.group(0))
        if not paths:
            raise ValueError(f'No geometry route in {name} template')
        result[paths[0]].append({'kind': 'world-map-party', 'label': name,
                                 'entityName': name, 'confidence': 'source-verified',
                                 'evidence': [template, WMAP + ':1851', WMAP + ':3060'],
                                 'contexts': [{'usage': 'world-map-party-avatar'}]})
        result[paths[0]].append({'kind': 'party-field', 'label': name,
                                 'entityName': name, 'confidence': 'source-verified',
                                 'evidence': [template, RETAIL + ':573'],
                                 'contexts': [{'usage': 'field-object-source-also-used-as-world-map-avatar'}]})
    # The unpacker explicitly names Claire and replaces these known model slots.
    for path in ('SECT/DRGN22.BIN/863/33', 'SECT/DRGN23.BIN/506/33', 'SECT/DRGN24.BIN/260/33'):
        result[path].append({'kind': 'story-npc', 'label': 'Claire', 'entityName': 'Claire',
                             'confidence': 'source-verified', 'evidence': [UNPACKER + ':874'],
                             'contexts': [{'usage': 'Claire-model-retail-repair'}]})
    # Descriptions annotate scripts, not an actor registry. Keep them candidates.
    with (repo / 'patches/scripts.csv').open(newline='') as stream:
        for line, row in enumerate(csv.reader(stream), 1):
            if len(row) < 4:
                continue
            match = re.fullmatch(r'SECT/DRGN2([1-4])\.BIN/(\d+)/(\d+)', row[1])
            if not match or int(match[3]) == 0:
                continue
            disc, script_dir, script_file = map(int, match.groups())
            model = f'SECT/DRGN2{disc}.BIN/{script_dir - 1}/{(script_file - 1) * 33}'
            if model not in result:
                continue
            candidates = _candidate_labels(row[3])
            if candidates:
                result[model].append({'kind': 'field-unresolved',
                                     'label': ' / '.join(candidates) + ' (patch metadata candidate)',
                                     'candidateNames': candidates, 'confidence': 'unresolved',
                                     'evidence': [f'patches/scripts.csv:{line}', RETAIL + ':570'],
                                     'contexts': [{'script': row[1], 'patchDescription': row[3]}]})
    for index, name in enumerate(world_names):
        result[f'SECT/DRGN0.BIN/{5705 + index}'].append({
            'kind': 'world-map-scenery', 'label': name + ' world-map scenery',
            'confidence': 'source-verified', 'evidence': [WMAP + ':2154', SITEM + ':235'],
            'contexts': [{'continentIndex': index, 'location': name}],
            'formatCaveat': 'Bare TMD load; may not be recognized by CContainer inventory'})
    world_models = ((5715, 'Queen Fury', 'world-map-vehicle', 'Queen Fury'),
                    (5716, 'Coolon', 'world-map-vehicle', 'Coolon'),
                    (5717, 'World-map teleport animation', 'prop', None),
                    (5714, 'Legacy world-map party avatar (currently not loaded)', 'world-map-party', None))
    for number, label, kind, entity in world_models:
        record = {'kind': kind, 'label': label, 'confidence': 'source-verified' if entity else 'context-verified',
                  'evidence': [WMAP + ':1815', WMAP + ':3025', WMAP + ':2480', WMAP + ':3128', WMAP + ':3200'],
                  'contexts': [{'usage': 'world-map', 'worldModelSlot': number - 5714}]}
        if entity:
            record['entityName'] = entity
        result[f'SECT/DRGN0.BIN/{number}/0'].append(record)
    for path, label, confidence in (
            ('SUBMAP/savepoint/2', 'Save point', 'source-verified'),
            ('SUBMAP/savepoint/4', 'Additional save-point resource (not loaded by current SMap)', 'context-verified'),
            ('shadow.ctmd', 'Shared character shadow', 'source-verified')):
        result[path].append({'kind': 'prop', 'label': label, 'confidence': confidence,
                             'evidence': [UNPACKER + ':720', UNPACKER + ':1356', 'src/main/java/legend/game/submap/SMap.java:4070'],
                             'contexts': [{'usage': 'field-or-shared-visual-resource'}]})
    return dict(result)


def summarize(mapping):
    return {'sourceRoutes': len(mapping),
            'kindReferences': dict(Counter(r['kind'] for records in mapping.values() for r in records)),
            'verifiedEntities': sorted({r['entityName'] for records in mapping.values() for r in records if 'entityName' in r}),
            'candidateActorReferences': sum(any('candidateNames' in r for r in records) for records in mapping.values())}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    mapping = build_source_map(args.files, args.repo)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('x') as stream:
            json.dump(mapping, stream, indent=2)
            stream.write('\n')
    print(json.dumps(summarize(mapping), indent=2))
