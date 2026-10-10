#!/usr/bin/env python3
"""Compile custom face/hair paints into their bounded head atlas, AGPL v3."""
from pathlib import Path
import hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parent/'resources/charhd-experiment'
def main():
    face=Image.open(ROOT/'dart-face-paint-v1.png').convert('RGB')
    hair=Image.open(ROOT/'dart-hair-paint-v3.png').convert('RGB').resize((768,768),Image.Resampling.LANCZOS)
    assert face.size==(1254,1254)
    atlas=Image.new('RGB',(1254,2022));atlas.paste(face,(0,0));atlas.paste(hair,(0,1254))
    atlas.paste(hair.resize((486,768)),(768,1254))
    path=ROOT/'dart-head-detail-v2.png';atlas.save(path)
    path.with_suffix('.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'\n')
    print(path)
if __name__=='__main__':main()
