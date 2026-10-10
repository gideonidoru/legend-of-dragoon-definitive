// Definitive rendering acceptance (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneLightingTest {
  @Test void enhancementPreservesAuthoredSceneHue() {
    final SceneLighting p = SceneLighting.ENHANCED;
    assertEquals(p.keyR(), p.keyG());
    assertEquals(p.keyG(), p.keyB());
    assertEquals(p.ambientR(), p.ambientG());
    assertEquals(p.ambientG(), p.ambientB());
    assertNotEquals(SceneLighting.ORIGINAL, p);
  }

  @Test void malformedProfilesCannotEnterTheRenderLoop() {
    assertThrows(IllegalArgumentException.class, () -> new SceneLighting(Float.NaN, 1, 1, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new SceneLighting(1, 1, 1, 1, Float.POSITIVE_INFINITY, 1));
    assertThrows(IllegalArgumentException.class, () -> new SceneLighting(1, 1, 1, 1, 1, 2));
    assertThrows(IllegalArgumentException.class, () -> new SceneLighting(0, 1, 1, 1, 1, 1));
  }
}
