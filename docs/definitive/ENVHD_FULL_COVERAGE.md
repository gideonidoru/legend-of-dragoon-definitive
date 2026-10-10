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
| Static continent terrain | 366 material bindings | 364 / 292 masters on terrain draft | 1 uniform native, 1 wrapped material held; gameplay and final quality pending |
| Battle floors, walls and scenery | 900 bindings across 89 nonempty stages | 24 existing pilot bindings | 876 uncovered; 518 new candidates cover 525 static bindings but **zero new selections**; 12 masters need repair; 350 animation/context bindings and 1 incomplete palette held |
| Field backgrounds/foregrounds | 650 source configurations; 5,528 unique visible images | 38 existing Skurfa packs; EnvHD gaps 0 | All 5,162 nonuniform unowned images generated and repaired privately; 608 backgrounds and 552 foregrounds compared, eighteen bespoke holds; remaining review and integration pending; three uniform sources native; 612 configurations, protected aliases, foreground states and validation |
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

The selected panorama/world assets are on the prior integrated development
baseline. Static terrain remains on draft PR 10, and the new battle material
history is an independent draft increment stacked above it. These branch results
are not proof that the current consolidated downloadable installer includes them.
No partial or standalone EnvHD release is produced by these checkpoints.

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

The full field generation pass now produced all 5,162 unique candidates. The
public `field-generation-receipt.json` binds every candidate to its output hash,
dimensions and recorded generator identity; all PNG byte hashes were freshly
verified. It contains metadata only. The 2.5 GB of generated artwork remains in
private staging until the complete individual review and publication gate passes.
The complete Python repair pass now guards source-colored boundaries on 342
backgrounds. Its public repair receipt verifies all 5,162 repaired candidates.
Individual source/candidate comparison notes cover 608 backgrounds and 552
foregrounds: 1,142 development baselines and eighteen text/signage/pattern holds. The other
4,002 foreground reviews, foreground state handling and runtime delivery remain
open. Generation and repair completion are not field restoration completion.
