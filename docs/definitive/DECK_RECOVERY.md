# Steam Deck installation and launch recovery

## Report and evidence

On October 10, 2026 the owner reported duplicate file-picker windows, missing selection checkboxes, doubled D-pad navigation, failure when selecting already installed discs, missing Steam artwork, and a white window in Gaming Mode. The same installation starts in Desktop Mode. Exit code 130 appeared after the owner pressed Steam's Stop button. The launch log also reported a 32-bit Steam overlay being ignored by the 64-bit process.

Three deterministic tests reproduced the original delivery behavior: identical disc reimport threw an error, the Steam Play script routed to `--manage`, and the shortcut icon was empty. The complete physical Gaming Mode failure has not been reproduced on the Mac Studio. A direct game entry removes the Swing/SDL manager from that path; this is a targeted correction, not proof that all device-specific launch problems are resolved. Code 130 after Stop is an interruption, not evidence of the original failure. The ignored overlay warning alone is also not evidence of that failure.

## Installation and maintenance

- The installer records its verified location in `~/.config/legend-of-dragoon-definitive/installation.properties`, including SD-card/custom destinations. On later starts it validates the ownership marker and retained data generation before selecting the recorded path. Stale, malformed, linked or unowned locations are rejected. The default remains `~/Games/Legend-of-Dragoon-Definitive`.
- A discovered installation offers **Reinstall** and **Uninstall**. Reinstall repairs a damaged release only from a completely verified package of the exact identity, retaining the previous folder as `release-recovery-<UUID>`. Arbitrary folders and custom launch scripts are not overwritten.
- Uninstall removes managed engine versions, generated workspaces, launchers, generated artwork and the matching Steam entry. **Delete ISOs** starts unchecked. Saves, settings, custom mods and pre-update snapshots remain. The location record remains so Reinstall finds and copies the retained private data. Uninstall requires the exclusive operation lock and refuses unexpected release/workspace targets before removal. Interrupted uninstall remains marked for Reinstall.
- ISO deletion, when chosen, also removes recognized disc recovery folders. Extracted game assets and managed engine recovery folders are removed with the game. Unknown files in the installation root and unrelated Steam files are retained.

## Discs

The installer inspects existing images before presenting the disc-selection step. A complete supported set gets **Use installed discs** and **Choose disc files**. Inspection validates the four US raw-image headers and records full SHA256 hashes. These are continuity/copy checks, not a claim that an image matches a published retail checksum database.

Selecting identical images again succeeds without copying or changing their timestamps. Different images require a confirmation before replacement; the old folder is retained as `disc-recovery-<UUID>`. Staging, content hashing and atomic directory publication prevent partial four-disc sets from becoming active. Changing disc contents invalidates the old extraction, retaining it separately before preparing fresh files. Already prepared, unchanged files are reused.

## Shared navigation and launch

The installer, launcher and updater use the same input arbiter. Keyboard events from Steam and direct SDL buttons/sticks share a duplicate-suppression window. Held directions wait 450 ms before repeating, then advance at most once per 180 ms. Confirm actions do not repeat. Releases, focus changes outside the app and disconnects reset the appropriate state. Keyboard text editing retains its normal behavior.

Each owner can open one file picker at a time. Rows contain real checkbox controls rather than selection glyphs; files remain selected across folders. Touch scrolling remains available. Use selected files disposes the picker before starting work.

`Play Game.sh` routes directly through the verified active manager's `--play` command to the native game. `Manage Installation.sh` opens the Play-first manager with automatic update checking and shared controller navigation. Steam does not need a Swing window before creating the game window. Steam's 32-bit overlay entry is replaced by its available 64-bit counterpart for the game; unrelated preload entries are retained. Explicit Steam Stop codes 130/143 are not shown as startup failures. Other failures remain nonzero, with the actual game-log tail copied into the outer launch log.

On Linux the installer and manager open maximized within the available display bounds. Fullscreen is enabled through the upstream native configuration format, with the original settings backed up before the fullscreen value changes. Other settings are preserved. The launcher exposes a **Fullscreen** preference. No renderer or gameplay behavior was rewritten.

## Steam artwork

Original North American cover key art featuring Dart, Shana and Rose is bundled in the manager/package, without runtime image downloads. The complete source composition is fitted into portrait, landscape, hero and icon surfaces; a separate transparent title label is provided. The existing matching shortcut keeps its app ID and unrelated fields while its missing icon is repaired. Repeating Add to Steam keeps one entry, avoids redundant library backups when nothing changed, and repairs missing grid files. Player-chosen grid artwork is retained.

