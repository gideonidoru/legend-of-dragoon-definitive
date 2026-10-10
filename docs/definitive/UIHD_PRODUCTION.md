# Complete UIHD coverage and engine integration

The approved target is a beautiful 2026 AA presentation that retains the game's retro identity, readable combat cues and responsive timing. UIHD now supplies complete default HUD and portrait asset coverage in one deliverable. Source selection and rendering-component verification are complete; combined gameplay, all-screen visual acceptance and physical Steam Deck measurements remain unverified.

UIHD v0.2.0 is independent in the existing Mods manager and included by the normal Definitive package. The project coordinates one combined installer release. No original extraction, discs, saves, private comparison boards, inference inputs, weights or local runtimes are published.

## Coverage ledger

The catalog at `integrations/uihd/runtime-assets/uihd/catalog.json` contains 259 selected custom PNGs with source/output hashes, dimensions, exact coverage classes, palette/crop identity, pinned processing identities and review status. Counts describe resources and palette variants, rather than distinct screens.

| Default family | Complete coverage |
| --- | --- |
| Character portraits | All nine: Dart, Lavitz, Shana, Rose, Haschel, Albert, Meru, Kongol and Miranda; 48² to 192². Atlas replacements reach battle and menu consumers. Legacy native character pages are also covered. |
| Combat HUD | Entire 256² indexed page at 2× for all six banks of sixteen palettes: 96 resources. Digits, damage/status indicators, targeting, addition and Dragoon timing/frame/meter/eye/Perfect consumers retain original geometry and animation. |
| Shared basic UI | Entire 256×160 page at 2× for all three sixteen-palette banks: 48 resources; the original four-column/four-row physical palette arrangement is preserved. |
| Menu sheets | Entire 256×192 main page: fifteen palettes. Entire 256×64 items page: fourteen palettes. Entire 256² character page: three base plus thirteen final overlay palettes. All at 2×. Final overlays match the native loader's overwritten palette rows. |
| Dragoon spirits | All eight elements' three animation frames plus both divine overlays: 26 atlas entries at 4×. |
| Goods | All 21 default goods icons at 4×. Lavitz's portrait-in-picture and bulletin lettering preserve visible source pixels. |
| PNG symbols and controls | All ten element symbols, the complete battle command sheet and both checkbox states at 4×; cell-isolated processing preserves frame identity. |
| Dialogue | Complete shared border and seven-frame arrow sheets at 4×. |
| Existing HD and procedural UI | Existing smooth font option, title/logo artwork, credits treatment and resolution-independent mesh panels, bars and reticles are retained. Font choice remains in the game's graphics configuration. These do not require a replacement low-resolution texture. |

There are 30 PNG atlas entries, 26 native-source atlas entries, 12 ordinary PNG entries and 191 native palette variants across fifteen families. The approved pinned offline Real-ESRGAN pipeline blends 70% reconstructed RGB with 30% nearest source art. Exact PNG alpha and native STP/black visibility classes are restored after processing. Source-layout review retains original silhouettes and face identity; it does not establish player visual acceptance of every screen.

## Enhanced rendering and residency

* Interface metadata protects UI from scene bloom, material lighting and scene smoothing. Native source/reference samplers use units 3/2; normal/roughness maps retain units 4/5. UIHD introduces no lighting profile or material override.
* Display geometry, UV units, scissoring, input and animation timing remain unchanged. Every fragment retains native VRAM discard and STP classification. RGB restoration applies only when actual live native color/STP matches the source reference; palette changes and VRAM reuse fall back per pixel.
* Quad builders retain indexed source coordinates. Ordinary UI draws select native restoration automatically, including selected HUD sub-quads, mirrored UVs, physical page/CLUT overrides and compatible whole-object batches. Mixed-source batches retain authoritative native rendering. Existing authored texture owners win. No extra geometry or draw call is added.
* Pooled draws reset vertex offsets and optional state. Paused retained draws validate residency before each use and restore native VRAM on every fallback redraw. Removed selections cannot resurrect through old queued preparation. Cleared owned textures retire on the renderer before the next upload; failed partial uploads retire immediately.
* All 191 native variants are selected as metadata, with no eager decode or GPU allocation. First use validates and uploads only the required page; resident source plus restored artwork is limited to 32 MiB. Current-frame pages are pinned; least recently used unpinned deferred pages retire immediately before replacement uploads, including while paused. Saturation or failed uploads retain original art. Failed resources are not decoded again on every draw.
* Native private snapshots replay fresh events through the current mod bus after registry/campaign reboot. Storage remains bounded to sixteen families and 2 MiB. Each event preserves its physical palette row layout and independent propagation state.
* PNG decoding/prewarming reuses the engine's content-addressed 64 MiB shared cache and bounded low-priority worker, including its disabled-cache path. Prewarming remains sequential and creates no GPU objects. Full catalog decoded size is **169,535,488 bytes**, a corpus size rather than persistent residency. Private TIM snapshots, temporary decoding arrays and shared cache residency are separate from GPU residency.
* The complete default 56-entry UI atlas packs to 1024², measured at 4 MiB on the windowless backend; standalone PNG artwork adds 1,769,472 bytes. Native residency is bounded independently. Other active mods can grow the atlas up to the existing 2048² capacity; no whole-game memory or Steam Deck frame-time guarantee is inferred from these component bounds.
* Unpadded atlas/command sheets retain nearest filtering and exact source masks. Original logical dimensions stay separate from enlarged textures. Failed/oversized/hash-mismatched sources retain originals, and all source reads are bounded.

## Verification

Before import, all 259 outputs were compared with actual local source jobs: original/output identity, dimensions, exact alpha, native black/STP classes and protected visible regions passed. Private original comparison sheets were inspected for all nine portraits and representative HUD pages; none are published.

`UiArtworkTest` checks every packaged resource's hash, dimensions, coverage classes and complete family counts. It also checks ownership, immutable event snapshots, native visibility, shared cached PNG decoding, physical coordinates, lazy registration and sixteen-family bounded replay.

The opt-in `uihdSourceProbe` loads the actual UIHD JAR against a private working fixture. It replaces all 56 atlas entries and twelve PNGs, selects and decodes all 191 native variants from the fifteen real source families, preserves prior atlas ownership and replays all families with zero GPU allocation. It opens no game, window or GPU context.

The opt-in windowless `nativeRendererProbe` verifies the complete atlas, exact GPU STP uploads, disable/re-enable behavior, logical dimensions and deletion. Its full-coverage fixture exercises lazy residency, same-frame pinning, immediate eviction, automatic HUD source binding, mirrored UVs, pooled offsets and repeated paused fallback. Existing material-map, SMAA, shader rollback and UI-protection fixtures remain in the same backend run. This is Apple M5 Max OpenGL/Metal component evidence, not physical Steam Deck validation.

The two-axis source review found pooled offset reuse, repeated paused fallback and eviction/read-bound issues; corrections and component fixtures cover them. Final combined checks and review results are recorded in the merge PR. The game and desktop apps were not launched by this work. A passing build and source-faithful reconstruction do not by themselves establish final 2026 AA artistic acceptance.
