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
| Legacy model-texture enhancement | Optional offline enhancement tools plus versioned external outputs; SubmapObjectTextureEvent is a field-object starting seam | Battle/character/enemy loader and UV/palette/alpha compatibility need separate audit; no universal high-resolution model hook verified |
| Field object textures | `SubmapObjectTextureEvent` offers indexed `Consumer<TextureBuilder>` replacements | Object mapping and pack compatibility; not a universal model replacement promise |
| Icons/UI artwork | `RegisterAtlasTexturesEvent`, `IconMapEvent`, `CoreMod.registerAtlasIcons` | Registered atlas names, layout/UV metrics, licensing |
| Readable menus | `FontManager`, `gfx/fonts/default.*` and `smooth.*`, `FontOptions` sizing/colors/shadows | Per-screen layout/clipping; no proven global scale hook |
| Controller usability | Input action registries, `RegisterDefaultInputBindingsEvent`, `ControllerKeybindsConfigEntry`, deadzone/rumble and Steam Input mode settings | Double input, reconnect, prompts, game/menu focus and Steam overlay |
| Optional convenience | Existing save-anywhere, encounter rate, automatic additions, quick text and run-by-default configs | Gameplay/save implications and faithful preset completeness |
| Gameplay/content mods | Character, equipment, item, battle action, post-battle and engine-state registries; battle, submap, inventory, character and save events | APIs are under active development; pin engine compatibility |
| Language/UI text | `GameEngine` language overrides and `lang/` | Fonts, localization coverage and label length |

## Faithful mode requirements

Faithful mode is a planned preset, not implemented. It should disable Definitive gameplay overrides, retain normal additions, retail encounter behavior, original party restrictions/inventory limits and retail save rules, with a separate original-artwork toggle. Upstream defaults are not necessarily retail: `SaveAnywhereConfig` defaults true, encounter rate defaults AVERAGE, and quick text defaults ALWAYS. Inventory/party/timing settings need a complete source and play audit before naming a preset faithful. Persist choices explicitly and never silently change an existing campaign. Rendering/accessibility options should remain independent of gameplay changes.

## Concrete reuse candidates

[Skurfa's HDR backgrounds v1.1.0](SKURFA_RESEARCH.md) is the first concrete HD-pack candidate. Its standalone mod uses SubmapEnvironmentTextureEvent; its Java source compiles against our pinned baseline. The source covers 43 disk/cut branches using 38 asset sets, not the whole game. MIT covers original code/tools, while the project's notices exclude derivative game artwork. Prefer a user-installed author release and evaluate its existing adapter before building another. Runtime scene correctness, SDR appearance and Deck memory/frame times remain unverified; cut 21's full-size foreground layers imply a 515.625 MiB base-level RGBA8 allocation estimate, not a measurement. No pack is installed or bundled.

[Quality of Life+](QOL_FORK_RESEARCH.md) offers action footers, equipment filters/sorts and purchase/sale quantity prompts worth adapting selectively. Use current binding-aware InputCodepoints and inventory APIs. Do not merge the fork wholesale: its experimental tag is an old upstream ancestor, release binaries are not traced to source, and its feature branch uses V7 saves versus our V10. Addition feedback needs a narrow failed-input diagnostic seam; gameplay rewards/timing changes remain outside faithful mode. Exclude destructive Ironman campaign deletion.

[Dragoon Modifier](DRAGOON_MODIFIER_RESEARCH.md) is a standalone optional campaign overhaul whose 46 Java source files compile against this baseline. It replaces character templates, item/equipment registrations, progression and enemy rewards even in its Retail NA preset; save files depend on its registry IDs. Its missing published license, independently varied stat-control defect, external preset files and restart/removal behavior need resolution before adoption. Existing engine settings already cover several overlapping convenience options.

[Battle Rewards](BATTLE_REWARDS_RESEARCH.md) is a smaller optional XP/gold concept with AGPL source, but its current source fails a compile check against our API and the release tags point at an older commit. Use current reward/config events for a narrowly scoped adaptation only after provenance and behavior checks. Define reward-handler ownership/composition before pairing it with Dragoon Modifier. Turning modifiers off changes future behavior; it cannot reverse earned gold, experience or progression. Neither has been installed or runtime-validated.

The [community Upscaled Graphics Mod](https://legendofdragoon.org/projects/image-upscaling/) previously explored model textures as well as backgrounds/UI. Its latest visible update is March 2025 and describes paused work with no completion date. The owner reports it was abandoned; current abandonment is not independently confirmed by that page. Treat it as historical context and credit its contributors, without depending on a future release or importing gallery images. The new [legacy model/texture upscaling goal](TEXTURE_UPSCALING.md) is separately scoped and remains unimplemented.

See the [community modding index](https://legendofdragoon.org/modding/) for ecosystem context; current source controls API details.
