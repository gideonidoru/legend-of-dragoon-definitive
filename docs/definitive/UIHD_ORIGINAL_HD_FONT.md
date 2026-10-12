# UIHD Original HD font

## Approved behavior

The owner approved a faithful HD redraw of the Original font on 2026-10-11, then specified that it is the default supplied by enabled UIHD, not a selectable option. UIHD disable/unload restores normal engine configured-font behavior. Do not overwrite the saved font configuration, replace base font files, add a dropdown entry, or create another toggle. The later release instruction makes the complete redraw and verified runtime wiring a prerequisite for the next public installer. The existing public installer remains available while this incremental UIHD improvement is reviewed.

## Backlog and acceptance

| Work | Status | Required evidence |
| --- | --- | --- |
| Source and layout audit | Completed source audit | Original atlas/metadata identities; all 139 glyphs and controller symbols; logical metrics and clipping paths |
| Faithful per-glyph redraw | 139 authored; independent source and native LINEAR shape reviews support integration | Individually reconstructed original shapes, stroke character and proportions; source comparison and independent art review; no whole-atlas text invention |
| Atlas packing | Complete candidate: 2048² RGBA8, 16-pixel gutters, deterministic four-file output | Transparent gutters, exact bounds, UV consistency, complete glyph set, deterministic output hashes |
| UIHD runtime ownership | Implemented candidate; packaged/native verification in progress | Event-based selection or equivalent narrow seam; automatic override with UIHD enabled, restoration on disable/unload; stable existing control references and config/mod reload |
| Resource lifecycle | Windowless shipping-renderer lifetime checks pass; final integrated replay pending | Render-thread texture/mesh retirement, persistent cache cleanup, safe reenable and failed artwork fallback |
| Packaging | Complete candidate resources inside UIHD; actual provider tests in progress | UIHD-owned resources, no loose engine-font discovery; exact-source package inventory and enabled/disabled loader checks |
| Steam Deck readability | Open | Physical dialogue/menu/controller-prompt reading, wrapping/clipping/shadows at normal play scales |

Preserve Original's exact x/y offsets, widths, heights, baseline, 12-unit line height, colours, shadows and clipping behavior. Preserve all 139 glyphs, including controller remappings and generic/paddle/misc buttons. Increased atlas dimensions do not prove redraw quality, runtime selection, or Deck readability.

## Initial audit

Both engine fonts have 139 glyphs, identical codepoint sets, valid atlas rectangles and complete printable ASCII coverage. Original is 256 by 256; Smooth is 1024 by 1024. All InputCodepoints drawing symbols are present. EF00 is a dialogue action marker expanded to its current binding, not a missing drawable glyph. Literal checked repository language resources use supported characters. User/mod-generated Unicode is not thereby covered.

Fonts use the regular PNG texture constructor with nearest filtering by default; HD world filtering is opt-in per texture and does not automatically qualify fonts. Render-time prompt remapping can use a different width than measurement (e.g. Smooth Xbox One Start: base width 12, drawn Menu width 10). Existing metrics must be measured consistently with actual rendered glyph selection.

Source findings to reproduce separately: Font.textWidth counts each newline as an 8-unit missing glyph after resetting the line; lineWidth omits missing glyphs while charWidth advances by 8. SItem.renderMenuCentredText's ten-character backtrack is unbounded by startIndex/zero, and its final render call ignores the supplied font. These are layout defects rather than atlas-resolution gaps. PR82 merged the newline/missing-glyph measurement correction into main (four baseline failures plus one healthy control become five passing cases). PR95 merged the centered-wrap/backtracking and supplied-font correction (five baseline failures and one healthy control become six passing cases). The font integration candidate also reproduces four mapped-prompt measurement/colour/invalidation failures and corrects layout, colour and drawing to use the same platform-specific symbol; its ordinary/already-mapped control remains unchanged. No public installer containing the UIHD font default has yet shipped.

## Proposed implementation seam

