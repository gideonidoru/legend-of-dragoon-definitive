# Legend of Dragoon: Definitive

## [⬇ Download the Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop)

**Start here in Steam Deck Desktop Mode.** The guided installer downloads the engine, Java, bundled HD mods and enhanced cinematics, then prepares your own game discs. No separate model transfer or asset-generation setup is needed.

[Release notes](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest) · [Portable installer ZIP](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Definitive-Installer.zip) · [Installation and recovery help](docs/definitive/INSTALLER.md)

<a href="https://ko-fi.com/gideonidoru"><img src="https://storage.ko-fi.com/cdn/kofi5.png" alt="Buy me a coffee on Ko-fi" height="36" /></a>

If you're enjoying Definitive, you can [buy me a coffee](https://ko-fi.com/gideonidoru) to support the project.

Definitive is a Steam Deck-first modernization of *The Legend of Dragoon*, built on [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains). We want the game to feel as good to return to today as it did the first time: familiar characters and painted environments, clearer visuals, comfortable controls, and an installation you can get through without assembling a collection of tools and mods yourself.

**This is an unofficial community alpha.** The installer and launcher have been confirmed working on a physical Steam Deck. The newer models, cinematics and rendering changes still need device testing; the complete visual remaster is in progress.

## Install and play

1. Switch your Steam Deck to **Desktop Mode** and download the installer above. Use a fresh copy if you tried an earlier release; older shortcuts can point to older builds.
2. Open `Install-Definitive.desktop` in **Dolphin**. Allow execution if KDE asks.
3. Choose your installation folder and supply your **four US game-disc images**, or reuse discs from an existing installation. BIN/raw ISO files and ZIP, RAR or 7z containers are supported.
4. Let setup download and prepare the game. Add the optional Steam library shortcut, then launch from Steam or select **Play** in the Definitive launcher.

The default folder is `/home/deck/Games/Legend-of-Dragoon-Definitive`; custom and SD-card locations are supported. The initial download is a small shortcut. Setup then downloads the roughly 13 MB installer interface, Java and a **roughly 1.7 GB game package**, in addition to preparing your discs. Game discs are not included, and your source images stay untouched.

The launcher checks for updates and can restore a previous local version. **Repair** verifies managed files and downloads only missing or changed files, preserving prepared game data. **Update** reuses matching verified files, retrieves changed/new files and omits removed files. **Reinstall** downloads the full application and prepares a fresh workspace while retaining saves, settings, custom mods and imported discs. **Uninstall** removes ISOs only if you explicitly choose that option.

Setup shows overall progress and current-file progress. Downloads and activation are checked against the release inventory, with interruption recovery and protection against maintenance while the game is running. Only the current release is kept on GitHub, so use the download button above rather than an older shortcut. Local Restore versions and personal data are retained. [Installation and recovery help](docs/definitive/INSTALLER.md).

![Definitive launcher with Play, artwork choices, version restore and Steam library actions](docs/definitive/images/launcher-linux.png)

*Linux launcher preview. This image shows the interface; it is not a Steam Deck gameplay capture.*

## What we're aiming for

The visual goal is a polished modern AA game with a deliberate retro aesthetic. That means keeping the original compositions, silhouettes, costumes and atmosphere while improving the things that stand out on a modern screen: angular faces, blocky limbs, uneven material detail, jagged edges and low-resolution cinematics.

The goal covers the whole campaign: every character category, environment, interface family, cinematic and effect. Early pilots help establish the style and runtime support; they do not narrow that goal or stand in for full coverage.

There are several parts to making that work together:

