# EnvHD integration status

This thread owns EnvHD only. CharHD, UIHD and FxHD production is handled separately.
Custom artwork and code are public and included by the existing default bundle
build. Keep installer releases consolidated through the delivery thread; do not
publish a new download for each asset/code iteration.

## Selected development artwork

- 70 distinct battle panoramas cover 73 source/header variants. One uniform-black
  source is deliberately exempt. The public panorama history has 123 revisions.
- 38 source-bound location landscapes and one parchment world-map backdrop now
  have reviewed 4x neural baselines. Their public source/provenance/review ledger
  is `integrations/envhd/production/world-artwork.json`.
- The pre-existing battle-material pilot covers 24 material regions on stages 0/6.
- Native gameplay/Steam Deck visual acceptance remains pending. Neural smoothing
  does not establish final Skurfa-equivalent bespoke detail.

The world backdrop retains original placement, three rendering paths, brightness
and additive blending. Location images retain the 120x90 popup display, source
palette/visibility and fades. Optional replacements honor prior mods; invalid
artwork falls back to original. Text, controls, characters and effects are not
part of these replacements. Both normal and abrupt world teardown release artwork.
Retail placeholders (Forest of Winglies' inherited `dummy` image and map-thumbnail
aliases) remain truthful source baselines, rather than invented canonical vistas.

## Skurfa protection

Existing Skurfa artwork, code, notices and pinned resource bytes remain unchanged.
The ownership ledger protects 38 packs and 87 source-identical cut/period mappings.
Reuse those resources; produce only unowned gaps. No field background listener or
new field artwork has been added by the panorama/world extension yet.

## Verification and remaining work

Independent Python/Java decoders agree on all 74 battle and 39 world sources.
Seventeen batch, eight sky-import, eleven world and five ownership regression tests
pass. The Java delivery suite has 122 passes, four desktop-only skips, no failures.
Full original-source checks and packaged-byte validation accompany each final batch.
No game window or visible desktop app was launched.

Next production categories remain world terrain/scenery, remaining battle material
regions, unowned field scenes and environmental props. The field census has 612
unowned configurations before visual deduplication/empty-layer filtering; shared
sources and Skurfa aliases must stay deduplicated. Full EnvHD completion is not
claimed by these first two baseline batches. Improve bespoke materials and inspect
native scene behavior before claiming final visual acceptance.
