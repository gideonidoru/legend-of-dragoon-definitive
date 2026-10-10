#!/usr/bin/env python3
"""Source-backed ModelsHD authoring catalog; AGPL v3, see LICENSE.

Publishes metadata only: identities, route evidence, counts and measured reuse.
Never publishes source meshes, textures, scripts, raw extraction or absolute paths.
"""
import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import struct
import subprocess


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


exporter = module('catalog_export', 'export-modelshd-pilot.py')
KINDS = {'party-field', 'story-npc', 'field-unresolved', 'prop', 'world-map-party', 'world-map-vehicle', 'world-map-scenery',
         'enemy', 'boss', 'story-combatant', 'battle-stage', 'dragoon-effect', 'item-effect', 'enemy-effect', 'cutscene-effect',
         'effect-unresolved', 'field-overlay', 'utility', 'unresolved-container'}
CONFIDENCE = {'source-verified', 'context-verified', 'unresolved'}
BUCKETS = {'1-party': {'party-field', 'world-map-party'}, '2-story-npcs': {'story-npc'},
           '3-bosses-story-combatants': {'boss', 'story-combatant'}, '4-enemies': {'enemy'},
           '5-scenes-props-effects': {'prop', 'world-map-vehicle', 'world-map-scenery', 'battle-stage', 'dragoon-effect', 'item-effect', 'enemy-effect', 'cutscene-effect', 'field-overlay', 'utility'},
           '6-unresolved-actor': {'field-unresolved', 'effect-unresolved', 'unresolved-container'}}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def relative(value):
    if not isinstance(value, str) or '\\' in value or '\x00' in value:
        raise ValueError('Invalid source identifier')
    path = PurePosixPath(value)
    if path.is_absolute() or '..' in path.parts or not path.parts:
        raise ValueError('Source identifiers must be bounded relative paths')
    return value


def clean_record(record):
    allowed = {'kind', 'label', 'entityName', 'confidence', 'evidence', 'contexts', 'candidateNames',
               'family', 'monsterId', 'stageId', 'battleStageId', 'notes', 'routeRole', 'objectIndex',
               'script', 'texture', 'formatCaveat', 'encounterRole', 'semanticFamilyCandidate', 'semanticFamilyBasis',
               'bossKillScriptContext', 'bossClassificationBasis'}
    value = {k: record[k] for k in allowed if k in record}
    if value.get('kind') not in KINDS or value.get('confidence') not in CONFIDENCE:
        raise ValueError('Unknown mapping kind or confidence: ' + repr(value))
    if not isinstance(value.get('label'), str) or not value['label']:
        raise ValueError('Every mapping needs a human-readable label')
    # The result is a public custom catalog, not a dump of private authoring inputs.
    text = json.dumps(value)
    if any(marker in text for marker in ('/Users/', '/private/', '/home/', 'file://')):
        raise ValueError('Private path in mapping metadata')
    if value.get('entityName') and value['confidence'] != 'source-verified':
        raise ValueError('Unproved actor identities must remain candidates')
    return value


def fingerprint_parts(parts):
    fingerprints = []
    topology = []
    for points, polygons in parts:
        refs = [p[0] for p in polygons]
        connectivity = json.dumps({'vertices': len(points), 'faces': refs}, separators=(',', ':')).encode()
        topology.append(sha(connectivity))
        fingerprints.append(sha(connectivity + exporter.np.asarray(points, dtype='>f4').tobytes()))
    # Ordered parts and face/vertex indices are retained; normal/material-only differences can be shared.
    return fingerprints, sha(json.dumps(fingerprints, separators=(',', ':')).encode()), sha(json.dumps(topology, separators=(',', ':')).encode())


def analyze(data, expected):
    parts, _, identity = exporter.native_parts(data)
    if identity != expected:
        raise ValueError('Input geometry differs from its inventoried canonical identity')
    _, packets, palette_animation, auxiliary = exporter.surface.poses.materials.faces(data)
    fingerprints, shape, topology = fingerprint_parts(parts)
    triangles = [triangle for _, polygons in parts for triangle in exporter.surface.triangles(polygons)]
    nonmanifold, collapsed, borders = 0, 0, 0
    for points, polygons in parts:
        edges = Counter()
        for refs, *_ in exporter.surface.triangles(polygons):
            if len(set(refs)) != 3 or exporter.np.linalg.norm(exporter.np.cross(points[refs[1]]-points[refs[0]], points[refs[2]]-points[refs[0]])) <= 1e-10:
                collapsed += 1
            edges.update(tuple(sorted(e)) for e in zip(refs, refs[1:]+refs[:1]))
        nonmanifold += sum(n > 2 for n in edges.values())
        borders += sum(n == 1 for n in edges.values())
    part_triangles = [sum(len(p[0])-2 for p in polygons) for _, polygons in parts]
    return {'parts': len(parts), 'vertices': sum(len(points) for points, _ in parts),
            'polygons': sum(len(polygons) for _, polygons in parts), 'triangles': len(triangles),
            'texturedFaces': len(packets), 'usedClutAddresses': len({clut for _, clut, _ in packets}),
            'hasClutAnimations': palette_animation, 'hasAuxiliaryData': auxiliary,
            'openBoundaryEdges': borders, 'nonmanifoldEdges': nonmanifold, 'collapsedTriangles': collapsed,
            'shapeSha256': shape, 'topologySha256': topology, 'partShapeSha256': fingerprints, 'partTriangles': part_triangles}