- **A consistent world.** Build around Skurfa's HD backgrounds, then fill missing environments, battle panoramas, world-map artwork and props. Keep foreground masks and scene placement correct so characters still walk behind the right trees, walls and doors.
- **Recognizable, better-shaped characters.** Refine the broad model roster efficiently, then give faces, hands, hair, costumes and awkward joints the individual attention they need. Preserve the original animations and transformations.
- **Lighting that belongs to the scene.** Give models smoother shading, restrained material highlights, soft contact shadows and light from luminous effects, while respecting the painted backgrounds' own lighting.
- **Clear handheld presentation.** Keep dialogue, menus and timing prompts crisp. Improve the surrounding image without obscuring the cues needed for Additions.
- **An easy return to the game.** Put installation, updates, recovery and controller-friendly navigation in one place. Keep convenience settings and visual choices independent.
- **Quality that holds up on Deck.** Choose defaults through comparisons in motion and measurements of frame times, memory, loading and power. A successful build is only one part of that evidence.

Our approach is incremental: reuse good community work, add separate mods, and extend the engine where those mods need a small shared feature. Skurfa's existing art stays intact. The broad geometry pass now reaches eligible models throughout the game. Further remodeling prioritizes party faces and costumes, prominent NPCs, bosses and dragons, then common enemy families and visible props. [Visual direction](docs/definitive/CHARACTER_REMASTER.md) · [Model roadmap](docs/definitive/MODELS_HD_ROADMAP.md) · [Project plan](docs/definitive/PROJECT_PLAN.md).

## ModelsHD: the broad first pass

ModelsHD 0.4 inspects all **1,343 supported canonical model containers**. **964 receive some smoothing; 379 keep their originals** because their topology is unsafe or refinement adds no useful detail. The mod bundles **8,531 unique custom part replacements**, including the nine confirmed party field models and all 19 party battle forms, with eligible NPCs, bosses, enemies, scenery and world-map resources. Original animation/script data and active texture addressing remain intact.

Version 0.4 is merged into main and included in the consolidated source build; the current public installer still carries version 0.3. This is a modest first geometry pass. Individual faces, hair, hands and costumes still need deliberate reconstruction. [Build and use ModelsHD](integrations/modelshd/README.md) · [Exact coverage and verification](docs/definitive/MODELS_HD_WORLD_PASS.md) · [Model catalog](docs/definitive/MODEL_CATALOG.md).

## Textures: clearer detail throughout the game

The texture work is just as central to Definitive as the smoother models. We're restoring the painted detail on armor and clothing, scenery, portraits, menus and effects while keeping the original designs recognizable. The aim is a coherent game, with characters and battlefields that belong beside Skurfa's backgrounds.

- **Environments with more detail.** Merged EnvHD artwork includes 70 battle panorama masters, 38 location landscapes and the parchment world map. The next environment changes cover static continent terrain, battle floors, walls and scenery, then the missing field backgrounds and foreground layers. Skurfa's existing artwork stays intact.
- **Sharper character artwork.** CharHD has published 678 palette-aware, 4× restoration candidates across its discovered character sources. Dart's red armor is the first selected, individually reworked battle texture. Faces, hair, clothing and the wider party/NPC/enemy roster still need authored detail, runtime integration and review; the candidate collection is not a finished character pack.
- **A complete default interface source pass.** UIHD 0.3 is merged with **738 custom resources**: all nine portraits, battle and menu artwork, save-card portraits, world-map cues, exploration indicators, dialogue, chapter cards, credits, title/end screens and the loading eye. Small icons and portraits use 4× artwork; larger sheets use 2×. This completes the audited default raster coverage, with in-game visual acceptance still ahead.
- **Effects that keep their animation.** Six improved field textures are merged. The broader [FxHD change under review](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/19) covers **6,466 audited effect source paths** with **2,160 unique 2× detail maps**, reaching spells, enemy and boss attacks, transformations, cutscenes, field effects, world-map smoke and title flames. Palette changes, texture copies and scrolling still drive the original animation.

The environment work under review includes **292 selected terrain masters in eight atlases** and **518 battle-material candidates**, with 12 of the latter flagged for bespoke repair. The full field-art worklist identifies **5,162 unowned pictorial images** to restore, with backgrounds and movable/hidden foreground layers kept separate. These are distinct worklists, not numbers to add into a completion percentage. [Terrain integration](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/10) · [Battle materials](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/14) · [Field artwork](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/17).

