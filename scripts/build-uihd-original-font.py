#!/usr/bin/env python3
"""Author source-bound vector glyph candidates without inventing new letterforms.

The vectors preserve Original's pixel-centre coverage and logical metrics. Small
quadratic corner joins soften the source outline at HD resolution; independent
reference/art review is required before shipping this candidate.
"""

import argparse
import hashlib
import json
import io
import shutil
from pathlib import Path

from PIL import Image



def contours(pixels):
    edges = set()
    for x, y in pixels:
        for neighbour, start, end in (
            ((x, y - 1), (x, y), (x + 1, y)),
            ((x + 1, y), (x + 1, y), (x + 1, y + 1)),
            ((x, y + 1), (x + 1, y + 1), (x, y + 1)),
            ((x - 1, y), (x, y + 1), (x, y)),
        ):
            if neighbour not in pixels:
                edges.add((start, end))
    loops = []
    direction = {(1, 0): 0, (0, 1): 1, (-1, 0): 2, (0, -1): 3}
    while edges:
        first = min(edges)
        start, end = first
        points = [start]
        edges.remove(first)
        previous = start
        while end != start:
            points.append(end)
            candidates = [edge for edge in edges if edge[0] == end]
            if not candidates:
                raise ValueError("Unclosed source glyph contour")
            incoming = direction[(end[0] - previous[0], end[1] - previous[1])]
            def turn(edge):
                outgoing = direction[(edge[1][0] - end[0], edge[1][1] - end[1])]
                # Keep corner-touching source components distinct.
                return ({1: 0, 0: 1, 3: 2, 2: 3}[(outgoing - incoming) % 4], edge)
            edge = min(candidates, key=turn)
            edges.remove(edge)
            previous, end = edge
        corners = []
        for i, current in enumerate(points):
            before, after = points[i - 1], points[(i + 1) % len(points)]
            if (current[0] - before[0]) * (after[1] - current[1]) != (current[1] - before[1]) * (after[0] - current[0]):
                corners.append(current)
        if len(corners) < 4:
            raise ValueError("Degenerate source glyph contour")
        loops.append(corners)
    return loops


def vector_path(loops, radius):
    commands = []
    def towards(a, b):
        distance = abs(a[0] - b[0]) + abs(a[1] - b[1])
        fraction = min(radius, distance / 2) / distance
        return (a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction)
    def xy(point):
        return f"{point[0]:g},{point[1]:g}"
    for points in loops:
        for i, corner in enumerate(points):
            incoming = towards(corner, points[i - 1])
            outgoing = towards(corner, points[(i + 1) % len(points)])
            commands.append(("M" if i == 0 else "L") + xy(incoming))
            commands.append("Q" + xy(corner) + " " + xy(outgoing))
        commands.append("Z")
    return " ".join(commands)


SOURCE_IMAGE_SHA256 = "dcf84d68a67f0f76aaaf4233d0c2d1ec3c588e8f98a2ca3fb4d873b0fee246d8"
SOURCE_METADATA_SHA256 = "b4faacbef8f23e5fff5374484c1356e9f92134b1abaf928c491e8587e91b1c12"


def require_real_output(output):
    # Inspect lexical prefixes before normalizing, including links before '..'.
    path = output if output.is_absolute() else Path.cwd() / output
    prefix = Path(path.anchor)
    for part in path.parts[1:]:
        prefix /= part
        if prefix.is_symlink():
            raise ValueError("Candidate output has a linked path component")
    if output.exists():
        raise ValueError("Candidate output must be fresh; do not replace reviewed art")


