# UIHD production and enhanced rendering

UIHD 0.3 completes the audited default HUD, portrait and interface source denominator in one deliverable. Its738 custom outputs cover gameplay/menu/save portraits, all HUD palettes, dialogue, world-map/exploration cues, every chapter animation/shadow state, the356 raster credits, title/end screens and loading eye. The [complete source ledger](UIHD_COVERAGE.md) distinguishes retained fonts/procedural UI, other mods' scene art and remaining hardware/artistic acceptance.

The target remains a gorgeous 2026 AA retro presentation with readable combat cues and responsive timing. Source/component coverage is complete; all-screen visual acceptance, combined gameplay and physical Steam Deck measurements remain unverified. The earlier 259-resource whole-game claim was incomplete and is superseded by the source-denominator audit.

UIHD remains independent in the existing Mods manager and enters the normal Definitive package. One coordinated installer release contains the project's custom work. Original extraction, discs, saves, private comparison boards, inference inputs, weights and local runtimes remain unpublished. Existing attribution is retained.

## Artwork and source fidelity

The catalog contains30 PNG atlas entries, 26 native-source atlas entries, 13 ordinary PNGs, 665 native variants from 424 families and4 direct rasters. Every output includes source/output identities, dimensions, processing pins, coverage identity and development review status. Palette and animation variants are resources, not extra screens.

The approved offline Real-ESRGAN-x4plus pipeline blends70% reconstructed RGB with30% nearest source artwork. It restores exact ordinary PNG alpha and native STP/black visibility classes after reconstruction. Small icons/portraits use 4×; full HUD/menu/world-map pages and large background/Game Over rasters use 2×. Font choice and existing HD title assets remain integrated. Source-layout inspection covered all nine portraits, every additional chapter/credit source and the added HUD/direct raster families; this is not final player artistic acceptance.

## Engine integration and residency

* UI draw metadata protects interface pixels from scene bloom, material lighting and scene smoothing. Native source/restoration samplers occupy units 3/2; normal/roughness maps remain on 4/5. UIHD adds no scene lighting/material overrides.
* Original geometry, logical UV units, scissoring, input and animation timing remain authoritative. Native VRAM still determines fragment discard and STP. Restored RGB is sampled only where live color/STP matches the original source reference, so palette animation and unrelated VRAM reuse fall back safely.
* Quad builders retain actual packed page/CLUT coordinates, per-quad source bounds, mirrored UVs and overrides. UI draws select native restoration automatically; compatible whole-object batches are supported. Mixed-source batches and existing authored texture owners retain their original paths. UIHD adds no indexed draw calls.
* Stable chapter name/number and 16 credit slots replace the same owner's previous source selection. Current-frame source changes invalidate stale retained draws and retire owned textures before replacement uploads. Other owners keep priority. Current-slot snapshots replay fresh events after mod reboot; the private snapshot cache is bounded to 64 slots and 2 MiB.
* Native catalog selections remain metadata-only until needed. Source plus restored GPU residency stays within 32 MiB. Current-frame pages are pinned; unpinned least-recently-used deferred pages retire before new allocation. Saturation and failed uploads keep native rendering. Cleared slots retire on the renderer even while paused; pooled draws reset optional state and rebind native VRAM on every fallback redraw.
* PNG decoding/prewarming uses the engine's content-addressed 64 MiB cache and bounded low-priority worker, including the disabled-cache path. Prewarming is sequential and creates no GPU objects. The full decoded 319,027,968-byte corpus is not permanent residency. Private TIM snapshots, temporary decode arrays, scene-owned direct textures and shared CPU cache are separate costs.
* The 56-entry gameplay atlas fits 1024², measured at 4 MiB on the windowless backend. Nine HD saved portraits fit within a 1024² CPU atlas; the serialized PNG records actual dimensions. Optional art failures retain source portraits. Other mods can grow bounded atlases to 2048²; those are separate layouts and not whole-game/Deck memory guarantees.
* Direct title artwork preserves native STP; Game Over preserves native MCQ placement/coverage. New and matching existing save-card portraits share source-bound atlas selection. Browsing never rewrites saves, and custom/HD snapshots remain authoritative.
* All input/resource reads are bounded and identities checked before selection. Unpadded icon sheets retain nearest filtering and exact source masks. Missing, changed or invalid sources retain original art.

## Verification

All 738 produced outputs were compared with actual source jobs before import: identity, dimensions, exact alpha/STP/black classes and protected visible pixels passed. Private comparison sheets were inspected and remain outside the public repository.

`UiArtworkTest` verifies all packaged identities, dimensions, coverage classes and family totals, shared-cache decoding, immutable/first-owner selection, physical coordinates, bounded slot replay and safe saved-card packing. Throwing listeners, malformed images and oversized optional portraits all retain original save imagery.

The opt-in `uihdSourceProbe` loads the actual UIHD JAR against a private fixture, validates all source families/variants and checks both save-card paths plus native unsupported-format fallback. It opens no window, game or GPU context. The opt-in windowless `nativeRendererProbe` checks actual texture uploads/deletion, atlas capacity, repeated credit/chapter slot replacement, paused fallback, 32 MiB residency and interaction with existing material maps, SMAA and shader rollback. This is Apple M5 Max OpenGL/Metal component evidence, not physical Steam Deck acceptance.

Full tests and exact-source hosted builds are recorded with the merge. Cinematic cadence, audio clocks and the coordinated mods' source fixes remain included. Builds, reconstruction and source review alone do not establish final2026 AA artistic quality or a complete gameplay stability guarantee.
