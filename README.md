# Legend of Dragoon: Definitive

**A Steam Deck-first modernization of The Legend of Dragoon, built on Severed Chains.**

Definitive aims to make the game easier to install, comfortable to play with a controller, and clearer on a handheld screen, with community HD artwork, neural-assisted character reconstruction, and optional gameplay conveniences. The visual target is a polished modern AA game with an intentional retro style: recognizable characters, clearer faces, richer materials and carefully smoother models. Character reconstruction is planned; the current tools are experiments. Selectable Definitive and Faithful campaign presets keep gameplay choices separate from presentation and accessibility.

This is an unofficial community project in early development. **The guided installer is an alpha. The refined release adds verified installation, clear progress/error screens, automatic Steam restart and recovery checks; physical Deck acceptance remains pending.** Skurfa is source-linked and builds into the package; QoL+ interface ideas are being adapted selectively. Full physical Steam Deck validation remains pending.

## Install on Steam Deck

**[Download the Steam Deck installer](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-09-presentation/Install-Definitive.desktop)**

In Desktop Mode, download and open the installer in Dolphin. Allow execution if KDE asks. It takes care of our build, Java, HD artwork, disc import/preparation and an optional Steam library shortcut. Choose your four US BIN/raw ISO images or ZIP, RAR and 7z containers. Your originals stay untouched; bundled emulators and patches are left out. The default location is `/home/deck/Games/Legend-of-Dragoon-Definitive`.

The entry download is tiny; the portable interface is about 9 MB. Java, the game engine and HD artwork download during setup. The launcher centers on **Play**, checks our releases automatically, and keeps artwork/mod choices and version restore secondary. Touch-sized controls and controller navigation are implemented; physical Deck input and Gaming Mode verification remain pending. **[Installer details and recovery](docs/definitive/INSTALLER.md)** · [Reliability repair and verification](docs/definitive/DELIVERY_RELIABILITY.md) · [Portable installer ZIP](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-09-presentation/Definitive-Installer.zip).

## Where this project comes from

[Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains), developed by the Legend of Dragoon Modding community, is a Java port of the game with a modding API. It supplies Definitive's engine, platform support, game systems, extraction tools, and mod infrastructure. Definitive builds on that work and the wider modding community's contributions.

This repository preserves Severed Chains' Git history, license, credits, and source notices. The initial validated upstream commit is [`fba1543543865e29ee572f479003d9b47158eeb3`](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains/commit/fba1543543865e29ee572f479003d9b47158eeb3), retained on `upstream-baseline`. Development continues here, with the official repository configured as `upstream` and reviewed merges used to incorporate its updates.

Project changes should stay modular: separate mods, settings, installation tools, and small engine hooks when needed. An engine rewrite is outside the project's goals. See the [upstream synchronization strategy](docs/definitive/UPSTREAM.md) and [architecture](docs/definitive/ARCHITECTURE.md).

## Goals

- **Existing HD artwork:** evaluate community packs, retain original-art fallback, and check foreground layers, transitions, memory use, and handheld readability.
- **Modern retro character art:** use neural-assisted reconstruction and deliberate remodeling to improve faces, material detail and selected surfaces while preserving recognizable original designs and animation. Prove one visibly improved character before expanding coverage. Evaluate real-time neural rendering and whole-frame upscaling separately using Deck evidence. [Visual strategy](docs/definitive/NEURAL_VISUAL_STRATEGY.md) · [Scope and acceptance](docs/definitive/TEXTURE_UPSCALING.md).
- **Straightforward installation:** a guided Steam Deck setup with clear disc-import errors, safe upgrades, and recoverable saves/settings.
- **Controller comfort:** complete controller-only navigation, accurate button hints, remapping, reconnect support, and reliable Steam Input behavior.
- **Readable menus:** useful font, contrast, and spacing options at 1280×800 without clipped text or hidden actions.
- **Optional conveniences:** reuse existing settings and suitable mods for text speed, encounters, Addition assistance, and progression options, with explicit campaign choices.
- **Measured performance:** establish real Deck frame-time, loading, memory, and power evidence before rendering improvements or upscaling experiments.

[Definitive and Faithful presets](docs/definitive/PRESETS.md) explicitly choose campaign settings. Faithful retains retail-oriented mechanics; it does not claim bit-perfect retail equivalence. Gameplay changes will remain optional. Disabling a reward or progression modifier later cannot undo rewards already earned.

## Community integrations

Skurfa and selected QoL+ improvements are the primary integration track. Dragoon Modifier and Battle Rewards remain optional candidates and are not installed. Credit and compatibility are recorded per source revision.

