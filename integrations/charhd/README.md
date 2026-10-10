# CharHD

Independent presentation mod for Legend of Dragoon Definitive, development release 0.2.0.

Source-bound battle material adapter with one bundled Dart armor development pilot. The full baseline generation pass produced 678 source-bound restoration candidates across party, field/world, enemy, boss, NPC and unresolved actor routes. Another 13 audited models have no textured faces. These candidates remain under art review and are not selected for runtime delivery. The original field texture adapter is retained; full field, enemy and NPC coverage remains open.

Included by the normal Definitive build/installer. Enable or disable `charhd` in the game’s Mods menu. No installer toggle. Requires the matching Definitive engine hooks; unmodified Severed Chains is not supported. See [production status](../../docs/definitive/CHARHD_PRODUCTION.md) for coverage, validation and limitations.

The pilot uses explicit palette surface/roughness metadata with the new material lighting, preserving any source part material where no assignment exists. It does not infer normals from painted highlights. Native visual acceptance and Steam Deck performance remain unverified; this is not the final 2026 AA character pass.

Custom development artwork and authoring prompts are versioned under `production/`; only the selected pilot is copied into `runtime-assets/`. Original source data, diagnostic controls, model weights and inference tools stay outside the repository.

Code: AGPL v3. See ARTWORK-NOTICES.txt for the separate status of game-derived resources. No original discs, model weights or inference runtimes are bundled.