def fallback(source):
    if source == 'shadow.ctmd':
        return {'kind': 'utility', 'label': 'Shared character shadow', 'confidence': 'source-verified', 'evidence': ['src/main/java/legend/game/unpacker/Unpacker.java', 'src/main/java/legend/game/Graphics.java']}
    if source.startswith('SUBMAP/savepoint/'):
        return {'kind': 'prop', 'label': 'Save point', 'confidence': 'source-verified', 'entityName': 'Save point', 'evidence': ['src/main/java/legend/game/submap/SMap.java']}
    return {'kind': 'unresolved-container', 'label': 'Unresolved container ' + source, 'confidence': 'unresolved', 'evidence': [], 'notes': 'No supported semantic loader route or actor identity is established.'}


def choose_bucket(records):
    kinds = {r['kind'] for r in records}
    for bucket, members in BUCKETS.items():
        if members & kinds:
            return bucket
    raise ValueError('Unclassified mapping')


def summarize(rows):
    counts = Counter(row['bucket'] for row in rows)
    named = sum(bool(row['entityNames']) for row in rows)
    route = sum(any(r['confidence'] != 'unresolved' for a in row['appearances'] for r in a['mappings']) for row in rows)
    return {'remainingGeometryContainers': len(rows), 'sourceReferences': sum(len(r['appearances']) for r in rows),
            'exclusivePriorityBucketCounts': dict(sorted(counts.items())), 'namedGeometryContainers': named,
            'contextOrIdentityMappedContainers': route, 'unresolvedRouteContainers': len(rows)-route,
            'distinctEntityNames': len({n for r in rows for n in r['entityNames']}),
            'uniqueOrderedShapes': len({r['geometry']['shapeSha256'] for r in rows}),
            'uniqueOrderedTopologies': len({r['geometry']['topologySha256'] for r in rows}),
            'auxiliaryDataContainers': sum(r['geometry']['hasAuxiliaryData'] or r['geometry']['hasClutAnimations'] for r in rows),
            'nonmanifoldContainers': sum(r['geometry']['nonmanifoldEdges'] > 0 for r in rows),
            'collapsedTriangleContainers': sum(r['geometry']['collapsedTriangles'] > 0 for r in rows)}


