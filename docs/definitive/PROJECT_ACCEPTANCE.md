# Definitive project acceptance and coverage matrix

Checkpoint: **2026-10-11**. This is the shared plan for proving a finished project, not a claim that it is finished. The 17 readiness ratings from the coordinator's progress snapshot are subjective assessments, not completion percentages. No domain is accepted as 10/10. The machine-readable companion is [PROJECT_ACCEPTANCE.json](PROJECT_ACCEPTANCE.json).

## Source, release and evidence boundaries

| Checkpoint | Exact identity | Scope |
| --- | --- | --- |
| Published download | `definitive-alpha-2026-10-11-envhd`, source `0af4f997e27f49783180cb2ff1c5a04012467289` | 4,892 selected field images, six painted field replacements, 270 field holds; twelve battle motifs and one terrain material remain held |
| Integrated main | `696cea631db511041ed7ab6466f03da355eebb7c` | 4,917 selected fields, 31 painted field replacements, 245 field holds; twelve painted battle repairs integrated, one terrain hold remains |
| Latest integrated candidate gates | `32f5db9ffd3859bb1436020a4897556c8622721e`, merged by PR 30 | [Linux, Mac and synthetic run 38137903656](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38137903656) passed; this is candidate-tree evidence, not a new published build |
| Public native package gate | source `0af4f997e`, [run 38111833335](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38111833335) | Package/source/resource checks; reported public maintenance checks are headless, not Deck gameplay |
| Character authoring | draft PR 25 at coordinator-confirmed `264f8645784de50a2d6aa399f2d8118272043727` | Existing accepted Dart head remains installed; new nine-character studies are not accepted runtime replacements |

The [current public release](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest) is the download authority. Main's 25 additional fields and twelve painted battle repairs must not be described as already shipped. Preserve the [Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop) and README character showcase. This docs branch does not publish a release or change main.

The source reports used here are `Project-Progress-Ratings-2026-10-11.json`, `EnvHD-Combined-Release-Results-2026-10-11.txt` and `Prop-Overlay-Reconciliation-Checkpoint-14d7.txt`. They remain local evidence records; this document copies only curated counts and conclusions, not private controls or original game data. The ratings JSON digest is recorded in the companion. Domain documentation can contain historical checkpoints; use the exact release/source identity above, not an old document's opening count.

## Active blockers and positive device observation

| ID | Current report | Accountable owner | Required closure |
| --- | --- | --- | --- |
| B01 | Intermittent rendering corruption in ordinary gameplay; stationary image keeps flickering | MEDIA, with RENDER support | Reproduce on the affected exact published build; isolate modules/state; fix and run original and all-mod regressions on Deck |
| B02 | Ground or background props disappear when Dart attacks, a flying enemy attacks, or the camera turns toward Dart | MEDIA, with RENDER support | Matched transition captures plus texture/depth/binding/lifetime evidence; no disappearing scenery through repeated attack/camera sequences |
| B03 | Battle introduction flickers and is sluggish during setup | MEDIA, with RENDER support | Entry frame-time/load trace and same-save baseline comparison; measured improvement with B01/B02 still closed |

The owner directly reported in the MEDIA chat: **“the FMV are working correctly”**, then separately reported the rendering problems above. This is positive physical playback feedback for the observed scenes, not a signed all-18-film, device-routing or suspend matrix. An older MEDIA final response still says physical playback is unverified; retain both timestamps/scopes rather than discarding the newer owner observation. Root's coordination report supplied the Deck context; the device/build details still need attachment to the case record. No cause is confirmed here. The photo attached to that report is private and is not exported into this document.

**B01 appearance detail, direct human report in ROOT:** “the flicker looked more like the original renderer was at like 50% anchored to the bottom left then it was scaling to the full screen.” Preserve the existing ordinary-gameplay/no-recent-movie and stationary-changing context. MEDIA and RENDER are testing viewport, framebuffer, internal/output scaling and pass state alongside the separate clipping/depth defects. This describes the observed image; it proves neither that two renderers are active nor that viewport state is the cause. No fix is accepted or integrated from this observation.

### Reproduced engineering defects under investigation

Owner evidence was read directly from the existing RENDER and MEDIA chats. These are two separate defects, not two interchangeable explanations for all three symptoms. Exact immutable fix sources and retained test receipts are still pending; neither is merged or physically accepted at this evidence checkpoint.

