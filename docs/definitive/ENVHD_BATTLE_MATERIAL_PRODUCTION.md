# EnvHD battle surface production

This increment versions custom candidates for battle floors, walls and scenery.
It changes no selected runtime artwork, game adapter, installer or release output.
CharHD, UIHD and FxHD production remain separate. Existing Skurfa resources and
the two battle-material pilot atlases are unchanged.

## Source coverage

The fresh private extraction contains 89 nonempty battle model/texture pairs in
128 slots; 39 model slots are empty. The original CContainer/TMD packets reference
900 page/CLUT bindings, including part zero and one 8-bit interpretation of a
4-bit-uploaded image. Blend-mode bits do not create a different material identity.
This replaces the older palette-only census, which did not fully handle mixed BPP.

- 525 eligible static bindings deduplicate to 518 cropped RGBA source identities.
- 24 bindings on stages 0/6 retain their existing pilot artwork.
- 350 bindings remain native because their image, source padding or palette
  dependencies intersect texture animation or its scratch writes.
- One 8-bit binding on stage 67 remains native: the stage TIM does not initialize
  its complete 256-word palette. The pipeline does not infer the missing VRAM state.

Each source crop includes eight source texels around the conservative UV bounds,
clamped inside its uploaded image. The entire crop is decoded using that face's
actual page/BPP and CLUT coordinates after image-first, then palette uploads.
No palettes are flattened together. Crop position and source UV origin accompany
every binding, including shared masters and source-identical stage aliases.

Animation rectangles follow `Battle.applyBattleStageTextureAnimations`: ten
sentinel-terminated container sequences and width divided by four. Exclusions
also include referenced 16/256-word palettes and the temporary VRAM area at
960,256. Palette-animation containers remain native. Source geometry, model part
animation and counterattack CLUT darkening remain authoritative.

## Candidate history and review

`scripts/batch-envhd-battle-materials.py` uses the pinned Real-ESRGAN 4x engine and
weights with clamped inference context. Python repair restores exact nearest-4x
STP, discarded-zero and visible-black classes. Newly inferred black on a nonblack
source uses the original RGB, preserving visible dark pixels. Original decodes,
inference inputs, logs and comparison boards remain outside Git.

The actual inference source is commit `2d7986ce2`, with pipeline SHA-256
`7862ee67731b77e646c22f298ebcb3325206a0a38ddf2eb8bea21882ce03c153`.
Subsequent review tightened child-path redirection, bounded resume controls, and
palette/scratch animation dependencies. The fresh census proves those changes
alter no current candidate identity, crop, binding or output. The importer verifies
the historic plan with only its original animation reason text; it does not relabel
the actual inference run as a newer pipeline.

All 518 originals/candidates were visually inspected across 22 private comparison
boards, with the engine's random-encounter location mapping where available. Source
roles remain tied to the stage's geometry, including environmental statuary,
architecture, carvings, wood, rock, ice and foliage. No character model, interactive
UI or effect script is replaced by this batch.

506 candidates retain a reviewed development-baseline verdict. Twelve candidates
need bespoke revision for carved markings, fine crests or geometric motifs whose
detail/topology changed under smoothing. These flags are explicit in
`integrations/envhd/production/battle-material-artwork.json`; no canonical lettering
has been invented. All native and final quality acceptances remain pending. Neural
smoothing is not proof of final Skurfa-equivalent bespoke detail.

Custom PNGs and output-bound review/provenance manifests live under
`battle-material-candidates/<decoded-RGBA-hash>`. The candidate ledger selects zero
runtime materials. Publication recomputes the complete fresh source census,
verifies bounded exact RGBA bytes and visibility, requires complete explicit
intent/style/layout reviews, preserves immutable versions, and rolls back failed
writes. It publishes generated artwork and metadata only.

## Verification and next integration

22 synthetic battle batch/publication regressions pass without retail source data
or a game window. Both independent source/publication reviews accepted the final
safeguards. All 518 actual generated PNGs match the fresh source-bound metadata and
exact STP/discard/black coverage. The CI visual fixture loop includes this suite.
The selected runtime resource tree remains byte-identical to terrain checkpoint
`9031847dc`; this candidate batch does not rebuild or publish an installation.

Next, implement a separate source-bound battle atlas and optional material mapping
that returns native UVs for held bindings. Preserve existing pilot resources,
geometry/refinement hooks, surface responses, stage transforms, stage reload
generation checks, mesh/texture ownership and counterattack darkening fallback.
Pack only eligible reviewed bindings; exclude the twelve revision-needed masters.
Validate mixed HD/native faces, teardown, allocation fallback, source tampering and
all original stage decodes before changing runtime selection. Animated surfaces
need a separate frame/scroll-preserving treatment, not a frozen first-frame atlas.

Native scene seams, lighting, blending, scene transitions and Steam Deck acceptance
remain pending. Unowned field scenes/environmental props and bespoke material
improvement remain broader EnvHD backlog; this batch does not complete EnvHD.
