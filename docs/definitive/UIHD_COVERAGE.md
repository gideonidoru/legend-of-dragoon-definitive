# UIHD default UI source denominator

UIHD 0.3 accounts for every default UI raster loading/drawing path found in the engine source audit. This is source and component coverage, not a claim of final AA artistic acceptance, whole-game playthrough stability or physical Steam Deck measurements. The earlier 259-resource claim missed entire consumers; this ledger corrects that claim and records their completed source integration.

The selected catalog has 738 custom outputs: 30 PNG atlas entries, 26 native-source atlas entries, 13 ordinary PNGs, 665 native palette variants from 424 source families, and 4 direct rasters. Counts measure artwork/palette/animation resources rather than screens. All nine default party portraits are included; there is no remaining default HUD/portrait raster family deferred to a later batch.

| Included family | Complete source denominator | Runtime consumer / integration |
| --- | --- | --- |
| Battle HUD | `DRGN0/4113/0` indexed page with six palette files; 6 × 16 = 96 variants | `Battle.battleHudTexturesLoaded` captures the actual shared page with each relocated CLUT. `BattleHud`, `BattleUiParts`, combat lists, targeting, Addition and Dragoon timing/meter/eye/Perfect quads use protected UI draws and source metadata. |
| Basic/menu/dialogue UI | `DRGN0/6669/{0,1,2}`: 48 variants; `6665` main 15, items 14, characters 3 plus final 13 overlay variants; dialogue and seven-frame arrow: 2 | `Scus94491BpeSegment.loadBasicUiTextures`, `SItem.loadMenuTextures`; `Menus`, `Text`, shops/settings/retail save cards reuse those pages. Source rectangles, physical palette columns/rows and final palette overwrites match the loaders. |
| Live portraits | All nine `characters/{dart,lavitz,shana,rose,haschel,albert,meru,kongol,miranda}/portrait.png` | Atlas selection reaches battle, character cards, inventory, item-use, party-swap and post-battle consumers. Legacy native portraits are also covered by the menu sheets. |
| Severed save-card portraits | The same nine sources, without adding duplicate output artwork | New V10 saves share atlas replacement selection and bounded CPU packing. Existing matching originals receive display-only restoration. Existing HD/custom portraits retain their own artwork. Save bytes and original portrait rectangles are not rewritten by browsing. Optional listener, malformed-image and capacity failures retain authoritative original portraits. |
| Goods and spirits | All 21 `gfx/goods/*.png`; 8 elements × 3 spirit frames plus 2 divine overlays from 4113/0+5 | Registry atlas replacement retains original logical dimensions. Protected lettering and portrait-in-picture pixels remain source-faithful. |
| PNG symbols/controls | Ten elements, full battle command sheet, both checkbox states and loading eye: 13 PNGs | `UiTextures` selection preserves original UV units. The loading-eye draw is protected UI and reloads with mod selection. Cell-based sheets retain isolated processing and nearest sampling. |
| World-map HUD | Entire `DRGN0/5695/0`, 256 × 240, all 16 palettes | Continent labels, Coolon/Queen Fury states, map markers, zoom overlays and path dots. World-map loader captures the source; zoom and perspective path-dot draws explicitly mark UI. Archives 5695/1..3 are scene smoke and are not interface art. |
| Exploration indicators | Entire `SUBMAP/big_arrow.tim`, `small_arrow.tim`, `alert.tim`: 1 + 3 + 1 palette variants | `SMap` captures the actual upload; `MapIndicator` retains its UV/CLUT overrides and player/door/alert animation. |
| Chapter cards | Four archives 6670..6673, each six name frames and six number frames, each two palettes: 96 outputs from 48 TIMs | Every original upload transition calls the source selection helper. Stable name/number slots replace previous frames; text and both shadow/translucency variants retain original geometry, brightness and timing. |
| Retail ending credits | Archive 5720: 356 nonempty physical TIMs; empty entry 50 has no raster artwork | All 356 outputs are selected by source identity when their original 16 upload slots rotate. Credit names, types, positions, colors, scrolling and timing remain original. The ignored final row and two inaccurate retail image-block lengths are handled with bounded source-rectangle decoding. |
| The End | Entire `DRGN0/7610`: one palette | Original indexed text draw is restored. Its palette/brightness remains authoritative. The separate flame CLUT animation is retained by the scene path. Unsupported/no-CLUT replacement sources remain native. |
| Title and modern credits background | `DRGN0/5718/{0,1}` stitched background, 4 trademark, 5+6 stitched copyright: 3 rasters | Both `Ttle` and the independent `CreditsScreen` background consumer use the same source-bound raster seam. Placement, scrolling and fade remain original. |
| Game Over | `DRGN0/6667` MCQ: one raster | Bounded CPU decoder follows the native column-major tile/CLUT walk; the four absent unused tail words are never read. Selected artwork keeps native placement/dimensions. Missing or invalid replacement uses original MCQ rendering. |

## Retained and separately owned rendering

These are explicit members of the source audit, rather than missing UIHD raster replacements:

* `gfx/fonts/default.png` and `smooth.png`, including controller glyphs, use the engine font renderer. The original retro font and existing 1024² smooth font remain selectable in the graphics configuration. UIHD does not override that choice.
* Existing HD `gfx/ui/logo.png` and `title_menu.png` remain authored title artwork; the additional native title sources above are upgraded. `credits_fade.png` is a procedural gradient mask.
* HUD bars, borders, Addition reticles, panel backgrounds and credit gradients use geometry. They retain resolution-independent drawing and protected UI scope.
* Dabas/PocketStation renders an emulated monochrome display; its pixel identity is gameplay state, not a restoration source. The OS window icon is application metadata.
* World-map location landscapes and the parchment backdrop use EnvHD's existing thumbnail/background seams. World-map terrain, smoke, title fire and other scene/effect textures belong to EnvHD/FxHD and their independent coverage ledgers. Their appearance does not expand the default interface raster denominator.
* Other mods' characters and custom saved snapshots are not the nine default sources. Source checks and earlier ownership preserve those assets; adding a character does not falsely reuse a default portrait.

## Evidence and remaining acceptance

The source audit followed loading plus drawing consumers, including direct texture conversions and `McqBuilder` paths, rather than deriving completion from producer jobs. Independent standards/spec reviews found the missing families and then rechecked the completed routes. No remaining default UI raster bypass was found.

The opt-in packaged `uihdSourceProbe` checks all 738 outputs against actual local sources: 56 atlas selections, 13 PNG selections, 4 direct rasters, 665 native variants from 424 families. Every chapter/credit source is checked before its stable slot rotates. Current sources replay after mod reboot with zero eager GPU allocation. All nine old/new saved-card portrait paths are checked; unsupported no-CLUT sources remain native. No private extraction or save is included in the public assets.

`UiArtworkTest` verifies the complete packaged hashes/dimensions/coverage classes, first-owner behavior, slot snapshots, CPU packing and optional save-artwork failures. The windowless `nativeRendererProbe` exercises actual GPU ownership, five successive slot replacements, retirement before allocation, bounded residency, pooled/paused fallback, complete atlas packing and interaction with the enhanced renderer. Gameplay, every-screen visual acceptance and physical Deck frame-time/residency measurements still need player/hardware evidence.
