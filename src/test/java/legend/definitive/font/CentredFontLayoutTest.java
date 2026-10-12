package legend.definitive.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import legend.core.font.Font;
import legend.core.font.Glyph;
import legend.core.lang.I18nText;
import legend.game.Text;
import legend.game.inventory.screens.FontOptions;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the shipping SItem method; replaces only GPU drawing and static game initialization. */
class CentredFontLayoutTest {
  public static final class DrawSink {
    record Draw(Font font, String text, float x, float y) { }
    static final List<Draw> draws = new ArrayList<>();
    public static void renderText(final String text, final float x, final float y, final FontOptions options, final Text.QueueCallback callback) {
      draws.add(new Draw(null, text, x, y));
    }
    public static void renderText(final Font font, final String text, final float x, final float y, final FontOptions options, final Text.QueueCallback callback) {
      draws.add(new Draw(font, text, x, y));
    }
  }

  private static Font font() {
    final var glyphs = new Char2ObjectOpenHashMap<Glyph>();
    for(final char c : new char[]{'A', 'B', ' '}) glyphs.put(c, new Glyph(c, 0, 0, 1, 1, 0, 0, c == ' ' ? 1 : 2, 12, true));
    return new Font(Path.of("fixture-font.json"), new I18nText("fixture"), glyphs);
  }

  private static float render(final Font font, final String text, final int width, final float size) throws Exception {
    DrawSink.draws.clear();
    final var cf = ClassFile.of();
    final byte[] source;
    try(final var in = CentredFontLayoutTest.class.getResourceAsStream("/legend/game/SItem.class")) { source = in.readAllBytes(); }
    final byte[] isolated = cf.transformClass(cf.parse(source), ClassTransform.dropping(element -> element instanceof MethodModel method && method.methodName().equalsString("<clinit>"))
      .andThen(ClassTransform.transformingMethodBodies((builder, element) -> {
        if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("legend/game/Text") && call.name().equalsString("renderText")) {
          builder.invokestatic(ClassDesc.of(DrawSink.class.getName()), call.name().stringValue(), call.typeSymbol());
        } else builder.with(element);
      })));
    final Class<?> target = new ClassLoader(CentredFontLayoutTest.class.getClassLoader()) {
      Class<?> define() { return defineClass("legend.game.SItem", isolated, 0, isolated.length); }
    }.define();
    try {
      return (float)target.getMethod("renderMenuCentredText", Font.class, String.class, float.class, float.class, int.class, FontOptions.class, Text.QueueCallback.class)
        .invoke(null, font, text, 100.0f, 20.0f, width, new FontOptions().size(size), null);
    } catch(final InvocationTargetException failed) {
      if(failed.getCause() instanceof Exception failure) throw failure;
      throw failed;
    }
  }

  @Test void glyphWiderThanLineDoesNotBacktrackBeforeTextOrProduceEmptyLine() throws Exception {
    assertEquals(24, render(font(), "AA", 1, 1));
    assertEquals(List.of("A", "A"), DrawSink.draws.stream().map(DrawSink.Draw::text).toList());
  }
  @Test void backtrackingNeverCrossesParagraphOrCurrentLine() throws Exception {
    render(font(), "A\nBBBB", 2, 1);
    assertEquals(List.of("A", "B", "B", "B", "B"), DrawSink.draws.stream().map(DrawSink.Draw::text).toList());
  }
  @Test void breakingAtSpaceRemeasuresTheNextWord() throws Exception {
    render(font(), "AA BBBB", 6, 1);
    assertEquals(List.of("AA", "BBB", "B"), DrawSink.draws.stream().map(DrawSink.Draw::text).toList());
  }
  @Test void suppliedFontIsUsedForBothMeasurementAndDrawing() throws Exception {
    final Font font = font();
    assertEquals(12, render(font, "AB", 100, 1));
    assertEquals(1, DrawSink.draws.size());
    assertSame(font, DrawSink.draws.getFirst().font());
    assertEquals(98, DrawSink.draws.getFirst().x());
  }
  @Test void scaledWrapPreservesLineHeight() throws Exception {
    assertEquals(72, render(font(), "AAAAA", 8, 2));
    assertEquals(List.of("AA", "AA", "A"), DrawSink.draws.stream().map(DrawSink.Draw::text).toList());
  }
  @Test void healthyEmptyTextAndUnwrappedExplicitNewlinePreserveHeight() throws Exception {
    assertEquals(12, render(font(), "", 100, 1));
    assertEquals(24, render(font(), "AA\nB", 100, 1));
    assertEquals(List.of("AA\nB"), DrawSink.draws.stream().map(DrawSink.Draw::text).toList());
  }
}
