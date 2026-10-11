# EnvHD field integration candidate

The published maintenance installer contains **2,723 selected development field
images** from source `476c004ca`. This branch prepares **4,892 selected images**
for the next coordinated integration: **4,886 reviewed neural baselines** and
**six source-verified painted repairs**. It does not change main or the public
download. Native gameplay and final Skurfa-equivalent quality remain pending.

All **5,162 generated field sources** have been individually compared against
original pixels and scene placement. The 2,163 additional reviewed neural images
are now versioned and selected on this branch. Six original neural holds have
separately reviewed painted replacements; **270 source images remain held**.
Three uniform sources and empty/missing native slots retain their original data.

| Field images | Count |
| --- | ---: |
| Generated and source-layout repaired neural baselines | 5,162 |
| Individually compared source images | 5,162 |
| Reviewed neural development baselines | 4,886 |
| Original neural repair holds | 276 |
| Separately reviewed painted replacements | 6 |
| Remaining held source images | 270 |
| Still awaiting individual source comparison | 0 |
| Selected on this development branch | 4,892 |
| Selected in the published installer | 2,723 |
| Reviewed baselines awaiting candidate publication/selection | 0 |

Selection covers at least one image in **605 of 612 unowned source configurations**.
That counter is not complete scene coverage or proven route reachability. Seven
configurations still have no selected image; native/uniform and remaining held
sources stay original. Skurfa's 38 owned configurations and protected cut/period
aliases are reused rather than regenerated.

Foreground comparison boards 186–379 completed the continuation review.
`field-review-progress.json` retains those output-bound notes and identities.
The exact historical source-476 controls are archived in
`field-review-receipt-source-476c004ca.json` and
`field-runtime-artwork-source-476c004ca.json`; their bytes match the recorded
baseline hashes. `field-review-receipt.json` now holds the complete neural review,
and `field-runtime-artwork.json` records the expanded selection and both the
neural and painted review receipt hashes. Original game images and comparison
boards remain in private production storage.

The six painted candidates in `field-bespoke-foliage.json` cover Donau's
flower-shop plant and diagonal flower display, two related Evergreen Forest
fragments, a shrub at Shirley's Shrine, and Lohan's independent purple-blue flower
variant. Their generated inputs, prompts, repaired outputs and exact hashes are
versioned under `bespoke-field-candidates`. Only custom artwork is published.
All six were compared at target texture size and in static scene placement;
source alpha and visible-black classes are exact. These comparisons do not prove
runtime depth, scripted state visibility, reachability or final painted quality.

The initial Donau display paintings left 25.5% and 26.8% of the original footprint
uncovered. A row-wise geometry trial filled that shape but visibly smeared petals
and was rejected. A reviewed perspective registration restores the upper bend
while retaining separate flowers, followed by the original visibility repair.
Its source/painted control points, registered-input hash and separately pinned
registration algorithm are recorded with the candidate. The unregistered and
row-wise variants are not selected.

Near-transparent generated matte colours are excluded before restoring source
visibility. Lohan's opaque RGB painting is converted to RGBA with constant opaque
alpha only because its source is entirely opaque; its wood background patch and
purple-blue foreground flowers remain independent of the red arrangement baked
into the background. Its isolated native slot placement was checked because
stacking all foreground state variants obscures this object.

The authoring selection accepts `--files <private extracted source directory>`
and `--painted-manifest integrations/envhd/production/field-bespoke-foliage.json`.
Omitting both selects only the verified neural candidates. Fresh source census,
Env-only ownership, usage bindings, original source hashes, generation/output
hashes, recorded review hashes and exact repaired pixels are checked before
selection. Painted selection and withdrawal rebuild the owned runtime tree
atomically while preserving immutable candidate history. A changed painted
manifest during copying aborts before replacing runtime files.

These arguments belong to production tools. Players still receive one EnvHD mod
with the ordinary internal mod-manager switch and the existing Env1HD–Env4HD
download packs. All selected PNGs, normal package contents and pack sizes must
pass the coordinated package checks before the public installer is updated.

Painted preflight and the repair command inspect PNG headers before decoding
pixels. Compressed inputs are read with a 32 MiB limit plus one rejection byte;
the decoded source must fit a one-million-pixel fourfold target, generated input
is limited to four megapixels and 4096 pixels on either side, and repaired output
must have the exact fourfold source dimensions. These input bounds leave the six
reviewed painted outputs unchanged. Their updated repair identity is bound to
the current manifest and selection; the source-759 painted manifest and runtime
ledger preserve the prior checkpoint byte-for-byte.
The decoder's own lazy dimensions, mode and PNG format must also agree with the
validated header before any copy, conversion, registration or output pixel
array is allocated. Duplicate-header regressions exercise source, RGB/RGBA
generated input and repaired output paths. Source-2ec controls retain the first
bounded-input checkpoint; this correction also leaves every artwork byte unchanged.

Remaining repairs include Crystal Palace balustrades, Twin Castle embroidery,
painted signage, wall pictures, patterned upholstery, small carved fittings,
fine organic structures, woven seats, bedding and heraldic emblems. The earlier
masked cloth/balustrade detail-restoration trials improved pattern retention but
remained too close to interpolated native detail; those trials cleared no holds.
All native/gameplay and final-quality acceptance counts remain **zero**. The wider
EnvHD backlog and unresolved environment prop/animation ownership remain in
`full-coverage.json`; this field integration is not whole-campaign completion.
