# EnvHD current coverage

[The active ledger](../../integrations/envhd/production/active-coverage-ledger.json) records current selected development assets, held work, custom drafts, unknown denominators and next tasks in one place. Its hashes bind the underlying source and selection ledgers. Different units must not be added into a whole-campaign image count.

Current static work remaining is **204 images: 203 field images and one terrain repair**. Battle materials additionally have **351 held bindings**, including 350 animation/context bindings and one incomplete palette. Their complete image-job denominator is unknown. Eight animated continent parts and environment props/overlays also need source/state/ownership contracts before their image denominators can be established.

Field selection is 4,959 development images: 4,886 neural and 73 painted. These are source-reviewed selections, not native gameplay or final Skurfa-equivalent quality acceptance. All protected Skurfa work is reused.

Current authoring queue: all83 historical canonical source keys reviewed:73selected,10requiring revision and none awaiting review. All earlier recipes/custom versions remain preserved. The51 unique unselected custom source keys are a subset of203field holds. Skurfa artwork is reused; native gameplay and final quality remain pending.

The [recorded generation4 download snapshot](../../integrations/envhd/production/publication-source-5d3db5c8-generation4-snapshot.json) at source `5d3db5c8` contains 4,925 field selections/39paintings and34 battle paintings. The historical Donau increment selected4,928 fields/42paintings and retained47 battle paintings: three field paintings and13 battle paintings beyond that recorded snapshot. These are comparisons with the preserved generation4 source ledgers, not a fresh observation of the live carrier. ROOT tracks subsequent coordinated publication and installer verification. Earlier source0af/d731 snapshots remain preserved as history. The [Tiberoa stair investigation](ENVHD_TIBEROA_MAPPING.md) has one pending terrain painting and mapping proposal, a subset of the existing terrain hold.

## Held natural-rock drafts

[Three generated foreground drafts](../../integrations/envhd/production/field-bespoke-natural-rock-held-drafts.json) cover two Undersea Cavern formations and one Volcano Villude rock. Their raw paintings exaggerate rock relief and grain. The original pinned cutout recipe preserves canvas, source alpha and visible black, but those checks cannot establish source intent or quality.

The separate experimental recipe `scripts/repair-envhd-painted-macro-detail.py` first applies the unchanged cutout gates. It discards broad painted colors and relief, combines source-derived interpolation with bounded painted detail, and anchors each native pixel's mean RGB within half an integer level. Saturated channels, hidden pixels, alpha and visible black remain constrained. Its receipts record the new method and dependency hashes; it does not impersonate the original cutout recipe.

The refined drafts preserve broad source forms but remain too soft and pixelated for HD selection. All three jobs remain held, with their generated inputs, repaired variants and prompts retained as approved custom derivative-art history. The source-mean variants preserve recoverable native RGBA values: averaging and rounding each 4×4 cell reconstructs the original low-resolution image, verified against all three source hashes. Original input/reference/context files and comparison boards remain separate and are not added. Neither this recipe nor these drafts are inputs to the runtime mod or installer.

Nine behavioral checks cover broad recoloring, invented relief, native visibility and saturated channels, original aspect/coverage gates, opaque-only RGB input, input identity/mutation, existing output preservation and determinism. Passing these checks does not clear the art holds. The next attempt must improve visible detail while preserving the actual source forms; independent review and native validation remain necessary.

## Textile candidates

[The complete 32-painting author-reviewed candidate set](../../integrations/envhd/production/field-bespoke-textiles-reviewed-selection.json) preserves the existing 31-painting selection and adds Donau cut 249's two patterned furnishing surfaces. The new painting restores dark brown weave and muted red/ochre marks erased by the neural baseline. It retains the original left/right proportions, dark separation and green-brown support. Independent development review is clear. Fresh source preflight reproduced all 32 paintings, then selection added only Donau while preserving all 4,917 prior assets and every protected Skurfa cut. Native and final quality acceptance remain pending.

