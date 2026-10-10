package legend.definitive.rendering;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.FloatBuffer;
import static org.junit.jupiter.api.Assertions.*;

class EffectLightsTest {
  @Test void invalidOrDarkEmittersDoNotConsumeTheBudget() {
    final EffectLights lights = new EffectLights();
    lights.add(new Vector3f(Float.NaN, 0, 0), 1, 1, 1, 256);
    lights.add(new Vector3f(), Float.NaN, 1, 1, 256);
    lights.add(new Vector3f(), 1, 1, 1, -1);
    lights.add(new Vector3f(), 0, 0, 0, 256);
    assertEquals(0, lights.count());
  }

  @Test void particleClustersShareOneLightAndBrighterClustersWinAtCapacity() {
    final EffectLights lights = new EffectLights();
    lights.add(new Vector3f(), 0.2f, 0.2f, 0.2f, 256);
    lights.add(new Vector3f(2, 0, 0), 0.5f, 0.5f, 0.5f, 256);
    assertEquals(1, lights.count());
    for(int i = 1; i <= 20; i++) lights.add(new Vector3f(i * 1000, 0, 0), i / 20.0f, i / 20.0f, i / 20.0f, 256);
    assertEquals(4, lights.count());
    final FloatBuffer positions = FloatBuffer.allocate(16), colours = FloatBuffer.allocate(16);
    lights.store(positions, colours);
    float weakest = 1;
    for(int i = 0; i < 4; i++) weakest = Math.min(weakest, colours.get(i * 4 + 3));
    assertEquals(0.85f, weakest, 0.00001f);
    lights.clear();
    assertEquals(0, lights.count(), "Light lifetimes follow queued frame lifetimes");
  }

  @Test void malformedIntensityAndRadiusStayBounded() {
    final EffectLights lights = new EffectLights();
    lights.add(new Vector3f(), 20, -20, 2, 100000);
    final FloatBuffer positions = FloatBuffer.allocate(16), colours = FloatBuffer.allocate(16);
    lights.store(positions, colours);
    assertEquals(512, positions.get(3));
    assertEquals(0.22f, colours.get(0));
    assertEquals(0, colours.get(1));
    assertEquals(0.22f, colours.get(2));
  }
}
