# Whole-game rendering and lighting

October 10, 2026. Applies throughout the game, with no disk, map, character, resolution or artwork-pack allowlist. Artwork upscaling and mesh/rig authoring remain with their separate workstreams.

The target is a gorgeous modern retro presentation with coherent painted environments, sculptural characters and clear silhouettes. This is a renderer foundation toward the 2026 AA target; headless checks do not establish final whole-campaign appearance or Steam Deck performance.

## Existing Graphics configuration

Controls use Severed Chains' GLOBAL Graphics category, serialization and help text. Missing values take the defaults below. Existing choices are respected. Neither gameplay presets nor combat timing are changed.

| Control | Default | Whole-game behavior |
| --- | --- | --- |
| Smooth Model Lighting | On | Authored model lights evaluated on normalized interpolated normals in both field/world and battle TMD pipelines. |
| Enhanced Scene Lighting | On | Softened diffuse light transitions, neutral 1.03 direct-light and 0.98 ambient gains, using each model's existing light directions, colors and ambient. |
| Soft Contact Shadows | On | Feathered contact shadows for field actors, battle actors, scripted shadow effects and the world-map traveler. |
| Edge Smoothing | 0.5 | Bounded spatial filtering in the existing final screen pass. Zero restores original sampling. Inactive with CRT effects. |

Beauty determines visual defaults within performance and stability constraints. Continuous surface lighting, restrained shadow softness and moderate edge smoothing are enabled. A single golden-cliff profile no longer selects coverage or gives unrelated scenes a sunset tint.

## Coverage and behavior

| Rendering consumer | Integration |
| --- | --- |
| Field characters, NPCs, props and cutscene models | Shared TMD vertex/fragment lighting; field actor shadows use the shared contact mesh. |
| Battle actors and lit stage geometry | Both model-lighting controls; battle actor shadow placement and attachment modes retained. |
| World-map lit geometry and traveler | Shared TMD lighting; traveler retains its original shadow transform with a softer mesh. |
| Scripted battle shadow effects | Shared contact mesh through the existing battle effect route; scripted transforms, dynamic blend override, depth offset and battle tint retained. |
| Background artwork, overlays, menus and FMVs | Existing final screen edge filter. Baked artwork is not recolored by model-light uniforms. |
| Unlit and translucent effects | Original lighting/blending rules, including CTMD color overflow. Final screen edge filter still applies. |

Every normal-renderer frame writes complete lighting controls to both model shaders. Scene transitions do not select a special profile or leave behind another scene's hue. Shader reload renews all added uniform handles. Custom submaps automatically participate through the same model shader route.

Enhanced Scene Lighting uses a small diffuse wrap: `max(diffuse, (diffuse + 0.08) / 1.08)` before the existing clamp. Unit-facing highlights retain the authored diffuse intensity; light colors and directions remain scene-specific. Zero-colored lights remain zero, so an unlit scene is not illuminated by a fixed global glow. Equal RGB gains preserve authored hue. Both original vertex lighting and normalized per-fragment lighting support the enhancement independently. Existing color ranges, CLUT animation, indexed palettes, UV lookup, STP/discard classification, scissoring and depth formulas are retained. Degenerate interpolated normals use the original vertex result.

Soft actor shadows fit the loaded native shadow's center, horizontal plane and X/Z extents. The source model and texture are retained for the off setting. One shared persistent soft mesh is reused across field and battle actors; no per-actor mesh or per-frame geometry allocation is added. Its TMD adjacency layout feeds the existing geometry shader. Field screen offsets/depth, battle attachment/scale and scripted effect blend selection stay with their original callers. Scripted shadows retain the native 0x80 color multiplier and per-channel CTMD overflow when applying battle tint. Reloading the shared shadow asset clears the cache. Nonfinite, degenerate or non-horizontal custom shadow footprints retain their authored asset instead of failing scene loading.

The world traveler uses the same radial falloff in a standard triangle mesh, built at initialization or when the setting changes. Replacement is built before deleting the previous mesh; existing world-state unload owns cleanup. The unified setting is registered as `soft_contact_shadows`; the earlier unpublished `soft_world_map_shadow` pilot name was removed.

## Cost and Steam Deck considerations

