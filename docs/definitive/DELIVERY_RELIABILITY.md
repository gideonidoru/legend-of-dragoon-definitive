# Installer, updater and launcher reliability

## Report and confirmed defects

On 2026-10-09 the owner reported that the guided installer on Steam Deck did not create the expected folder, install files or add a Steam shortcut. Preparation used a bouncing bar with generic text, the disc picker obscured progress, and Finish remained open. The bootstrap log contained Java Unsafe warnings, while the preparation log was empty. Those warnings do not identify the installation failure.

The exact cause of the missing Deck installation remains unconfirmed because the original worker had no persistent exception log. The actual release download and installation succeeded in an isolated Mac test; that does not establish that the same operation succeeded on the Deck.

Confirmed source defects were reproduced by driving the real setup buttons: progress stayed indeterminate and Finish changed to the launcher instead of closing. Background exceptions were shown only in a small footer, without a stack trace or a dedicated failure state. Relative destinations could resolve inside temporary bootstrap storage. These are corrected independently of the still-unconfirmed device-specific trigger.

## Required behavior

- Installation advances only after the active package inventory, artwork/dependencies, data folder and executable launchers are verified. Launcher checks happen before publishing new active state.
- A complete screen follows successful disc preparation, with the installation folder and optional Steam integration. Adding the shortcut successfully or finishing without Steam verifies readiness and closes setup.
- Errors replace the body with an explicit stopped screen, the cause, a retry action and readable log details. A failed operation cannot be presented as completed.
- The file picker is disposed before its callback starts preparation. Cancel closes the picker and re-enables the installer. A failed Steam addition keeps setup available for retry or finishing without Steam.
- Progress describes the current task: downloaded/copied MB, package files being unpacked, disc checks, and upstream conversion/writing counts. The steady bar represents completed phases, not a time estimate. Work with unknown totals does not fabricate a percentage or ETA. Elapsed time continues while waiting.
- Only complete, platform-matched, checksum-verified release packages activate. Interrupted, oversized, HTTP-error and corrupt downloads leave the active version/data unchanged and remove the temporary download.
- Updates retain pre-update data and the previous verified package; rollback reactivates the previous engine with copied prior data, retaining newer data separately.
- The launch scripts retain errors and exit nonzero on startup failure. The managed game process writes a per-workspace log. Failed starts release the operation lock. Known original launch scripts can be upgraded atomically; owner-customized scripts are preserved.

The UI copy describes actions directly. Paths wrap on handheld screens. Relative destinations are rejected and `~/` is expanded before use. Remote downloads have connection, body and inactivity limits; source cloning/building and disc preparation have wall-time limits. Initial network setup checks free space; disc copying checks space against the selected images. These checks improve failure handling; they cannot guarantee hardware, storage, network or Steam behavior under every condition.

## Logs and recovery

- `~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log`: starting the downloaded entry point.
- `~/.cache/legend-of-dragoon-definitive/installer-bootstrap.log`: portable installer and Java preparation.
- `~/.cache/legend-of-dragoon-definitive/installer.log`: operation starts, phases, completions and full exceptions. **Show error details** reads a bounded tail of this file.
- `<installation>/launcher.log`: Java/bootstrap/manager startup, including failures before a window exists.
- `<installation>/workspaces/<version>-<data>/preparation.log`: private upstream disc extraction status/errors.
- The same workspace's `launcher.log`: managed game-process output.
- A source fallback retains its `.source-build-*/setup-build.log` and checkout for diagnosis/corresponding source access.

Use **Return and retry** after resolving the displayed cause. If import succeeded and extraction failed, return to the disc step and choose **Use installed discs**; existing images are retained. Close Steam before adding its shortcut. A failed shortcut operation does not declare Steam integration successful or overwrite an unexpectedly changed library. Existing shortcuts are backed up.

Download a new `.desktop` from the corrected release. Previously downloaded entries are pinned to their original script/checksum and cannot silently become a different installer.

## Verification

Development host: Apple Silicon Mac Studio, macOS 27.0.1, Corretto Java 25, Gradle wrapper 9.1.0. Local checks use headless Swing rendering and isolated synthetic packages/Steam data. No test edits the owner's Steam library or starts retail gameplay.

The first regressions reproduced two failures before correction. The corrected actual install button creates a verified active package and `Play Game.sh`; corrupt-package tests keep activation absent and show a visible retry/error screen. Finish has a close callback after readiness verification. Layout tests check every action at 1024/1100 by 700 and render install, disc, success, working and failure screens for inspection.

A private real-disc pipeline installed the corrected Mac package, checked its inventory/HD mod, copied and SHA256-verified all four owner-supplied images, completed real upstream extraction, verified the launchers and wrote a binary shortcut into a separate fixture library. It completed in about 14 seconds on this Mac's local storage with the runtime/dependencies already present. That is not a Deck performance estimate. The source revision for this first local run was the working tree based on `f263cfe0d32face4933ca8f140fdfaedce3baf59`.

Reproduce public checks:

```sh
./gradlew --no-daemon --console=plain build definitivePackage portableInstaller \
  -PreleaseTag=definitive-alpha-2026-10-09-installer-fix
python3 scripts/assemble-installer.py
python3 scripts/verify-desktop-entry.py
python3 scripts/verify-portable-bootstrap.py
# Linux CI: real Swing window lifecycle without a physical desktop
xvfb-run -a ./gradlew --no-daemon --console=plain managerWindowTest \
  -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

`InstallerFlowTest` drives real buttons/workers, verifies actual installed outputs and failure UI, checks destinations and screen fit. `InstallerWindowTest` uses a Linux virtual display to verify the picker is closed before its callback and Finish disposes the actual window. `UpdateFailureTest` exercises the actual transfer/checksum/activation path with isolated HTTP transport; it covers errors, corruption, interruption, oversized metadata, successful activation/data retention and rollback. `LauncherFailureTest` uses non-game fixtures to check persistent startup/game-process errors, lock release, linked-log rejection and launcher repair/customization retention. Existing package, archive, restore, data and Steam fixtures remain required.

Hosted Linux/macOS builds, the virtual window checks, the final published source/assets and their digests are recorded below when complete. Physical Deck installation, touch/controller operation, Gaming Mode library visibility, interrupted real-device updates and long-term gameplay remain acceptance checks. Compilation, fixture success and a Mac extraction pass do not establish those results.

Reinstallation also repairs the managed root manager and Java bootstrap from the verified package. Readiness checks their contents against a matching installed inventory, rather than only checking that the files exist. The format-1 root manager must remain compatible with retained format-1 engines so rollback can still route to them; custom launcher scripts are retained. A regression damages both managed files, rejects readiness, then requires repair with saved data unchanged.

Both startup scripts register cleanup immediately after acquiring their locks. Isolated fixtures force temporary-directory creation to fail, then retry; neither lock remains behind. These deterministic repairs do not establish the original Deck failure's cause.

The final local build passed 45 delivery checks, with one actual-window test intentionally skipped on the Mac. The corrected real-disc extraction was repeated successfully in about 11 seconds, including named loading/conversion/writing tasks. The sandbox prevented the default home-cache log during that private probe; isolated logging regressions use an explicit writable test log and pass. Bootstrap download, checksum, runtime, startup and temporary-storage failure fixtures pass; native Desktop Entry parsing and actual-window checks are delegated to Linux CI.

Managed bootstrap replacement stages and verifies the complete pair and prior recovery copies before publication. If publication fails, the old pair is restored; a forced failure on the second file verifies the old launcher inventory, active state, saves and operation lock remain usable. If recovery itself encounters a storage error, its copies are retained and their paths are included in the exception rather than discarded.
