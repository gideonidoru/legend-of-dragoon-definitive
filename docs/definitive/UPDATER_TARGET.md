# Updater repository target — 2026-10-09

Requested change: point update discovery at the owner's Definitive repository.

`src/main/java/legend/core/Updater.java` now queries:

```text
https://api.github.com/repos/gideonidoru/legend-of-dragoon-definitive/releases
```

This replaces the official Severed Chains feed. A dated source comment identifies the project modification. Upstream history, license and notices are retained. Keep this constant during future upstream merges.

Release parsing, channel/timestamp filtering, platform asset selection, downloads and update application are unchanged. Update checks require a non-null stamped `Version.TIMESTAMP`; matching release tags start with `Version.CHANNEL` and have a newer timestamp. Manual/IDE snapshots still skip update checks. This change does not create a release or enable publishing workflows.

## Verification

- Authenticated GitHub account: gideonidoru.
- GitHub repository releases endpoint accessible; it returned zero releases at verification time.
- Headless supported Steam Deck package build passed, exit 0, BUILD SUCCESSFUL in 6s (11 actionable tasks: 10 executed, 1 up-to-date).
- Inspected the compiled Updater.class constant using javap: it contains the Definitive endpoint.
- `:test NO-SOURCE`: no gameplay tests executed. No game, updater window or browser launched; no update downloaded or applied.
- Build environment: AVALON Mac Studio, macOS 27.0.1 ARM64; temporary Corretto 25.0.0.36.2; checked-in Gradle 9.1.0 wrapper; dependencies cached from baseline validation.

Build command from repository root:

```sh
JAVA_HOME=/private/tmp/lod-definitive-tooling/amazon-corretto-25.jdk/Contents/Home \
GRADLE_USER_HOME=/private/tmp/lod-definitive-tooling/gradle \
./gradlew --offline --no-daemon --console=plain clean build \
  -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

[Full build output](evidence/updater-target-build.txt) (trailing whitespace normalized). Existing deprecated/unchecked API and native-access warnings remain.

## Remaining release work

Publish compatible version-stamped releases with matching tags and platform assets only after defining package/mod version pairing and testing upgrade/restore. Source feed redirection is complete; actual update installation, rollback and Steam Deck execution remain unverified. Actions was disabled for this updater-target milestone; build-only CI was subsequently enabled as recorded in [BUILD_CONTROLS.md](BUILD_CONTROLS.md). See UPSTREAM.md and BACKLOG.md for release boundaries.
