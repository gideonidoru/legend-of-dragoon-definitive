// Definitive private atlas preflight (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.materials;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import legend.definitive.textures.TexturePilot;
import legend.definitive.textures.TimImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable private material preflight. Does not upload textures, alter models or activate a pack. */
public final class MaterialAtlas {
  public record Region(int palette, int left, int top, int right, int bottom, int x, int y, int width, int height) { }
  private record Weights(int scale, String bin, String parameters) { }
  private static final List<Weights> WEIGHTS = List.of(
    new Weights(2, "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04"),
    new Weights(4, "713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf", "35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86"),
    new Weights(4, "fe01c269cfd10cdef8e018ab66ebe750cf79c7af4d1f9c16c737e1295229bacc", "2b8fb6e0ae4d2d85704ca08c119a2f5ea40add4f2ecd512eb7f4cd44b6127ed4")
  );
  private final int width, height, scale;
  private final List<Region> regions;
  private final ByteBuffer rgba;
  private final String modelHash, timHash, atlasHash;
  private final long checkedTexels;

  private MaterialAtlas(final SourceMaterials.Layout layout, final int scale, final BufferedImage image, final String modelHash, final String timHash, final String atlasHash) {
    this.width = layout.width(); this.height = layout.height(); this.scale = scale; this.regions = List.copyOf(layout.regions());
    this.modelHash = modelHash; this.timHash = timHash; this.atlasHash = atlasHash;
    this.checkedTexels = this.regions.stream().mapToLong(region -> (long)region.width * region.height).sum();
    final ByteBuffer bytes = ByteBuffer.allocateDirect(this.width * this.height * 4);
    final int[] row = new int[this.width];
    for(int y = 0; y < this.height; y++) {
      image.getRGB(0, y, this.width, 1, row, 0, this.width);
      for(final int pixel : row) bytes.put((byte)(pixel >>> 16)).put((byte)(pixel >>> 8)).put((byte)pixel).put((byte)(pixel >>> 24));
    }
    bytes.flip(); this.rgba = bytes.asReadOnlyBuffer();
  }
  public int width() { return this.width; }
  public int height() { return this.height; }
  public int scale() { return this.scale; }
  public List<Region> regions() { return this.regions; }
  public ByteBuffer rgba() { return this.rgba.asReadOnlyBuffer(); }
  public String modelHash() { return this.modelHash; }
  public String timHash() { return this.timHash; }
  public String atlasHash() { return this.atlasHash; }
  public long checkedTexels() { return this.checkedTexels; }

  public static MaterialAtlas read(final Path folder, final byte[] originalModel, final byte[] originalTim) throws IOException {
    if(originalModel.length > 16 * 1024 * 1024 || originalTim.length > 16 * 1024 * 1024 || Files.isSymbolicLink(folder)) throw bad("Input exceeds atlas bounds or uses a linked folder.");
    return read(readBounded(folder.resolve("manifest.json"), 65536), readBounded(folder.resolve("atlas-engine-stp.png"), 32 * 1024 * 1024), originalModel, originalTim);
  }

