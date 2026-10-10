package charhd;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import legend.definitive.models.TmdGeometryPack;
import legend.definitive.textures.TexturePilot;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.tmd.TmdFaceDetail;
import legend.game.tmd.TmdObjTable1c;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Source-bound character upgrades. Geometry and its new UV material are one optional replacement. */
final class ReconstructedCharacters {
  @FunctionalInterface interface Resources { byte[] read(String path, int limit) throws IOException; }
  private record Detail(String geometry, float[][] uv, ByteBuffer pixels, int width, int height) { }
  private record Part(String baseline, String geometry, byte[] pack, Detail detail) { }
  private final Map<String, Part> parts;

  ReconstructedCharacters() throws IOException {
    this((path, limit) -> {
      try(final var input = ReconstructedCharacters.class.getResourceAsStream("/charhd/characters/" + path)) {
        if(input == null) throw new IOException("Missing character resource: " + path);
        final byte[] bytes = input.readNBytes(limit + 1);
        if(bytes.length > limit) throw new IOException("Character resource exceeds budget");
        return bytes;
      }
    });
  }

  ReconstructedCharacters(final Resources resources) throws IOException {
    try {
      final var manifest = json(resources.read("characters.json", 256 * 1024));
      if(TmdGeometryPack.integer(manifest.get("format")) != 1) throw new IOException("Unknown character manifest format");
      final var entries = manifest.getAsJsonArray("parts");
      if(entries.isEmpty() || entries.size() > 512) throw new IOException("Invalid character part count");
      final Map<String, Part> parts = new HashMap<>();
      final Map<String, ByteBuffer> images = new HashMap<>();
      for(final var row : entries) {
        final var entry = row.getAsJsonObject();
        final String source = hash(entry, "sourceGeometrySha256"), geometry = hash(entry, "geometrySha256");
        final byte[] pack = checked(resources, entry, "pack", 16 * 1024 * 1024);
        Detail detail = null;
        if(entry.has("uv")) {
          final var uv = json(checked(resources, entry, "uv", 2 * 1024 * 1024));
          if(TmdGeometryPack.integer(uv.get("version")) != 1 || !source.equals(hash(uv, "sourceGeometrySha256")) ||
            !geometry.equals(hash(uv, "geometrySha256")) || !hash(entry, "textureSha256").equals(hash(uv, "textureSha256")))
            throw new IOException("Character UV binding does not match geometry/material");
          final var rows = uv.getAsJsonArray("uvs");
          if(rows.isEmpty() || rows.size() > 65535) throw new IOException("Invalid character UV count");
          final float[][] coordinates = new float[rows.size()][2];
          for(int index = 0; index < rows.size(); index++) {
            final var pair = rows.get(index).getAsJsonArray();
            if(pair.size() != 2) throw new IOException("Expected character UV pair");
            for(int component = 0; component < 2; component++) {
              coordinates[index][component] = pair.get(component).getAsFloat();
              if(!Float.isFinite(coordinates[index][component]) || coordinates[index][component] < 0 || coordinates[index][component] > 1)
                throw new IOException("Invalid character UV coordinate");
            }
          }
          final int width = TmdGeometryPack.integer(entry.get("width")), height = TmdGeometryPack.integer(entry.get("height"));
          if(width < 1 || height < 1 || width > 2048 || height > 2048) throw new IOException("Character image exceeds pixel budget");
          final String imageKey = hash(entry, "textureSha256") + ":" + width + ":" + height;
          ByteBuffer pixels = images.get(imageKey);
          if(pixels == null) {
            pixels = pixels(checked(resources, entry, "texture", 8 * 1024 * 1024), width, height);
            images.put(imageKey, pixels);
          }
          detail = new Detail(geometry, coordinates, pixels, width, height);
        }
        if(parts.put(source, new Part(hash(entry, "baselineGeometrySha256"), geometry, pack, detail)) != null)
          throw new IOException("Duplicate character source identity");
      }
      this.parts = Map.copyOf(parts);
    } catch(final RuntimeException malformed) {
      throw new IOException("Malformed character reconstruction manifest", malformed);
    }
  }

  boolean apply(final TmdAppearanceEvent event) throws IOException {
    if(event.appearance != event.geometry || event.source.requiresNativeVertexIndices() || event.geometry.requiresNativeVertexIndices() ||
      event.source.isAuthoredGeometry() || event.geometry.faceDetail() != null) return false;
    final var originals = new TmdObjTable1c[]{event.source};
    final Part part = this.parts.get(TmdGeometryPack.identity(originals));
    if(part == null) return false;
    // Only our recorded ModelsHD baseline may hand off its geometry. Other geometry owners keep priority.
    if(event.geometry != event.source && !part.baseline.equals(TmdGeometryPack.identity(new TmdObjTable1c[]{event.geometry}))) return false;
    final var replacement = TmdGeometryPack.read(new ByteArrayInputStream(part.pack), originals)[0];
    if(!part.geometry.equals(TmdGeometryPack.identity(new TmdObjTable1c[]{replacement}))) throw new IOException("Character geometry binding differs");
    final var appearance = part.detail == null ? replacement : paint(replacement, part.detail);
    event.appearance = appearance;
    return true;
  }

