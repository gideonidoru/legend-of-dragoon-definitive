package legend.definitive.artwork;

import legend.game.textures.Image;
import legend.definitive.textures.TexturePilot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Reconstructs the static continent materials from the complete ordered upload. */
public final class WorldTerrainSource {
  public record Material(int page, int clut, List<Integer> parts, int u, int v, Image image, String fingerprint) {
    public boolean held() { return this.image == null; }
    public boolean uniform() {
      if(this.held()) return false;
      for(int p = 4; p < this.image.data.length; p++) if(this.image.data[p] != this.image.data[p % 4]) return false;
      return true;
    }
  }
  public record Scene(String modelHash, String bankHash, int parts, List<Material> materials) { }
  private record Rect(int x, int y, int w, int h, short[] words) { }
  private record Tim(int bpp, Rect image) { }
  private static final class References {
    final TreeSet<Integer> parts = new TreeSet<>();
    final List<int[]> uv = new ArrayList<>();
  }
  private WorldTerrainSource() { }

  public static Scene decode(final byte[] model, final List<byte[]> bank) throws IOException {
    if(model.length > 1024 * 1024 || bank.isEmpty() || bank.size() > 256 || bank.stream().anyMatch(b -> b.length > 1024 * 1024) || bank.stream().mapToLong(b -> b.length).sum() > 16 * 1024 * 1024) throw bad("World source exceeds bounds");
    try {
      final short[] vram = new short[1024 * 512];
      final boolean[] written = new boolean[vram.length];
      final List<Tim> tims = new ArrayList<>();
      final MessageDigest hash = MessageDigest.getInstance("SHA-256");
      for(int i = 0; i < bank.size(); i++) {
        final byte[] data = bank.get(i);
        hash.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(i).putInt(data.length).array()); hash.update(data);
        if(data.length == 0) continue;
        final ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        final int flags = bytes.getInt(4);
        if(bytes.getInt(0) != 16 || flags != 8 && flags != 9) throw bad("Unsupported world TIM");
        final Rect palette = block(bytes, 8), image = block(bytes, 8 + bytes.getInt(8));
        if(8 + bytes.getInt(8) + 12 + image.words.length * 2 != data.length) throw bad("Trailing TIM content");
        upload(image, vram, written); upload(palette, vram, written);
        tims.add(new Tim(flags & 3, image));
      }
      final ByteBuffer bytes = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN);
      final int parts = bytes.getInt(8);
      if(bytes.getInt(0) != 65 || bytes.getInt(4) != 0 || parts < 2 || parts > 128 || 12 + parts * 28 > model.length) throw bad("Unsupported world TMD");
      final Map<Integer, References> references = new LinkedHashMap<>();
      for(int part = 1; part < parts; part++) {
        final int entry = 12 + part * 28, count = bytes.getInt(entry + 20);
        final long start = 12L + Integer.toUnsignedLong(bytes.getInt(entry + 16));
        if(count < 0 || count > 100000 || start > model.length) throw bad("Invalid world primitives");
        int offset = (int)start;
        for(int face = 0; face < count; face++) {
          final int header = bytes.getInt(offset), mode = header >>> 24, length = (header >>> 8 & 255) * 4;
          if(length == 0 || (long)offset + 4 + length > model.length) throw bad("Truncated world primitive");
          if((mode & 4) != 0) {
            final int vertices = (mode & 8) != 0 ? 4 : 3;
            if((mode & 0xe0) != 0x20 || length < vertices * 4) throw bad("Unsupported world polygon");
            final int page = Short.toUnsignedInt(bytes.getShort(offset + 10)) & 0x19f, clut = Short.toUnsignedInt(bytes.getShort(offset + 6));
            if((page >>> 7 & 3) > 1) throw bad("Unsupported world BPP");
            final References refs = references.computeIfAbsent(page << 16 | clut, ignored -> new References());
            refs.parts.add(part);
            for(int j = 0; j < vertices; j++) refs.uv.add(new int[]{Byte.toUnsignedInt(model[offset + 4 + j * 4]), Byte.toUnsignedInt(model[offset + 5 + j * 4])});
          }
          offset += 4 + length;
        }
      }
      final List<Material> materials = new ArrayList<>();
      for(final int key : references.keySet().stream().sorted().toList()) {
        final int page = key >>> 16, clut = key & 65535, divisor = (page >>> 7 & 3) == 0 ? 4 : 2;
        final int px = (page & 15) * 64, py = (page & 16) * 16;
        final References refs = references.get(key);
        final List<Tim> matches = tims.stream().filter(t -> t.bpp == (page >>> 7 & 3) && refs.uv.stream().allMatch(uv ->
          px + uv[0] / divisor >= t.image.x && px + uv[0] / divisor < t.image.x + t.image.w && py + uv[1] >= t.image.y && py + uv[1] < t.image.y + t.image.h)).toList();
        if(matches.size() != 1) { materials.add(new Material(page, clut, List.copyOf(refs.parts), 0, 0, null, null)); continue; }
        final Rect rect = matches.getFirst().image;
        final int cx = (clut & 63) * 16, cy = clut >>> 6, colours = divisor == 4 ? 16 : 256;
        if(cy >= 512 || cx + colours > 1024) throw bad("World CLUT outside VRAM");
        for(int c = 0; c < colours; c++) if(!written[cy * 1024 + cx + c]) throw bad("World CLUT was not uploaded");
        final byte[] rgba = new byte[rect.w * divisor * rect.h * 4];
        for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w * divisor; x++) {
          final int address = (rect.y + y) * 1024 + rect.x + x / divisor;
          if(!written[address]) throw bad("World image was not uploaded");
          final int bits = divisor == 4 ? 4 : 8;
          final int index = Short.toUnsignedInt(vram[address]) >>> (x % divisor * bits) & ((1 << bits) - 1);
          final int colour = Short.toUnsignedInt(vram[cy * 1024 + cx + index]), p = (y * rect.w * divisor + x) * 4;
          rgba[p] = (byte)((colour & 31) * 255 / 31); rgba[p + 1] = (byte)((colour >>> 5 & 31) * 255 / 31);
          rgba[p + 2] = (byte)((colour >>> 10 & 31) * 255 / 31); rgba[p + 3] = (byte)((colour >>> 15) * 255);
        }
        final Image image = new Image(rgba, rect.w * divisor, rect.h);
        materials.add(new Material(page, clut, List.copyOf(refs.parts), (rect.x - px) * divisor, rect.y - py, image, SkySource.fingerprint(image)));
      }
      return new Scene(TexturePilot.sha256(model), HexFormat.of().formatHex(hash.digest()), parts, List.copyOf(materials));
    } catch(final IndexOutOfBoundsException | NoSuchAlgorithmException failure) { throw new IOException("Invalid world source", failure); }
  }

  private static Rect block(final ByteBuffer bytes, final int offset) throws IOException {
    if(offset < 8 || (long)offset + 12 > bytes.limit()) throw bad("Truncated TIM block");
    final int size = bytes.getInt(offset), x = Short.toUnsignedInt(bytes.getShort(offset + 4)), y = Short.toUnsignedInt(bytes.getShort(offset + 6));
    final int w = Short.toUnsignedInt(bytes.getShort(offset + 8)), h = Short.toUnsignedInt(bytes.getShort(offset + 10));
    if(w == 0 || h == 0 || x + w > 1024 || y + h > 512 || size != 12L + w * h * 2L || (long)offset + size > bytes.limit()) throw bad("Invalid TIM block");
    final short[] words = new short[w * h];
    for(int i = 0; i < words.length; i++) words[i] = bytes.getShort(offset + 12 + i * 2);
    return new Rect(x, y, w, h, words);
  }
  private static void upload(final Rect rect, final short[] vram, final boolean[] written) {
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      final int p = (rect.y + y) * 1024 + rect.x + x; vram[p] = rect.words[y * rect.w + x]; written[p] = true;
    }
  }
  private static IOException bad(final String message) { return new IOException(message); }
}