  public static MaterialAtlas read(final byte[] manifestBytes, final byte[] png, final byte[] originalModel, final byte[] originalTim) throws IOException {
    if(manifestBytes.length > 65536 || png.length > 32 * 1024 * 1024 || originalModel.length > 16 * 1024 * 1024 || originalTim.length > 16 * 1024 * 1024)
      throw bad("Input exceeds atlas bounds.");
    final byte[] model = originalModel.clone(), tim = originalTim.clone();
    final Map<String, Object> manifest = object(json(manifestBytes));
    final String modelHash = TexturePilot.sha256(model), timHash = TexturePilot.sha256(tim);
    if(!"definitive-private-material-pack-1".equals(manifest.get("pipeline")) || !modelHash.equals(manifest.get("modelSha256")) || !timHash.equals(manifest.get("timSha256"))) throw bad("Atlas does not match the original model and TIM.");
    final int scale = integer(manifest.get("scale"));
    final SourceMaterials.Layout layout = SourceMaterials.layout(model, tim, scale);
    if(integer(manifest.get("paddingSourceTexels")) != 8 || !integers(manifest.get("atlasSize")).equals(List.of(layout.width(), layout.height()))) throw bad("Atlas size or padding differs from the reproducible layout.");
    final List<?> entries = array(manifest.get("materials"));
    if(entries.size() != layout.regions().size()) throw bad("Requires the complete material map.");
    final Set<Integer> used = new HashSet<>();
    for(int i = 0; i < entries.size(); i++) {
      final Map<String, Object> entry = object(entries.get(i));
      final Region region = layout.regions().get(i);
      if(!entry.keySet().equals(Set.of("palette", "sourceCrop", "atlasRect")) || integer(entry.get("palette")) != region.palette || !integers(entry.get("sourceCrop")).equals(List.of(region.left, region.top, region.right, region.bottom)) || !integers(entry.get("atlasRect")).equals(List.of(region.x, region.y, region.width, region.height))) throw bad("Material map differs from the original UV layout.");
      used.add(region.palette);
    }
    final List<Integer> preserved = integers(manifest.getOrDefault("preservedPalettes", List.of()));
    if(new HashSet<>(preserved).size() != preserved.size() || !used.containsAll(preserved)) throw bad("Invalid preserved palette selection.");
    final Object algorithm = manifest.get("algorithm");
    if(!"nearest".equals(algorithm) && !"neural".equals(algorithm) && !"authored".equals(algorithm)) throw bad("Unsupported material processing description.");
    if("authored".equals(algorithm)) {
      final var provenance = object(manifest.get("authoring"));
      for(final String key : List.of("baseAtlasSha256", "generatedImageSha256", "promptSha256")) {
        if(!(provenance.get(key) instanceof String hash) || !hash.matches("[0-9a-f]{64}")) throw bad("Missing authored material provenance.");
      }
    }
    if("neural".equals(algorithm)) {
      if(!(manifest.get("strength") instanceof Number strength) || !Double.isFinite(strength.doubleValue()) || strength.doubleValue() < 0 || strength.doubleValue() > 1) throw bad("Invalid neural strength.");
      if(WEIGHTS.stream().noneMatch(weights -> weights.scale == scale && weights.bin.equals(manifest.get("weightsSha256")) && weights.parameters.equals(manifest.get("paramsSha256")))) throw bad("Neural weights differ from the pinned comparison models.");
    }
    final String atlasHash = TexturePilot.sha256(png);
    final ByteBuffer header = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
    if(png.length < 33 || header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452 || header.getInt(16) != layout.width() || header.getInt(20) != layout.height() || png[24] != 8 || png[25] != 6 || !atlasHash.equals(manifest.get("atlasEngineSha256"))) throw bad("Requires the matching bounded 8-bit RGBA atlas PNG.");
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    if(image == null || image.getWidth() != layout.width() || image.getHeight() != layout.height()) throw bad("Invalid atlas PNG.");
    for(final Region region : layout.regions()) {
      final TimImage source = TimImage.read(tim, region.palette);
      for(int y = 0; y < region.height; y++) {
        final int sy = Math.clamp(region.top + y / scale, 0, source.height() - 1);
        for(int x = 0; x < region.width; x++) {
          final int sx = Math.clamp(region.left + x / scale, 0, source.width() - 1);
          final int expected = source.pixels()[sy * source.width() + sx], actual = image.getRGB(region.x + x, region.y + y);
          if(expected >>> 24 != actual >>> 24 || (expected == 0) != (actual == 0) || (expected & 0xffffff) == 0 && (actual & 0xffffff) != 0) throw bad("STP, discard coverage or visible original black changed.");
          if(("nearest".equals(algorithm) || preserved.contains(region.palette)) && expected != actual) throw bad("Pixels changed in an original or preserved material.");
        }
      }
    }
    // Unmapped packing gaps cannot carry hidden content into a future native experiment.
    final boolean[] occupied = new boolean[layout.width()];
    final int[] row = new int[layout.width()];
    for(int y = 0; y < layout.height(); y++) {
      Arrays.fill(occupied, false);
      for(final Region region : layout.regions()) if(y >= region.y && y < region.y + region.height) Arrays.fill(occupied, region.x, region.x + region.width, true);
      image.getRGB(0, y, layout.width(), 1, row, 0, layout.width());
      for(int x = 0; x < row.length; x++) if(!occupied[x] && row[x] != 0) throw bad("Unmapped atlas padding must remain all-zero.");
    }
    return new MaterialAtlas(layout, scale, image, modelHash, timHash, atlasHash);
  }

  static byte[] readBounded(final Path path, final int limit) throws IOException {
    final var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if(!attributes.isRegularFile() || attributes.size() > limit) throw bad("Non-regular or oversized atlas input.");
    try(final var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
      final byte[] data = input.readNBytes(limit + 1);
      if(data.length > limit) throw bad("Atlas input exceeds its limit.");
      return data;
    }
  }

  private static Object json(final byte[] bytes) throws IOException {
    try(final JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      reader.setStrictness(Strictness.STRICT); reader.setNestingLimit(16);
      final Object result = value(reader, new int[]{4096});
      if(reader.peek() != JsonToken.END_DOCUMENT) throw bad("Trailing manifest content.");
      return result;
    } catch(final NumberFormatException | IllegalStateException e) { throw new IOException("Invalid atlas manifest.", e); }
  }
  private static Object value(final JsonReader reader, final int[] remaining) throws IOException {
    if(--remaining[0] < 0) throw bad("Too many manifest fields.");
    switch(reader.peek()) {
      case BEGIN_OBJECT -> {
        final Map<String, Object> result = new HashMap<>(); reader.beginObject();
        while(reader.hasNext()) {
          final String key = reader.nextName();
          if(result.containsKey(key)) throw bad("Duplicate manifest field.");
          result.put(key, value(reader, remaining));
        }
        reader.endObject(); return result;
      }
      case BEGIN_ARRAY -> {
        final List<Object> result = new ArrayList<>(); reader.beginArray();
        while(reader.hasNext()) result.add(value(reader, remaining));
        reader.endArray(); return result;
      }
      case STRING -> { return reader.nextString(); }
      case NUMBER -> {
        final String text = reader.nextString();
        if(text.matches("-?(0|[1-9][0-9]*)")) return Long.valueOf(text);
        final double number = Double.parseDouble(text);
        if(!Double.isFinite(number)) throw bad("Nonfinite manifest number.");
        return number;
      }
      case BOOLEAN -> { return reader.nextBoolean(); }
      case NULL -> { reader.nextNull(); return null; }
      default -> throw bad("Invalid manifest value.");
    }
  }
  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(final Object value) throws IOException {
    if(!(value instanceof Map)) throw bad("Expected a manifest object.");
    return (Map<String, Object>)value;
  }
  private static List<?> array(final Object value) throws IOException {
    if(!(value instanceof List<?> list)) throw bad("Expected a manifest array.");
    return list;
  }
  private static int integer(final Object value) throws IOException {
    if(!(value instanceof Long number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw bad("Expected an exact bounded integer.");
    return number.intValue();
  }
  private static List<Integer> integers(final Object value) throws IOException {
    final List<Integer> result = new ArrayList<>();
    for(final Object entry : array(value)) result.add(integer(entry));
    return result;
  }
  private static IOException bad(final String reason) { return new IOException(reason); }
}