  private static TmdObjTable1c paint(final TmdObjTable1c geometry, final Detail detail) throws IOException {
    if(geometry.n_vert_04 != detail.uv.length) throw new IOException("Character UV count differs from geometry");
    final var primitives = new ArrayList<TmdObjTable1c.Primitive>();
    final float[][] uv = new float[geometry.n_primitive_14][];
    final int[] sources = new int[geometry.n_primitive_14];
    final var surfaces = new legend.core.renderer.SurfaceResponse[geometry.n_primitive_14];
    int rendered = 0;
    for(final var primitive : geometry.primitives_10) for(final byte[] packet : primitive.data()) {
      final int mode = primitive.header() >>> 24, count = (mode & 8) == 0 ? 3 : 4;
      final boolean lit = (mode & 1) == 0;
      int cursor = (mode & 4) != 0 ? count * 4 : 0;
      if((primitive.header() & 0x40000) != 0 || !lit) cursor += count * 4;
      else if((mode & 4) == 0) cursor += 4;
      uv[rendered] = new float[count * 2];
      for(int corner = 0; corner < count; corner++) {
        if(lit && ((mode & 16) != 0 || corner == 0)) cursor += 2;
        final int vertex = (packet[cursor] & 255) | (packet[cursor + 1] & 255) << 8;
        cursor += 2;
        uv[rendered][corner * 2] = detail.uv[vertex][0];
        uv[rendered][corner * 2 + 1] = detail.uv[vertex][1];
      }
      primitives.add(new TmdObjTable1c.Primitive(0, primitive.width(), primitive.header() & ~0x02000000, new byte[][]{packet}));
      sources[rendered] = geometry.sourceFace(rendered);
      surfaces[rendered] = geometry.faceSurface(rendered);
      rendered++;
    }
    final var result = new TmdObjTable1c("CharHD reconstructed character part", geometry.vert_top_00, geometry.normal_top_08,
      primitives.toArray(TmdObjTable1c.Primitive[]::new));
    result.sourceFaces(sources, java.util.Arrays.stream(sources).max().orElseThrow() + 1);
    result.faceSurfaces(surfaces);
    result.surfaceMaterial(geometry.surfaceMaterial());
    result.faceDetail(new TmdFaceDetail(detail.pixels.duplicate(), detail.width, detail.height, uv));
    return result;
  }

  private static JsonObject json(final byte[] bytes) throws IOException {
    final var reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
    reader.setStrictness(Strictness.STRICT);
    reader.setNestingLimit(32);
    final JsonObject value = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(reader, JsonObject.class);
    if(reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing character manifest data");
    return value;
  }

  private static String hash(final JsonObject entry, final String name) throws IOException {
    final String value = entry.get(name).getAsString();
    if(!value.matches("[a-f0-9]{64}")) throw new IOException("Invalid character identity/checksum");
    return value;
  }

  private static byte[] checked(final Resources resources, final JsonObject entry, final String name, final int limit) throws IOException {
    final String path = entry.get(name).getAsString();
    if(!path.matches("[a-z0-9][a-z0-9/._-]*") || path.contains("..")) throw new IOException("Invalid character resource path");
    final byte[] bytes = resources.read(path, limit);
    if(bytes.length > limit || !hash(entry, name + "Sha256").equals(TexturePilot.sha256(bytes))) throw new IOException("Character resource checksum mismatch");
    return bytes;
  }

  private static ByteBuffer pixels(final byte[] png, final int width, final int height) throws IOException {
    try(final var input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
      final var readers = ImageIO.getImageReaders(input);
      if(!readers.hasNext()) throw new IOException("Invalid character image");
      final var reader = readers.next();
      try {
        reader.setInput(input);
        if(!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) != width || reader.getHeight(0) != height)
          throw new IOException("Character image dimensions differ");
        final var image = reader.read(0);
        final var rgba = ByteBuffer.allocateDirect(width * height * 4);
        for(int y = 0; y < height; y++) for(int x = 0; x < width; x++) {
          final int rgb = image.getRGB(x, y);
          rgba.put((byte)(rgb >>> 16)).put((byte)(rgb >>> 8)).put((byte)rgb).put((byte)255);
        }
        return rgba.flip().asReadOnlyBuffer();
      } finally { reader.dispose(); }
    }
  }
}