Source: [Legend of Dragoon community merchandise archive](https://legendofdragoon.org/merchandise/), [original preserved image](https://legendofdragoon.org/wp-content/uploads/LODbox-expanded_EZG.webp). Original WebP SHA256: `15e076e65c41048055c27cb2c4ab404b4caeb8a1ca6825265bb84c26c92e2ce7`. Lossless format conversion to bundled PNG SHA256: `f1742c33e1e618e10c692b2cecc40f2bd79d9d4cd24727b1ebc54926b12e553e`. Artwork copyright belongs to Sony Interactive Entertainment; it is not relicensed under the project's AGPL code license or asserted to be public domain. Bundled at the owner's request.

## Validation and remaining gate

Local environment: Mac Studio, macOS 27.0.1, Corretto Java 25, Gradle 9.1.0. `deliveryTest` drives actual installation, repair, disc import/comparison, settings serialization, shell routing, isolated Steam library edits and uninstall/reinstall. The final local delivery suite contains **109 cases: 105 passed, zero failures, four window cases deferred**. Four real Swing window cases run separately in Linux CI's virtual display. Synthetic ZIP/RAR4/7z fixtures contain no retail assets. Artwork and maintenance screens are rendered for layout inspection. The local full build compiles and packages the engine without gameplay tests.

Required physical follow-up: launch the new package from Gaming Mode; confirm that the native game opens fullscreen; exercise slow taps, held D-pad, folder selection and controller reconnect; then reuse the existing discs through Reinstall. Mac and virtual-display checks do not establish physical Deck performance or gameplay. The private model/material studies remain outside this delivery release.

## Published build identity

Release: `definitive-alpha-2026-10-10-deck-recovery`. Source: `0a29faff2876ccfef515ac83ec029648b4d31240`. [Required build run 38061319093](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38061319093) passed all three jobs: synthetic visual fixtures, Linux x64/Steam Deck package and macOS package. Hosted runners used Temurin Java 25.0.0+36, Gradle 9.1.0, Ubuntu 24.04 and macOS 15.

Build command: `./gradlew --no-daemon --console=plain clean build definitivePackage portableInstaller -PreleaseTag=definitive-alpha-2026-10-10-deck-recovery`; Linux adds `-Pos=linux -Parch=x86_64 -Psteamdeck=true`. Linux window command: `xvfb-run -a ./gradlew --no-daemon --console=plain managerWindowTest -Pos=linux -Parch=x86_64 -Psteamdeck=true`. Both delivery reports contain 109 cases, zero failures and four deferred window cases; the separate Linux window report passed all four without skips. These are compilation and isolated delivery/window results, not a game or Deck gameplay run.

Downloaded hosted packages were unpacked and their complete manifest inventories verified with the Java 25 manager's `--verify` command. Both packages match the exact source, release tag and platform. Linux ID: `alpha-f1433d8e016b1afc`; macOS ID: `alpha-788d5f3a7b798235`. Both hosted portable installers match the local final installer byte for byte.

| Artifact | SHA256 |
| --- | --- |
| Linux x64 package | `f88d055609df3caf0ea6acd1504f2c0450217d7e9c4e7c9afdf7e77d4869a7d4` |
| macOS ARM64 package | `e305f7715dd5d74b1888ca31d25ac478ed63b33e838f169e35664bc5aa3420ae` |
| Portable installer | `a7c744120fa8976e90cbf667d952f99e10669fae0b1abb44873c5785d4b706bf` |
| Shell entry | `86d10ae6ad3e9f27f2790bf27aec5ced3146ee64bfdee0e90b3ee745a91b239b` |
| Desktop entry | `373c1705309ac66b9382f0ffeec2be18cdabf89df7f9e6b9831749087ea00293` |

The publication gate checks source/run/account identity, package manifests, entry-point pins and the complete six-file upload set before making the draft public. Older custom installations created before location receipts existed need their folder selected once; default-path installations are detected directly.

Publication and post-publication checks completed October 10, 2026. The desktop entry downloaded anonymously and matched the checked file byte for byte. The published portable manager selected and downloaded the public macOS package, checked its GitHub digest, installed it into a new isolated temporary folder and verified the complete inventory. A custom location receipt was recorded and rediscovered, and the generated Play script used `--play`. Logs, registry and installation files for this check stayed under `/private/tmp/lod-definitive-tooling/release-deck-recovery-oct10/`; no owner installation, real Steam library, discs, saves, game window or desktop focus was used. This host check does not establish Linux runtime or physical Deck acceptance.
