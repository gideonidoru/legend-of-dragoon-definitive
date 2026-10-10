# FxHD production status

## Field batch 0.2.0

Seven original named TIM textures were processed into eight source/palette jobs and sixteen baseline candidates. One soft smoke repair makes seventeen public custom candidates. Six 2x resources are selected and integrated, with 81,920 bytes of texture storage and up to the same amount in the engine's shared decode cache. Original controls and comparison boards are private.

| Source / actual binding | Consumer | Disposition |
|---|---|---|
| dust | AttachedSobjEffect orthographic trail, B_PLUS_F | Selected 2x; prior 4x pilot compared |
| left_foot / right_foot | AttachedSobjEffect footprints, B_MINUS_F | Both selected 2x; native geometry retained |
| smoke_1, own CLUT | SMap opcode 770, smoke plume | Selected smooth 2x repair; neural grain rejected |
| smoke_2 with dust CLUT | SMap opcode 782 cloud, 790 unused smoke route | Selected 2x paired-palette variant |
| smoke_2, own CLUT | No matching native cloud binding established | Candidate only; not installed |
| savepoint_big_circle | SMap opcode 772, central save-point glow | Selected 2x; UV extent remains 31/32, existing fade/emission/light intact |
| savepoint | Model/scrolling/overlapping VRAM sheet | Candidates only; original animation retained |

The seven-file denominator is an initial field inventory, not all game effects. Exact earliest in-game locations for smoke and scripted footprint modes are not yet certified. Save-point allocation and dust/footprint/script consumer routes are mapped in code; no live gameplay was launched for this batch.

Each render family checks the current CPU-side indexed pixels and palette once before choosing its HD draw. Palette animation or another mod's VRAM edit immediately restores original indexed drawing; restoring the source reuses the existing HD texture. No original VRAM uploads, particle timing or simulation changes were introduced. A missing FxHD listener leaves the original route.

## Engine enhancements

This batch targets the shared current renderer (including default surface maps, native scene profiles, SMAA, selective emission, protected UI and bounded image preparation). The mod prewarms immutable resources through Texture.prewarmPng; actual reads acquire/release PngAssets leases, respecting the Graphics image-cache setting. Those workers create no renderer resources and carry no scene state. Uploads and ownership remain on the original renderer thread.

Particles and footprints remain unlit. Applying normal/roughness maps to smoke or dust would change their meaning. The save-point's existing world emitter, fade curve, emissive amount and reduced-motion/flashing branch are preserved. No new lights, brightness, particles or post passes are added. Nearest sampling, clamped edges and no mip chain retain exact visibility classes for these tiny sprites; the renderer's default surface maps still apply to eligible models around them.

Memory accounting separates 80 KiB of selected GPU texels, up to 80 KiB of shared decoded storage, less than 8 KiB of source/palette controls and one upload buffer (at most 16 KiB) at a time. JVM/driver allocation overhead and physical Deck frame time/power are unmeasured.

## Validation and delivery

Focused tests cover immutable source events, previous replacement priority, malformed TIMs, wrong native address/palette/BPP, exact STP/discard/black, CPU-side VRAM mutation/restoration, repeated owner cleanup, resource hashes and installer preservation of manual FxHD copies/settings, including renamed official older copies identified without loading their classes. The opt-in fxHdSourceProbe checks all six resources against the user's local extraction without a game window. Four synthetic Python fixtures cover corrupt inputs, visibility preservation and failures while writing provenance or swapping the complete module tree. Publication failure restores the previous tree and a retry succeeds. The stricter visible-black checks also pass on all six actual selected resources.

Native rendered gameplay, all-campaign appearance, busy-scene performance, suspend/resume and physical Steam Deck acceptance remain pending. The six selections are development quality, not final AA acceptance.

FxHD is an independent normal bundled mod, selected through the existing Mods menu. Only selected runtime files enter its JAR; custom rejected/deferred candidates remain in production/candidates. The orchestrator owns the single combined public release. No release is created for this batch in isolation.

Integration with the reviewed consolidated installer source f5651e420 retains EnvHD/CharHD/UIHD in gradle/hd-mods.gradle and builds only FxHD 0.2.0 through gradle/fxhd.gradle. The older event/adapter/dust-only hook is replaced by these source-and-live-palette controls. The existing exact-deletion lifecycle regression now exercises owned EffectArtwork. Current-only release retention, the pinned FMV input, selective file delivery and paired installer entrypoints are preserved.

## Next queue

Map the default battle sprite metrics and DEFF SpriteType consumers to actual indexed regions, source upload packages, palettes, mutation/copy behavior and encounter routes. Candidate production begins only after those bindings are verified. Frame families require shared recipes and temporal review; Dragoon transformations follow reusable particles and one common spell. Procedural lines/lightning/distortion remain renderer consumers. Do not count these as generated or integrated today.

The private takeover plan is /Users/markmurray/Downloads/LoD-Texture-Analysis-2026-10-10/fxhd-production-plan.txt. Public selection/tool/output hashes are in integrations/fxhd/production/field-selection.json.
