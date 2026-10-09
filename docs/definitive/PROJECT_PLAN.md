# Legend of Dragoon: Definitive — full project plan

October 2026. Adapted from the owner's pasted plan on 2026-10-09. The pasted backlog table and Sprint 0 checklist were absent; [BACKLOG.md](BACKLOG.md) and [SPRINT_0.md](SPRINT_0.md) reconstruct them as proposals. This plan records direction and acceptance targets; it does not authorize launching games, purchasing resources, contacting creators/testers or publishing binaries. The active scope remains repository setup, planning and headless validation until the owner selects the next implementation milestone.

## Executive decision

Build a modular modernization suite on Severed Chains. Aim for an experience with the polish of an official remaster while preserving the game's identity; this is an unofficial community project. Keep Java and the existing engine, native platform integrations and mod API. Prioritize player impact, reuse upstream capabilities and community artwork, design for Steam Deck, isolate enhancements, and make gameplay changes optional.

The initial repository milestone is complete as recorded in [VALIDATION.md](VALIDATION.md). It is **not** the plan's M1 Deck Alpha. Plan M0 is only partially complete: source builds pass, but real Deck baseline tests remain pending.

## Success criteria

| Dimension | Release target | Required proof |
| --- | --- | --- |
| Playability | All four discs without project-introduced progression blockers | Checkpoint matrix and full playthrough reports at the release SHA |
| Installation | Guided setup without an end-user terminal | Fresh-device installation and offline second launch |
| Visual quality | Consistent HD backgrounds and enhanced legacy model textures without obvious seams or mismatched layers | Approved scene/model comparison, UV/alpha/palette checks, original-art fallback and measured Deck budgets |
| Performance | Stable engine-supported rates and Addition timing | Per-state frame-time and input results, not just an FPS counter |
| Controls | Complete controller-only play | Title, setup, menus, battle, saves and recovery walkthrough |
| Reliability | Safe save/load, updates and suspend/resume | Backup/restore checks, cold launch and cycle records |
| Modernization | Readable menus, improved inventory, optional guidance | Handheld legibility, navigation and save compatibility checks |
| Compatibility | Faithful mode and supported mod functionality | Explicit preset audit and versioned mod matrix |

## Reuse before rebuilding

