#!/usr/bin/env python3
"""Verify the nine static CharHD authoring candidates. AGPL v3; see LICENSE.

Requires Pillow, NumPy and trimesh in the isolated authoring environment.
This checks actual asset integrity and preserves the captured runtime selection;
it does not certify design fidelity, animation or Steam Deck performance.
"""
import hashlib
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image
import trimesh


ROOT = Path(__file__).resolve().parents[1]
AUTHORING = ROOT / 'integrations/charhd/production/party-reconstruction'
CHARACTERS = {'dart', 'rose', 'lavitz', 'shana', 'haschel', 'albert', 'meru', 'kongol', 'miranda'}


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def artifact(directory, name):
    path = (directory / name).resolve()
    if not path.is_relative_to(directory.resolve()) or not path.is_file():
        raise ValueError(f'Invalid authoring artifact: {name}')
    return path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_character(entry):
    character = entry['character']
    directory = AUTHORING / entry['directory']
    require(directory.resolve().is_relative_to(AUTHORING.resolve()), 'Authoring directory escapes its root')
    require(directory == AUTHORING / character / 'original-design-v3', 'Unexpected character directory')
    for name, expected in entry['sha256'].items():
        require(digest(artifact(directory, name)) == expected, f'{character}: hash mismatch for {name}')

    artwork = json.loads(artifact(directory, 'artwork-receipt.json').read_text())
    reconstruction = json.loads(artifact(directory, 'reconstruction-receipt.json').read_text())
    require(artwork['character'] == reconstruction['character'] == character, 'Character receipt mismatch')
    require(artwork['image'] == entry['artwork'], 'Selected artwork mismatch')
    require(digest(artifact(directory, entry['artwork'])) == artwork['imageSha256'] == reconstruction['inputSha256'], 'Reconstruction input mismatch')
    require(digest(artifact(directory, entry['artwork'].replace('.png', '.prompt.txt'))) == artwork['promptSha256'], 'Prompt receipt mismatch')
    require(reconstruction['assetKind'] == 'body' and reconstruction['networkDisabled'], 'Unexpected reconstruction scope')
    require(not reconstruction['nativeImport'] and not reconstruction['nativeRig'], 'Static candidate marked as native')
    for receipt in [entry, artwork, reconstruction]:
        require(not receipt['runtimeSelected'] and not receipt['ownerCandidateAccepted'], 'Draft candidate incorrectly promoted')

    with Image.open(artifact(directory, entry['artwork'])) as image:
        require(image.mode == 'RGBA' and list(image.size) == artwork['imageSize'], 'Artwork mode/size mismatch')
        require(image.getchannel('A').getextrema() == (0, 255), 'Artwork lacks usable transparency')

    mesh_path = artifact(directory, entry['mesh'])
    data = mesh_path.read_bytes()
    magic, version, length = struct.unpack_from('<4sII', data)
    require(magic == b'glTF' and version == 2 and length == len(data), 'Invalid GLB header')
    chunk_length, chunk_type = struct.unpack_from('<I4s', data, 12)
    require(chunk_type == b'JSON', 'GLB lacks a JSON chunk')
    gltf = json.loads(data[20:20 + chunk_length])
    require(not gltf.get('skins'), 'Unreviewed rig added to static candidate')
    require(all('uri' not in buffer for buffer in gltf['buffers']), 'GLB requires an external buffer')
    require(all('uri' not in image for image in gltf['images']), 'GLB requires an external image')
    scene = trimesh.load(mesh_path, force='scene', process=False)
    require(len(scene.geometry) == 1, 'Unexpected static mesh count')
    mesh = next(iter(scene.geometry.values()))
    require(np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all(), 'Non-finite geometry')
    require(mesh.visual.kind == 'texture', 'Missing baked material')
    require(np.isfinite(mesh.visual.uv).all() and mesh.visual.uv.min() >= 0 and mesh.visual.uv.max() <= 1, 'Invalid UV coordinates')
    require(mesh.faces.min() >= 0 and mesh.faces.max() < len(mesh.vertices), 'Invalid face indices')
    require(len(mesh.faces) == entry['assetIntegrity']['triangles'] == reconstruction['variants'][0]['triangles'] <= 24000, 'Triangle count mismatch')
    require(digest(mesh_path) == reconstruction['variants'][0]['meshSha256'], 'Baked mesh receipt mismatch')
    texture_path = artifact(directory, entry['texture'])
    require(digest(texture_path) == reconstruction['variants'][0]['textureSha256'], 'Baked texture receipt mismatch')
    with Image.open(texture_path) as texture:
        require(texture.mode == 'RGB' and texture.size == (2048, 2048), 'Unexpected atlas format')
        require(np.array_equal(np.asarray(texture), np.asarray(mesh.visual.material.baseColorTexture.convert('RGB'))), 'Embedded/sidecar atlas mismatch')
    return len(mesh.faces)


def main():
    inventory = json.loads((AUTHORING / 'original-design-v3.json').read_text())
    entries = inventory['characters']
    require(inventory['format'] == 1 and len(entries) == 9 and {entry['character'] for entry in entries} == CHARACTERS, 'Incomplete party inventory')
    triangles = sum(verify_character(entry) for entry in entries)
    runtime = ROOT / 'integrations/charhd/runtime-assets'
    actual = {str(path.relative_to(runtime)): digest(path) for path in runtime.rglob('*') if path.is_file()}
    require(actual == inventory['runtimeBaseline'], 'Selected runtime resources changed; review the baseline before publishing')
    print(json.dumps({'charactersVerified': len(entries), 'triangles': triangles, 'runtimeFilesUnchanged': len(actual), 'scope': 'Static integrity only; native animation and Deck validation pending'}))


if __name__ == '__main__':
    main()