| ID | Owner | Reproduced evidence | Remaining proof |
| --- | --- | --- | --- |
| ENG-D01 | RENDER, MEDIA support | Windowless native M5 Max/OpenGL 4.1 depth-clear probe: disabled depth writes retain seeded 0.25 instead of clearing to 1.0; restoring writes before clear removes stale depth | Isolated GlApi/GlesApi correction and failing-before/passing-after regression, exact source receipt and production-transition checks. No confirmed connection to Deck flicker; coordinator notes no current production addBatch caller and ordinary rendering ends opaque |
| ENG-D02 | MEDIA, RENDER support | Published field and battle rendering rejects a whole textured face when a corner crosses an old PlayStation clipping threshold although it remains visible under the modern camera | Exact shader/camera correction, retained face-visibility symptom regression, repeated attacks/camera flips and physical Deck before/after. Strong match for disappearing ground/props, not physical closure |

B01 stationary corruption and B03 sluggish/flickering battle setup remain open independently. Existing GPU texture-retirement/UI-atlas/shader-state fixtures did not reproduce the reported corruption and do not clear it. Update A12 and related A03 cases only when source, native regression and symptom evidence arrive; do not describe the public download as fixed.

## Owners and handoffs

| Key | Accountable existing chat | Responsibility |
| --- | --- | --- |
| ROOT | Orchestrate project workstreams | Integration, installer/launcher/updater, combined device/campaign gates and sole merge/release authority |
| MEDIA | Improve Steam Deck FMV quality | Pacing, audio, FMVHD and primary current rendering-defect diagnosis; supports FxHD timing/transition diagnosis |
| ENV | Plan texture upscaling | Environment/Skurfa assets, source/state families and static holds |
| CHAR | Check Mac Studio connection | ModelsHD, CharHD and individual character fidelity/rigging acceptance |
| MODS | Assess Definitive mod integrations | Consumer/material ownership, cross-mod compatibility, QoL, controls/accessibility |
| RENDER | Improve rendering and lighting | Renderer/UIHD/FxHD, lighting, GPU evidence and support for current diagnosis |
| DOCS | Update Definitive project README | Acceptance/coverage inventory, README, provenance and license evidence |

These are accountable coordinators, not proof of exclusive code ownership. ROOT arranges owner evidence and physical sessions. Authoring owners deliver case IDs, exact source/package identity, evidence type and remaining scope to ROOT; DOCS reconciles them here. No outgoing chat messages were sent while preparing this checkpoint. Owner thread identifiers are retained in the companion for retrieval.

## Evidence and closure rules

Use **not-run**, **pass**, **fail**, **blocked**, or **accepted-native** per case. Existing component/build evidence is recorded in each domain's checkpoint; new case rows start not-run until actual case-level results are attached. A deliberate native fallback can be accepted-native only with an owner rationale and appropriate original-rendering checks. “Generated,” “selected,” “installed,” “rendered,” “artist approved,” and “device accepted” are separate fields.

Every result must record: case ID; source SHA; tag/package and file inventory digests; platform/device model, SteamOS/runtime and Steam Input mode; installed module versions/toggles; private disc/save/scene identifiers; reproducible steps; expected versus observed behavior; timing/memory measurements where relevant; evidence file hashes; reviewer and date; exclusions and follow-up issue. Keep private save/disc identities and original captures out of public artifacts; publish sanitized receipts and custom deliverables with notices.

A severe open defect blocks its affected domains and the combined release gate even when fixtures pass. A rating increase requires owner/reviewer evidence against this matrix; 10/10 requires complete denominators or explicitly approved exclusions, all required content/state families, actual runtime/artistic/device/campaign checks and no unresolved severe blockers. A docs commit or clean CI run cannot earn it. The controlling scope is the repository source/contracts and the coordinator’s 17-domain snapshot; shared stress workloads below are proposed qualification procedures, not additional promised features. Owners may refine workload length with a documented rationale. Passing an invented count of cases alone does not establish completion. Component checks can run before final campaign acceptance; the prerequisites sequence closure, not permission to begin source work.

## Shared physical and integration cases

