#!/usr/bin/env python3
"""Build a private offline pose comparison viewer. AGPL v3; see LICENSE.
Validates inspection output identities; does not certify artwork or native rendering.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import PIL


spec = importlib.util.spec_from_file_location('transparency', Path(__file__).with_name('preserve-texture-transparency.py'))
transparency = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transparency)


def bounded_manifest(path):
    if path.is_symlink(): raise ValueError('Manifest must not be a symlink')
    with path.open('rb') as stream: data = stream.read(65537)
    if len(data) > 65536: raise ValueError('Manifest exceeds the review limit')
    result = json.loads(data)
    if not isinstance(result, dict): raise ValueError('Manifest must be an object')
    return result, hashlib.sha256(data).hexdigest()


def identity(value):
    if not isinstance(value, str) or len(value) != 64 or any(char not in '0123456789abcdef' for char in value):
        raise ValueError('Invalid source or output identity')
    return value


def build_review(inspection, output, packs=()):
    if output.resolve().is_relative_to(Path(__file__).resolve().parents[1]):
        raise ValueError('Use a new private output outside the source checkout')
    report, report_hash = bounded_manifest(inspection/'manifest.json')
    if report.get('pipeline') != 'definitive-private-pose-inspection-1': raise ValueError('Unsupported inspection')
    for name in ('modelSha256', 'timSha256', 'animationSha256'): identity(report.get(name))
    frames, yaws, candidates = report.get('keyframes'), report.get('cameraYawRadians'), report.get('candidates')
    if not isinstance(frames, list) or not 1 <= len(frames) <= 3 or any(type(frame) is not int or not 0 <= frame < 256 for frame in frames):
        raise ValueError('Invalid inspection keyframes')
    if not isinstance(yaws, list) or len(yaws) != len(frames) or any(type(yaw) not in (int, float) or not math.isfinite(yaw) for yaw in yaws):
        raise ValueError('Invalid inspection camera views')
    if not isinstance(candidates, list) or not 1 <= len(candidates) <= 3 or (packs and len(packs) != len(candidates)):
        raise ValueError('Provide one to three candidates and one optional pack per candidate')
    labels, allocation, pack_hashes = ['Original per-face palettes'], [None], []
    for index, candidate in enumerate(candidates):
        if not isinstance(candidate, dict) or not isinstance(candidate.get('label'), str) or len(candidate['label']) > 160:
            raise ValueError('Invalid candidate label')
        identity(candidate.get('sha256'))
        labels.append(candidate['label']); allocated = None
        if packs:
            pack, digest = bounded_manifest(packs[index]/'manifest.json')
            if pack.get('pipeline') != 'definitive-private-material-pack-1' or pack.get('atlasEngineSha256') != candidate['sha256'] or any(pack.get(name) != report[name] for name in ('modelSha256', 'timSha256')):
                raise ValueError('Allocation metadata does not match the inspected material')
            size = pack.get('atlasSize')
            if not isinstance(size, list) or len(size) != 2 or any(type(value) is not int or not 1 <= value <= 4096 for value in size):
                raise ValueError('Invalid atlas allocation dimensions')
            image, atlas_hash = transparency.read_png(packs[index]/'atlas-engine-stp.png', 4096)
            if atlas_hash != candidate['sha256'] or list(image.size) != size:
                raise ValueError('Allocation differs from the inspected atlas image')
            entries = pack.get('materials')
            if not isinstance(entries, list) or not 1 <= len(entries) <= 64 or any(not isinstance(entry, dict) or set(entry) != {'palette', 'sourceCrop', 'atlasRect'} or type(entry['palette']) is not int or not 0 <= entry['palette'] < 64 or any(not isinstance(entry[name], list) or len(entry[name]) != 4 or any(type(value) is not int for value in entry[name]) for name in ('sourceCrop', 'atlasRect')) for entry in entries):
                raise ValueError('Invalid allocation material map')
            mapping = {entry['palette']: entry for entry in entries}
            map_hash = hashlib.sha256(json.dumps(mapping, sort_keys=True).encode()).hexdigest()
            if len(mapping) != len(entries) or map_hash != candidate.get('materialMapSha256'):
                raise ValueError('Allocation material map differs from the inspected representation')
            allocated = size[0]*size[1]*4
            pack_hashes.append(digest)
        allocation.append(allocated)
    outputs = report.get('outputs')
    if not isinstance(outputs, list) or len(outputs) > 100: raise ValueError('Invalid inspection output list')
    declared = {}
    for item in outputs:
        if not isinstance(item, dict) or not isinstance(item.get('path'), str) or item['path'] in declared:
            raise ValueError('Invalid or duplicate inspection output')
        declared[item['path']] = identity(item.get('sha256'))
    views = []
    for frame, yaw in zip(frames, yaws):
        images = []
        for column in range(len(labels)):
            name = f'pose-{frame}-candidate-{column}.png'
            path = inspection/name
            if path.is_symlink() or path.stat().st_size > 1024*1024: raise ValueError('Invalid inspection image path or size')
            with path.open('rb') as stream: data = stream.read(1024*1024+1)
            if len(data) > 1024*1024: raise ValueError('Inspection image exceeds the review limit')
            if hashlib.sha256(data).hexdigest() != declared.get(name): raise ValueError('Inspection image identity differs')
            image, digest = transparency.read_png(path, 512)
            if image.size != (384, 448): raise ValueError('Review expects the recorded 384x448 inspection view')
            # Rehash the actual embedded bytes, independently of a second decoder read.
            if digest != hashlib.sha256(data).hexdigest(): raise ValueError('Inspection image changed while reading')
            images.append('data:image/png;base64,'+base64.b64encode(data).decode('ascii'))
        views.append({'label': f'Keyframe {frame} · camera {round(math.degrees(yaw))}°', 'images': images})
    payload = {'labels': labels, 'allocatedRgbaBytes': allocation, 'views': views,
               'sourceModelSha256': report['modelSha256'], 'sourceTimSha256': report['timSha256'],
               'sourceAnimationSha256': report['animationSha256'], 'inspectionManifestSha256': report_hash}
    template = Path(__file__).with_name('visual-review-template.html').read_text()
    encoded = json.dumps(payload, ensure_ascii=True).replace('<', '\\u003c')
    document = template.replace('__VISUAL_REVIEW_DATA__', encoded)
    if document == template: raise ValueError('Review template has no data marker')
    output.mkdir(exist_ok=False)
    (output/'index.html').write_text(document)
    provenance = {'pipeline': 'definitive-private-visual-review-1', 'inspectionManifestSha256': report_hash,
                  'materialManifestSha256': pack_hashes, 'indexSha256': hashlib.sha256(document.encode()).hexdigest(),
                  'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  'templateSha256': hashlib.sha256(template.encode()).hexdigest(), 'pillow': PIL.__version__,
                  'scope': 'Private offline raster preview. Allocation metadata is tied to the inspected candidate identity; no native rendering, runtime residency, art or community acceptance is certified.'}
    (output/'review-manifest.json').write_text(json.dumps(provenance, indent=2)+'\n')
    return output/'index.html'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inspection', type=Path, required=True)
    parser.add_argument('--pack', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if PIL.__version__ != '12.3.0': parser.error('Use Pillow 12.3.0 for the recorded image checks')
    print('Private review:', build_review(args.inspection, args.output, args.pack))


if __name__ == '__main__': main()
