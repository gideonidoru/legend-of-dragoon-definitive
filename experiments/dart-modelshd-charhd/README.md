# Dart: ModelsHD + CharHD experiment

Status: **experimental; not approved for main or release**. The user requires explicit approval of matched before/after comparisons before promotion. Keep this work on `experiment/dart-modelshd-charhd`; do not merge, release, change the normal ModelsHD payload index or add this directory to installer tasks. Public custom work remains permitted; publication is distinct from visual acceptance.

Scope: Dart normal field and battle models. Compare the current ModelsHD 0.4 baseline and the experiment, each with original textures and the source-bound CharHD 4x restoration. Use identical cameras, poses, framing, background and rendering for each comparison. Preserve original source part indices, animation pivots and active material addressing. Keep original geometry controls, extracted game input and runtimes outside Git.

Acceptance requires whole-character visual review, stock/CharHD texture consistency, animation and material integration evidence and measured Steam Deck performance. Offline pose renders are clearly labeled; they do not prove gameplay, native lighting or performance. Experimental results may be rejected or revised. Approval must come directly from the user and explicitly cover the proposed main/release change.

## What this increment adds

- Dart field and normal battle custom part candidates, with fixed attachments and the existing animation structure.
- CharHD 4x texture restoration in matched comparisons, plus an opt-in runtime field texture override.
- An experimental appearance event after ModelsHD preparation, explicit source-face lineage, and a bounded supplemental face texture. The engine keeps original CPU tables intact, preserves active body addressing and retains prepared geometry if an optional appearance fails.
- Owned face textures released with rendered objects; defensive pixel/UV copies, image/hash budgets, and native-deformation/unknown-source guards.

The face layer addresses a concrete limitation: much of the original face is vertex-colored, so merely upscaling the existing character texture cannot add facial detail there. Its separate sampler leaves indexed textures and future CharHD RGBA body atlases free to use their existing addresses. The current CharHD production thread owns the battle atlas route; this experiment does not merge its unfinished runtime changes. Full native battle body-atlas integration remains a promotion prerequisite.

## Review and validation

The review bundle contains six controlled combinations: ModelsHD 0.4 and experimental shapes, each with stock, CharHD, and CharHD plus face paint. It includes matched field/battle whole-character views, close-ups at three angles and stored pose keys 0, 5 and 9. A four-stage view separates texture restoration, shape changes and painted detail. Images are offline orthographic packet/UV comparisons, with no scene lighting or postprocessing; they are not game screenshots. The Java probe checks actual native mesh construction separately. The windowless GPU fixture uses synthetic geometry, not a live game scene.

Current counts: field 1,558→3,118 triangles; battle 2,838→5,508. Ten stored keys per inspected animation remain finite; original model part arrays, packets and CPU vertices remain unchanged. Increased geometry and the approximately 6 MiB base face texture have **not** been measured on a Steam Deck. These counts are costs, not visual quality scores.

Next art work: refine hair masses and their material detail, improve the side profile/ear transition, then rebuild hands where visible. After owner review, integrate the selected art with the production CharHD material route and verify scene lighting, transitions, animation breadth and physical Deck memory/frametime. Expand to other party characters only after the Dart direction proves worthwhile.

## Developer-only commands

Use Java 25 and the normal supported Gradle wrapper. This directory is not included by ordinary installer/package tasks.

```sh
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle \
  dartExperimentTests modelsHdTests dartExperimentJar
python3 experiments/dart-modelshd-charhd/test-study.py
```

The optional native probe reads a private completed extraction and a generated private study directory. It never launches a window:

```sh
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle dartExperimentProbe \
  -PdartFiles=/PRIVATE/files -PdartStudy=/PRIVATE/study \
  -PdartReport=/PRIVATE/native-report.json
```

Generate comparisons using the pinned Pillow/NumPy requirements, original local source files and the committed restoration snapshots. The output must be new and outside the checkout/source extraction; unchanged control geometry is emitted only there:

```sh
python3 experiments/dart-modelshd-charhd/build-study.py \
  --files /PRIVATE/files --output /PRIVATE/new-study \
  --combat-charhd experiments/dart-modelshd-charhd/charhd/restoration/combat \
  --field-charhd experiments/dart-modelshd-charhd/charhd/restoration/field
```

The opt-in JAR is a CharHD face/field extension, not a replacement for the engine or ModelsHD. Shape payloads are separate experimental ModelsHD override packs; no automatic installer copies them. For a future authorized developer gameplay trial, build this branch's engine, enable ModelsHD and the experimental JAR, and stage the custom packs in its isolated `model-packs/modelshd/parts` directory. Removing the experimental mod and its overrides restores the normal route. Do not stage these into the user's normal installation or change release defaults without approval.

Battle face mapping has been corrected after owner review: the original red bandana and hair materials are retained, and battle-specific eye/mouth alignment is shared by the offline and native paths. Field mapping and mesh geometry are unchanged.

See [PROVENANCE.md](PROVENANCE.md), [receipt.json](receipt.json) and [VALIDATION.md](VALIDATION.md) for asset origins, hashes and the evidence limits.
