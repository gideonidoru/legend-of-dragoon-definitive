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
metadata are versioned. Runtime terrain selections remain zero.

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

## Runtime integration requirements

Pack the reviewed masters into bounded, padded continent atlases. Bind each atlas
to the exact model and complete ordered texture-bank hashes. The UV mapper must
key on both texture page and CLUT, preserve fractional authored UVs, and permit a
native fallback for the uniform and wrapped bindings. A page or palette identity
alone is insufficient for these mixed 4-bit/8-bit banks.

Construct optional textures and meshes only on the render thread after both fresh
model and bank loads complete. Clear old snapshots at continent reload and guard
against delayed callbacks. Preserve existing world transforms, floating terrain,
lighting, depth, ocean behavior and all prior-mod replacements. Release optional
resources on reload, normal teardown and abrupt transitions; allocation/source
failure retains the native scene.

Texture remapping must retain ModelsHD's geometry refinement and surface responses.
Do not rebuild a separate retail-only mesh that bypasses its hook. An optional
atlas mesh must also avoid redirecting a model's ordinary cached mesh to one that
requires a different texture: original fallback must remain valid after deletion.

Seventeen synthetic pipeline/import regressions pass. Independent review validated
all 293 corrected candidates and their private source snapshots against a fresh
final-VRAM census. Publication checks full source/provenance records, bounded exact
8-bit RGBA PNG bytes and output-bound reviews before any write. Existing candidate
versions are immutable; a failed import rolls back touched files. These checks do
not substitute for atlas sampling, native scene, blending and lifecycle acceptance.

Keep this batch separate from the ready panorama/world integration while runtime
work is unfinished. Installer downloads are published only by the consolidated
delivery workflow, not per art iteration.
