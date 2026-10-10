# FxHD production: field milestone

The approved takeover plan is being delivered in coherent batches. This milestone covers orthographic dust, left/right footprint trails, smoke plume, smoke cloud with its actual dust palette, and the static save-point central glow. The later combat/animated-spell/transformation milestones remain open. A first field batch is not whole-game FxHD completion.

Requirements:

1. Produce resumable source-bound 2x/4x candidates using the approved local batch pipeline, recorded source/output/tool hashes, and at most one focused repair per failed baseline.
2. Keep original extracted data, diagnostic comparison boards, inference inputs/logs, binaries and weights private. Publish custom candidates and selected resources with attribution and candid quality status.
3. Preserve source image and actual palette binding, binary PSX STP, discarded/visible-black classes, geometry, UV extent, particle count, timing and blend mode. Preserve live original VRAM for other consumers. Mutated/mismatched images or palettes must fall back to the original.
4. Do not overwrite an earlier mod's replacement. Missing, corrupt, unsupported or mismatched resources must retain the original. Allocate on the existing render thread, reuse per effect family, and release safely on repeat teardown and scene transitions. No per-particle decode/upload or inference.
5. Use the updated renderer's bounded image cache and worker prewarming. Preserve existing SMAA, emission/bloom, save-point world lighting, UI protection and reduced-flashing behavior. Keep smoke/dust/footprints unlit; material-map semantics do not apply to those sprites. Sampling must retain source visibility; use no mips/linear filtering for this batch.
6. Bundle the independent FxHD JAR through the standard build/install path and retain saved mod settings. A manual older FxHD copy must not introduce a second active mod ID; preserve its actual file. Existing in-game Mods selection remains the control.
7. Run source/hash/palette/visibility/native-address and CPU-VRAM checks, cleanup/fallback/priority tests, synthetic import regressions and package validation. Native rendered gameplay and physical Steam Deck acceptance must remain explicitly pending until observed.
8. Select 2x where 4x adds no useful handheld benefit. The provisional active texture budget is 16 MiB; this fixed field set uses 81,920 bytes before driver overhead. CPU cache/staging is accounted separately.
9. Merge reviewed implementation into main. Use the orchestrator's single current combined-release flow; do not create separate public releases for code iterations. The current public download remains until a consolidated replacement passes publication verification.

Do not replace the animated/overlapping save-point sheet, own-palette smoke_2 variant, TMD dust, skid texture, procedural effects, combat particles or spell/transform families as part of this static field milestone. Those require their own consumer, animation and native validation evidence.
