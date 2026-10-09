# Sprint 0 — first 14 days

A reconstructed checklist completing the truncated pasted plan. Relative day windows are suggestions, not deadlines. Evidence must be attached to each completed item. This is preparation for M0; the completed initialization milestone does not satisfy device gates.

## Days 1–2: foundation

- [x] Confirm Mac Studio terminal/filesystem access and gideonidoru authentication.
- [x] Create public repository, preserve upstream history/license/notices and set remotes.
- [x] Pin baseline SHA and validate unmodified macOS + Deck package configurations.
- [x] Record build/test caveats and prevent inherited publishing workflows running.
- [x] Document architecture, reuse opportunities and prioritized roadmap.
- [x] Reconcile the supplied full plan and create proposed missing backlog/checklist.
- [ ] Configure a durable maintained JDK 25 outside Git (temporary validation JDK exists).

Evidence for completed build/setup items: VALIDATION.md. The new planning items are documentary deliverables, not implementation verification.

## Days 3–5: safe delivery contracts

- [x] Implement source/build-only CI; reviewed workflow has no publishers or game invocations. Actions enabled with two pinned official actions; both hosted jobs passed. Evidence: BUILD_CONTROLS.md.
- [x] Make gameplay-test activation an explicit opt-in and verify it with seven headless fixture scenarios; no engine launched.
- [ ] Choose package/version/update ownership and prevent cross-channel replacement.
- [ ] Specify private file validation, save backup and failure recovery before installer coding.
- [ ] Inventory license/credit inclusion and separate HD-pack rights.

## Days 4–7: hardware and artwork readiness

- [ ] Identify test Deck model/SteamOS and an authorized execution session.
- [x] Receive user-provided game files privately; four BIN/CUE pairs are in top-level isos/, expected US disc IDs checked and bytes preserved during cleanup. All remain ignored; extraction/gameplay not run.
- [ ] Obtain a creator-approved HD artifact/version and rights record; do not assume public galleries are packs.
- [ ] Record representative scene IDs/layers, pack gaps and original-art fallback.

No creators or testers have been contacted; that requires explicit messaging authorization.

## Days 6–10: real baseline

- [ ] Run ten cold launches and twenty suspend/resume cycles on Deck.
- [ ] Record offline behavior, controller-only navigation, reconnect and Steam Input combinations.
- [ ] Save/load in representative field/town/battle states and preserve backups.
- [ ] Measure per-state frame times and Addition behavior with original artwork.
- [ ] Record readability at native handheld resolution and current setting defaults.

Use DECK_TEST_PLAN.md. Failures remain failures; missing inputs/hardware are blocked, not passes.

## Days 11–14: first-slice selection

- [ ] Complete faithful/convenience setting audit; do not equate upstream defaults with retail.
- [ ] Select one installation/control/readability slice using baseline evidence.
- [ ] Confirm the representative artwork adapter can use current mod events.
- [ ] Update backlog estimates, compatibility matrix and risks after findings.
- [ ] Declare M0 complete only when build, architecture and actual Deck checks have evidence.

If HD access remains unavailable, continue install/controls/readability; limit visual work to a licensed fixture proof of concept. If hardware is unavailable, advance headless tooling and contracts while leaving M0 device acceptance open.
