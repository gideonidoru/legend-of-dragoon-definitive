# Legend of Dragoon: Definitive

**Rediscover Endiness. Built for Steam Deck.**

## [⬇ Download the Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop)

The journey you remember, with richer characters, restored artwork and a new level of detail. One guided installation gets everything ready.

[Release notes](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest) · [Portable installer ZIP](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Definitive-Installer.zip) · [Installation help](docs/definitive/INSTALLER.md)

<a href="https://ko-fi.com/gideonidoru"><img src="https://storage.ko-fi.com/cdn/kofi5.png" alt="Buy me a coffee on Ko-fi" height="36" /></a>

**Legend of Dragoon: Definitive** brings a visual restoration and a complete Steam Deck setup to [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains), the native PC port of *The Legend of Dragoon*. From Dart’s crimson armor to the painted forests of Endiness, its custom models, HD textures and engine improvements give familiar places and characters more room to shine. The story, original music and rhythm of Addition combat remain at its heart.

Bring your **four US game-disc images**. The installer takes care of the engine, Java, artwork and mods.

> **Development alpha.** Character and environment restoration are still in progress. Physical Steam Deck gameplay and performance validation are ongoing.

## Endiness, in greater detail

Painted towns, forests and interiors carry the atmosphere of *The Legend of Dragoon*. Definitive restores their detail for a modern screen, from distant landscapes to the foliage and architecture around your party.

![Matched forest field scene with Definitive visual enhancements disabled on the left and enabled on the right](docs/definitive/images/comparisons/field-visuals-off-on.jpg)

*Forest cliff · Visual enhancements off → on.*

The environment library includes **4,917 field images**, with separate backgrounds and foregrounds, plus battle panoramas, world-map artwork and selected terrain and scenery textures. Hand-painted replacements add detail to woodwork, fabrics, plants and buildings across 31 field images and twelve battle motifs. Foreground layers keep their original placement and depth, so your party still moves through the scene as intended.

![Matched battle view with all Definitive visual enhancements disabled on the left and enabled on the right](docs/definitive/images/comparisons/battle-visuals-off-on.jpg)

*Battle scene · Visual enhancements off → on.*

