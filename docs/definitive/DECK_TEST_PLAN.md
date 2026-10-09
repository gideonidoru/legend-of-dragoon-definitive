# Steam Deck baseline and release test protocol

Status: planned; no device results. Private game files and explicit permission are required before any launch or UI/device manipulation. This document is a recording protocol, not execution authorization.

## Every run record

Record date, tester, Deck model, SteamOS/driver, engine/project SHA, JDK, artifact checksum, mod/pack versions, native output resolution, render scale, refresh/frame cap, power limit, Steam Input mode and cache state. Record scene/save identifiers privately, without uploading saves or original asset screenshots automatically. A comparable A/B run changes one variable.

| Test | Protocol | Acceptance / record |
| --- | --- | --- |
| Clean install | New user directory, supported private game images, guided flow | No end-user terminal; missing/unsupported files explained; no private content in package |
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
