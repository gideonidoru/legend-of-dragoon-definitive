# Installer alpha — 2026-10-09

Published tag: `definitive-alpha-2026-10-09` (prerelease).

Engine/manager packages are compiled from `f2ab10fe249b5120365dbfe1f376867b62fb8cd5`; the release tag also records the subsequently stamped tiny entry wrappers. No engine/manager source changes occurred between compilation and wrapper assembly. Upstream `fba1543543865e29ee572f479003d9b47158eeb3`; Skurfa `3c9e4b3ecefc31cb32fd1281a7857f3a08f56081`. JDK 25; Gradle 9.1.0; engine AGPL v3 preserved.

Hosted build [37995788699](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/37995788699) passed native Linux x64 and macOS ARM64 builds, 24 headless tests, the seven test-control scenarios, native RAR4/7z import fixtures and native Linux Desktop Entry parsing/harmless launch. Both downloaded platform packages passed complete per-file verification locally; release inventory excludes discs, extracted retail files, saves, private packs and Java runtimes. Five authorized native Mac gameplay checks passed, including detached presets, configuration round trips, sparse preset reset and corrupt remembered-preset fallback.

| Asset | SHA256 |
| --- | --- |
| Definitive-Installer.zip | `df0753719feda6e30dfd3d525f608306b39eb143f859c05cd605db8042cc09be` |
| Install-Definitive.sh | `70589c62d2f9b7d876653fbf774c6554c6ce04f6f542dd91ecdfe9541d5d343a` |
| Install-Definitive.desktop (bootstrap correction) | `bcabf4056b9dcb487b2229d5f9181caeaf41729ee00d9fa9bae74f9525395575` |
| Linux x64 package | `142f09673cec404dff9a347a1d689bb300710b9bacf00b4f6a6d9016cb7faaf7` |
| macOS ARM64 package | `0f1651051414871692b34c57612e7d6428152251948aaf789bb3549d96a17adf` |

The downloadable entry verifies the script, which verifies the approximately 9 MB portable UI. Java bootstrap verifies the official Corretto archive; engine downloads use GitHub asset digest plus the package's internal inventory. Updates retain private generations and verified snapshots. No game input is provided by the project.

The [desktop bootstrap correction](BOOTSTRAP_FIX.md) replaces the tiny entry and updates its checksum inventory. The original desktop asset hash was `016e3f8dd302f9f06e640950d39e69d72d9c6b20bf1bfdfea542d0b743d54081`; the engine, portable ZIP and entry script remain byte-identical. Source correspondence for this wrapper correction is recorded separately from the original release tag.

Physical Steam Deck controller/touch, Gaming Mode, reconnect, long-play performance and update recovery remain pending. The texture pilot is off by default; neural candidates and multi-palette models are not shipped. Faithful is a settings target, not bit-perfect retail equivalence or a complete campaign validation.

## Review

Standards review identified three correctness issues: shortcut quoting, sparse preset leakage, and unbounded response-body stalls. All were corrected with native parser, preset and stalled-body checks. No documented coding-standard violation was established.

Spec review identified preset leakage and download recovery, then an invalid remembered-preset fallback edge case. All were corrected and the Mac regression suite passed. Remaining requirements concern physical hardware/player evidence and the ongoing visual enhancement work, not a claim of finished AAA quality or community approval.
