# Steam Deck-first roadmap

Deliver visible improvements through mods, configuration and packaging. Keep faithful mode available, with no engine rewrite. This is a prioritized plan, not a promise of already working features.

| Priority | Deliverable | Acceptance evidence / dependency |
| --- | --- | --- |
| P0 / milestone 1 | Public source repository, preserved upstream/license, baseline builds and documents | Validation report; no engine behavior changes |
| P1 / next | Reliable Deck installation and baseline measurement | Native Linux x64 portable package, clear private disc import, executable permissions, Steam shortcut guidance, actionable first-run errors, no shell typing after setup; fresh Deck install and upgrade preserve saves/mods/config; fix release/updater policy first |
| P1 / visible art | Integrate existing HD artwork as an optional pack | Confirm author/version/permission; manifest and disk/cut mapping; representative backgrounds, foreground occlusion and transitions; original-art fallback; cap texture memory and compare frametimes; no new generated upscale pipeline |
| P1 / controls | Comfortable controller-only flow | Boot/title/options/save/load/battle reachable; appropriate glyphs, reliable confirm/cancel, remapping, deadzones; Steam Input on/off, reconnect and overlay do not double-input; test handheld and external controller |
| P1 / readability | Legible 1280x800 handheld menus | Font/contrast/spacing options; no clipping in inventory, combat, dialogue or long labels; compare photographed handheld reading at normal distance; retain original presentation option |
| P2 / convenience | Opt-in quick text, save convenience, encounter control and addition assistance | Reuse existing settings first, explain effects, record choices per campaign; faithful preset audit including retail defaults; save/load and disabling mods tested |
| P3 / measured rendering | Targeted renderer tuning and optional upscaling | Only after Deck baseline and HD-pack timings; bottleneck evidence and equal-scene A/B results; improve worst frametimes without broken UI/occlusion or altered timing; no engine rewrite |
| Release gate | Reproducible, licensed, maintainable package | Source + license + attribution, no disc/assets/saves/tokens/runtimes in Git, compatible update channel, asset-pack terms, Deck install/upgrade/gameplay evidence |

## Measurement plan before graphics expansion

Record Deck model (LCD/OLED), SteamOS, driver, engine SHA, JDK, pack versions, render settings, output resolution, frame cap and power limit. Use the same private save/route and warm/cold cache conditions. Include title/menus, a detailed field scene, transitions, busy battle/effects and FMV. Measure median, p95 and p99 frametime, stutter counts, CPU/GPU load, system/texture memory, startup/load latency and power/battery estimate. Test suspend/resume and extended play. Capture an original-art baseline, then a pack-only comparison before renderer changes.

Provisional goals: responsive stable 60 fps where the engine supports it (16.7 ms frame budget), with a tested stable 30 fps fallback (33.3 ms). Final targets depend on actual Deck data; do not advertise them from Mac compilation. Menu legibility, input reliability and save safety outrank a nominal FPS number.

## First implementation slice after this milestone

1. Configure a durable JDK 25 and replace imported publishing workflows with build-only CI; resolve updater policy before distributing fork binaries.
2. User supplies game files privately and separately authorizes gameplay/device tests. Establish an actual Steam Deck baseline.
3. Confirm the existing HD pack's author-approved artifact, terms, API compatibility and scene coverage. If unavailable, continue installation/controls/readability work while treating HD integration as blocked.
4. Prototype one reversible artwork adapter and one readable menu/control flow using established mod/config seams. Compare to faithful mode and baseline performance before broadening.
