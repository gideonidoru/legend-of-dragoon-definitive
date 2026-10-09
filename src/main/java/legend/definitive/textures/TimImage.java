// Definitive offline texture pilot (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.textures;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Strict bounded TIM reader. Alpha carries the PlayStation STP bit, not PNG opacity. */
public record TimImage(int width, int height, int paletteCount, int[] pixels) {
  public static TimImage read(final byte[] bytes, final int palette) throws IOException {
    if(bytes.length < 20 || bytes.length > 16 * 1024 * 1024) throw bad();
    final ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    if(in.getInt() != 0x10) throw bad();
    final int flags = in.getInt(), bpp = flags & 7;
    if((flags & ~15) != 0 || bpp > 2 || ((flags & 8) != 0) != (bpp < 2)) throw bad();
    int[] colours = null; int palettes = 0;
    if(bpp < 2) {
      final int coloursPerPalette = bpp == 0 ? 16 : 256;
      final Block clut = block(in);
      final int entries = clut.width * clut.height;
      if(entries % coloursPerPalette != 0 || entries < coloursPerPalette || clut.data.remaining() != entries * 2) throw bad();
      palettes = entries / coloursPerPalette;
      if(palette < 0 || palette >= palettes) throw new IOException("Choose a valid explicit palette: 0 to " + (palettes - 1));
      colours = new int[coloursPerPalette]; clut.data.position(palette * coloursPerPalette * 2);
      for(int i = 0; i < colours.length; i++) colours[i] = colour(Short.toUnsignedInt(clut.data.getShort()));
    } else if(palette != 0) throw new IOException("Direct-colour TIM requires palette 0.");
    final Block image = block(in);
    final int width = image.width * (bpp == 0 ? 4 : bpp == 1 ? 2 : 1), height = image.height;
    if(width < 1 || width > 512 || height < 1 || height > 512 || image.data.remaining() != image.width * height * 2 || in.hasRemaining()) throw bad();
    final int[] pixels = new int[width * height];
    for(int i = 0; i < pixels.length;) {
      if(bpp == 2) pixels[i++] = colour(Short.toUnsignedInt(image.data.getShort()));
      else {
        final int value = Byte.toUnsignedInt(image.data.get());
        if(bpp == 0) { pixels[i++] = colours[value & 15]; pixels[i++] = colours[value >>> 4]; }
        else pixels[i++] = colours[value];
      }
    }
    return new TimImage(width, height, palettes, pixels);
  }
  private record Block(int width, int height, ByteBuffer data) { }
  private static Block block(final ByteBuffer in) throws IOException {
    if(in.remaining() < 12) throw bad();
    final int size = in.getInt(); in.getShort(); in.getShort();
    final int width = Short.toUnsignedInt(in.getShort()), height = Short.toUnsignedInt(in.getShort());
    if(size < 12 || size - 12 > in.remaining() || width == 0 || height == 0 || (long)width * height > 512L * 512) throw bad();
    final ByteBuffer data = in.slice().order(ByteOrder.LITTLE_ENDIAN); data.limit(size - 12); in.position(in.position() + size - 12);
    return new Block(width, height, data);
  }
  private static int colour(final int value) {
    return ((value & 0x8000) != 0 ? 0xff000000 : 0) | ((value & 31) * 255 / 31 << 16) | ((value >>> 5 & 31) * 255 / 31 << 8) | (value >>> 10 & 31) * 255 / 31;
  }
  private static IOException bad() { return new IOException("Unsupported, oversized or malformed TIM. Pilot supports bounded 4/8/15-bit images."); }
}
