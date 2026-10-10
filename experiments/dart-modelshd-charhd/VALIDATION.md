# Validation record · 2026-10-10

Base: `2b2e95cb5995f7d1ca76b08f12c9a932be134c97`, current ModelsHD 0.4. Work remains on `experiment/dart-modelshd-charhd`; no main, installer defaults or release was updated. Normal package tasks exclude the experimental CharHD JAR and art/mesh payloads. Normal engine builds on this experimental branch do contain the dormant engine hook, so that engine build itself is also gated.

Environment: Mac Studio, Mac17,14; macOS 27.0.1 (26A434); Amazon Corretto Java 25, Gradle wrapper 9.1.0; offline authoring Python 3.12, NumPy 2.5.1, Pillow 12.3.0. Windowless CGL renderer reported Apple M5 Max / OpenGL 4.1 Metal - 91.7. This is Mac evidence, not Steam Deck evidence.

## Results

- Supported `build` completed. The normal interactive game test was deliberately skipped by its existing opt-in policy.
- Delivery suite: 182 total, 175 passed, seven environment-gated skips; no failures/errors. ModelsHD suite: 31 passed. Experimental CharHD suite: nine passed, including source immutability, full-resolution UV detail, unknown-source behavior, resource bounds/ownership, original face bytes and geometry-preserving appearance fallback.
- Python source/model/material/pose regression fixtures: 56 passed across eleven files. New experimental generator fixtures: three passed, covering shared edges/source lineage, fixed attachment boundaries/nonfolding triangles and selected-face UV detail versus unchanged material controls.
- Actual private Dart sources: eight native construction combinations (field/battle, indexed/simulated RGBA body addressing, face on/off). Exactly one painted head when enabled, none when disabled. Every native float finite; prepared positions, indices and normals match the pre-appearance geometry as a per-vertex multiset; original CPU vertices and packet bytes unchanged. Listeners are retained with reachability fences, with collection deliberately forced before every part. All field cases construct 149,312 floats; all battle cases 264,064. The report is in `validation/native-construction.json`. Simulated RGBA uses original logical source dimensions (field 64×112; battle 256×256), not physical 4x pixel dimensions.
- Windowless GPU fixture: actual field and battle shaders compiled/linked. Synthetic detail-free body pixels matched the committed baseline exactly. Supplemental paint changed color while preserving coverage and opaque alpha, including opaque black. Body UV offsets and body normal/roughness textures could not shift/distort the face layer. This fixture does not prove live CharHD body atlas playback, character scene lighting, mod-load order or gameplay.
- Private final study: all 84 unique comparison images generated; ten stored pose keys per inspected animation finite. Keys 0, 5, 9 rendered for visual samples. Custom mesh payload hashes match the committed recipe receipt; no original controls are committed.

Independent review found that the earlier native harness used unretained weak event listeners: its apparent successful report contained missing late battle refinement. That report was invalidated and replaced. The corrected probe now compares prepared geometry directly and tests listener lifetime under collection. Review also corrected persistent texture ownership and replaced duplicated offline/runtime face-selection/projection constants with one bounded resource.

The earlier vertex-color face bake was rejected for blurred features. A failed sandbox-only field inference attempt was rerun successfully with the existing Metal tool; that failed log remains private and is not presented as a success. A synthetic fallback fixture initially used an authored table that correctly skipped native events; it was corrected to a parsed native fixture with an accessible listener. An incomplete comparison matrix discovered during bundle assembly was corrected and now fails on missing/duplicated outputs.

## Reproduce

Set Java 25 using the local toolchain and an isolated Gradle home. `PRIVATE_FILES`, `PRIVATE_STUDY`, and `PRIVATE_REPORT` refer to owner-held files outside Git:

```sh
./gradlew --no-daemon --console=plain \
  -I experiments/dart-modelshd-charhd/experiment.init.gradle \
  build modelsHdTests dartExperimentTests dartExperimentJar dartExperimentProbe \
  -PdartFiles="$PRIVATE_FILES" -PdartStudy="$PRIVATE_STUDY" \
  -PdartReport="$PRIVATE_REPORT"
python3 experiments/dart-modelshd-charhd/test-study.py
```

The 56 Python fixtures were executed directly as scripts, because their hyphenated names are not discovered by the standard unittest filename/import rule:

```sh
for test in test-surface-refinement test-modelshd-roster test-modelshd-world-pass \
  test-model-reference test-model-poses test-model-material-audit test-material-packing \
  test-uv-footprints test-material-islands test-texture-transparency test-model-catalog; do
  python3 "scripts/$test.py" || exit 1
done
```

On macOS, stage the five committed baseline model shaders into a temporary directory and run the windowless fixture:

```sh
mkdir -p /tmp/dart-baseline-shaders
for shader in tmd.vsh tmd.gsh tmd.fsh battle_tmd.vsh battle_tmd.fsh; do
  git show "2b2e95cb5:gfx/shaders/$shader" > "/tmp/dart-baseline-shaders/$shader"
done
clang -Wno-deprecated-declarations -framework OpenGL \
  experiments/dart-modelshd-charhd/face-detail-probe.c -o /tmp/dart-face-detail-probe
/tmp/dart-face-detail-probe gfx/shaders /tmp/dart-baseline-shaders
```

No game, UI test or desktop game window was launched. Compilation, CPU construction, offline visualization and synthetic GPU fixtures remain distinct from gameplay and physical Deck validation.

The standalone viewer contains 84 embedded model images and requires no server. Its JavaScript passed a syntax check; a headless Chrome screenshot was visually inspected. Chrome produced the screenshot but timed out while exiting with CVDisplayLink warnings; the isolated review processes were cleaned up. This is layout inspection, not a live game/UI test.

## Promotion blockers

Explicit user approval of the chosen visual direction; hair/hand/side-profile refinements if requested; production CharHD battle material-map integration; actual scene-lighting/STP/mod composition and transition testing; broader character animation coverage; measured physical Steam Deck frametime and memory. The user-directed faithful mode must keep the experimental face/art changes disabled. Nothing here claims universal mod compatibility or community acceptance.

## Battle bandana correction — 2026-10-10

Owner review identified that the battle eyes were too high and the bandana disappeared. A direct private-source check reproduced paint on red cloth source faces 21 and 23. A separate landmark regression reproduced the shared field projection placing the battle eye sample at V=0.44645 instead of the painted eye region. A selected hair face (75) was also removed from the face layer.

The shared mapping now preserves all identified bandana faces (20, 21, 23, 110, 121, 122) and hair face 75. Battle-only vertical projection uses face origin 4.0 and scale multiplier 1.25; field mapping and all mesh payloads are unchanged. Offline and native paths read these values from the same bounded resource. The two new regressions failed before the fix in both Python and Java, then passed after it.

Verification: 11 experimental Java tests, five Python generator tests and all eight actual-source native construction combinations passed; ModelsHD's existing 31 tests remain passing/up to date. All 84 comparisons regenerated. All 42 field images and all 12 custom mesh payloads remain byte-identical to the previous version. Three battle camera views and stored keys 0, 5, 9 were visually inspected after correction. The runtime probe used the previous private study's identical geometry packs, with the newly compiled face mapping. No game was launched; gameplay and physical Deck validation remain pending. The earlier full build and synthetic GPU results above were not rerun for this mapping-only correction.

Revised private review: `Legend-of-Dragoon-Dart-Experiment-v2/index.html` and matching ZIP. Historical independent review above covered the earlier revision, not this owner-requested correction. Main/release approval remains required.
