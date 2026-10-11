# EnvHD painted battle motif repairs

Six battle materials have separately reviewed painted replacements. Three are
Flanvel Tower materials:
the orthogonal maze-like stone marking, central circular lattice and looped
architectural stone relief. Their
original neural outputs remain archived as held candidates.
Main `0af4f997` and its in-flight installer release do not include these paintings.
They are selected only on the future authoring branch.

The material pass uses built-in image generation for blank pale mint stone only.
The authorized Python repair restores the exact original palette-class marking,
including its disconnected pixels, line junctions and lower grey band. A narrow
bevel remains inside the source silhouette. No glyph meaning or canonical text
has been inferred. All 379 marking pixels and 336 band pixels retain exact 4x
coverage; the final RGB contrast independently recovers the original mask.

The source is stage 44 part 0, page 7, CLUT 15389, crop `[72,200,120,248]`.
Original UVs, model/texture identities, STP, discard and visible-black classes
remain authoritative. Coloured STP-zero texels are visible in this material;
ordinary field-alpha interpretation would discard the whole stone texture.

`battle-bespoke-motifs.json` records source bindings, generated/input/output
hashes, algorithm and dependency pins, material recipe and individual intent,
style and layout review. Public custom inputs, outputs and prompts are in
`bespoke-battle-candidates`; original sources and comparison boards stay private.
Skurfa resources are unchanged. Native and final-quality acceptance remain zero.

`verify-envhd-painted-battle.py` re-decodes the complete fresh battle census and
checks usage, ownership, historical hold, paths, bounded PNG header/decoder state,
review, hashes and deterministic repaired pixels. Forging a new output hash does
not bypass exact pixel reproduction. `pack-envhd-battle-materials.py` accepts an
explicit `--painted-manifest` to include these verified replacements alongside
existing reviewed neural surfaces. The option can be repeated for different
reviewed recipes. Omitting it preserves the previous selection. Duplicate
receipts or source replacements reject before any runtime publication.

Twenty-nine repair/provenance checks and the existing 22 battle-material checks pass.
A fresh-source dry selection reproduces the previous 506-master/513-binding
selection byte for byte without the option. With the painting it produces 507
masters and 514 bindings; with both it produces 508 masters and 515 bindings,
and with all three it produces 509 masters and 516 bindings (540 with the 24
existing pilot bindings). Each changes only the stage-44 atlas/manifest and runtime
ledger; every other atlas and existing source mapping is unchanged. The combined
six-replacement selection described below is now applied to the authoring branch.

## Circular lattice

The second source is stage 44 part 0, page 7, CLUT 15580, crop
`[136,0,200,56]`, sampled UVs `[144,0,191,47]`. The 64x56 crop becomes 256x224.
It contains dark teal stonework, diagonal supports, a pale mint circular face and
partial neighboring light faces. The initial painted layout reinterpreted the
marking, so a separate edit removes all generated lattice detail from the face.
The final repair uses that blank painted face and restores both original gray
palette classes in the central source circle `[31.5,23.5,13]`: 59 darker-gray and
128 gray-green cells. The surrounding graded annular stone shading is treated
as material lighting rather than extra invented glyphs. The original marking's
junctions, gaps, disconnected cells and two tones are retained exactly at 4x;
final RGB contrast independently recovers all 187 source marking cells.

The second recipe leaves the first repair's algorithm, controls and PNG bytes
unchanged. `repair-envhd-painted-battle-circle.py` has its own immutable pin and
records the reviewed circle, palette classes, smooth inner-face lighting and
internal bevel. `battle-bespoke-lattice.json` records its independent source,
usage, generated stages, final review and deterministic repair. A public custom
`render-preview-v1.png` provides conventional alpha for review; `image-v1.png`
retains the native STP channel for game use. Private comparison boards include
actual source, the held neural result, generated blank face and final repair.

## Looped stone architecture

The third source is stage 44 part 0, page 7, CLUT 15452, crop `[24,0,88,72]`;
sampled UVs `[32,0,79,63]` cover the central 48x64 region. The 64x72 crop includes
adjoining side padding and a bottom eight-row strip; it becomes 256x288.
The painted pass retains the broad curved ribbons, rounded openings and olive
recesses while adding restrained aged-stone texture. The source/held-neural/
painted comparison checks major relief shapes, opening positions, light, palette
and padding placement. Fine grain and pits are inferred material detail.

`repair-envhd-painted-battle-material.py` preserves exact native STP/discard/
visible-black classes and fits the reviewed full-canvas painting. Its receipt
explicitly says internal feature alignment requires individual source comparison;
it does not claim the palette-topology proof used for the two lattice repairs.
`battle-bespoke-architecture.json` binds the fresh source usage, painted input,
repair pixels and individual review. It remains a development candidate with
native gameplay and final quality acceptance pending.

## Stage-30 decorative panels and stage-38 flame surface

The stage-30 red fabric crop is 56x63, page7/CLUT15772, crop
`[200,40,256,103]`; its output is224x252. A separate edit removes the generated
crest from the painted fabric. `repair-envhd-painted-battle-rectangle.py` restores
215 accent cells from five native palette classes inside `[14,15,48,50]`.
The stage-30 lavender panel is48x48, page7/CLUT15773, crop
`[200,168,248,216]`; its output is192x192. A blank-panel edit removes invented
markings before the repair restores222 cells from three native pale-lavender
classes inside `[8,8,37,40]`. All three star positions and the connecting strokes
retain their original cell geometry. `battle-bespoke-royal-panels.json` records
both individual reviews and the exact rectangle/palette recipe.

The rectangle recipe locks only the nominated palette classes, including their
gaps and disconnected endpoints; it does not assert that every shadow or framing
detail is restored mechanically. High-frequency painted grain and a narrow inner
bevel provide material detail without extending the palette-class silhouette.
The receipt records class counts and mask identity. Unlike the earlier mint stone
recipe, it makes no independent final-RGB mask-classification claim. Source
comparisons check the painted framing and adjoining crop fragments separately.

The stage-38 static crop is40x80, page7/CLUT15644, part5, crop
`[0,56,40,136]`; its output is160x320. It is a gold/cream branching flame-shaped
surface on charcoal stone, not an animated fire effect. Its individually reviewed
matte painting replaces the held neural glow while retaining the source's main
branch pattern, thin lower stem and adjoining fragments. The existing generic
material recipe preserves native semi-transparent stone, STP-zero coloured gold,
discard and visible-black classes. Fine grain and fissures are inferred detail;
exact internal topology is not a mechanical claim. `battle-bespoke-flame.json`
records this independent review. The stage's separate animation families remain
native.

A fresh-source dry pass reproduces the previous selection byte for byte when no
painted manifests are requested. The five manifests together select512 masters,
519 new bindings and543 bindings including the24 pilot bindings. Only stage30,
38 and44 atlases/manifests and the battle runtime ledger change. Existing source
bindings are identical; every other atlas and the four field packs are unchanged.
The authoring branch now applies those writes, archives the former runtime ledger
and refreshes coverage counts and hashes. Six static battle sources still need
replacements. Native gameplay and final-quality acceptance remain zero.

Next coordinated integration must verify the rebuilt EnvHD JAR/source mapping
and run the final-source installer gates. This authoring
selection is not in the current public installation; it does not establish
native seam, camera, blend, gameplay or Steam Deck visual acceptance.
