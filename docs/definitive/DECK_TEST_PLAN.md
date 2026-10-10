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
