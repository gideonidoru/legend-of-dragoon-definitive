# UIHD Original HD font

## Approved behavior

The owner approved a faithful HD redraw of the Original font on 2026-10-11, then specified that it is the default supplied by enabled UIHD, not a selectable option. UIHD disable/unload restores normal engine configured-font behavior. Do not overwrite the saved font configuration, replace base font files, add a dropdown entry, or create another toggle. This is an incremental UIHD deliverable in the normal installation; it does not delay already-ready releases.

## Backlog and acceptance

| Work | Status | Required evidence |
| --- | --- | --- |
| Source and layout audit | In progress | Original atlas/metadata identities; all 139 glyphs and controller symbols; logical metrics and clipping paths |
| Faithful per-glyph redraw | Planned | Individually reconstructed original shapes, stroke character and proportions; source comparison and independent art review; no whole-atlas text invention |
| Atlas packing | Planned | Transparent gutters, exact bounds, UV consistency, complete glyph set, deterministic output hashes |
| UIHD runtime ownership | Planned | Event-based selection or equivalent narrow seam; automatic override with UIHD enabled, restoration on disable/unload; stable existing control references and config/mod reload |
| Resource lifecycle | Planned | Render-thread texture/mesh retirement, persistent cache cleanup, safe reenable and failed artwork fallback |
| Packaging | Planned | UIHD-owned resources, no loose engine-font discovery; exact-source package inventory and enabled/disabled loader checks |
| Steam Deck readability | Open | Physical dialogue/menu/controller-prompt reading, wrapping/clipping/shadows at normal play scales |

Preserve Original's exact x/y offsets, widths, heights, baseline, 12-unit line height, colours, shadows and clipping behavior. Preserve all 139 glyphs, including controller remappings and generic/paddle/misc buttons. Increased atlas dimensions do not prove redraw quality, runtime selection, or Deck readability.

## Initial audit

Both engine fonts have 139 glyphs, identical codepoint sets, valid atlas rectangles and complete printable ASCII coverage. Original is 256 by 256; Smooth is 1024 by 1024. All InputCodepoints drawing symbols are present. EF00 is a dialogue action marker expanded to its current binding, not a missing drawable glyph. Literal checked repository language resources use supported characters. User/mod-generated Unicode is not thereby covered.

Fonts use the regular PNG texture constructor with nearest filtering by default; HD world filtering is opt-in per texture and does not automatically qualify fonts. Render-time prompt remapping can use a different width than measurement (e.g. Smooth Xbox One Start: base width 12, drawn Menu width 10). Existing metrics must be measured consistently with actual rendered glyph selection.

Source findings to reproduce separately: Font.textWidth counts each newline as an 8-unit missing glyph after resetting the line; lineWidth omits missing glyphs while charWidth advances by 8. SItem.renderMenuCentredText's ten-character backtrack is unbounded by startIndex/zero, and its final render call ignores the supplied font. These are layout defects rather than atlas-resolution gaps. PR82 merged the newline/missing-glyph measurement correction into main (four baseline failures plus one healthy control become five passing cases). The centered-wrap and mapped-prompt observations still need separate reproduction and fixes; no UIHD font default change has yet shipped.

## Proposed implementation seam

Keep the stored configured font separate from the effective UIHD override. Reselect on mod boot and config changes. Existing menu controls capture font references, so replacing only the global DEFAULT_FONT cannot guarantee coherent live disable/reenable; use a stable effective-font reference or equivalent lifecycle-safe indirection. Keep UIHD resources inside the module and load them through bounded resource APIs, outside gfx/fonts auto-discovery. Retire superseded graphics resources only through renderer ownership, with original/configured fallback when validated artwork cannot load.

Authored, reviewed, merged, published and device-accepted states are separate. This document starts the approved backlog and design; it does not claim completed artwork or runtime/device acceptance.


## Reference candidate and authoring safeguards

All 139 deterministic vector reference glyphs were authored, with exact Original logical metrics and a zero-difference pixel-centre oracle. Independent art review rejected this softened staircase treatment as insufficient for the requested AA redraw; it is a reference candidate, not selected runtime artwork. The approved redraw still needs reconstructed curves, diagonals and controller outlines plus individual art review.

The authoring tool pins both Original source files and reads each once. It checks every lexical output ancestor, rejects oversize padded cells and existing outputs, validates the atlas in memory before writing, and removes only its newly owned incomplete candidate after a write failure. Eight isolated controls exercise parent/link-before-dotdot/broken links, changed source pins, oversize cells, partial-write cleanup, preserved existing outputs and source mutation after the single reads. These controls establish authoring safety, not art acceptance or font integration.
