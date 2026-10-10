package legend.definitive.rendering;

import legend.core.renderer.Shader;
import legend.core.renderer.ShaderUniformInt;
import legend.core.renderer.ShaderUniformVec4;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public final class ModernLightingUniforms {
  private final ShaderUniformInt materials, enabled, count;
  private final ShaderUniformVec4 positions, colours;
  private final ShaderUniformVec4 environmentDirection;
  private final legend.core.renderer.ShaderUniformVec3 environmentColour, environmentAmbient;
  private final FloatBuffer positionBuffer = BufferUtils.createFloatBuffer(16);
  private final FloatBuffer colourBuffer = BufferUtils.createFloatBuffer(16);

  public ModernLightingUniforms(final Shader<?> shader) {
    this.materials = shader.uniformInt("materialLighting");
    this.enabled = shader.uniformInt("modernLighting");
    this.count = shader.uniformInt("effectLightCount");
    this.positions = shader.uniformVec4("effectPositions");
    this.colours = shader.uniformVec4("effectColours");
    this.environmentDirection = shader.uniformVec4("environmentDirection");
    this.environmentColour = shader.uniformVec3("environmentColour");
    this.environmentAmbient = shader.uniformVec3("environmentAmbient");
  }

  public void set(final boolean materials, final boolean effects, final EffectLights lights) {
    this.set(materials, effects, lights, EnvironmentLight.NONE);
  }

  public void set(final boolean materials, final boolean effects, final EffectLights lights, final EnvironmentLight environment) {
    this.materials.set(materials ? 1 : 0);
    this.enabled.set(materials || effects && lights.count() > 0 || environment.influence() > 0 ? 1 : 0);
    this.environmentDirection.set(environment.x(), environment.y(), environment.z(), environment.influence());
    this.environmentColour.set(environment.r(), environment.g(), environment.b());
    this.environmentAmbient.set(environment.ambientR(), environment.ambientG(), environment.ambientB());
    this.count.set(effects ? lights.count() : 0);
    lights.store(this.positionBuffer, this.colourBuffer);
    this.positions.set(this.positionBuffer);
    this.colours.set(this.colourBuffer);
  }
}
