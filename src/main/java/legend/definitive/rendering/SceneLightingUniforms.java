// Definitive scene-light tuning (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import legend.core.renderer.Shader;
import legend.core.renderer.ShaderUniformInt;
import legend.core.renderer.ShaderUniformVec3;

/** Handles are renewed after shader reload, and every frame sets the complete profile. */
public final class SceneLightingUniforms {
  private final ShaderUniformInt enabled;
  private final ShaderUniformVec3 key;
  private final ShaderUniformVec3 ambient;

  public SceneLightingUniforms(final Shader<?> shader) {
    this.enabled = shader.uniformInt("sceneLighting");
    this.key = shader.uniformVec3("sceneKeyTint");
    this.ambient = shader.uniformVec3("sceneAmbientTint");
  }

  public void set(final SceneLighting profile) {
    this.enabled.set(profile.equals(SceneLighting.ORIGINAL) ? 0 : 1);
    this.key.set(profile.keyR(), profile.keyG(), profile.keyB());
    this.ambient.set(profile.ambientR(), profile.ambientG(), profile.ambientB());
  }
}
