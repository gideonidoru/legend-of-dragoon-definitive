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
| Protect Interface | On | Dialogue, menus, title/credits text, battle HUD, Addition/Dragoon prompts and map indicators retain crisp pixels. |
| HD Texture Filtering | On | Mipmapped color filtering and at most 4x supported anisotropy on HD environment replacements; explicit gutter contract for atlases. |
| Material Lighting | On | Restrained matte highlight on unknown opaque lit surfaces; authored cloth, skin and metal tags change the response. |
| Effect Lights | On | At most four nearby colored lights from luminous particles, sprite/geometry effects and field save points. |
| Scene Bloom | 0.25 | Selective soft glow from emissive draws, independently of CRT settings. |
| Scene Sharpening | 0.2 | Contrast-adaptive detail enhancement bounded by the local color range; no history or overshoot. |

Beauty determines visual defaults within performance and stability constraints. Continuous surface lighting, restrained shadow softness and moderate edge smoothing are enabled. A single golden-cliff profile no longer selects coverage or gives unrelated scenes a sunset tint.

## Coverage and behavior

| Rendering consumer | Integration |
| --- | --- |
| Field characters, NPCs, props and cutscene models | Shared TMD vertex/fragment lighting; field actor shadows use the shared contact mesh. |
| Battle actors and lit stage geometry | Both model-lighting controls; battle actor shadow placement and attachment modes retained. |
| World-map lit geometry and traveler | Shared TMD lighting; traveler retains its original shadow transform with a softer mesh. |
| Scripted battle shadow effects | Shared contact mesh through the existing battle effect route; scripted transforms, dynamic blend override, depth offset and battle tint retained. |
| Background artwork, scene overlays and FMVs | Shared scene presentation controls. HD environment replacements acquire explicit color filtering. Painted environments retain their authored lighting. |
| Dialogue, menus, battle/Addition HUD, map indicators and title/credits text | Protected coverage target; shared font and native UI helpers tag their draws. Original draw order, depth and blend rules remain intact. |
| Unlit and translucent effects | Original lighting/blending rules, including CTMD color overflow. Explicit luminous effects contribute to selective bloom; interface protection keeps prompts crisp. |

Every normal-renderer frame writes complete lighting controls to both model shaders. Scene transitions do not select a special profile or leave behind another scene's hue. Shader reload renews all added uniform handles. Custom submaps automatically participate through the same model shader route.

Enhanced Scene Lighting uses a small diffuse wrap: `max(diffuse, (diffuse + 0.08) / 1.08)` before the existing clamp. Unit-facing highlights retain the authored diffuse intensity; light colors and directions remain scene-specific. Zero-colored lights remain zero, so an unlit scene is not illuminated by a fixed global glow. Equal RGB gains preserve authored hue. Both original vertex lighting and normalized per-fragment lighting support the enhancement independently. Existing color ranges, CLUT animation, indexed palettes, UV lookup, STP/discard classification, scissoring and depth formulas are retained. Degenerate interpolated normals use the original vertex result.

Soft actor shadows fit the loaded native shadow's center, horizontal plane and X/Z extents. The source model and texture are retained for the off setting. One shared persistent soft mesh is reused across field and battle actors; no per-actor mesh or per-frame geometry allocation is added. Its TMD adjacency layout feeds the existing geometry shader. Field screen offsets/depth, battle attachment/scale and scripted effect blend selection stay with their original callers. Scripted shadows retain the native 0x80 color multiplier and per-channel CTMD overflow when applying battle tint. Reloading the shared shadow asset clears the cache. Nonfinite, degenerate or non-horizontal custom shadow footprints retain their authored asset instead of failing scene loading.

The world traveler uses the same radial falloff in a standard triangle mesh, built at initialization or when the setting changes. Replacement is built before deleting the previous mesh; existing world-state unload owns cleanup. The unified setting is registered as `soft_contact_shadows`; the earlier unpublished `soft_world_map_shadow` pilot name was removed.

## Cost and Steam Deck considerations

No extra scene draw passes, temporal history, neural work or asset production are introduced. Scene draws now output emission (RGBA8) and interface coverage (R8) alongside the existing color target. Both auxiliary targets are double buffered with the scene, resized/deleted with it and explicitly cleared to zero. They add 10 bytes per internal render pixel in retained texture storage: about 9.8 MiB at a 1280 by 800 internal render size. Existing internal resolution/aspect and 240-line alignment rules are retained. Each existing contact shadow remains one draw. A soft disc is 160 triangles, 480 standard vertices or 960 TMD adjacency vertices; the shared actor vertex buffer is approximately 60 KiB. The added fragment lighting arithmetic and increased shadow vertices require physical Deck measurements.

