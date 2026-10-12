# Steam Deck baseline and release test protocol

Status: planned; no device results. Private game files and explicit permission are required before any launch or UI/device manipulation. This document is a recording protocol, not execution authorization.

## Every run record

Record date, tester, Deck model, SteamOS/driver, engine/project SHA, JDK, artifact checksum, mod/pack versions, native output resolution, render scale, refresh/frame cap, power limit, Steam Input mode and cache state. Record scene/save identifiers privately, without uploading saves or original asset screenshots automatically. A comparable A/B run changes one variable.

| Test | Protocol | Acceptance / record |
| --- | --- | --- |
| Clean install | New user directory, supported private game images, guided flow | No manual commands required; missing/unsupported files explained; no private content in package |
| Setup progress and completion | Import four images or a supported archive; observe task/count progress; exercise both Finish paths in separate isolated installs | Picker closes before preparation; expected folder, verified files and launchers exist; both Finish paths close setup; errors cannot show success |
| Steam integration | Leave Steam running, add shortcut, observe graceful close and automatic restart, then switch to Gaming Mode; repeat addition | Exactly one working shortcut; Play opens the installed launcher; blocked shutdown and failed restart show errors and remain retryable with diagnostics |
| Forced setup termination | Terminate setup during portable/runtime download, then retry; separately leave a downloader running | Retry succeeds after all owners stop; a still-running owner prevents overlap; no manual removal of current regular lock files |
| Storage and permission failures | Use an isolated unwritable destination and a controlled insufficient-space volume | Clear stopped screen or startup diagnostic; no activation of a partial package; no modification of an existing valid install |
| Download faults | Disconnect during engine/runtime transfer; separately reject a corrupt package | Timeout/error gives a usable log and retry; checksum failure cannot activate; prior installation and private data remain usable |
| Cold launch | Ten complete exit/start cycles, record cold vs warm cache | 10/10 reach playable/menu state; time/diagnostics captured |
| Offline | After completed provisioning, disable network and start | Launch/load work offline; updates fail gracefully |
| Controller | Title/options/menu/inventory/equipment/dialogue/save/battle | All paths navigable; glyphs and confirm/cancel consistent; no touchscreen/keyboard needed |
| Input compatibility | Steam Input on/off, reconnect, overlay, handheld/external controller | No double action, stuck button or loss of focus; settings persist |
| Readability | 1280x800 handheld viewing; short/long labels and dense screens | Legible text; no clipping/overlap; font/art choices recorded |
| Save/load | Field/town, before/after battle, transitions and multiple campaigns | Valid progression/inventory/mod IDs; backup restore works |
| Suspend/resume | Twenty cycles distributed across menu/field/battle/FMV, short and long waits | 20/20 resume without stuck input/audio, timing drift, crash or save damage; report failures explicitly |
| Performance | Fixed routes for menus, detailed field, transitions, busy battle and FMV | Median/p95/p99 frametime, stutter, CPU/GPU, memory and power traces; engine-supported state rates respected |
| Addition timing | Normal speed, same Addition, repeated input sequence before/after change | No project-induced timing/window/responsiveness drift; distinguish human variance from instrumented traces |
| HD art | Original vs pack scenes, foreground layers, camera/transition variants | No seams, broken masks or aspect change; missing-map fallback; memory/frame-time measured |
| Updates | Upgrade with mod/pack/config/save preservation; simulate download failure | Never replace with incompatible channel; interrupted update recovers |
| Rollback | Snapshot engine+mods+config+saves before upgrade; restore isolated copy | Restore original compatible state; do not assume newer saves load in older engine |
| Four-disc progression | Checkpoints around disc changes, boss/script transitions and completion | Full playthrough and critical checkpoints at release SHA; no new blockers |

## Evidence and stop conditions

Use PASS, FAIL, BLOCKED or NOT RUN for each scenario. Counts are acceptance targets, not reliability guarantees. Attach logs and measurements; screenshots require asset/privacy review before public publication. Stop a failing update/save test before overwriting the only private save copy. Compare project failures with unmodified upstream on the same setup where safe.