Keep the stored configured font separate from the effective UIHD override. Reselect on mod boot and config changes. Existing menu controls capture font references, so replacing only the global DEFAULT_FONT cannot guarantee coherent live disable/reenable; use a stable effective-font reference or equivalent lifecycle-safe indirection. Keep UIHD resources inside the module and load them through bounded resource APIs, outside gfx/fonts auto-discovery. Retire superseded graphics resources only through renderer ownership, with original/configured fallback when validated artwork cannot load.

Authored, reviewed, merged, published and device-accepted states are separate. This document starts the approved backlog and design; it does not claim completed artwork or runtime/device acceptance.


## Reference candidate and authoring safeguards

All 139 deterministic vector reference glyphs were authored, with exact Original logical metrics and a zero-difference pixel-centre oracle. Independent art review rejected this softened staircase treatment as insufficient for the requested AA redraw; it is a reference candidate, not selected runtime artwork. At that checkpoint, the approved redraw still needed reconstructed curves, diagonals and controller outlines plus individual art review. The complete replacement and its runtime evidence are recorded below.

The authoring tool pins both Original source files and reads each once. It checks every lexical output ancestor, rejects oversize padded cells and existing outputs, validates the atlas in memory before writing, and removes only its newly owned incomplete candidate after a write failure. Eight isolated controls exercise parent/link-before-dotdot/broken links, changed source pins, oversize cells, partial-write cleanup, preserved existing outputs and source mutation after the single reads. These controls establish authoring safety, not art acceptance or font integration.

## Complete redraw candidate

The reference staircase candidate remains excluded. The current redraw consists of 98 individually authored letters/punctuation/extra symbols and 41 individually authored controller symbols, with curved bowls and continuous diagonals. Rectilinear stems and bars retain their original hard corners. Independent comparisons corrected Q's tail and the dollar sign's continuous shaft. Every source x/y/w/h/colour record remains exact, including zero-height space and the 41 private-area symbols. EF00 remains an expanded action marker, not a drawable glyph.

The glyph SVGs and their source-bound packets are versioned authoring sources. `scripts/pack-uihd-original-font.py` packs all and only 139 glyphs; `scripts/RenderUiHdFont.java` rasterizes the explicit vectors without substituting a host font. The complete 2048 by 2048 RGBA8 PNG and metadata live inside UIHD's `/uihd/font/` resources, outside engine font discovery. Two independent packs produced four byte-identical output files, and all atlas pixels outside the packed glyph rectangles—including gutters—are transparent.

Runtime selection is automatic through an optional font event. A stable effective font preserves existing control references and separate configured state. Only exclusively owned packaged resources retire, after every retained renderer batch resets; uninitialized candidates do not retain a GPU retirement lease. Failed provider validation or upload falls back to the configured font. The optional atlas uses texture alpha and linear filtering without mipmaps; original/configured font rendering remains unchanged apart from coherent controller-symbol measurement. Runtime proof, accepted source, public installation and physical Steam Deck readability are recorded separately. This candidate status does not assert a released or device-accepted HD font.

The windowless shipping-renderer probe verifies all 4,194,304 uploaded atlas pixels, visible antialiased edges, shadows, clipping, retained texture/mesh handles before queue reset, disposal afterward, identical reenable output and separate configured state. It renders all 138 non-space glyphs at 1×/2×/3× against black and RGB204 gray (828 raster cases); blank space remains the 139th source-bound glyph. Independent inspection of all twelve sheets found no blocking identity, counter or sampling defect. White uncoloured controller symbols remain faint on light gray and have zero contrast on pure white, as expected from the preserved source colour flags. Raster presence does not establish comfortable reading in the game's actual menus or on Steam Deck; contextual contrast and physical-device acceptance remain open.

Original font/reference glyphs and controller symbols derive from Severed Chains and The Legend of Dragoon. Preserve the repository's upstream licenses and attribution. Custom SVG redraws are Definitive project deliverables; controller marks retain their respective owners.
