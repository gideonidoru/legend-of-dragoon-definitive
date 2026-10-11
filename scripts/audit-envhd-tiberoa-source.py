#!/usr/bin/env python3
"""Private source audit for the held Tiberoa stair material; never selects it.

The proposed mapping lifts native corners into a unique uploaded rectangle before
interpolation. It is an authoring hypothesis, not a change to the TMD decoder.
"""
import argparse
import importlib.util
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('terrain', ROOT / 'scripts/batch-envhd-terrain.py')
terrain = importlib.util.module_from_spec(spec)
spec.loader.exec_module(terrain)
MODEL_SHA = '3abcfbf7683c7805690fd0ba44f7e15b49a39862a2465b1c59acc184a4905cc3'
BANK_SHA = '8c2d844dc9f6ce295514a087ed9d0e996ef456c4172223bca58211a44b1178bc'
DECODER_SHA = '2e182287e873db6aa46cbede3bf18339c894608dc7a8610abf78fe8c8ee64751'


def crossing_source(tims, page, uv):
    """Require one crossing upload, both sides of the seam and all lifted corners."""
    bpp = page >> 7 & 3
    if bpp not in (0, 1) or not uv:
        raise ValueError('Unsupported or empty material')
    if any(type(u) is not int or type(v) is not int or not (0 <= u <= 255 and 0 <= v <= 255) for u, v in uv):
        raise ValueError('Expected native unsigned byte corners')
    divisor = 4 if bpp == 0 else 2
    px, py = (page & 15) * 64, (page & 16) * 16
    matches = []
    for tim in tims:
        rect = tim['image']
        origin = (rect['x'] - px) * divisor
        end = origin + rect['w'] * divisor
        if tim['bpp'] != bpp or not 0 <= origin < 256 < end <= 512:
            continue
        if not any(u < origin for u, _ in uv) or not any(u >= origin for u, _ in uv):
            continue
        lifted = [(u + 256 if u < origin else u, v) for u, v in uv]
        if all(origin <= u < end and rect['y'] <= py + v < rect['y'] + rect['h']
               for u, v in lifted):
            matches.append((tim, lifted))
    if len(matches) != 1:
        raise ValueError('Expected exactly one crossing uploaded rectangle')
    return matches[0]


def final_owners(tims):
    owners = np.full((512, 1024), -1, dtype=np.int32)
    for tim in tims:
        for rect in (tim['image'], tim['palette']):
            owners[rect['y']:rect['y'] + rect['h'], rect['x']:rect['x'] + rect['w']] = tim['file']
    return owners


def decode_crossing(vram, written, tims, page, clut, uv):
    tim, lifted = crossing_source(tims, page, uv)
    rect = tim['image']
    owners = final_owners(tims)
    image_owners = owners[rect['y']:rect['y'] + rect['h'], rect['x']:rect['x'] + rect['w']]
    if not np.all(image_owners == tim['file']):
        raise ValueError('Crossing source overwritten by a different upload')
    image = terrain.decode_material(vram, written, tim, page, clut)
    colours = 16 if tim['bpp'] == 0 else 256
    cx, cy = (clut & 63) * 16, clut >> 6
    palette_owners = owners[cy, cx:cx + colours]
    ids, counts = np.unique(palette_owners, return_counts=True)
    return image, tim, lifted, {str(int(i)): int(n) for i, n in zip(ids, counts)}


def face_records(model, page, clut):
    # Validate the entire static packet stream before extracting matching faces.
    terrain.static_materials(model)
    count = struct.unpack_from('<I', model, 8)[0]
    result = []
    for part in range(1, count):
        offset, faces = struct.unpack_from('<II', model, 12 + 28 * part + 16)
        position = 12 + offset
        for face in range(faces):
            header = struct.unpack_from('<I', model, position)[0]
            mode, payload = header >> 24, (header >> 8 & 255) * 4
            if mode & 4:
                n = 4 if mode & 8 else 3
                face_clut = struct.unpack_from('<H', model, position + 6)[0]
                face_page = struct.unpack_from('<H', model, position + 10)[0] & 0x19f
                if (face_page, face_clut) == (page, clut):
                    result.append(dict(part=part, face=face, packetOffset=position,
                                       packetSha256=terrain.digest(model[position:position + 4 + payload]),
                                       nativeCorners=[[model[position + 4 + j * 4], model[position + 5 + j * 4]] for j in range(n)]))
            position += 4 + payload
    return result