Release sign-off needs the complete matrix and issue dispositions, not only compilation or a brief launch. Save rollback must restore the pre-upgrade snapshot because upstream documents that newer saves may not load in older engine versions. Verify that behavior on the selected versions.

## Reproducible combined gameplay run

This extends the shared P01–P04 protocols in [PROJECT_ACCEPTANCE.md](PROJECT_ACCEPTANCE.md#shared-physical-and-integration-cases). That file controls case status and release acceptance. The run below supplies evidence for A03.01–03, A04.01–03, A12.01–04, A14.01–03 and A15.01–04; it does not close their other requirements or the four-disc campaign.

The coordinator binds the installed source and package to the current delivery receipt before testing. Record an unresolved binding as BLOCKED; do not ask the player to infer it from a launcher label. Use a private copied save and a separate test installation/configuration. Preserve the only valid save, discs and existing installation. Set up capture only in an authorized device session; this document does not authorize installing tools or launching the game.

1. Record the run fields below and the coordinator-approved performance budgets. Keep the same route, save, party, encounter and input sequence for every comparison. Record any unavoidable randomness rather than treating different encounters as equivalent.
2. Start capture before launch. Mark launch requested, launcher ready, title ready, save selected and first controllable field frame. Capture the first visit with its cache state intact, then a separately marked warm repeat. Never discard the first slow visit from the cold result.
3. In the field, stand still for 60 seconds, walk the fixed route and return to the starting view. Inspect Dart's head, foreground layers and props throughout; capture any flicker even while stationary. Open the character menu with Y, then exercise inventory, equipment and dialogue. Record which artwork appeared and whether text and controller prompts remain readable.
4. Trigger the fixed encounter. Mark encounter trigger, first visible battle frame and first controllable battle turn separately. Capture the entire entry effect and camera movement. Exercise Dart attacking, a far enemy approaching Dart, camera flips toward each side, a repeatable Addition and a busy effect sequence. Keep ground and background props in view before, during and after each action. Record visible disappearance, material swaps, flashing and delayed input separately from measured entry latency.
5. Return to the field and hold the original view for 60 seconds. Repeat the field route and menu. Compare Dart's head and scene materials before entry, while the battle assets were active and after return. Save and reload the private copy, then repeat the same view. A correct single still image is insufficient evidence for intermittent faults.
6. Repeat the route with all seven default HD modules on, all optional HD modules off, and the relevant P04 isolation combinations. Change one supported setting at a time at its supported boot/reload boundary. Record actual effective module state and any unknown third-party owner. Do not label these selected combinations as all 128 possible combinations.
7. For comparable performance captures, collect three complete repeats per selected profile and configuration. Do not blend cold, warm, menus, battle entry and ordinary battle into one distribution. Retain each repeat and each failure, including a route that could not finish.

Use the same private route on unmodified upstream with native artwork where compatible. If a save, setting or encounter cannot be made comparable, record that limit. Existing positive player observations remain scoped observations; a later controlled run adds evidence without erasing them or implying every scene was previously tested.

## Frame, resource and responsiveness evidence

ROOT approves a measured 30fps or 60fps presentation profile and explicit scene, entry/load, memory and power budgets **before performance sign-off**. Leave unset budgets blank and the associated acceptance BLOCKED. Presentation rate and the engine's native simulation rate are separate: record both; a 60fps presentation counter does not prove 60 native gameplay steps per second.

The capture receipt must name the collector and version, timestamp clock, units, sampling interval, covered phases, missing intervals and available counters. Preserve raw data privately. A sampled overlay value is not a trace of every frame. Compute frame-time percentiles only from a verified per-frame series; otherwise label them sampled statistics and leave the per-frame requirement unresolved. Missing CPU/GPU duration or memory counters remain missing, rather than substituting utilization percentages or an allocation budget.

For each marked phase and repeat, report frame count, capture duration, p50/p95/p99, worst frame and counts above the approved stutter thresholds. Use sorted per-frame durations and nearest-rank percentiles (`ceil(p × N)`, one-based), state the method and do not remove outliers. Report entry/load latencies from the marked endpoints, with abandoned or failed loads listed separately. Keep capture overhead and capture-disabled comparison results when available.

Record RAM/VRAM, CPU/GPU duration where measurable, power, temperature, battery and charging state with their sampling intervals. Compare the first, middle and final route in the long sessions below; flag growth or thermal changes rather than relying on one quiet opening scene. Human reports of responsiveness and Addition timing remain separate from instrumented input-to-visible-response measurements. Record the measurement method and uncertainty before making a latency claim.

## Lifecycle and long-session sequence

Perform the following on **each** supported storage route: internal storage and SD. Keep Desktop/Gaming Mode and Steam Input on/off coverage attached to individual cases; completing one route does not stand in for another. The wider P01/P06 control and maintenance matrices still apply.

| Sequence | Reproducible coverage | Evidence |
| --- | --- | --- |
| Ten cold launches | Fully exit between attempts; distinguish warm filesystem/shader caches from a cold process and record both | All ten attempts, endpoint times, exit/relaunch diagnostics and recovery; retain failed attempts in the count |
| Twenty suspend cycles | Menu ×3, field ×4, battle ×4, Addition ×3, loading ×3, movie ×3; include short and longer waits and record actual duration | Per-cycle state, held-button condition, resume timing/input/audio/texture result and recovery; loading/save tests use an expendable copy |
| Two 120-minute sessions | All default modules; repeat the combined route at start, midpoint and end; include save/reload, world/field transitions, busy battles, films and menu visits | Continuous timestamps, phase markers, resource trends, every fault and capture gap; an interrupted session is retained and does not count as a completed 120-minute run |
| Audio routing | During those sessions cover default/named output, speakers, wired and Bluetooth where available; disconnect/reconnect and suspend during gameplay and films | Preference before/after, actual output, silence or missing tails, measured A/V timing where available; unavailable hardware is BLOCKED for that route |
| Controls and exit | Remap/reconnect, overlay and held-input resume; use Steam Stop and relaunch in the session plan | No double/stuck actions, current glyphs, save integrity and restart result; any external assistance is recorded |

Avoid repeatedly overwriting a save merely to reproduce a failed transition. Preserve the failure's logs, private capture and configuration first. Continue unaffected safe cases, but stop a destructive save/update route until the valid snapshot is protected. Headless GPU, audio loopback, package tests and cross-target builds retain their own evidence categories and cannot fill these physical-device results.

## Private run record template

Copy this table into the private test record, once per installation/profile/configuration. Keep original assets, saves and private diagnostics out of public Git and release payloads. Public summaries may include sanitized measurements, case results and source/package identifiers after review.

| Field | Value to record |
| --- | --- |
| Run identity | Date, tester, run ID, storage route and Desktop/Gaming Mode |
| Installed binding | Delivery generation/receipt, engine SHA, package checksum, JDK and effective module versions; coordinator verifies |
| Device | Deck model, SteamOS, graphics driver, display/output, controller and Steam Input mode |
| Profile | Resolution, render scale, refresh/cap, simulation rate, power limit, visual settings, charging state and initial battery/temperature |
| Budgets | ROOT approval reference; presentation, per-phase frame/stutter, entry/load, RAM/VRAM, power and latency budgets; unset fields stay blank |
| Private route | Save copy ID, field/encounter/party/input sequence, upstream comparator and expected native/HD appearance |
| Capture | Collector/version/clock/units/intervals, raw-file references/checksums, available counters and capture overhead |
| Phase markers | Launch/title/load/field/entry/attack/camera/effects/return/reload, with timestamps and any omitted phase |
| Repeats | Cold/warm classification, each repeat's metrics, first/middle/end long-session comparison and every capture gap |
| Lifecycle | Every launch/suspend/session/audio/control attempt, duration, result and recovery; failed attempts are never silently reset |
| Visual faults | Field flicker, head changes, battle ground/prop disappearance, camera/entry flashing; private capture reference and exact trigger |
| Case disposition | Existing A-case ID, PASS/FAIL/BLOCKED/NOT RUN, tested scope, unmet requirements, linked issue and coordinator sign-off |

PASS requires both the applicable visual/lifecycle criteria and approved measured budgets. A missing capture, untested required route or unset budget cannot become PASS because the run looked smooth. This protocol is ready to execute; it records no device results by itself.
