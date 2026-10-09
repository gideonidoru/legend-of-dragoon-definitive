# Milestone 1 validation — 2026-10-09

This is the historical unmodified-baseline record. The subsequent project updater target adjustment is recorded separately in [UPDATER_TARGET.md](UPDATER_TARGET.md); the upstream-feed blocker below describes the original milestone state. Later CI/test-control changes are recorded in [BUILD_CONTROLS.md](BUILD_CONTROLS.md); the original test-exclusion blocker is historical as well.

## Later private-input checkpoint

On 2026-10-09, after the recorded baseline builds, the owner supplied four BIN/CUE pairs privately. Files were moved to top-level isos/ for nonrecursive engine discovery; expected US disc IDs were read from volume descriptors and SHA-256 hashes verified unchanged after cleanup. Emulator/patch/archive extras were moved to recoverable Trash. Inputs remain ignored, not committed. Extraction, gameplay and Deck execution remain not run; this header check does not establish complete disc integrity or gameplay compatibility. The historical results below are unchanged.

## Identity, provenance and scope

- Terminal/filesystem access confirmed on **AVALON**, `system_profiler` reports **Mac Studio**, model **Mac17,14**, **Apple M5 Max**, 18 cores (6 Super + 12 Performance), **64 GB RAM**.
- macOS **27.0.1**, build **26A434**, ARM64 (`aarch64` as reported by Gradle). User date/time context: America/New_York, 2026-10-09.
- GitHub `gh auth status` and `gh api user --jq .login` verified **gideonidoru**. The initial sandboxed network check could not connect and misleadingly reported invalid authentication; unrestricted authorized network access confirmed valid keyring authentication. No token recorded here.
- Repository existence check returned not found before creation; created **public** `gideonidoru/legend-of-dragoon-definitive`. Actions disabled before first push to prevent inherited publishing workflows from running.
- Cloned official upstream main without altering/reinitializing history. Exact baseline: **`fba1543543865e29ee572f479003d9b47158eeb3`**, commit message “Add event when cloning characters”, dated 2026-10-09. Fixed branch `upstream-baseline` preserves this snapshot.
- LICENSE is GNU Affero General Public License v3; unchanged SHA-256: `8486a10c4393cee1c25392769ddd3b2d6c242d6ec7928e1414efff7dfb2f07ef`. CREDITS, credits.txt and existing notices preserved.
- No engine Java source, build scripts, launcher behavior or game behavior changed. Milestone additions: documentation/evidence and ignore rules for private data. No new game images, extracted assets, saves, credentials, dependency caches or local runtimes committed.

## Environment and reproducible commands

No system JDK was discoverable via `java -version` or `/usr/libexec/java_home -V`. Downloaded upstream's pinned Corretto archive into temporary storage, outside Git:

```sh
mkdir -p /private/tmp/lod-definitive-tooling
curl --fail --location --output /private/tmp/lod-definitive-tooling/jdk25.tar.gz \
  https://corretto.aws/downloads/resources/25.0.0.36.2/amazon-corretto-25.0.0.36.2-macosx-aarch64.tar.gz
tar -xzf /private/tmp/lod-definitive-tooling/jdk25.tar.gz -C /private/tmp/lod-definitive-tooling
```

Archive SHA-256 observed locally: `62dc483ae2d51d995e7ec756cda0a3b2491293946f3e9281cc2e9a1b8f11d1bd` (recorded digest, not an independent provider signature verification).
Java reported OpenJDK **25**, Corretto **25.0.0.36.2**, build **25+36-LTS**. Checked-in wrapper fetched **Gradle 9.1.0**.

From `/Users/markmurray/Downloads/Development/legend-of-dragoon-definitive`, before editing project documentation:

```sh
JAVA_HOME=/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home \
GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle \
./gradlew --no-daemon --console=plain clean build \
  > /private/tmp/lod-definitive-tooling/baseline-build.log 2>&1

JAVA_HOME=/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home \
GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle \
./gradlew --no-daemon --console=plain clean build \
  -Pos=linux -Parch=x86_64 -Psteamdeck=true \
  > /private/tmp/lod-definitive-tooling/steamdeck-package-build.log 2>&1
```

## Results

| Check | Observed result | What it proves |
| --- | --- | --- |
| macOS ARM64 clean build | Exit 0; BUILD SUCCESSFUL in 21s; 11 actionable tasks, 9 executed, 2 up-to-date | Unmodified engine/test sources compile and supported native-target package tasks complete |
| Steam Deck Linux x64 package configuration on Mac | Exit 0; BUILD SUCCESSFUL in 8s; 11 actionable tasks, 10 executed, 1 up-to-date | Supported cross-target compilation and packaging complete; Linux dependencies selected |
| Package inspection | Game/updater JARs, libs, gfx, patches, lang, LICENSE, credits and launch support files produced; launcher COMPILED=true, download helper targets linux-x64 | Expected Deck support files generated; not execution proof |
| Gameplay tests | `:test NO-SOURCE` in both builds | No tests executed; do not interpret build success as gameplay success |
| Game files | isos/files/saves contain tracked .gitignore placeholders only | No private game content supplied or used |
| Gameplay / display / audio | Not run | No desktop interruption or game launch |
| Actual Steam Deck | Not accessed or run | No device performance, install, input, suspend/resume, readability or gameplay validation |

Full command output (trailing whitespace normalized): [macOS build](evidence/baseline-build.txt), [Deck package configuration](evidence/steamdeck-package-build.txt). Warnings include Gradle native-access use, deprecated Java APIs and unchecked operations. They did not fail compilation and were not changed in this milestone.

Both builds used the unmodified upstream source/build configuration. Final documentation and ignore-rule changes do not change engine behavior. Current ignored `build/libs` is the Linux x64 package tree, not an executable Mac package; regenerate for macOS before any authorized Mac play. No release archive or gameplay-ready Definitive build has been published.

## Conflicts and blockers

1. README says Java 21; build.gradle and CI require 25. Use 25 and preserve/report the discrepancy.
2. Test guide advertises `-PrunTests`, while build.gradle excludes all tests unconditionally. Test activation requires a later explicit build/test change. Windowed E2E tests also require private assets and permission.
3. No game files or Deck validation. User will provide files later; obtain permission before game/UI execution.
4. HD project exists, but this milestone has not verified a distributable licensed pack, coverage or compatibility. [Public project status](https://legendofdragoon.org/projects/image-upscaling/) contains an older progress update and no completion date; do not treat it as a current release confirmation.
5. Updater still points at upstream releases; inherited CI requires upstream services/secrets. Resolve package identity/update channel and introduce build-only CI before distributing binaries or enabling Actions.
6. Temporary JDK/cache may be deleted by macOS; durable developer JDK setup remains.
7. Faithful preset is planned, not implemented. Existing upstream convenience defaults differ from retail; require a full preset audit before claiming fidelity.
8. Steam Deck launcher places JVM-style flags after the main class; inspect argument behavior during later launcher work. No launcher edits made.

## Next steps

Follow [ROADMAP.md](ROADMAP.md): stabilize install/release boundaries, measure a real Deck baseline with user-provided private files and authorized execution, confirm HD-pack terms/compatibility, then implement small mod/config improvements for art, controller flow and readable menus. Rendering/upscaling experiments follow measured evidence.

## Later integration checkpoint

The initial evidence above remains historical. Public Skurfa source/artwork integration, current package builds and authorized Mac gameplay smoke results are recorded in [INTEGRATION_ALPHA.md](INTEGRATION_ALPHA.md). No physical Deck or complete playthrough validation is implied.
