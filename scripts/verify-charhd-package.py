#!/usr/bin/env python3
"""Check selected CharHD model/material assets in the actual installer package. AGPLv3."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(package):
    descriptor = json.loads((ROOT / 'delivery/mods/hd-mods.json').read_text())
    current = next(mod['jar'] for mod in descriptor['mods'] if mod['id'] == 'charhd')
    folder = package / 'bundled-mods'
    found = sorted(path.name for path in folder.glob('CharHD-*.jar'))
    if found != [current]:
        raise ValueError('Expected only the current CharHD JAR: ' + repr(found))
    selected = ROOT / 'integrations/charhd/runtime-assets/charhd/characters'
    manifest = json.loads((selected / 'characters.json').read_text())
    expected = {'characters.json'}
    if manifest['format'] != 1 or len({row['sourceGeometrySha256'] for row in manifest['parts']}) != len(manifest['parts']):
        raise ValueError('Invalid character selection')
    for row in manifest['parts']:
        for key in ('pack', 'uv', 'texture'):
            if key in row:
                path = row[key]
                if Path(path).is_absolute() or '..' in Path(path).parts:
                    raise ValueError('Invalid selected resource path')
                expected.add(path)
                if hashlib.sha256((selected / path).read_bytes()).hexdigest() != row[key + 'Sha256']:
                    raise ValueError('Selected resource checksum mismatch: ' + path)
    with zipfile.ZipFile(folder / current) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError('Duplicate or corrupt CharHD archive entries')
        prefix = 'charhd/characters/'
        actual = {name[len(prefix):] for name in names if name.startswith(prefix) and not name.endswith('/')}
        if actual != expected:
            raise ValueError('Shipped character resources differ from selected inventory')
        for path in expected:
            if archive.read(prefix + path) != (selected / path).read_bytes():
                raise ValueError('Shipped character bytes differ: ' + path)
        for name in ('LICENSE', 'README.md', 'ARTWORK-NOTICES.txt', 'TRIPOSR-LICENSE.txt'):
            if 'META-INF/charhd/' + name not in names:
                raise ValueError('Missing CharHD attribution: ' + name)
        if 'charhd/ReconstructedCharacters.class' not in names or 'charhd/CharHdMod.class' not in names:
            raise ValueError('Missing production character adapter')
        if any('charhd-experiment/' in name or 'production/' in name or name.endswith(('.tim', '.iso', '.glb', '.ckpt')) for name in names):
            raise ValueError('Nonruntime authoring/source payload entered CharHD JAR')
    print(f'CharHD package verified: one current JAR, {len(manifest["parts"])} selected custom parts, exact materials/hashes/notices, no experiment or authoring payloads.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    verify(parser.parse_args().package)
