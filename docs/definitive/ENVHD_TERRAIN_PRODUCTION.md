# EnvHD static world terrain production

The candidate batch covers eight world-map regions: South Serdio, North Serdio,
Tiberoa, Illisa Bay, Mille Seseau, Gloriano, Death Frontier and Endiness. It belongs
to EnvHD only. Skurfa's field scenes and other mods' production resources are unchanged.

## Source coverage and review

World models `SECT/DRGN0.BIN/5705` through `5712` reference ordered TIM banks
`5697` through `5704`. Native loading uploads each TIM's image before its palette,
in numeric directory order. The pipeline reproduces the final VRAM contents, rather
than assuming each TIM's embedded palette remains authoritative. It handles both
4-bit and 8-bit materials and uses the indexed TMD shader's full 0–255 color scale.
The general world UI/door/smoke bank `5695` is excluded.

The source census found 366 distinct static page/CLUT bindings. Exactly 365 fit one
uploaded source rectangle. Their decoded RGBA pixels deduplicate to 293 masters,
so shared materials are restored once. Source model, ordered bank, image-file,
palette/page, UV-origin and decoded-pixel identities remain in the public ledger.

All 293 candidates were generated with pinned local Real-ESRGAN x4plus weights and
engine, with 16 original pixels of clamped inference context. Opposite edges are
never averaged or mirrored. A Python repair restores original nearest-scaled STP,
discard and visible-black classes and prevents newly black pixels over a nonblack
source for either STP value. Zero alpha is the PSX STP encoding, not ordinary PNG
transparency: nonblack zero-alpha pixels remain visible to the indexed-texture shader.

Source-versus-candidate boards cover every master. The public ledger records 292
visually reviewed development baselines. Their neural smoothing/detail is useful
baseline work; this does not establish final bespoke quality. Native gameplay and
Steam Deck acceptance remain pending. Original PNGs, review boards, inference files,
logs and model weights stay outside Git; only custom candidate PNGs and source/review
metadata are versioned. The separate terrain development branch now selects 292
reviewed masters through eight runtime atlases; this is outside the frozen scope
of the imminent combined installer release.

## Deliberate native retention

- Model part zero remains the animated ocean. Its scrolling source texture and
  cycling palette must stay on the native rendering path during static integration.
  The static material image rectangles do not intersect the animated water region.
- Gloriano part 2, page 21 / CLUT 31157 uses a uniform `(222,222,246,0)` source.
  It has no detail to restore and should retain native artwork. Its generated
  candidate is retained as unselected history rather than treated as an improvement.
- Tiberoa part 10, page 21 / CLUT 31285 spans UV 248 through 7 across a page boundary.
  No single source rectangle covers its actual unsigned coordinates. The binding
  remains explicitly held until that source/render mapping is validated.

## Runtime integration

`scripts/pack-envhd-terrain.py` reproduces deterministic shelves of whole decoded
TIM rectangles with eight source texels (32 output pixels) of clamped padding.
Atlas width is 4096, heights range from 1728 to 2560, and the eight custom PNGs total
about 47 MiB. The 364 HD bindings exclude the held wrapped material and uniform
exemption. `terrain-runtime-artwork.json` records the selected source/bank/atlas
identities. Its native and final-quality acceptance remain explicitly pending.

The runtime independently reconstructs final VRAM, texture references, decoded
pixel identities and packed placement from the fresh original sources. It rejects
unknown/incomplete mappings, changed STP/discard/visible-black, non-clamped padding,
nonempty packing gaps and unbounded/duplicate metadata. UV mapping keys both texture
page and CLUT, retains fractional positions and returns a native indexed fallback
for excluded materials within an otherwise replaced part.

Optional textures and meshes are constructed on the render thread after both fresh
model and bank loads complete. A shared generation gate prevents old callbacks from
uploading or replacing the new native scene. It snapshots raw model bytes before
TMD parsing mutates packet headers, clears both load flags at reload and invalidates
pending loads on teardown. Existing transforms, floating terrain, lighting, depth,
water animation and earlier artwork replacements retain their original paths.
Optional resources release on reload/normal/abrupt teardown; allocation/source
failure retains the native scene. The padded color atlas uses the existing HD
filtering option with a gutter-limited mip budget and nearest source STP/discard.

Texture remapping invokes the existing ModelsHD geometry hook, retains per-face
surface flags and authored/source material responses, and does not write the
optional mesh into the original table's geometry cache. Native fallback therefore
remains usable after optional deletion. Synthetic recording-renderer checks exercise
refinement, mixed HD/indexed faces, failed staged allocation and cache separation.
The real eight-source mesh probe built 67 optional parts and 51,651 HD / 36 retained
indexed vertices. The current bundled ModelsHD index selected no refinements for
these raw continent parts (0 of 142 requests including later native rebuilds); the
hook is preserved and exercised, but no continent geometry improvement is claimed.

Seventeen synthetic pipeline/import regressions pass. Independent review validated
all 293 corrected candidates and their private source snapshots against a fresh
final-VRAM census. Publication checks full source/provenance records, bounded exact
8-bit RGBA PNG bytes and output-bound reviews before any write. Existing candidate
versions are immutable; a failed import rolls back touched files. These checks do
not substitute for native scene/blending or Steam Deck acceptance. Ten additional
packing/selection regressions and ten Java source/mapping/callback/mesh regressions
pass. The independent Java source audit agrees with Python on all eight scenes,
366 static bindings and 293 unique decodes and validates all 364 HD atlas bindings.

Keep this batch separate from the frozen ready panorama/world release scope until
the delivery coordinator selects a later candidate. Installer downloads are
published only by the consolidated delivery workflow, not per art iteration.
