#!/usr/bin/env python3
"""Public custom smoothing packs, local original inputs; AGPL v3, see LICENSE.

One source-bound pass per unique eligible rigid part. Original geometry controls
are never exported. Manifest covers every catalog container and the core roster.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile

spec = importlib.util.spec_from_file_location('world_export', Path(__file__).with_name('export-modelshd-pilot.py'))
exporter = importlib.util.module_from_spec(spec); spec.loader.exec_module(exporter)
np = exporter.np


def sha(data):
    return hashlib.sha256(data).hexdigest()


def part_identities(data, parts, native):
    """Exactly ModelPack.identity([table]): material addressing excluded."""
    table = struct.unpack_from('<I', data)[0] + 12
    result = []
    for i, ((points, polygons), (normals, refs)) in enumerate(zip(parts, native)):
        digest = hashlib.sha256()
        put = lambda value: digest.update(struct.pack('>I', value & 0xffffffff))
        _, _, _, _, offset, faces, scale = struct.unpack_from('<7I', data, table+i*28)
        put(1); put(scale)
        for vectors in (points, normals):
            put(len(vectors)); digest.update(np.asarray(vectors, dtype='>f4').tobytes())
        put(faces); cursor = table+offset
        for polygon, normal_refs in zip(polygons, refs):
            header = struct.unpack_from('<I', data, cursor)[0]
            put(header & 0xff040000)
            mode = header >> 24
            for corner, vertex in enumerate(polygon[0]):
                if not mode & 1 and (mode & 16 or corner == 0): put(normal_refs[corner])
                put(vertex)
            cursor += 4+((header >> 8) & 255)*4
        result.append(digest.hexdigest())
    return result


def refine_candidate(identity, part, native):
    candidate, reports = exporter.export_geometry([part], [native], identity, strength=.7, crease_degrees=70)
    report = reports[0]
    if not report['changed']:
        return None, report
    points, polygons = part
    new = candidate['parts'][0]
    # Avoid 4x tessellation of flat/helpers that gain no surface displacement.
    displacement = 0.
    for face in new['faces']:
        original = points[polygons[face['sourceFace']][0]]
        normal = np.cross(original[1]-original[0],original[2]-original[0])
        normal /= np.linalg.norm(normal)
        delta = np.array(new['vertices'])[face['vertices']] - np.array(face['sourceWeights']) @ original
        displacement = max(displacement,float(np.abs(delta @ normal).max()))
    if displacement <= 1e-8:
        return None, {**report, 'changed': False, 'reason': 'no geometric smoothing benefit'}
    report['maxSurfaceDisplacement'] = displacement
    triangles = exporter.surface.triangles(polygons)
    edges = Counter(tuple(sorted(edge)) for refs, *_ in triangles for edge in zip(refs, refs[1:]+refs[:1]))
    boundary = sorted({v for edge,n in edges.items() if n == 1 for v in edge})
    if boundary and not np.array_equal(points[boundary],np.array(new['vertices'])[boundary]):
        raise ValueError('Smoothing moved an original open joint border')
    for face in new['faces']:
        a,b,c = np.array(new['vertices'])[face['vertices']]
        if np.linalg.norm(np.cross(b-a,c-a)) <= 1e-10:
            return None, {**report,'changed':False,'reason':'refinement collapsed a triangle'}
    payload = json.dumps(candidate,separators=(',',':'),allow_nan=False).encode()
    if len(payload)>16*1024*1024 or len(new['vertices'])>65535 or len(new['faces'])>50000:
        return None, {**report,'changed':False,'reason':'native part budget exceeded'}
    return payload, report


def build(files, catalog_path, core_path, output):
    files, output = files.resolve(), output.resolve()
    if output.exists(): raise FileExistsError('Choose a new output; existing custom work is retained')
    catalog = json.loads(catalog_path.read_text()); core = json.loads(core_path.read_text())
    jobs = [{'id':r['sourceGeometrySha256'],'label':r['label'], 'bucket':r['bucket'],
             'sources':r['appearances']} for r in catalog['models']]
    jobs += [{'id':r['sourceGeometrySha256'], 'label':r['character']+' / '+r['form'], 'bucket':'core-party-battle',
              'sources':[{'source':f"characters/{r['character']}/models/{r['form']}/32",'sourceSha256':r['sourceSha256']}]} for r in core['models']]
    if len(jobs)!=1343 or len({r['id'] for r in jobs})!=1343: raise ValueError('Expected all 1324 remaining plus 19 core containers')
    output.parent.mkdir(parents=True,exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.modelshd-world-',dir=output.parent))
    try:
        folder = stage/'parts'; folder.mkdir()
        processed, entries, coverage = {}, {}, []
        for index, job in enumerate(jobs):
            sources = job['sources']
            for source in sources:
                identifier = source['source']; path = files/identifier
                if Path(identifier).is_absolute() or '..' in Path(identifier).parts or not path.resolve().is_relative_to(files):
                    raise ValueError('Invalid source alias')
                data = exporter.surface.poses.materials.bounded_read(path)
                if sha(data)!=source['sourceSha256']: raise ValueError('Catalog source changed: '+identifier)
            data = exporter.surface.poses.materials.bounded_read(files/sources[0]['source'])
            parts,native,identity = exporter.native_parts(data)
            if identity!=job['id']: raise ValueError('Canonical geometry changed')
            part_ids = part_identities(data,parts,native); states=[]
            for part_id,part,attributes in zip(part_ids,parts,native):
                if part_id not in processed:
                    payload,report = refine_candidate(part_id,part,attributes)
                    if payload is not None:
                        (folder/(part_id+'.json')).write_bytes(payload)
                        entries[part_id] = {'sourceGeometrySha256':part_id,'packSha256':sha(payload),'packBytes':len(payload),
                                           'sourceVertices':len(part[0]),'candidateVertices':report['vertices'],
                                           'sourceTriangles':report['inputTriangles'],'candidateTriangles':report['outputTriangles']}
                    processed[part_id] = report
                states.append({'sourcePartGeometrySha256':part_id, **processed[part_id]})
            coverage.append({'sourceGeometrySha256':identity,'label':job['label'],'bucket':job['bucket'],
                             'parts':states,'changedParts':sum(s['changed'] for s in states),
                             'hasClutAnimations':bool(struct.unpack_from('<I',data,4)[0]),
                             'hasTextureAnimationData':bool(struct.unpack_from('<I',data,8)[0])})
            if (index+1)%100==0: print('Inspected',index+1,'containers',flush=True)
        summary = {'inspectedContainers':len(coverage),'containersWithSmoothing':sum(r['changedParts']>0 for r in coverage),
                   'containersRetained':sum(r['changedParts']==0 for r in coverage),'uniqueSourceParts':len(processed),
                   'uniqueSmoothedParts':len(entries),'uniquePartsRetained':len(processed)-len(entries),
                   'partInstances':sum(len(r['parts']) for r in coverage),
                   'smoothedPartInstances':sum(r['changedParts'] for r in coverage),
                   'uniqueSourceTrianglesSmoothed':sum(r['sourceTriangles'] for r in entries.values()),
                   'uniqueCandidateTriangles':sum(r['candidateTriangles'] for r in entries.values()),
                   'customPayloadBytes':sum(r['packBytes'] for r in entries.values()),
                   'retainedReasons':dict(Counter(r.get('reason','unspecified') for r in processed.values() if not r['changed'])),
                   'containersWithSmoothingByBucket':dict(Counter(r['bucket'] for r in coverage if r['changedParts']>0))}
        manifest = {'format':1,'version':'0.4.0','recipe':{'iterations':1,'strength':.7,'creaseDegrees':70,'jointBorders':'fixed'},
                    'scope':'Rendered geometry only; original CPU tables, animation, scripts and material ownership retained. No gameplay or physical Deck acceptance claim.',
                    'catalogSha256':sha(catalog_path.read_bytes()),'coreRosterSha256':sha(core_path.read_bytes()),
                    'tools':{name:sha(Path(__file__).with_name(name).read_bytes()) for name in ['build-modelshd-world-pass.py','export-modelshd-pilot.py','refine-model-surfaces.py']},
                    'summary':summary,'partPacks':[entries[k] for k in sorted(entries)],'coverage':coverage}
        (stage/'world-pass.json').write_text(json.dumps(manifest,indent=2)+'\n')
        if output.exists(): raise FileExistsError('Output appeared during generation')
        os.rename(stage,output)
        return manifest
    except BaseException:
        shutil.rmtree(stage);raise


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files',type=Path,required=True);parser.add_argument('--catalog',type=Path,required=True)
    parser.add_argument('--core-roster',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    print(json.dumps(build(args.files,args.catalog,args.core_roster,args.output)['summary'],indent=2))