- **P01 — Controls/accessibility:** internal and SD installations; Desktop and Gaming Mode; Deck controls, touch and keyboard; Steam Input on/off; remap/reconnect/held input; focus/glyphs/readability/reduced flashing and supported assistive routes. Record any route that requires external assistance.
- **P02 — Performance:** log actual resolution/settings, target profile and refresh/cap; measure p50/p95/p99 frame times, dropped frames, CPU/GPU time, RAM/VRAM, load and cold-launch latency, temperature/power/battery. Use quiet and complex fields, world map, battle entry, three party actors plus effects and films. ROOT must approve explicit budgets for a measured 60fps or 30fps profile before pass; none are asserted by this document.
- **P03 — Lifecycle:** ten cold launches and twenty suspend/resume cycles spread across menu, field, battle, Addition, loading and movie states. Two 120-minute all-mod sessions with save/reload, audio route changes and Steam Stop/relaunch. Record failures rather than resetting the count silently; retain case-level coverage of all storage/input routes.
- **P04 — Combined mods:** all seven default-on; all optional HD off; each module individually toggled against all-on; CharHD × ModelsHD on/off; EnvHD × Skurfa on/off; FxHD × CharHD sampler/material coexistence; original/enhanced FMV fallback; UIHD palette/save variants; unknown custom-owner priority. Exercise changes only at supported reload/boot boundaries and preserve explicit opt-outs. Expand this risk-based set when shared-page/state evidence reveals a dependency; it is not proof of all 128 binary combinations.
- **P05 — Four-disc campaign:** for each of four US discs, maintain chapter/cut/state checkpoints, bosses, party swaps/forms, scripted scenes, Additions, world/field transitions and films. Save/load each checkpoint, check story/inventory/reward state, then Update/Restore a real campaign. Include the documented `ENDING1H` and `ENDING2H` film sequences from the [FMV production manifest](../../integrations/fmvhd/production-manifest.json) and all reachable required source families; source presence alone does not prove reachability.
- **P06 — Delivery/maintenance:** initial install, offline prepared launch, network loss, interrupted download/activation, low space, corrupt payload and receipts, selective Repair, full Reinstall, old/retired release migration, Update/Restore and Uninstall on both storage types. Sentinel saves/preferences/custom mods/discs must survive the applicable actions; no unrelated process can be terminated. Hardware tests require a coordinated owner session rather than an unrequested desktop launch.

## Delivery owner case crosswalk

The coordinator supplied eighteen concrete delivery cases. The sanitized, source-pointer-checked copy is [DELIVERY_ACCEPTANCE.json](DELIVERY_ACCEPTANCE.json); its original receipt hash is retained. All cases remain pending physical acceptance or the exact next-release gate. Prior headless proofs do not clear their physical criteria. A named source pointer is context, not proof that the specific case passes; missing pointers are explicitly flagged for owner reconciliation.

| Domain | Delivery owner cases |
| --- | --- |
| A01 Installer/maintenance | DEL-01 internal install; DEL-02 SD install; DEL-03 Repair; DEL-04 Reinstall; DEL-05 Uninstall; DEL-06 active game; DEL-07 stale receipts; DEL-10 interrupted preparation; DEL-11 activation; DEL-13 old migration |
| A02 Launcher/updater | DEL-08 Update; DEL-09 offline launch; DEL-12 Steam Stop/relaunch; DEL-13 migration; DEL-14 latest Desktop |
| A14 Controls/accessibility | Controller paths through DEL-01, DEL-02, DEL-12 and DEL-17 |
| A15 Device reliability | DEL-18 cold launches/save/suspend/long sessions |
| A16 Campaign/compatibility | DEL-17 default/opt-out campaigns; DEL-18 persistence |
| A17 Release/provenance | DEL-14 Desktop checksum matching; DEL-15 actual public package identity; DEL-16 verified replacement before pruning |

## Coverage inventory and unresolved denominators

- **INV01 — Consumer ledger:** one row per source/consumer/state family with exact model/TIM/script upload identity, original formats, palette/UV/page/crop/STP/depth, aliases, animation/frame/palette dependencies, reachable chapter/disc/cut/state, geometry owner and distinct artwork owner. Record protected upstream/custom ownership and intentional native fallback. Hash-sharing is not transferable semantic ownership.
- **INV02 — Completion ledger:** bind each ledger row to a generated candidate, review, selected runtime inventory, package identity, visual/animation/device acceptance and remaining issue. Deduplicate only proved identical units; do not add images, bindings, pages and configurations together. Whole-game image, prop/overlay and animation-family denominators remain unknown.
- EnvHD main: 4,917 fields selected / 5,162 generated individually reviewed / 245 held; 605 configurations have at least one selected layer, not complete scene coverage. Eight static continent atlases select 292 masters for 364 bindings. Battle selections are 549 bindings of 900; 350 animation/context bindings plus one palette gap remain native. One wrapped terrain material and world animation remain open.
- Props/overlays: the older pool of 481 field-object and 400 overlay page hashes is not 881 EnvHD jobs. Source reconciliation explains 608 script hints (567 aliases, 41 outside object lists) and 25 texture hints (24 outside lists, one sentinel); 511 overlay routes resolve to 467 source pairs. Twelve consumers have source-proved character ownership. Most material ownership and actual state reachability remain unresolved; the historical +201 files/+24 hashes lacks a row inventory and inclusion predicate. Do not silently relabel those as new artwork.
- Character/model: broad smoothing covers supported containers, not completed bespoke heads/bodies. Nine new character studies, field/battle adaptation, Dragoon/Divine forms and the wider NPC/enemy/boss census remain separate. Missing routes/format/source/animation counts in the domain snapshot can overlap and must not be summed into a completion percentage.
- Effects/UI: audited texture/resource coverage does not close procedural consumers, native GPU output, palette/copy state, save variants or whole-campaign scene acceptance. Maintain source and runtime denominators separately.

