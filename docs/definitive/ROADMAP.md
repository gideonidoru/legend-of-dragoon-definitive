# Steam Deck-first roadmap

Full program scope and provisional 24-week gates: [PROJECT_PLAN.md](PROJECT_PLAN.md). Proposed implementation briefs: [BACKLOG.md](BACKLOG.md). First-14-day checklist: [SPRINT_0.md](SPRINT_0.md). This roadmap preserves the immediate player-impact ordering.

Deliver visible improvements through mods, configuration and packaging. Keep faithful mode available, with no engine rewrite. This is a prioritized plan, not a promise of already working features.

| Priority | Deliverable | Acceptance evidence / dependency |
| --- | --- | --- |
| P0 / milestone 1 | Public source repository, preserved upstream/license, baseline builds and documents | Validation report; no engine behavior changes |
| P1 / next | Reliable Deck installation and baseline measurement | Native Linux x64 portable package, clear private disc import, executable permissions, Steam shortcut guidance, actionable first-run errors, no shell typing after setup; fresh Deck install and upgrade preserve saves/mods/config; fix release/updater policy first |
| P1 / visible art | Integrate existing HD artwork as an optional pack | Evaluate Skurfa v1.1.0 standalone mod first; verify artifact hashes/terms and disk/cut mapping; representative backgrounds, foreground occlusion and transitions; original-art fallback; cap texture memory and compare frametimes; reuse this background pack before generating replacements |
| P1 / controls | Comfortable controller-only flow | Boot/title/options/save/load/battle reachable; binding-aware action hints adapted from QoL ideas, appropriate glyphs, reliable confirm/cancel, remapping, deadzones; Steam Input on/off, reconnect and overlay do not double-input; test handheld and external controller |
| P1 / readability | Legible 1280x800 handheld menus | Font/contrast/spacing options; no clipping in inventory, combat, dialogue or long labels; compare photographed handheld reading at normal distance; retain original presentation option |
| P2 / model visuals | Original-design character remaster: clearer faces, richer materials, smoother models | The owner rejected the early texture-only passes as too subtle. Prioritize authored face/head reconstruction and selected replacement surfaces, preserving source-part pivots, animation, palette variants and faithful fallback. Pin tools/private source hashes; test UVs, STP, normals, seams, transitions and Deck cost. [Direction and experiment evidence](CHARACTER_REMASTER.md); no engine rewrite |
| P2 / convenience | Opt-in quick text, save convenience, encounter control and addition assistance | Reuse existing settings first; evaluate Dragoon Modifier and Battle Rewards as separate optional profiles with explicit reward ownership; explain persistent effects and record choices per campaign; faithful preset audit including retail defaults; save/load and disabling mods tested |
| P3 / measured rendering | Targeted renderer tuning and optional whole-frame upscaling | Only after Deck baseline and HD-pack timings; bottleneck evidence and equal-scene A/B results; improve worst frametimes without broken UI/occlusion or altered timing; no engine rewrite |
| Release gate | Reproducible, licensed, maintainable package | Source + license + attribution, no private discs/extracted retail assets/saves/tokens/runtimes in Git; pinned community-artwork source and notices, compatible update channel, asset-pack terms, Deck install/upgrade/gameplay evidence |

## Measurement plan before graphics expansion

Record Deck model (LCD/OLED), SteamOS, driver, engine SHA, JDK, pack versions, render settings, output resolution, frame cap and power limit. Use the same private save/route and warm/cold cache conditions. Include title/menus, a detailed field scene, transitions, busy battle/effects and FMV. Measure median, p95 and p99 frametime, stutter counts, CPU/GPU load, system/texture memory, startup/load latency and power/battery estimate. Test suspend/resume and extended play. Capture an original-art baseline, then a pack-only comparison before renderer changes.

Provisional goals: responsive stable 60 fps where the engine supports it (16.7 ms frame budget), with a tested stable 30 fps fallback (33.3 ms). Final targets depend on actual Deck data; do not advertise them from Mac compilation. Menu legibility, input reliability and save safety outrank a nominal FPS number.

## First implementation slice after this milestone

1. Build-only CI is complete. Finish package/version pairing and save-safe upgrade/restore policy before distributing fork binaries; configure a durable local JDK 25.
2. Private discs are extracted and Mac gameplay smoke tests pass. Obtain Deck details and establish an actual Steam Deck baseline. The owner has authorized local game testing.
3. Skurfa v1.1.0 is now a public pinned submodule with inclusive recursive cloning and source-built packaging. Artifact hashes and startup loading are verified; trial representative scenes and measure Deck performance.
4. Adapt one readable menu/control flow using current binding-aware input APIs and selected QoL interaction ideas. Use a narrow artwork shim only if the existing mod needs it. Compare to faithful mode and baseline performance before broadening.
5. Audit model-texture paths and prototype the [legacy texture upscaler](TEXTURE_UPSCALING.md) on a licensed fixture first, then representative private textures in an authorized validation session. Keep geometry replacement and whole-frame upscaling as separately evaluated options.

Research and evidence: [Skurfa HD backgrounds](SKURFA_RESEARCH.md), [Quality of Life+ fork](QOL_FORK_RESEARCH.md), [Dragoon Modifier](DRAGOON_MODIFIER_RESEARCH.md), [Battle Rewards](BATTLE_REWARDS_RESEARCH.md). Skurfa and selective QoL interface adaptation are the primary track; Dragoon Modifier and Battle Rewards stay optional candidates. Skurfa loads on Mac; no physical Deck validation has occurred. [Current implementation and evidence](INTEGRATION_ALPHA.md).
