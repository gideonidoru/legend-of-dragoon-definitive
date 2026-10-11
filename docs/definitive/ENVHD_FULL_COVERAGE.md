# Full EnvHD coverage

EnvHD is not complete. Generated candidates, selected runtime artwork and native
visual acceptance are different milestones. The machine-readable worklist is
[`full-coverage.json`](../../integrations/envhd/production/full-coverage.json).
It covers every environment category and exposes unresolved denominators instead
of presenting a batch as campaign completion. CharHD, UIHD and FxHD are separately
owned. Custom deliverables are public; originals and private diagnostics are not.

| Environment category | Known source denominator | Selected runtime HD | Remaining |
| --- | --- | --- | --- |
| Battle panoramas | 74 source/header variants, 70 nonuniform masters | 73 bindings / 70 masters | Uniform black intentionally native; gameplay and final quality pending |
| Location landscapes | 38 source images | 38 | Gameplay and final quality pending |
| World parchment backdrop | 1 source image | 1 | Gameplay and final quality pending |
| Static continent terrain | 366 material bindings | 364 bindings / 292 masters | 1 uniform native, 1 wrapped material held; gameplay and final quality pending |
| Battle floors, walls and scenery | 900 bindings across 89 nonempty stages | 549 bindings (24 pilot + 525 new), 518 new selected masters | All identified static candidate masters reviewed; 351 native bindings: 350 animation/context and one incomplete palette |
| Field backgrounds/foregrounds | 650 source configurations; 5,528 unique visible images | 38 existing Skurfa packs; 4,917 EnvHD development images (4,886 neural and thirty-one painted) | All 5,162 generated images reviewed; 245 remaining bespoke holds and zero unreviewed images; 3 uniform sources native; 605 configurations have at least one selected layer, not complete scene coverage; script/route/visual validation pending |
| Field props and scene overlays | Mixed pool: 481 field-object page hashes and 400 overlay page hashes | EnvHD 0 | Complete consumer/ownership/material census first; these are **not** 881 EnvHD jobs |
| Animated environment surfaces | Exact frame/palette denominator unresolved; eight continent animated parts and 350 held battle bindings known | 0 HD animation families | Water, scrolling surfaces, scenery frames/palettes, mapping and validation |

Do not sum these rows: an image, source configuration, texture page and runtime
material binding are different units. The field image denominator is independently decoded; the overall image
denominator still requires prop/overlay ownership and animation-family census.
No total-image completion percentage is claimed. Each
row's exact remaining work and intentional exclusions are in the JSON ledger.
All custom native/final-quality acceptance counts remain zero.

The authoring branch selects twelve separately reviewed painted battle replacements:
three Flanvel Tower materials, two stage-30 decorative panels and a stage-38
static flame-shaped surface, plus a Shirley's Shrine plaque, stage-17 floor
fragments, stage-45 aqua panel, stage-69 circular ornament and two Moon materials.
Their seven source-bound painted manifests are
recorded in the current battle runtime ledger. Fresh native CPU/source checks
and individual comparisons remain separate from gameplay acceptance. The
previous 506-master/513-binding runtime ledger is archived byte for byte as
`battle-material-runtime-artwork-source-0af4f997.json`; the earlier six-painting
selection is archived as `battle-material-runtime-artwork-source-a6a0b62f.json`.
The published `definitive-alpha-2026-10-11-envhd` package source `0af4f997` and
historical main entrypoint metadata `0b8df09a` exclude these twelve paintings. Published
battle selection remains 537 total bindings until the next coordinated integration.
Known static repair work on this authoring branch is 245 fields plus one terrain
mapping hold. The [Tiberoa investigation](ENVHD_TIBEROA_MAPPING.md) records the
three unsigned-UV faces and complete upload evidence; it clears no hold. This
does not count unresolved animation/prop families as completed.

The field ownership audit freshly verified all 1,044 source folders and all
protected Skurfa background/foreground hashes. Existing resources are unchanged:
38 source-identical packs protect 87 cut/period mappings. Skurfa currently registers
43 disk/cut bindings. The 87 protected mappings are a source-reuse worklist, not
87 proven runtime substitutions. EnvHD must reuse those resources where needed;
it must not redraw Skurfa scenes or change their branding/attribution.

The broad field census includes available source configurations outside the usual
rendered-field table. Keep them in the worklist until source reachability is
resolved. Its 771 unsuccessful records include unused/sentinel routes; they require
classification, not 771 invented backgrounds. Foreground counts include zero-area
slots, movable/hideable layers, translucency and alternate frames. A single
composited master copied into every mask would bake hidden/moving foregrounds into
the backdrop. Preserve each source layer's content and state when restoring them.

The owner-requested default installation selects the reviewed development subset
under one EnvHD mod. Four field resource ZIPs accompany its JAR without new mod
IDs or installer toggles. Exact-source package/publication checks control delivery.
Skurfa's pinned resources and notices remain unchanged. A 44-cut exclusion protects
its scenes regardless of listener order; no selected EnvHD configuration overlaps.

