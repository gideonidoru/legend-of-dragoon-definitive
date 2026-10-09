# Legend of Dragoon: Definitive

A Steam Deck-first modernization project built on [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains). Unofficial community project; not affiliated with Sony or the upstream maintainers.

**Milestone 1: repository setup, baseline build validation, and planning.** No engine behavior changes or playable Definitive release yet. Preserve a faithful mode; prioritize existing HD artwork, installation, controller usability, readable menus, and optional conveniences. No engine rewrite.

- [Full project plan and Sprint 0](docs/definitive/PROJECT_PLAN.md)
- [Setup and build](docs/definitive/SETUP.md)
- [Upstream synchronization](docs/definitive/UPSTREAM.md)
- [Architecture and mod extension points](docs/definitive/ARCHITECTURE.md)
- [Steam Deck-first roadmap](docs/definitive/ROADMAP.md)
- [Verified results and next steps](docs/definitive/VALIDATION.md)

The checkout requires **Java 25**, with the **Gradle 9.1.0 wrapper**. The Java 21 note in the preserved upstream README below is stale relative to `build.gradle` and upstream CI. Code remains under the unchanged [AGPL v3 license](LICENSE); retain [CREDITS](CREDITS) and [credits.txt](credits.txt), including artwork attribution. Game disc images, extracted game assets, saves, credentials, and local runtimes must remain outside version control. Build-only CI replaces inherited publishing workflows; gameplay tests remain explicitly opt-in. See [build and test controls](docs/definitive/BUILD_CONTROLS.md).

---

## Preserved upstream README

Like what you see? Send me a tip! You can also subscribe to our [YouTube channel](https://www.youtube.com/@legend-of-dragoon). We do devstreams most Wednesdays at 8:00PM Atlantic Time.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/W7W4HFVW9)

# Severed Chains

A project to reverse engineer Legend of Dragoon into a high-level language with a modding API. This is not an emulator, but assembly code disassembled and rewritten in Java.

### Current Progress

- Game engine is fully functional with a few minor glitches that don't negatively affect gameplay
- Modding API is actively in development
- Game is fully playable with no known crashes

### Interested in playing?

Visit our player guide here! https://legendofdragoon.org/projects/severed-chains/

### Interested in the code?

Visit our discord and drop into the [#modding channel](https://discord.com/channels/307164262063669248/318595603636551701)!

A strong knowledge of Java and MIPS assembly is recommended. If you are interested in contributing (or just curious), the following steps should get you up and running:
1. Install a git client and ensure the installation includes command line integration
2. Clone this repository to your local computer using git
3. Copy your ISOs or BINs of the LoD disks into the `isos` directory.
4. Open your local copy of this repository in your IDE (IntelliJ recommended)
5. Gradle should automatically attempt to configure the project and download all dependencies. If it doesn't, expand the gradle tab and click refresh. This process should succeed; resolve any errors if it does not. (lack of command line git can cause issues here)
6. Run the project

Note: Java 21 is required. It is **strongly** recommended to run with assertions enabled.

### Controls ###

Controllers and gamepads are fully supported. Keyboard controls may be changed in the in-game options menu.

Default keyboard controls:
- D-pad - arrow keys
- Shape buttons - WASD
- Start - enter
- Select - space
- L1 - Q
- L2 - 1
- L3 - Z
- R1 - E
- R2 - 3
- R3 - C
- F11 - pause
- F12 - open debug tools (developer features - can easily cause crashes)
- DEL - kill sounds (rarely, a sound may get stuck playing)
- Tab - VRAM viewer

To set up a controller, simply connect it before or after starting the game,
and select it from the controller dropdown in the in-game options menu.

**NOTE**: There are known issues with using DS4windows, and possibly other controller emulators. Severed Chains supports 1800+ controllers out of the box so it's very likely you can just plug in your controller, set it up, and play. If you find a controller that isn't in our controller database, please contact us and we'll work with you to get it added. If you do use DS4windows, make sure your controller isn't hidden and close DS4windows.

### Updating

When a new version is available, an "Update Available" button appears on the title screen. Clicking it downloads and applies the update automatically if a platform-specific release is available, otherwise it opens the release page in your browser for manual download.

The automatic updater preserves your saves, mods, ISOs, extracted files, and config. After the update completes, restart the game. A log of each update is written to `update_log.txt` in the game directory.
### GPU Selection

Severed Chains supports GPU preference settings for systems with multiple graphics cards (laptops with integrated and discrete GPUs).

**Linux/Steam Deck**: GPU preference is applied automatically based on your `launch.conf` setting. No additional setup required.

**Windows**: GPU preference requires one-time configuration. You have two options:

1. **Easy Setup (Recommended)**: After running launch.bat Run `gpu-optional-setup.bat` and follow the prompts. This is optional but makes GPU selection automatic.
   
2. **Manual Setup**: Add Severed Chains to Windows Graphics Settings:
   - Open Windows Settings > Display > Graphics
   - Click "Add desktop app" or "Browse"
   - Navigate to your Severed Chains folder and select `jdk25\bin\java.exe`
   - Click "Options" and choose your preferred GPU (Power saving or High performance)

To configure GPU preference in UNIX systems, edit `launch.conf` in your game folder:
- `GPU_PREFERENCE=0` - Auto (let the system decide)
- `GPU_PREFERENCE=1` - Discrete GPU (NVIDIA/AMD dedicated graphics)
- `GPU_PREFERENCE=2` - Integrated GPU (Intel/AMD integrated graphics)

### Copyright Information

Even though it is not an emulator, Legend of Dragoon Java can not be played without the user providing the LoD disk images. Assets are extracted from the ROMs at runtime. This codebase does not include any official Legend of Dragoon code or assets.
