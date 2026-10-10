package charhd;

import legend.core.renderer.SurfaceMaterial;
import legend.core.renderer.SurfaceResponse;
import legend.definitive.materials.MaterialAtlas;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Strictness;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Explicit reviewed palette assignments; omitted/mixed materials retain engine metadata. */
public final class CharacterSurfaces {
  private CharacterSurfaces() { }
  public static Map<Integer, SurfaceResponse> read(final byte[] bytes, final MaterialAtlas atlas) throws IOException {
    return read(bytes, atlas.regions().stream().map(MaterialAtlas.Region::palette).collect(java.util.stream.Collectors.toSet()));
  }

  static Map<Integer, SurfaceResponse> read(final byte[] bytes, final java.util.Set<Integer> palettes) throws IOException {
    if(bytes.length > 65536) throw new IOException("Oversized character materials");
    final var values = new HashMap<Integer, SurfaceResponse>();
    try(final var reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      reader.setStrictness(Strictness.STRICT);
      reader.beginArray();
      while(reader.hasNext()) {
        reader.beginObject();
        Integer palette = null;
        SurfaceMaterial material = null;
        Float roughness = null;
        while(reader.hasNext()) switch(reader.nextName()) {
          case "palette" -> { if(palette != null || reader.peek() != JsonToken.NUMBER) throw new IOException("Invalid palette"); palette = reader.nextInt(); }
          case "surface" -> { if(material != null || reader.peek() != JsonToken.STRING) throw new IOException("Invalid surface"); material = SurfaceMaterial.valueOf(reader.nextString().toUpperCase(java.util.Locale.ROOT)); }
          case "roughness" -> { if(roughness != null || reader.peek() != JsonToken.NUMBER) throw new IOException("Invalid roughness"); roughness = (float)reader.nextDouble(); }
          default -> throw new IOException("Unknown character material field");
        }
        reader.endObject();
        if(palette == null || material == null || roughness == null) throw new IOException("Incomplete material");
        final int key = palette;
        if(!palettes.contains(key) || values.put(palette, new SurfaceResponse(material, roughness)) != null)
          throw new IOException("Unknown or duplicate palette");
      }
      reader.endArray();
      if(reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing material data");
      return Map.copyOf(values);
    } catch(final RuntimeException failure) { throw new IOException("Invalid character surface metadata", failure); }
  }
}
