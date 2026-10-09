# Source integration alpha — October 9, 2026

## Delivered scope

The owner selected Skurfa and selective QoL+ interface improvements as primary integrations, kept Dragoon Modifier and Battle Rewards optional, and authorized local game launches/tests. Skurfa is public and linked in Git so a recursive clone includes its source and artwork. No full QoL fork merge, save-format downgrade, reward overhaul, texture upscaler or faithful preset is claimed.

- `integrations/skurfa`: public Git submodule, pinned at `3c9e4b3ecefc31cb32fd1281a7857f3a08f56081` (v1.1.0). Its repository retains source, runtime artwork, MIT LICENSE and THIRD_PARTY_NOTICES.md. MIT applies to original code/tools; derivative artwork retains the separate notices and underlying rights.
- `gradle/skurfa.gradle`: compiles against this checkout and packages source resources/artwork into `build/libs/mods/Skurfas-HDR-Backgrounds-v1.1.0.jar`. Includes original license, notices and README under `META-INF/skurfa/`. Ordinary builds and CI include this JAR but never launch gameplay or publish binary assets. CI initializes submodules.
- Equipment menu: binding-aware Equip, Sort, Unequip and Back footer actions, inspired by QoL+ interface goals and implemented through current menu APIs. Actions execute only after equipment loading is ready. Inventory mechanics, rewards and save format are unchanged. Footer rendering/controller usability still need visual/device checks.
- Mac test tooling: explicit `macGameplayTest -PrunTests` keeps SDL/OpenGL on the native first thread and JUnit on a worker. Registry texture initialization uses the render thread. Test-only SDL events target the engine window. Guard now waits for action consumption and the next player turn before asserting HP.

## Inclusive clone and build

```sh
git clone --recurse-submodules https://github.com/gideonidoru/legend-of-dragoon-definitive.git
cd legend-of-dragoon-definitive
# Set JAVA_HOME to your external JDK 25 installation.
./gradlew --no-daemon --console=plain clean build
```

Existing clones: `git submodule update --init --recursive`. A plain parent-only clone or GitHub source ZIP does not include submodule contents. The build rejects an uninitialized Skurfa integration instead of emitting an empty mod. Do not run `submodule update --remote` for routine builds; use the reviewed gitlink.

The generated portable package already contains Skurfa under `mods/`. Skurfa covers a subset of scenes; unmapped scenes use existing backgrounds. To use original backgrounds everywhere, remove its JAR from a local package before launch. A user-facing artwork selector and faithful preset remain future work. Do not install both a release JAR and the source-built JAR with the same mod ID.

For development launched from the repository root, `mods/` is separate from `build/libs/mods/`: copy the generated JAR to local `mods/`, preserving any installed version first. Local binaries remain ignored. `scripts/install-skurfa.py /path/to/Skurfas-HDR-Backgrounds-v1.1.0.zip` is an optional checksum-verified release-install alternative: exact archive/member hashes, idempotent matching install, conflicting destination refusal and symlink rejection. Recursive source builds need no release download.

## Exact source and environment

