# EnvHD current coverage

[The active ledger](../../integrations/envhd/production/active-coverage-ledger.json) records current selected development assets, held work, custom drafts, unknown denominators and next tasks in one place. Its hashes bind the underlying source and selection ledgers. Different units must not be added into a whole-campaign image count.

Current static work remaining is **203 images: 202 field images and one terrain repair**. Battle materials additionally have **351 held bindings**, including 350 animation/context bindings and one incomplete palette. Their complete image-job denominator is unknown. Eight animated continent parts and environment props/overlays also need source/state/ownership contracts before their image denominators can be established.

Field selection is 4,960 development images: 4,886 neural and 74 painted.

Current authoring queue: all83historical canonical sourcekeys reviewed:74selected,9requiringrevision andnoneawaitingrevieworintegration. Alloldrecipes/custom/heldversions preserved.50unique unselected customkeys subset202fieldholds. Skurfareused; nativegameplay/finalqualitypending.

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

## Scenery and fitting candidates

The [complete 47-candidate input](../../integrations/envhd/production/field-bespoke-scenery-fittings-reviewed-selection.json) preserves all 43 prior records and adds four paintings: Queen Fury lattice cargo cages, the two-panel Crystal Palace rail, Shirley Shrine's left stone finial fragment and Undersea Cavern's low coral rim. Corrected cargo and rail versions restore the source construction and panel divisions lost in their first attempts. The [two rejected versions](../../integrations/envhd/production/field-bespoke-scenery-fittings-held-drafts.json) retain their custom artwork, prompts and rejection reasons. Source references and comparison boards remain private.

The coral source is too wide for ordinary authoring, so its 64×18 crop was placed at native `[16,23]` inside a transparent 96×64 canvas. The new optional `sourceAuthoringCrop` operation validates that explicit geometry and the original generated PNG's aspect before decoding; its fixed fourfold `[64,92,320,164]` crop yields 256×72. Generated silhouette intersection-over-union must reach 0.97, and visible generated pixels outside the crop may not exceed 1% of the original visible source. This coral measured 0.9835974807945187 and zero outside pixels. The crop applies neither source alpha nor source RGB. The unchanged pinned cutout repair follows it and restores exact original alpha and visible black. The operation cannot combine with registration, canvas normalization, opaque-border repair or source-surround restoration.

All 47 current-source recipes reproduced, and 109 focused checks passed, including 14 crop behavior cases and eight added preflight cases. Those checks prove source binding and reproducibility; they do not prove fine motif fidelity, native placement or complete scene finish. Independent review must check cargo interior bars, rail surface/fittings, the Shrine fragment's dark weathering and adjoining finial, and the coral's fine branch joins. Six candidates now await independent review; the two separate Rouge/Phantom revisions remain held. Nineteen unique unselected custom source keys are a subset of the 237 held field images.

Runtime remains 4,925 selected fields, including 39 independently reviewed paintings. Known static work remains 238 images. No runtime, engine, protected Skurfa artwork, installer or release selection changes in this authoring checkpoint.


## Organic scenery and open storage crate authoring

The [complete 49-candidate input](../../integrations/envhd/production/field-bespoke-organic-storage-reviewed-selection.json) preserves all 47 prior records and adds the Moon cut 600 radial/organic layer and Queen Fury cut 284 open storage crate. Both original crops, complete source contexts, raw paintings, repaired pixels and current static placements were inspected. Moon glow/stipple strength and crate fine construction remain interpretive and require independent review. Eight candidates now await independent review; no new runtime selection.

[Six held versions](../../integrations/envhd/production/field-bespoke-organic-storage-held-drafts.json) preserve unsuccessful Rouge lattice corrections 2/3, Phantom padded-crop corrections 3/4, Fueno stonework 1 and Black Castle rootwork 1. Rouge still breaks shifted connectors against native lattice openings; Phantom fails unchanged silhouette gating and has unresolved internal landmarks. Fueno adds strong construction detail/brightness; Black Castle misses source-visible material. Source gates remain unchanged and none of these holds is cleared. Further Rouge/Phantom work needs more constrained landmark authoring rather than another identical full-crop attempt.

All 49 current-source candidate recipes reproduced. Runtime remains 4,925 selected fields, including 39 independently reviewed development paintings. Twenty-three unique unselected custom source keys are a subset of 237 field holds; known static work remains 238 images. Original sources, scene layouts and comparison evidence stay private. Native gameplay, final art quality and current download integration remain separately unverified for these new candidates.


## Curved scenery material corrections

The [complete 51-candidate input](../../integrations/envhd/production/field-bespoke-scenic-materials-reviewed-selection.json) preserves all 49 prior records and adds corrected Ulara cut 514 platform rim and Fueno cut 314 interior support. Original crops, complete source scenes, raw paintings, repaired pixels and current static placements were inspected. Ulara version 2 removes newly invented block courses; Fueno version 2 reduces raised bands and rugged ledge relief. Both retain interpretive surface detail and visible joins to adjacent existing layers, requiring independent source review and whole-scene finish work. Ten candidates await independent review; none added to runtime.

[Five held versions](../../integrations/envhd/production/field-bespoke-scenic-materials-held-drafts.json) preserve Undersea Cavern coral versions 1/2, the first Ulara/Fueno paintings and the uncertain Hoax fragment. Coral still misses too much source-visible branch footprint, even after a targeted correction. Hoax material identity is not established and its painting invents coarse masonry. No coverage, visibility or semantic hold was waived.

All 51 current-source candidate recipes reproduced. Runtime remains 4,925 selected fields, including 39 independently reviewed development paintings. Twenty-seven unique unselected custom source keys remain a subset of the 237 field holds; known static work remains 238 images. Original references, layout, diagnostics and comparison boards stay private. No new runtime, engine, protected Skurfa, delivery, installer or release changes. Native gameplay and final art acceptance remain pending.


## Crystal Palace railings and architectural support

The [complete 55-candidate input](../../integrations/envhd/production/field-bespoke-palace-railings-reviewed-selection.json) preserves all 51 prior records and the 39 selected paintings, adding four Crystal Palace fragments: cut 368 foreground 3, cut 369 foregrounds 1/2 and cut 367 foreground 3. Original complete scenes, raw paintings, repaired crops and individual static placements were inspected. Joint cut 368 and cut 369 composites check the adjacent candidate pieces together. Original slopes, openings, outer footprints and visible black remain source controlled.

Fourteen candidates await independent review. Fine fitting divisions, thin curved band registration, gold strength, veining and adjoining native-layer finish remain interpretive. The cut 367 projecting stone support subject is unresolved; authoring follows broad source curves/recesses without assigning a face or creature. These static comparisons do not establish live occlusion, script-state coverage or final whole-scene quality.