Restoration happens before installation, so the Steam Deck loads ordinary prepared textures. We preserve palette separation, transparent edges, visible black pixels, original placement and animation; an invalid or changed source keeps its native artwork. Higher resolution gives us room for clearer detail, but each result still needs artistic review at playing distance. The download above contains the published release; merged source and open texture work are described separately below.

## Before and after

These comparisons document the **earlier ModelsHD 0.3 party battle pass**. Version 0.4 uses a more conservative crease threshold across the full supported catalog; these images do not preview that new recipe. Both sides use the same textures, pose, camera, scale and lighting, so the visible change comes from geometry. The lower panels remove textures to make the smoother surfaces easier to see.

### Dart

![Dart before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/dart-before-after.png)

Dart's battle mesh goes from **870 to 2,946 triangles**. The first pass rounds eligible surfaces while retaining the recognizable armor, sword and silhouette.

### Meru

![Meru before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/meru-before-after.png)

Meru's battle mesh goes from **806 to 2,687 triangles**. The untextured view makes the changes to limbs and clothing more apparent than the original textures do.

**These are offline model previews, not gameplay screenshots.** They document the earlier 0.3 pass. Newer world-pass and Dart face/remodeling experiments need their own matched comparisons before they can be presented here. This is a modest first refinement, not the finished character remaster. Clearer faces, better hands, stronger costume shapes and natural joints still need deliberate remodeling. [Comparison notes](docs/definitive/images/comparisons/README.md).

## Mods and artwork

The normal installation supplies **Skurfa, ModelsHD, FMVHD, EnvHD, CharHD, UIHD and FxHD** as separate modules. Use the game's mod manager for individual choices; the launcher's original/HD artwork preference also controls managed Skurfa and ModelsHD selection.

**The table describes merged source, not everything in the current download.** The public all-HD repair release still carries ModelsHD 0.3 and earlier artwork pilots. Newer models, SMAA, audio, gameplay conveniences, UIHD and cinematic fixes are merged. A development alpha is being prepared for testing the playback fixes, following the merged engine pacing checks, with final release verification required before publication. That test release will still have partial HD artwork coverage; the full-campaign restoration goal remains unchanged. The download button always points to the current published installer. Bundling all seven modules does not mean their artwork is finished.