| Candidate | Intended role | Evaluation status |
| --- | --- | --- |
| [Skurfa's upscaled HDR backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds) | Optional HD field artwork | Pinned public submodule includes source and artwork in recursive clones; builds its mod JAR into the package and loads successfully on Mac. Limited scene coverage; visual correctness and Deck performance remain to be checked. [Assessment](docs/definitive/SKURFA_RESEARCH.md). |
| [Quality of Life+](https://github.com/FrancisDionne/Severed-Chains/releases/tag/experimental) | Contextual action hints, inventory workflows, and training feedback | First slice adds binding-aware Equip, Sort, Unequip and Back equipment-menu hints using current APIs. More inventory workflows and training feedback are planned. No whole-fork merge or save-format downgrade. [Assessment](docs/definitive/QOL_FORK_RESEARCH.md). |
| [Dragoon Modifier](https://github.com/Legend-of-Dragoon-Modding/sc-dragoon-modifier) | Optional difficulty and balance presets | All 46 Java sources compile against our baseline. It is a broad campaign overhaul with stat-control defects and unresolved licensing; evaluate only as an optional profile. [Assessment](docs/definitive/DRAGOON_MODIFIER_RESEARCH.md). |
| [Battle Rewards Mod](https://github.com/DennytXVII/BattleRewardsMod/releases) | Optional experience and gold reward controls | AGPL source fails a compile check against our baseline; release tags are stale. Evaluate a current-API adaptation and reward overlap before adoption. [Assessment](docs/definitive/BATTLE_REWARDS_RESEARCH.md). |

Skurfa is linked at a fixed commit under `integrations/skurfa`, preserving its original notices. It stays a separate mod JAR, removable for original backgrounds. The other two candidates require save, reward-overlap, license and API checks before adoption. [Integration evidence and next steps](docs/definitive/INTEGRATION_ALPHA.md).

## Current status

As of October 9, 2026:

- Public repository, upstream history/remotes, documentation, and prioritized backlog are established.
- Unmodified baseline builds passed on the development Mac; hosted macOS ARM64 and Linux x64/Steam Deck package builds also passed.
- Seven headless scenarios verified gameplay-test controls. Ordinary builds skip gameplay tests; these checks do not execute the game.
- The updater targets this project's repository. The guided manager pairs verified engine/mod packages, keeps private data separate, retains pre-update snapshots, and restores the prior engine with its prior data. Headless recovery fixtures pass; real Deck update recovery remains to be exercised.
- Four private discs were extracted locally; normal engine startup and five automated Mac gameplay smoke tests passed with the source-built Skurfa mod. Disc images, extracted retail assets and test campaigns remain excluded from Git.
- Optional texture tools audit per-face palettes, compare neural material candidates, preserve PSX transparency, and inspect original model keyframes. Private field and separate-palette Haschel/Meru comparisons pass sampled offline coverage checks, including variants preserving original head materials. An offline review viewer compares original and candidate artwork with verified source identities. An independent [Java material preflight](docs/definitive/JAVA_MATERIAL_PREFLIGHT.md) verifies the complete source-bound atlas/map and original transparency before a native experiment. Native multi-palette integration, community art review and Deck validation remain open. The published runtime pilot stays off by default. The owner judged the early texture passes too subtle; the [character remaster direction](docs/definitive/CHARACTER_REMASTER.md) now prioritizes clearer faces, richer materials and carefully remodeled surfaces faithful to the original designs. [Texture evidence](docs/definitive/TEXTURE_UPSCALING.md).
- Skurfa source/build integration and the first QoL interface slice are implemented. Definitive/Faithful presets and their configuration round trips pass on Mac; actual Steam Deck validation remains outstanding; these smoke tests do not establish artwork quality, controller usability or a complete playthrough.

[Baseline evidence](docs/definitive/VALIDATION.md) · [Build and test controls](docs/definitive/BUILD_CONTROLS.md) · [Roadmap](docs/definitive/ROADMAP.md) · [Prioritized backlog](docs/definitive/BACKLOG.md)

## Build from source

Install **JDK 25** and use the checked-in **Gradle 9.1.0 wrapper**. Compilation does not require game files.

```sh
git clone --recurse-submodules https://github.com/gideonidoru/legend-of-dragoon-definitive.git
cd legend-of-dragoon-definitive
./gradlew --no-daemon --console=plain clean build
```

To compile/package the Linux x64 Steam Deck target:

```sh
./gradlew --no-daemon --console=plain clean build definitivePackage portableInstaller -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

For an existing checkout, run `git submodule update --init --recursive` before building. Output is generated in `build/libs/`, including `mods/Skurfas-HDR-Backgrounds-v1.1.0.jar` compiled from the pinned source and artwork. Ordinary builds never launch the game. Building a Deck package on another computer does not prove Deck gameplay or native-library compatibility. See [setup and build instructions](docs/definitive/SETUP.md) for JDK configuration, dependencies, private disc input, and test controls. No game launch is part of these build commands.

## Roadmap and contributions

The guided installer establishes package identity, paired mod versions, and save-safe installation/upgrade contracts. Next validate representative Skurfa scenes and the equipment footer, extend the selected QoL inventory workflows, and establish a real Deck baseline. The offline texture comparison pilot is implemented; palette-aware model mapping and real Deck budgets remain prerequisites for useful runtime coverage. Expand optional gameplay features after the faithful-mode and persistence contracts are clear.

The [full project plan](docs/definitive/PROJECT_PLAN.md), [Sprint 0 checklist](docs/definitive/SPRINT_0.md), and [Deck test plan](docs/definitive/DECK_TEST_PLAN.md) define the work and its acceptance evidence. Contributions should identify the player benefit, upstream/mod overlap, license and credits, faithful-mode implications, and validation performed. Small, reversible changes with clear compatibility boundaries are preferred.

## License and acknowledgments

Engine code remains under the unchanged [GNU Affero General Public License v3](LICENSE). Preserve [CREDITS](CREDITS), [credits.txt](credits.txt), and applicable per-file notices. Community mods and artwork have their own terms; this engine license does not grant redistribution rights to game discs or remastered game artwork.

Thanks to the Severed Chains maintainers, the Legend of Dragoon Modding community, and the creators of the candidate projects linked above. Definitive is not affiliated with or endorsed by Sony, the original rights holders, the upstream maintainers, or those mod creators.

Supply your own supported game disc images privately in `isos/`. Never commit disc images, extracted game files, saves, private configuration, credentials, or local runtimes. Skurfa's public source and runtime artwork are included through its pinned submodule, under its own notices; game discs and extracted retail files are not included. The [original upstream README](docs/upstream/README.md) is preserved for historical context; its Java 21 note predates this checkout's Java 25 build requirement.
