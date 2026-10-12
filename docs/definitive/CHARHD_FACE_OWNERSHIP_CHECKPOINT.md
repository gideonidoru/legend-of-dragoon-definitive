# CharHD facial ownership checkpoint

2026-10-11. **Artistic review fails; the experimental party heads are not selected for normal installation.** The owner's Haschel screenshot shows a smaller painted face within a larger head, with another jaw/chin contour underneath. A passing mesh, UV or package check does not resolve that defect.

## Findings from matched offline studies

The v4 frontal baker replaces visible front texels but can retain facial shading from its inferred base texture around the jaw and ears. Its profile-face-layer count of zero does not establish that the entire resulting head contains one face. The original reconstruction also has folds beneath the mandible. The existing body already supplies a neck, so adding another neck column does not solve the attachment.

The first jaw-boundary surgery reduces the extra lower contour, but its sharp facial paint and ear/sideburn transitions still read as a mask. It remains rejected. Wider texture neutralization removes inherited face color but can incorrectly erase tinted hair. Profile hair restoration does not recover every affected region. Those ablations remain rejected too.

A separate local anatomical-base study has one skin surface and no inferred face beneath it. It demonstrates a more human jaw and ear structure, but its face proportions differ from Haschel and its hair/scalp and albedo transitions fail. It is not an accepted replacement or a new project art direction.

The anatomical modeling aid is Blender's [Human Base Meshes v1.4.1](https://www.blender.org/download/demo-files/), published under CC0. Its downloaded archive SHA256 is `811f43accbb31a88266d932f8f5563b2d13586fca0ba2693aad1f5fe582b3515`. Blender 4.5.9 was used headlessly, with downloaded-file script execution disabled. Neither the local runtime nor the whole downloaded asset bundle is an installer dependency or committed game asset.

The custom feature-free skin tile is preserved in `integrations/charhd/production/party-reconstruction/authoring-materials/feature-free-skin-v1`. It is an unselected authoring input, not proof of correct anatomy or seamless material boundaries.

## Build and preservation checks

The working branch was brought forward from accepted main `6d5c0d1a671e4c93bbd41d5efd6dd17d6a236b53` at merge `ef90c887b`. The preview preservation baseline now records that accepted main source explicitly: fifteen accepted Dart source bindings and twenty-five selected CharHD runtime files. The three added Dart aliases came from accepted main; the experimental work does not change their geometry or art.

Environment: Mac ARM host, Corretto Java 25, Gradle 9.1.0. Full build used the existing checksum-verified local FMV source archive. The exact visual fixture environment used Pillow 12.3.0 and NumPy 2.5.1 from the committed requirements, separately from the older authoring environment.

```sh
./gradlew charHdTests modelsHdTests deliveryTest --offline --no-daemon --console=plain
./gradlew build stageDefinitivePackage -PfmvHdAssets=build/fmvhd/videos.zip --offline --no-daemon --console=plain
python scripts/verify-party-head-preview.py
python scripts/verify-charhd-package.py build/definitive/package
```

Both Gradle runs passed. The first reported 16 CharHD tests and 32 ModelsHD tests passing, plus 700 delivery tests passing and 10 skipped. All 42 canonical visual fixture scripts passed in the pinned environment; the extra 21 EnvHD battle-family differential checks and workflow syntax validation passed. The staged normal package verified fifteen selected custom bindings and excluded experimental/authoring payloads. The refreshed preview guard passed sixteen archived v4 fits, three negative checks and byte preservation of all twenty-five selected runtime files.

These are source, construction and packaging results. They are not artistic acceptance of the archived v4 heads, verification of the new anatomical studies, native gameplay acceptance, or physical Steam Deck validation. Earlier pose metrics copied into private exploratory receipts must not be represented as fresh measurements of new geometry.

## Acceptance work remaining

Resolve facial geometry/paint correspondence before another portrait generation pass. Preserve the original character's facial width, head size and native bone pose. A single facial source must own the entire relevant surface without revealing another eye, mouth or chin beneath it. Check untextured/clay, neutral lighting, close-ups, full body and animated native poses before presenting candidates for approval. The cheek, ear, sideburn, scalp and jaw material boundaries must survive quarter and full profile views.

All eight experimental replacements remain held; accepted Dart is the unchanged benchmark. The owner's explicit visual approval remains required before experimental heads reach main or a downloadable release.
