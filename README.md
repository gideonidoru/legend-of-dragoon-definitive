# Legend of Dragoon: Definitive

## [⬇ Download the Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-10-fmvhd/Install-Definitive.desktop)

**Start here in Steam Deck Desktop Mode.** The guided installer downloads the engine, Java, bundled HD mods and enhanced cinematics, then prepares your own game discs. No separate model transfer or asset-generation setup is needed.

[Release notes](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/definitive-alpha-2026-10-10-fmvhd) · [Portable installer ZIP](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-10-fmvhd/Definitive-Installer.zip) · [Installation and recovery help](docs/definitive/INSTALLER.md)

Definitive is a Steam Deck-first modernization of *The Legend of Dragoon*, built on [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains). We want the game to feel as good to return to today as it did the first time: familiar characters and painted environments, clearer visuals, comfortable controls, and an installation you can get through without assembling a collection of tools and mods yourself.

**This is an unofficial community alpha.** The installer and launcher have been confirmed working on a physical Steam Deck. The newer models, cinematics and rendering changes still need device testing; the complete visual remaster is in progress.

## Install and play

1. Switch your Steam Deck to **Desktop Mode** and download the installer above. Use a fresh copy if you tried an earlier release; older shortcuts can point to older builds.
2. Open `Install-Definitive.desktop` in **Dolphin**. Allow execution if KDE asks.
3. Choose your installation folder and supply your **four US game-disc images**, or reuse discs from an existing installation. BIN/raw ISO files and ZIP, RAR or 7z containers are supported.
4. Let setup download and prepare the game. Add the optional Steam library shortcut, then launch from Steam or select **Play** in the Definitive launcher.

The default folder is `/home/deck/Games/Legend-of-Dragoon-Definitive`; custom and SD-card locations are supported. The initial download is a small shortcut. Setup then downloads the roughly 13 MB installer interface, Java and a **roughly 1.7 GB game package**, in addition to preparing your discs. Game discs are not included, and your source images stay untouched.

The launcher checks for updates and can restore a previous version. **Reinstall** retains saves, settings, mods and ISOs. **Uninstall** removes ISOs only if you explicitly choose that option. For an older custom installation, select its folder once so the installer can remember it. [More about installation, updates and recovery](docs/definitive/INSTALLER.md).

![Definitive launcher with Play, artwork choices, version restore and Steam library actions](docs/definitive/images/launcher-linux.png)

*Linux launcher preview. This image shows the interface; it is not a Steam Deck gameplay capture.*

## What we're aiming for

The visual goal is a polished modern AA game with a deliberate retro aesthetic. That means keeping the original compositions, silhouettes, costumes and atmosphere while improving the things that stand out on a modern screen: angular faces, blocky limbs, uneven material detail, jagged edges and low-resolution cinematics.

There are several parts to making that work together:

- **A consistent world.** Build around Skurfa's HD backgrounds, then fill missing environments, battle panoramas, world-map artwork and props. Keep foreground masks and scene placement correct so characters still walk behind the right trees, walls and doors.
- **Recognizable, better-shaped characters.** Refine the broad model roster efficiently, then give faces, hands, hair, costumes and awkward joints the individual attention they need. Preserve the original animations and transformations.
- **Lighting that belongs to the scene.** Give models smoother shading, restrained material highlights, soft contact shadows and light from luminous effects, while respecting the painted backgrounds' own lighting.
- **Clear handheld presentation.** Keep dialogue, menus and timing prompts crisp. Improve the surrounding image without obscuring the cues needed for Additions.
- **An easy return to the game.** Put installation, updates, recovery and controller-friendly navigation in one place. Keep convenience settings and visual choices independent.
- **Quality that holds up on Deck.** Choose defaults through comparisons in motion and measurements of frame times, memory, loading and power. A successful build is only one part of that evidence.

Our approach is incremental: reuse good community work, add separate mods, and extend the engine where those mods need a small shared feature. Skurfa's existing art stays intact. The next model priorities are party field/story models, prominent NPCs, bosses and dragons, then common enemy families and remaining visible props. [Visual direction](docs/definitive/CHARACTER_REMASTER.md) · [Model roadmap](docs/definitive/MODELS_HD_ROADMAP.md) · [Project plan](docs/definitive/PROJECT_PLAN.md).

