package legend.definitive.rendering;

import legend.core.renderer.Shader;
import legend.core.renderer.ShaderUniformInt;
import legend.core.renderer.ShaderUniformVec4;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public final class ModernLightingUniforms {
  private final ShaderUniformInt materials, enabled, count;
  private final ShaderUniformVec4 positions, colours;
  private final FloatBuffer positionBuffer = BufferUtils.createFloatBuffer(16);
  private final FloatBuffer colourBuffer = BufferUtils.createFloatBuffer(16);

  public ModernLightingUniforms(final Shader<?> shader) {
    this.materials = shader.uniformInt("materialLighting");
    this.enabled = shader.uniformInt("modernLighting");
    this.count = shader.uniformInt("effectLightCount");
    this.positions = shader.uniformVec4("effectPositions");
    this.colours = shader.uniformVec4("effectColours");
  }

  public void set(final boolean materials, final boolean effects, final EffectLights lights) {
    this.materials.set(materials ? 1 : 0);
    this.enabled.set(materials || effects && lights.count() > 0 ? 1 : 0);
    this.count.set(effects ? lights.count() : 0);
    lights.store(this.positionBuffer, this.colourBuffer);
    this.positions.set(this.positionBuffer);
    this.colours.set(this.colourBuffer);
  }
}
