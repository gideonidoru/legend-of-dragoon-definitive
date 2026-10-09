# Proposed prioritized backlog

Reconstructed from the owner's plan because its interactive priority table was absent. Impact/effort are provisional 1–5 estimates (5 = greatest). Stages are delivery order, not severity. These are local briefs, not created GitHub issues. Implementation status is recorded below the table; acceptance targets are not blanket completion claims.

| ID / stage | Work | Impact / effort | Dependencies | Acceptance |
| --- | --- | --- | --- | --- |
| D01 / P0 | Build-only CI and explicit test opt-in | 3 / 2 | Retain source-only build path and Actions disabled until workflows safe | Mac and Linux package builds; no automatic game launch/publish; verify test enable/disable behavior |
| D02 / P0 | Update/package identity decision | 5 / 2 | Upstream updater audit | Document engine/mod version pairing and prevent inadvertent replacement by upstream binary before release |
| D03 / P0 | Actual Deck baseline and capability audit | 5 / 2 | Hardware, private game files, execution permission | Complete M0 hardware record, per-state rates, controls/save/readability inventory |
| D04 / P1 | Guided install/private disc validation | 5 / 3 | D01–D03 | Fresh setup without terminal, actionable unsupported/missing-file errors, offline second launch |
| D05 / P1 | Backup, safe upgrade and isolated restore | 5 / 3 | D02–D04 | Preserve saves/settings/packs; restore pre-upgrade snapshot even with newer incompatible saves |
| D06 / P1 | Deck controller/glyph/profile polish | 5 / 2 | D03 | Complete navigation, remapping and reconnect; Steam Input on/off without doubled actions |
| D07 / P1 | Readable menu/font proof of concept | 5 / 2 | D03 | 1280x800 handheld legibility, no clipping; original-art/layout option |
| D08 / P1 | Existing artwork provenance/coverage manifest | 5 / 1 | Author-provided artifact and terms; outreach separately authorized | Version/checksum/permission/credits/mapping recorded; absent pack handled explicitly |
| D09 / P2 | Representative HD adapter and layer checks | 5 / 3 | D03, D08 | Approved scenes; fallback and disable cleanly; measured texture memory/frame-time cost |
| D10 / P2 | Inventory navigation/equipment comparison | 4 / 3 | D06–D07, relevant API audit | Sorting/filtering/comparison without inventory loss or save mutation errors |
| D11 / P2 | Faithful and convenience preset contract | 5 / 2 | D03, complete config audit | Explicit reversible per-campaign settings; no silent migration; retail behavior checks |
| D12 / P3 | Addition training/feedback mod | 4 / 4 | D03, D11, timing/API audit | Practice isolated from progression/rewards; controller-only; timing unchanged outside training |
| D13 / P3 | Optional journey journal/hints | 3 / 4 | D11, progression event coverage | Reliable entries, spoiler control, reload behavior, no progression-flag mutation |
| D14 / P4 | Evidence-led rendering/optional CAS | 3 / 4 | D03, D09, proven bottleneck | Equal-scene A/B quality/power/frame-time improvement; no input/timing regression |
| D15 / P5 | Four-disc, compatibility and release QA | 5 / 5 | Accepted features frozen | Clean install/upgrade/restore, complete progression and artifact/source/notices checks |

Prefer D01–D08 and visible wins before ambitious journal/training/rendering work. D08 can be investigated independently, but D09 needs both usable assets and a device baseline. Schedule gates in PROJECT_PLAN.md do not override these dependencies.

D01 is complete: local builds and seven control scenarios pass, and both hosted platform jobs pass. Evidence is in BUILD_CONTROLS.md. D02 is partially complete: the updater targets the project repository, but package/version pairing and rollback policy remain. Device-dependent work is still blocked by private files/hardware and execution permission.