All 55 candidate recipes reproduced against current source data. Runtime remains 4,925 selected fields, including 39 independently reviewed development paintings. Thirty-one unique unselected custom source keys remain a subset of the 237 field holds; known static work remains 238 images. No new runtime, engine, protected Skurfa, delivery, installer or release changes. Source references, layout, diagnostics and comparison boards remain private. Native gameplay and final art acceptance remain pending.


## Castle curved architecture and ornament

The [complete 58-candidate input](../../integrations/envhd/production/field-bespoke-castle-curves-reviewed-selection.json) preserves all 55 prior records and 39 selected paintings, adding Crystal Palace cut 369 foreground 0, Twin Castle cut 221 foreground 5 and corrected Twin Castle cut 222 foreground 8. Original complete scenes, raw paintings, repaired crops and current static placements were inspected. The Palace piece was also previewed jointly with the two prior pending cut 369 rails. Source alpha, visible black, crop and footprint remain exact; finer hardware, skull apertures, fracture registration, mottled wear and lighting still need independent review. The day rim exposes a rectangular finish join to adjoining existing architecture; source alignment does not establish final whole-scene quality.

[Three held versions](../../integrations/envhd/production/field-bespoke-castle-curves-held-drafts.json) retain Twin Castle cut 663 balcony versions 1/2 and night balcony version 1. The exterior balcony keeps an invented central gold emblem after targeted correction; both versions remain held despite technical repair passing. Its initial prompt incorrectly said four pale stripe insets; closer source inspection confirms five, including the one near the right crop, also present in the paintings. Corrected night version 2 removes coherent new gold stems and is independently pending; fitting highlights and surface flecks still need source-lighting review.

All 58 current-source candidate recipes reproduced. Seventeen candidates await independent review, and Rouge/Phantom P2 revisions remain held. Thirty-five unique unselected custom source keys are a subset of 237 field holds; known static work remains 238 images. Runtime stays 4,925 selected fields/39 paintings. No new engine, protected Skurfa, runtime, delivery, installer or release changes. Source references/layout/comparison boards remain private; native gameplay and final art acceptance remain pending.


## Structural materials and existing sky reuse

The [complete 60-candidate input](../../integrations/envhd/production/field-bespoke-structural-materials-reviewed-selection.json) preserves 58 prior records and 39 selected paintings, adding Queen Fury cut 282 foreground 6 and Black Castle cut 192 foreground 4 version 3. Original whole scenes, raw paintings, repaired crops and static placements were inspected. The Queen Fury panel belongs to fitted cabin cupboards/bunks; its small fittings, internal marks and contrast still need independent review. Fresh Black Castle version 3 uses native crop and whole original scene only, reducing invented leafy relief. Its exact material identity, vertical dark-patch registration and adjacent finish remain unresolved. Exact source alpha, visible black and crop preservation do not prove native script-state or final whole-scene quality.

[Four held versions](../../integrations/envhd/production/field-bespoke-structural-materials-held-drafts.json) retain Bale cut 67 foreground 4, Hoax cut 626 foreground 6 and Black Castle versions 1/2. Bale fails the unchanged footprint coverage gate and invents lower stair forms; Hoax redraws unresolved lower source marks and adds construction details; Black Castle versions 1/2 impose heavy leafy/lichen-like relief. All 60 current-source candidate recipes reproduced. Nineteen candidates await independent review; Rouge/Phantom P2 revisions remain held. Thirty-nine unique unselected custom source keys remain a subset of 237 field holds. Known remaining static work stays 238 images; runtime stays 4,925 selected fields / 39 paintings. No runtime, protected Skurfa, engine, delivery, installer or release changes. Original references/layout/comparisons remain private; native and final acceptance remain pending.

The [existing sky reuse audit](../../integrations/envhd/production/battle-sky-existing-custom-reuse.json) records public source-bound panorama candidates and exact selected runtime copies, including twelve custom/non-neural records. `battle-skies.json` field/world usage lists configured encounter routes selecting battle panoramas; these routes do not establish shared pixels with field backgrounds, world terrain, location thumbnails or parchment. Deduplicate actual decoded panorama masters and preserve each MCQ header's placement. No new sky artwork was generated.


## Interior furnishings and Mayfil shadowed supports

The [complete 63-candidate input](../../integrations/envhd/production/field-bespoke-interior-supports-reviewed-selection.json) preserves all 60 prior records and 39 selected paintings, adding corrected Black Castle cut 660 foreground 3 and Mayfil cut 542/544 foreground 0. Original complete scenes, both raw/repaired versions and current static placements were inspected. Source-only version 2 corrects the throne's heavy bevel/diagonal lower-panel relief and removes large block/fracture networks from the two Mayfil supports. Exact frame bands, lower-panel mark identity, native dark-patch registration, shaft streaks and upper sampling/finish joins still need independent review. Source silhouette and alpha checks do not establish correct internal features, native state behavior or whole-scene finish.

[Five held versions](../../integrations/envhd/production/field-bespoke-interior-supports-held-drafts.json) preserve Twin Castle cut 227 foreground 4 versions 1/2, Black Castle throne version 1 and both Mayfil versions 1. The Twin Castle chair/table correction reduces gold/ochre contrast but leaves a rectangular tabletop material/light join; both versions stay held. The next correction should constrain the chair and adjoining table features rather than repeating an identical whole-crop prompt. Version 1 Mayfil fracture networks and throne relief remain rejected despite passing unchanged technical repair.

All 63 current-source candidate recipes reproduced; 22 candidates await independent review and Rouge/Phantom P2 revisions remain held. Forty-three unique unselected custom source keys are a subset of 237 field holds. Known static work stays 238 images; runtime stays 4,925 selected fields / 39 paintings. All eight custom raw/repaired pairs are public and hash-verified, with prompts recorded in the manifests. Engine, protected Skurfa, runtime, delivery, installer and release selections remain unchanged. Original source/reference/layout/comparison files remain private. Native gameplay and final art acceptance remain pending.


## Red chair, Palace openwork and Wingly fittings

The [complete 66-candidate input](../../integrations/envhd/production/field-bespoke-carved-furnishings-reviewed-selection.json) preserves all 63 prior records and 39 selected paintings, adding Twin Castle cut 226 foreground 5, Crystal Palace cut 375 foreground 2 and corrected Wingly Forest cut 386 foreground 2. Original complete scenes, every raw/repaired version and static scene placements were inspected. Fine chair upholstery marks, frame thickness/light, Palace cap aperture shape/steps and pale Wingly recess/diagonal-fold registration still need independent source review. The Palace cap's sculptural identity and the Wingly fragment's precise material remain unresolved; no new creature or readable symbol is accepted.

