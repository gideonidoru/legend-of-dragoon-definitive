package legend.core.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import legend.core.gpu.Bpp;
import legend.core.gte.MV;
import legend.core.lang.TextComponent;
import legend.core.memory.Method;
import legend.core.platform.input.InputCodepoints;
import legend.core.platform.input.InputGamepadType;
import legend.core.renderer.Obj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;

import java.nio.file.Path;

import static legend.core.GameEngine.RENDERER;

public class Font {
  public final Path path;
  public final TextComponent name;
  private final Char2ObjectMap<Glyph> glyphs;

  private Texture texture;
  private Obj obj;
  private Object resourceOwner;
  private boolean retirementScheduled;

  synchronized void claimOwnership(final Object owner) {
    if(this.retirementScheduled || this.resourceOwner != null && this.resourceOwner != owner) throw new IllegalArgumentException("Font resources already owned or retired");
    this.resourceOwner = owner;
  }
  protected boolean hasOwnedResources() { return this.texture != null || this.obj != null; }
  synchronized boolean isOwned() { return this.resourceOwner != null; }
  synchronized void scheduleRetirement() { this.retirementScheduled = true; }

  public Font(final Path path, final TextComponent name, final Char2ObjectMap<Glyph> glyphs) {
    this.path = path;
    this.name = name;
    this.glyphs = glyphs;
  }

  public void init() {
    synchronized(this) { if(this.retirementScheduled) throw new IllegalStateException("Retired optional font cannot be initialized"); }
    if(this.texture == null) {
      this.texture = this.createTexture();
      this.texture.persistent = true;
    }

    if(this.obj == null) {
      final QuadBuilder builder = new QuadBuilder("Font " + this.name);

      for(final Glyph glyph : this.glyphs.values()) {
        glyph.index = builder.currentQuadIndex();

        builder
          .add()
          .bpp(Bpp.BITS_24)
          .uv(glyph.texU / (float)this.texture.width, glyph.texV / (float)this.texture.height)
          .uvSize(glyph.texW / (float)this.texture.width, glyph.texH / (float)this.texture.height)
          .pos(glyph.x, glyph.y, 0.0f)
          .posSize(glyph.w, glyph.h)
        ;
      }

      this.obj = builder.build();
      this.obj.persistent = true;
    }
  }

  protected Texture createTexture() {
    final String fontName = this.path.getFileName().toString();
    return Texture.png("Font " + fontName, this.path.resolveSibling(fontName.substring(0, fontName.lastIndexOf('.')) + ".png"));
  }

  /** Only an exclusively owned optional font may be released, after retained draw queues clear. */
  public void releaseOwnedResources() {
    synchronized(this) { if(this.resourceOwner == null || !this.retirementScheduled) throw new IllegalStateException("Only scheduled optional font resources may be released after queue reset"); }
    if(this.obj != null) { this.obj.delete(); this.obj = null; }
    if(this.texture != null) { this.texture.delete(); this.texture = null; }
  }

  /** A captured font reference can change its effective metrics without changing its identity. */
  public long generation() { return this.inputGamepadType().ordinal(); }

  protected InputGamepadType inputGamepadType() { return legend.core.GameEngine.PLATFORM.getGamepadType(); }

  /** Layout, colour and drawing resolve the same platform-specific prompt symbol. */
  private char visibleCodepoint(final char codepoint) {
    return codepoint >= InputCodepoints.DPAD_UP && codepoint <= InputCodepoints.GUIDE
      ? InputCodepoints.getCodepoint(this.inputGamepadType(), codepoint) : codepoint;
  }

  public QueuedModelStandard queueChar(final char chr, final MV transforms) {
    final Glyph glyph = this.glyphs.get(this.visibleCodepoint(chr));

    if(glyph == null) {
      final MV mv = new MV(transforms);
      mv.scale(5.0f, 5.0f, 1.0f);
      mv.transfer.x += 5.0f;
      mv.transfer.y += 5.0f;
      return RENDERER.queueUiOrthoModel(RENDERER.lineBox, mv, QueuedModelStandard.class);
    }

    return RENDERER.queueUiOrthoModel(this.obj, transforms, QueuedModelStandard.class)
      .texture(this.texture)
      .vertices(glyph.index * 4, 4)
    ;
  }

  public boolean usesColour(final char chr) {
    final Glyph glyph = this.glyphs.get(this.visibleCodepoint(chr));
    return glyph != null && glyph.colour;
  }

  public int charWidth(final char chr) {
    final Glyph glyph = this.glyphs.get(this.visibleCodepoint(chr));
    return glyph == null ? 8 : glyph.w;
  }

  /** Measures the width of a single line (up to the first linebreak encountered) */
  public int lineWidth(final String text) {
    return this.lineWidth(text, 0);
  }

  /** Measures the width of a single line (up to the first linebreak encountered) */
  public int lineWidth(final String text, final int start) {
    int width = 0;
    for(int index = start; index < text.length(); index++) {
      final char c = text.charAt(index);

      if(c == '\n') {
        break;
      }

      width += this.charWidth(c);
    }

    return width;
  }

  /** Measures the width of the whole string, taking linebreaks into account. The width will be the width of the widest line. */
  @Method(0x8002a59cL)
  public int textWidth(final String text) {
    int width = 0;
    int currentWidth = 0;
    for(int index = 0; index < text.length(); index++) {
      if(text.charAt(index) == '\n') {
        currentWidth = 0;
        continue;
      }

      currentWidth += this.charWidth(text.charAt(index));

      if(currentWidth > width) {
        width = currentWidth;
      }
    }

    return width;
  }

  public int textHeight(final String text) {
    int lines = 1;
    int newlinePos = -1;
    while((newlinePos = text.indexOf('\n', newlinePos + 1)) != -1) {
      lines++;
    }

    return lines * 12;
  }
}
