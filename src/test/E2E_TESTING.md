# E2E Testing - Severed Chains

Definitive build/test controls updated 2026-10-09. These tests launch the game; obtain the owner's permission before running them. Ordinary builds and CI do not run them.

End-to-end tests that boot the full game engine and exercise gameplay scenarios (battle, menus) inside a real LWJGL/SDL window.

Tests are not headless. A display is required (or a virtual framebuffer on CI).

## Prerequisites

- Java 25 - set JAVA_HOME explicitly to a JDK outside version control; the wrapper does not install a development JDK.
- Privately unpacked game files, including the completed extraction marker `files/version`. The Gradle test task fails before starting tests if the marker is missing. A fresh checkout's `files/.gitignore` is not extracted game data. Do not launch just to extract assets without permission.
- Windows tested. Linux/macOS should work but untested

## Running from IntelliJ

### One time project setup

1. Open the `Severed-Chains/` directory in IntelliJ
2. Set the Project SDK to the bundled JDK: File > Project Structure > Project > SDK > Add SDK > JDK and select `jdk25.0.0_36/`
3. Set Gradle JVM to the same JDK: File > Settings > Build Execution Deployment > Build Tools > Gradle > Gradle JVM

## Running from Command Line

### Windows PowerShell

```powershell
$env:JAVA_HOME = "$PWD\jdk25.0.0_36"
.\gradlew.bat test --tests "legend.game.EngineBootTest" -PrunTests --rerun-tasks
```

### Linux/macOS

```bash
export JAVA_HOME="$PWD/jdk25.0.0_36"
./gradlew test --tests "legend.game.EngineBootTest" -PrunTests --rerun-tasks
```

`-PrunTests` (or `-PrunTests=true`) explicitly enables the supported `EngineBootTest` suite. Absent or `-PrunTests=false` skips the test task; other values are rejected. `--rerun-tasks` forces re-run even if inputs have not changed. `--tests` can narrow the suite to a method. `ExampleTest` remains excluded: it is an interactive manual sandbox that depends on saves and may wait indefinitely.

The task checks only the extraction marker, not completeness of every asset. Complete extraction and valid private files are still required. The Gradle policy does not control IntelliJ's direct JUnit gutter runner; use the Gradle task with these controls instead.

For safe headless verification without real game classes/assets:

```sh
python3 scripts/verify-test-controls.py
```

This uses an isolated synthetic JUnit fixture and verifies seven scenarios: default off, explicit false, missing private files, bare/true opt-ins, invalid value, and excluded sandbox. It does not run engine tests.

## Test Overview

Tests are ordered by method name and share a single engine instance (`@TestInstance(PER_CLASS)`).

- `test1_freshStateIsValid` new game state has correct gold, party size and chapter
- `test2_battleStartsAndMonsterHasHp` encounter 0 (Berserk Mouse) loads and monster has full HP
- `test3_guardDoesNotChangeMonsterHp` selecting Guard does not damage the monster

## Architecture

```
src/test/java/legend/game/
  EngineBootTest.java - JUnit 5 test class (3 tests)
  ExampleTest.java    - sandbox for manual testing and experimentation
  Bootstrapper.java   - boots engine on a background thread
  Harness.java        - state injection, battle control, game state setup
  Wait.java           - condition polling with timeouts
  Input.java          - SDL keyboard event injection
  TestConfig.java     - encounter ID, stage, RNG seeds

src/test/resources/
  log4j2-test.xml     - routes E2E logs to e2e-test.log
```

### Design notes

The script runtime must stay alive during state transitions. `Harness.transitionToEngineState()` only sets `engineStateOnceLoaded_8004dd24` and lets the main loop handle the rest. No calls to `SCRIPTS.stop()` or `SCRIPTS.clear()`.

`battleInitialCameraMovementFinished_800c66a8` is forced to true to skip the intro camera pan and save about 5 seconds per battle.

`Harness.startBattle()` waits for the battle's internal `loadingStage` (read via reflection) to reach 21 or higher which is when `battleTick` begins and the combat HUD is interactive.

All volume configs are set to 0 in `@BeforeAll` before any key press so FMVs and music never produce sound.

## Logs

- `e2e-test.log` - all [E2E] tagged log output from test classes
- `debug.log` - full engine log same as a normal game run

Both files are created in the repo root.

## Troubleshooting

- Missing extraction marker: the opted-in task fails with a `files/version` prerequisite error before launching tests.
- Gradle skips tests: you forgot `-PrunTests`
- Timeout on engine loading: first run may be slow while shaders compile. Increase timeout in `Bootstrapper.java`
- Timed out waiting for player turn: battle may not have loaded fully. Check `e2e-test.log` for the last loading stage
- SDL window does not appear: ensure a display is available. On headless CI use `xvfb-run` on Linux
