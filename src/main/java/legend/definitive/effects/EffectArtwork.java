// Definitive effects (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.effects;

import legend.core.gpu.Rect4i;
import legend.core.gpu.Gpu;
import legend.core.renderer.Texture;
import legend.definitive.textures.TimImage;
import legend.game.modding.events.submap.EffectTextureEvent;
import legend.game.textures.Image;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.util.Arrays;

import static legend.core.GameEngine.EVENTS;
import static legend.core.GameEngine.GPU;

/** One render-thread owned sprite texture. Live indexed changes retain the original route. */
public final class EffectArtwork implements AutoCloseable {
  public final Texture texture;
  private final Rect4i imageRect;
  private final Rect4i paletteRect;
  private final byte[] imageControl;
  private final byte[] paletteControl;
  private final FileData imageScratch;
  private final FileData paletteScratch;
  private boolean closed;

  EffectArtwork(final Texture texture, final Tim source) {
    this.texture = texture;
    this.imageRect = source.getImageRect();
    this.paletteRect = source.getClutRect();
    this.imageControl = source.getImageData().getBytes().clone();
    this.paletteControl = source.getClutData().getBytes().clone();
    this.imageScratch = new FileData(new byte[this.imageControl.length]);
    this.paletteScratch = new FileData(new byte[this.paletteControl.length]);
  }

  /** Retain original behavior unless the complete native image/palette binding matches. */
  public static EffectArtwork load(final String id, final String image, final String palette, final int tpage, final int clut, final int u, final int v) {
    try {
      final byte[] original = Loader.loadFileSync("SUBMAP/" + image + ".tim").getBytes();
      final byte[] colours = image.equals(palette) ? original : Loader.loadFileSync("SUBMAP/" + palette + ".tim").getBytes();
      final byte[] bytes = withPalette(original, colours);
      final Tim source = new Tim(new FileData(bytes));
      if(!bindingMatches(source, tpage, clut, u, v)) return null;
      final EffectTextureEvent event = EVENTS.postEvent(new EffectTextureEvent(id, bytes));
      if(event.replacement == null) return null;
      validate(TimImage.read(bytes, 0), event.replacement);
      final Image replacement = event.replacement;
      final Texture texture = Texture.create("FxHD " + id, builder -> {
        final var pixels = BufferUtils.createByteBuffer(replacement.data.length);
        pixels.put(replacement.data).flip();
        builder.data(pixels, replacement.width, replacement.height);
        builder.wrapS(false);
        builder.wrapT(false);
      });
      return new EffectArtwork(texture, source);
    } catch(final Exception failure) {
      org.apache.logging.log4j.LogManager.getLogger().warn("FxHD retained original {}: {}", id, failure.getMessage());
      return null;
    }
  }

  /** Preserve the actual palette header and colors, including the cloud's dust palette. */
  public static byte[] withPalette(final byte[] image, final byte[] palette) throws IOException {
    TimImage.read(image, 0);
    TimImage.read(palette, 0);
    final FileData pixels = new FileData(image), colours = new FileData(palette);
    if(pixels.readInt(4) != 8 || colours.readInt(4) != 8 || pixels.readInt(8) != 44 || colours.readInt(8) != 44) {
      throw new IOException("Field effects require one 16-colour palette");
    }
    final byte[] result = image.clone();
    System.arraycopy(palette, 8, result, 8, 44);
    return result;
  }

  public static boolean bindingMatches(final Tim source, final int tpage, final int clut, final int u, final int v) {
    final Rect4i pixels = source.getImageRect(), palette = source.getClutRect();
    return (tpage >>> 7 & 3) == 0 && pixels.x == (tpage & 15) * 64 + u / 4
      && pixels.y == ((tpage & 16) != 0 ? 256 : 0) + v
      && palette.x == (clut & 63) * 16 && palette.y == clut >>> 6;
  }

  /** Alpha is the original STP bit. Black with/without STP must retain its classification. */
  public static void validate(final TimImage source, final Image image) throws IOException {
    final int scale = image.width / source.width();
    if((scale != 2 && scale != 4) || image.width != source.width() * scale || image.height != source.height() * scale
      || image.data.length != image.width * image.height * 4) throw new IOException("Effect dimensions differ");
    for(int y = 0; y < image.height; y++) for(int x = 0; x < image.width; x++) {
      final int expected = source.pixels()[(y / scale) * source.width() + x / scale], index = (y * image.width + x) * 4;
      final int rgb = Byte.toUnsignedInt(image.data[index]) << 16 | Byte.toUnsignedInt(image.data[index + 1]) << 8 | Byte.toUnsignedInt(image.data[index + 2]);
      final int alpha = Byte.toUnsignedInt(image.data[index + 3]);
      if(alpha != expected >>> 24 || (rgb == 0 && alpha == 0) != (expected == 0) || (expected & 0xffffff) == 0 && rgb != 0) {
        throw new IOException("Effect STP/discard/visible-black changed");
      }
    }
  }

  public boolean matchesNative() {
    return this.matchesNative(GPU);
  }

  boolean matchesNative(final Gpu gpu) {
    if(this.closed) return false;
    gpu.downloadData15(this.imageRect, this.imageScratch);
    gpu.downloadData15(this.paletteRect, this.paletteScratch);
    return Arrays.equals(this.imageControl, this.imageScratch.getBytes()) && Arrays.equals(this.paletteControl, this.paletteScratch.getBytes());
  }

  @Override
  public void close() {
    if(!this.closed) {
      this.closed = true;
      this.texture.delete();
    }
  }
}
