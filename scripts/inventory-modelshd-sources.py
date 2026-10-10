#!/usr/bin/env python3
"""Private geometry-container inventory, AGPL v3; see LICENSE.

Counts source references and exact canonical geometry, not named characters or sculpts.
Recognizes bounded relative TMD containers; unsupported formats remain explicit.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import struct

spec = importlib.util.spec_from_file_location('roster', Path(__file__).with_name('build-modelshd-roster.py'))
roster = importlib.util.module_from_spec(spec)
spec.loader.exec_module(roster)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    files, output = args.files.resolve(), args.output.resolve()
    repo = Path(__file__).resolve().parents[1]
    if output == repo or repo in output.parents:
        parser.error('Keep the detailed source-path inventory outside Git')
    if output.exists():
        parser.error('Choose a new output file')
    identities, held, seen = {}, [], 0
    supported = Counter()
    for path in sorted(files.rglob('*')):
        if not path.is_file():
            continue
        seen += 1
        with path.open('rb') as stream:
            header = stream.read(64)
        if len(header) < 24:
            continue
        offset = struct.unpack_from('<I', header)[0]
        if not 12 <= offset <= 40 or struct.unpack_from('<I', header, offset)[0] != 0x41:
            continue
        flags, parts = struct.unpack_from('<2I', header, offset+4)
        if not 0 < parts <= 256:
            continue
        relative = str(path.relative_to(files))
        group = '/'.join(path.relative_to(files).parts[:2]) if relative.startswith('SECT/') else path.relative_to(files).parts[0]
        try:
            if flags != 0:
                raise ValueError(f'Unsupported TMD flags {flags}')
            data = roster.exporter.surface.poses.materials.bounded_read(path)
            _, _, identity = roster.exporter.native_parts(data)
        except (ValueError, struct.error, IndexError) as error:
            held.append({'source': relative, 'group': group, 'reason': str(error)})
            continue
        supported[group] += 1
        entry = identities.setdefault(identity, {'sourceGeometrySha256': identity, 'parts': parts, 'sources': []})
        entry['sources'].append(relative)
    core = set()
    for name, form in roster.ROSTER:
        _, _, identity = roster.exporter.native_parts((files / 'characters' / name / 'models' / form / '32').read_bytes())
        core.add(identity)
    summary = {'filesScanned': seen, 'recognizedSourceReferences': sum(supported.values())+len(held), 'supportedSourceReferences': sum(supported.values()),
               'uniqueSupportedGeometryContainers': len(identities), 'coreBattleGeometries': len(core), 'additionalSupportedGeometryContainers': len(set(identities)-core),
               'unsupportedReferences': len(held), 'supportedReferencesByLocation': dict(supported), 'heldReasons': dict(Counter(row['reason'] for row in held))}
    report = {'scope': 'Header-selected relative TMD containers only. Exact geometry deduplication excludes texture addressing. A container may be a person, prop, multipart boss, effect or variant. No semantic identity, near-duplicate grouping or exhaustive format coverage.',
              'toolSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'summary': summary, 'geometry': list(identities.values()), 'held': held}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x') as stream:
        json.dump(report, stream, indent=2); stream.write('\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
