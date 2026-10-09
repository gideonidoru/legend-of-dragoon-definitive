// Definitive private atlas preflight (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.materials;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import legend.definitive.materials.MaterialAtlas.Region;

/** Reads original, unrelocated material references without constructing or mutating game models. */
final class SourceMaterials {
  record Layout(int sourceWidth, int sourceHeight, int width, int height, List<Region> regions) { }
  private static final int PADDING = 8, MAX_ATLAS = 4096;
  private SourceMaterials() { }

  static Layout layout(final byte[] model, final byte[] tim, final int scale) throws IOException {
    if(scale != 2 && scale != 4) throw bad("Scale must be 2 or 4.");
    final ByteBuffer texture = ByteBuffer.wrap(tim).order(ByteOrder.LITTLE_ENDIAN);
    range(texture, 0, 20);
    final int cw = ushort(texture, 16), ch = ushort(texture, 18);
    if(texture.getInt(0) != 0x10 || texture.getInt(4) != 8 || (cw != 16 && cw != 32 && cw != 64) || ch < 1 || ch > 16 || texture.getInt(8) != 12 + cw * ch * 2) throw bad("Requires a standard 4-bit TIM and bounded CLUT.");
    final int image = 20 + cw * ch * 2;
    range(texture, image, 12);
    final int words = ushort(texture, image + 8), height = ushort(texture, image + 10), width = words * 4;
    final long payload = (long)words * height * 2;
    if(width < 1 || width > 512 || height < 1 || height > 512 || texture.getInt(image) != 12 + payload || image + 12 + payload != tim.length) throw bad("Requires a standard bounded TIM payload; padded field TIMs remain unsupported.");

    final ByteBuffer source = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN);
    range(source, 0, 12);
    final long tmd = uint(source, 0);
    range(source, tmd, 12);
    final long count = uint(source, (int)tmd + 8), table = tmd + 12;
    if(source.getInt((int)tmd) != 0x41 || source.getInt((int)tmd + 4) != 0 || count < 1 || count > 256 || source.getInt(4) != 0 || source.getInt(8) != 0) throw bad("Requires an unpacked relative TMD without CLUT animation or extra dependencies.");
    range(source, table, count * 28);
    final Map<Integer, int[]> bounds = new TreeMap<>();
    long totalFaces = 0;
    int texturedFaces = 0;
    for(int part = 0; part < count; part++) {
      final int entry = (int)table + part * 28;
      final long nv = uint(source, entry + 4), nn = uint(source, entry + 12), faces = uint(source, entry + 20);
      totalFaces += faces;
      if(nv > 100000 || nn > 100000 || faces > 100000 || totalFaces > 100000) throw bad("Model exceeds material-audit limits.");
      range(source, table + uint(source, entry), nv * 8);
      range(source, table + uint(source, entry + 8), nn * 8);
      long position = table + uint(source, entry + 16);
      for(int face = 0; face < faces; face++) {
        range(source, position, 4);
        final int header = source.getInt((int)position), mode = header >>> 24, size = (header >>> 8 & 255) * 4;
        if((mode >>> 5 & 3) != 1 || size < 4 || size > 64) throw bad("Unsupported polygon packet.");
        range(source, position + 4, size);
        if((mode & 4) != 0) {
          final int vertices = (mode & 8) != 0 ? 4 : 3;
          if(size < vertices * 4 || ++texturedFaces > 5000) throw bad("Truncated or excessive texture packets.");
          final int packet = (int)position + 4, clut = ushort(source, packet + 2), tpage = ushort(source, packet + 6);
          final int column = clut & 3, row = clut >>> 6 & 15;
          if((tpage >>> 7 & 3) != 0 || column >= cw / 16 || row >= ch) throw bad("Palette or texture bit depth is outside the source TIM.");
          final int palette = row * (cw / 16) + column;
          final int[] box = bounds.computeIfAbsent(palette, ignored -> new int[]{width, height, -1, -1});
          for(int vertex = 0; vertex < vertices; vertex++) {
            final int u = Byte.toUnsignedInt(source.get(packet + vertex * 4)), v = Byte.toUnsignedInt(source.get(packet + vertex * 4 + 1));
            if(u >= width || v >= height) throw bad("UV lies outside the source TIM.");
            box[0] = Math.min(box[0], u); box[1] = Math.min(box[1], v);
            box[2] = Math.max(box[2], u); box[3] = Math.max(box[3], v);
          }
        }
        position += 4 + size;
      }
    }
    // Integer UV extrema themselves belong to the conservative floor-cell footprint.
    // Its crop bounds are therefore min(vertex)..max(vertex)+1, even for degenerate faces.
    final List<Region> crops = new ArrayList<>();
    for(final var entry : bounds.entrySet()) {
      final int[] box = entry.getValue();
      final int left = box[0] - PADDING, top = box[1] - PADDING, right = box[2] + 1 + PADDING, bottom = box[3] + 1 + PADDING;
      crops.add(new Region(entry.getKey(), left, top, right, bottom, 0, 0, (right - left) * scale, (bottom - top) * scale));
    }
    return pack(width, height, crops);
  }

  private static Layout pack(final int sourceWidth, final int sourceHeight, final List<Region> crops) throws IOException {
    if(crops.isEmpty() || crops.size() > 64) throw bad("Requires 1 to 64 used palettes.");
    final List<Region> order = new ArrayList<>(crops);
    order.sort(Comparator.comparingInt(Region::height).reversed().thenComparing(Comparator.comparingInt(Region::width).reversed()).thenComparingInt(Region::palette));
    final int widest = order.stream().mapToInt(Region::width).max().orElseThrow();
    Layout best = null;
    for(int width = 16; width <= MAX_ATLAS; width *= 2) {
      if(widest > width) continue;
      final Map<Integer, Region> slots = new LinkedHashMap<>();
      int x = 0, y = 0, rowHeight = 0;
      for(final Region crop : order) {
        if(x + crop.width() > width) { y += rowHeight; x = rowHeight = 0; }
        slots.put(crop.palette(), new Region(crop.palette(), crop.left(), crop.top(), crop.right(), crop.bottom(), x, y, crop.width(), crop.height()));
        x += crop.width(); rowHeight = Math.max(rowHeight, crop.height());
      }
      final int height = (y + rowHeight + 3) / 4 * 4;
      if(height > MAX_ATLAS) continue;
      if(best == null || better(width, height, best)) best = new Layout(sourceWidth, sourceHeight, width, height, crops.stream().map(crop -> slots.get(crop.palette())).toList());
    }
    if(best == null) throw bad("Materials do not fit the bounded atlas.");
    return best;
  }

  private static boolean better(final int width, final int height, final Layout best) {
    final int area = width * height, previous = best.width() * best.height();
    if(area != previous) return area < previous;
    final int side = Math.max(width, height), previousSide = Math.max(best.width(), best.height());
    return side != previousSide ? side < previousSide : width < best.width();
  }
  private static int ushort(final ByteBuffer data, final int offset) { return Short.toUnsignedInt(data.getShort(offset)); }
  private static long uint(final ByteBuffer data, final int offset) { return Integer.toUnsignedLong(data.getInt(offset)); }
  private static void range(final ByteBuffer data, final long offset, final long length) throws IOException {
    if(offset < 0 || length < 0 || offset > data.limit() || length > data.limit() - offset) throw bad("Truncated model or texture range.");
  }
  private static IOException bad(final String reason) { return new IOException(reason); }
}
