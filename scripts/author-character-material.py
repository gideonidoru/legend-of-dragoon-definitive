#!/usr/bin/env python3
"""Pack an explicitly authored palette tile, preserving original STP/discard. AGPLv3."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'tim', 'base', 'image', 'prompt', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--palette', required=True, type=int)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]):
        parser.error('Stage source-derived diagnostic previews outside the repository')
    spec = importlib.util.spec_from_file_location('packing', Path(__file__).with_name('pack-model-materials.py'))
    packing = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(packing)
    model, tim = packing.materials.bounded_read(args.model), packing.materials.bounded_read(args.tim)
    atlas, mapping, base = packing.validate_pack(args.base, model, tim)
    if args.palette not in mapping:
        parser.error('Authored palette is absent from the verified source')
    entry = mapping[args.palette]
    x, y, width, height = entry['atlasRect']
    generated, digest = packing.transparency.read_png(args.image, 4096)
    # Image generation may return another raster size. Normalize exactly once to the
    # original rectangle; never change native UVs or any other palette rectangle.
    normalized = generated.convert('RGB').resize((width, height), Image.Resampling.LANCZOS)
    source = packing.clamped_crop(packing.source_palette(tim, args.palette), entry['sourceCrop'])
    tile, _ = packing.transparency.preserve(source, normalized)
    atlas.paste(tile, (x, y))
    args.output.mkdir(parents=True, exist_ok=False)
    atlas.save(args.output / 'atlas-engine-stp.png')
    packing.rgba_preview(atlas).save(args.output / 'atlas-preview.png')
    report = {key: base[key] for key in ('pipeline', 'modelSha256', 'timSha256', 'scale', 'paddingSourceTexels', 'atlasSize', 'materials')}
    report.update(algorithm='authored', preservedPalettes=[p for p in base.get('preservedPalettes', []) if p != args.palette],
        atlasEngineSha256=packing.neural.sha(args.output / 'atlas-engine-stp.png'),
        authoring={'baseAtlasSha256': base['atlasEngineSha256'], 'generatedImageSha256': digest,
            'promptSha256': hashlib.sha256(args.prompt.read_bytes()).hexdigest(), 'palette': args.palette,
            'generatedSize': list(generated.size), 'normalizedSize': [width, height],
            'normalization': 'one Lanczos resize; source STP, discard and visible black restored'},
        reviewStatus='pending-posed-art-review')
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    packing.validate_pack(args.output, model, tim)
    print('Validated authored palette:', args.palette, 'in', args.output)


if __name__ == '__main__':
    main()
