# UIHD production and engine integration

## Target and scope

The approved target is whole-game interface art consistent with a beautiful 2026 AA presentation that preserves Legend of Dragoon's retro identity, readability, responsiveness and Steam Deck performance. This first production milestone supplies the shared loading/rendering foundation and a reviewed source-faithful batch. It does **not** finish the whole UI or establish the final artistic bar.

UIHD remains independent in the existing Mods manager and is included in normal downloadable installation. The existing package task includes its reproducible JAR. No independent installer release is published by this work; delivery can consolidate this source with the current combined release. Public custom assets and code are approved; no original extraction, discs, saves, private comparison boards, inference inputs, weights or runtimes enter the package.

## Current selection and remaining coverage

The runtime catalog is `integrations/uihd/runtime-assets/uihd/catalog.json`. Each entry pins source bytes, output bytes, original/output dimensions, palette/crop when applicable, processing identities, source coverage and review status. Missing/mismatched resources retain the original interface.

| Family | Current selected coverage | Remaining work |
| --- | --- | --- |
| Goods | All 21 default goods, 32² to 128² | In-game readability and artistic acceptance |
| Elements | All ten default PNG symbols, 48² to 192² | In-game readability |
| Battle commands | 64² sheet to 256², 16² cells processed separately | Full HUD, numbers, status, damage, targeting and addition/Dragoon timing prompts |
| Checkboxes | Both 16² source cells at 4× | Settings/menu combination review |
| Menus | Standard 48×32 panel region, 15 palette variants at 4×; native item left 128×64 region, 14 variants at 2× | Other panel styles, cursors, arrows, decorative UI, remaining screens and native text |
| Dialogue | 64×46 border region and seven-frame 112×14 arrow at 4× | Native scene/clipping/animation review |
| Portraits | Original nine characters remain | Bespoke faithful restoration, likeness and small-size review |
| Fonts/title | Existing original and smooth fonts, logo/title assets remain | Typography/controller-glyph audit; existing HD artwork should not be blindly upscaled |

The 64 PNGs comprise 21 atlas images, 12 PNG sheets/images and 31 native palette/crop variants. They are not 64 distinct screens and not a whole-game completion percentage. All runtime entries have source-layout review with native/Deck acceptance pending. Custom production uses the approved pinned offline Real-ESRGAN batch, source context, a 70% reconstruction/30% nearest blend and exact source coverage. Portrait-in-picture and bulletin lettering preserve visible source pixels rather than invented detail. Native item crops exclude the baked lettering on the right of the sheet. The producer is `scripts/build-uihd-assets.py`; outputs and inference work start outside the repository.

## Enhanced engine behavior

* `.ui()` metadata keeps these draws in the renderer's existing protected interface layer: no scene bloom, lighting/material response or smoothing washes out text or combat cues. UIHD adds no scene lighting profile or material override.
* PNG loading retains original logical dimensions separately from larger GPU textures. Command animation offsets and checkbox coordinates continue using source units. No input, animation, hitbox or battle timing changes are made.
* Native UI uses two local reference/artwork samplers on units 3/2. Native indexed VRAM remains on its normal units; normal/roughness maps keep units 4/5. Every fragment still uses original native discard and STP. RGB restoration applies only while the actual live native color and STP match the local source reference. Palette changes or VRAM reuse fall back to original RGB per pixel.
* No extra UI geometry or draw call is introduced. Optional state resets when pooled models are acquired and every standard draw sets the enabled uniform. Existing texture overrides win.
* Unpadded UI atlases and command frames use nearest filtering and clamped sheets. Their exact alpha/black coverage is restored after processing; neighboring cells cannot bleed through linear filtering. Whole-image scene filtering remains controlled by existing graphics settings.
* PNG decoding uses the existing content-addressed 64 MiB shared cache (or uncached route when disabled). Low-priority sequential prewarming reuses the bounded engine worker and never creates graphics objects. Native region textures prepare at the beginning of the next renderer frame; late arrivals can prepare on first use. Failed uploads retain native art and are not retried every draw.
* Native selection retains at most 16 MiB of reference plus restored image payload. Removed selections cannot resurrect textures from queued preparation after a mod reboot. Cleared GPU textures use the engine's ordinary deferred deletion. Native source snapshots remain private in memory (eight families / 2 MiB maximum) and replay through the current event bus on mod or registry reboot, so enabled native art survives campaign loads and disabled mods cannot retain it. The persistent checkbox sheet also reloads after mod selection with a generation guard; obsolete selections cannot replace a newer sheet. Atlas packing grows only to 2048² and releases native packing buffers even when a packing attempt fails. Original image decoding also releases STB allocations after copying.

## Budget and verification

Current enhanced RGBA payload is 6,744,064 bytes (6.43 MiB), before atlas packing. Native reference plus enhanced payload is 4,167,296 bytes (3.97 MiB); standalone PNG payload is 1,769,472 bytes (1.69 MiB); goods contribute 1,376,256 bytes to the shared atlas. Atlas padding/capacity and other active mods determine actual GPU residency. A 1024² shared atlas plus these native/PNG textures would total about 9.66 MiB, within the proposed 16 MiB additional-UI budget; this is an estimate, not a measured Steam Deck total. Shared decoded cache residency and CPU copies are separate from GPU memory. A full 2048² atlas can exceed the additional-UI proposal when combined with other mods and must be measured rather than advertised as a guaranteed whole-game bound.

Headless tests validate resource identity, dimensions, every output texel's exact alpha/STP/black class, protected visible pixels, first-owner precedence, immutable event snapshots, indexed PNG decoding, reuse after prewarming, physical menu-page overrides and partial dialogue regions, bounded atlas retries and payload limits. Private source audit also compared all native candidates against the locally extracted originals before import. Windowless GPU verification exercises UIHD restoration, unchanged STP/discard coverage, live palette fallback, scene exclusion and uniform reset alongside the existing lighting/material/bloom/interface-protection fixtures.

No game, window, desktop app or physical Steam Deck was launched for this milestone. Combined-mod gameplay, first-visit frame time, actual GPU residency, disable/re-enable behavior, all-screen readability and user visual acceptance remain required. A passing build or neural restoration is not proof that the 2026 AA artistic goal has been reached.