The coordinator reports a `c98f` animation-discovery checkpoint: Spec clear; fourteen behavioral checks passed with no skips; Standards found **two reproduced P2 findings**, both active and owned by ENV:

- **ANIM-P2-01:** ENOSPC cleanup can leave a partial final ledger.
- **ANIM-P2-02:** a late worklist reread creates a receipt race against the pinned worklist.

Neither review nor those failures was independently reproduced here. The full checkpoint identity and corrected owner receipt remain prerequisites for closure. They change neither runtime selection nor animation acceptance; discovery remains incomplete.

## Domain matrix

| ID | Domain | Owner | Readiness snapshot | Gate |
| --- | --- | --- | --- | --- |
| A01 | Installer, Repair and Reinstall | ROOT | 8/10 | pending |
| A02 | Launcher and Updater | ROOT | 8/10 | pending |
| A03 | Engine pacing | MEDIA | 8/10 | blocked |
| A04 | Audio | MEDIA | 8/10 | pending |
| A05 | FMVHD | MEDIA | 8/10 | pending |
| A06 | EnvHD | ENV | 6/10 | blocked |
| A07 | Skurfa integration | ENV | 8/10 | pending |
| A08 | ModelsHD | CHAR | 6/10 | blocked |
| A09 | CharHD | CHAR | 4/10 | pending |
| A10 | FxHD | RENDER | 7/10 | blocked |
| A11 | UIHD | RENDER | 8/10 | pending |
| A12 | Rendering and lighting | RENDER | 7/10 | blocked |
| A13 | QoL and gameplay settings | MODS | 7/10 | pending |
| A14 | Controls and accessibility | MODS | 5/10 | pending |
| A15 | Steam Deck performance and reliability | ROOT | 3/10 | blocked |
| A16 | Whole-campaign QA and compatibility | ROOT | 3/10 | blocked |
| A17 | Release, documentation and provenance | DOCS | 8/10 | pending |

### A01 — Installer, Repair and Reinstall

**Owner:** ROOT. **Prerequisites:** Verified source/package inventory and coordinated physical test access.

**Checkpoint evidence:** All seven mods plus four EnvHD packs; checksum repair and full reinstall verified. This is inherited evidence, not a newly executed domain acceptance pass.

- **A01.01 · not-run:** Fresh-install all seven modules and four EnvHD packs on internal and SD storage, including a folder with spaces; verify exact inventory, notices, disc identity and executable shortcut.
- **A01.02 · not-run:** Exercise healthy and one-file-corrupt Repair, missing/corrupt launch receipts, partial bootstrap state and retired-release HTTP 404 migration; preserve personal-data hashes and prove unrelated processes survive.
- **A01.03 · not-run:** Full Reinstall must fetch every payload and prepare a new workspace; Uninstall must retain saves/settings/discs unless disc deletion is explicitly selected.
- **A01.04 · not-run:** Interrupt download and activation, retry after network failure, reject corrupt payloads and insufficient space without activating partial data; offline maintenance gives a recoverable explanation.

Evidence context: [INSTALLER](INSTALLER.md) · [FILE_DELIVERY](FILE_DELIVERY.md) · [MAINTENANCE_GAME_RECOVERY](MAINTENANCE_GAME_RECOVERY.md).

### A02 — Launcher and Updater

**Owner:** ROOT. **Prerequisites:** A01, P02, P03, P04.

**Checkpoint evidence:** Matching release identity, differential updates, recovery and current-only publication verified. This is inherited evidence, not a newly executed domain acceptance pass.