[Three held versions](../../integrations/envhd/production/field-bespoke-carved-furnishings-held-drafts.json) retain Wingly Forest cut 384 foreground 6 versions 1/2 and cut 386 foreground 2 version 1. Placement inspection corrects the initial prompt's bottom-right support shorthand: cut 384's crop is the lower hanging architectural/organic fitting beneath illuminated green fixtures. Its source-only correction reduces heavy looping grain but does not close source relief registration or the upper/right whole-fixture finish join; both versions remain held. Cut 386 version 1 adds concentric ridges and ornate bevels; corrected version 2 removes those additions and remains independently pending.

All 66 current-source candidate recipes reproduced. Twenty-five candidates await independent review; Rouge/Phantom P2 revisions remain held. Forty-seven unique unselected custom source keys remain a subset of 237 field holds. Known static work stays 238 images; runtime stays 4,925 selected fields / 39 paintings. Six custom raw/repaired pairs are public and hash-verified, with prompts recorded in the manifests. No gates waived or runtime, engine, protected Skurfa, delivery, installer or release changes in this authoring patch. Original source/reference/layout/comparison files remain private; native gameplay and final whole-scene art acceptance remain pending.


## Accepted main integration

Main `ad1a234d` is integrated into the ongoing artwork branch. Its accepted 4,925 fields / 39 field paintings remain byte-exact, alongside its accepted 22 additional battle presentation paintings (34 total painted battle masters). Every premerge field authoring file and all 66 candidate records remain unchanged; 25 pending candidates and two reviewed revisions stay outside new runtime selection. No art hold is cleared by this merge. Build fixtures retain both main's battle/macro controls and this branch's source-authoring-crop controls.

Publication figures now distinguish main's recorded `d7311334` snapshot (4,917 fields / 12 battle paintings) from the accepted source selection: eight field paintings and 22 battle paintings still await coordinated exact-source publication according to that snapshot. The old `0af4f997` snapshot is preserved as historical metadata. This is a repository snapshot, not a fresh live release query. Native gameplay and final artwork acceptance remain pending; no game was launched.

The merged source controls pass 75 focused synthetic fixtures: authoring crop, painted fields, macro detail and battle presentation packing. All 42 CI fixture paths exist without duplicate names. The 66-candidate source preflight remains bound to unchanged candidate/verifier bytes; these checks do not establish native gameplay or final visual acceptance.


## Palace dining table, Ulara counter and held rail/ledge corrections

The [complete 68-candidate input](../../integrations/envhd/production/field-bespoke-small-furnishings-reviewed-selection.json) preserves all 66 prior records and 39 selected paintings, adding Crystal Palace cut 369 foreground 14 and source-only corrected Ulara cut 525 foreground 12. Original full scenes, all seven raw versions and five repaired variants, and all five available static scene placements were inspected. The table preserves pale dishes, the small central angular object and cropped chair fragments; their precise geometry/identity, wood grain strength and lighting need independent source review. Ulara correction 2 removes strong rust/metal scratches, thick round inset rim and the sharply outlined polygon from its first painting. Source orange goods grouping, soft dark inset extent, thin bars and green-lit shadows still need independent review. Their precise material/content identities remain unresolved.

[Five held versions](../../integrations/envhd/production/field-bespoke-small-furnishings-held-drafts.json) retain Fueno cut 309 foreground 0 versions 1/2, Ulara counter version 1 and Wingly Forest cut 386 foreground 6 versions 1/2. Fueno version 1 imposes rigid bracket arms and rough masonry around uncertain source fittings; Wingly version 1 adds repeated raised circular studs, rimmed blue blocks and concentric relief. Their source-only second versions remove some invention but change the occupied footprint too much and fail the unchanged coverage gate. No repaired payload is produced for those two failures. Constrain the native source canvas, internal landmarks and adjoining material finish before another whole-crop attempt; no coverage waiver is allowed.

All 68 current-source candidate recipes reproduced. Twenty-seven candidates await independent review; Rouge/Phantom P2 revisions remain held. Fifty-one unique unselected custom source keys remain a subset of 237 field holds. Known static work stays 238 images; runtime stays 4,925 selected fields / 39 paintings. Seven custom raw inputs and five repaired outputs are public and hash-verified, with the built-in generation prompts recorded in the manifests. Accepted main, protected Skurfa, engine, runtime, delivery, installer and release selections remain unchanged. Original source/reference/layout/comparison images remain private; native gameplay and final whole-scene art acceptance remain pending.


## Crystal Palace table development selection

[Independent ROOT review](../../integrations/envhd/production/field-independent-small-furnishings-review.json) clears the table from cut369 foreground14 for development use. The original oval framing, two pale dishes, small central angular form, cropped chair fragments and restrained warm light are retained; fine wood and ceramic detail remains interpretive. The Ulara counter remains held because its far end is too warm/bright and its goods become conspicuous round forms. Correct that candidate independently.

The [40-painting selection](../../integrations/envhd/production/field-bespoke-table-independent-reviewed-selection.json) preserves all39 prior candidate records. Fresh original-source preflight reproduced all40 recipes. Runtime selection preserves all4925 prior records and every protected Skurfa cut, adding only the table. Relevant checks passed40 painted-field checks,3 install-selection checks and39 archive checks. The EnvHD mod and four download packs built successfully; package verification compared every resource name, size and selected image byte across4926 fields and5312 JAR resources. The [readiness receipt](../../integrations/envhd/production/field-table-selection-readiness.json) pins the five files. This coherent increment can proceed through the coordinator's normal main/install publication while remaining authoring continues. Native/device/final art acceptance remains pending; this is not a newly published download.


## Small stone fittings and flat wall authoring

The [complete70-candidate input](../../integrations/envhd/production/field-bespoke-stone-fittings-reviewed-selection.json) retains all68 prior records and40 selected paintings, adding Forbidden Land cut399 foreground9's small green stepped base and Ulara cut516 foreground4's finial pedestal. Corrected2 paintings reduce the original drafts' invented crest/coarse construction. Upper bands, diffuse patches, thin finial arms, collar tiers, lighting and adjoining scene finish still require independent source review. Neither candidate is selected.