| Mod | Full scope | Merged coverage and remaining work |
| --- | --- | --- |
| **[Skurfa's HDR Backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds)** | Existing community field backgrounds and foreground layers; the foundation for our environment style. | Pinned v1.1.0: 38 distinct scene packs and their aliases. Existing artwork and attribution are preserved. |
| **[ModelsHD](integrations/modelshd/README.md)** | Party, NPC, enemy, boss, scenery and world-map geometry, preserving original animation and scripts. | Merged v0.4: 964 supported containers receive refinement, including the nine party field models and all 19 party battle forms; 379 retain originals. Bespoke faces, hair, hands and costume reconstruction remain unfinished. |
| **[FMVHD](integrations/fmvhd/README.md)** | Every cinematic, with enhanced playback and original-video fallback. | All 18 films at 1280×768, original 15 fps cadence and 5:3 proportions. No generated motion frames. Full-game playback and physical Deck acceptance remain pending. |
| **[EnvHD](integrations/envhd/production/README.md)** | Field scenes and foregrounds, battle environments, world-map terrain/scenery and environmental props, alongside Skurfa's existing work. | Merged: 70 distinct battle panoramas through 73 source bindings, 38 location landscapes and one parchment world-map backdrop, alongside two battle-material pilots. Terrain/scenery expansion is under review; unowned field scenes, remaining battle materials, props and animated surfaces remain unfinished. |
| **[CharHD](docs/definitive/CHARHD_PRODUCTION.md)** | Party battle/field/world appearances, NPCs, enemies, bosses and scripted variants; texture ownership stays separate from ModelsHD geometry. | Merged: all 678 textured candidates from the initial restoration queue, plus the source-bound battle adapter and selected Dart armor pilot. Candidates are public development baselines, not 678 installed or accepted replacements. Actor identification, unsupported routes, runtime selection and final art remain open. |
| **[UIHD](docs/definitive/UIHD_PRODUCTION.md)** | Full default HUD, all nine portraits, menus, dialogue, world-map/exploration controls, chapter/title/end screens and credits. | UIHD 0.3 source audit selects 738 assets across every audited default UI raster family, including all animation/palette states and both save-card formats. [Complete denominator](docs/definitive/UIHD_COVERAGE.md). Component checks pass; all-screen visual acceptance, combined gameplay and physical Deck measurements remain pending. |
| **[FxHD](integrations/fxhd/README.md)** | Field and battle effects, including spells, enemy attacks and transformations, preserving timing, blending and transparency. | Merged: six selected 2× field textures—dust, both footprints, white smoke, brown smoke cloud and save-point glow—using about 80 KiB before driver overhead. Full retail source coverage is implemented in PR #19 under review; it is not yet merged or in the public installer. |

EnvHD's selected restoration is a source-faithful baseline, with native scene acceptance still pending. The terrain branch selects 292 reviewed masters for 364 static material bindings; one uniform source stays native and one wrapped material is held. Animated water keeps its original path. The newer field census resolves 5,528 distinct visible images across 650 configurations, with 5,162 nonuniform unowned restoration jobs. Field runtime integration, props and animated surfaces remain open. [Merged environment coverage](docs/definitive/ENVHD_STATUS.md) · [Full environment worklist under review](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/17).

CharHD's expanded audit found 816 model/texture pairs across 6,945 bindings. Its initial queue includes 678 textured candidates and 13 untextured entries; animated data, unsupported mappings, unbound routes and source failures still need work. The complete NPC and scripted-variant census is unresolved, and none of these candidates has final AA artwork or native/Deck acceptance.

CharHD's source-bound battle adapter preserves palette coverage and native effects, supports explicit surface/roughness assignments and works with ModelsHD's separate geometry route. All 38 headless combinations of the 19 party forms with ModelsHD on/off have passed their mesh checks. Batch restoration alone remains too subtle for the final target; Dart's armor is the first focused reconstruction. Face, hair and remaining costume work still need deliberate treatment and native review.

UIHD now reaches the full audited default interface, including both new and matching existing save-card portraits. Browsing saves does not rewrite them; existing custom portraits retain their artwork. [Complete interface coverage](docs/definitive/UIHD_COVERAGE.md). It keeps original logical sizes, animation offsets and clipping while loading larger images. Native palette variants load on demand within a 32 MiB artwork residency budget; current-frame resources stay pinned and unused pages can retire, including while paused. Existing smooth fonts and resolution-independent panels remain available.

Palette or VRAM changes fall back to original pixels, and disabling the mod reloads persistent artwork correctly. FxHD likewise keeps original drawing when source pixels or palettes change, and integrates with effect lighting, save-point emission and reduced-flashing settings. These compatibility checks do not establish physical Deck appearance or performance.

HD assets are prepared ahead of time; the Deck does not need to run the authoring tools or neural models. Custom meshes and artwork are public project deliverables, with only selected runtime assets entering the installed mods. Development candidates retain their review status.

FMVHD preserves the source timing and soundtrack content, with bounded buffering, source/checksum checks and fallback when a compatible replacement is unavailable. Its restoration can soften some detail; it is not a recovery of the original studio masters. Existing campaigns use the game's normal mod-selection prompt to enable newly installed mods. [FMV checks and remaining device tests](docs/definitive/FMVHD_VALIDATION.md).

## Engine and presentation improvements

Severed Chains supplies the game port, rendering and audio foundations, input support, disc extraction and modding API. Definitive builds on those systems. The engine on **main** now includes the following improvements for the next consolidated installer:

- **SMAA anti-aliasing:** cleaner silhouettes and diagonal edges through three spatial passes, with dialogue, menus, battle HUD and Addition prompts protected. It uses the original MIT-licensed [SMAA implementation](gfx/shaders/smaa/README.md), with the earlier edge filter available as fallback.
- **Smoother lighting and soft contact shadows** across field maps, battles, the world map and model-based cutscenes, retaining the scene's original light directions and colors.
- **Richer material support:** individual faces can carry surface types and roughness, while compatible HD assets can supply normal and roughness maps. Cloth, skin, leather and metal can respond differently; artists still need to author meaningful material assignments.
- **Artwork-matched lighting support:** environment packs can describe their light direction, color and ambient tone so models fit the painted scene. Profiles reset with scene changes rather than leaking into the next location.
- **Colored effect lights:** up to four nearby emitters from luminous effects and save points illuminate supported opaque geometry. Selective bloom gives luminous effects a soft glow independently of CRT styling.
- **HD texture filtering and gentle sharpening:** mipmaps and capability-checked anisotropic filtering improve eligible HD images. Atlas padding, original palettes and transparency remain part of the compatibility contract.
- **Bounded artwork caching:** repeated PNG loads reuse decoded pixels within a 64 MiB retained cache. Prewarming hooks let artwork mods prepare images before display, and loading measurements help identify remaining stalls.
- **Protected HD interface artwork:** UI atlas draws explicitly retain crisp coverage; source-bound replacements preserve live palette changes, native transparency, clipping and animation coordinates. Atlas-capacity fallback retains registered artwork, and mod reloads cannot resurrect an obsolete selection.
- **Safer graphics handling:** failed shader reloads retain the working program, texture uploads and mipmap updates use the correct bindings, and deleted texture IDs no longer leave stale cached bindings.
- **Stable timing when changing speed:** the shared scheduler resets its deadline when the callback rate changes, preventing an old movie or menu deadline from delaying faster gameplay. Script/model animation cadence and audio clocks have separate regression checks. [Engine pacing](docs/definitive/ENGINE_PACING.md).
- **Correct cinematic speed:** movies keep their own timing even when gameplay is sped up. Every cinematic frame reaches the renderer, including when playback starts on a skipped gameplay frame; normal gameplay settings return afterward. Streaming audio no longer counts unplayed buffers during empty starts or underruns, preventing the accelerated opening reported in the public build. [Playback fix and regression evidence](docs/definitive/CINEMATIC_TIMING.md).

### Default assets: detail, lighting and first visits

These additions are also merged into **main**:

- **Default surface detail** adds a subtle shared normal/roughness finish to supported original and HD models. Original palette animation and transparency stay live, and authored maps take precedence. This is a conservative baseline, not hand-painted skin, fabric or metal masks for every asset.
- **Automatic scene-lighting profiles** follow the game's original lights and scripted changes when an artist profile is absent. They preserve the original lighting as the dominant input; they do not infer lighting from background images.
- **First-visit preparation** moves native field-background decoding onto a bounded CPU worker during loading. The 43 bundled Skurfa scene mappings also receive preload hints, prioritizing images that fit the cache. Failed or oversized preparation retains the original loading route.

These features use the existing **Graphics** settings, with restrained defaults and individual controls. SMAA and the other screen effects use no temporal accumulation, generated frames or extra input buffering. Local GPU and loading probes pass, but physical Deck frame times, memory use and battery impact remain to be measured. [Rendering details and verification](docs/definitive/RENDERING_LIGHTING.md).

### Faithful audio

The merged audio improvements preserve decoded prerecorded XA clips as PCM WAV, avoiding the extra lossy conversion used previously. Playback queues only valid decoded samples, lets clip endings finish, and maintains more accurate timing during underflow or device recovery.

The original score, voices, effects, volume behavior and music settings remain intact. The combined installer will refresh older recordings locally from installed discs; there is no replacement soundtrack download. All **41 recordings** in the tested US set decoded successfully, adding about **156 MiB** to local game data. Physical Deck listening, Bluetooth/device switching and suspend/resume checks remain open. [Audio validation](docs/definitive/AUDIO_VALIDATION.md).

## Gameplay comfort, on your terms

Choose **Definitive** or **Faithful** when creating a campaign. Definitive enables conveniences such as saving anywhere, battle autosaves, running by default, faster text, shorter transitions, enemy HP bars and turn-order information. Faithful selects retail-oriented settings. Both keep normal Additions and their ordinary timing windows, default to 1× rewards, and omit difficulty overhauls.

Artwork, rendering, audio and controller settings remain independent of the campaign preset. Faithful is a settings profile rather than a claim of bit-perfect retail emulation. Changing a preset later cannot undo progression already earned. [Full preset comparison](docs/definitive/PRESETS.md).

The current installer includes binding-aware **Equip, Sort, Unequip and Back** equipment hints inspired by [Quality of Life+](https://github.com/FrancisDionne/Severed-Chains/releases/tag/experimental).

The broader [native-settings implementation](docs/definitive/QOL_SETTINGS.md) is now merged into main for the consolidated installer:

- **Inventory and shops:** contextual hints follow remapped bindings; equipment can be filtered and sorted; quantity buying/selling respects affordability, capacity and protected items.
- **Campaign rewards:** independent enemy XP and gold multipliers range from 0× to 10×, defaulting to **1×**. A battle keeps the values it started with, and item drops retain their existing rules.
- **Addition timing feedback:** optional Early, Late, Wrong Button, Good and Perfect hints, **off by default**. Feedback observes the existing hit result without changing timing windows, damage or mastery.

These are features in the existing settings, with no separate mod to manage. Both campaign presets retain 1× rewards and feedback off. The reward-control implementation is independently authored around current APIs, inspired by Battle Rewards; its older mod is not bundled. **Dragoon Modifier** remains an unbundled overhaul candidate. [Community integration notes](docs/definitive/INTEGRATION_ALPHA.md).

## Build from source

Players should use the **[Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop)**. For development, install **JDK 25** and use the checked-in **Gradle 9.1.0 wrapper**. Compilation requires no game discs.

```sh
git clone --recurse-submodules https://github.com/gideonidoru/legend-of-dragoon-definitive.git
cd legend-of-dragoon-definitive
./gradlew --no-daemon --console=plain clean build
```

To package the Linux x64 / Steam Deck target:

```sh
./gradlew --no-daemon --console=plain clean build definitivePackage portableInstaller -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

For an existing checkout, initialize integrations with `git submodule update --init --recursive`. Ordinary builds never launch the game. [Setup and build instructions](docs/definitive/SETUP.md) · [Test controls](docs/definitive/BUILD_CONTROLS.md).

## Credits, contributions and licensing

Definitive depends on the work of the **Severed Chains maintainers and Legend of Dragoon Modding community**, and on creators such as **Skurfa** and **Quality of Life+**. We preserve upstream Git history, [credits](CREDITS), [source notices](credits.txt) and the unchanged [AGPL v3 engine license](LICENSE). Mods and artwork retain their own notices; the engine license does not confer rights to the original game's assets. This project is not affiliated with or endorsed by Sony or the original rights holders.

Contributions should explain the player benefit, existing upstream/mod overlap, compatibility and validation. Small modules with clear ownership make it easier to keep incorporating upstream improvements. [Upstream strategy](docs/definitive/UPSTREAM.md) · [Architecture](docs/definitive/ARCHITECTURE.md) · [Backlog](docs/definitive/BACKLOG.md).

Custom Definitive mod code, ModelsHD meshes and HD artwork upgrades are public and versioned, and intended for the normal downloadable installation. FMVHD's enhanced footage is published under the documented project policy exception; its derived footage remains distinct from the code license. **Game discs, full original extraction, saves, credentials, private diagnostics and local runtimes are not included.** [Asset policy](docs/definitive/ASSET_POLICY.md) · [FMVHD provenance and notices](integrations/fmvhd/README.md) · [Original upstream README](docs/upstream/README.md).