[Twin Castle cut 226's cropped embroidered bed](../../integrations/envhd/production/field-bespoke-textile-held-drafts.json) now has a detailed painting, but the upper embroidery loops have been regularized into circular nodes. The source pattern needs correction before selection. Both candidates pass the unchanged cutout canvas/coverage/alpha/black gates. Only custom generated artwork, repaired variants, prompts and hash-bound metadata are versioned; original game references and comparison boards remain private. Thirteen known custom-draft source images now remain a subset of the 244 held field images; repeated versions and reviewed/held histories are deduplicated by source identity.

## Animated world water source

[The partial source contract](../../integrations/envhd/production/world-water-source-audit.json) identifies one byte-identical water TIM across all eight continent banks. It initializes a 64×64 texture at native word rectangle `[448,0,16,64]` and eight palette rows at `[1008,485,16,8]`. All 296 part-zero textured faces use page 7 and UVs within `[0,0,63,63]`. Each continent's ordered uploads completely initializes these regions. General bank 5695 touches neither region, and both final whole-bank orders agree. These checks do not simulate partially interleaved asynchronous callbacks or every other upload/state.

The source-extracted Java float counters were exercised with a fake row-offset recorder, without executing the full engine or GPU. At normal world initialization mode 1, the first row rotation occurs on invocation 1, then every six eligible invocations; the palette counter resets every **43**, rather than an assumed 42. Together with 64 row offsets, their isolated controller state repeats after a one-invocation transient and a 16,512-invocation period. Modes 2 and 3 were measured only as fixed-mode sensitivity probes. Periods are controller facts, not frame-image generation counts. Script/debug mode changes, guards and presentation remain unresolved.

The Queen Fury wake samples the same page and palette sequence. The existing indexed-detail infrastructure could preserve palette and row movement, but its consumer ownership and EnvHD/FxHD switch behavior need coordination before integration. The next work should reuse this one water source across all eight parts, preserving live palettes and native timing, rather than generating thousands of phase combinations. No animated-world hold is cleared by this partial audit.

## Four-times water candidate

[One generated coefficient family](../../integrations/envhd/production/world-water-candidate-artwork.json) now covers the shared water source at 256×256. The pinned batch upscaler processed all eight palette profiles using 16 pixels of periodic context. A single source-local coefficient field was then fitted against all eight outputs together. Each sample retains its native index as the primary contribution and uses at most a half-strength mixture with a secondary index from the wrapped 3×3 source neighborhood. The exact primary channel makes the original indices recoverable; original TIM/input/palette files are not added.

Eight custom RGB previews show clearer caustic highlights while retaining the source ripple pattern. They are opaque diagnostic renders, not independent jobs or runtime alpha/STP textures. Native index zero is visibly colored in all eight current palettes; index zero must never be treated as a transparency rule. The manifest records the actual source black/STP classes separately.

The public replay tool verifies all common/continent bank files and part-zero model hashes, all captured neural inputs/outputs, source canvas and palette positions, and reproduces the private prototype's coefficient image byte-for-byte. Fourteen CPU behavior tests cover every palette's influence, bounded mixtures, periodic neighborhoods, live recoloring, signed row shift equivalence, native black, STP-independent preview RGB, malformed codes and source/inference changes before receipt publication. These checks do not exercise the renderer or prove native gameplay.

The current shared indexed companion accepts 2× images and cannot load this 4× candidate. Coordinated consumer-local 4× support must preserve live palettes, row/copy ownership, UV64 fallback and all four EnvHD/FxHD combinations. EnvHD owns terrain water; the Queen Fury wake stays native unless FxHD supplies its own independently selected binding. This candidate is versioned but unselected, with artistic, renderer and device acceptance pending.

## Further textile corrections

[The complete 33-painting candidate input](../../integrations/envhd/production/field-bespoke-textile-detail-reviewed-selection.json) preserves every current32 painted record and adds Fletz cut206 slot2. Its native source is a small dark navy/gray-green textile fragment, not a sign. The first painted attempt made diffuse marks into outlined scrolls; the second softens them into woven patches. Original crop, room context and exact120×88 repaired output were compared. Fine pattern interpretation still needs independent review; the candidate is unselected. Fresh source preflight reproduces all33 paintings with unchanged recipe pins.

Twin Castle bed versions2 and3 correct some closed embroidery junctions into open curls, but one upper junction remains closed and the last attempt adds embossed-looking upholstery texture. The throne-hall cloth version1 retains the broad plum/green panels, support rod and finial, but overinterprets ornament and the lower-left metal support. These versions remain held in the [versioned draft history](../../integrations/envhd/production/field-bespoke-textile-held-drafts.json), alongside the rejected Fletz first attempt. All five new attempts passed source canvas/alpha/black/coverage checks; those gates do not prove material or motif intent. No remaining hold, runtime selection or installer changed in this authoring checkpoint.

## Architectural authoring batch

[The complete36 candidate input](../../integrations/envhd/production/field-bespoke-wood-architecture-reviewed-selection.json) preserves all32 selected paintings and the pending Fletz fragment, then adds Rouge's interlocking timber railing, Deningrad's room partition and Indels Castle's small carved panel. Each native crop and complete original scene was compared with the generated/repaired output. Fine lattice joins, picture/fitting content and carved ornament remain interpretive and require independent review. New candidates are unselected; all244 field holds and the Tiberoa hold remain.

[Two Phantom Ship column paintings](../../integrations/envhd/production/field-bespoke-wood-architecture-held-drafts.json) returned the wrong full-canvas aspect. Both were rejected by the existing36:62 source gate before repair. No repaired output or runtime selection exists for either. Their custom generated inputs and prompts are preserved; source references and diagnostics remain separate. Original cutout/registration algorithms and gates are unchanged.


## Seven independently cleared development selections

The [complete39-painting selection input](../../integrations/envhd/production/field-bespoke-seven-independent-reviewed-selection.json) retains every prior32 painted record and adds seven independently development-intent-cleared candidates: Fletz textile, Deningrad partition, Indels carved panel, two Evergreen canopy fragments, corrected Bale facade relief and Furni source-surround door repair. The [independent nine-image receipt](../../integrations/envhd/production/field-independent-nine-review.json) records exact raw/output identities and individual dispositions. Standards/source replay cleared all41 source-bound recipes; image-level Spec review cleared seven for development only.

Rouge lattice openings cut misaligned generated connectors into fragments, and Phantom Ship canvas-fit moves an internal lower band and pale weathering groups. Both remain unselected until source landmarks are repaired and independently reviewed. New Bale corner and Shrine arch candidates remain independently pending. Current unselected draft sources are15 unique source keys within237field holds; histories and repeated versions are deduplicated by source identity.

Selection preserves all4,918 earlier asset records and every Skurfa protected cut. Fresh current-source replay, complete selected-field CPU audit and five-part package byte checks are required before the coordinator integrates this source. This is not native/script/GPU/Steam Deck or final Skurfa-equivalent finish acceptance. No separate release was published.


## Crystal Palace table development selection

[Independent original-reference review](../../integrations/envhd/production/field-independent-table-review.json) clears cut 369 foreground 14 for development use. The original oval tabletop framing, two pale dishes, small central angular form, cropped chair fragments and restrained warm lighting are retained. Fine wood and ceramic detail remains interpretive; native/device/final art acceptance is pending.

The [complete 40-painting input](../../integrations/envhd/production/field-bespoke-table-independent-reviewed-selection.json) preserves main's prior 39 candidate records exactly. Fresh original-source preflight reproduced all 40 recipes. Runtime selection retains all 4,925 prior records and every protected Skurfa cut, adding only the table. The [readiness receipt](../../integrations/envhd/production/field-table-selection-readiness.json) records the source bindings and focused checks. This branch contains only the table's custom generated/repaired pixels, prompt-bearing recipe, review and selection metadata and coverage updates. Unrelated authoring, Tiberoa, pending drafts, verifier support and workflow changes are excluded. The EnvHD mod and four field packs built from clean committed source. Strict package verification compared all 4,926 selected field image names, sizes and bytes across the four parts, plus 5,312 JAR resources. The 32 painted-field, three install-selection and 39 archive checks passed. Public release publication remains the coordinator's responsibility.


## Recorded generation4 publication checkpoint

Generation4/source `5d3db5c84174f2936fe7256123a11f6bb640760a` publishes4,925 selected fields/39 painted fields and34 painted battle masters. Accepted main `6612a533307e5f066bc46e7870d658f8add42752` selects4,926/40 and47 respectively; one table and13 Giganto paintings await the next coordinated download. The earlier0af publication receipt remains historical. Native and final artistic acceptance remain separate.


## Two Donau development selections

[Independent original-reference review](../../integrations/envhd/production/field-independent-donau-two-review.json) clears the Donau column and arch (cut244 foreground8) and larger exterior wall (cut241 foreground0) for development selection. Their source silhouettes, openings and main color landmarks remain anchored. Fine surface detail is interpretive. The tablet remains excluded until its dense chips and pits are softened and independently reviewed.

The [complete42-painting input](../../integrations/envhd/production/field-bespoke-donau-two-independent-reviewed-selection.json) preserves main's prior40 candidate records exactly. All42 recipes reproduce from current original sources. Runtime selection preserves all4926 prior records and every protected Skurfa cut. The [readiness receipt](../../integrations/envhd/production/field-donau-two-selection-readiness.json) binds the selection, independent review and86 focused checks. Only these two custom input/output images, their recipes/review, runtime resources and coverage metadata are included. Unfinished authoring remains on the ongoing branch. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending; ROOT coordinates the current installer publication.

The EnvHD JAR and four field packs built successfully from frozen committed source. Strict packaging verification matched all4928 selected field images and5314 JAR resources by name, size and SHA256. Env1HD and Env3HD are byte-identical to the prior accepted package; Env2HD and Env4HD each contain one additional painting. Battle runtime resources remain exact to accepted main. The package receipt records all five output hashes. This verifies the EnvHD subset, with full installer/carrier closure owned by ROOT.


## Twin Castle clipped hanging development selection

The [independent review](../../integrations/envhd/production/field-independent-twin-fragment-review.json) records both reviewers clearing cut228 foreground4: its clipped cloth angle, unequal small strokes, nested curves, rails, finial and lower wire preserve source relationships. The two-panel crop remains held due to reviewer disagreement on crisp markings; the three-panel crop needs repeated stars and fitting details softened. Their authoring records and all held versions remain on the ongoing branch.

The [complete43-painting selection](../../integrations/envhd/production/field-bespoke-twin-fragment-independent-reviewed-selection.json) preserves all42 prior records exactly. All43 recipes reproduce from current originals; runtime retains all4,928 previous records and every protected Skurfa cut. The [readiness receipt](../../integrations/envhd/production/field-twin-fragment-selection-readiness.json) binds the one-image change, original-fidelity reviews and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending; ROOT coordinates the current installer publication.


## Five independently reviewed scenery selections

The [independent eight-crop review](../../integrations/envhd/production/field-independent-scenery-eight-review.json) clears the Shrine arch, Shrine left V-finial fragment, Moon radial layer, Queen Fury open crate and Ulara rim for development selection. The Bale roof corner, Undersea coral rim and Fueno support require correction and remain excluded. Actual original/reference crops and static scene placements were inspected; source integrity and replay passed.

The [complete48-painting selection](../../integrations/envhd/production/field-bespoke-scenery-five-independent-reviewed-selection.json) preserves every prior43 recipe exactly. All48 recipes replay from current originals; all4,929 previous runtime records and protected Skurfa cuts remain exact. The [readiness receipt](../../integrations/envhd/production/field-scenery-five-selection-readiness.json) binds the five-image change and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending. ROOT coordinates the current installer publication; the prior publication ledger is a historical observed snapshot.


## Four independently reviewed palace selections

The [independent eight-crop review](../../integrations/envhd/production/field-independent-palace-eight-review.json) clears the Crystal Palace cut368 panel rail, cut369 rising rail, cut367 baluster/support and Twin Castle cut221 ornamented rim for development selection. The Crystal Palace lower post, curved-edge fitting and descending rail, plus Twin Castle night fittings require correction and remain excluded. Actual original/reference crops and static scene placements were inspected; source integrity and replay passed.

The [complete52-painting selection](../../integrations/envhd/production/field-bespoke-palace-four-independent-reviewed-selection.json) preserves every prior48 recipe exactly. All52 recipes replay from current originals; all4,934 previous runtime records and protected Skurfa cuts remain exact. The [readiness receipt](../../integrations/envhd/production/field-palace-four-selection-readiness.json) binds the four-image change and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending. ROOT coordinates the current installer publication; the prior publication ledger is a historical observed snapshot.


## Sixteen independently reviewed scenery and furnishing selections

The [eighteen-crop review](../../integrations/envhd/production/field-independent-final-eighteen-review.json) clears sixteen source-faithful furnishings and architectural crops across Queen Fury, Black Castle, Mayfil, Twin Castle, Crystal Palace, Wingly Forest, Forbidden Land, Ulara and Lohan. The Queen Fury cages need finer original lattice spacing; the Shrine patch needs flat low-relief shading. Those two remain excluded. Two cleared Lohan source layers are fully occluded in the supplied all-layer composites; script-state visibility remains unverified.

The [complete68-painting selection](../../integrations/envhd/production/field-bespoke-scenery-sixteen-independent-reviewed-selection.json) preserves all prior52 recipes and4,938 runtime records, including protected Skurfa cuts. All68 recipes replay from current originals and86 focused checks pass. The [readiness receipt](../../integrations/envhd/production/field-scenery-sixteen-selection-readiness.json) binds this increment; ROOT coordinates installer/current-carrier publication. Original diagnostic pixels remain private; no helper/engine/UI/workflow change is included. Native/script/GPU, physical Deck, campaign and final artistic acceptance remain pending.


## Three corrected wall and rail paintings selected

The [six-source review](../../integrations/envhd/production/field-independent-precise-six-review.json) clears the flat Shrine wall and two Palace rail fittings with the original rounded forms. Cargo lattice, palace-post coverage/transfer seams and balcony fitting/illumination issues remain held. The [complete 71-painting selection](../../integrations/envhd/production/field-bespoke-precise-three-independent-reviewed-selection.json) retains all prior 68 recipes and uses version 2 candidate filenames for the corrections, preserving earlier artwork. Runtime resource names and the single EnvHD switch remain unchanged.

All 71 paintings replay from fresh originals and 90 focused checks pass. The [readiness receipt](../../integrations/envhd/production/field-precise-three-selection-readiness.json) binds this increment; ROOT coordinates installer publication. Protected Skurfa artwork and all 4,954 prior runtime assets remain exact. Native, script-state, Deck, campaign and final artistic acceptance remain pending.


## Corrected Twin Castle balcony selected

The [independent review](../../integrations/envhd/production/field-independent-balcony-registration-review.json) clears the corrected curved night balcony: muted fittings, subdued cap and diffuse dark front. Existing pinned perspective registration aligns the painted curve before unchanged cutout repair. The original standalone coverage failure remains recorded in authoring history; no guard or helper was changed.

The [complete72-painting selection](../../integrations/envhd/production/field-bespoke-balcony-independent-reviewed-selection.json) preserves all71 prior recipes and4957 runtime records. The [readiness receipt](../../integrations/envhd/production/field-balcony-selection-readiness.json) tracks source replay and package verification. One EnvHD switch controls all four download packs. Native/script/GPU, campaign, physical Deck and final artistic acceptance remain pending.


## Crystal Palace post correction selected

The [independent review](../../integrations/envhd/production/field-independent-post-components-review.json) clears an exact replay of the already original-reviewed Palace post correction. A bounded component helper transfers the existing custom gold shaft and continuous teal base into the earlier custom rail painting. Only the explicit lower-right source region changes; the original alpha and all outside pixels are exact. Both generated inputs and prompts are retained, with distinct composite provenance. The rejected standalone full-frame correction remains rejected. No new image generation was needed.

The [complete73-painting selection](../../integrations/envhd/production/field-bespoke-post-components-reviewed-selection.json) preserves all72 prior recipes and4958 runtime records. The [readiness receipt](../../integrations/envhd/production/field-post-components-selection-readiness.json) binds behavioral checks, fresh source replay and package verification. One EnvHD switch controls all four download packs. Native/script/GPU, campaign, physical Deck and final artistic acceptance remain pending.


## Dynamic and prop inventory snapshots reconciled

The [curated checkpoint](../../integrations/envhd/production/environment-dynamic-prop-inventory-checkpoint.json) keeps units separate: one authored shared water coefficient family serves eight continent parts with296 textured faces and eight palette rows; it is not eight separate restoration jobs or16,512 animation-frame jobs. The candidate remains unselected pending4x consumer/state/ownership support.

The received field-material snapshot has135,279 material bindings across7,664 contexts. Ownership is unresolved for7,652 contexts;12 are source-proved CharHD. Its5,984 repeated inspection groups are priorities, not EnvHD image jobs. The snapshot explains the earlier25 absent texture hints as24 excluded outside-MRG routes plus one included sentinel, and608 absent script hints as567 present alias targets plus41 excluded outside-MRG routes. These route hints are not missing runtime assets. Mutable alias ordering, actual controller/script branches, inherited uploads and exact sampling coverage remain open. Both whole dynamic and owned-prop image denominators remain unknown. This records the retained source snapshots rather than claiming a fresh whole-game census.


The [independent inventory review](../../integrations/envhd/production/environment-dynamic-prop-inventory-independent-review.json) clears this metadata checkpoint at source3b199 on both axes. Its4958/72 baseline is historical. Current accepted selection remains4959/73 with204knownstatic images left; this inventory update selects no new images.
