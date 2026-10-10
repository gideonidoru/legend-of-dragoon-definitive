# Full CharHD coverage ledger

CharHD owns every character texture route: all party battle forms, field/world appearances, NPCs, story and scripted variants, enemies and bosses. Portraits, geometry, environment art and effects remain with their existing owners. A completed restoration batch is not completed CharHD.

The machine-readable [complete coverage ledger](../../integrations/charhd/production/complete-coverage-ledger.json) accounts for every currently discovered model/TIM pair and separately lists unmapped routes and failed sources. It records remaining work for each pair, including identity, runtime delivery, faithful AA artwork, native behavior and physical Deck acceptance.

| Stage | Current verified count |
| --- | ---: |
| Discovered source-bound model/TIM pairs | 816 |
| Source consumer bindings | 6,945 |
| Generated, source-validated restoration candidates | 678 |
| Models without textured faces; color atlas does not apply | 13 |
| Animated palette/texture or extra-data holds | 103 |
| Format/mapping holds | 22 |
| Unbound routes requiring model/texture association | 69 |
| Source-read failures requiring investigation | 33 |
| Selected runtime pairs | 1: Dart's authored development pilot |
| Final AA art / native visual / physical Deck acceptance | 0 / 0 / 0 |

The 816 pairs are a discovered population, not the complete game denominator. The geometry catalog has 1,324 containers, including other mod owners and unresolved actors; it is not a character count. Unresolved field actors must be identified before asserting complete NPC coverage. Missing and scripted consumers remain explicit. No whole-game completion percentage is valid.

The generated subset comprises 19 party battle forms, 15 party field/world pairs, one confirmed NPC, 72 boss/story pairs, 116 enemy pairs and 455 unresolved field actor pairs. These are distinct source model/TIM combinations, not distinct named characters. Custom candidates are public on main; only the selected Dart revision is currently bundled in CharHD's normal installer JAR. The battle material adapter exists; full palette-aware field/world and broad runtime selection remain unfinished.

Remaining work is the entire program, without implicit completion at a batch boundary:

1. Reconcile all extracted character sources and script/model/texture consumers. Resolve the 69 unbound routes, 33 source failures and unresolved actor ownership. Include every scripted costume, transformation and world actor; keep living creatures distinct from environment/vehicle props.
2. Resolve the 22 format/mapping cases using actual native texture addressing. Preserve palette changes and VRAM texture animations for the 103 extra-data cases instead of freezing them.
3. Support source-bound palette atlases in battle, field and world rendering, including model reloads, texture swaps, shared consumers, existing-mod priority and resource teardown. Different TIMs sharing one geometry require a complete pair identity.
4. Complete the faithful AA art and explicit material pass across every applicable source. Baseline upscaling has not been accepted as that finish. Retain original identities, costumes, expressions and silhouettes.
5. Select verified custom revisions for the normal CharHD installer payload and test combined-mod rendering, gameplay transitions and physical Steam Deck performance. Public/versioned artwork does not establish these acceptance results.

Release readiness remains **false** until the full denominator is established, all applicable routes are accounted for and delivered, and required acceptance evidence exists. Headless CPU mesh and source-mask checks establish their stated boundaries only. They cannot replace native visual or physical Deck acceptance.
