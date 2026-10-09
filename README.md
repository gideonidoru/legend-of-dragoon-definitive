# Legend of Dragoon: Definitive

**A Steam Deck-first modernization of The Legend of Dragoon, built on Severed Chains.**

Definitive aims to make the game easier to install, comfortable to play with a controller, and clearer on a handheld screen, with optional community HD artwork, enhanced model textures, and gameplay conveniences. A planned faithful mode will preserve the original gameplay experience while keeping presentation and accessibility choices separate.

This is an unofficial community project in early development. **There is no playable Definitive release yet.** The candidate integrations below are being evaluated; listing them does not mean they are included or compatible.

## Where this project comes from

[Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains), developed by the Legend of Dragoon Modding community, is a Java port of the game with a modding API. It supplies Definitive's engine, platform support, game systems, extraction tools, and mod infrastructure. Definitive builds on that work and the wider modding community's contributions.

This repository preserves Severed Chains' Git history, license, credits, and source notices. The initial validated upstream commit is [`fba1543543865e29ee572f479003d9b47158eeb3`](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains/commit/fba1543543865e29ee572f479003d9b47158eeb3), retained on `upstream-baseline`. Development continues here, with the official repository configured as `upstream` and reviewed merges used to incorporate its updates.

Project changes should stay modular: separate mods, settings, installation tools, and small engine hooks when needed. An engine rewrite is outside the project's goals. See the [upstream synchronization strategy](docs/definitive/UPSTREAM.md) and [architecture](docs/definitive/ARCHITECTURE.md).

## Goals

- **Existing HD artwork:** evaluate community packs, retain original-art fallback, and check foreground layers, transitions, memory use, and handheld readability.
- **Legacy model and texture upscaling:** develop an optional, reproducible enhancement pipeline for character, enemy, and environment textures, preserving the original art direction, silhouettes, and animation. Evaluate whole-frame upscaling separately using Deck evidence. [Scope and acceptance](docs/definitive/TEXTURE_UPSCALING.md).
- **Straightforward installation:** a guided Steam Deck setup with clear disc-import errors, safe upgrades, and recoverable saves/settings.
- **Controller comfort:** complete controller-only navigation, accurate button hints, remapping, reconnect support, and reliable Steam Input behavior.
- **Readable menus:** useful font, contrast, and spacing options at 1280×800 without clipped text or hidden actions.
- **Optional conveniences:** reuse existing settings and suitable mods for text speed, encounters, Addition assistance, and progression options, with explicit campaign choices.
- **Measured performance:** establish real Deck frame-time, loading, memory, and power evidence before rendering improvements or upscaling experiments.

Faithful mode is a planned, audited preset—not a claim that current upstream defaults match the retail game. Gameplay changes will remain optional. Disabling a reward or progression modifier later cannot undo rewards already earned.

## Candidate community integrations

These four projects form the initial evaluation shortlist. Credit and compatibility will be recorded per source revision and release.

