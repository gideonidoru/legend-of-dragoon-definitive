# Architecture and available extension points

Source inspection at upstream `fba1543543865e29ee572f479003d9b47158eeb3`; extension candidates below are code evidence, not runtime compatibility proof.

## Engine map

| Area | Source | Responsibility |
| --- | --- | --- |
| Bootstrap/services | `src/main/java/legend/core/GameEngine.java`, `legend/game/Main.java` | Unpacking, platform, rendering, audio, mods, registry and config initialization |
| Platform/input | `legend/core/platform/SdlPlatformManager.java`, `SdlInput.java`, `input/` | SDL windows/controllers and input actions/bindings; gamecontrollerdb.txt |
| Rendering | `legend/core/renderer/`, `legend/core/gpu/`, `legend/game/textures/` | Native OpenGL/OpenGL ES-facing render/texture pipeline, legacy texture data and atlases |
| Game states | `legend/game/submap/`, `combat/`, `wmap/`, `inventory/screens/` | Field maps, battle, world traversal and menus |
| Scripts/content | `legend/game/scripting/`, `legend/game/unpacker/`, `patches/`, `legend/lodmod/` | Reverse-engineered game behavior, disc extraction, script patches and retail content registrations |
| Persistence | `legend/game/saves/`, `legend/game/modding/coremod/config/` | Saves, campaign/global settings, serializers and mod compatibility |
| Font/localization | `legend/core/font/FontManager.java`, `gfx/fonts/`, `lang/` | JSON glyph metrics/atlas fonts and language overrides |

This is a Java port with a modding API, not an emulator. Keep the existing engine and native platform layers.

## Mod integration seams

`build.gradle` depends on `org.legendofdragoon:mod-loader:4.3.4`. `GameEngine` discovers `./mods` and loads selected mod IDs, initializes registries, event listeners and localization. `CoreMod`, `LodMod` and `TurnOrderMod` provide in-tree examples of `@Mod`, `@EventListener` and namespaced registrars. `ModsScreen`, campaign selection and save configuration track enabled/missing mods. Verify the external loader's packaging/discovery contract before writing a new mod; annotations alone are not a complete tutorial.

| Improvement | Existing seam | Limits to validate |
| --- | --- | --- |
| HD field artwork | `SubmapEnvironmentTextureEvent` exposes disk/cut, replacement background and foreground textures; `RetailSubmap` consumes them with retail fallback | Correct layer ordering, cutout edges, scaling, ownership/deletion and memory pressure |
| Field object textures | `SubmapObjectTextureEvent` offers indexed `Consumer<TextureBuilder>` replacements | Object mapping and pack compatibility; not a universal model replacement promise |
| Icons/UI artwork | `RegisterAtlasTexturesEvent`, `IconMapEvent`, `CoreMod.registerAtlasIcons` | Registered atlas names, layout/UV metrics, licensing |
| Readable menus | `FontManager`, `gfx/fonts/default.*` and `smooth.*`, `FontOptions` sizing/colors/shadows | Per-screen layout/clipping; no proven global scale hook |
| Controller usability | Input action registries, `RegisterDefaultInputBindingsEvent`, `ControllerKeybindsConfigEntry`, deadzone/rumble and Steam Input mode settings | Double input, reconnect, prompts, game/menu focus and Steam overlay |
| Optional convenience | Existing save-anywhere, encounter rate, automatic additions, quick text and run-by-default configs | Gameplay/save implications and faithful preset completeness |
| Gameplay/content mods | Character, equipment, item, battle action, post-battle and engine-state registries; battle, submap, inventory, character and save events | APIs are under active development; pin engine compatibility |
| Language/UI text | `GameEngine` language overrides and `lang/` | Fonts, localization coverage and label length |

## Faithful mode requirements

Faithful mode is a planned preset, not implemented. It should disable Definitive gameplay overrides, retain normal additions, retail encounter behavior, original party restrictions/inventory limits and retail save rules, with a separate original-artwork toggle. Upstream defaults are not necessarily retail: `SaveAnywhereConfig` defaults true, encounter rate defaults AVERAGE, and quick text defaults ALWAYS. Inventory/party/timing settings need a complete source and play audit before naming a preset faithful. Persist choices explicitly and never silently change an existing campaign. Rendering/accessibility options should remain independent of gameplay changes.

## HD pack candidate

The [community Upscaled Graphics Mod](https://legendofdragoon.org/projects/image-upscaling/) identifies an existing Severed Chains-focused artwork effort with backgrounds and UI work. Its public page contains a March 2025 progress update and no completion date. This confirms a candidate project, not an available licensed, complete pack compatible with this checkout. Next: obtain author-provided release/version and terms, inventory coverage and mapping, then test a small representative scene set using the texture events. Do not scrape gallery images or bundle an unverified pack. No pack was downloaded or integrated in milestone 1.

See the [community modding index](https://legendofdragoon.org/modding/) for ecosystem context; current source controls API details.
