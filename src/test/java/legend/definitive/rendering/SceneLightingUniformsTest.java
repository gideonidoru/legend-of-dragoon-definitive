// Definitive rendering acceptance (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import legend.core.renderer.Shader;
import legend.core.renderer.ShaderOptions;
import legend.core.renderer.ShaderUniformInt;
import legend.core.renderer.noop.NoopShader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SceneLightingUniformsTest {
  @Test void disablingAndReenablingLightingClearsAndRestoresEveryGain() {
    final int[] enabled = {-1};
    final Map<String, float[]> vectors = new HashMap<>();
    @SuppressWarnings("unchecked")
    final Shader<ShaderOptions> shader = (Shader<ShaderOptions>)Proxy.newProxyInstance(Shader.class.getClassLoader(), new Class<?>[] {Shader.class}, (proxy, method, args) -> switch(method.getName()) {
      case "uniformInt" -> (ShaderUniformInt)value -> enabled[0] = value;
      case "uniformVec3" -> new NoopShader.UniformVec3() {
        @Override
        public void set(final float x, final float y, final float z) {
          vectors.put((String)args[0], new float[] {x, y, z});
        }
      };
      default -> throw new UnsupportedOperationException(method.getName());
    });
    final SceneLightingUniforms uniforms = new SceneLightingUniforms(shader);
    uniforms.set(SceneLighting.ENHANCED);
    assertEquals(1, enabled[0]);
    assertNotEquals(1, vectors.get("sceneKeyTint")[2]);
    uniforms.set(SceneLighting.ORIGINAL);
    assertEquals(0, enabled[0]);
    assertArrayEquals(new float[] {1, 1, 1}, vectors.get("sceneKeyTint"));
    assertArrayEquals(new float[] {1, 1, 1}, vectors.get("sceneAmbientTint"));
    uniforms.set(SceneLighting.ENHANCED);
    assertEquals(1, enabled[0]);
    assertEquals(SceneLighting.ENHANCED.ambientB(), vectors.get("sceneAmbientTint")[2]);
  }
}