The [upstream community page](https://legendofdragoon.org/projects/severed-chains/) mixes present and planned capabilities. Verify each in the chosen engine commit and on hardware. Native resolution, widescreen and existing control/configuration support are extension foundations, not reasons to replace the renderer. Source confirms inventory-size configuration, Addition assistance, encounter options, save convenience and fast-speed configuration. Improve navigation, training/feedback and reliability around them.

Battle rate is a special case: the community page describes retail-rate battle and lists 60 FPS battle as future work. Do not promise 60 FPS across the whole game or independently rewrite battle timing. Coordinate narrow upstream changes only if needed. [CAPABILITIES.md](CAPABILITIES.md) separates source evidence from runtime proof.

## Workstreams

These are areas of responsibility for one primary developer, not staffed teams or delegated agents. They may overlap when dependencies allow.

| Stream | Deliverables | Exit evidence |
| --- | --- | --- |
| A — Platform/reliability | Pinned Linux packages, guided private game import, safe updates, controller profile, backups, crash diagnostics, offline launch and suspend/resume | Ten consecutive cold launches; twenty suspend/resume cycles; save/load across fields, towns and battles without new failures |
| B — Visual remaster | Asset inventory, existing pack adapter, masks/background validation, optional model-texture upscaling pipeline, UI art and screenshot comparisons | Approved scenes free of visible seams, occlusion errors, aspect mismatches and excessive sharpening; measured memory/frame-time cost |
| C — Gameplay/interface | Readable hierarchy, inventory/equipment comparisons, Addition practice, optional journal/hints, accessibility | Controller-only flows, opt-in behavior, faithful mode and valid progression/save state |
| D — Performance | CPU/GPU timings, memory tracking, shader/battery measurements, native-resolution profiles; possible CAS | Measured improvement to consistency, quality or power with no timing/responsiveness regression |
| E — Release/QA | Build CI, tracked tasks, compatibility matrix, versioned packages, rollback and regression suite | Clean install/upgrade/restore, matching source and notices, four-disc QA against selected baseline |

The [legacy model/texture upscaling goal](TEXTURE_UPSCALING.md) adds a reproducible optional asset-enhancement pipeline, starting with original geometry and animation. Whole-frame upscaling remains a separate renderer experiment; higher-detail mesh replacement needs its own scope and acceptance evidence. No upscaler implementation or selected AI/tool model exists yet.

HD pack access and redistribution permission are prerequisites to bundling. Use clearly licensed synthetic fixtures for pipeline tests if needed; do not commit extracted game artwork. No creator outreach has occurred. Filtering, anti-aliasing, sharpening and CAS are gated experiments after performance evidence. FSR 2/3 and frame generation are research only.

## Provisional 24-week schedule

Assumption: one primary developer with AI assistance and actual Deck access. Add 8–12 contingency weeks for assets and full-game QA. Weeks are relative to an agreed start; this document establishes no calendar deadline. Completion depends on gates, not elapsed time.

| Weeks | Focus | Gate |
| --- | --- | --- |
| 1–2 | Capability/source audit, package/update decisions, test plans and Deck baseline | M0 — build + architecture audit + actual Deck evidence |
| 3–6 | Guided setup, controls, backup/restore, offline launch and suspend/resume | M1 — Deck Alpha |
| 5–12 | Pack provenance/coverage, representative HD adapter, small model-texture upscaling trial and scene regression | M2 — Visual Alpha |
| 9–18 | Readability/inventory first, then training/journal proofs of concept | M3 — Modern UX Beta |
| 17–22 | Compatibility, four-disc checkpoints, soak tests and freeze | M4 — Release Candidate |
| 23–24 | Install/upgrade/restore verification, source/notices and sign-off | M5 — 1.0 |

If artwork is unavailable by M1, M2 becomes a limited integration proof of concept; platform and UX continue. If later features threaten quality or full-game QA, explicitly approve a narrower 1.0 scope and move them to a post-1.0 backlog. Do not silently label an incomplete feature set complete.

## Architecture and repository layout

The current full-history fork remains the pinned engine base. Do not reorganize upstream source for the illustrative layout in the pasted plan. Proposed future boundaries:

```text
src/, gfx/, patches/, lang/       # existing upstream structure, retained
mods/                            # ignored runtime JAR installation, not mod source
docs/definitive/                  # plans, decisions, results and test protocols
enhancements/                    # future separately built, namespaced mod source
delivery/steamdeck/               # future installer/launcher/package tooling
artwork-pipeline/                 # future manifests/validation, no game art
private-assets/, external-artwork/ # ignored local inputs
```

No empty implementation scaffolding was added. Prefer existing settings/events and separate mod JARs. Resolve missing seams with small upstream contributions. [UPSTREAM.md](UPSTREAM.md) defines merge-based synchronization, the immutable baseline branch and release/updater blockers. Extraction to a separate pinned engine dependency is a later decision requiring build/API compatibility evidence.

## Risks and mitigations

| Risk | Priority | Mitigation and trigger |
| --- | --- | --- |
| No usable/redistributable HD pack | High | Confirm artifact, terms, mapping and creator attribution before integration; limited fixtures if unavailable |
| Upstream API churn | High | Pin engine/mod versions and run compatibility checks at each sync |
| Upscaled texture artifacts or model mismatch | High | Check UV seams, alpha, palette variants and animation; retain original textures and reject invented detail or excessive sharpening |
| Mask/tile defects | High | Layer-aware scene comparisons and handheld visual review |
| Addition timing changes | High | Repeat input scenarios and correlate with frame-time traces |
| Expansion into a remake | High | Small vertical slices and an explicit release freeze |
| License/asset rights | High | Preserve AGPL and notices; record separate pack rights before distribution |
| Weak full-game QA | High | Four-disc checkpoint matrix and playthrough sign-off; tester recruitment only if authorized |
| Update/save incompatibility | High | Back up binary, source version, packs, config and saves; test isolated restore |
| Deck access absent | Medium | Identify hardware before claiming M0; Mac packaging is not a substitute |

## Resources

Owner-provided planning allowances: tools/hosting $0–$300, optional art compute $100–$500, storage/backup $50–$200, testing/miscellaneous $100–$400; stated total $250–$1,400. The upper line-item sum is **$1,400**, and the lower sum **$250** assumes $0 tools/hosting. Existing Deck hardware is assumed; labor, licensing, commissioned artwork and new hardware are excluded. These are estimates, not vendor quotes, purchasing authorization or a requirement to spend. Reusing authorized artwork is preferred to new compute-heavy regeneration.

Start with [Sprint 0](SPRINT_0.md), the [backlog](BACKLOG.md), and the [Deck test protocol](DECK_TEST_PLAN.md). Current verification remains in [VALIDATION.md](VALIDATION.md).
