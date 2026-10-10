# Visual fidelity and Steam Deck experience

The target is coherent, exceptionally polished presentation across backgrounds, character/enemy/environment models, textures, effects, menus and the install/play workflow. Original concept art and in-game identity control faces, costume markings, silhouettes and animation. Skurfa's enhanced background treatment supplies a world-art reference: model detail should belong in those painted environments. Photoreal skin, glossy generic materials or invented clothing symbols are not automatic improvements.

## Quality gates

| Area | Evidence required before making a default |
| --- | --- |
| Texture reconstruction | Original/control/candidate crops at handheld and enlarged sizes; recognizable symbols, face and material shapes; no ringing, paint loss or invented marks |
| Palette and alpha | Per-face CLUT usage and animation mapped; STP/discard/blending preserved; no color shifts, translucent halos or missing black pixels |
| UVs and animation | Idle/walk/combat pose comparisons; no shell bleeding, visible seams, stretched faces or inconsistent detail over frames |
| Model detail | Authored, rig-compatible geometry; preserved silhouette/character identity; joint deformation and weapon attachment checks; no automatic promise from image upscaling |
| World integration | Same-scene comparison with original and Skurfa backgrounds, occlusion/layer transitions, matching color and lighting treatment |
| Handheld interface | 1280×800, readable text and focus states, long strings, touch/controller routes, reconnect and Steam Input/Gaming Mode |
| Player preference | Side-by-side community feedback with controls and tradeoffs disclosed; retain original presentation; no claim of community approval before feedback |

Highest-fidelity and balanced Deck profiles may choose different asset sizes once evidence supports them. Faithful gameplay remains independent of artwork and model presentation. No mandatory reward, difficulty or timing changes should accompany a visual profile.

## Take useful advantage of the Deck APU

The Deck shares CPU/GPU power and memory. Move heavy neural enhancement to the offline asset pipeline, then render reviewed ordinary assets. Avoid real-time neural work or frame generation that could add latency or disrupt timed Additions. Preserve existing engine behavior and measure before changing its renderer.

A 256×256 decoded RGBA comparison occupies 256 KiB; 512×512 uses 1 MiB and 1024×1024 uses 4 MiB, excluding mipmaps and driver overhead. The sampled 4-bit TIM payload is about 34 KiB including its palette, so replacing indexed storage with RGBA costs substantially more than a scale-factor-only comparison suggests. Creating one full atlas for every unused palette would waste memory; audit which material variants are actually used. Actual engine VRAM sharing/allocation and residency still require measurement.

Record equal-scene warm/cold load times, transition peaks, texture/system memory, frame-time median/p95/p99, CPU/GPU utilization and power at the same brightness, resolution and cap. Inspect battles, busy villages, translucent effects, FMVs and menus; repeat with original versus HD backgrounds and model candidates. Treat suspend/resume, controller reconnect, long play and offline updates as experience requirements.

Targeted opportunities after that baseline: avoid duplicate texture uploads and unnecessary decode work, bound caches, reuse immutable mod assets, prepare work away from the render thread, stream only necessary variants, and select filtering/mipmap behavior by actual UV/seam results. Evaluate whole-frame upscaling only if a measured GPU bottleneck justifies it. Do not promise arbitrary 60 FPS or change gameplay timing to meet a marketing target.

## Current status

Installer layout and input-routing fixtures, native Linux archive/shortcut checks, and five Mac engine/preset smoke tests pass. The [October 10 alpha installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/definitive-alpha-2026-10-10-refinement-2) is published with its [delivery evidence](PRESENTATION_PASS.md). Private nearest/Scale2x/neural texture comparisons exist, with model/source/output identities recorded. Per-face palette audits produce correctly colored Dart and one field model atlas without conflicting palette coverage. Mask-restored and palette-isolated experiments retain original STP/discard coverage exactly in those samples, while inferred detail and UV/filter footprints remain unaccepted. The [private reconstructed Haschel head](LOCAL_MODEL_RECONSTRUCTION.md), refined body and bound rear ribbons now have a [posed GPU comparison](NATIVE_SHADER_STUDY.md) using controlled lighting. The face is visibly clearer; shoulder construction, skin detail, clothing, native integration and world matching remain unfinished. No neural pack or reconstructed mesh is active or shipped; runtime model geometry is unchanged. Physical Deck measurements and community preference are still open.

The next useful seam is posed-model comparison and a conservative palette-aware loader contract: bind a candidate to original model and TIM identities, verify CLUT dependencies and UV/filter footprints, and fail back to original artwork. Authoring better geometry and packs should proceed from the material inventory, not a wholesale renderer rewrite. Texture enhancement alone does not add polygons, repair joints or create higher-fidelity animation.