[Seven held versions](../../integrations/envhd/production/field-bespoke-stone-fittings-held-drafts.json) preserve the first Forbidden/Ulara drafts, both pale Shrine fitting attempts and three flat Shrine wall attempts. Pale fitting1/2 introduce four heavy knob-like forms and fractured/projecting construction. Wall1/2 turn a flat near-rectangular crop into a crown silhouette and fail unchanged coverage; they have no repaired outputs. Wall3 retains the flat occupied corners, and its private repaired draft preserves native alpha/black, but its generated input is opaque RGB while the original has an empty row/column. Existing candidate preflight rejects that combination. This third wall draft remains held; no guard, source pixels or repair threshold is changed. A separately explicit supported container-conversion protocol is needed before that painting can become a selectable candidate.

All70 compatible current-source recipes reproduced after the unsupported wall3 was moved to held history. Nine raw paintings and seven repaired drafts are public/hash-bound; original references, source layouts and comparisons remain private.27 candidates await independent review,54 unique custom unselected source keys remain a subset of236 field holds, and known static work remains237. Runtime4926 fields/40paintings, protected Skurfa, engine, delivery and install selection are unchanged. The clean table-only integration branch is reported separately. Native/device/final whole-scene acceptance remains pending.


## Recorded RGB container conversion for the flat Shrine wall

The [complete 71-candidate input](../../integrations/envhd/production/field-bespoke-rgb-wall-reviewed-selection.json) keeps all 70 prior records exact and adds the existing Shrine cut153 foreground2 painting3 through an explicitly recorded RGB-to-RGBA container conversion. The original generated RGB PNG remains retained and hash-bound. Conversion preserves every RGB pixel and coordinate, adding only constant alpha255; it applies no original source pixels, resizing, normalization or masking. The unchanged cutout recipe then reproduces the same repaired image and native source alpha/black. Earlier held history remains exact.

Preflight reconstructs the converted bytes from the retained original PNG and requires exact recipe metadata, script hash, paths, bounds and bytes. Legacy unrecorded RGB cutout inputs remain prohibited. Eight container checks and 46 field-selection checks passed, including changed original/container colours, alpha, dimensions, input identity, transparency keys, animation and incompatible padding/geometry operations. Fresh original-source preflight reproduced all 71 recipes. The wall's fine grain, diffuse patch geometry, contrast and adjoining finish still require independent review; it is not installed. Runtime4,926 fields/40paintings, 236field holds and237 known static holds are unchanged. See the [readiness receipt](../../integrations/envhd/production/field-rgb-wall-authoring-readiness.json).


## Accepted main integration at6612a533

The table-only increment passed independent Spec and Standards reviews and merged through PR54. Authoring now uses main's exact 40-painting selection/review, field runtime ledger and combined source-bound package receipt. Every pending candidate, held draft, Tiberoa proposal and optional authoring helper remains preserved separately. Battle source bytes and35 presentation+12 prior paintings are exactly accepted main. This merge changes no original/custom painting pixels and clears no new art holds. Public download counters are refreshed separately from current published source; merging does not publish a download.


## Wheel, hanging forms and vessel corrections

The [complete74-candidate input](../../integrations/envhd/production/field-bespoke-wheel-furnishings-reviewed-selection.json) retains all71 prior records exactly and adds three corrected paintings for Lohan cut636's circular wall fitting and unequal red hanging forms, and Crystal Palace cut370's small tabletop vessel group. The red forms' precise identity is unresolved; the earlier lamps shorthand is not an established interpretation. Original/raw/repaired crops and static source/EnvHD context were inspected. Both Lohan targets are hidden by a large map in the all-layer static composite, so script visibility and adjacent finish remain unresolved. Fine source divisions, patch grouping, highlights and construction need independent review.

[Five held versions](../../integrations/envhd/production/field-bespoke-wheel-furnishings-held-drafts.json) preserve earlier material/construction overinterpretation and both Fletz furnishing attempts. Fletz1 fails unchanged coverage. Fletz2 passes structural repair but adds strong scratched/rippled relief and bright support edges; it remains held. The original includes small red marks at the lower-right and lower edge, so these are not an invented red-object finding. Eight generated versions produced seven repaired variants; all original source gates remain unchanged.

Fresh source preflight reproduced all74 candidate recipes. Thirty-one candidates now await independent review; three existing independent revisions remain. Fifty-eight unique unselected custom source keys are a subset of236 field holds. Runtime4,926 fields/40paintings and237 known static remaining images are unchanged. See the [readiness receipt](../../integrations/envhd/production/field-wheel-furnishings-authoring-readiness.json). Original/reference/layout/comparison pixels remain private; this authoring increment is not a runtime or download activation.


## Recorded generation4 publication checkpoint

Generation4/source `5d3db5c84174f2936fe7256123a11f6bb640760a` publishes4,925 selected fields/39 painted fields and34 painted battle masters. Accepted main `6612a533307e5f066bc46e7870d658f8add42752` selects4,926/40 and47 respectively; one table and13 Giganto paintings await the next coordinated download. The earlier0af publication receipt remains historical. Native and final artistic acceptance remain separate.


## Small furnishing and tableware batch

The [complete77-candidate input](../../integrations/envhd/production/field-bespoke-tableware-reviewed-selection.json) retains all74 prior records exactly and adds Twin Castle cut667's pale bowl-like furnishing, Crystal Palace cut369's small curved brown fitting, and the corrected Lohan cut146 table/item group. Original/raw/repaired crops and static source/EnvHD placement were inspected. Exact small object identities remain unresolved. Body/feet/patch boundaries, brown support construction, table grain and tiny item grouping require independent original review.

[Seven held versions](../../integrations/envhd/production/field-bespoke-tableware-held-drafts.json) preserve both Indels full-crop coverage failures, the Bale first coverage failure, and four structurally repaired drafts with excessive contrast, grain, highlights or item/construction definition. The Fueno and Bale corrections still overstate the softly shaded source materials. Indels needs a correction of its separately placed furnishing components rather than another unconstrained full-crop attempt. Ten generated versions produced seven repaired variants; source geometry/coverage/alpha/visible-black gates are unchanged.

Fresh source preflight reproduced all77 candidate recipes. Thirty-four authoring candidates await independent review; three existing independent revisions remain. Sixty-four unique unselected custom source keys are a subset of236 field holds. Runtime4,926 fields/40paintings and237 known static remaining images are unchanged. See the [readiness receipt](../../integrations/envhd/production/field-tableware-authoring-readiness.json). Original/reference/layout/comparison pixels remain private. This increment records custom artwork for review and does not activate it in the mod or installer.


## Donau architecture corrections

The [complete80-candidate input](../../integrations/envhd/production/field-bespoke-donau-architecture-reviewed-selection.json) retains all77 prior records exactly and adds corrected paintings for Donau cut244's hall column/arch and wall tablet, and cut241's larger curved exterior wall fragment. Original crops, both raw/repaired versions where available and static source/EnvHD placement were inspected. Fine column lobes/bands, tablet interior motif, exterior opening boundaries and sparse surface marks require independent original review. Static assembly shows transitions with neighboring neural layers; script state/depth/reachability and scene-wide finish remain unresolved. No known writing or object identity is asserted for ambiguous marks.

