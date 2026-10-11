# EnvHD remaining coverage: discovery and acceptance

This handoff preserves reviewed selection `32f5db9f`: **4,917 field images**
(4,886 neural and 31 painted), **245 held fields plus one Tiberoa terrain hold**.
The current public source `0af4f997` installer has 4,892 fields. Neither held
custom history nor a successful source decode means final art or gameplay acceptance.
The unresolved animation and prop/overlay work is additional to those 246 static
repairs. EnvHD campaign completion and its complete image denominator remain unknown.

## Fresh discovery completed

- [Battle native worklist](../../integrations/envhd/production/battle-native-discovery-worklist.json):
  fresh census exactly matches the existing 89 nonempty stage source records and
  all 351 held bindings. All model/TIM identities were reread; no source hold was
  cleared. The 350 animation/context bindings span 67 stages and intersect 145 stage
  animation-slot references. **153 bindings intersect animated sampled image
  rectangles; 197 overlap only through padded image context.** These groups are
  bindings, not restored images or animation families. The 197 are a discovery
  priority, not approved static replacements. Palette and scratch regions were
  included in the intersection check; none adds an intersection in this snapshot.
- The remaining stage 67 binding is page 135/CLUT 15900, sourced from
  `SECT/DRGN0.BIN/2564/0/0` and `SECT/DRGN0.BIN/2564/2`. Its referenced palette is
 256 words at VRAM `[448,248]`. The stage-only image-then-CLUT upload snapshot
  initializes 64 words and leaves 192 unknown. This does not establish inherited
  gameplay palette content. No missing colors were guessed.
- [Prop/overlay discovery status](../../integrations/envhd/production/field-prop-overlay-discovery-status.json):
  fresh route hints cover 6,993 field-object model records and 511 overlay records.
  The physical field texture pool has 6,968 files/505 unique raw hashes; every
  physical texture has at least one route hint. The routed overlay TIM pool has
 467 files/400 raw hashes. The detailed consumer/hash index remains private.
  **No EnvHD material ownership or complete image denominator has been established.**
- The older field-object snapshot in `full-coverage.json` is 6,767 files/481 hashes.
  The fresh pool is 201 files/24 hashes larger; reconcile source identities and
  census scope before updating that historical coverage snapshot. There are 25
  hinted texture references and 608 hinted script references without a physical
  file at the derived path. Aliases, sentinel routes and source reuse are not yet
  reconciled; these are not 608 or 25 confirmed missing runtime assets.
- World animation still has eight known animated continent parts and zero selected
  HD animation families. The historical general world-bank 375 files/350 raw hashes
  is a mixed pool, not an EnvHD denominator. No complete frame/palette cycle census
  has been performed. Field object/overlay animation also remains unenumerated.

## Priority 1: close battle frame and sampling-context families

Source entry points:
[batch census](../../scripts/batch-envhd-battle-materials.py), functions
`references`, `padded_bounds`, `scene` and `census`;
[native stage animation](../../src/main/java/legend/game/combat/Battle.java),
`applyBattleStageTextureAnimations` and `renderBattleStage`.
Stage source identifiers are `SECT/DRGN0.BIN/{2497+stage}/0/0` and sibling `2`.
The native copier uses the scratch region at `960,256`; the worklist preserves
individual page/CLUT/part/face/UV bindings and animation-sequence hashes.

Next deliverable: a source-bound family ledger for every 350 held binding, including
ordered image/CLUT uploads, sequence instructions, scratch reads/writes, exact
sampling and padding dependencies, frame/palette hashes, loop/terminal conditions,
timing and consumer material identities. Start with the 197 padding-only bindings
because their visible source content may be reusable across many states. Prove
that reuse against every reachable neighbor/palette state; do not shrink padding,
ignore scratch, change filtering or drop dependencies to clear a hold.

Completion check: all 350 bindings reconcile to complete families and actual
source states, with no unexplained upload/read region. The census checks animation
dependencies before full palette decoding; audit all 350 bindings for additional
uninitialized palette/image regions rather than treating the first blocker as
the only blocker. Repeated sequence hashes
alone do not establish identical image families. Generation starts only after
this ledger closes. The runtime adapter must preserve scrolling, wrapping,
translucency, palette changes, timing, counterattack darkening and native fallback.
Headless native sequence replay and differential UV/palette/visibility checks are
required before gameplay seam, camera and lifetime acceptance. Native retention
is an outstanding hold, not completed HD animation.

## Priority 2: establish stage 67 palette upload provenance

Trace the stage loader and preceding/overlapping uploads in
[Battle](../../src/main/java/legend/game/combat/Battle.java) and the source-ordered
TIM upload decoder in [terrain batch](../../scripts/batch-envhd-terrain.py).
Keep page 135/CLUT 15900 native until all 256 words have attributable upload/state
provenance for the consuming 83 faces. A convenient neighboring palette, prior
battle snapshot or inferred colors is not evidence.