Complete delivery requires accounting for every source, finishing all adapters,
reviewing each selected output, passing package/source checks, and validating
native gameplay and Steam Deck rendering. Headless builds, GPU probes and review
boards alone do not satisfy that final gate.

The complete field pixel worklist is `field-source-worklist.json`: 650 configurations,
5,560 visible bindings, 5,528 distinct visible images, 124 empty slots (123
foregrounds and one background), and seven native missing-texture slots. Actual
Java TIM/palette/environment decoders independently agree with every image hash
and canvas. Skurfa protects 363 unique source images; 5,165 are unowned, of which
three are uniform and 5,162 require pictorial restoration. No foreground can be
derived from a backdrop by exact visible-pixel equality. These figures describe
source work, not completed or installed artwork.

All 5,162 field candidates completed private generation and Python repair; 342
backgrounds received source-color perimeter guards. All 5,162 generated images
have been individually reviewed: 4,886 neural development baselines and 276
original neural holds. Thirty-one selected painted replacements clear thirty-one
of those holds for development selection, leaving 245 held sources and zero
unreviewed images. The current candidate selects 4,917 public custom PNGs; its
painted inputs, prompts and source-bound repair receipts are also versioned.
Original comparisons remain private, and held/uniform sources retain native art.

The published combined installer includes 4,892 selected development field images.
This authoring branch adds twenty-five selected fields: the earlier Queen Fury/Kazas fittings,
eight organic/furnishing, eight weave/wood/foliage and seven selected bedding/upholstery repairs. Selection is 4,917,
awaiting coordinated publication. The complete thirty-one-painting selection input is
`field-bespoke-bedding-reviewed-selection.json`; prior foliage, fittings, organic and weave manifests remain
byte-identical. Source-719, source-464, source-e050, source-e7eb and source-260c field controls preserve earlier selections.
The previous maintenance installer used the historical source-476 checkpoint of 2,723.
At that checkpoint 2,840 images had been reviewed, 117 were held and 2,322
awaited review. Those are historical counters, not the current candidate's status.
Archived generation/repair/review controls preserve their checkpoint counters;
`field-runtime-artwork.json` records the current 4,917-image authoring selection. Field source
identities and selected visibility classes are checked independently of visual
acceptance; all 72 new battle atlases pass source upload,
palette, STP, black, padding and 36,584 original textured-face UV checks. Native
scene behavior and final quality remain unaccepted.

The eight new field repairs restore a cut stump, three distinct chair views, two
foliage/architecture fragments, a six-bowl kitchen counter with woven basket, and a
two-flower branch. Their individual output-bound reviews record rejected drafts and
interpretive details. Fresh-source repair reproduction and source visibility checks
support development selection, not gameplay or final Skurfa-equivalent acceptance.
Generation used the built-in image tool and the existing pinned Python cutout repair.

Eight additional weave/wood/foliage repairs preserve all prior4,902 runtime records and
all sixteen prior painted candidates. Original/held/repaired images and native source
slot placements were compared individually. Moon's opaque flat-ground edge required
a separately pinned, optional Python border repair; it requires a uniform opaque native
border, excludes non-flat source features, and is independently reproduced during
painted preflight. The existing cutout and terrain algorithms are unchanged. Source
alpha/black/canvas evidence does not establish runtime depth, state visibility, route
reachability or final Skurfa-equivalent painted quality. Detailed reviews and prompt
links are in [field review progress](ENVHD_FIELD_REVIEW_PROGRESS.md).

Eight bedding/upholstery replacements preserve all previous 4,910 runtime records and
all 24 previous painted candidates. The new complete 32-record manifest independently
reproduces repaired pixels from freshly decoded source and unchanged raw tool art.
The unchanged cutout recipe preserves alpha/black classes; tiny textile motifs and
interior folds remain interpretive. The initial selection cleared eight development
source holds. Independent review subsequently withdraws the full Deningrad bed and retains seven of the eight.
Current remaining work is 245 field repairs plus the one terrain mapping hold
(246 known static repairs).
It does not complete the unresolved prop/animation inventory or native acceptance.

Independent review retains the full Deningrad bed as a held custom candidate:
its substantial dark support/shadow plane lacks established intended material/content.
Exact alpha and repaired footprint coverage do not resolve that intent gap.
`field-bespoke-bedding-reviewed-selection.json` selects 31 paintings and records
that hold, preserving all 32 original candidate records in the unchanged bedding
manifest. Runtime resources exclude the full bed and use its native source layer;
all other 4,917 records remain exact. Current selection is 4,917 (4,886 neural plus 31
painted), with 245 field holds plus one terrain hold. No recipes or thresholds change.

The prop/animation raw-page figures above are historical discovery snapshots,
not complete EnvHD material/image counts. See the [remaining discovery plan](ENVHD_REMAINING_DISCOVERY_PLAN.md)
for fresh battle dependency checks, current prop route/raw-page counts, unresolved
aliases and source-located workstreams with acceptance conditions. Current selected
artwork and its source-ledger controls remain unchanged by that discovery work.
