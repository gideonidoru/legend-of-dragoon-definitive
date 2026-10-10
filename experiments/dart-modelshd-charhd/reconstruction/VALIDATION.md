# Dart reconstruction validation · 2026-10-10

Status: experimental, not visually approved for main/release. These results concern the reconstructed Dart head and its composition with existing ModelsHD/CharHD. They do not establish a complete AA character or physical Steam Deck readiness.

## Source and environment

The preferred experiment was preserved. Reconstruction began from `20b802cc586647208777df893c15bc1b5a363e6f`, whose rejected volumetric hair remains separate historical work. The initial reconstruction checkpoint is `41ad1974a2df294da53a955b51c59fb0f2af3e79`. Production source at `63a08e9b079fe35dc3ff8c07d4704927a90d4283` was incorporated into this experiment only, through merge `77e0694ce84fc9e71c4a45def2a993ec5f32699f`. The final patch adds the integration fixes and final material cleanup. Tested source blobs and private trial hashes are recorded in [validation-source-record.json](validation-source-record.json); asset hashes are in [asset-inventory.json](asset-inventory.json).

Verified host: Mac Studio, hardware `Mac17,14`, Apple M5 Max, macOS 27.0.1 / 26A434. Build: Amazon Corretto 25.0.0.36.2, Gradle wrapper 9.1.0. Reconstruction: isolated Python 3.12.0, torch 2.14.1, NumPy 1.26.4, Pillow 10.1.0, trimesh 4.0.5, fast-simplification 0.2.0, xatlas 0.0.11, SciPy 1.17.1. No local tool runtime or weights are committed or installed into the game.

## Results

| Check | Result | What it proves |
|---|---|---|
| Supported `build` | Passed | Compilation, supported package/test tasks; ordinary game test skipped by its existing opt-in gate |
| Delivery tests | 328 discovered, 321 passed, seven skipped, no failures/errors | Headless source/delivery regressions, including the native pixel-buffer regression |
| ModelsHD tests | 32 passed | Geometry and source ownership regressions |
| CharHD tests | Three passed | Current mod material regressions |
| Experimental tests | 16 passed | Supplemental appearance, exact head UV binding rejection, body-atlas composition, resource and ownership behavior |
| Actual source native probe | Eight combinations passed | Field/battle, indexed/simulated RGBA body addressing, head layer on/off; original CPU tables and prepared geometry unchanged |
| Native Mac candidate trial | Six checks passed | Boot, battle, Guard, configuration tests and actual GPU-rendered head with 2K detail while production CharHD body appearance is active |
| Matched native Mac baseline | Six checks passed | Same engine/body material with preferred head geometry and 1254-wide accepted face layer |
| Normal production CharHD JAR | No `dart-head-aa` resource | Experimental asset excluded from the normal mod package |
| Opt-in experimental JAR | 2K resource/checksum present | Explicit build includes the gated material |

Do not count repeated trials as wider gameplay coverage. The six-check trials include a battle and Guard, not an attack-animation campaign. The source probe's simulated RGBA addressing is distinct from the real battle CharHD body route, which was separately asserted in the live trial.

The latest native head bindings have geometry identities `8814dc36066fced448de5dd2975efb67fa2de06daf88a63eb00cfac9ed648818` (field) and `c7dcb52fe0bc114dceba14f7737a3acd29e478834b0d25538af94af23346253c` (battle). Final texture SHA-256: `8e75f9c5297bca7981b9be3a77c3210636fa8a80f2304768c109954536fb4f38`.

## Integration failures caught and fixed

After incorporating the production CharHD route, the first live combined trial crashed in the Mac graphics driver during `glTexImage2D`, called by `CharacterAppearance.create`. `MaterialAtlas.rgba()` was backed by a heap buffer, while the native uploader consumes a direct memory address. The new regression test failed before the change. Allocating a direct, read-only pixel buffer fixed that upload fault; the next native trial reached the battle normally.