[Nine held versions](../../integrations/envhd/production/field-bespoke-donau-architecture-held-drafts.json) retain all six first attempts and three corrected variants. Hall1 changed the86:84 source canvas to a square and fails the unchanged aspect gate; no repaired output exists. The other five1s pass structural repair but overinterpret material, ornament or window detail. The corrected wall/foliage, narrow hall support and narrow exterior fragment still impose loop-like motifs/openings, raised gold/olive relief or a framed angular window and remain held. Twelve generated versions produced11 repaired variants; all pinned gates remain unchanged.

Fresh source preflight reproduced all80 candidate recipes. Thirty-seven authoring candidates await independent review; three existing independent revisions remain. Seventy unique unselected custom source keys are a subset of236 field holds. Runtime4,926 fields/40paintings and237 known static remaining images are unchanged. See the [readiness receipt](../../integrations/envhd/production/field-donau-architecture-authoring-readiness.json). Original/reference/layout/comparison pixels remain private; this increment records custom artwork for review without mod or installer activation.

## Two Donau development selections

[Independent original-reference review](../../integrations/envhd/production/field-independent-donau-two-review.json) clears the Donau column and arch (cut244 foreground8) and larger exterior wall (cut241 foreground0) for development selection. Their source silhouettes, openings and main color landmarks remain anchored. Fine surface detail is interpretive. The tablet remains excluded until its dense chips and pits are softened and independently reviewed.

The [complete42-painting input](../../integrations/envhd/production/field-bespoke-donau-two-independent-reviewed-selection.json) preserves main's prior40 candidate records exactly. All42 recipes reproduce from current original sources. Runtime selection preserves all4926 prior records and every protected Skurfa cut. The [readiness receipt](../../integrations/envhd/production/field-donau-two-selection-readiness.json) binds the selection, independent review and86 focused checks. Only these two custom input/output images, their recipes/review, runtime resources and coverage metadata are included. Unfinished authoring remains on the ongoing branch. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending; ROOT coordinates the current installer publication.

The EnvHD JAR and four field packs built successfully from frozen committed source. Strict packaging verification matched all4928 selected field images and5314 JAR resources by name, size and SHA256. Env1HD and Env3HD are byte-identical to the prior accepted package; Env2HD and Env4HD each contain one additional painting. Battle runtime resources remain exact to accepted main. The package receipt records all five output hashes. This verifies the EnvHD subset, with full installer/carrier closure owned by ROOT.


## Historical Donau integration authoring checkpoint

At that checkpoint, accepted main selected4,928 fields/42paintings. The complete80 historical candidate records and all held-version histories were retained byte-exact on this ongoing branch. The independent queue then had34 pending candidates and4 revisions after the two Donau selections and tablet material disposition;68 unique unselected custom source keys were within234 field holds. Historical authoring receipts keep their original checkpoint counts. The separate Twin Castle batch was private then; the following checkpoint records its public custom candidates and current queue counts.


## Twin Castle cool-light hangings

The [complete83-candidate input](../../integrations/envhd/production/field-bespoke-twin-hangings-reviewed-selection.json) preserves all80 earlier records exactly and adds three cut228 paintings: two hanging panels and their rails/fittings (foreground0, corrected version2), three panels with crossed supports (foreground8, corrected version2), and the cropped cloth/rail fragment (foreground4, version1). Original native4x, repaired outputs and static EnvHD placements were compared. Corrections reduce the first fitting's invented cage and the larger central symbol. Fine woven markings, seam contrast, small joints and fitting highlights remain interpretive and independently pending. No known heraldic or text identity is asserted.

[Eight held versions](../../integrations/envhd/production/field-bespoke-twin-hangings-held-drafts.json) retain five first attempts and three warm-light corrections across five sources. The warm narrow crop fails the unchanged source coverage gate twice; neither has a repaired output. Other holds retain overly regular pale motifs, newly coherent cloth patterns or overdefined cage/joint construction. Eleven generated inputs yield9 repaired variants. No guard is waived, no Skurfa art regenerated and no field activated.

Fresh original-source preflight reproduced all83 recipes. All42 selected painting records, all runtime bytes and prior custom/held histories remain exact. At this authoring checkpoint, the independent queue was37 pending/4 revisions, with74 unselected custom source keys within234 field holds. Runtime4,928 fields/42paintings and235 known static remaining images are unchanged. See the [readiness receipt](../../integrations/envhd/production/field-twin-hangings-authoring-readiness.json). Original/reference/layout/comparison pixels remain private. Native/script/GPU/depth, physical Deck, campaign, neighboring layer finish and final art acceptance remain separate work.


## Independent Twin Castle hanging dispositions

The [independent three-crop review](../../integrations/envhd/production/field-independent-twin-hangings-review.json) clears foreground4 for development: its cropped cloth angle, unequal small marks, nested curves, rails, finial and curved lower wire preserve native relationships. Foreground0 needs its crisp cross-like marks softened into unequal native strokes. Foreground8 needs repeated six-point stars reduced to unresolved flecks and its new round fitting bolt/ring details reduced. Both remain held for revision. Source-integrity review passed; its historical-checkpoint wording finding is fixed.

All83 authoring recipes and prior versions remain exact. The current queue is34 independent-pending,6 independent revisions and1 development-cleared/unselected candidate. Runtime4,928 fields/42 paintings,234 field holds and235 known static remaining images remain unchanged. Coordinator parallel review, clean selection integration and native/final acceptance are separate.


## Held Twin Castle local corrections

[Five third-version corrections](../../integrations/envhd/production/field-bespoke-twin-hangings-local-corrections-held-drafts.json) retain four repaired local motif/material holds and one repeated warm narrow coverage failure. Fitting cage/window details are reduced, but the three-panel cloth still has regular star-like marks, the warm pair overdefines the cloth and lower illumination, and the cool pair makes the side strokes too faint. The warm narrow source fails the unchanged coverage gate for a third time; it has no repaired output. A different local source-aligned repair is needed before these held motifs can advance.

All83 candidate recipes and all prior versions remain exact. No new source keys, candidates or runtime selections are added. The separate unanimously cleared foreground4 integration proceeds independently. Original/reference/layout/comparison pixels remain private; all public PNGs are custom generated or repaired art.


## Twin Castle clipped hanging development selection