Completion check: source-located ordered upload evidence explains all 192 currently
unknown words, including state variants. CPU decode/UV checks then reproduce the
actual native source. If source manifests cannot establish inherited state,
actual gameplay upload observation is required; request permission before launching
or manipulating the game. A fixture replay alone is not gameplay palette proof.

## Priority 3: enumerate world water and animated scenery

Source entry points: [WMap](../../src/main/java/legend/game/wmap/WMap.java),
`prepareWaterAnimation`, `animateWater`, `loadWmapTextures` and terrain rendering;
[terrain census](../../scripts/batch-envhd-terrain.py), `static_materials` and
`census`. Static terrain excludes animated part 0. Continent model sources are
`SECT/DRGN0.BIN/5705` through `5712`, with ordered texture banks `5697` through `5704`.
Water currently rotates a 16-word by 64-row native rectangle at `448,0`; decode its
actual BPP/material consumers before converting those units into image dimensions.

Next deliverable: complete part 0 and general-bank consumer/owner map, ordered
uploads, source/palette variants and native row-copy/scroll cycles. Replay each
native state until the source cycle repeats; record state timing and decoded image
identity rather than multiplying eight parts by a guessed frame count. Separate
world UI, vehicles, actors and effect resources from EnvHD water/scenery.

Completion check: every animated consumer has an attributable complete cycle and
owner. Custom frame families preserve cycle timing, texture continuity, seams,
wrap, lighting and alpha. Static continent coverage does not clear this workstream.

## Priority 4: resolve field prop and overlay texture ownership

Source entry points: [field route builder](../../scripts/modelshd-field-map.py),
`build_source_map`; [model catalog](../../scripts/catalog-modelshd-sources.py);
[RetailSubmap](../../src/main/java/legend/game/submap/RetailSubmap.java),
`loadAssets`, `prepareSobjs` and the overlay loading/`prepareMap` path;
[Models](../../src/main/java/legend/game/Models.java), `animateModel`,
`animateModelClut` and `animateModelTextures`;
[SMap](../../src/main/java/legend/game/submap/SMap.java), texture-animation
script controls. `SUBMAP/NEWROOT.RDT` routes early/late field banks; field objects
use the associated model/script directories and `textures/{objectIndex}`. Overlay
models use the cut's `smapFileIndices_800f982c` directory, with texture/matrix data
in the next directory. Do not treat the ModelsHD scene/prop/effect bucket or a
route hint labeled `prop` as confirmed texture ownership.

Next deliverable: a reconciled per-consumer material ledger covering all physical
pages, aliases and unused/sentinel records. Resolve physical-file gaps first,
validate recognized model containers and actual script consumers, then attach
ordered TIM/CLUT uploads, textured faces, UV/crop bounds and animation/state data.
Assign EnvHD only to demonstrated environment consumers such as chests, doors,
furnishings, architecture and vegetation. Actors/NPCs belong to CharHD; particle
and effect consumers require FxHD classification; UI consumers belong to UIHD.
ModelsHD owns geometry integration, which does not determine texture ownership.
Shared pages require per-consumer material identities and coordinated ownership;
keep conflicts native until they are resolved. Coordinate those boundaries through
the project orchestrator instead of regenerating other threads' work.

Completion check: every raw page has an actual consumer/alias or explained
exclusion; every environment-owned material has source/palette/crop/state identity
and an agreed owner. Only then deduplicate decoded material/frame images and publish
an EnvHD job denominator. **505 field hashes plus 400 overlay hashes is not 905 jobs.**
Start restoration with unambiguous static environment props and shared repeated
materials; group complete animations next. Source-bound mixed native/HD adapters
must preserve actor/effect exclusions, interaction state and ModelsHD mesh UVs.

## Static authoring and release gates continue separately

The 245 field holds remain in the historical neural intent reviews; separate
painted selection has cleared 31 of 276 original holds. Repair the remaining
fields in source-consistent material/motif families. Ambiguous inscriptions,
sculpture identity and the held Deningrad bed need concrete source intent; alpha
and shape checks cannot substitute for it. Two moss/door attempts remain held
in [custom draft history](../../integrations/envhd/production/field-bespoke-stone-ground-held-drafts.json).
The [Tiberoa mapping investigation](ENVHD_TIBEROA_MAPPING.md) remains one terrain
hold; do not silently unwrap its unsigned UV faces or change geometry/core sampling.

All work preserves Skurfa and its aliases, other mod ownership, original source
pixels and private diagnostics. Selected artifacts require independent intent/style/
layout review, exact source replay, relevant failure checks, normal four-pack/one-ID
package verification and coordinated platform gates. Original pixels stay private;
custom code/art/prompts and curated source metadata are versioned. Native gameplay,
physical Steam Deck and final Skurfa-equivalent quality acceptance remain outstanding.
The orchestrator integrates coherent reviewed checkpoints and publishes coordinated
installer updates; a small artwork or discovery batch does not create a release.
No complete EnvHD percentage will be reported until all these denominators and
acceptance checks are resolved.
