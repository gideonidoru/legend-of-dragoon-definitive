# Capability verification ledger

Pinned engine: `fba1543543865e29ee572f479003d9b47158eeb3`. Source evidence below was inspected on 2026-10-09; it establishes available code/settings, not device behavior. Runtime verification is pending for every row.

| Capability | Source evidence | Decision / remaining proof |
| --- | --- | --- |
| Java/Gradle requirements | build.gradle targets 25; wrapper 9.1.0; CI JDK 25 | Passed compile/package; README 21 conflict recorded |
| Native rendering/input cadence | RenderEngine sets a 60 limit/input tick; battle code separately uses vsyncMode | Measure each state; do not infer 60 FPS battle from renderer cadence |
| Inventory expansion | InventorySizeConfigEntry: default 32, edit range 1–9999 | Reuse capacity config; test stack/sort/navigation and oversized save behavior |
| Party choice | UnlockPartyConfig defaults true | Faithful candidate disables unlock; verify narrative/progression |
| Running controls | RunByDefaultConfig defaults true | Separate comfort preference from fidelity definition |
| Addition assistance | AdditionMode NORMAL/AUTOMATIC; AdditionTimingWindow default 1.0 | Reuse assistance, build training only after timing baseline |
| Bench experience | SecondaryCharacterXpMultiplier setting; deserialization fallback 0.5 | Include in faithful audit, verify current constructor and game behavior |
| Encounter control | RETAIL/AVERAGE/INTENDED/NONE; default AVERAGE | Faithful requires explicit choice rather than default inheritance |
| Save convenience | SaveAnywhereConfig defaults true; SaveManager/serializers | Backup/restore and save-rule fidelity pending |
| Text | QuickTextMode HOLD/ALWAYS/INSTANT; default ALWAYS | Reuse preference; faithful/input behavior needs play proof |
| Fast speed | Config game_speed_multiplier default 1 and getter | Verify activation and return to normal; keep training/timing tests at normal speed |
| Mod registration/artwork | GameEngine discovery; registries and SubmapEnvironmentTextureEvent/SubmapObjectTextureEvent | Package contract, lifetime/masks and actual pack compatibility pending |
| Controllers | SDL layer, binding configs, Steam Input handling, controller database | Source support exists; Deck reconnection, glyphs, overlay and doubled inputs pending |
| Updates | Updater now queries gideonidoru/legend-of-dragoon-definitive releases (2026-10-09 project change) | See UPDATER_TARGET.md; compatible version pairing and upgrade/rollback validation remain pending |
| E2E tests | Gradle gameplay-tests policy supports explicit runTests opt-in for EngineBootTest only; default task skipped | Synthetic control checks do not establish gameplay correctness; private assets and execution permission remain required |

The [community project page](https://legendofdragoon.org/projects/severed-chains/) still identifies 60 FPS battles as later upstream work; its feature list is not proof for this SHA. The [HD project page](https://legendofdragoon.org/projects/image-upscaling/) identifies an artwork effort, not a verified downloadable/redistributable pack for this checkout.

Faithful mode is a product contract requiring a complete audit beyond these examples. List each setting and mod, retail target, current default, chosen preset value, storage scope, save consequences and a regression scenario before implementing it.