The [independent review](../../integrations/envhd/production/field-independent-twin-fragment-review.json) records both reviewers clearing cut228 foreground4: its clipped cloth angle, unequal small strokes, nested curves, rails, finial and lower wire preserve source relationships. The two-panel crop remains held due to reviewer disagreement on crisp markings; the three-panel crop needs repeated stars and fitting details softened. Their authoring records and all held versions remain on the ongoing branch.

The [complete43-painting selection](../../integrations/envhd/production/field-bespoke-twin-fragment-independent-reviewed-selection.json) preserves all42 prior records exactly. All43 recipes reproduce from current originals; runtime retains all4,928 previous records and every protected Skurfa cut. The [readiness receipt](../../integrations/envhd/production/field-twin-fragment-selection-readiness.json) binds the one-image change, original-fidelity reviews and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending; ROOT coordinates the current installer publication.


## Ongoing authoring after Twin fragment integration

Accepted main selects 4,929 fields with 43 bespoke paintings. All83 historical candidate records and every earlier custom/held version remain byte-exact on this branch. The current queue is34 independent-pending/6 revisions, with73 unique unselected custom source keys within233 field holds. Historical authoring/review receipts keep their original counts; the one unanimously cleared fragment is tracked by the accepted main selection separately. The remaining233 fields and one terrain repair total234 known static images. Native/state/campaign/final acceptance remains pending.


## Held organic and scenery batch

[Eight first-version custom paintings](../../integrations/envhd/production/field-bespoke-organic-scenery-held-drafts.json) cover Prison Island ribs, Moon radial scenery and layered planes, Aglis fittings and a Mayfil support. Seven pass the unchanged pinned cutout geometry/coverage/alpha/black repair; the Moon three-plane source fails coverage and has no repaired output. Material relief, hard ridge contrast and ambiguous pale marks need source-faithful correction or independent review. All eight remain unselected.

Custom inputs, repaired outputs, prompts, hashes and source identities are versioned. Original crops, scene contexts and comparison/placement diagnostics remain private. Complete83 prior recipes and selected4,929 fields stay exact;81 unique unselected custom keys remain a subset of233 field holds. The separate five-cleared scenery main increment proceeds independently.


## Five independently reviewed scenery selections

The [independent eight-crop review](../../integrations/envhd/production/field-independent-scenery-eight-review.json) clears the Shrine arch, Shrine left V-finial fragment, Moon radial layer, Queen Fury open crate and Ulara rim for development selection. The Bale roof corner, Undersea coral rim and Fueno support require correction and remain excluded. Actual original/reference crops and static scene placements were inspected; source integrity and replay passed.

The [complete48-painting selection](../../integrations/envhd/production/field-bespoke-scenery-five-independent-reviewed-selection.json) preserves every prior43 recipe exactly. All48 recipes replay from current originals; all4,929 previous runtime records and protected Skurfa cuts remain exact. The [readiness receipt](../../integrations/envhd/production/field-scenery-five-selection-readiness.json) binds the five-image change and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending. ROOT coordinates the current installer publication; the prior publication ledger is a historical observed snapshot.


## Ongoing authoring after scenery integration

Accepted main selects4,934 fields with48 paintings; all83 historical recipes and every prior custom/held byte remain exact. Current queue26 independent-pending/9 reviewed revisions and76 unique unselected custom source keys within228field holds. Eight newly authored organic/scenic versions remain held separately. Known static remainder229 includes228fields and one terrain repair; animation/props denominators remain unresolved. Native/state/campaign/final acceptance is pending.


## Held local source-feature prototypes

[Three local restoration prototypes](../../integrations/envhd/production/field-bespoke-source-feature-three-held-drafts.json) reuse existing public Aglis and Twin Castle paintings. The authoring helper restores original ambiguous marking shapes inside explicit source-coordinate polygons, retaining exact parent cutout pixels outside them. The original aspect/coverage gate, source alpha and visible black remain unchanged; eight focused behavior checks pass. Actual comparisons show soft restored patches and unfinished material transitions, so all three remain held with no runtime selection or new unique image count. Custom outputs and recipes are versioned; original images and comparison diagnostics stay private.


## Four independently reviewed palace selections

The [independent eight-crop review](../../integrations/envhd/production/field-independent-palace-eight-review.json) clears the Crystal Palace cut368 panel rail, cut369 rising rail, cut367 baluster/support and Twin Castle cut221 ornamented rim for development selection. The Crystal Palace lower post, curved-edge fitting and descending rail, plus Twin Castle night fittings require correction and remain excluded. Actual original/reference crops and static scene placements were inspected; source integrity and replay passed.

The [complete52-painting selection](../../integrations/envhd/production/field-bespoke-palace-four-independent-reviewed-selection.json) preserves every prior48 recipe exactly. All52 recipes replay from current originals; all4,934 previous runtime records and protected Skurfa cuts remain exact. The [readiness receipt](../../integrations/envhd/production/field-palace-four-selection-readiness.json) binds the four-image change and86 focused checks. Native/scripted state, physical Deck, campaign and final artistic acceptance remain pending. ROOT coordinates the current installer publication; the prior publication ledger is a historical observed snapshot.


## Ongoing authoring after palace integration

Accepted main selects4,938 fields with52 paintings; all83 historical recipes and every prior custom/held byte remain preserved. Current queue18 independent-pending/13 reviewed revisions and72 unique unselected custom source keys within224field holds. Organic/scenic and local-feature prototypes remain held separately. Known static remainder225 includes224fields and one terrain repair; animation/props denominators remain unresolved. Native/state/campaign/final acceptance is pending.


## Held source-feature blend refinements

[Three built-in edit refinements](../../integrations/envhd/production/field-bespoke-feature-blend-three-held-drafts.json) retain every earlier prototype. Two pass the unchanged cutout repair; one fails the original aspect guard and retains raw custom input only. Aglis support mark/exposure fidelity remains pending; Twin Castle introduces symmetrical stars again and needs correction. All remain held, adding no selected or unique source images. The independently cleared sixteen-image integration proceeds separately. Custom inputs, outputs and prompts are versioned; original crops and diagnostics stay private.


## Sixteen independently reviewed scenery and furnishing selections

The [eighteen-crop review](../../integrations/envhd/production/field-independent-final-eighteen-review.json) clears sixteen source-faithful furnishings and architectural crops across Queen Fury, Black Castle, Mayfil, Twin Castle, Crystal Palace, Wingly Forest, Forbidden Land, Ulara and Lohan. The Queen Fury cages need finer original lattice spacing; the Shrine patch needs flat low-relief shading. Those two remain excluded. Two cleared Lohan source layers are fully occluded in the supplied all-layer composites; script-state visibility remains unverified.

