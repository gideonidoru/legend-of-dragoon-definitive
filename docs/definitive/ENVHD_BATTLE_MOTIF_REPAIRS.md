# EnvHD painted battle motif repairs

The Flanvel Tower maze-like stone marking has one separately reviewed painted
repair candidate. Its original neural output remains archived as a held candidate.
The immutable installer candidate at `b1775c6f` does not include this new painting.

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
existing reviewed neural surfaces. Omitting it preserves the previous selection.

Ten new repair/provenance checks and the existing 22 battle-material checks pass.
A fresh-source dry selection reproduces the previous 506-master/513-binding
selection byte for byte without the option. With the painting it produces 507
masters and 514 bindings, changing only the stage-44 atlas/manifest and runtime
ledger; every other atlas and existing source mapping is unchanged. That proof
captures proposed writes without applying them to the installed runtime tree.

Next integration must archive the former runtime ledger, refresh coverage hashes
and counts, rebuild EnvHD and its four download packs, verify source/package
mapping and run the coordinated final-source installer gates. This authoring
candidate is not in the current public installation; it does not establish
native seam, camera, blend, gameplay or Steam Deck visual acceptance.
