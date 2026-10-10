package charhd;

import org.junit.jupiter.api.Test;
import legend.core.renderer.SurfaceMaterial;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class CharacterSurfacesTest {
  private static byte[] json(final String value) { return value.getBytes(StandardCharsets.UTF_8); }
  @Test void explicitMaterialsRetainTheirRoughnessAndOmittedRegionsHaveNoOverride() throws Exception {
    final var plan = CharacterSurfaces.read(json("[{\"palette\":0,\"surface\":\"metal\",\"roughness\":0.4}]"), Set.of(0, 1));
    assertEquals(SurfaceMaterial.METAL, plan.get(0).material());
    assertEquals(0.4f, plan.get(0).roughness());
    assertNull(plan.get(1));
    assertThrows(UnsupportedOperationException.class, () -> plan.clear());
  }
  @Test void unknownAndDuplicatePalettesCannotPartiallyInstallMaterials() {
    for(final String value : new String[]{"[{\"palette\":2,\"surface\":\"metal\",\"roughness\":0.4}]",
      "[{\"palette\":0,\"surface\":\"skin\",\"roughness\":0.5},{\"palette\":0,\"surface\":\"metal\",\"roughness\":0.4}]"})
      assertThrows(IOException.class, () -> CharacterSurfaces.read(json(value), Set.of(0)));
  }
  @Test void malformedMissingAndUnboundedFieldsRejectTheWholePlan() {
    for(final String value : new String[]{"[{\"palette\":0}]", "[{\"palette\":0,\"surface\":\"plastic\",\"roughness\":0.4}]",
      "[{\"palette\":0,\"surface\":\"skin\",\"roughness\":0}]", "[{\"palette\":0,\"palette\":0,\"surface\":\"skin\",\"roughness\":0.5}]",
      "[{\"palette\":0,\"surface\":\"skin\",\"roughness\":\"0.5\"}]", "[] {}"})
      assertThrows(IOException.class, () -> CharacterSurfaces.read(json(value), Set.of(0)));
  }
}
