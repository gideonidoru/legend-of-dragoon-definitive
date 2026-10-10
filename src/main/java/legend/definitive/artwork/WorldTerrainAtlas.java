package legend.definitive.artwork;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import legend.game.textures.Image;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, source-verified static world atlas; mixed native faces remain indexed. */
public final class WorldTerrainAtlas implements TextureUv {
  public record Region(int page, int clut, int u, int v, int x, int y, int width, int height, String fingerprint, List<Integer> parts) { }
  private final int width, height, parts;
  private final String modelHash, bankHash;
  private final List<Region> regions;
  private final Map<Integer, Region> mappings;
  private final ByteBuffer rgba;

  private WorldTerrainAtlas(final Image image, final WorldTerrainSource.Scene scene, final List<Region> regions) {
    this.width = image.width; this.height = image.height; this.parts = scene.parts();
    this.modelHash = scene.modelHash(); this.bankHash = scene.bankHash();
    this.regions = List.copyOf(regions); this.mappings = new HashMap<>();
    for(final Region region : regions) this.mappings.put(region.page << 16 | region.clut, region);
    this.rgba = ByteBuffer.wrap(image.data.clone()).asReadOnlyBuffer();
  }
  public int width() { return this.width; }
  public int height() { return this.height; }
  public int parts() { return this.parts; }
  public String modelHash() { return this.modelHash; }
  public String bankHash() { return this.bankHash; }
  public List<Region> regions() { return this.regions; }
  public ByteBuffer rgba() { return this.rgba.asReadOnlyBuffer(); }
  public boolean replacesPart(final int part) { return this.regions.stream().anyMatch(r -> r.parts.contains(part)); }

  @Override public float[] map(final int page, final int clut, final float u, final float v) {
    final Region region = this.mappings.get((page & 0x19f) << 16 | clut);
    if(region == null) return null;
    if(!Float.isFinite(u) || !Float.isFinite(v) || u < region.u || v < region.v || u >= region.u + region.width / 4.0f || v >= region.v + region.height / 4.0f) throw new IllegalArgumentException("Terrain UV outside source rectangle");
    return new float[]{(region.x + (u - region.u) * 4) / this.width, (region.y + (v - region.v) * 4) / this.height};
  }