The screen filter skips low-contrast regions, bounds the direction search to four source texels and clamps reads at the image boundary. Explicit minifying derivatives select the existing linear minification filter: nine worst-case shader sampling instructions instead of the earlier manual filter's 33. The earlier controlled diagonal comparison differed by at most 1/255. This is an instruction-count reduction, not a measured bandwidth or frame-time gain. The coverage target protects interface pixels and a one-texel fringe before any modern screen filter. CRT styling retains its intentional whole-image behavior. Live readability still needs review at the user-selected resolution.

These are conventional APU-friendly rendering choices, with no neural work, frame generation, TDP/clock tuning or driver overrides. Valve documents the Deck's 8-CU RDNA 2 GPU and 1280×800 screen in its [official specifications](https://www.steamdeck.com/en/tech). Explicit-gradient sampling follows the [Khronos OpenGL specification](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf) and [GLSL specification](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.60.pdf). The bounded spatial filter is FXAA-style, not an implementation of NVIDIA FXAA 3.11 or AMD FSR; see NVIDIA's [FXAA white paper](https://developer.download.nvidia.com/assets/gamedev/files/sdk/11/FXAA_WhitePaper.pdf).

## Current verification

- Combined headless Java checks: 136 delivery cases, 132 passed, zero failures/errors and four window-only cases skipped; 22 ModelsHD cases passed. Pinned Skurfa and FMVHD source compilation pass. FMV queue/cancellation and silent-audio-tail checks use synthetic videos and the OpenAL null output driver.
- Core engine JAR builds with the new renderer and FMV classes; distributed shader copies match the source. ModelsHD 0.3.0 JAR builds with all 19 checksum-verified custom model packs and the material-compatible reader. A new complete installer or hosted release has not been published by this rendering change.
- Windowless Apple M5 Max / OpenGL 4.1 compilation and linking of both actual model pipelines and the actual screen shader.
- Both model pipelines exercised with an original-mesh pose and an indexed-palette combat pose: original disabled output byte-exact, opaque coverage unchanged, unlit/translucent lighting bypass byte-exact.
- Authored warm, cool, dim and completely black light inputs tested in both pipelines; coverage unchanged, toggles restore previous pixels and zero lights remain black.
- Contact-shadow winding, complete radial tiling, finite/radius bounds, zero-darkness fringe, shifted native footprint bounds/plane and nonmutation tested.
- Soft shadows rendered through standard, field TMD and battle TMD shader routes with subtractive blending: nonempty bounded coverage, no draw GL errors, matching controlled shadow images across routes.
- A one-time macOS integer-sampler diagnostic remains in the untextured shadow harness. Separate indexed-palette model fixtures validate the palette route; the shadow diagnostic is not evidence of physical Deck compatibility.

Private whole-game implementation review outputs: `/Users/markmurray/Downloads/LoD-Rendering-Review-2026-10-10/whole-game/`. These are controlled headless model and flat-surface shadow studies, not live field/battle screenshots. The native shadow footprint was also inspected from the locally extracted shared asset; derived asset data remains outside Git.

`scripts/headless-rendering-probe.c` reproduces windowless shader/pixel checks on macOS. Compile with `clang -Wno-deprecated-declarations scripts/headless-rendering-probe.c -framework OpenGL -o /tmp/rendering-probe`. Pass current and pre-change shader directories. Optional arguments are a private DGSC fixture and output prefix; a further shader directory compares the earlier manual screen filter. Shadow mode accepts two private mesh files and two RGBA output paths, and checks all three shader routes. Derived captures stay outside Git. The harness uses OpenGL 4.1 program-uniform setup; runtime shader changes remain within the existing GLSL 330 contract.

## Remaining acceptance

Implementation coverage is game-wide; appearance and stability have not been observed throughout the campaign. Live field foreground masking, shadow placement under unusual actor transforms, animated palettes, busy spells, menus, FMVs, scene transitions, suspend/resume and Addition readability remain to be reviewed. Physical Deck CPU/GPU frame time, p95/p99, memory and power remain unmeasured. No claim of final AA quality or a measured Deck speedup is made.

Material response and selective emissive glow now integrate into shared rendering routes with the model/artwork contracts. Authored material tags and further atmosphere work can build on these APIs. Final defaults should be judged off/on at handheld size and in motion, with the more beautiful option retained when it meets the measured stability and frame budget.

## Mod-facing rendering contract

The APIs are additive: existing queued models, texture constructors, native model factories and shader-option constructors keep their signatures. Unspecified materials remain matte. Missing material metadata does not reject older packs. The bundled model/artwork workstreams retain their existing geometry identity, animated parts, indexed page/CLUT words, UV normalization, STP partitions and replacement rollback behavior.

- `QueuedModel.ui()` protects a draw. `queueUiOrthoModel` and nested menu UI scopes provide shared entry points. Queue acquisition resets UI, emission and material state; scope cleanup uses `finally`.
- `QueuedModel.emissive(amount)` explicitly marks luminous content. UI never emits. Shared additive effect routes supply this metadata; reduced-flashing mode suppresses their new glow/light contributions. Ordinary bright paint, skin, text and palette entries are not selected by luminance.
- `Obj.surfaceMaterial`, `TmdObjTable1c.surfaceMaterial` and `QueuedModel.surface` accept `MATTE`, `CLOTH`, `SKIN` or `METAL`. ModelsHD accepts an optional `surface` string per animation part. Authored tags survive GPU-object rebuilds and replacement; omitted tags inherit the active source. Invalid tags reject the pack through its existing fallback. This does not invent per-face semantic masks for untagged assets.
- `Texture.hdFiltering()` marks a full-image RGB/RGBA color texture. All HD field environment replacements use this route, independent of which map or pack supplied them. Integer palettes, depth and coverage textures reject color filtering. Full-canvas foregrounds discard empty source texels before filtered reads; explicit gradients remain valid across those discards. RGB can use mipmaps while alpha/STP and zero-texel classification use the original level-zero texel.
- `Texture.hdAtlasFiltering(duplicatedGutterPixels)` requires at least four duplicated edge pixels and caps mip level conservatively for a 4x anisotropic footprint. Unpadded model atlases retain their original sampling until their producer declares a safe gutter. This prevents an engine-wide filtering toggle from silently corrupting UV islands. Texture uploads invalidate their mip chain; regeneration occurs on the next use, and configuration changes restore original min/mag sampling. Anisotropy is capability checked and clamped to the driver limit.
- `RenderEngine.effectLight(worldPosition, r, g, b, radius)` lets a mod submit an emitter in the same world coordinates as queued 3D models. Nearby particles share a light, brighter emitters win at capacity, radius is capped at 512 world units and each contribution is bounded. The collector follows queued-frame lifetime, including pause/frame advance and skipped render frames. Only opaque lit geometry receives the new light. Painted background lighting is not reconstructed or guessed.

The retained MRT approach preserves PS1 draw order and compositing: opaque scene draws replace coverage; translucent non-UI draws preserve it; translucent UI writes a binary coverage value independently of color blending. Fades also protect their coverage so glow does not leak through black transitions. Scissor/discard decisions happen before auxiliary outputs.

## Stability and performance bounds

The post pass uses bounded spatial kernels: nine maximum coverage fetches, nine bloom samples, five sharpening samples and the existing bounded edge filter. There is no frame generation, temporal accumulation, motion blur or extra input buffering. Full-image mipmaps add approximately one third of texture storage and are generated at load/update time rather than each frame. Existing cut/unload texture ownership releases them with their parent texture. The larger full-canvas HD foreground sets still need memory measurements on Deck.

Four lights are the hard shader/collector budget. Unknown surfaces have a subtle matte response; strong metallic highlights require an authored tag. Nonfinite emitter inputs and invalid post-effect strengths are rejected or bounded. Singular transforms retain legacy shading. Indexed mask state changes are cached and blend changes disable mask blending without repeating that operation for every primitive. Shader uniform handles are cached and refreshed on successful reload; a failed compile or link preserves that program's previous working version in both OpenGL and GLES.

`headless-modern-rendering-probe.c` compiles and draws the shipping standard, field, battle and screen shaders in a windowless CGL context. It verifies MRT/R8 completeness, binary UI coverage under half blending, non-UI translucent mask preservation, selective emission, zero auxiliary clears, disabled output against the committed baseline, distinct material response, local-light hue/radius and reset, unlit/translucent bypass, HD mip filtering with exact transparency/STP coverage, protected UI/fringe pixels, deterministic bloom/sharpening and bounded sharpening. These are controlled fixtures; they are not screenshots or a whole-campaign soak. GLES Java compilation passes; the GPU probe exercises desktop OpenGL only.

The optional `NativeRendererProbe` additionally exercises actual Java OpenGL backend objects: failed compile/link rollback, successful reload/deletion, cached uniform refresh, MRT/R8 creation, indexed mask/blend state, clears, atlas mip limits, live filtering changes, uploads after switching active texture units, mip regeneration and deletion. A cached binding previously allowed uploads to reach the wrong active unit; both OpenGL and GLES upload paths now select unit zero explicitly. The backend probe passes without opening a window or booting gameplay mods. Its intentionally malformed shaders produce expected error log messages.

To reproduce the backend probe on macOS with the project's Java runtime:

```sh
clang -dynamiclib -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin" scripts/headless-renderer-context.c -framework OpenGL -o /tmp/librenderer-context.dylib
./gradlew nativeRendererProbe -I gradle/native-renderer-probe.gradle -PrendererContextPath=/tmp/librenderer-context.dylib --console=plain
```

The changes are included in the existing core/graphics packaging paths. Source and tests are public project deliverables. Game images, unmodified extracted data and private diagnostics remain outside the custom deliverables. Physical Steam Deck frame times, memory/battery behavior, real input responsiveness and final visual acceptance remain unverified.