def audit(files):
    if terrain.digest((ROOT / 'scripts/batch-envhd-terrain.py').read_bytes()) != DECODER_SHA:
        raise ValueError('Pinned decoder changed')
    base = files / 'SECT/DRGN0.BIN'
    paths = sorted((p for p in (base / '5699').iterdir() if p.name.isdigit()), key=lambda p: int(p.name))
    if [int(p.name) for p in paths] != list(range(49)):
        raise ValueError('Expected complete 49-file Tiberoa bank')
    sources = [(int(p.name), terrain.bounded_read(p)) for p in paths]
    model = terrain.bounded_read(base / '5707')
    bank_sha = terrain.digest(b''.join(struct.pack('<II', i, len(data)) + data for i, data in sources))
    if terrain.digest(model) != MODEL_SHA or bank_sha != BANK_SHA:
        raise ValueError('Pinned source model or ordered bank changed')
    faces = face_records(model, 21, 31285)
    if [(f['part'], f['face']) for f in faces] != [(10, 73), (10, 89), (10, 109)]:
        raise ValueError('Held face set changed')
    uv = [tuple(corner) for f in faces for corner in f['nativeCorners']]
    vram, written, tims = terrain.upload_bank(sources)
    image, tim, lifted, palette_owners = decode_crossing(vram, written, tims, 21, 31285, uv)
    if tim['file'] != 14 or palette_owners != {'14': 16} or image.size != (16, 32):
        raise ValueError('Crossing image or palette identity changed')
    # Separate scalar indexed sampling checks every pixel, including U256..263.
    rgba = np.asarray(image)
    for y in range(32):
        for x in range(16):
            u = 248 + x
            index = (int(vram[256 + y, 320 + u // 4]) >> ((u & 3) * 4)) & 15
            colour = int(vram[488, 848 + index])
            expected = [(colour & 31) * 255 // 31, ((colour >> 5) & 31) * 255 // 31,
                        ((colour >> 10) & 31) * 255 // 31, (colour >> 15) * 255]
            if expected != rgba[y, x].tolist():
                raise ValueError('Scalar VRAM sampling disagrees')
    cursor = 0
    for face in faces:
        n = len(face['nativeCorners'])
        face['proposedCorners'] = [list(c) for c in lifted[cursor:cursor + n]]
        cursor += n
    receipt = dict(schema=1, kind='pending-cross-page-source-contract', bank=2, region='Tiberoa',
                   page=21, clut=31285, modelSha256=MODEL_SHA, bankSha256=BANK_SHA,
                   bankFiles=[dict(file=i, size=len(data), sha256=terrain.digest(data)) for i, data in sources],
                   imageFile=14, imageSourceSha256=tim['sourceSha256'],
                   sourceSize=list(image.size), sourceUvOrigin=[248, 0],
                   decodedRgbaSha256=terrain.fingerprint(image), faces=faces,
                   uploadedImageRectangle={k: tim['image'][k] for k in ('x', 'y', 'w', 'h')},
                   imageWordsFinalOwner=14, imageWordsVerified=128, paletteFinalOwners=palette_owners,
                   scalarPixelsVerified=512, stpValues=np.unique(rgba[:, :, 3]).tolist(),
                   visibleBlackPixels=int(np.all(rgba[:, :, :3] == 0, axis=2).sum()),
                   proposal='Lift native U=7 to263 on these three source faces before interpolation; no modulo256',
                   modelsHdRequirement='Resolve original corners before refinement/barycentric UV interpolation; mapping already-interpolated unsigned UVs is unsafe',
                   surfaceClassification='Three stair entrances beside grassy-roofed town buildings in static source-model CPU comparisons',
                   nativeMappingAccepted=False, runtimeSelected=False, finalQualityAccepted=False,
                   decoderSha256=DECODER_SHA, auditScriptSha256=terrain.digest(Path(__file__).read_bytes()),
                   limitations=['Source-bound authoring hypothesis; original hardware intent and native gameplay comparison unverified',
                                'No engine, geometry, shader, atlas schema or installed artwork change'])
    return image, receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    destination = args.output.resolve()
    if destination.is_relative_to(ROOT) or destination.is_relative_to(args.files.resolve()):
        raise ValueError('Original image output must stay outside Git and extraction')
    image, receipt = audit(args.files)
    destination.mkdir(parents=True, exist_ok=False)
    image.save(destination / 'source-stp.png')
    image.convert('RGB').save(destination / 'source-input.png')
    image.convert('RGB').resize((256, 512), Image.Resampling.NEAREST).save(destination / 'source-preview.png')
    (destination / 'source-contract.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'sourceSize': receipt['sourceSize'], 'decodedRgbaSha256': receipt['decodedRgbaSha256'],
                      'faces': len(receipt['faces']), 'scalarPixelsVerified': 512, 'runtimeSelected': False}))


if __name__ == '__main__':
    main()