Upstream engine baseline: `fba1543543865e29ee572f479003d9b47158eeb3`. Parent before this slice: `1ca64c98cad71d6a6fa421e605ea62f21be5f609`; implementation revision: [`eb5461a046bcc3843e49abeda43927607f33e9a4`](https://github.com/gideonidoru/legend-of-dragoon-definitive/commit/eb5461a046bcc3843e49abeda43927607f33e9a4). No newer engine merge was performed. QoL research source: `FrancisDionne/Severed-Chains`, branch `Everything-QoL`, commit `35a7b14616d038d9616a6012f4c44002379cf914`; interaction ideas are selectively reimplemented against the current APIs.

Local validation: AVALON Mac Studio, Apple M5 Max, 64 GB, Mac17,14, ARM64; macOS 27.0.1 (26A434). Corretto 25.0.0.36.2, Java 25+36-LTS; Gradle wrapper 9.1.0. OpenGL reported `4.1 Metal - 91.7`. GitHub authentication reconfirmed as `gideonidoru`; origin/upstream point to the owner/official repositories respectively.

Validation used `JAVA_HOME=/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home` and `GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle`. Both are disposable local tooling outside Git.

## Commands and verified results

| Check | Command / result |
| --- | --- |
| Mac package with source-built Skurfa | `./gradlew --no-daemon --console=plain clean build` — success, 13 seconds, gameplay `test` skipped |
| Linux x64 / Deck package with source-built Skurfa | `./gradlew --no-daemon --console=plain clean build -Pos=linux -Parch=x86_64 -Psteamdeck=true` — success, 12 seconds, gameplay skipped |
| Game extraction and normal Mac startup | Direct Java launch below — all four owner-supplied US discs extracted/patched; engine initialized OpenGL and FMV, and discovered/loaded `scbackgroundhd`; stopped the owned process afterward |
| Mac gameplay with source-built Skurfa installed locally | `./gradlew --no-daemon --console=plain macGameplayTest -PrunTests` — success, 13 seconds; three found/started/successful, zero skipped/failed. Fresh state: gold 20, party 1, chapter 0. Encounter 0/stage 0: monster HP 2/2. Guard consumed the turn, then next player turn arrived after 5.6 seconds; monster HP stayed 2 |
| Explicit Mac task disabled | `macGameplayTest -PrunTests=false` — rejected with the required opt-in error before launching; the Mac-specific gate is checked in addition to generic controls |
| Missing submodule | Isolated fixture applies the actual Skurfa build script with no mod sources; `compileSkurfa` fails with initialization instructions |
| Headless test controls | `python3 scripts/verify-test-controls.py` — all seven scenarios pass; no engine/assets loaded |
| Release installer | Verified archive/member installation, matching-file idempotence, wrong-archive rejection and conflicting-file preservation |
| Source-built JAR audit | 302,778,225 bytes; 368 PNG entries; mod class, original notices and license present; ZIP integrity passed. Identical SHA256 from Mac and Deck builds: `139a7da47b51bd0da631ed16e8a0b7f92a0376a37568f6bcb460387b7814b4da` |

Normal startup command, executed with the external JDK from the repository root:

```sh
"$JAVA_HOME/bin/java" -XstartOnFirstThread -ea -Xmx4G \
  -Djoml.fastmath -Djoml.sinLookup -Djoml.useMathFma \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  --enable-native-access=ALL-UNNAMED \
  -cp 'build/libs/lod-game-snapshot.jar:build/libs/libs/*' legend.game.Main
```

The initial normal startup used the verified published Skurfa release JAR; the final three-test run used our source-built JAR. The release archive SHA256 is `a62583eb3c1e1d436f1b17078323024c97eca2d3b86056d94591cb2c4288c7ad`, and its original JAR SHA256 is `9578d357d01a747f3460737be7ffb90224a6be383fd77c4b5efc9f671989c1c8`. Source-built JAR metadata/notices differ, so these hashes should not match.

Local logs are retained in `/private/tmp/lod-definitive-tooling/`: `integration-game.log`, `integration-source-build.log`, `integration-source-deck-build.log`, `integration-gameplay-source.log`, `integration-test-controls.log`, `integration-final-macos-build.log`, and `integration-hosted-ci.log`. A final clean Mac rebuild after the Deck check passed in 12 seconds, restoring local Mac-native packaging. They are private evidence, not repository artifacts. The owner began without saves; the harness creates a local `E2E-Test` campaign.

Hosted build-only CI also passed both macOS and Linux x64/Steam Deck jobs, including recursive checkout, source-built Skurfa packaging and all seven headless control scenarios, at implementation revision `eb5461a046bcc3843e49abeda43927607f33e9a4`: [run 37984243580](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/37984243580). No game files, gameplay or binary publication were used by CI.

## Failures resolved and limits

The original generic Gradle test path failed on Mac because SDL was started on a JUnit worker thread. An early first-thread runner then exposed premature window readiness/input routing and registry texture upload from the wrong thread (native OpenGL abort). The test tooling fixes those paths; final execution completed successfully. No engine rendering behavior was changed to make tests pass.

These are automated state/battle smoke tests using injected state and input, not a fresh user playthrough. Mod discovery/loading proves compatibility at startup, not correct Skurfa foreground masks or visual quality. No supported native UI capture was available for the Java window, so equipment-footer appearance and artwork scene quality were not visually verified. There is no physical Steam Deck, controller, frame-time, power, suspend/resume or full-disc progression evidence yet. The first-thread runner exits its owned game process after results; long-session graceful shutdown is not covered.

## Next work in priority order

1. Inspect Skurfa early forest/cut 9, foreground-mask transitions and high-layer cut 21; verify original-background fallback and record texture memory. Keep visual findings separate from these smoke results.
2. Visually check equipment hints with keyboard/controller bindings at 1280×800, then adapt QoL inventory sort/filter/quantity workflows using current saves and storage APIs. Cover full inventory, cancel and quantity boundaries.
3. Establish physical Deck baseline: model/SteamOS, controller-only fresh launch, import/install, readability, frame-time/memory and suspend/resume. Compare original backgrounds with Skurfa before tuning rendering.
4. Finish package versioning, safe updates/rollback and faithful preset. Keep the two balance mods optional; leave model texture and whole-frame upscaling behind performance evidence.

Public Git tracks the submodule link and project source/config/docs. Private discs, extracted retail assets, saves, local runtime/configuration, downloaded archives and local/generated mod JARs remain excluded. Future binary release packaging still needs its own source/notices/artifact review; none was published in this slice.
