# Animated party hair development

Custom strand paint for Rose's field back hair and battle fringe, and Meru's three field and three battle ponytail parts. These eight resources accompany the registered `head-program-v4` study. They are **development assets awaiting artistic review**, not a normal CharHD selection or release approval.

The original animated hair shape, positions, normals, source weights and native bone pivots are retained. Vertices are duplicated only for UV seams. Opaque hair uses a custom strand atlas; Rose's fringe uses explicit alpha cutout and retains the source silhouette. The fringe RGB is entirely custom. Original texture RGB, game binaries, full actor controls and original comparison pixels are not included.

The authored strand images and exact generation requests are in `authoring/`. Inputs are custom head artwork, with reference roles and hashes recorded. The rejected procedural stripe test is not selected here. Custom code and mod resources follow the project's AGPL v3 license; retain Severed Chains and ModelsHD attribution.

## Reproduce

Use the project's Python authoring environment with NumPy, Pillow, SciPy and trimesh. Set `GAME_FILES` to locally unpacked owner-provided game files and `STUDY` to a new folder outside the checkout. No original assets are downloaded or published by these commands.

```sh
python scripts/bake-party-hair-details.py \
  --files "$GAME_FILES" \
  --heads integrations/charhd/production/party-reconstruction/head-program-v4 \
  --strand-paints integrations/charhd/production/party-reconstruction/hair-program-v1/authoring \
  --output "$STUDY"

python scripts/verify-party-hair-details.py \
  --files "$GAME_FILES" --hair "$STUDY" --report "$STUDY/VERIFICATION.json"

python scripts/stage-party-head-preview.py \
  --auxiliary-hair "$STUDY" --output "$STUDY/isolated-preview"
```

The default preview contains the corrected sixteen head entries plus twelve byte-identical accepted Dart entries. Hair is added only through the explicit option above, giving 36 parts. Normal installation and ordinary CharHD packaging remain unchanged. The preview is for an isolated test installation; do not install two CharHD JARs together.

For matching offline comparisons, pass `--auxiliaries "$STUDY"` to `scripts/render-party-candidates.py`, with the corresponding `head-program-v4/<character>` candidate. The renderer uses the same original body and pose for both sides, treats cutout alpha explicitly, and neutralizes vertex colour on custom detail exactly as the engine does. Keep all before/after renders outside Git.

## Verified on 2026-10-11

- Eight geometry/UV/texture transactions and unchanged source positions, normals, weights and pivots: passed (`VERIFICATION.json`).
- Rose battle fringe silhouette intersection-over-union: **0.9958655566** in a 512-pixel offline rest-pose test. This is a raster comparison, not a guarantee of pixel-identical GPU filtering.
- Fully transparent cutout is empty; opaque detail ignores alpha; visible black remains visible: passed in the offline renderer. The separate engine cutout checkpoint contains GPU evidence.
- Actual game loader CPU construction: **192 combinations**, covering all eight characters, field/battle, indexed/RGBA/mapped materials, ModelsHD on/off and candidate on/off (`CPU-CONSTRUCTION.json`). Painted-part counts, dimensions, cutout flags, neutral detail colours and unchanged source packets/positions were checked.
- CharHD suite: **15 tests, zero failures/errors/skips**. Accepted Dart's twelve parts and all nineteen normal runtime files remained unchanged.

Environment: Mac Studio (Mac17,14), Apple M5 Max, macOS 27.0.1 build 26A434, Corretto Java 25+36, Gradle 9.1.0. Tests ran headlessly. The construction fixture logged a missing `gfx/fonts/default.json`; the geometry checks completed successfully. No gameplay or Steam Deck acceptance is claimed.

## Remaining artistic work

The new paint improves colour consistency but retains the angular segmented ponytail and native fringe outline. Rose still has overlapping fringe geometry, and Meru still has bow/ponytail transitions to reconcile. Several party heads have unacceptable jaw/neck transitions. These assets must not be presented as finished AA models.

All nine characters, including accepted Dart as a benchmark, require owner visual review before this experiment enters main or a release. Publication of custom draft resources is separate from that approval.