def build(root, output):
    source_image = root / "gfx/fonts/default.png"
    source_metadata = root / "gfx/fonts/default.json"
    require_real_output(output)
    image_bytes, metadata_bytes = source_image.read_bytes(), source_metadata.read_bytes()
    image_hash = hashlib.sha256(image_bytes).hexdigest()
    metadata_hash = hashlib.sha256(metadata_bytes).hexdigest()
    if image_hash != SOURCE_IMAGE_SHA256 or metadata_hash != SOURCE_METADATA_SHA256:
        raise ValueError("Original font source does not match the reviewed pins")
    source = json.loads(metadata_bytes)
    with Image.open(io.BytesIO(image_bytes)) as decoded:
        image = decoded.convert("RGBA")
    if image.size != (256, 256) or {value for count, value in image.getcolors(3) or []} != {(0, 0, 0, 0), (255, 255, 255, 255)}:
        raise ValueError("Original font artwork differs from the reviewed binary-mask source")
    glyphs = source["glyphs"]
    if len(glyphs) != 139:
        raise ValueError("Original font glyph denominator changed")
    pending = {}
    scale, gutter, extent, radius = 8, 2, 2048, 0.18
    x, y, row_height = 0, 0, 0
    packed, entries, drawing = {}, [], []
    for key in sorted(glyphs, key=lambda k: ord(k) if len(k) == 1 else int(k[2:], 16)):
        glyph = glyphs[key]
        codepoint = ord(key) if len(key) == 1 else int(key[2:], 16)
        w, h, u, v = (glyph[k] for k in ("texW", "texH", "texU", "texV"))
        if min(w, h) < 0 or min(u, v) < 0 or u + w > image.width or v + h > image.height:
            raise ValueError("Original atlas rectangle escapes its source")
        pixels = {(px, py) for py in range(h) for px in range(w) if image.getpixel((u + px, v + py))[3]}
        outlines = contours(pixels)
        path = vector_path(outlines, radius)
        body = f'<path fill="white" fill-rule="evenodd" d="{path}"/>'
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{max(1, w) * scale}" height="{max(1, h) * scale}" viewBox="0 0 {max(1, w)} {max(1, h)}">{body}</svg>\n'
        vector_file = f"glyphs/u{codepoint:04x}.svg"
        pending[vector_file] = svg.encode("utf-8")
        cell_width, cell_height = (w + gutter * 2) * scale, (h + gutter * 2) * scale
        if cell_width > extent or cell_height > extent:
            raise ValueError("Candidate glyph exceeds its bounded atlas")
        if x + cell_width > extent:
            x, y, row_height = 0, y + row_height, 0
        if y + cell_height > extent:
            raise ValueError("Candidate font exceeds its bounded atlas")
        tex_u, tex_v = x + gutter * scale, y + gutter * scale
        packed[key] = dict(glyph, texU=tex_u, texV=tex_v, texW=w * scale, texH=h * scale)
        drawing.append(f'<g transform="translate({tex_u} {tex_v}) scale({scale})">{body}</g>')
        entries.append({"codepoint": f"U+{codepoint:04X}", "key": key, "sourceRectangle": [u, v, w, h], "whitePixelCentres": len(pixels), "contours": outlines, "vectorSha256": hashlib.sha256(pending[vector_file]).hexdigest(), "logicalMetrics": {k: glyph[k] for k in ("x", "y", "w", "h", "colour")}})
        x += cell_width
        row_height = max(row_height, cell_height)
    atlas = "original-hd.svg"
    pending[atlas] = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{extent}" height="{extent}" viewBox="0 0 {extent} {extent}">' + "".join(drawing) + "</svg>\n").encode("utf-8")
    metadata = "original-hd.json"
    pending[metadata] = (json.dumps({"name": "uihd.font.original_hd.name", "glyphs": packed}, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
    for key, glyph in glyphs.items():
        if any(packed[key][k] != glyph[k] for k in ("x", "y", "w", "h", "colour")):
            raise ValueError("Candidate changes a logical Original glyph metric")
    ledger = {"status": "vector-candidate-reference-art-review-pending", "glyphCount": 139, "sourceImageSha256": image_hash, "sourceMetadataSha256": metadata_hash, "vectorAtlasSha256": hashlib.sha256(pending[atlas]).hexdigest(), "candidateMetadataSha256": hashlib.sha256(pending[metadata]).hexdigest(), "scale": scale, "atlasSize": [extent, extent], "transparentGutterPixels": gutter * scale, "cornerJoinRadiusSourceUnits": radius, "logicalMetricsChanged": 0, "runtimeSelected": False, "deviceAccepted": False, "glyphs": entries}
    pending["source-comparison.json"] = (json.dumps(ledger, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
    # Validate everything in memory before taking ownership of a fresh directory.
    require_real_output(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    require_real_output(output)
    output.mkdir()
    owned = output.stat(follow_symlinks=False)
    try:
        (output / "glyphs").mkdir()
        for name, data in pending.items():
            with (output / name).open("xb") as target:
                target.write(data)
    except BaseException as failure:
        try:
            current = output.stat(follow_symlinks=False)
            if (current.st_dev, current.st_ino) == (owned.st_dev, owned.st_ino):
                shutil.rmtree(output)
        except OSError as cleanup:
            failure.add_note(f"Could not remove the owned incomplete candidate: {cleanup}")
        raise
    print(f"Authored {len(entries)} vector glyph candidates; metrics unchanged; no runtime selection")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    build(arguments.root, arguments.output)
