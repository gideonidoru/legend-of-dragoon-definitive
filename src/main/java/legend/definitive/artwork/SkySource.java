package legend.definitive.artwork;

import legend.game.textures.Image;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** CPU-only MCQ decoder, using McqBuilder's column-major tile and CLUT walk. */
public final class SkySource {
  private SkySource() { }

  public static Image decode(final byte[] source) throws IOException {
    if(source.length < 40 || source.length > 262188) throw new IOException("Invalid sky source bounds");
    final ByteBuffer bytes = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
    final int magic = bytes.getInt(0), offset = bytes.getInt(4);
    final int vw = unsigned(bytes, 8), vh = unsigned(bytes, 10);
    int cx = unsigned(bytes, 12), cy = unsigned(bytes, 14), u = unsigned(bytes, 16), v = unsigned(bytes, 18);
    final int width = unsigned(bytes, 20), height = unsigned(bytes, 22);
    if((magic != 0x151434d && magic != 0x251434d) || offset < 40 || offset > source.length || vw < 1 || vw > 256 || vh < 1 || vh > 512 || (long)offset + vw * vh * 2L != source.length) throw new IOException("Invalid sky source payload");
    if(width < 16 || height < 16 || width > 2048 || height > 2048 || width % 16 != 0 || height % 16 != 0) throw new IOException("Sky source is not a drawable tile image");
    int pageX = u & 0x3c0;
    final int pageY = v & 0x100;
    u = u * 4 & 0xfc;
    final byte[] rgba = new byte[width * height * 4];
    for(int x = 0; x < width; x += 16) for(int y = 0; y < height; y += 16) {
      final int[] palette = new int[16];
      for(int i = 0; i < 16; i++) palette[i] = word(bytes, offset, vw, vh, cx + i, cy);
      for(int dy = 0; dy < 16; dy++) for(int dx = 0; dx < 16; dx++) {
        final int tx = (u + dx) & 255, ty = (v + dy) & 255;
        final int packed = word(bytes, offset, vw, vh, pageX + tx / 4, pageY + ty);
        final int colour = palette[packed >>> ((tx & 3) * 4) & 15];
        final int p = ((y + dy) * width + x + dx) * 4;
        rgba[p] = (byte)((colour & 31) << 3);
        rgba[p + 1] = (byte)((colour >>> 5 & 31) << 3);
        rgba[p + 2] = (byte)((colour >>> 10 & 31) << 3);
        rgba[p + 3] = colour == 0 ? 0 : (byte)255;
      }
      v = v + 16 & 0xf0;
      if(v == 0) { u = u + 16 & 0xfc; if(u == 0) pageX += 64; }
      cy = cy + 1 & 255;
      if(cy == 0) cx += 16;
      cy |= pageY;
    }
    return new Image(rgba, width, height);
  }

  /** Retail zero texels remain holes; visible black (0x8000) remains opaque. */
  public static Image applyCoverage(final byte[] source, final Image replacement) throws IOException {
    final Image original = decode(source);
    final int scale = replacement.width / original.width;
    if(scale < 1 || scale > 8 || replacement.width > 4096 || replacement.height > 4096 || replacement.width != original.width * scale || replacement.height != original.height * scale || replacement.data.length != (long)replacement.width * replacement.height * 4) throw new IOException("Sky artwork differs from original layout");
    final byte[] pixels = replacement.data.clone();
    for(int y = 0; y < replacement.height; y++) for(int x = 0; x < replacement.width; x++) {
      final int p = (y * replacement.width + x) * 4;
      final boolean visible = original.data[((y / scale) * original.width + x / scale) * 4 + 3] != 0;
      if(!visible) { pixels[p] = 0; pixels[p + 1] = 0; pixels[p + 2] = 0; }
      pixels[p + 3] = visible ? (byte)255 : 0;
    }
    return new Image(pixels, replacement.width, replacement.height);
  }

  private static int unsigned(final ByteBuffer bytes, final int offset) { return Short.toUnsignedInt(bytes.getShort(offset)); }
  private static int word(final ByteBuffer bytes, final int offset, final int width, final int height, final int x, final int y) throws IOException {
    if(x < 0 || y < 0 || x >= width || y >= height) throw new IOException("Sky samples outside its uploaded VRAM rectangle");
    return unsigned(bytes, offset + (y * width + x) * 2);
  }
}
