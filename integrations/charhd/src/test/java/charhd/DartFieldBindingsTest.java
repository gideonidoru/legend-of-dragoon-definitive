// Accepted Dart artwork must survive variant rebinding unchanged. AGPL v3; see LICENSE.
package charhd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DartFieldBindingsTest {
  private static JsonObject resource(final String path) throws Exception {
    try(final var input = ReconstructedCharacters.class.getResourceAsStream("/charhd/characters/" + path)) {
      assertNotNull(input, path);
      return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }

  @Test void allKnownFieldHeadsUseTheAcceptedGeometryUvAndMaterial() throws Exception {
    final var heads = new HashMap<String, JsonObject>();
    for(final var element : resource("characters.json").getAsJsonArray("parts")) {
      final var row = element.getAsJsonObject();
      if(row.get("character").getAsString().equals("dart") && row.get("form").getAsString().equals("field") && row.has("uv")) {
        assertNull(heads.put(row.get("sourceGeometrySha256").getAsString(), row), "Duplicate native head binding");
      }
    }
    final String canonical = "b84e13a11adbd3419e1e4c5b810c8bd797f9dc68af5d41fe3c4736ce5c4f584b";
    assertEquals(Set.of(canonical,
      "82d567bfa9c5bd155ae5d09aad12bb6b0b0bcd72a0e2543213b4822efecfc05b",
      "7ec2a8d20854bdffb131236bfec72593e54b3a40ccd08232afb8a5f372583706",
      "640e2a8491d68dd0351de32036c1f581548ca9ed1ec869ada24c92a903939c7c"), heads.keySet());
    final var accepted = heads.get(canonical);
    final var acceptedParts = resource(accepted.get("pack").getAsString()).get("parts");
    final var acceptedUv = resource(accepted.get("uv").getAsString()).get("uvs");
    for(final var entry : heads.entrySet()) {
      final var row = entry.getValue();
      final var pack = resource(row.get("pack").getAsString());
      final var uv = resource(row.get("uv").getAsString());
      assertEquals(entry.getKey(), pack.get("sourceGeometrySha256").getAsString());
      assertEquals(entry.getKey(), uv.get("sourceGeometrySha256").getAsString());
      assertEquals(acceptedParts, pack.get("parts"), "Variant changed accepted custom geometry");
      assertEquals(acceptedUv, uv.get("uvs"), "Variant changed accepted paint placement");
      for(final String key : Set.of("geometrySha256", "texture", "textureSha256", "width", "height")) {
        assertEquals(accepted.get(key), row.get(key), key);
      }
    }
    // Exercise the production checksum and bounded-resource loader for every row.
    assertNotNull(new ReconstructedCharacters());
  }
}