| Candidate | Intended role | Evaluation status |
| --- | --- | --- |
| [Skurfa's upscaled HDR backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds) | Optional HD field artwork | Source compiles against our baseline; limited scene coverage. Artwork rights, scene correctness, SDR appearance, and Deck memory/performance still need validation. [Assessment](docs/definitive/SKURFA_RESEARCH.md). |
| [Quality of Life+](https://github.com/FrancisDionne/Severed-Chains/releases/tag/experimental) | Contextual action hints, inventory workflows, and training feedback | Selectively adapt interface ideas. It is a modified engine fork with divergent saves and unproven release-to-source correspondence. [Assessment](docs/definitive/QOL_FORK_RESEARCH.md). |
| [Dragoon Modifier](https://github.com/Legend-of-Dragoon-Modding/sc-dragoon-modifier) | Optional difficulty and balance presets | All 46 Java sources compile against our baseline. It is a broad campaign overhaul with stat-control defects and unresolved licensing; evaluate only as an optional profile. [Assessment](docs/definitive/DRAGOON_MODIFIER_RESEARCH.md). |
| [Battle Rewards Mod](https://github.com/DennytXVII/BattleRewardsMod/releases) | Optional experience and gold reward controls | AGPL source fails a compile check against our baseline; release tags are stale. Evaluate a current-API adaptation and reward overlap before adoption. [Assessment](docs/definitive/BATTLE_REWARDS_RESEARCH.md). |

The shortlist is not a bundled modpack. Do not assume all four can run together: save compatibility, overlapping reward changes, licensing, and exact engine/API versions must be checked. Artwork remains external to this source repository.

## Current status

As of October 9, 2026:

- Public repository, upstream history/remotes, documentation, and prioritized backlog are established.
- Unmodified baseline builds passed on the development Mac; hosted macOS ARM64 and Linux x64/Steam Deck package builds also passed.
- Seven headless scenarios verified gameplay-test controls. Ordinary builds skip gameplay tests; these checks do not execute the game.
- The updater targets this project's repository. Release versioning, compatible mod sets, and save-safe upgrade/rollback remain to be designed and verified.
- Four private disc images are available locally and their expected US disc IDs were checked. They are excluded from Git; extraction and gameplay have not run.
- No candidate mods are integrated, faithful mode is not implemented, and no actual Steam Deck validation has occurred.

[Baseline evidence](docs/definitive/VALIDATION.md) · [Build and test controls](docs/definitive/BUILD_CONTROLS.md) · [Roadmap](docs/definitive/ROADMAP.md) · [Prioritized backlog](docs/definitive/BACKLOG.md)

## Build from source

Install **JDK 25** and use the checked-in **Gradle 9.1.0 wrapper**. Compilation does not require game files.

```sh
git clone https://github.com/gideonidoru/legend-of-dragoon-definitive.git
cd legend-of-dragoon-definitive
./gradlew --no-daemon --console=plain clean build
```

To compile/package the Linux x64 Steam Deck target:

```sh
./gradlew --no-daemon --console=plain clean build -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

Output is generated in `build/libs/`. Building a Deck package on another computer does not prove Deck gameplay or native-library compatibility. See [setup and build instructions](docs/definitive/SETUP.md) for JDK configuration, dependencies, private disc input, and test controls. No game launch is part of these build commands.

## Roadmap and contributions

The next milestone focuses on package identity, compatible mod versions, and save-safe installation/upgrade contracts. Then establish a real Deck baseline and test representative artwork scenes, controller flows, and menu readability. Add a small model-texture upscaling proof of concept once texture mapping and Deck budgets are established. Expand optional gameplay features after the faithful-mode and persistence contracts are clear.

The [full project plan](docs/definitive/PROJECT_PLAN.md), [Sprint 0 checklist](docs/definitive/SPRINT_0.md), and [Deck test plan](docs/definitive/DECK_TEST_PLAN.md) define the work and its acceptance evidence. Contributions should identify the player benefit, upstream/mod overlap, license and credits, faithful-mode implications, and validation performed. Small, reversible changes with clear compatibility boundaries are preferred.

## License and acknowledgments

Engine code remains under the unchanged [GNU Affero General Public License v3](LICENSE). Preserve [CREDITS](CREDITS), [credits.txt](credits.txt), and applicable per-file notices. Community mods and artwork have their own terms; this engine license does not grant redistribution rights to game discs or remastered game artwork.

Thanks to the Severed Chains maintainers, the Legend of Dragoon Modding community, and the creators of the candidate projects linked above. Definitive is not affiliated with or endorsed by Sony, the original rights holders, the upstream maintainers, or those mod creators.

Supply your own supported game disc images privately in `isos/`. Never commit disc images, extracted game files, saves, private configuration, credentials, or local runtimes. No game disc content or third-party pack is distributed by this project. The [original upstream README](docs/upstream/README.md) is preserved for historical context; its Java 21 note predates this checkout's Java 25 build requirement.
