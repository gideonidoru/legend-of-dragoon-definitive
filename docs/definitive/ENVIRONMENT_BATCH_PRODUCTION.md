# Environment batch production

The owner explicitly authorized the batch pipeline and Python image repair on
2026-10-10. EnvHD now selects all 70 distinct non-uniform battle panorama masters,
covering 73 original MCQ/header variants with shared images. One verified uniform
opaque-black panorama needs no artwork. There are 123 public custom revisions;
none has native gameplay or Steam Deck visual acceptance yet.

The first executed batch produced 63 candidates: 18 border repairs of existing
layout-reviewed generative drafts and 45 source-preserving neural upscales. Visual
comparison selected 42 and held 21 because opposite-edge averaging reflected or
doubled architectural, root, cliff and other hard structures. A second batch
upscaled those 21 original sources without edge averaging. Original/output and
original/output repeat-strip review found no new structural or source-intent hold
at review-sheet scale. Existing original repeat discontinuities remain.

Seven earlier generative selections and five repaired generative selections are
retained. The other 58 selections are source-faithful neural development baselines.
Neural smoothing can simplify bark, masonry and atmospheric detail; selective
bespoke reconstruction remains necessary for the final Skurfa-quality target.
Full panorama source coverage does not mean full EnvHD completion.

`scripts/batch-envhd-skies.py` defaults to planning only. It rehashes and decodes
original MCQ variants, verifies shared artwork ownership and dimensions, and
validates recorded candidates. Pixel-identical stage/header variants share a job;
verified uniform opaque black is excluded. Execution uses pinned Real-ESRGAN
executable and weights. Original-image jobs receive periodic horizontal and
clamped vertical context, then crop back to the exact 4x source rectangle.

The original mode repairs a narrow border while protecting original discard and
opaque visible-black classes. Matching edge colors cannot prove coherent joins.
`--source-edge-baselines` schedules unselected masters from originals and skips
opposite-edge averaging entirely. Its distinct production method is
`real-esrgan-x4plus-periodic-preserved-source-edges`, with `repairBorderPixels: 0`.
It preserves source structure rather than claiming newly seamless reconstruction.

Outputs and provenance are staged privately outside the repository and extraction.
`private-work` contains original-derived inputs and logs and is never published.
Reviewed custom PNGs, saved recipes, individual reviews and actual production
identities are versioned publicly, including held revisions. Original extraction,
comparison sheets, tool binaries and weights remain separate.

The batch tool does not edit installed resources or ownership ledgers.
`scripts/import-envhd-sky.py --production-record <candidates.json>` checks the
actual method, source/input/output identities, predecessor reviews and pinned tool
hashes before recording and selecting reviewed artwork. It preserves the source
alpha/discard/visible-black classes. Neural upscales and Python repairs are not
labeled as new image-generation results. Skurfa references are visual-review-only
for these methods. All existing Skurfa assets and notices remain unchanged.

This pipeline currently covers battle panoramas. World-map artwork, battle
materials and unowned field scenes remain part of EnvHD's wider production queue;
field ownership exclusions prevent redoing Skurfa packs or source-identical aliases.
UIHD and FxHD are handled by other threads.

Synthetic regression tests cover shared variants, retain-selected behavior,
source/candidate/tool drift rejection, uniform-black exclusion, exact visibility,
private staging restrictions, unchanged central artwork, and unchanged asymmetric
source-edge output. Import tests reject false border provenance before publishing.
These checks invoke no game window or desktop application.

## World-map batch

The world batch has also executed: 38 distinct engine-referenced location TIM
images and the 320x256 parchment-map MCQ backdrop. `batch-envhd-world.py` uses
clamped input context on all four sides, preserves source layout and visibility,
and never averages opposite edges. Every source is decoded from the exact bounded
bytes that were hashed. `import-envhd-world.py` verifies the complete source census,
pinned provenance, explicit individual reviews, exact 4x source classes and 8-bit
RGBA PNG compatibility before transactional publication. Existing v1 revisions are
immutable. Eleven synthetic world tests include corrupted provenance, alpha,
16-bit PNG rejection and conflicting-version rollback.

All 39 original/output comparisons passed development source-fidelity review;
retail placeholders and lettering remain inherited, not re-authored. Independent
Java decoding agrees on every source pixel. The rebuilt default installation
bundle includes all 109 selected battle/world images through EnvHD. Source-based
baselines still require bespoke detail and native acceptance; upcoming categories
and the separate delivery thread's consolidated release remain tracked in
`ENVHD_STATUS.md`.