- **A02.01 · not-run:** Launch from Desktop and Gaming Mode with controller only; repeat Steam Stop/relaunch and ensure only the intended installation game processes are managed.
- **A02.02 · not-run:** Update a real campaign from an older installation, verify changed/new/removed files and saved mod choices, then Restore and reload the same campaign.
- **A02.03 · not-run:** After successful preparation, cold-launch offline from internal and SD storage; distinguish first-install network requirements from offline gameplay.
- **A02.04 · not-run:** Exercise cancelled/failed update and stale shortcut recovery; verify stable Desktop resolves the current checksum-matched script and retained local Restore data survives hosted retirement.

Evidence context: [INSTALLER](INSTALLER.md) · [DELIVERY_RELIABILITY](DELIVERY_RELIABILITY.md).

### A03 — Engine pacing

**Owner:** MEDIA. **Prerequisites:** B01, B02, B03.

**Checkpoint evidence:** Separated gameplay/media clocks and extensive rate/speed regression coverage. This is inherited evidence, not a newly executed domain acceptance pass.

- **A03.01 · not-run:** Run battle entry, field/battle/world transitions, Additions and selected gameplay speeds with measured simulation/presentation cadence; no speed-dependent hit-window or timer drift.
- **A03.02 · not-run:** Exercise movie entry/exit, skip, pause, fallback, suspend and stalls; restore input/display state without accumulated simulation bursts.
- **A03.03 · not-run:** Compare ordinary battle entry against the same save on upstream/native artwork and each module-isolation route; record latency and frame-time traces before calling a fix complete.

Evidence context: [ENGINE_PACING](ENGINE_PACING.md) · [CINEMATIC_TIMING](CINEMATIC_TIMING.md).

### A04 — Audio

**Owner:** MEDIA. **Prerequisites:** P01, P03.

**Checkpoint evidence:** Native output selection, queue ownership and device-loss recovery checks pass. This is inherited evidence, not a newly executed domain acceptance pass.

- **A04.01 · not-run:** Exercise OS-default and named speakers, wired headphones and Bluetooth; unavailable devices recover without rewriting the saved preference.
- **A04.02 · not-run:** Disconnect/reconnect and suspend during gameplay and films; unplayed queued samples must not advance media, truncate tails or leave silent gameplay.
- **A04.03 · not-run:** Measure A/V synchronization through long opening/endings and sustained gameplay with captured timing evidence; native loopback results remain separate from physical routing.

Evidence context: [AUDIO_VALIDATION](AUDIO_VALIDATION.md) · [CINEMATIC_TIMING](CINEMATIC_TIMING.md).

### A05 — FMVHD

**Owner:** MEDIA. **Prerequisites:** A04, P03.

**Checkpoint evidence:** All18 enhanced and original movies pass current-public-engine native timing checks. This is inherited evidence, not a newly executed domain acceptance pass.

- **A05.01 · not-run:** Individually watch and record all 18 enhanced and 18 original films, logo and new-game opening on the candidate Deck build; check normal speed, dialogue/music sync, endings and skip.
- **A05.02 · not-run:** Reconcile 602 conversion warnings and seven-frame aggregate difference from the ratings checkpoint to per-film source/output timelines; document intended exclusions or repair defects.
- **A05.03 · not-run:** Check crop, color, aspect, detail/artifacts and audio by matched source frames; corrupt/missing replacement must recover through the supported original route.
- **A05.04 · not-run:** Owner reports current Deck FMVs work; retain that observation, but do not mark every film/device scenario passed from it.

Evidence context: [FMVHD_VALIDATION](FMVHD_VALIDATION.md) · [CINEMATIC_TIMING](CINEMATIC_TIMING.md).

### A06 — EnvHD

**Owner:** ENV. **Prerequisites:** INV01, INV02, A07, B01, B02.

**Checkpoint evidence:** Main4917 fields,31painted; static battle/terrain/panorama integration is verified. This is inherited evidence, not a newly executed domain acceptance pass.

- **A06.01 · not-run:** Reconcile every selected field master to source, output hash, consumer, mask and runtime state; verify all 605 partially covered configurations without claiming complete scene coverage.
- **A06.02 · not-run:** Close or explicitly accept native preservation for 245 field holds and one terrain hold; classify 350 battle animation/context bindings plus one palette gap and all world animation families.
- **A06.03 · not-run:** Validate complete animated palette/frame/scroll families and source-bound foreground movement, visibility, STP, depth and transitions in actual field/battle/world scenes.
- **A06.04 · not-run:** Complete props/overlay ownership census before assigning jobs; compare selected scenes with original/Skurfa art at normal handheld size, then measure combined residency.

Evidence context: [ENVHD_FULL_COVERAGE](ENVHD_FULL_COVERAGE.md) · [ENVHD_TERRAIN_PRODUCTION](ENVHD_TERRAIN_PRODUCTION.md) · [ENVHD_BATTLE_MATERIAL_PRODUCTION](ENVHD_BATTLE_MATERIAL_PRODUCTION.md).

