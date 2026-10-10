package legend.definitive.rendering;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Artist-supplied world-space direction toward the light; no image-based inference. */
public record EnvironmentLight(float x, float y, float z, float r, float g, float b,
                               float ambientR, float ambientG, float ambientB, float influence) {
  public static final EnvironmentLight NONE = new EnvironmentLight(0, -1, 0, 0, 0, 0, 0, 0, 0, 0);
  public EnvironmentLight {
    if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new IllegalArgumentException("Nonfinite environment direction");
    final double length = Math.sqrt((double)x*x + (double)y*y + (double)z*z);
    if(length < 1e-6) throw new IllegalArgumentException("Environment direction cannot be zero");
    x = (float)(x / length); y = (float)(y / length); z = (float)(z / length);
    for(final float value : new float[] {r,g,b,ambientR,ambientG,ambientB,influence}) {
      if(!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Environment values must be in [0, 1]");
    }
  }
  private static float number(final com.google.gson.JsonElement value) {
    if(value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected numeric light value");
    return value.getAsFloat();
  }
  private static float[] vector(final JsonObject root, final String name) {
    final JsonArray values = root.getAsJsonArray(name);
    if(values == null || values.size() != 3) throw new IllegalArgumentException("Expected three " + name + " values");
    return new float[] {number(values.get(0)), number(values.get(1)), number(values.get(2))};
  }
  /** Takes stream ownership and fails closed on oversized, malformed or unsupported metadata. */
  public static EnvironmentLight read(final InputStream input) throws IOException {
    try(input) {
      final byte[] data = input.readNBytes(16385);
      if(data.length > 16384) throw new IOException("Environment metadata exceeds 16 KiB");
      final JsonReader reader = new JsonReader(new StringReader(new String(data, StandardCharsets.UTF_8)));
      reader.setStrictness(Strictness.STRICT); reader.setNestingLimit(8);
      final JsonObject root = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(reader, JsonObject.class);
      final var version = root.get("version");
      number(version); // Reject string, boolean and missing versions before exact numeric comparison.
      if(reader.peek() != JsonToken.END_DOCUMENT || new java.math.BigDecimal(version.getAsString()).compareTo(java.math.BigDecimal.ONE) != 0) throw new IOException("Unsupported environment metadata");
      final float[] direction = vector(root,"direction"), colour = vector(root,"colour"), ambient = vector(root,"ambient");
      return new EnvironmentLight(direction[0],direction[1],direction[2],colour[0],colour[1],colour[2],ambient[0],ambient[1],ambient[2],number(root.get("influence")));
    } catch(final RuntimeException e) { throw new IOException("Invalid environment metadata",e); }
  }
  public static EnvironmentLight loadOptional(final int disk, final int cut) {
    final Path path = Path.of("lighting-packs","submaps","disk"+disk,"cut"+cut+".json");
    if(!Files.isRegularFile(path)) return NONE;
    try { return read(Files.newInputStream(path)); }
    catch(final IOException e) { org.apache.logging.log4j.LogManager.getLogger().warn("Retaining authored game lights: invalid environment metadata {}",path,e); return NONE; }
  }
}