## ModelsHD: the broad first pass

ModelsHD 0.4 inspects all **1,343 supported canonical model containers**. **1,004 receive some smoothing; 339 keep their originals** because their topology is unsafe or refinement adds no useful detail. The complete mod bundles **8,777 unique custom part replacements**, including the nine confirmed party field models and all 19 party battle forms, with eligible NPCs, bosses, enemies, scenery and world-map resources. Original animation/script data and active texture addressing remain intact.

This is a modest first geometry pass. Individual faces, hair, hands and costumes still need deliberate reconstruction. [Build and use ModelsHD](integrations/modelshd/README.md) · [Exact coverage and verification](docs/definitive/MODELS_HD_WORLD_PASS.md) · [Model catalog](docs/definitive/MODEL_CATALOG.md).

## Before and after

These comparisons document the **earlier ModelsHD 0.3 party battle pass**. Version 0.4 uses a more conservative crease threshold across the full supported catalog; these images do not preview that new recipe. Both sides use the same textures, pose, camera, scale and lighting, so the visible change comes from geometry. The lower panels remove textures to make the smoother surfaces easier to see.

### Dart

![Dart before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/dart-before-after.png)

Dart's battle mesh goes from **870 to 2,946 triangles**. The first pass rounds eligible surfaces while retaining the recognizable armor, sword and silhouette.

### Meru

![Meru before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/meru-before-after.png)

Meru's battle mesh goes from **806 to 2,687 triangles**. The untextured view makes the changes to limbs and clothing more apparent than the original textures do.

**These are offline model previews, not gameplay screenshots.** This is a modest first refinement, not the finished character remaster. Clearer faces, better hands, stronger costume shapes and natural joints still need deliberate remodeling. [Comparison notes](docs/definitive/images/comparisons/README.md).

## Mods and artwork

The current installer bundles **Skurfa, ModelsHD and FMVHD**. Four additional artwork modules are being integrated on the [HD artwork branch](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/1); their latest production work is not yet part of the installer linked above. Each mod has its own identity, and activation belongs in Severed Chains' existing mod manager.