### A07 — Skurfa integration

**Owner:** ENV. **Prerequisites:** INV01, B01, B02.

**Checkpoint evidence:** Pinned normal installation and protected original ownership/aliases preserved. This is inherited evidence, not a newly executed domain acceptance pass.

- **A07.01 · not-run:** Verify all pinned Skurfa resource bytes and notices remain unchanged through install/update/restore.
- **A07.02 · not-run:** Exercise protected cut/period aliases, foreground occlusion, transitions and filtering with EnvHD toggled and listener order varied; no redraw, ownership conflict or duplicate replacement.
- **A07.03 · not-run:** Record representative complete-scene visual approval and combined Deck load/residency costs; 38 packs or protected aliases alone do not imply campaign acceptance.

Evidence context: [ENVHD_FULL_COVERAGE](ENVHD_FULL_COVERAGE.md).

### A08 — ModelsHD

**Owner:** CHAR. **Prerequisites:** INV01, B01, B02.

**Checkpoint evidence:** 1343supported containers:964refined/379original; normal bundled installation. This is inherited evidence, not a newly executed domain acceptance pass.

- **A08.01 · not-run:** Reconcile 1,343 supported containers: 964 refined and 379 native, with justification per retained case; keep unknown routes named rather than guessing identities.
- **A08.02 · not-run:** Exercise all nine field actors and 19 battle forms through idle/attack/damage/victory/death, joints/grips/weapons and scene reloads; inspect seams, deformation and original-index effects.
- **A08.03 · not-run:** Resolve 80 mixed-bit-depth references and 30 unresolved routes/formats from the checkpoint; test original, CharHD, mapped/RGBA and other texture-owner combinations.
- **A08.04 · not-run:** Measure three-party-actor plus effect residency/performance on Deck; smoothing counts do not establish bespoke character fidelity.

Evidence context: [MODELS_HD_WORLD_PASS](MODELS_HD_WORLD_PASS.md) · [MODELS_HD_ROADMAP](MODELS_HD_ROADMAP.md).

### A09 — CharHD

**Owner:** CHAR. **Prerequisites:** INV01, A08, A12, P01.

**Checkpoint evidence:** Accepted Dart head installed; live owner reports9new artwork candidates and is reviewing meshes. This is inherited evidence, not a newly executed domain acceptance pass.

- **A09.01 · not-run:** Approve each of nine normal characters against original illustrations/in-game references: silhouette, asymmetry, costume construction/colors/motifs, hair/face, hands, feet and weapon proportions.
- **A09.02 · not-run:** For every candidate, validate field and battle articulated topology through front/side/rear and moving idle/attack/damage/victory/death poses; a good still pose cannot clear detached surfaces or open partitions.
- **A09.03 · not-run:** Integrate geometry and exact material atomically with source identity, fallback and unknown-owner priority; exercise CharHD/ModelsHD on/off and shared material lifetime.
- **A09.04 · not-run:** Inventory and accept Dragoon/Divine forms and the wider NPC/enemy/boss scope separately; resolve checkpoint 69 unbound routes, 33 source failures, 22 format cases and 103 animation/extra holds without summing unlike units.
- **A09.05 · not-run:** Dart accepted head remains the installed selection; nine new body/artwork studies are draft and require fidelity, moving-geometry, native and Deck approval before promotion.

Evidence context: [PARTY_RECONSTRUCTION](PARTY_RECONSTRUCTION.md) · [CHARHD_COVERAGE](CHARHD_COVERAGE.md).

### A10 — FxHD

**Owner:** RENDER, with MEDIA timing/transition support. **Prerequisites:** INV01, A12, B01, B02.

**Checkpoint evidence:** 6466retail source bindings checked;palette-live integration and bounded residency. This is inherited evidence, not a newly executed domain acceptance pass.

- **A10.01 · not-run:** Reconcile 6,466 source bindings and 2,160 unique detail maps, plus the procedural/copy/mesh consumer families; classify 30 unchanged generated outputs.
- **A10.02 · not-run:** Inspect spells, transformations, items, enemy/boss attacks, title, save points, field/world and cutscene effects across palette animation, scrolling, copy/write, STP and black transparency.
- **A10.03 · not-run:** Exercise stale epochs, mod toggles, original fallback and combined load with CharHD sampler/material ownership; verify bounded memory and no per-particle GPU allocation.
- **A10.04 · not-run:** Approve real-scene appearance and reduced-flashing behavior with battle/camera transitions; full catalog source coverage is not full campaign visual acceptance.