The [complete68-painting selection](../../integrations/envhd/production/field-bespoke-scenery-sixteen-independent-reviewed-selection.json) preserves all prior52 recipes and4,938 runtime records, including protected Skurfa cuts. All68 recipes replay from current originals and86 focused checks pass. The [readiness receipt](../../integrations/envhd/production/field-scenery-sixteen-selection-readiness.json) binds this increment; ROOT coordinates installer/current-carrier publication. Original diagnostic pixels remain private; no helper/engine/UI/workflow change is included. Native/script/GPU, physical Deck, campaign and final artistic acceptance remain pending.


## Ongoing authoring after sixteen scenery selections

Accepted main selects4,954 fields with68 paintings; all83 historical recipes and every prior custom/held version remain preserved. Current canonical queue0 independent-pending/15 reviewed revisions;56 unique unselected custom source keys within208field holds. Organic/scenic, local-feature and feature-blend drafts remain held separately. Known static remainder209 includes208fields and one terrain repair; animation/props denominators remain unresolved. Native/state/campaign/final acceptance is pending.


## Held precise corrections after sixteen scenery selections

[Six targeted built-in edits](../../integrations/envhd/production/field-bespoke-precise-six-held-drafts.json) address fine cage lattice, flat Shrine wall shading, palace-post construction, curved rail fittings and subdued balcony details. Five pass unchanged cutout repair. One fails the original silhouette coverage check; its raw input stays failed and a separate bounded Python component-transfer proposal preserves the earlier canvas and alpha. These are held review candidates, adding no selected or unique source keys. Every prior recipe/version remains; custom paintings and prompts are public while original/reference/placement pixels remain private. Native/script/Deck/campaign/final acceptance remains pending.


## Three corrected wall and rail candidates cleared

The [fresh six-source review](../../integrations/envhd/production/field-independent-precise-six-review.json) clears the flat Shrine wall and two rounded Crystal Palace rail fittings. Cargo braces, palace-post coverage/transfer seams and balcony illumination/details require further repair. The [prospective71-painting selection](../../integrations/envhd/production/field-bespoke-precise-three-independent-reviewed-selection.json) keeps all68 earlier selected recipes exact and uses explicit version2 paths for the three corrected candidates, retaining every original version1 recipe and file. Current runtime remains4,954/68 until the next checked integration. Canonical source queue68selected/12revision/3clear-awaiting-main/0pending. The [mean-exposure balcony prototype](../../integrations/envhd/production/field-bespoke-balcony-exposure-held-draft.json) remains held because average colour matching does not remove the identified fitting and edge defects.


## Three corrected wall and rail paintings selected

The [six-source review](../../integrations/envhd/production/field-independent-precise-six-review.json) clears the flat Shrine wall and two Palace rail fittings with the original rounded forms. Cargo lattice, palace-post coverage/transfer seams and balcony fitting/illumination issues remain held. The [complete 71-painting selection](../../integrations/envhd/production/field-bespoke-precise-three-independent-reviewed-selection.json) retains all prior 68 recipes and uses version 2 candidate filenames for the corrections, preserving earlier artwork. Runtime resource names and the single EnvHD switch remain unchanged.

All 71 paintings replay from fresh originals and 90 focused checks pass. The [readiness receipt](../../integrations/envhd/production/field-precise-three-selection-readiness.json) binds this increment; ROOT coordinates installer publication. Protected Skurfa artwork and all 4,954 prior runtime assets remain exact. Native, script-state, Deck, campaign and final artistic acceptance remain pending.


## Existing cargo, balcony and post revisions

[Three further versions](../../integrations/envhd/production/field-bespoke-source-constrained-three-held-drafts.json) address existing reviewed revision sources. Two builtin edits use the original crops and scenes: cargo mesh passes the unchanged cutout checks, while the balcony footprint still fails and has no repaired output. The palace post uses a bounded Python alignment of earlier custom gold and teal components with aggregate exposure matching; source alpha and pixels outside the component remain exact. Its visual quality and selection-helper support remain unresolved. No extra post generation call was made.

All versions remain held for review. Current selection stays 4,957 fields / 71 paintings, with 206 known static holds, 53 unique unselected custom keys and 12 reviewed canonical revision sources. Original crops, exposure guide and comparisons remain private.


## Existing balcony painting registered

A [separate balcony variant](../../integrations/envhd/production/field-bespoke-balcony-registered-held-draft.json) reuses the same generated painting. The existing perspective-registration helper maps explicitly recorded painted silhouette endpoints to original endpoints without applying original alpha or RGB. The unchanged cutout recipe then passes. The standalone input coverage failure stays recorded; this is a separate operation and version, still held for visual review. No new generation, helper or guard changes were needed. Current runtime/counts remain unchanged.


## Corrected Twin Castle balcony selected

The [independent review](../../integrations/envhd/production/field-independent-balcony-registration-review.json) clears the corrected curved night balcony: muted fittings, subdued cap and diffuse dark front. Existing pinned perspective registration aligns the painted curve before unchanged cutout repair. The original standalone coverage failure remains recorded in authoring history; no guard or helper was changed.

The [complete72-painting selection](../../integrations/envhd/production/field-bespoke-balcony-independent-reviewed-selection.json) preserves all71 prior recipes and4957 runtime records. The [readiness receipt](../../integrations/envhd/production/field-balcony-selection-readiness.json) tracks source replay and package verification. One EnvHD switch controls all four download packs. Native/script/GPU, campaign, physical Deck and final artistic acceptance remain pending.


## Palace post component replay prepared

The [prospective73-painting manifest](../../integrations/envhd/production/field-bespoke-post-components-reviewed-selection.json) retains the current72 recipes and adds the previously independently cleared Palace post prototype. A new bounded helper repairs the old generated base with the unchanged cutout, transfers two components from an existing generated correction, matches source class mean exposure, and preserves all base alpha and every pixel outside the explicit native rectangle. The composite input/output are byte-identical to the reviewed prototype. Actual original RGB pixels are not transferred into the painting.

The [authoring receipt](../../integrations/envhd/production/field-post-components-authoring-readiness.json) binds both generated inputs, component operations and tests.11 helper tests and59 authoring preflight tests pass; all73 prospective recipes replay from current sources. Runtime remains4958fields/72paintings. New helper/code review and checked integration are pending; the earlier failed standalone correction remains failed. No generation calls were made. Native, campaign, physical Deck and final art acceptance remain pending.


The [post helper/selection review](../../integrations/envhd/production/field-independent-post-components-review.json) is now CLEAR on both axes at source21a7. Its composite size is corrected to280x456 and enforced by a regression check. All73 candidate records remain exact after adding this review metadata; the source-preflight receipt retains the original manifest hash and explicitly records this carry. Runtime remains72 paintings until checked integration/package verification. Canonical queue:72selected,10revision,one cleared awaiting integration.


