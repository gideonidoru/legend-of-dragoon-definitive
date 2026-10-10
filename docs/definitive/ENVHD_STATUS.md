# EnvHD integration status

This thread owns EnvHD only. CharHD, UIHD and FxHD production is handled separately.
Custom artwork and code are public and included by the existing default bundle
build. Keep installer releases consolidated through the delivery thread; do not
publish a new download for each asset/code iteration.

EnvHD is not complete. See `ENVHD_FULL_COVERAGE.md` and the source-bound
`integrations/envhd/production/full-coverage.json` for all categories, selected
versus candidate counts, unresolved denominators and exact remaining work.

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
pass. On the combined main baseline `3472edf1e23b27fd06da1b81580e05985bf57d1e`,
the Java delivery suite has 278 passes, seven skips, no failures. Run the audio
regressions with their silent driver (`ALSOFT_DRIVERS=null`); an earlier run without
that driver failed the unrelated audio-clock regression.
Full original-source checks and packaged-byte validation accompany each final batch.
No game window or visible desktop app was launched.

The ready asset source is `766f926a52655fdde5c4dab0cdeac86dad816146`.
Integration preserves the combined build's ModelsHD 0.4 geometry hooks, renderer,
UI scaling and delivery code. The popup conflict keeps `queueUiOrthoModel` together
with the optional EnvHD thumbnail texture. No other mod's production folders change.
The staged EnvHD JAR contains 225 byte-matching runtime files: 109 panorama/world
PNGs plus the two pre-existing material-pilot atlas PNGs. Its SHA-256 is
`557d9da1eb1408d0e9cac6520ef4c52c978f074e50696387a89e6367177a49b7`.
All 406 Skurfa runtime files match the pinned upstream source. This is integration
evidence; the delivery thread publishes the single consolidated installation.

The terrain batch remains independent on `codex/envhd-terrain` (draft PR #10),
outside the frozen imminent combined release scope. Its reviewed
pipeline source is `90a2dc037`. It generated 293 unique candidates for
365 of 366 static material bindings across eight regions. Their custom PNGs and
source-bound reviews are now versioned in `integrations/envhd/production/terrain-candidates`
and `terrain-artwork.json`; original decodes and diagnostics remain private. One uniform-color
candidate should retain native artwork, and one wrapped Tiberoa material remains
held. Eight bounded, padded runtime atlases now select the 292 reviewed masters
for 364 static bindings. Their selection ledger is `terrain-runtime-artwork.json`.
Seventeen candidate/import and ten packing/selection regressions pass. Independent
Java decoding agrees with all 293 source identities and validates all eight atlases.
Ten Java regressions check source/mapping/callback and optional mesh behavior; the
real continent mesh probe builds all 67 optional parts with the existing geometry
hook and source-owned native exceptions. See `ENVHD_TERRAIN_PRODUCTION.md` for limits:
native visual acceptance and final Skurfa-equivalent quality remain pending.

The next battle-material batch now versions 518 candidates for 525 static bindings:
506 reviewed development baselines and twelve bespoke revision flags. No new battle
runtime selection changes. The fresh 900-binding census protects 24 existing pilot
bindings and holds 350 animated/animation-adjacent bindings plus one incomplete
8-bit palette. See `ENVHD_BATTLE_MATERIAL_PRODUCTION.md` for source/review evidence
and the pending atlas integration.

Next production categories remain battle-material runtime integration and animated
surfaces, unowned field scenes and environmental props. The field census has 612
unowned configurations before visual deduplication/empty-layer filtering; shared
sources and Skurfa aliases must stay deduplicated. Full EnvHD completion is not
claimed by these baseline batches. Improve bespoke materials and inspect
native scene behavior before claiming final visual acceptance.

The field census is now decoded and independently checked by native Java CPU
decoders: 650 configurations, 5,528 unique visible images, 124 empty slots and
seven native missing-texture slots. The public `field-source-worklist.json` tracks
every image/scene identity. Of 5,165 unowned visible images, three uniform sources
remain native and 5,162 require restoration. Runtime selections are still zero.
The complete private batch preserves each foreground independently; it does not
flatten movable, hideable or alternate layers into the background.

Field production source `75f924ac4` produced 724 complete private candidates before
a checkpoint. The directory batch reuses those validated outputs with their exact
origin-plan/source/output hashes, then restores the remaining complete worklist
with backgrounds first. Eight real samples are pixel-identical between per-image
and persistent-directory inference; the eight-sample directory run took 1.83
seconds. Eighteen synthetic fixtures now cover complete alias/render guards,
prior-output validation and fixed chunk identity across interruption. All outputs
remain review candidates; none are new runtime selections.
