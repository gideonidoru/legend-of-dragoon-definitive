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
| Battle floors, walls and scenery | 900 bindings across 89 nonempty stages | 537 bindings (24 pilot + 513 new), 506 new selected masters | 363 native bindings: 12 revision holds, 350 animation/context bindings and 1 incomplete palette |
| Field backgrounds/foregrounds | 650 source configurations; 5,528 unique visible images | 38 existing Skurfa packs; 4,892 EnvHD development images (4,886 neural and six painted) | All 5,162 generated images reviewed; 270 remaining bespoke holds and zero unreviewed images; 3 uniform sources native; 605 configurations have at least one selected layer, not complete scene coverage; script/route/visual validation pending |
| Field props and scene overlays | Mixed pool: 481 field-object page hashes and 400 overlay page hashes | EnvHD 0 | Complete consumer/ownership/material census first; these are **not** 881 EnvHD jobs |
| Animated environment surfaces | Exact frame/palette denominator unresolved; eight continent animated parts and 350 held battle bindings known | 0 HD animation families | Water, scrolling surfaces, scenery frames/palettes, mapping and validation |

Do not sum these rows: an image, source configuration, texture page and runtime
material binding are different units. The field image denominator is independently decoded; the overall image
denominator still requires prop/overlay ownership and animation-family census.
No total-image completion percentage is claimed. Each
row's exact remaining work and intentional exclusions are in the JSON ledger.
All custom native/final-quality acceptance counts remain zero.

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
original neural holds. Six separately reviewed painted replacements clear six
of those holds for development selection, leaving 270 held sources and zero
unreviewed images. The current candidate selects 4,892 public custom PNGs; its
painted inputs, prompts and source-bound repair receipts are also versioned.
Original comparisons remain private, and held/uniform sources retain native art.

The published maintenance installer remains on the historical source-476 checkpoint
of 2,723 selected field images until coordinated integration and publication.
At that checkpoint 2,840 images had been reviewed, 117 were held and 2,322
awaited review. Those are historical counters, not the current candidate's status.
Archived generation/repair/review controls preserve their checkpoint counters;
`field-runtime-artwork.json` records the current 4,892-image selection. Field source
identities and selected visibility classes are checked independently of visual
acceptance; all 72 new battle atlases pass source upload,
palette, STP, black, padding and 36,584 original textured-face UV checks. Native
scene behavior and final quality remain unaccepted.
