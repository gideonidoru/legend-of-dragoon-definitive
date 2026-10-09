# Setup and build

## Requirements verified on 2026-10-09

- Git with command line integration; authenticated `gh` only needed for repository administration.
- JDK 25: `build.gradle` sets source and target to `JavaVersion.VERSION_25`; upstream CI at the baseline used Temurin 25; current build-only CI uses pinned Temurin 25. The upstream README says 21, but the executable build configuration controls this checkout.
- Use the checked-in `./gradlew` (Windows: `gradlew.bat`), which selects Gradle 9.1.0. Do not substitute a system Gradle.
- Network access for wrapper and Maven dependencies. No disc images are needed for compilation.
- Upstream recommends IntelliJ and familiarity with Java and MIPS. No CONTRIBUTING.md or repository AGENTS.md was found at the baseline commit. Consult the upstream README and community modding channel for contribution discussions.

## Clone and build

```sh
git clone --recurse-submodules https://github.com/gideonidoru/legend-of-dragoon-definitive.git
cd legend-of-dragoon-definitive
git remote add upstream https://github.com/Legend-of-Dragoon-Modding/Severed-Chains.git
export JAVA_HOME="/absolute/path/to/a/jdk25/Contents/Home" # macOS JDK bundle
# Linux JDKs generally use /absolute/path/to/jdk25 instead
"$JAVA_HOME/bin/java" -version
./gradlew --no-daemon --console=plain clean build
```

The initialization checkout is `/Users/markmurray/Downloads/Development/legend-of-dragoon-definitive`. Downloads is writable for this session; move the checkout to a permanent development folder later if desired, before cleaning Downloads.

A temporary Amazon Corretto 25.0.0.36.2 JDK was used for validation, matching upstream's pinned `download-java` version. It is located at `/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home`, with `GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle`. Temporary storage is disposable; install/configure a maintained JDK 25 outside the repository for ongoing development. Neither the runtime nor cache is tracked.

Existing clones need `git submodule update --init --recursive`. Ordinary builds compile Skurfa into `build/libs/mods/Skurfas-HDR-Backgrounds-v1.1.0.jar`, including its artwork and notices. Building requires the initialized submodule; do not use GitHub’s parent-only source ZIP for an inclusive checkout. To play with original backgrounds, remove this JAR from a local package before launch. Faithful preset automation is still planned.

## Steam Deck package configuration

Upstream CI uses:

```sh
./gradlew --no-daemon --console=plain clean build -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

This selects Linux x64 dependencies and the Steam Deck launcher. Output goes to `build/libs/`, including the game JAR, dependency directory, launcher, download helper, graphics, patches, languages, license and credits. A Mac can compile/package this target; that does not execute or validate Linux native libraries. Use clean builds between platforms to avoid carrying support files across output directories. `updaterJar` is part of the supported packaging process.

## Game files and later execution

Compilation is separate from play. Provide your own supported disc images privately in the top level of `isos/`; discovery does not recurse through subfolders. The engine extracts them into `files/` at runtime. Do not add either directory's contents to Git. `saves/` is likewise local. The initial build milestone did not use images or launch the engine. On 2026-10-09 the owner supplied four BIN/CUE pairs locally; expected US disc IDs were checked and bytes preserved during folder cleanup. A later integration session extracted all four discs and passed Mac startup/gameplay smoke tests; see [INTEGRATION_ALPHA.md](INTEGRATION_ALPHA.md).

The [community Steam Deck guide](https://legendofdragoon.org/guides/setup-steamdeck/) describes extracting the portable package in Desktop Mode, providing disc images, checking executable permissions for `launch` and `download-java`, and adding `launch` as a non-Steam game. Its first-run flow requires network access for Java and time for extraction. These instructions have not been exercised here; Definitive installation automation remains future work. A relocated package needs its Steam shortcut path updated.

Do not launch the game, UI tests, or change desktop focus without the owner's permission. Upstream recommends assertions for gameplay; audit launch arguments before relying on assertion/heap flags (the checked-in Steam Deck script places `-Xmx2G -ea` after the main class).

## Test controls and CI

Ordinary builds compile engine test sources but skip the `test` task explicitly. `gradle/gameplay-tests.gradle` enables only the supported EngineBootTest suite with `-PrunTests` or `-PrunTests=true`; `-PrunTests=false` skips it and invalid values fail. An opted-in task requires the private extraction marker `files/version` before tests start. ExampleTest remains excluded as an interactive sandbox. This policy applies to Gradle, not direct IDE JUnit execution.

After permission and complete private game extraction, use the command documented in [E2E_TESTING.md](../../src/test/E2E_TESTING.md). Do not run that command for headless validation. Instead run:

```sh
python3 scripts/verify-test-controls.py
```

This runs a synthetic JUnit fixture that shares the production Gradle policy and has no engine classes, game assets or window code. It verifies skipping, explicit opt-in, prerequisite rejection and sandbox exclusion.

`.github/workflows/build.yml` builds macOS and Linux x64/Steam Deck packages with the wrapper, then verifies the controls with this fixture. It uses read-only repository access, pinned official action commits and JDK 25. It does not launch gameplay, create releases, upload binaries, publish Maven packages or use upstream service credentials. Build-only CI results and limitations are recorded in [BUILD_CONTROLS.md](BUILD_CONTROLS.md).

## Publication hygiene

Retain upstream history, LICENSE, CREDITS, credits.txt and per-file notices. New source modifications must identify changes and retain applicable licensing; distribute corresponding source alongside future covered binaries. Artwork has its own provenance and terms; upstream's code license does not grant rights to game images or third-party packs. Verify each pack's redistribution permission and attribution before bundling. Skurfa is linked as a pinned public submodule with source and runtime artwork. Preserve its LICENSE and THIRD_PARTY_NOTICES.md; its MIT license does not relicense derivative artwork. See the integration record for build output and version pins.

Only explicitly stage source and documentation. Never stage images, extracted assets, saves, tokens, environment files, private configs or runtimes. The public Skurfa gitlink is intentionally tracked; private retail disc/extraction files and local mod binaries remain ignored.
