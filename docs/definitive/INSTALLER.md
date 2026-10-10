# Guided installer and launcher

Latest delivery recovery: [Deck installation discovery, maintenance, discs, shared input and direct Steam launch](DECK_RECOVERY.md).

The [delivery reliability repair](DELIVERY_RELIABILITY.md) records the reported Deck failure, confirmed fixes, diagnostics and remaining device checks.

## Steam Deck Desktop Mode

Download **Install-Definitive.desktop** from the alpha release linked at the top of the README. In Dolphin, right-click it, open **Properties → Permissions**, enable **Is executable**, then open it and allow execution if KDE asks. The bootstrap opens a terminal while preparing the graphical installer. No terminal typing, system package installation, GitHub sign-in, or game download is needed. The entry point is a small text file; it verifies and downloads the roughly 9 MB portable installer. First use also downloads a checksum-verified Java 25 runtime into the user's cache. The engine, HD artwork and dependencies are a separate, larger download.

Operation phases and full errors are saved to `~/.cache/legend-of-dragoon-definitive/installer.log`; the stopped screen provides **Show error details** and a retry action. Progress shows actual download/copy sizes and extraction tasks rather than a bouncing bar. Startup output is saved to `~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log`; later preparation details are in `installer-bootstrap.log` in that folder. Download and checksum failures keep the terminal open until Enter is pressed. If KDE has not executed the file, no bootstrap log can be created; executable/trust handling remains a separate Deck check. The [bootstrap correction](BOOTSTRAP_FIX.md) records the reproduced quoting error and regression coverage.

1. **Install:** use the default `$HOME/Games/Legend-of-Dragoon-Definitive` (`/home/deck/Games/Legend-of-Dragoon-Definitive` on a standard Deck), or choose another location. A compatible platform package from `gideonidoru/legend-of-dragoon-definitive` is preferred. If no matching release exists, the installer recursively clones this repository and uses its Gradle wrapper to build the edition. An unavailable network or invalid release metadata produces an error instead of silently substituting code.
2. **Your discs:** select four US BIN/raw ISO images, any mixture of ZIP/RAR/7z containers containing them, or use previously imported discs. The installer identifies the disc headers, excludes unrelated utilities and patches, preserves originals, verifies copies, and runs the upstream extraction pipeline privately without opening the game. For archives, only image candidates are unpacked into owned staging; archive paths never become destination paths.
3. **Steam:** optionally add a non-Steam shortcut. The installer requests a graceful Steam exit, waits for it to close, adds and verifies the shortcut, then restarts Steam. It never force-kills the client. A blocked exit or failed restart shows an error with a retry path. If there are multiple local accounts, select one. Existing shortcuts are preserved and the original `shortcuts.vdf` is backed up. A verified installation-complete screen precedes this choice. Successful Steam addition and **Finish without Steam** close the installer. Return to Gaming Mode afterward.

Use the Steam shortcut or **Play Game.sh** for the launcher. **Play** is its primary action. It automatically checks our repository's compatible release assets without blocking offline play. Installing an offered update is a separate action. Artwork/mod preferences and restore are secondary. Gameplay presets are selected inside the game when creating a campaign.

Touch targets are 48–60 pixels; the file picker has 54-pixel rows, tap selection without Ctrl-click, and drag scrolling. D-pad/left stick navigate, A selects, B goes back/cancels, and shoulder buttons page through files. Native SDL gamepad events and keyboard navigation share UI actions. Touch, keyboard and Steam's Desktop Mode pointer controls remain available. Direct controller operation in Desktop Mode depends on Steam exposing a gamepad rather than only its desktop mouse mapping. The native shortcut disables desktop-controller substitution. Physical Deck controller/reconnect/overlay behavior remains a device acceptance item, not proven by Mac or CI tests.

## Disc support and space

This alpha matches upstream's four **US retail, raw 2352-byte sector** disc reader: SCUS94491, SCUS94584, SCUS94585 and SCUS94586. A filename ending in `.iso` does not establish its sector format. Cooked 2048-byte ISO, CHD, encrypted archives and incomplete multi-volume archives are rejected with actionable errors. CUE files are unnecessary. Existing imported files are never silently replaced.

Keep enough free space for the engine, a private copy of the four images, extracted data, temporary archive staging, and retained updates. Allow roughly 12 GB free for initial setup, in addition to source discs already present; exact usage depends on input compression. Updates retain previous versions and data snapshots. Source-build fallback also needs wrapper/dependency caches, `git`, `curl` and `tar`; stock SteamOS Desktop Mode is the target, not a Flatpak sandbox.

## Installation layout and recovery

- `releases/<package-id>/`: immutable, verified engine/support files and paired bundled mods.
- `isos/`: imported private images; never part of a release package.
- `data/data-<uuid>/`: saves, settings, optional custom mod JARs and private texture packs.
- `workspaces/<package-id>-<data-id>/`: per-version extraction and runtime links.
- `snapshots/snapshot-<uuid>/`: verified pre-update copies of private data.
- Stable root launcher scripts route through the active manager, including after restoring a prior version.

