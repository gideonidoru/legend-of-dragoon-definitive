> **October 10, 2026 update:** CharHD 0.3.0 now includes the owner-approved Dart head/hair reconstruction and matching 2K material, plus its preferred custom body shapes. Geometry/material activation is atomic and composes with the current body atlas. This passes 24 bundled CPU combinations and a six-check native Mac battle trial without experimental JARs or external packs. The full body and physical Deck remain unvalidated. See [party scope](PARTY_RECONSTRUCTION.md) and [promotion evidence](../../integrations/charhd/production/party-reconstruction/DART_PROMOTION.md). The 0.2 baseline census and historical pilot evidence below remain valid as historical records.

# CharHD production, 10 October 2026

Target: a beautiful 2026 AA presentation that preserves The Legend of Dragoon's recognizable character designs, color language and painted retro heritage. Resolution alone is not acceptance. Geometry remains the ModelsHD workstream; portraits remain UIHD; spell effects remain FxHD.

## Full scope

CharHD covers the entire character texture program: party battle forms, party field/world appearances, enemies and bosses, story and generic NPCs, and scripted character variants. The 19 named playable battle forms are the first production subset, not the mod's total scope or completion denominator.

`integrations/charhd/production/full-scope-coverage.json` tracks these categories separately. Current extraction contains 244 nonempty enemy/boss battle texture files with 226 distinct raw texture hashes; duplicates and source-bound model consumers must be reconciled before counting finished enemy assets. Party field/world discovery has 15 model/TIM pairs using 13 texture identities. The geometry catalog also has 536 unresolved actor containers requiring ownership classification; that number is not a verified NPC population. Story/generic NPCs and scripted variants need a complete consumer census. No whole-game completion percentage is valid yet.

## Full restoration batch

The expanded audit found 816 distinct source model/TIM pairs across 6,945 bindings. The initial restoration queue contains 691 entries: 678 have texture regions to reconstruct and 13 have no textured faces, so a color atlas does not apply. Another 103 pairs retain animated/extra-data holds and 22 retain format/mapping holds. There are also 69 unbound routes and 33 source failures requiring investigation; these counts are not a complete game population.

The generation pass produced all 678 textured restoration candidates. The 13 untextured models require no color-atlas reconstruction. These are development baselines; final AA artwork and native/Deck acceptance remain at zero.

The resumable queue uses the existing pinned 4× restoration model at 0.75 strength, preserving palette separation, exact STP/discard masks, visible black and UV coverage. Failed or interrupted revisions remain available for diagnosis; retries use a new folder. Already produced packs are validated against their original source hashes before reuse. No-texture entries are recorded explicitly without inventing artwork.

`full-candidate-coverage.json` records publication progress for every entry of the original queue. New custom baselines use model/TIM pair identities under `production/full-candidates`; unresolved field actors retain unresolved ownership. The publisher validates outputs and permits only reconstructed atlas PNGs and a sanitized provenance manifest. Source controls, previews, neural inputs/outputs, machine paths and command logs remain private. Publication is not runtime selection or final artistic approval.

## Current delivery

CharHD 0.2.0 builds independently on the current main engine and is included by the normal package staging task. Its source-bound battle adapter loads validated per-palette color atlases and optional explicit surface/roughness metadata. The bundled development pilot is Dart's normal battle armor. The original field texture pilot adapter remains available; comprehensive field, NPC and enemy artwork is still open.

All 19 named playable battle forms have 4x palette-isolated restoration candidates: nine normal, nine Dragoon, and Divine Dart. Custom candidates are versioned in `integrations/charhd/production`; originals, unmodified controls, extraction, poses, saves, runtimes and model weights remain outside Git. The full baseline pool is under review, not the final AA art pass. One authored Dart armor revision is selected for development inspection; zero assets have final AA, native visual or physical Deck acceptance.

The source census binds each model/TIM pair by hash. Restoration preserves exact original STP, discarded pixels, visible black and native UV coverage. Authored tiles retain the same verified atlas layout and restore those masks after a single recorded size normalization. Authoring provenance identifies the base atlas, generated image and exact prompt. These identities establish reproducibility and association, not artistic quality.

## Material integration

Use the existing `SurfaceResponse` flags to distinguish lacquered armor, cloth, leather and skin. Assignment is explicit per verified palette, with roughness in the engine's supported range. Mixed or unreviewed palettes retain the source part's material; omitted metadata never guesses from brightness. No normals are manufactured from painted highlights. Existing engine material finish and lighting still apply; deliberately authored normal/roughness maps can follow after native review shows a need.

The optional meshes use the original CPU geometry, part identities and animation inputs. ModelsHD may provide its refined GPU geometry through the existing event seam. Texture ownership and geometry ownership remain separate. An earlier UV/packet override keeps priority; unknown authored geometry, native vertex-index consumers, CLUT animation or extra subfiles retain their original route. Malformed packs log a fallback and keep original assets. Optional GPU resources are released on model deletion, texture replacement and combatant teardown.

The battle event receives bounded copies of the raw model and TIM before UV relocation. Independently arriving sources are paired and consumed on the render thread, in either completion order. Changed source pairs clear the prior appearance; delayed callbacks from an earlier combatant generation are rejected. Packs match both hashes, validate every region and every STP/discard pixel, then allocate optional resources. Color is bound to the direct texture slot; native indexed effects and transforms remain in their original slots. Face-specific surface flags preserve part-wide materials when no palette assignment exists.

## Evidence and limits

The NoopApi source probe constructs all 19 source pairs with ModelsHD disabled and enabled: 38 CPU mesh combinations. It checks finite mapped UVs, bounds, source ownership, animation/native-index fallbacks and teardown. It does not draw through OpenGL or prove native blending, game lighting or performance. Synthetic Java tests cover relocation, conflicting palettes, material metadata and native mesh attributes; Python fixtures cover packing and source-mask rejection.

Dart armor-v1 has been inspected at three original keyframes and three angles using the offline pose renderer. It provides cleaner armor panels while keeping the original crimson design and silhouettes. Its fixed highlights need review under native material lighting. Face, hair, sword, trousers and boots remain baseline restoration; this pilot is visibly incomplete as a whole-character AA remaster.

The 19 baseline atlases total 179,019,776 bytes of base-level RGBA data if all were decoded together. They are not all bundled or simultaneously resident. Dart's pilot is 14,680,064 bytes before any GPU overhead. Neither number is a measured Deck residency or frametime result.

## Next acceptance steps

1. Review Dart under native battle lights, blending, shadows, model changes and scene teardown. Compare CharHD off/on and ModelsHD off/on at normal playing distance. Adjust painted highlights and roughness together.
2. Reconstruct faces and the remaining prominent costume materials for Dart, Haschel and Meru. Retain expressions, silhouettes and original details; inspect each result on the posed model before extending the style.
3. Promote the remaining normal and Dragoon forms only after comparable art and native checks. Verified source associations do not make a generated asset finished.
4. Review the deduplicated party field consumers from the model catalog. The 1,026 bindings reduce to 15 model/TIM byte pairs with 13 distinct textures, all source-format eligible. Two pairs still lack a named entity assignment. These are not accepted field textures; audit enemy/NPC ownership independently.
5. Measure GPU residency, load/transition behavior, p95/p99 frametimes and power on a physical Steam Deck. Keep the default installer simple and activation in the game's existing Mods menu.

The consolidated artwork build delegates CharHD to its specialized 0.2 source set; the other three artwork task definitions remain unchanged. The package must contain exactly one CharHD jar. Do not overwrite the active EnvHD production checkout.