def make_catalog(inventory, files, repo, maps):
    baseline = json.loads((repo/'integrations/modelshd/src/main/resources/modelshd/models/roster.json').read_text())
    core = {r['sourceGeometrySha256'] for r in baseline['models']}
    if len(core) != 19:
        raise ValueError('Expected the existing complete 19-model party battle roster')
    identities = set()
    source_identity_cache = {}
    rows = []
    for source_row in inventory['geometry']:
        identity = source_row['sourceGeometrySha256']
        if len(identity) != 64 or any(c not in '0123456789abcdef' for c in identity) or identity in identities:
            raise ValueError('Duplicate or invalid canonical geometry identity')
        identities.add(identity)
        if identity in core:
            continue
        sources = sorted({relative(s) for s in source_row['sources']})
        if len(sources) != len(source_row['sources']) or not sources:
            raise ValueError('Missing or duplicate source aliases')
        path = files/sources[0]
        if not path.resolve().is_relative_to(files.resolve()):
            raise ValueError('Source alias escapes the extraction root')
        data = exporter.surface.poses.materials.bounded_read(path)
        metrics = analyze(data, identity)
        source_identity_cache[sha(data)] = identity
        if metrics['parts'] != source_row['parts']:
            raise ValueError('Part count disagrees with inventory')
        appearances, records = [], []
        for source in sources:
            alias_path = files/source
            if not alias_path.resolve().is_relative_to(files.resolve()):
                raise ValueError('Source alias escapes the extraction root')
            alias = exporter.surface.poses.materials.bounded_read(alias_path)
            alias_digest = sha(alias)
            if alias_digest not in source_identity_cache:
                source_identity_cache[alias_digest] = exporter.native_parts(alias)[2]
            if source_identity_cache[alias_digest] != identity:
                raise ValueError('Inventoried alias no longer matches its canonical geometry: '+source)
            mapped = [clean_record(record) for mapping in maps for record in mapping.get(source, [])]
            if not mapped:
                mapped = [clean_record(fallback(source))]
            appearances.append({'source': source, 'sourceSha256': alias_digest, 'mappings': mapped})
            records.extend(mapped)
        names = sorted({r['entityName'] for r in records if r.get('entityName')})
        labels = sorted({r['label'] for r in records})
        kind = sorted({r['kind'] for r in records})
        bucket = choose_bucket(records)
        blockers = ['Geometry replacement hook for '+k for k in kind if k not in {'enemy','boss','story-combatant'}]
        if metrics['hasClutAnimations'] or metrics['hasAuxiliaryData']:
            blockers.append('Auxiliary/animated palette contract must be supported before replacement')
        if metrics['nonmanifoldEdges'] or metrics['collapsedTriangles']:
            blockers.append('Topology needs inspection before surface refinement')
        if metrics['parts'] > 64:
            blockers.append('Part count exceeds current ModelsHD adapter budget')
        if not names:
            blockers.append('Actor/prop identity needs confirmation; scene/effect names are context only')
        candidate_names = sorted({name for record in records for name in record.get('candidateNames', [])})
        location_names = sorted({context.get('location') for record in records for context in record.get('contexts', [])
                                 if isinstance(context, dict) and context.get('location')})
        label = ' / '.join(names) if names else labels[0]
        if not names and 'field-unresolved' in kind:
            label = (' / '.join(candidate_names) + ' · candidate') if candidate_names else 'Field object · ' + (' / '.join(location_names[:2]) or 'unnamed scene')
        rows.append({'sourceGeometrySha256': identity, 'catalogId': 'model-'+identity[:16], 'label': label,
                     'entityNames': names, 'kinds': kind, 'bucket': bucket, 'appearances': appearances,
                     'candidateEntityNames': candidate_names, 'locationNames': location_names,
                     'referenceCount': len(sources), 'sourceSha256': sha(data), 'geometry': metrics,
                     'readiness': 'research-required' if blockers else 'authoring-candidate', 'blockers': sorted(set(blockers))})
        if len(rows) % 100 == 0:
            print('Cataloged', len(rows), 'remaining geometry containers', flush=True)
    expected = inventory['summary']['additionalSupportedGeometryContainers']
    if len(rows) != expected or len(identities & core) != 19:
        raise ValueError('Catalog must cover exactly the inventoried remaining set and exclude exactly 19 core models')
    return rows, core


def reuse_groups(rows):
    shapes, topologies, parts = defaultdict(list), defaultdict(list), defaultdict(list)
    for row in rows:
        identity = row['catalogId']
        shapes[row['geometry']['shapeSha256']].append(identity)
        topologies[row['geometry']['topologySha256']].append(identity)
        for part_index, part in enumerate(row['geometry']['partShapeSha256']):
            parts[part].append({'model': identity, 'part': part_index})
    return {'exactOrderedShapeGroups': [{'shapeSha256': k, 'models': sorted(v)} for k,v in sorted(shapes.items()) if len(v)>1],
            'orderedTopologyCandidateGroups': [{'topologySha256': k, 'models': sorted(v), 'status': 'candidate-only: same indexed connectivity does not establish a shared character/family/animation rig'} for k,v in sorted(topologies.items()) if len(v)>1],
            'exactSharedPartGroups': [{'partShapeSha256':k, 'instances':v} for k,v in sorted(parts.items()) if len({x['model'] for x in v})>1]}


def component_candidates(rows, core_references):
    """Measured reuse suggestions, not automatic identity or export compatibility.

    Ignore tiny helper/quad parts; weight exact local-coordinate/connectivity
    matches by their triangle count. Duplicated parts use multiset intersection.
    Require at least half of each complete model, not only a shared small head.
    """
    all_models = rows + core_references
    weights, owners = {}, defaultdict(set)
    for row in all_models:
        g = row['geometry']
        counts = Counter()
        for key, triangles in zip(g['partShapeSha256'], g['partTriangles']):
            if triangles >= 8:
                counts[key] += 1
                weights[key] = triangles
                owners[key].add(row['catalogId'])
        row['_reuseCounts'] = counts
        row['_reuseWeight'] = sum(weights[key]*count for key,count in counts.items())
    lookup = {row['catalogId']:row for row in all_models}
    suggestions = []
    seen = set()
    for row in rows:
        candidates = set().union(*(owners[key] for key in row['_reuseCounts'])) if row['_reuseCounts'] else set()
        for other_id in sorted(candidates):
            pair = tuple(sorted((row['catalogId'],other_id)))
            if row['catalogId']==other_id or pair in seen:
                continue
            seen.add(pair)
            other = lookup[other_id]
            shared = sum(weights[key]*count for key,count in (row['_reuseCounts'] & other['_reuseCounts']).items())
            a,b = row['_reuseWeight'],other['_reuseWeight']
            if shared>=100 and a and b and shared/a>=.5 and shared/b>=.5:
                suggestions.append({'models':list(pair),'sharedTriangles':shared,
                                    'weightedCoverage':{row['catalogId']:round(shared/a,4),other_id:round(shared/b,4)},
                                    'status':'candidate-only: exact shared parts do not establish identity, part-index/animation or material compatibility'})
    for row in all_models:
        del row['_reuseCounts']; del row['_reuseWeight']
    return sorted(suggestions, key=lambda r:(-min(r['weightedCoverage'].values()), -r['sharedTriangles'],r['models']))


