package legend.definitive.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import legend.core.font.Font;
import legend.core.font.Glyph;
import legend.core.lang.RawText;
import legend.core.platform.input.InputCodepoints;
import legend.core.platform.input.InputGamepadType;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FontPromptMetricsTest {
  private static final class ControllerFont extends Font {
    InputGamepadType type = InputGamepadType.STANDARD;
    ControllerFont() { super(Path.of("fixture.json"), new RawText("prompt"), glyphs()); }
    protected InputGamepadType inputGamepadType() { return this.type; }
  }
  private static Char2ObjectOpenHashMap<Glyph> glyphs() {
    var glyphs = new Char2ObjectOpenHashMap<Glyph>();
    for(var pair : new int[][]{{'A',8},{InputCodepoints.START,12},{InputCodepoints.XBOX_BUTTON_MENU,10},{InputCodepoints.GUIDE,14},{InputCodepoints.XBOX_BUTTON_GUIDE,12}}) {
      glyphs.put((char)pair[0],new Glyph((char)pair[0],0,0,pair[1],16,0,0,pair[1],16,pair[0]!=InputCodepoints.GUIDE));
    }
    return glyphs;
  }
  @Test void promptAdvanceUsesTheActualControllerGlyph() {
    var font = new ControllerFont(); font.type = InputGamepadType.XBOX_ONE;
    assertEquals(10,font.charWidth(InputCodepoints.START));
    assertEquals(12,font.charWidth(InputCodepoints.GUIDE));
    assertEquals(18,font.lineWidth("A"+InputCodepoints.START));
  }
  @Test void multilinePromptMeasurementUsesMappedGlyphsOnEachLine() {
    var font = new ControllerFont(); font.type = InputGamepadType.XBOX_ONE;
    assertEquals(20,font.textWidth(""+InputCodepoints.START+InputCodepoints.START+"\nA"));
    assertEquals(10,font.lineWidth("A\n"+InputCodepoints.START,2));
  }
  @Test void colourAndShadowEligibilityFollowTheDrawnSymbol() {
    var font = new ControllerFont(); assertFalse(font.usesColour(InputCodepoints.GUIDE));
    font.type = InputGamepadType.XBOX_ONE; assertTrue(font.usesColour(InputCodepoints.GUIDE));
  }
  @Test void controllerChangeInvalidatesCapturedLabelMetrics() {
    var font = new ControllerFont(); long old = font.generation();
    font.type = InputGamepadType.XBOX_ONE; assertNotEquals(old,font.generation());
  }
  @Test void ordinaryAndAlreadyMappedGlyphsRemainUnchanged() {
    var font = new ControllerFont();
    for(var type : InputGamepadType.values()) {
      font.type=type;assertEquals(8,font.charWidth('A'));assertEquals(10,font.charWidth(InputCodepoints.XBOX_BUTTON_MENU));assertEquals(8,font.charWidth('\u2603'));
    }
  }
}
