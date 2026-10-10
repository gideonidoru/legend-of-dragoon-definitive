# Legend of Dragoon: Definitive

## [⬇ Download the Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop)

**Download in Steam Deck Desktop Mode, open the installer, and bring your four US game discs.** Setup takes care of the engine, Java, HD mods and enhanced cinematics. You don't need to assemble a mod list or generate artwork yourself.

[Release notes](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest) · [Portable installer ZIP](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Definitive-Installer.zip) · [Installation help](docs/definitive/INSTALLER.md)

<a href="https://ko-fi.com/gideonidoru"><img src="https://storage.ko-fi.com/cdn/kofi5.png" alt="Buy me a coffee on Ko-fi" height="36" /></a>

If you'd like to support Definitive, [buy me a coffee](https://ko-fi.com/gideonidoru).

Definitive brings *The Legend of Dragoon* to Steam Deck with clearer artwork, smoother models, improved lighting and a few comforts for returning players. It's built on [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains), with the original characters, painted world, music and combat at the heart of it.

We're aiming for a polished modern AA presentation that keeps the game's retro character, across the whole campaign. That means giving faces, costumes, environments, menus and effects the same care, while preserving the original designs and animations.

**This is an unofficial community alpha.** The visual remaster is still in progress. The download currently provides the earlier all-HD repair release; some improvements described below are prepared for the next installer. Check the [release notes](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest) for what's included in your download. Gameplay and performance testing on Steam Deck are ongoing.

## Install and play

1. Switch to **Desktop Mode** and download `Install-Definitive.desktop` using the button above.
2. Open it in **Dolphin** and allow execution if prompted.
3. Choose an installation folder and supply your **four US game-disc images**, or reuse discs from an existing installation. BIN/raw ISO files and ZIP, RAR or 7z containers are supported.
4. Let setup prepare the game. Add the optional Steam shortcut, then launch from Steam or choose **Play** in the Definitive launcher.

The default folder is `/home/deck/Games/Legend-of-Dragoon-Definitive`. Custom folders and SD cards are supported. Allow room for Java, the roughly **1.7 GB game download**, your discs and prepared game data. Game discs are not included; your source images stay untouched.

The launcher handles updates and lets you restore a previous installed version. **Repair** downloads missing or changed application files. **Reinstall** replaces the full application while keeping your saves, settings, custom mods and imported discs. Setup shows both overall and current-file progress. [Help with installation, updates or recovery](docs/definitive/INSTALLER.md).

![Definitive launcher with Play, artwork choices, version restore and Steam library actions](docs/definitive/images/launcher-linux.png)

*Linux launcher preview.*

## A familiar game, with a clearer picture

### Smoother models and restored textures

ModelsHD softens angular surfaces across supported party, enemy, NPC and scenery models while keeping the original animation. Character texture work restores painted detail on clothing, weapons and armor, with more deliberate reconstruction where a simple upscale falls short. Dart's crimson armor is an early example; individual faces, hair and costumes still need further work.

Skurfa's HD backgrounds set the direction for the environments. EnvHD extends that work with restored battle panoramas, location artwork and the parchment world map. Terrain, battle surfaces and the remaining field scenes are also being restored, with foreground layers kept separate so characters still pass behind the right walls, trees and doors.

### Sharper spells, attacks and effects

The latest **FxHD 1.0** goes well beyond dust and footprints. It restores textures across Dragoon spells and transformations, item magic, enemy and boss attacks, battle cutscenes, save points, world-map atmosphere and title flames. Animated colors, scrolling, blending and transparency keep their original behavior.

The full-catalog version is prepared for the next installer; the current public download still has the earlier field-effect pack. Procedural effects such as lightning and weapon trails continue to use the engine's rendering, and distortion effects still draw from the live scene. [More about FxHD](integrations/fxhd/README.md).

### Clear menus and portraits

UIHD restores all nine party portraits and the default interface artwork, including battle prompts, inventory and shop menus, dialogue borders, save-card portraits, world-map markers, chapter cards and credits. Original sizes and timing stay intact, keeping combat cues readable. Existing smooth fonts remain available in Graphics settings.

### Lighting, edges and cinematics

- **Cleaner edges:** SMAA reduces jagged silhouettes while keeping menus, dialogue and Addition prompts crisp.
- **More natural surfaces:** smoother shading, material detail and soft contact shadows help models sit in the painted world. Cloth, skin, leather and metal can have different finishes.
- **Light from the scene:** supported luminous effects and save points cast colored light, with restrained bloom and scene-aware lighting.
- **Clearer cinematics:** all 18 films have enhanced 1280×768 versions, keeping the original 15 fps motion and soundtrack. Playback fixes keep movies at their intended speed when gameplay is accelerated.
- **Faithful audio:** prerecorded clips retain decoded PCM quality, with improvements to clip endings, playback timing and recovery. The original score, voices and effects remain.
- **Clearer textures in motion:** improved filtering and gentle sharpening help supported HD artwork stay readable as the camera moves.
- **Prepared artwork:** texture restoration happens before download. Caching and background preparation help avoid repeated loading work; the Deck doesn't run the authoring tools during play.

Visual options live in **Graphics** and can be adjusted independently of gameplay settings. The restoration targets the original art direction; it cannot recover lost studio masters or make every tiny detail authentic.