An exclusive operation lock prevents overlapping installation, import, extraction, restore and managed gameplay. Package inventory, platform and SHA256 checks complete before atomic activation. The ZIP download digest comes from GitHub release metadata; manifests establish integrity, not an independent publisher signature. Only trust packages from this project's release/source distribution.

Restore reactivates the previous engine with a fresh copy of its verified pre-update saves/settings/mods, and its artwork preference. Newer data is retained separately. Extracted data stays paired with its engine version. A failed operation can leave owned inactive staging/source/log directories for diagnosis; it never activates an unchecked payload. A stopped extraction can be retried with **Use installed discs**. Startup locks are released by the operating system after all owning processes end; the regular lock files need no deletion. A directory lock left by an older installer is rejected with a recovery message: close older installers before removing only that empty legacy directory. Existing unowned installation directories and unexpected links are rejected.

## Building and assembling delivery

```sh
./gradlew --no-daemon --console=plain clean build definitivePackage portableInstaller -PreleaseTag=<release-tag>
# Linux / Deck target:
./gradlew --no-daemon --console=plain clean build definitivePackage portableInstaller \
  -Pos=linux -Parch=x86_64 -Psteamdeck=true -PreleaseTag=<release-tag>
python3 scripts/verify-disc-archives.py
python3 scripts/assemble-installer.py
```

The assembly script stamps the versioned entry script and desktop launcher with the portable ZIP's exact SHA256. Re-run it after changing the UI/bootstrap package. Do not upload a different ZIP beneath those hashes. The desktop entry and source script are tiny transferable entry points; the portable ZIP works when unpacked and its `Install.sh` is run. Public release packages contain source-correlated engine and mods, notices, libraries and a portable 7-Zip helper, never local Java runtimes, discs, saves or extracted retail files. Preserve the release's corresponding source commit and recursive Skurfa revision.

7-Zip 26.04 is downloaded only into generated build output from the [official downloads](https://www.7-zip.org/download.html); its pinned GitHub SHA256 and bundled License.txt are retained. RAR and 7z reading uses that separate executable; ZIP reading uses Java. Steam shortcut serialization implements the binary KeyValues protocol independently, with [Valve's format implementation](https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/tier1/KeyValues.cpp) as a reference. The library edit happens only while Steam is closed, consistent with the [SteamTinkerLaunch author's integration guidance](https://github.com/sonic2kk/steamtinkerlaunch/wiki/Add-Non-Steam-Game). SDL input uses the [SDL3 gamepad API](https://wiki.libsdl.org/SDL3/CategoryGamepad), polled on the Linux main thread without a video window or desktop input injection.

## Verified / still required

On October 9, 2026, the Mac Studio (AVALON, Apple Silicon, macOS 27.0.1) built the installer using Corretto 25 and Gradle 9.1.0. Seventeen initial headless delivery tests passed, including corrupt/traversal/duplicate packages, immutable updates and rollback, data retention, locks, disc identification/copying, ZIP filtering, Steam backup/idempotence/corruption, release-source validation, UI renders and controller mapping/dead zones. Native original synthetic RAR4/7z fixtures are checked separately. The owner's four raw discs were imported with equal source/destination SHA256 and prepared in a fresh managed workspace without launching a game window. The first preparation attempt exposed missing packaged dependencies; task ordering and complete-payload verification were corrected before the successful retry.

These results establish installer/backend behavior and compilation on Mac. They do not establish physical Deck performance, touch feel, native controller input/reconnect, Steam library rendering, long playthrough compatibility or AAA quality. The Deck acceptance pass remains required. Archived fixture files and build/runtime caches stay outside Git.

## Final alpha checks

2026-10-09: 24 headless tests passed across installer/recovery, disc and Steam fixtures, controller mapping/layout, preset contracts and texture validation. A clean Mac package build passed; five authorized native Mac gameplay checks passed. The hosted Linux/macOS installer checkpoint retained release archives successfully. The final release is assembled from the subsequent checked source revision, with hashes stamped into the tiny entry point. Linux Desktop Entry parsing has a native harmless invocation fixture; it does not establish physical KDE/Deck behavior. Actual touch, native controller hotplug, Gaming Mode, performance and real-device recovery remain pending.


## Recovery alpha checks

The recovery build at `d1598b705a6fe37716a4ad603d0ddfacb4b09e92` passed Linux and Mac packaging, 45 delivery checks on each platform, a separate actual-window lifecycle check on Linux, eight interruption/concurrency cases and thirteen release-publication cases on both platforms. The downloaded Mac package repeated real four-disc preparation in a private QA installation. Exact packages, source, checksums and publication evidence are recorded in [Delivery reliability](DELIVERY_RELIABILITY.md). These results do not replace the physical [Deck acceptance protocol](DECK_TEST_PLAN.md).
