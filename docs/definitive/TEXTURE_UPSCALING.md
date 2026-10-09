# Legacy model and texture upscaling

Added at the owner's request on 2026-10-09. **Planned goal; no upscaler implemented or chosen, and no game textures processed.** Improve the appearance of original character, enemy and environment models through an optional, reproducible texture-enhancement pipeline. Preserve the game's art direction and make original-texture fallback available independently of gameplay presets.

## Prior community effort

The [Upscaled Graphics Mod page](https://legendofdragoon.org/projects/image-upscaling/) credits theflyingzamboni, Zychronix and Mr. Scentless and includes model textures among its experiments. Its March 16, 2025 update says the old emulator-focused results were discarded and the effort moved to Severed Chains; work was paused around other projects. The owner reports abandonment, but the public page still describes an intention to resume and supplies no completion date. Its present delivery status is therefore unverified. We will not make Definitive depend on its completion, assume its assets are available, or copy previews without established terms.

## Separate enhancement paths

| Path | Intended improvement | Scope |
| --- | --- | --- |
| Offline texture upscaling | Clearer character clothing/faces, enemy detail and environmental surfaces | First target: process textures ahead of play and load an optional external pack. Preserve original mesh, silhouette, UV layout, skeleton and animation. AI-assisted methods may be evaluated; no algorithm or model is selected. |
| Whole-frame upscaling | Improve output quality or performance at a chosen internal resolution | Separate D14 renderer experiment after baseline evidence; assess motion, UI text, particle effects, latency and power. Sharpening alone is not texture reconstruction. |
| Higher-detail geometry | Smoother silhouettes or authored model replacements | A later, separately scoped option. Image super-resolution cannot automatically add correct mesh topology, rigging or animation. No engine or model-system rewrite is planned. |

The first path extends the artwork goals beyond Skurfa's background coverage. It does not require applying a neural model every frame on a Steam Deck. Respect the existing engine and seek a small compatibility hook only if a demonstrated loader limitation needs one.

## Existing source seams and open questions

At fixed baseline `fba1543543865e29ee572f479003d9b47158eeb3`, [SubmapObjectTextureEvent](../../src/main/java/legend/game/modding/events/submap/SubmapObjectTextureEvent.java) exposes indexed TextureBuilder replacements. [RetailSubmap.prepareSobjModel](../../src/main/java/legend/game/submap/RetailSubmap.java) consumes them and builds the model using original texture dimensions; [SMap.renderSmapModel](../../src/main/java/legend/game/submap/SMap.java) binds the override. This is source evidence for a field-object trial, not proof of all-model high-resolution support.

[CharacterTemplate](../../src/main/java/legend/game/characters/CharacterTemplate.java) supplies character battle texture paths and [Battle](../../src/main/java/legend/game/combat/Battle.java) loads character/enemy TIM data. Those paths do not establish a generic high-resolution RGBA replacement contract. Audit battle loaders, normalized UVs, palette variants, transparency/blending, animated textures and texture ownership before claiming character/enemy coverage. Avoid replacing character registries solely for presentation if that introduces unnecessary save dependencies.

## First implementation slice and acceptance

1. Inventory representative field-character, battle-character, enemy and environment textures privately, recording mappings and unsupported cases. Include small faces, transparent edges, palette swaps and animated surfaces.
2. Use a clearly licensed synthetic fixture to compare a simple reference upscale with candidate enhancement methods. Pin tool/model versions, weights hashes, inference settings, licenses and source/output hashes. Choose by evidence rather than a universal scale factor or marketing label.
3. Process a small private asset set only within the authorized implementation/test scope. Keep extracted and derived game textures, weights and runtimes outside Git; repository deliverables are tools, manifests, validation and permitted synthetic fixtures. Resolve output/pack distribution terms separately.
4. Validate UV seams, alpha edges, palette-dependent variants, animation consistency and preserved visual identity. Reject hallucinated symbols/facial detail, shimmer, sharpening halos and stretched/clipped mappings. Compare at handheld viewing size and enlarged inspection size.
5. Compare original vs enhanced textures on the same Deck scenes/settings: load time, transition peaks, system/texture memory, frame-time percentiles and power. Select sizes from the measured budget; do not assume 8x textures fit or improve handheld results.
6. Demonstrate per-pack disabling/missing-output fallback, scene transition cleanup, suspend/resume and unchanged gameplay/saves. Keep the original texture path intact. Geometry improvements and whole-frame effects need separate acceptance.

Inputs, extracted assets, generated packs and gameplay screenshots stay private unless publication rights and scope are established. No model downloads, creator messages, purchases, game launches or engine changes occurred while adding this goal. D16 tracks this work; D03/device evidence and API/mapping audits remain prerequisites to runtime claims. The existing provisional schedule is not a promise of whole-game model remaster coverage.
