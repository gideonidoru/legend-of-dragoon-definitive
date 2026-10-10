// ModelsHD optional geometry adapter (2026-10-10), AGPL v3; see LICENSE.
package modelshd;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import legend.game.tmd.TmdObjTable1c;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Geometry only: original runtime packets supply palette, page and blend semantics. */
public final class ModelPack {
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private ModelPack() { }
  record Face(int header, byte[] packet) { }

  public static String identity(final TmdObjTable1c[] parts) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final ByteBuffer value = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
      put(digest, value, parts.length);
      for(final TmdObjTable1c part : parts) {
        put(digest, value, part.scale_18);
        for(final Vector3f[] vectors : new Vector3f[][] {part.vert_top_00, part.normal_top_08}) {
          put(digest, value, vectors.length);
          for(final Vector3f vector : vectors) {
            put(digest, value, Float.floatToIntBits(vector.x));
            put(digest, value, Float.floatToIntBits(vector.y));
            put(digest, value, Float.floatToIntBits(vector.z));
          }
        }
        final List<Face> faces = faces(part);
        put(digest, value, faces.size());
        for(final Face face : faces) {
          put(digest, value, face.header & 0xff04_0000);
          // UV/CLUT/page relocation does not change geometry identity.
          final int mode = face.header >>> 24;
          final int corners = (mode & 8) == 0 ? 3 : 4;
          final boolean lit = (mode & 1) == 0;
          final boolean shaded = (face.header & 0x40000) != 0;
          int cursor = (mode & 4) != 0 ? corners * 4 : 0;
          if(shaded || !lit) cursor += corners * 4;
          else if((mode & 4) == 0) cursor += 4;
          for(int i = 0; i < corners; i++) {
            if(lit && ((mode & 16) != 0 || i == 0)) { put(digest, value, u16(face.packet, cursor)); cursor += 2; }
            put(digest, value, u16(face.packet, cursor)); cursor += 2;
          }
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
  }

  private static void put(final MessageDigest digest, final ByteBuffer value, final int number) {
    value.clear(); value.putInt(number); digest.update(value.array());
  }

  public static TmdObjTable1c[] read(final Path path, final TmdObjTable1c[] originals) throws IOException {
    return read(Files.newInputStream(path), originals);
  }

  /** Takes ownership of the stream; the same strict reader serves bundled and authored packs. */
  public static TmdObjTable1c[] read(final InputStream input, final TmdObjTable1c[] originals) throws IOException {
    try(var stream = input) {
      final byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
      if(bytes.length > MAX_BYTES) throw new IOException("Model pack exceeds 16 MiB");
      final JsonReader reader = new JsonReader(new java.io.StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
      reader.setStrictness(Strictness.STRICT);
      reader.setNestingLimit(32);
      final JsonObject root = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(reader, JsonObject.class);
      if(reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing model pack data");
      if(integer(root.get("version")) != 1 || !identity(originals).equals(root.get("sourceGeometrySha256").getAsString())) {
        throw new IOException("Model pack source or version does not match");
      }
      final JsonArray parts = root.getAsJsonArray("parts");
      if(parts.size() != originals.length || parts.size() == 0 || parts.size() > 64) throw new IOException("Model pack must supply every original animation part");
      final TmdObjTable1c[] result = new TmdObjTable1c[parts.size()];
      int vertices = 0, polygons = 0;
      for(int i = 0; i < result.length; i++) {
        if(originals[i].scale_18 != 0) throw new IOException("Scaled source parts are not supported");
        final JsonObject part = parts.get(i).getAsJsonObject();
        final Vector3f[] points = vectors(part.getAsJsonArray("vertices"), false);
        final Vector3f[] normals = vectors(part.getAsJsonArray("normals"), true);
        vertices += points.length;
        final JsonArray definitions = part.getAsJsonArray("faces");
        polygons += definitions.size();
        if(vertices > 200000 || polygons > 50000 || definitions.size() == 0) throw new IOException("Model geometry budget exceeded");
        final List<Face> source = faces(originals[i]);
        final TmdObjTable1c.Primitive[] primitives = new TmdObjTable1c.Primitive[definitions.size()];
        final legend.core.renderer.SurfaceResponse[] surfaces = new legend.core.renderer.SurfaceResponse[definitions.size()];
        for(int f = 0; f < primitives.length; f++) {
          final JsonObject face = definitions.get(f).getAsJsonObject();
          final int sourceIndex = integer(face.get("sourceFace"));
          if(sourceIndex < 0 || sourceIndex >= source.size()) throw new IOException("Unknown source material face");
          primitives[f] = packet(source.get(sourceIndex), face, points.length, normals.length);
          final JsonObject material = face.has("material") ? face.getAsJsonObject("material") : part.has("material") ? part.getAsJsonObject("material") : null;
          if(material != null) {
            final var type = legend.core.renderer.SurfaceMaterial.valueOf(material.get("surface").getAsString().toUpperCase(java.util.Locale.ROOT));
            if(material.has("roughness") && (!material.get("roughness").isJsonPrimitive() || !material.get("roughness").getAsJsonPrimitive().isNumber())) throw new IOException("Roughness must be numeric");
            surfaces[f] = material.has("roughness") ? new legend.core.renderer.SurfaceResponse(type, material.get("roughness").getAsFloat()) : new legend.core.renderer.SurfaceResponse(type);
          } else surfaces[f] = originals[i].faceSurface(sourceIndex);
        }
        result[i] = new TmdObjTable1c("ModelsHD part " + i, points, normals, primitives);
        result[i].faceSurfaces(surfaces);
        if(part.has("surface")) {
          result[i].surfaceMaterial(legend.core.renderer.SurfaceMaterial.valueOf(part.get("surface").getAsString().toUpperCase(java.util.Locale.ROOT)));
        }
      }
      return result;
    } catch(final RuntimeException e) {
      throw new IOException("Invalid ModelsHD geometry: " + e.getMessage(), e);
    }
  }

  private static int integer(final com.google.gson.JsonElement value) throws IOException {
    if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Expected numeric integer");
    final double number = value.getAsDouble();
    if(!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IOException("Invalid integer");
    return (int)number;
  }

  private static Vector3f[] vectors(final JsonArray rows, final boolean normals) throws IOException {
    if(rows.size() == 0 || rows.size() > 65535) throw new IOException("Invalid vector count");
    final Vector3f[] result = new Vector3f[rows.size()];
    for(int i = 0; i < result.length; i++) {
      final JsonArray row = rows.get(i).getAsJsonArray();
      if(row.size() != 3) throw new IOException("Expected XYZ vector");
      final Vector3f vector = new Vector3f(row.get(0).getAsFloat(), row.get(1).getAsFloat(), row.get(2).getAsFloat());
      if(!vector.isFinite() || vector.lengthSquared() > 1e9f || normals && Math.abs(vector.length() - 1) > .01f) throw new IOException("Nonfinite, excessive or invalid normal vector");
      result[i] = vector;
    }
    return result;
  }

  private static List<Face> faces(final TmdObjTable1c table) {
    final List<Face> result = new ArrayList<>();
    for(final var primitive : table.primitives_10) for(final byte[] packet : primitive.data()) result.add(new Face(primitive.header(), packet));
    return result;
  }

  private static int u16(final byte[] bytes, final int offset) {
    return (bytes[offset] & 255) | (bytes[offset + 1] & 255) << 8;
  }

  /** Copy runtime material words; interpolate only source corner UVs and vertex colours. */
  private static TmdObjTable1c.Primitive packet(final Face source, final JsonObject face, final int points, final int normals) throws IOException {
    final int mode = source.header >>> 24;
    if((mode >>> 5 & 3) != 1) throw new IOException("Unsupported source polygon");
    final boolean textured = (mode & 4) != 0, lit = (mode & 1) == 0, shaded = (source.header & 0x40000) != 0;
    if(textured && shaded || !textured && !lit) throw new IOException("Unsupported source lighting");
    final int oldCount = (mode & 8) == 0 ? 3 : 4;
    final JsonArray refs = face.getAsJsonArray("vertices"), nr = face.getAsJsonArray("normals"), weights = face.getAsJsonArray("sourceWeights");
    final int count = refs.size();
    if((count != 3 && count != 4) || nr.size() != count || weights.size() != count) throw new IOException("Expected triangle or quad corner attributes");
    final double[][] bary = new double[count][oldCount];
    for(int i = 0; i < count; i++) {
      final JsonArray row = weights.get(i).getAsJsonArray();
      if(row.size() != oldCount) throw new IOException("Source corner mapping differs");
      double sum = 0;
      for(int j = 0; j < oldCount; j++) {
        final double w = row.get(j).getAsDouble();
        if(!Double.isFinite(w) || w < 0 || w > 1) throw new IOException("Invalid source corner weight");
        bary[i][j] = w; sum += w;
      }
      if(Math.abs(sum - 1) > 1e-6) throw new IOException("Source corner weights must sum to one");
    }
    final int sourceColour = textured ? oldCount * 4 : 0;
    final int colourCount = shaded || !lit ? count : textured ? 0 : 1;
    final int prefix = (textured ? count * 4 : 0) + colourCount * 4;
    final int size = (prefix + count * (lit ? 4 : 2) + 3) / 4 * 4;
    final ByteBuffer data = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    if(textured) for(int i = 0; i < count; i++) {
      for(int component = 0; component < 2; component++) {
        double uv = 0;
        for(int j = 0; j < oldCount; j++) uv += bary[i][j] * (source.packet[j * 4 + component] & 255);
        data.put((byte)Math.round(uv));
      }
      // Palette and texture page including STP blend bits are copied exactly.
      data.putShort((short)(i < 2 ? u16(source.packet, i * 4 + 2) : 0));
    }
    for(int i = 0; i < colourCount; i++) {
      for(int component = 0; component < 3; component++) {
        double color = 0;
        for(int j = 0; j < oldCount; j++) color += bary[i][j] * (source.packet[sourceColour + ((shaded || !lit) ? j * 4 : 0) + component] & 255);
        data.put((byte)Math.round(color));
      }
      double alpha = 0;
      for(int j = 0; j < oldCount; j++) alpha += bary[i][j] * (source.packet[sourceColour + ((shaded || !lit) ? j * 4 : 0) + 3] & 255);
      data.put((byte)Math.round(alpha));
    }
    for(int i = 0; i < count; i++) {
      final int vertex = integer(refs.get(i)), normal = integer(nr.get(i));
      if(vertex < 0 || vertex >= points || normal < 0 || normal >= normals) throw new IOException("Geometry index is out of range");
      if(lit) data.putShort((short)normal);
      data.putShort((short)vertex);
    }
    final int newMode = (mode & ~8) | (count == 4 ? 8 : 0) | (lit ? 16 : 0);
    final int header = newMode << 24 | (source.header & 0x40000) | size / 4 << 8 | 1;
    return new TmdObjTable1c.Primitive(0, size, header, new byte[][] {data.array()});
  }
}
