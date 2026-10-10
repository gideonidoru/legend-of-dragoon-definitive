# Environment batch production

The existing generative panorama pass has 39 revisions across 28 of 70 restoration
masters, with seven selected after image review. Layout drift and repeating joins
prevent most drafts from entering the installed mod. This batch workflow is
prepared as an alternate production method; actual artwork pixel editing awaits
the owner's explicit choice. No new artwork is claimed by preparing the pipeline.

`scripts/batch-envhd-skies.py` defaults to planning only. It rehashes and decodes
each original MCQ variant, checks the shared artwork owner and source dimensions,
and validates the recorded candidate bytes before scheduling a border repair.
Pixel-identical stage/header variants remain one job. Verified uniform opaque
black is excluded. The current plan retains seven selections, repairs 18 drafts
whose source layout passed review, and upscales 45 originals (42 ungenerated and
three whose generated layout failed).

Execution is an explicit `--execute` operation using the already pinned local
Real-ESRGAN executable and weights. Original-image jobs receive periodic horizontal
context and clamped vertical context before inference; output is cropped back to
the exact 4x source rectangle. Repair jobs keep their existing central artwork.
Both receive a narrow smooth border correction while protecting original discard
coverage and opaque visible-black pixels. Opposite protected coverage classes are
never blended. The border correction does not repair misplaced silhouettes or
prove a seam invisible; each result still needs visual review.

Outputs and provenance go into a new private staging folder outside both the
repository and original extraction. `private-work` contains original-derived
inputs and logs and must never be published. The custom output PNGs and their
production-method records will be published after review under the existing owner
authorization, including rejected/development revisions. Neural-tool binaries,
weights, original extraction and diagnostic comparisons remain separate.

The batch tool never edits the ownership ledger, installed resources or selected
runtime manifests. Its candidates remain pending source-intent, Skurfa-style,
layout and repeat review. A source-preserving neural upscale is a baseline;
matching Skurfa's material quality still requires inspection and selective bespoke
reconstruction. No automatic score selects artwork or proves native acceptance.
`scripts/import-envhd-sky.py --production-record <candidates.json>` verifies and
records the actual batch method, source/input/output identities, predecessor
review and pinned tool hashes. A neural upscale or Python repair is never labeled
as a new image-generation result. Skurfa references are explicitly review-only
for these methods. Original source colors are restored wherever rounding or
inference would otherwise introduce a new opaque-black texel.

Skurfa retains its resources, notices and independent activation. This command
addresses the battle-panorama task ledger only; it does not generate field scenes
or replace Skurfa packs. The field ownership exclusions remain authoritative when
production moves on to the remaining environment categories.

`scripts/test-envhd-sky-batch.py` uses synthetic fixtures to verify shared header
variants, retain-selected behavior, source/candidate/tool drift rejection,
uniform-black exclusion, exact source visibility, unchanged central artwork,
private-output restrictions, and candidate-only execution. It invokes no neural
runtime, real game artwork, game window or desktop application.