Evidence context: [FXHD_PRODUCTION](FXHD_PRODUCTION.md).

### A11 — UIHD

**Owner:** RENDER. **Prerequisites:** A12, P01, INV01.

**Checkpoint evidence:** 738outputs;9portraits;player menu and99post-battle extra bindings verified. This is inherited evidence, not a newly executed domain acceptance pass.

- **A11.01 · not-run:** Reconcile 738 outputs, nine portraits and 99 post-battle extra bindings from the checkpoint; render actual portrait/palette atlases rather than accepting CPU file counts.
- **A11.02 · not-run:** Walk title/loading, menus, inventory/shop, dialogue, battle/Additions, world map, chapter cards, credits and save-card variants at handheld size; check clipping, colors, animation and legibility.
- **A11.03 · not-run:** Exercise paused cache eviction, current-frame pinning, stale/invalid source fallback and original/HD combinations; record peak residency with all seven modules.
- **A11.04 · not-run:** Verify save-card rendering does not rewrite older saves and controls/accessibility remain usable with UIHD off.

Evidence context: [UIHD_COVERAGE](UIHD_COVERAGE.md) · [UIHD_PRODUCTION](UIHD_PRODUCTION.md).

### A12 — Rendering and lighting

**Owner:** RENDER. **Prerequisites:** B01, B02, B03, P02.

**Checkpoint evidence:** Whole-game lighting/shadows/materials/SMAA/bloom/filtering with native fixture evidence. This is inherited evidence, not a newly executed domain acceptance pass.

- **A12.01 · not-run:** Reproduce ordinary stationary flicker and disappearing ground/props during Dart/enemy attacks and camera flips on the affected published build, then retain deterministic regression scenes.
- **A12.02 · not-run:** Check native/HD textures, sampler formats/units, uploads, blending, depth, culling, shader reloads and deferred retirement before assigning a confirmed cause; test fixes with all modules and isolation.
- **A12.03 · not-run:** Approve scene-coherent lights/materials/shadows/SMAA/bloom/filtering at normal game size with authored maps and native fallback; retain UI/Additions coverage and reduced-flashing behavior.
- **A12.04 · not-run:** Measure each added effect and combined GPU cost on Deck; select and document defaults only after physical frame-time and memory evidence.

Evidence context: [RENDERING_LIGHTING](RENDERING_LIGHTING.md).

### A13 — QoL and gameplay settings

**Owner:** MODS. **Prerequisites:** P01, A03. QoL component work precedes final A16 campaign acceptance.

**Checkpoint evidence:** Inventory sorting/filtering,quantity selection,context hints,reward controls andAddition feedback integrated. This is inherited evidence, not a newly executed domain acceptance pass.

- **A13.01 · not-run:** Walk Definitive and Faithful presets with controller only; save/load old and new campaigns and confirm normal Addition timing/damage and 1x default rewards.
- **A13.02 · not-run:** Exercise sorting/filtering, full bag, equipment comparison, quantity buy/sell bounds, cancel/back, remapped context hints and reward settings without losing inventory or applying cancelled choices.
- **A13.03 · not-run:** Check battle autosave/save-anywhere, transition speed, enemy HP/turn order and optional Early/Late/Wrong Button feedback against actual game behavior.
- **A13.04 · not-run:** Inventory planned guidance/journal requirements separately; component features must not be presented as completion of unimplemented scope.

Evidence context: [PRESETS](PRESETS.md) · [QOL_SETTINGS](QOL_SETTINGS.md).

### A14 — Controls and accessibility

**Owner:** MODS. **Prerequisites:** A01, A02, A11, A13.

**Checkpoint evidence:** Keyboard/controller/accessibility paths and held-input/hotplug behavior have component coverage. This is inherited evidence, not a newly executed domain acceptance pass.

- **A14.01 · not-run:** Complete setup, Gaming Mode launch, campaign, menus, battle and maintenance using Deck controls; cover touch/keyboard fallbacks and Steam Input enabled/disabled.
- **A14.02 · not-run:** Remap every required action, reconnect/hotplug and resume held-input boundaries; verify prompt glyphs match current bindings without double input or stuck buttons.
- **A14.03 · not-run:** Check handheld legibility, focus/order, scalable text where supported, reduced-flashing and screen-reader paths; explicitly inventory unsupported accessibility expectations rather than assume them passed.

Evidence context: [INSTALLER](INSTALLER.md) · [QOL_SETTINGS](QOL_SETTINGS.md).

