#!/usr/bin/env python3
"""Private offline neural A/B. Requires Pillow 12.3.0 and publisher-supplied pinned NCNN files.
No game assets, models or generated images are copied into the source repository.
"""
from pathlib import Path
import argparse
import hashlib
import json
import platform
import subprocess
import time
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat
import PIL

MODELS = {
    'realesr-animevideov3': (2, 'realesr-animevideov3-x2', '548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d', 'b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04'),
    'realesrgan-x4plus': (4, 'realesrgan-x4plus', '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf', '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86'),
    'realesrgan-x4plus-anime': (4, 'realesrgan-x4plus-anime', 'fe01c269cfd10cdef8e018ab66ebe750cf79c7af4d1f9c16c737e1295229bacc', '2b8fb6e0ae4d2d85704ca08c119a2f5ea40add4f2ecd512eb7f4cd44b6127ed4'),
}
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', type=Path, required=True)
    parser.add_argument('--models', type=Path, required=True)
    parser.add_argument('--input', type=Path, action='append', required=True, help='Private TexturePilot output folder')
    parser.add_argument('--output', type=Path, required=True, help='New folder outside the repository')
    parser.add_argument('--background', type=Path, help='Optional HD background for the private style comparison')
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    if args.output.resolve().is_relative_to(project): parser.error('Use a private comparison folder outside the source checkout.')
    if PIL.__version__ != '12.3.0': parser.error('Use Pillow 12.3.0 for recorded comparisons.')
    for _, stem, binary, params in MODELS.values():
        if sha(args.models / (stem + '.bin')) != binary or sha(args.models / (stem + '.param')) != params: parser.error('Model identity differs from the pinned publisher files.')
    args.output.mkdir(parents=False, exist_ok=False)
    report = {'pipeline': 'definitive-neural-comparison-1', 'environment': platform.platform(), 'pillow': PIL.__version__, 'engineSha256': sha(args.engine), 'modelSource': 'https://github.com/xinntao/Real-ESRGAN/releases/tag/v0.2.5.0', 'inputs': [], 'scope': 'Private palette previews only. No model mapping, alpha/STP acceptance, gameplay quality or Deck performance is established.'}
    rows = []
    for index, folder in enumerate(args.input):
        source = folder / 'original-preview.png'
        with Image.open(source) as image: original = image.convert('RGBA')
        if original.width > 512 or original.height > 512: parser.error('Preview exceeds the pilot image limit.')
        sample = {'inputSha256': sha(source), 'timManifestSha256': sha(folder / 'manifest.properties'), 'width': original.width, 'height': original.height, 'candidates': []}
        nearest = original.resize((original.width * 2, original.height * 2), Image.Resampling.NEAREST)
        comparisons = [nearest, Image.open(folder / 'scale2x-preview.png').convert('RGBA')]
        for name, (scale, stem, binary, params) in MODELS.items():
            output = args.output / f'{index}-{name}.png'
            command = [str(args.engine.resolve()), '-i', str(source.resolve()), '-o', str(output.resolve()), '-m', str(args.models.resolve()), '-n', name, '-s', str(scale), '-t', '256', '-j', '1:1:1']
            started = time.monotonic()
            result = subprocess.run(command, capture_output=True, text=True, timeout=180, check=False)
            (args.output / f'{index}-{name}.log').write_text(result.stdout + result.stderr)
            if result.returncode: raise RuntimeError(f'{name} failed; inspect the retained private log.')
            with Image.open(output) as image: candidate = image.convert('RGBA')
            if candidate.size != (original.width * scale, original.height * scale): raise RuntimeError('Neural output dimensions do not match.')
            handheld = candidate.resize(nearest.size, Image.Resampling.LANCZOS) if scale != 2 else candidate
            delta = ImageStat.Stat(ImageChops.difference(handheld.convert('RGB'), nearest.convert('RGB'))).mean
            alpha_histogram = ImageChops.difference(handheld.getchannel('A'), nearest.getchannel('A')).histogram()
            sample['candidates'].append({'model': name, 'weightsSha256': binary, 'paramsSha256': params, 'seconds': round(time.monotonic() - started, 3), 'outputSha256': sha(output), 'outputSize': list(candidate.size), 'rgbaBytes': candidate.width * candidate.height * 4, 'meanRGBDifferenceAt2x': [round(v, 3) for v in delta], 'differentAlphaPixelsAt2x': sum(alpha_histogram[1:]), 'command': command, 'comparisonResample': 'Lanczos to 2x' if scale == 4 else 'native 2x'})
            if name != 'realesr-animevideov3': comparisons.append(handheld)
        rows.append((folder.name, comparisons)); report['inputs'].append(sample)
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    # Consistent scale and restrained labels; inspect source-size and enlarged crops separately.
    canvas = Image.new('RGB', (1520, 190 + 470 * len(rows)), '#f6f4ef'); draw = ImageDraw.Draw(canvas)
    try: font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 22)
    except OSError: font = ImageFont.load_default(size=22)
    draw.text((40, 28), 'Definitive | Private material comparison', font=font, fill='#202b28')
    draw.text((40, 65), 'Same atlas and palette. Neural detail is inferred; runtime palette and seam validation remains open.', font=font, fill='#68716b')
    if args.background:
        with Image.open(args.background) as image: background = image.convert('RGB'); background.thumbnail((200, 90)); canvas.paste(background, (1275, 18))
    labels = ['Original / nearest 2x', 'Scale2x control', 'Neural general 4x / 2x view', 'Neural illustration 4x / 2x view']
    for row, (name, images) in enumerate(rows):
        y = 145 + row * 470; draw.text((40, y), name, font=font, fill='#244e40')
        for column, (label, image) in enumerate(zip(labels, images)):
            x = 40 + column * 365; draw.text((x, y + 40), label, font=font, fill='#202b28')
            image.thumbnail((345, 345), Image.Resampling.NEAREST)
            base = Image.new('RGBA', image.size, '#202b28'); base.alpha_composite(image); canvas.paste(base.convert('RGB'), (x, y + 82))
    canvas.save(args.output / 'comparison.png')
    (args.output / 'README.md').write_text('# Private neural comparison\n\nSee comparison.png and manifest.json. No candidate is activated in the game. RGB difference is a change measure, not a quality score. AI can alter painted markings and blur tiny details. Alpha differences fail the current STP contract. Atlas palette selection is not a material mapping. Outputs stay private; model/runtime licenses remain with the publisher.\n')
    print('Private comparison:', args.output.resolve())

if __name__ == '__main__': main()
