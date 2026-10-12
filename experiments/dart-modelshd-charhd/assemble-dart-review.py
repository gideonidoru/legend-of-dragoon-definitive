#!/usr/bin/env python3
"""Lay out actual custom-model inspection captures, not generated comparisons. AGPL v3."""
from pathlib import Path
import argparse
import shutil
from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--captures', type=Path, required=True)
parser.add_argument('--before-neck', type=Path, required=True)
parser.add_argument('--after-neck', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=False)
font_file = '/System/Library/Fonts/Supplemental/Arial.ttf'
title = ImageFont.truetype(font_file, 34)
small = ImageFont.truetype(font_file, 24)


def comparison(images, labels, caption, filename):
    panel = 900
    sheet = Image.new('RGB', (panel * 2, panel + 145), (21, 24, 29))
    draw = ImageDraw.Draw(sheet)
    for index, (path, label) in enumerate(zip(images, labels)):
        image = Image.open(path).convert('RGB').resize((panel, panel), Image.Resampling.LANCZOS)
        sheet.paste(image, (index * panel, 75))
        draw.text((index * panel + 32, 24), label, font=title, fill=(235, 230, 221))
    draw.text((32, panel + 104), caption, font=small, fill=(172, 168, 161))
    sheet.save(args.output / filename)


comparison([args.captures/'before-quarter.png', args.captures/'ready-quarter.png'],
           ['Earlier static sculpt', 'Complete Dart review candidate'],
           'Actual geometry in the same studio. Different authored poses; not native game playback.',
           'body-before-after.png')
comparison([args.before_neck/'0.png', args.after_neck/'0.png'],
           ['Before: flat shoulder cut', 'After: shoulder and neck fit'],
           'Actual clay geometry. Neck-base alignment and facing corrected; accepted runtime unchanged.',
           'neck-before-after.png')
comparison([args.before_neck/'1.57.png', args.after_neck/'1.57.png'],
           ['Before: profile', 'After: profile'],
           'Actual clay geometry, same camera. The shoulder contours are retained around the neck.',
           'neck-profile-before-after.png')
for name in ['ready-quarter.png', 'neutral-front.png', 'neutral-rear.png', 'neutral-side.png',
             'guard-quarter.png', 'relaxed-quarter.png', 'stride-quarter.png', 'desktop-viewer.png',
             'touch-viewer.png', 'clay-viewer.png', 'browser-qa.json']:
    shutil.copyfile(args.captures/name, args.output/name)
print('Assembled actual-geometry review captures', args.output)
