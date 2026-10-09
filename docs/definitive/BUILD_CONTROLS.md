# Build-only CI and explicit test controls — 2026-10-09

## Change

Replaced the two inherited publishing workflows with `.github/workflows/build.yml`. Original files remain in upstream Git history. New CI builds macOS and Linux x64/Steam Deck packages using the checked-in Gradle 9.1.0 wrapper and pinned Temurin 25.0.0+36. Official checkout/setup-java actions are pinned to full commits. The job records its environment, compiles engine/test sources, packages support files, and exercises a separate headless test-control fixture.

The workflow uses `contents: read` and does not persist checkout credentials. There are no upstream service secrets, metadata scrapers, Maven publications, release writes, binary uploads or gameplay invocations. Triggers are source changes on main, pull requests, and manual dispatch. Documentation-only changes skip automatic runs; manual dispatch remains available. Concurrency cancels superseded runs; each platform job has a 20-minute timeout.

`build.gradle` now applies `gradle/gameplay-tests.gradle` instead of unconditionally excluding every test:

| Input | Behavior |
| --- | --- |
| No runTests property | Test task explicitly skipped; test sources still compile |
| `-PrunTests=false` | Test task skipped |
| `-PrunTests` / `-PrunTests=true` | Only supported EngineBootTest suite eligible; private extraction marker required |
| Any other value | Fail with a property-value error |
| Opt-in with missing `files/version` | Fail before test execution; fresh checkout placeholder is insufficient |
| Interactive ExampleTest | Always excluded from the supported suite, even with broad selection |

The file marker is a prerequisite check, not validation of all assets. It is written by the upstream unpacker after extraction; private valid game content and execution permission remain necessary. Gradle controls do not govern direct IDE JUnit runners. Future headless unit suites should use their own task rather than mixing windowed gameplay into default builds.

## Local verification

AVALON Mac Studio; macOS 27.0.1 ARM64; temporary Corretto 25.0.0.36.2; Gradle 9.1.0. No engine or updater launched.

```sh
export JAVA_HOME=/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home
export GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle
./gradlew --offline --no-daemon --console=plain clean build
./gradlew --offline --no-daemon --console=plain clean build -Pos=linux -Parch=x86_64 -Psteamdeck=true
python3 scripts/verify-test-controls.py
```

- macOS package build: exit 0, BUILD SUCCESSFUL in 6s, test SKIPPED.
- Linux x64/Steam Deck package configuration on Mac: exit 0, BUILD SUCCESSFUL in 6s, test SKIPPED. This is packaging evidence, not device execution.
- Headless fixture: seven scenarios passed, including bare/true opt-in execution with one successful synthetic JUnit test per positive case; missing extraction and invalid value rejected; manual sandbox excluded. The fixture has no engine dependencies, native libraries, window code or game assets. Its temporary synthetic `files/version` marker is not game content and never enters the real engine checkout.
- actionlint 1.7.12: workflow validation passed.

Evidence (trailing whitespace normalized): [macOS build](evidence/build-controls-macos.txt), [Deck package configuration](evidence/build-controls-deck.txt), [fixture scenarios](evidence/test-controls-probe.txt). Detailed fixture command logs are generated in ignored `build/test-controls/`.

## GitHub verification

Pending first hosted run. Repository Actions remains disabled while the safe workflow is prepared; enable it only after the publishing workflows are removed from main, then dispatch the new build-only workflow. Record the exact hosted run and results here after completion.

## Remaining limitations

No gameplay tests have executed. No real Steam Deck has been tested. Upgrade/save rollback, compatible release assets and version/channel stamping remain separate release work. This milestone does not publish a playable Definitive release or validate faithful presets.
