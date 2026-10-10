# EnvHD production ownership

Skurfa remains the owner of its existing field backgrounds and foreground layers.
EnvHD fills missing environment artwork and uses Skurfa's finished art as style
references. Its resources must not duplicate or replace Skurfa packs.

`field-ownership.json` groups all successful cut/time-period mappings by their
environment descriptor plus ordered source texture hashes. The current audit
protects 38 Skurfa packs, 43 runtime bindings, and 87 source-identical cut/period
mappings. There are 612 remaining source scene configurations before rendered
pixel deduplication. The five existing Skurfa runtime aliases remain in Skurfa.

Before field generation, refresh the ownership audit against the private source
census and current extraction using `scripts/audit-envhd-ownership.py`. It checks
Skurfa image hashes and every one of the 1,044 source folders and stops on drift,
missing required pages, malformed entries, or unresolved aliases. Generate
only scenes marked `owner: envhd`; reuse any scene marked `reuse-existing`, even
when a different location ID reaches it. Compare visibly identical scenes whose
source signatures differ before commissioning a second master.

Build a single scene master, then derive foregrounds with the original placement
and occlusion masks, following Skurfa's established extraction process. Empty
foreground slots are delivery placeholders, not separate generation jobs.

Battle panoramas, world-map art, and missing battle materials are separate source
assets from Skurfa's field scenes. A battle occurring in a Skurfa-covered location
does not mean its battle panorama is already covered. Bind each new asset to its
actual source hash and preserve another mod's selected replacement.

`battle-skies.json` tracks individual source-bound generations and reviews.
The 74 drawable MCQ file hashes decode to 71 distinct bitmaps. One is a uniform
opaque black image that remains resolution-independent. `battle-generation-tasks.json`
therefore has 70 restoration jobs. Stages 11/12/91 and 83/84 share their respective
bitmaps: restore each master once, while retaining each original MCQ header's
placement and clear-color behavior. No scene is regenerated for a different ID.
Visual review, source/layout validation, runtime integration, and native scene
acceptance are separate states. The overall goal is still incomplete; no inventory
or build result is a claim of 100% visual coverage.

All custom artwork candidates and revisions are public, versioned project assets
under the owner's October 10 authorization. Rejected/development revisions retain
their review status and stay outside `runtime-assets`; only selected reviewed
images enter the installed mod. Private original extraction and source-comparison
diagnostic images remain outside this repository.

Reviewed bitmaps live once under `envhd/sky-images/<decoded-pixel-hash>`. Each
source MCQ variant has its own source-bound manifest in `envhd/skies/<source-hash>`.
The loader checks the original source hash, decoded pixel fingerprint and output
hash before reading the selected version. Shared bitmaps keep each stage's own
retail offsets, camera scroll and clear colors.

`scripts/import-envhd-sky.py` verifies all original variants in a shared group,
existing selected resource hashes, bounded PNG bytes, output dimensions and version
conflicts before writing. Rejected new revisions preserve the earlier selection;
withdrawing the exact selected image removes its bindings and unreferenced runtime
copy while preserving the public candidate. A failed local publication rolls back
the touched files. Run `scripts/test-envhd-sky-import.py` for synthetic regression
fixtures; they use no retail artwork or game window.
