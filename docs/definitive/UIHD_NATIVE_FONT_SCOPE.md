# UIHD native font coverage

The Original HD provider covers the engine's shared 139-glyph font. It does not replace every game's font: the battle HUD, numeric menus and baked title/credits artwork have independent native rendering paths. A production battle capture exposed this gap: the shared text was HD while HP30/30 retained its original sprite digits.

UIHD supplies the shared Original HD font and source-bound native sprite lettering when enabled; disabling it restores the normal configured font and native sprite path. It does not alter the saved font setting or add another option. The prepared sprite increment extends the original ten small digits to HUD labels/slashes, large feedback, addition names, results/Points, PERFECT, menu numbers/separators and LEVEL UP. Baked chapter/world/credits/title lettering and the other unprepared families remain separate work.

The runtime catalog contains all 119 additional prepared vectors, including eight legacy battle-menu words. Current battle actions render localized shared-font text instead of those legacy word quads, retaining their existing dynamic labels and per-character coloring. Packaging the legacy words does not establish an active caller for them.

## Rendering contract

Selection binds a source TIM hash, indexed source crop, upload page, physical CLUT and authored PNG hash. The original indexed crop is retained for source-overwrite detection. Authored red-channel values encode palette indices; alpha encodes smooth coverage. The shader reads the live CLUT, including STP, and retains native tint, fade, scissor, depth and blend behavior. Independent caller artwork/texture overrides retain priority.

Palette upload layout follows `NativeUiTextureEvent.clutRows`, not the normalized TIM header. Basic UI uploads use four columns of four palettes; the forced system-font upload uses two horizontal palettes. Source bank-count validation remains separate from physical addressing.

PNG decoding is deferred until selection. Bounds and hashes are checked before decoding, and resident plus pending-retirement GPU bytes share a limit. Retirement runs after queued batches reset; its accounting callback does not reacquire the sprite registry monitor. Invalid, absent, stale or superseded selections retain the native path.

## Finite owned-engine inventory

This inventory concerns the known engine and shipped modules, not arbitrary future mods. The counts below describe source families and aliases, not visual acceptance or GPU coverage.

| Family | Required scope | Current state |
| --- | --- | --- |
| F01 shared font | 139 glyphs, including controller artwork | Original HD provider already integrated |
| F02 dialogue | Shared font plus speaker/scroll/shadow/clipping behavior | Shared provider; production interaction checks remain |
| F03 battle statistics | Ten small HP/MP/SP digits | Original two palette variants plus postbattle result aliases |
| F04 battle labels | HP/MP/SP composite and two slash placements | Exact composite/slash resources; original scissors and placements retained |
| F05 floating feedback | Small/large digits and MISS | Prepared small/large/MISS resources selected on the existing HUD route |
| F06 addition names | 66 semantic cells plus fallback91; 102 physical cells | All 67 prepared cells registered; existing advances and additive rendering retained |
| F07 large results | Digits, +/S/P and Points | Explicit selectors for all six uploaded palette banks; unsupported banks retain native rendering |
| F08 PERFECT | Eight placements, seven unique 8x16 crops | Exact 8px source crops, independently selected from LEVEL UP |
| F09 menu numbers | Fourteen cells plus clipped-colon decimal alias | TIM6665 main15 and comparison3 live palettes; original decimal scissor retained |
| F10 baked UI/menu labels | 364 entries, 2563 quad metrics across four UiFile sections | Eight prepared legacy word resources available; most artwork still native |
| F11 system font | `font.tim` forced image/CLUT upload | Typed-consumer closure incomplete; generic/software routes remain unresolved |
| F12 basic UI aliases | Three source events, sixteen palettes each | Physical palette-layout regression covered; artwork incomplete |
| F13 chapter cards | 48 TIM sources, 96 palette outputs | Existing restoration is not accepted as a faithful font redraw |
| F14 world names | Nine name rows, sixteen palette outputs | Preserve nontext compass/icons/grid; redraw incomplete |
| F15 retail credits | 356 cards, 317 distinct decoded card rasters | Joined script requires separate artwork; pilot quality not accepted |
| F16 title/end artwork | Title/logo/copyright/Game Over/The End | Direct-texture/profile paths need separate closure |
| F17 shipped/custom ownership | Exact supported module/font ownership | Known shipped closure established; future caller fonts remain independently owned |
| F18 LEVEL UP | Eight placements, six unique 7x16 crops | Normal/flash selectors plus 8px postbattle canvas aliases; strokes and caller metrics unchanged |

Full font coverage remains incomplete. Accepted increments can ship through the normal installer after their package and maintenance checks; they do not establish full-game, physical Steam Deck or all-family visual acceptance.

## Prepared resource generation

`scripts/generate-native-font-runtime.py` uses the reviewed packet/SVG hashes and locally supplied original source hashes to generate custom coverage PNGs. Outputs go to a new staging directory; original sources are read only. Publish the `resources` contents through UIHD, keeping palette previews and private diagnostics separate. The generator retains the ten previously accepted small-digit resources and adds ten exact postbattle palette aliases. Six postbattle LEVEL resources extend the original seven-pixel drawing canvas to eight pixels, retaining the native right-column shadow without stretching strokes or changing runtime spacing.

The menu comparison event uses the same indexed menu page with the actually uploaded extra palettes. It does not borrow the character portrait page or assume the basic UI palette layout. Every registered alias retains an exact source hash, crop and physical CLUT; live palette updates remain authoritative.

## Authoring controls

`scripts/RenderNativeSpriteFont.java` accepts bounded explicit M/L/Q/Z paths with palette roles. It preserves roles independently of duplicate palette RGB values and produces a preview decoded from those same roles. Unsupported paint combinations, groups/transforms, linked paths, existing outputs and overflowing dimensions fail closed. A failed output pair removes only outputs created by that invocation.

Run the independent headless authoring controls with JDK25 and Pillow:

```
python3 scripts/test-native-sprite-font.py
```

The optional `gradle/native-font-probe.gradle` runner uses a supplied windowless OpenGL context and private local source data. It is not an installer dependency and does not launch the game. Rendering receipts bind the source/package before execution; historical failures and older successful receipts are retained separately when code or artwork changes.