def outputs(catalog, directory):
    directory.mkdir(parents=True, exist_ok=True)
    path=directory/'model-catalog.json'
    path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False)+'\n')
    with (directory/'model-catalog.csv').open('w', newline='') as stream:
        writer=csv.writer(stream, lineterminator='\n')
        writer.writerow(['id','priority_bucket','label','proved_entity_names','kinds','source_references','parts','vertices','triangles','auxiliary_data','clut_animations','readiness','blockers','shape_sha256','source_geometry_sha256'])
        for r in catalog['models']:
            g=r['geometry']
            writer.writerow([r['catalogId'],r['bucket'],r['label'],' | '.join(r['entityNames']),' | '.join(r['kinds']),r['referenceCount'],g['parts'],g['vertices'],g['triangles'],g['hasAuxiliaryData'],g['hasClutAnimations'],r['readiness'],' | '.join(r['blockers']),g['shapeSha256'],r['sourceGeometrySha256']])
    print(json.dumps(catalog['summary'], indent=2))


def explorer(catalog):
    payload=json.dumps(catalog, separators=(',', ':'), ensure_ascii=False).replace('<','\\u003c')
    template=Path(__file__).with_name('modelshd-catalog-template.html').read_text()
    if template.count('@@CATALOG_JSON@@') != 1:
        raise ValueError('Invalid catalog explorer template')
    return template.replace('@@CATALOG_JSON@@', payload)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    repo=Path(__file__).resolve().parents[1]
    field=module('catalog_field','modelshd-field-map.py')
    battle=module('catalog_battle','modelshd-battle-map.py')
    maps=[field.build_source_map(args.files, repo), battle.build_source_map(args.files, repo)]
    inventory=json.loads(args.inventory.read_text())
    rows, core=make_catalog(inventory,args.files,repo,maps)
    core_references=[]
    baseline=json.loads((repo/'integrations/modelshd/src/main/resources/modelshd/models/roster.json').read_text())
    for row in baseline['models']:
        path=args.files/'characters'/row['character']/'models'/row['form']/'32'
        core_references.append({'catalogId':'core-'+row['sourceGeometrySha256'][:16], 'label':row['character']+' · '+row['form'],
                                'entityNames':[row['character'].title()], 'form':row['form'],
                                'geometry':analyze(exporter.surface.poses.materials.bounded_read(path),row['sourceGeometrySha256'])})
    shared_candidates=component_candidates(rows,core_references)
    rows.sort(key=lambda r:(r['bucket'], not bool(r['entityNames']), -r['referenceCount'], r['label'], r['catalogId']))
    hashes={name:sha((Path(__file__).parent/name).read_bytes()) for name in ['catalog-modelshd-sources.py','modelshd-field-map.py','modelshd-battle-map.py','inventory-modelshd-sources.py','export-modelshd-pilot.py','modelshd-catalog-template.html']}
    catalog={'format':1,'scope':'All remaining supported canonical containers in the inventoried extraction, not exhaustive game format coverage. Relative route/evidence metadata only; no original geometry, texture images or scripts.',
             'sourceRevision':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),
             'inputInventorySha256':sha(args.inventory.read_bytes()),'tools':hashes,'excludedPartyBattleModels':sorted(core),
             'summary':summarize(rows),'models':rows,'reuse':reuse_groups(rows),
             'coreReferenceMetrics':core_references,'sharedComponentCandidates':shared_candidates,
             'unsupportedReferenceCount':len(inventory['held']), 'unsupportedScope':dict(Counter(r['reason'] for r in inventory['held']))}
    text=json.dumps(catalog)
    if any(marker in text for marker in ('/Users/','/private/','file://')):
        raise ValueError('Public catalog contains a private absolute path')
    outputs(catalog,args.output)
    (args.output/'index.html').write_text(explorer(catalog))


if __name__=='__main__':
    main()