  public static WorldTerrainAtlas load(final Class<?> owner, final String base, final byte[] model, final List<byte[]> bank) throws IOException {
    return read(ArtworkResources.read(owner, base + "/manifest.json", 65536), ArtworkResources.read(owner, base + "/atlas-v1.png", 32 * 1024 * 1024), model, bank);
  }
  public static WorldTerrainAtlas read(final byte[] manifest, final byte[] png, final byte[] model, final List<byte[]> bank) throws IOException {
    if(manifest.length > 65536 || png.length > 32 * 1024 * 1024) throw bad("Oversized world atlas");
    final Map<String, Object> metadata = object(json(manifest));
    if(!metadata.keySet().equals(Set.of("schema", "pipeline", "modelSha256", "bankSha256", "scale", "paddingSourceTexels", "atlasSize", "atlasSha256", "materials")) || integer(metadata.get("schema")) != 1 || !"envhd-static-world-atlas-1".equals(metadata.get("pipeline")) || integer(metadata.get("scale")) != 4 || integer(metadata.get("paddingSourceTexels")) != 8) throw bad("Unsupported world atlas contract");
    final WorldTerrainSource.Scene source = WorldTerrainSource.decode(model, bank);
    if(!source.modelHash().equals(metadata.get("modelSha256")) || !source.bankHash().equals(metadata.get("bankSha256"))) throw bad("World atlas source differs");
    final Map<String, WorldTerrainSource.Material> unique = new HashMap<>();
    for(final var material : source.materials()) if(!material.held() && !material.uniform()) unique.putIfAbsent(material.fingerprint(), material);
    final Map<String, int[]> placements = new LinkedHashMap<>();
    int x = 0, y = 0, shelf = 0;
    for(final var material : unique.values().stream().sorted(Comparator.<WorldTerrainSource.Material>comparingInt(m -> m.image().height).reversed().thenComparing(Comparator.comparingInt((WorldTerrainSource.Material m) -> m.image().width).reversed()).thenComparing(WorldTerrainSource.Material::fingerprint)).toList()) {
      final int w = (material.image().width + 16) * 4, h = (material.image().height + 16) * 4;
      if(w > 4096 || h > 4096) throw bad("World material exceeds atlas bounds");
      if(x + w > 4096) { x = 0; y += shelf; shelf = 0; }
      placements.put(material.fingerprint(), new int[]{x + 32, y + 32, material.image().width * 4, material.image().height * 4});
      x += w; shelf = Math.max(shelf, h);
    }
    final int height = Math.max(4, y + shelf);
    if(height > 4096 || !integers(metadata.get("atlasSize")).equals(List.of(4096, height))) throw bad("World atlas layout differs");
    final List<?> entries = array(metadata.get("materials"));
    if(entries.size() != source.materials().size()) throw bad("Incomplete world material map");
    final List<Region> regions = new ArrayList<>();
    for(int i = 0; i < entries.size(); i++) {
      final var material = source.materials().get(i);
      final Map<String, Object> entry = object(entries.get(i));
      final String status = material.held() ? "held-native-uv-mapping" : material.uniform() ? "retain-native-uniform" : "hd";
      final Set<String> fields = material.held() ? Set.of("page", "clut", "status") : material.uniform() ? Set.of("page", "clut", "status", "decodedRgbaSha256", "sourceSize", "sourceUvOrigin") : Set.of("page", "clut", "status", "decodedRgbaSha256", "sourceSize", "sourceUvOrigin", "atlasRect");
      if(!entry.keySet().equals(fields) || integer(entry.get("page")) != material.page() || integer(entry.get("clut")) != material.clut() || !status.equals(entry.get("status"))) throw bad("World material identity differs");
      if(material.held()) continue;
      if(!material.fingerprint().equals(entry.get("decodedRgbaSha256")) || !integers(entry.get("sourceSize")).equals(List.of(material.image().width, material.image().height)) || !integers(entry.get("sourceUvOrigin")).equals(List.of(material.u(), material.v()))) throw bad("World decoded source differs");
      if(material.uniform()) continue;
      final int[] rect = placements.get(material.fingerprint());
      if(!integers(entry.get("atlasRect")).equals(Arrays.stream(rect).boxed().toList())) throw bad("World packed rectangle differs");
      regions.add(new Region(material.page(), material.clut(), material.u(), material.v(), rect[0], rect[1], rect[2], rect[3], material.fingerprint(), material.parts()));
    }
    ArtworkResources.hash(png, string(metadata.get("atlasSha256")));
    // STP requires RGBA even when a particular scene happens to be fully visible.
    if(png.length < 26 || png[25] != 6) throw bad("World atlas requires 8-bit RGBA");
    final Image image = ArtworkResources.image(png, 4096, height);
    for(final var entry : placements.entrySet()) {
      final int[] rect = entry.getValue(); final Image original = unique.get(entry.getKey()).image();
      for(int dy = -32; dy < rect[3] + 32; dy++) for(int dx = -32; dx < rect[2] + 32; dx++) {
        final int sx = Math.clamp(dx / 4, 0, original.width - 1), sy = Math.clamp(dy / 4, 0, original.height - 1);
        final int p = ((rect[1] + dy) * image.width + rect[0] + dx) * 4, q = (sy * original.width + sx) * 4;
        final boolean blackSource = black(original.data, q), blackOutput = black(image.data, p);
        if(original.data[q + 3] != image.data[p + 3] || blackSource != blackOutput) throw bad("World STP/discard/visible-black changed");
        if(dx < 0 || dy < 0 || dx >= rect[2] || dy >= rect[3]) {
          final int inner = ((rect[1] + Math.clamp(dy, 0, rect[3] - 1)) * image.width + rect[0] + Math.clamp(dx, 0, rect[2] - 1)) * 4;
          for(int c = 0; c < 4; c++) if(image.data[p + c] != image.data[inner + c]) throw bad("World padding must clamp its own material");
        }
      }
    }
    final boolean[] occupied = new boolean[image.width];
    for(int row = 0; row < image.height; row++) {
      Arrays.fill(occupied, false);
      for(final int[] rect : placements.values()) if(row >= rect[1] - 32 && row < rect[1] + rect[3] + 32) Arrays.fill(occupied, rect[0] - 32, rect[0] + rect[2] + 32, true);
      for(int col = 0; col < image.width; col++) if(!occupied[col]) {
        final int p = (row * image.width + col) * 4;
        if(!black(image.data, p) || image.data[p + 3] != 0) throw bad("World atlas packing gap is not empty");
      }
    }
    return new WorldTerrainAtlas(image, source, regions);
  }
  private static boolean black(final byte[] data, final int p) { return (data[p] | data[p + 1] | data[p + 2]) == 0; }
  private static Object json(final byte[] bytes) throws IOException {
    try(final JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      reader.setStrictness(Strictness.STRICT); reader.setNestingLimit(16);
      final Object value = value(reader, new int[]{4096});
      if(reader.peek() != JsonToken.END_DOCUMENT) throw bad("Trailing world manifest content");
      return value;
    } catch(final IllegalStateException | NumberFormatException failure) { throw new IOException("Invalid world manifest", failure); }
  }
  private static Object value(final JsonReader reader, final int[] budget) throws IOException {
    if(--budget[0] < 0) throw bad("World manifest exceeds field budget");
    switch(reader.peek()) {
      case BEGIN_OBJECT -> {
        final Map<String, Object> map = new HashMap<>(); reader.beginObject();
        while(reader.hasNext()) { final String key = reader.nextName(); if(map.containsKey(key)) throw bad("Duplicate world manifest key"); map.put(key, value(reader, budget)); }
        reader.endObject(); return map;
      }
      case BEGIN_ARRAY -> { final List<Object> list = new ArrayList<>(); reader.beginArray(); while(reader.hasNext()) list.add(value(reader, budget)); reader.endArray(); return list; }
      case STRING -> { return reader.nextString(); }
      case NUMBER -> { final String number = reader.nextString(); if(!number.matches("-?(0|[1-9][0-9]*)")) throw bad("Requires exact world manifest integer"); return Long.valueOf(number); }
      default -> throw bad("Unsupported world manifest value");
    }
  }
  @SuppressWarnings("unchecked") private static Map<String, Object> object(final Object value) throws IOException {
    if(!(value instanceof Map<?, ?>)) throw bad("Expected world manifest object"); return (Map<String, Object>)value;
  }
  private static List<?> array(final Object value) throws IOException { if(!(value instanceof List<?> list)) throw bad("Expected world manifest array"); return list; }
  private static String string(final Object value) throws IOException { if(!(value instanceof String text)) throw bad("Expected world manifest string"); return text; }
  private static int integer(final Object value) throws IOException { if(!(value instanceof Long number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw bad("World manifest integer outside bounds"); return number.intValue(); }
  private static List<Integer> integers(final Object value) throws IOException { final List<Integer> result = new ArrayList<>(); for(final Object item : array(value)) result.add(integer(item)); return result; }
  private static IOException bad(final String message) { return new IOException(message); }
}