**About the comparisons:** all four pairs on this page were captured in the native Mac game renderer at 1280 × 800, with matching cameras, poses and normal character scale. HD modules and visual rendering options are off on the left and on on the right. [Capture details](docs/definitive/images/comparisons/README.md#matched-in-engine-visual-comparisons).

## Familiar heroes. Finer detail.

### Dart

A reconstructed face, sculpted hair locks and a **2048 × 2048 painted material** bring Dart into focus. Painted costume details complement his crimson armor, while his original combat and cutscene animations carry every attack and expression.

![Dart before and after in matching native game-renderer views](docs/definitive/images/comparisons/battle-detail-off-on.jpg)

### Meru

Meru’s hammer, costume and distinctive silhouette remain instantly recognizable. Refined geometry and smoother lighting soften angular surfaces while preserving the movement and personality of her original design.

![Meru before and after in matching native game-renderer views](docs/definitive/images/comparisons/meru-native-off-on.jpg)

Geometry improvements cover eligible surfaces on **all nine party field models and all 19 party battle forms**, including Dragoons, as well as supported enemies, NPCs and scenery. Character textures and painted costume details add another layer of restoration. Dart’s reconstructed head is the most extensive character upgrade; the wider party benefits from surface refinement and artwork improvements.

[Character restoration](docs/definitive/CHARACTER_REMASTER.md) · [Model coverage](docs/definitive/MODELS_HD_WORLD_PASS.md)

## Magic, menus and memorable moments

**Sharper magic.** FxHD adds **2,160 unique 2× detail maps** to spells, transformations, attacks and animated effects. Dragoon magic, boss attacks, save points and title flames gain detail while retaining their original color changes, scrolling and transparency.

**A clearer interface.** Restored portraits for all nine heroes join sharper menus, dialogue borders, save cards and world-map markers. Battle prompts keep their original size and timing, making the artwork clearer without changing the precision Additions demand. Smooth fonts are available in Graphics settings.

**All 18 cinematics, enhanced.** FMVHD presents the films at **1280 × 768**, preserving their original 15 fps motion and soundtrack. Streaming playback keeps memory use bounded, with original-video fallback available.

## Make the adventure yours

Choose **Definitive** for saving anywhere, battle autosaves, running by default, faster text, shorter transitions, enemy HP bars and turn-order information. Choose **Faithful** for settings closer to the original game. Both presets keep normal Addition timing, **1× rewards** and timing feedback off by default.

Individual settings let you sort and filter equipment, buy and sell in quantities, adjust XP and gold rewards, or see button hints that follow your bindings. Optional Addition feedback identifies Early, Late and Wrong Button results without changing hit windows or damage.

Artwork and rendering choices are independent of those campaign presets. **Installed HD modules work with existing campaigns**, and your module choices are remembered. Use the game’s **Mods** menu to choose individual artwork upgrades and **Graphics** to adjust lighting, anti-aliasing and other presentation options. The launcher’s original/HD choice controls Skurfa backgrounds and ModelsHD.

The seven included artwork modules are [Skurfa’s HDR Backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds), [ModelsHD](integrations/modelshd/README.md), [CharHD](integrations/charhd/README.md), [EnvHD](docs/definitive/ENVHD_FULL_COVERAGE.md), [UIHD](docs/definitive/UIHD_PRODUCTION.md), [FxHD](integrations/fxhd/README.md) and [FMVHD](integrations/fmvhd/README.md). Everything is supplied by the installer, including all four EnvHD resource packs.

[Compare presets](docs/definitive/PRESETS.md) · [Gameplay settings](docs/definitive/QOL_SETTINGS.md) · [Artwork controls](docs/definitive/HD_MOD_ACTIVATION.md)

## Start playing on Steam Deck

1. Enter **Desktop Mode** and [download the installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/Install-Definitive.desktop).
2. Open `Install-Definitive.desktop` in **Dolphin**. Allow execution if prompted.
3. Choose an installation folder and supply your **four US game-disc images**, or reuse discs from an existing installation. BIN/raw ISO files and ZIP, RAR or 7z containers are supported.
4. Let setup prepare the game and add the optional Steam shortcut. Launch from Steam or the Definitive launcher.

Custom folders and SD cards are supported. Allow roughly **12 GB free for initial setup**, in addition to source discs already present. Actual space use depends on disc compression; retained updates need additional room. Setup needs an internet connection; your source images stay untouched. Game discs are not included.

![Definitive launcher with Play, artwork choices, version restore and Steam library actions](docs/definitive/images/launcher-linux.png)

*Linux launcher preview.*

Keep the installer for future maintenance: it retrieves and verifies the latest setup each time you open it. If an older installer or Play shortcut stops working, download a fresh copy and select your existing installation.

The launcher offers **Update** and **Restore** to a retained local version. The installer offers **Repair** for missing or damaged files, **Reinstall** for a fresh application workspace, and **Uninstall**. Repair and Reinstall preserve saves, settings, custom mods and imported discs; Uninstall retains personal data, with disc removal available only by your choice. Updates reuse verified unchanged files to reduce downloads.

[Installation and recovery guide](docs/definitive/INSTALLER.md)

## Technical presentation

The artwork is backed by changes to Severed Chains’ renderer, asset loading, audio and engine clocks. Together, they improve how the game looks and handles HD content while preserving the animation, scene layering and combat timing that make it feel familiar.

### Light, materials and image clarity

| Engine improvement | What you see |
| --- | --- |
| **SMAA 1x High** | Three-pass spatial anti-aliasing cleans up silhouettes and diagonals, with dedicated interface coverage protecting dialogue, menus and Addition prompts. |
| **Per-fragment lighting** | Light blends smoothly across supported model surfaces, following the scene’s original light directions and colors. |
| **Surface materials** | Cloth, skin, leather and metal respond differently to light. Per-face roughness and authored normal/roughness maps give supported surfaces more definition. |
| **Scene-aware lighting** | Lighting profiles help characters sit naturally within painted environments; native lights and scripted changes remain the fallback. |
| **Soft contact shadows** | Feathered shadows ground field and battle actors and the world-map traveler, retaining their original attachment and placement. |
| **Dynamic effect lights and selective bloom** | Up to four nearby luminous effects illuminate supported opaque geometry. Emissive content can glow; reduced-flashing settings suppress these added contributions. |
| **HD filtering and sharpening** | Mipmaps, supported anisotropic filtering up to 4× and bounded contrast-adaptive sharpening improve texture clarity. Palette textures, transparency masks and interface sheets retain the sampling they need. |

### HD detail that follows the game

Replacements stay tied to the original models and texture sources. Dart’s head uses **5,748 triangles**, integrated with his materials across twelve field and battle part packs. Animation hierarchies and script-facing model indices remain intact. Environment layers retain their movement, translucency and depth; effects use the current palette so color cycling stays animated. Interface artwork preserves native clipping and coordinates.

HD content also has explicit resource limits: a shared **64 MiB decoded-artwork cache**, a **16 MiB persistent GPU companion for effects**, and a **32 MiB source/restoration GPU budget for UIHD**. These are component limits, rather than a total game-memory figure. Background workers prepare eligible images during loading, obsolete work is cancelled on scene changes, and GPU uploads and cleanup stay on the renderer thread. Invalid or mismatched replacements fall back to original artwork.

Working shaders survive a failed reload, and obsolete artwork requests cannot overwrite a newer mod selection. Depth-buffer clearing restores depth writes in both OpenGL and OpenGL ES, preventing earlier draw state from leaving stale depth in later rendering.

### Sound and timing that stay together

Prerecorded XA audio is preserved as PCM after decoding, avoiding another lossy encode. Playback counts audio that actually reaches the output, protecting clip endings and keeping films synchronized through empty starts and underruns. The default output follows the operating system, with explicit device selection and fallback when an output becomes unavailable.

Gameplay runs on independent, monotonic fixed steps, while audio and hardware/minigame timers keep their own rates. Catch-up after a stall is bounded. Films follow played audio, bypass gameplay frame skipping and restore input and presentation state when they end. Losing an audio device cannot fast-forward a film by counting sound that never played. **Addition timing windows remain unchanged.**

### Verified installation and recovery

Downloads are checksum-verified before activation, so an incomplete transfer cannot become the active installation. ZIP64 and file-based delivery support the larger artwork library. Recovery can handle interrupted launches and damaged launch records while retaining personal data; artwork preferences are saved atomically so a failed write can be retried or cancelled.

For implementation details: [Rendering](docs/definitive/RENDERING_LIGHTING.md) · [Engine pacing](docs/definitive/ENGINE_PACING.md) · [Cinematics](docs/definitive/CINEMATIC_TIMING.md) · [Audio](docs/definitive/AUDIO_VALIDATION.md) · [Delivery](docs/definitive/FILE_DELIVERY.md)

## Built with the community

Definitive builds on the work of the **Severed Chains maintainers and Legend of Dragoon Modding community**, **Skurfa**, and other community creators, with inspiration from **Quality of Life+**. Their attribution and license notices are preserved. [Credits](CREDITS) · [Source notices](credits.txt)

This is an unofficial project, unaffiliated with Sony or the original rights holders. The engine uses [AGPL v3](LICENSE); mods and artwork retain their own notices. The code license does not grant rights to the original game’s assets. [Asset policy](docs/definitive/ASSET_POLICY.md) · [FMVHD notices](integrations/fmvhd/README.md)

Want to contribute? [Build instructions](docs/definitive/SETUP.md) · [Architecture](docs/definitive/ARCHITECTURE.md) · [Upstream approach](docs/definitive/UPSTREAM.md) · [Original Severed Chains README](docs/upstream/README.md)
