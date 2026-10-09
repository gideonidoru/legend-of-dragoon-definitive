#!/usr/bin/env python3
"""Reapply original PSX STP/discard semantics to a private neural RGB candidate.
Outputs an engine-encoded PNG and a conventional preview; no runtime integration claim.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
import platform
import sys
import PIL
from PIL import Image

MAX_FILE_BYTES = 32 * 1024 * 1024


def read_png(path, max_dimension):
    if path.stat().st_size > MAX_FILE_BYTES:
        raise ValueError('Image exceeds the private candidate file limit')
    with path.open('rb') as stream:
        data = stream.read(MAX_FILE_BYTES + 1)
    if len(data) > MAX_FILE_BYTES:
        raise ValueError('Image exceeds the private candidate file limit')
    with Image.open(io.BytesIO(data)) as image:
        if image.format != 'PNG' or not (1 <= image.width <= max_dimension and 1 <= image.height <= max_dimension):
            raise ValueError('Requires a bounded PNG image')
        return image.convert('RGBA'), hashlib.sha256(data).hexdigest()


def preserve(original, candidate):
    if not (1 <= original.width <= 512 and 1 <= original.height <= 512):
        raise ValueError('Original exceeds the pilot dimension limit')
    if candidate.width % original.width or candidate.height % original.height:
        raise ValueError('Candidate must use an exact integer scale')
    scale = candidate.width // original.width
    if scale not in (2, 4) or candidate.height // original.height != scale:
        raise ValueError('Supported scale is 2x or 4x in both dimensions')
    original = original.convert('RGBA')
    candidate = candidate.convert('RGBA')
    if any(alpha not in (0, 255) for alpha in original.getchannel('A').get_flattened_data()):
        raise ValueError('Original alpha must encode the binary PSX STP bit')
    original = original.resize(candidate.size, Image.Resampling.NEAREST)
    engine = Image.new('RGBA', candidate.size)
    preview = Image.new('RGBA', candidate.size)
    for y in range(candidate.height):
        for x in range(candidate.width):
            r,g,b,stp = original.getpixel((x,y)); nr,ng,nb,_ = candidate.getpixel((x,y))
            if r == g == b == 0: nr = ng = nb = 0  # original discarded black or visible STP black
            elif nr == ng == nb == 0 and stp == 0: nr,ng,nb = r,g,b  # do not create a new discarded pixel
            engine.putpixel((x,y),(nr,ng,nb,stp))
            preview.putpixel((x,y),(nr,ng,nb,255 if stp or r or g or b else 0))
    return engine,preview

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original_stp', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(Path(__file__).resolve().parents[1]):
        parser.error('Use a private output folder outside the source checkout')
    original, source_hash = read_png(args.original_stp, 512)
    candidate, candidate_hash = read_png(args.candidate, 2048)
    engine, preview = preserve(original, candidate)
    args.output.mkdir(exist_ok=False)
    engine.save(args.output / 'candidate-engine-stp.png')
    preview.save(args.output / 'candidate-preview.png')
    report = {
        'pipeline': 'definitive-stp-preservation-1',
        'environment': platform.platform(),
        'pillow': PIL.__version__,
        'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'command': [sys.executable, *sys.argv],
        'originalStpSha256': source_hash,
        'neuralCandidateSha256': candidate_hash,
        'originalSize': list(original.size),
        'candidateSize': list(candidate.size),
        'engineSha256': hashlib.sha256((args.output / 'candidate-engine-stp.png').read_bytes()).hexdigest(),
        'previewSha256': hashlib.sha256((args.output / 'candidate-preview.png').read_bytes()).hexdigest(),
        'scope': 'Original STP/discard coverage restored at nearest scale. Inferred RGB detail, UV seams, blending, animation and runtime acceptance remain unverified.',
    }
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Private transparency-preserving candidate:', args.output)

if __name__=='__main__':main()
