#!/usr/bin/env python3
"""Verify actual staged FxHD output, including upgrades with a retained pilot JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(package, stale=None):
    descriptor = json.loads((ROOT / 'delivery/mods/hd-mods.json').read_text())
    current = next(m['jar'] for m in descriptor['mods'] if m['id'] == 'fxhd')
    bundled = package / 'bundled-mods'
    found = sorted(p.name for p in bundled.glob('FxHD-*.jar'))
    if found != [current]:
        raise ValueError('Expected only the current FxHD output; found ' + repr(found))
    if stale is not None and (not stale.is_file() or (bundled / stale.name).exists()):
        raise ValueError('Incremental fixture must remain outside the package')
    ledger = json.loads((ROOT / 'integrations/fxhd/production/field-selection.json').read_text())
    expected = {r['runtime']['file']: r['runtime']['sha256'] for r in ledger['assets'] if r['selected']}
    full = json.loads((ROOT / 'integrations/fxhd/production/full-coverage.json').read_text())
    consumers = json.loads((ROOT / 'integrations/fxhd/production/consumer-coverage.json').read_text())
    if full['state'] != 'all-census-sources-generated-and-bound' or full['sourceCount'] != len(full['sources']) or full['uniqueJobs'] != len(full['outputs']) or {r['job'] for r in full['sources']} != set(full['outputs']):
        raise ValueError('Incomplete full FX ledger')
    if consumers['unclassifiedConsumers'] != 0:
        raise ValueError('Unclassified FX consumers')
    inventoried = {r['source'] for r in consumers['entries']}
    actual = {str(p.relative_to(ROOT)) for group in ('effects', 'particles') for p in (ROOT / 'src/main/java/legend/game/combat' / group).glob('*.java')}
    if not actual <= inventoried:
        raise ValueError('New effects/particles missing from consumer census')
    expected.update({'full/detail/' + job + '.png': info['outputSha256'] for job, info in full['outputs'].items()})
    bindings = '\n'.join(sorted({'\t'.join([r['sourceSha256'],r['job'],full['outputs'][r['job']]['outputSha256'],*map(str,r['sourceSize'])]) for r in full['sources']})) + '\n'
    with zipfile.ZipFile(bundled / current) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError('Invalid or duplicate FxHD archive entries')
        images = {n[len('fxhd/'):] for n in names if n.startswith('fxhd/') and n.endswith('.png')}
        if images != set(expected):
            raise ValueError('Runtime images differ from the reviewed selection')
        if archive.read('fxhd/full/bindings.tsv').decode() != bindings:
            raise ValueError('Incomplete runtime source bindings')
        for name, digest in expected.items():
            if hashlib.sha256(archive.read('fxhd/' + name)).hexdigest() != digest:
                raise ValueError('Runtime hash differs: ' + name)
        if any('production/' in n or n.endswith('.tim') for n in names):
            raise ValueError('Production candidates or source controls entered the runtime JAR')
        for notice in ('LICENSE', 'README.md', 'ARTWORK-NOTICES.txt'):
            if 'META-INF/fxhd/' + notice not in names:
                raise ValueError('Missing attribution: ' + notice)
        if 'fxhd/FxHdMod.class' not in names:
            raise ValueError('Missing runtime adapter')
    print(f'FxHD package verified: one current adapter, {full["sourceCount"]} covered sources, {full["uniqueJobs"]} indexed maps plus six field RGB images, complete bindings/hashes/notices; no source controls.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    parser.add_argument('--stale-fxhd', type=Path)
    args = parser.parse_args()
    verify(args.package, args.stale_fxhd)
