package legend.definitive.font;

import legend.core.font.Font;
import legend.core.font.FontManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontMetricsTest {
  private final Font font = new FontManager().get(Path.of("gfx/fonts/default.json"));

  @Test
  void multilineWidthMeasuresOnlyDrawableLines() {
    assertEquals(this.font.charWidth('A'), this.font.textWidth("A\nA"));
    assertEquals(this.font.lineWidth("WWWW"), this.font.textWidth("A\nWWWW"));
    assertEquals(this.font.lineWidth("WWWW"), this.font.textWidth("WWWW\nA"));
  }

  @Test
  void emptyLinesHaveNoGlyphAdvance() {
    assertEquals(0, this.font.textWidth(""));
    assertEquals(0, this.font.textWidth("\n\n"));
    assertEquals(this.font.lineWidth("A"), this.font.textWidth("\nA\n"));
  }

  @Test
  void missingGlyphAlignmentMatchesItsRenderedAdvance() {
    final String text = "A\u2603A";
    assertEquals(this.font.charWidth('A') * 2 + 8, this.font.lineWidth(text));
    assertEquals(this.font.textWidth(text), this.font.lineWidth(text));
    assertEquals(8, this.font.lineWidth("\u2603"));
  }

  @Test
  void lineStartingAtAnOffsetStopsAtItsOwnLinebreak() {
    assertEquals(this.font.charWidth('A') + 8, this.font.lineWidth("WW\nA\u2603\nWWWW", 3));
    assertEquals(0, this.font.lineWidth("WW\n", 3));
  }

  @Test
  void supportedGlyphAndSpaceAdvancesRemainUnchanged() {
    final String text = "The Legend of Dragoon 0123456789";
    int expected = 0;
    for(int i = 0; i < text.length(); i++) expected += this.font.charWidth(text.charAt(i));
    assertEquals(expected, this.font.lineWidth(text));
    assertEquals(expected, this.font.textWidth(text));
  }
}