## Dynamic and prop inventory snapshots reconciled

The [curated checkpoint](../../integrations/envhd/production/environment-dynamic-prop-inventory-checkpoint.json) keeps units separate: one authored shared water coefficient family serves eight continent parts with296 textured faces and eight palette rows; it is not eight separate restoration jobs or16,512 animation-frame jobs. The candidate remains unselected pending4x consumer/state/ownership support.

The received field-material snapshot has135,279 material bindings across7,664 contexts. Ownership is unresolved for7,652 contexts;12 are source-proved CharHD. Its5,984 repeated inspection groups are priorities, not EnvHD image jobs. The snapshot explains the earlier25 absent texture hints as24 excluded outside-MRG routes plus one included sentinel, and608 absent script hints as567 present alias targets plus41 excluded outside-MRG routes. These route hints are not missing runtime assets. Mutable alias ordering, actual controller/script branches, inherited uploads and exact sampling coverage remain open. Both whole dynamic and owned-prop image denominators remain unknown. This records the retained source snapshots rather than claiming a fresh whole-game census.


## Crystal Palace post correction selected

The [independent review](../../integrations/envhd/production/field-independent-post-components-review.json) clears an exact replay of the already original-reviewed Palace post correction. A bounded component helper transfers the existing custom gold shaft and continuous teal base into the earlier custom rail painting. Only the explicit lower-right source region changes; the original alpha and all outside pixels are exact. Both generated inputs and prompts are retained, with distinct composite provenance. The rejected standalone full-frame correction remains rejected. No new image generation was needed.

The [complete73-painting selection](../../integrations/envhd/production/field-bespoke-post-components-reviewed-selection.json) preserves all72 prior recipes and4958 runtime records. The [readiness receipt](../../integrations/envhd/production/field-post-components-selection-readiness.json) binds behavioral checks, fresh source replay and package verification. One EnvHD switch controls all four download packs. Native/script/GPU, campaign, physical Deck and final artistic acceptance remain pending.


The [independent inventory review](../../integrations/envhd/production/environment-dynamic-prop-inventory-independent-review.json) is CLEAR on both axes at source3b199. The inventory checkpoint retains its historical4958/72 baseline; accepted current selection is4959/73. Unknown dynamic and owned-prop image denominators remain explicit.


## Cargo fitting localized trials

The [held repair worklist](../../integrations/envhd/production/field-bespoke-cargo-fitting-held-drafts.json) preserves three component reuse trials that original-art review rejected. They leave the corrected cage mesh exact but retain unsupported mechanisms or erase the fitting marks. A targeted full-frame edit failed the unchanged aspect gate and is not used. A separate builtin-generated15x16 patch preserves the two irregular dark marks and pale lower feature more closely; unchanged cutout repair and an explicit local transfer retain every outside pixel and the original alpha. Fresh visual review and reproducible helper/code support are still required. All versions remain held, with4959fields/73paintings selected and204knownstatic images left. Original references and comparisons remain private.


## Cargo isolated fitting art cleared, recipe review pending

[Original-art review](../../integrations/envhd/production/field-cargo-local-patch-art-review.json) clears the isolated fitting correction for development intent: two irregular dark marks, soft tan mass and a pale lower feature survive without invented cup, slots or fasteners. The mesh outside the source rectangle remains exact. The [prospective74 recipe](../../integrations/envhd/production/field-bespoke-cargo-local-patch-reviewed-selection.json) retains all73 selected records and uses separate original builtin base/patch inputs, exact prompts and a pinned deterministic transfer. Nine local patch fixtures and68 authoring preflight fixtures pass. Independent helper/code/provenance review and integration remain required; runtime counts are unchanged4959/73 with204known static holds. Native gameplay, physical Deck and final visual acceptance remain pending.

Independent Spec and Standards review at2534925f both clear the Cargo local-patch helper and provenance with zero findings. The exact review/output/recipe identities are bound in the [public receipt](../../integrations/envhd/production/field-independent-cargo-local-patch-review.json). Runtime integration remains pending; counts unchanged.


## Queen Fury cargo fitting correction selected

The [original-art review](../../integrations/envhd/production/field-cargo-local-patch-art-review.json) clears the fitting’s two irregular dark marks, soft tan mass and pale lower feature. The [independent helper/provenance review](../../integrations/envhd/production/field-independent-cargo-local-patch-review.json) clears a source-bound local patch transfer. The earlier reviewed cargo mesh stays exact outside native rectangle[83,64,98,80]. Each builtin base/patch input independently passes unchanged cutout coverage, aspect, alpha and visible-black guards; both inputs and exact prompts are retained with distinct composite provenance. Three reuse trials and a whole-frame aspect failure remain rejected on the public authoring branch.

The [complete74-painting selection](../../integrations/envhd/production/field-bespoke-cargo-local-patch-reviewed-selection.json) preserves all73 prior recipes and4959runtime records. The [readiness receipt](../../integrations/envhd/production/field-cargo-local-patch-selection-readiness.json) binds behavioral checks, fresh source replay and package verification. Inherited battle metadata pins/counts are refreshed to the accepted base’s44presentations plus12paintings; no battle artwork changes. Publication counts in the historical ledger retain their recorded generation4 snapshot and do not claim a fresh download census. One EnvHD switch controls all four packs. Animated/owned-prop image denominators, native/script/GPU, campaign, physical Deck and final artistic acceptance remain pending.

Accepted main5fbda0d integrates the Cargo correction afterbothfinalaxisCLEAR0 andexact5packageverification. Historical precedingtrial/reviewpending statements record their creationstage; currentselection4960/74 and203static.


## Three existing landmark revisions

The [held worklist](../../integrations/envhd/production/field-bespoke-landmarks-three-held-drafts.json) retains three exact builtin-generated inputs and two structurally repaired outputs. Fresh original crops and complete scenes control Rouge’s lattice connections, the Phantom Ship’s lower column band and pale weathering, and Bale’s dim roof corner. Rouge and the column pass the unchanged source cutout gates but require independent art review. Bale’s coverage failure remains rejected; no gate waiver or repaired output is created. These are repeated existing source keys, not new image jobs. All runtime selections remain4960/74 with203knownstatic holds; original references and comparisons remain private.

Independent original-art review subsequently returned REVISE for both structurally repaired drafts: Rouge still has fragmented connecting rails; Phantom Ship retains overly bright, connected weathering despite its improved lower band. Bale remains structurally held without aesthetic review. All five custom trial PNGs and exact prompts remain preserved; no runtime selection changed.