| Mod | What it contributes | Current coverage |
| --- | --- | --- |
| **[Skurfa's HDR Backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds)** | Individually remastered field backgrounds and foreground layers; the foundation for our environment style. | Bundled pinned v1.1.0 integration: 38 distinct scene packs, with aliases reusing existing artwork. This is selected scene coverage, not every field in the game. |
| **[ModelsHD](integrations/modelshd/README.md)** | Custom geometry that retains original animation parts and active texture/material bindings. | Bundled v0.3: all 19 party battle forms—nine normal, nine Dragoon and Divine Dart. Field and world-map replacements remain unfinished. |
| **[FMVHD](integrations/fmvhd/README.md)** | Preprocessed, enhanced cinematics with streamed playback and original-video fallback. | Bundled: all 18 films at 1280×768, original 15 fps cadence and 5:3 proportions. No generated motion frames. |
| **EnvHD** | Missing environment art: battle skies and surfaces, world-map scenery, field scenes and props. | Development branch: two battle-material pilots and seven reviewed panorama selections. Native scene acceptance and broad coverage remain open. Skurfa-owned art is protected from duplicate generation. |
| **CharHD** | Character texture replacements, coordinated with ModelsHD's geometry and material identities. | Development adapter exists; no new character-art payload yet. It is separate from ModelsHD. |
| **UIHD** | Interface artwork, icons and later portrait/lettering work. | Development pilot: five item icons. Whole-interface coverage remains ahead. |
| **FxHD** | Effect textures and particles, with their original transparency and animation behavior preserved. | Development pilot: field dust. Spell and transformation artwork remains original. |

HD assets are prepared ahead of time; the Deck does not need to run the authoring tools or neural models. Custom meshes and artwork are public project deliverables, with only selected runtime assets entering the installed mods. Development candidates retain their review status.

FMVHD preserves the source timing and soundtrack content, with bounded buffering, source/checksum checks and fallback when a compatible replacement is unavailable. Its restoration can soften some detail; it is not a recovery of the original studio masters. Existing campaigns use the game's normal mod-selection prompt to enable newly installed mods. [FMV checks and remaining device tests](docs/definitive/FMVHD_VALIDATION.md).

## Engine and presentation improvements

Severed Chains supplies the native game port, rendering and audio foundations, input support, disc extraction and modding API. Definitive builds on those systems. Our current engine additions include:

- **Smoother model lighting and soft contact shadows** across field maps, battles, the world map and model-based cutscenes. Models retain the scene's existing light directions and colors.
- **Material-aware shading** for authored cloth, skin and metal tags, with a restrained matte response when tags are absent. Richer per-face material authoring remains future work.
- **Bounded colored effect lights:** up to four nearby emitters from luminous effects and save points can illuminate supported opaque geometry.
- **HD texture filtering** with mipmaps and capability-checked anisotropic filtering. Texture atlases need safe padding before this is applied, preserving palette and transparency behavior.
- **Edge smoothing, selective bloom and gentle adaptive sharpening**, with protected dialogue, menus, battle HUD and Addition prompts. Bloom works independently of CRT styling.
- **Safer graphics resource handling:** failed shader reloads retain the working program, and texture uploads/mipmap updates use the correct bindings.
- **Enhanced cinematic playback** with bounded streaming, cancellation, synchronization and preserved game transitions.

These controls live in **Graphics**, with restrained defaults and individual settings. The effects use spatial processing without temporal accumulation or extra input buffering. Physical Deck frame times, memory use and battery impact remain to be measured. [Rendering details and verification](docs/definitive/RENDERING_LIGHTING.md).

## Gameplay comfort, on your terms

Choose **Definitive** or **Faithful** when creating a campaign. Definitive enables conveniences such as saving anywhere, battle autosaves, running by default, faster text, shorter transitions, enemy HP bars and turn-order information. Faithful selects retail-oriented settings. Both keep normal Additions and their ordinary timing windows; neither bundles a reward multiplier or difficulty overhaul.

Artwork, rendering, audio and controller settings remain independent of the campaign preset. Faithful is a settings profile rather than a claim of bit-perfect retail emulation. Changing a preset later cannot undo progression already earned. [Full preset comparison](docs/definitive/PRESETS.md).

We've also adapted binding-aware **Equip, Sort, Unequip and Back** equipment hints from [Quality of Life+](https://github.com/FrancisDionne/Severed-Chains/releases/tag/experimental), using current engine APIs. Further inventory workflows and Addition training remain planned. **Dragoon Modifier** and **Battle Rewards** are evaluated candidates, not installed features. [Community integration notes](docs/definitive/INTEGRATION_ALPHA.md).

## Where development stands

**October 10, 2026:** the published alpha combines the guided installer, Skurfa backgrounds, the 19-form ModelsHD pass, all 18 FMVHD cinematics and the current rendering foundation. Linux and macOS package builds, headless delivery/recovery checks, model-loader checks and windowless GPU probes provide development evidence. The earlier installer/launcher was confirmed working by the owner on Deck.

The next proof is combined gameplay on the hardware: representative HD scenes, model transformations, foreground masking, busy effects, cinematic audio/skip/return behavior, controller reconnect, suspend/resume, and update/restore. A four-disc playthrough and measured Deck performance are still outstanding. We haven't reached the final AA visual target.

The model catalog covers **1,324 additional geometry containers**, with loading contexts, identity evidence and reuse leads. That is a planning inventory, not 1,324 finished models or distinct characters. Broader artwork production, further character remodeling, installation hardening and audio-fidelity work are active development tracks. [Model catalog](docs/definitive/MODEL_CATALOG.md) · [Deck test plan](docs/definitive/DECK_TEST_PLAN.md) · [Validation records](docs/definitive/VALIDATION.md).

## Build from source

Players should use the **[Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-10-fmvhd/Install-Definitive.desktop)**. For development, install **JDK 25** and use the checked-in **Gradle 9.1.0 wrapper**. Compilation requires no game discs.

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