That trial exposed a separate composition failure: the mapped body-atlas mesh skipped `TmdAppearanceEvent`, so the model the game actually drew had no reconstructed head texture. A mapped-path synthetic regression failed before the fix. The mapped path now dispatches the optional appearance extension, retains body UV mapping and preserves geometry when optional appearance construction fails. The live test now asserts the texture on `model.materialAppearance.mesh(7)`, not only on an independently constructed source head.

A whole opposite-side paint transfer produced background spill on the face because the generated paint was not perfectly registered. That texture was rejected. The final cleanup is restricted to hair, rejects neutral background samples and preserves the established face/bandana. The final material export checks that vertices, faces, UVs and normals remain unchanged.

## Native comparison conditions

Private runtime: an isolated clone of completed local extraction, links to the owner's four discs, a fresh test campaign, this experimental engine, ModelsHD, CharHD and the opt-in JAR. The normal installed game was not altered. No source game files or private diagnostic dumps are part of this branch.

Final comparison uses keyframe zero, paused actor animation, scale 2.0, a 300-unit vertical shift, fixed inspection camera and three root inspection rotations. Neither the shipped camera nor asset animation was changed. The actor was rotated to make its hair/face inspectable around the sword. The screenshot remains a native game draw, but the pose/framing are diagnostic. Both sides use the same body packs, CharHD body texture and scene.

The complete private captures remain alongside their trial logs. Public `review/` images show only the custom fitted head under studio lighting. Generated source artwork is not presented as a runtime result. Existing shader/native-access/video warnings remain in logs; a clean-log claim is not made.

## Commands

Use Java 25, the repository wrapper and isolated authoring dependencies. Original files, native full actor controls and diagnostic outputs must stay outside the checkout. `/PRIVATE` below is a placeholder for owner-local paths.

```sh
python3 experiments/dart-modelshd-charhd/reconstruction/reconstruct-and-bake.py \
  --tooling /PRIVATE/tooling --image experiments/dart-modelshd-charhd/reconstruction/dart-head-source-v1.png \
  --output /PRIVATE/new-head-study
python3 experiments/dart-modelshd-charhd/reconstruction/bake-material-views.py \
  --mesh /PRIVATE/new-head-study/head-6000-textured.glb \
  --paint experiments/dart-modelshd-charhd/reconstruction/dart-material-views-v1.png \
  --output /PRIVATE/painted-head
python3 experiments/dart-modelshd-charhd/reconstruction/bake-material-views.py \
  --mesh /PRIVATE/painted-head/head-6000-textured.glb \
  --paint experiments/dart-modelshd-charhd/reconstruction/dart-opposite-material-v1.png \
  --angles 270 --hair-only --output /PRIVATE/final-painted-head
python3 experiments/dart-modelshd-charhd/reconstruction/fit-native-head.py \
  --mesh /PRIVATE/final-painted-head/head-6000-textured.glb --files /PRIVATE/files \
  --output /PRIVATE/native-head-study
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle \
  dartExperimentTests deliveryTest modelsHdTests charHdTests
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle \
  dartExperimentProbe -PdartFiles=/PRIVATE/files -PdartStudy=/PRIVATE/native-head-study \
  -PdartReport=/PRIVATE/native-proof.json
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle \
  -I /PRIVATE/dart-live-01/live.init.gradle dartLiveTrial
./gradlew --no-daemon --console=plain build
```

The selected live trial harness remains private rather than becoming a default test that opens a game window. The strict dependency lock now includes the opt-in source sets; no existing dependency version was changed.

## Remaining acceptance work

The crown/rear clumps need further authored topology cleanup, and the full body still needs bespoke armor, fingers, cloth, boots and sword detail. Field gameplay, battle attack sequences, transformations, broad animation coverage, actual third-party texture replacements and physical Deck frametime/memory are not established by this record. The 2K head costs 16 MiB per GPU texture before mipmaps; the source and material appearance routes can own separate instances. Measure that cost before setting a production resolution or enabling additional characters. Preserve fallback and faithful mode throughout.

The source, model payloads and custom art are public experimental work. Main, release assets and installer defaults were not promoted. Visual acceptance still belongs to the owner.