### A15 — Steam Deck performance and reliability

**Owner:** ROOT. **Prerequisites:** B01, B02, B03, P01, P02, P03, P04.

**Checkpoint evidence:** Linux packages and bounded resource design;no complete physical baseline established. This is inherited evidence, not a newly executed domain acceptance pass.

- **A15.01 · not-run:** Complete P02 physical profile runs with measured frame-time percentiles, load/cold-start latency, RAM/VRAM, power/battery and temperatures; a resource budget is not a device measurement.
- **A15.02 · not-run:** Complete ten cold launches, twenty suspend/resume cycles and two 120-minute combined-mod sessions on internal and SD storage; record every hang/crash/texture fault and recovery.
- **A15.03 · not-run:** Choose measured 60fps or 30fps target profiles and explicit scene/load/memory/power budgets before performance sign-off; no invented 10/10 from Linux CI.
- **A15.04 · not-run:** Confirm stable battery/charging and thermals across representative complex fights/field/world/films, rather than one quiet scene.

Evidence context: [RENDERING_LIGHTING](RENDERING_LIGHTING.md) · [ENGINE_PACING](ENGINE_PACING.md).

### A16 — Whole-campaign QA and compatibility

**Owner:** ROOT. **Prerequisites:** INV01, INV02, B01, B02, B03, A01, A02, A03, A09, A15.

**Checkpoint evidence:** Regression fixtures and supported module/default/fallback combinations tested. This is inherited evidence, not a newly executed domain acceptance pass.

- **A16.01 · not-run:** Complete P05 four-disc progression/checkpoint playthrough including bosses, party/form changes, field/world/scripted scenes, the documented ENDING1H/ENDING2H film sequences and transitions; record chapter/cut/state coverage.
- **A16.02 · not-run:** At every checkpoint save/load and compare inventory, story flags, HP/MP, rewards and selected mods; test updates/Restore and real-save migration without silent data loss.
- **A16.03 · not-run:** Run P04 combined-mod and fallback matrix with palette/shared-page/animation states and third-party owner priority; original and Faithful routes remain usable.
- **A16.04 · not-run:** Close all severe defects, inventory unknown consumer denominators and obtain whole-campaign artistic/gameplay acceptance with regression evidence on the intended candidate.

Evidence context: [ARCHITECTURE](ARCHITECTURE.md) · [ENVHD_FULL_COVERAGE](ENVHD_FULL_COVERAGE.md) · [CHARHD_COVERAGE](CHARHD_COVERAGE.md).

### A17 — Release, documentation and provenance

**Owner:** DOCS. **Prerequisites:** INV01, INV02, A01, A02, A16.

**Checkpoint evidence:** One verified current public release;matching source/checksums/notices;published/development scope separated. This is inherited evidence, not a newly executed domain acceptance pass.

- **A17.01 · not-run:** Maintain this 17-domain matrix and source/consumer/owner/state inventory; link each pass to an exact source, package, device and reviewer rather than a rolling branch.
- **A17.02 · not-run:** Before a combined publication, verify exact-source Linux/Mac/synthetic gates, full asset inventory/hashes, anonymous installer entrypoints and actual public maintenance; preserve current-only retention and pinned clean-build input.
- **A17.03 · not-run:** Keep custom code, meshes and HD art public/versioned/in normal installation; retain licenses/upstream notices and exclude originals, discs, saves, credentials, local runtimes and private diagnostic/control data.
- **A17.04 · not-run:** Update front-facing README only for accepted published features, preserving prominent Steam Deck download, Ko-fi and accurately scoped comparison images; root alone merges/publishes coordinated releases.

Evidence context: [ASSET_POLICY](ASSET_POLICY.md) · [FILE_DELIVERY](FILE_DELIVERY.md) · [INSTALLER](INSTALLER.md).

## Update and publication procedure

DOCS updates a case only when its accountable owner supplies a reproducible receipt and scope. Preserve prior failed evidence and checkpoint identities. Maintain the unknown/held list until actual consumer/state/artistic/device acceptance clears each row; a new candidate receipt must not overwrite published counts. Reconcile contradictions against the current source manifest and a dated owner report, leaving disputed cases blocked.

ROOT retains merge and release ownership. Accepted fixes and content are gathered into one coordinated candidate, tested at an exact source, then verified from actual public downloads before a README release refresh or historical-download retirement. Keep active authoring drafts and required pinned build inputs. Do not publish or rewrite the front-facing README for every generation batch. Licenses/notices and original-data exclusions are required at both source and package boundaries.
