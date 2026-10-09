// Definitive independent Scale2x implementation, AGPL v3; see LICENSE.
package legend.definitive.textures;

/** Scale2x rules: https://www.scale2x.it/algorithm . No added colours, geometry or model weights. */
public final class TextureScaler {
  private TextureScaler() { }
  public static int[] twice(final TimImage image, final boolean nearest) {
    final int w = image.width(), h = image.height();
    final int[] source = image.pixels(), out = new int[w * h * 4];
    for(int y = 0; y < h; y++) for(int x = 0; x < w; x++) {
      final int e = source[y * w + x], b = source[Math.max(0, y - 1) * w + x], d = source[y * w + Math.max(0, x - 1)], f = source[y * w + Math.min(w - 1, x + 1)], south = source[Math.min(h - 1, y + 1) * w + x];
      final int offset = y * 2 * w * 2 + x * 2;
      final boolean edge = !nearest && b != south && d != f;
      out[offset] = edge && d == b ? d : e; out[offset + 1] = edge && b == f ? f : e;
      out[offset + w * 2] = edge && d == south ? d : e; out[offset + w * 2 + 1] = edge && south == f ? f : e;
    }
    return out;
  }
}
