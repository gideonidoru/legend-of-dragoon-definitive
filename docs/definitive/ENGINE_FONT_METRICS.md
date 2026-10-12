# Font layout measurement correction

## Problem and behavior

Dynamic multiline labels and text wrapping use Font.textWidth, while centered/right-aligned rendering uses Font.lineWidth. Previously a newline reset the current width and then added the missing-glyph fallback width (8), so Original A\nA measured 16 instead of 8. Missing characters advanced 8 during drawing but contributed zero to lineWidth, shifting alignment relative to the rendered fallback glyph.

textWidth now skips the newline after resetting its line accumulator. lineWidth now uses the existing charWidth rule for every non-newline character. Supported glyph advances, atlas artwork, font options, configuration, baseline, line height and drawing are unchanged.

## Verification scope

An isolated headless probe against the actual public generation-10 engine confirmed A=8, A\nA=16 and unsupported-character advance 8 versus alignment width 0. On exact accepted source 11ad865002bc78639aeca594c850ebb2e257761c, five JUnit cases produced four failures and one healthy control before the correction; all five pass after it. Cases cover longer first/second lines, empty/trailing lines, unsupported characters, offset line measurement and unchanged supported text/space advances.

This increment corrects measurement only. UIHD Original HD redraw/ownership, controller-remapping measurement, centered-wrap backtracking and supplied-font forwarding are separate tracked work. Headless cases do not establish physical Steam Deck readability or a new atlas's artistic acceptance.