## Before and after

These are matched offline previews. Each pair uses the same pose, camera and lighting; smoothing comparisons change geometry, and texture comparisons change artwork. They show individual improvements rather than a finished remaster or a Steam Deck gameplay capture.

### Dart — model smoothing

![Dart before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/dart-before-after.png)

The earlier ModelsHD 0.3 pass rounds Dart's armor and limbs while keeping his silhouette. The lower panels make the shape changes easier to see.

### Dart — texture restoration

![Dart texture before and after: original artwork and authored armor v1 on identical geometry in two matching poses](docs/definitive/images/comparisons/dart-textures-before-after.png)

Reworked crimson armor panels over the restored texture atlas. This is the selected development armor pilot, on unchanged original geometry.

### Meru — model smoothing

![Meru before and after ModelsHD 0.3, with matching textured and untextured views](docs/definitive/images/comparisons/meru-before-after.png)

The earlier ModelsHD 0.3 pass smooths eligible surfaces on Meru's limbs and clothing.

### Meru — texture restoration

![Meru texture before and after: original artwork and 4x restoration candidate on identical geometry in two matching poses](docs/definitive/images/comparisons/meru-textures-before-after.png)

The 4× restoration makes the hammer motifs and clothing patterns easier to read. This is a development candidate, not artwork currently enabled in the installed mod.

ModelsHD 0.4 uses a more conservative smoothing pass across the wider model roster. The geometry images above show version 0.3. [Image versions and comparison notes](docs/definitive/images/comparisons/README.md).

## Play your way

Choose **Definitive** or **Faithful** when starting a campaign. Definitive adds saving anywhere, battle autosaves, running by default, faster text, shorter transitions, enemy HP bars and turn-order information. Faithful uses settings closer to the original game.

Both presets keep normal Additions and their timing windows, with **1× rewards** and timing feedback off by default. Artwork, audio, rendering and controller settings are independent of the preset. [Compare the presets](docs/definitive/PRESETS.md).

The expanded convenience settings add equipment filtering and sorting, quantity buying and selling, and action hints that follow your button bindings. Optional XP and gold multipliers run from 0× to 10×. Addition feedback can tell you Early, Late, Wrong Button, Good or Perfect without changing the hit windows or damage. These options are part of the game's settings; difficulty overhauls are not bundled. [Gameplay and menu options](docs/definitive/QOL_SETTINGS.md).

## The HD mods

The normal installation includes seven modules. You can choose them individually in the game's **Mods** menu; the launcher also offers an original/HD artwork preference. Existing campaigns may ask you to enable newly installed mods.

| Mod | What it brings |
| --- | --- |
| **[Skurfa's HDR Backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds)** | Remastered field backgrounds and foreground layers, preserved with the original creator's attribution. |
| **[ModelsHD](integrations/modelshd/README.md)** | Smoother supported character, enemy, NPC and scenery models with original animations. |
| **[CharHD](docs/definitive/CHARHD_PRODUCTION.md)** | Restored character textures and authored costume detail. |
| **[EnvHD](docs/definitive/ENVHD_STATUS.md)** | Battle panoramas, world-map artwork and the ongoing restoration of missing environments. |
| **[UIHD](docs/definitive/UIHD_PRODUCTION.md)** | Sharper portraits, menus, combat cues and interface artwork. |
| **[FxHD](integrations/fxhd/README.md)** | Restored spell, attack, transformation and animated effect textures. |
| **[FMVHD](integrations/fmvhd/README.md)** | Enhanced versions of all 18 cinematics, with original-video fallback. |

Including every module doesn't mean every part of the remaster is finished. Character rebuilding across the party, NPCs and enemies continues, as does work on field scenes, terrain, battle materials, props and animated surfaces. Generated texture candidates are not automatically installed. The goal remains the whole campaign; current artwork still needs in-game review and Steam Deck testing. [Visual direction](docs/definitive/CHARACTER_REMASTER.md) · [Environment coverage](docs/definitive/ENVHD_STATUS.md) · [Character coverage](docs/definitive/CHARHD_PRODUCTION.md).

## Credits and contributing

Definitive builds on the work of the **Severed Chains maintainers and Legend of Dragoon Modding community**, **Skurfa**, and other community creators, including the inspiration from **Quality of Life+**. Their attribution and license notices are preserved. [Credits](CREDITS) · [Source notices](credits.txt).

This is an unofficial project, unaffiliated with Sony or the original rights holders. The engine uses the [AGPL v3 license](LICENSE); mods and artwork retain their own notices. Game discs are not distributed. Custom Definitive code, meshes and artwork are public project work; the code license does not grant rights to the original game's assets. [Asset policy](docs/definitive/ASSET_POLICY.md) · [FMVHD notices](integrations/fmvhd/README.md).

For development, use **JDK 25** and the checked-in **Gradle 9.1.0 wrapper**. [Build instructions](docs/definitive/SETUP.md) · [Rendering details](docs/definitive/RENDERING_LIGHTING.md) · [Architecture](docs/definitive/ARCHITECTURE.md) · [Upstream approach](docs/definitive/UPSTREAM.md) · [Original upstream README](docs/upstream/README.md).