No new draw passes, full-screen render targets, temporal history, asset textures, GPU extensions or shader dependencies are introduced. Each existing contact shadow remains one draw. A soft disc is 160 triangles, 480 standard vertices or 960 TMD adjacency vertices; the shared actor vertex buffer is approximately 60 KiB. The added fragment lighting arithmetic and increased shadow vertices require physical Deck measurements.

The screen filter skips low-contrast regions, bounds the direction search to four source texels and clamps reads at the image boundary. Explicit minifying derivatives select the existing linear minification filter: nine worst-case shader sampling instructions instead of the earlier manual filter's 33. The earlier controlled diagonal comparison differed by at most 1/255. This is an instruction-count reduction, not a measured bandwidth or frame-time gain. Small text and Addition overlays need live readability review because filtering occurs after composition.

These are conventional APU-friendly rendering choices, with no neural work, frame generation, TDP/clock tuning or driver overrides. Valve documents the Deck's 8-CU RDNA 2 GPU and 1280×800 screen in its [official specifications](https://www.steamdeck.com/en/tech). Explicit-gradient sampling follows the [Khronos OpenGL specification](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf) and [GLSL specification](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.60.pdf). The bounded spatial filter is FXAA-style, not an implementation of NVIDIA FXAA 3.11 or AMD FSR; see NVIDIA's [FXAA white paper](https://developer.download.nvidia.com/assets/gamedev/files/sdk/11/FXAA_WhitePaper.pdf).

## Current verification

- Headless Java build and delivery suite: 112 passing cases, four skipped window-only cases, zero failures/errors.
- Windowless Apple M5 Max / OpenGL 4.1 compilation and linking of both actual model pipelines and the actual screen shader.
- Both model pipelines exercised with an original-mesh pose and an indexed-palette combat pose: original disabled output byte-exact, opaque coverage unchanged, unlit/translucent lighting bypass byte-exact.
- Authored warm, cool, dim and completely black light inputs tested in both pipelines; coverage unchanged, toggles restore previous pixels and zero lights remain black.
- Contact-shadow winding, complete radial tiling, finite/radius bounds, zero-darkness fringe, shifted native footprint bounds/plane and nonmutation tested.
- Soft shadows rendered through standard, field TMD and battle TMD shader routes with subtractive blending: nonempty bounded coverage, no draw GL errors, matching controlled shadow images across routes.
- A one-time macOS integer-sampler diagnostic remains in the untextured shadow harness. Separate indexed-palette model fixtures validate the palette route; the shadow diagnostic is not evidence of physical Deck compatibility.

Private whole-game implementation review outputs: `/Users/markmurray/Downloads/LoD-Rendering-Review-2026-10-10/whole-game/`. These are controlled headless model and flat-surface shadow studies, not live field/battle screenshots. The native shadow footprint was also inspected from the locally extracted shared asset; derived asset data remains outside Git.

`scripts/headless-rendering-probe.c` reproduces windowless shader/pixel checks on macOS. Compile with `clang -Wno-deprecated-declarations scripts/headless-rendering-probe.c -framework OpenGL -o /tmp/rendering-probe`. Pass current and pre-change shader directories. Optional arguments are a private DGSC fixture and output prefix; a further shader directory compares the earlier manual screen filter. Shadow mode accepts two private mesh files and two RGBA output paths, and checks all three shader routes. Derived captures stay outside Git. The harness uses OpenGL 4.1 program-uniform setup; runtime shader changes remain within the existing GLSL 330 contract.

## Remaining acceptance

Implementation coverage is game-wide; appearance and stability have not been observed throughout the campaign. Live field foreground masking, shadow placement under unusual actor transforms, animated palettes, busy spells, menus, FMVs, scene transitions, suspend/resume and Addition readability remain to be reviewed. Physical Deck CPU/GPU frame time, p95/p99, memory and power remain unmeasured. No claim of final AA quality, a measured Deck speedup, packaging or installation is made.

Further material response, atmosphere and selective emissive glow should integrate into shared rendering routes with the model/artwork contracts, rather than become isolated scene demonstrations. Final defaults should be judged off/on at handheld size and in motion, with the more beautiful option retained when it meets the measured stability and frame budget.
