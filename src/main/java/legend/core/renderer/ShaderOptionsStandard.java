package legend.core.renderer;

public class ShaderOptionsStandard extends ShaderOptionsBase {
  private ShaderUniformInt uiArtwork;
  private ShaderUniformVec4 uiArtworkBounds;

  public void uiArtworkUniforms(final Shader<?> shader) {
    this.uiArtwork = shader.uniformInt("uiArtworkEnabled");
    this.uiArtworkBounds = shader.uniformVec4("uiArtworkBounds");
    shader.use();
    shader.uniformInt("uiArtworkTex").set(2);
    shader.uniformInt("uiSourceTex").set(3);
  }

  public void uiArtwork(final boolean enabled, final org.joml.Vector4f bounds) {
    if(this.uiArtwork != null) {
      this.uiArtwork.set(enabled ? 1 : 0);
      if(enabled) this.uiArtworkBounds.set(bounds);
    }
  }

  private final ShaderUniformFloat translucency;
  private final ShaderUniformFloat alpha;
  private final ShaderUniformFloat useTextureAlpha;

  public ShaderOptionsStandard(final ShaderUniformFloat modelIndex, final ShaderUniformVec3 colourUniform, final ShaderUniformVec2 uvOffsetUniform, final ShaderUniformVec2 clutUniform, final ShaderUniformVec2 tpageUniform, final ShaderUniformFloat discardTranslucency, final ShaderUniformFloat translucency, final ShaderUniformFloat alpha, final ShaderUniformFloat useTextureAlpha) {
    super(modelIndex, colourUniform, uvOffsetUniform, clutUniform, tpageUniform, discardTranslucency);
    this.translucency = translucency;
    this.alpha = alpha;
    this.useTextureAlpha = useTextureAlpha;
  }

  public ShaderOptionsStandard opaque() {
    this.translucency.set(0);
    return this;
  }

  public ShaderOptionsStandard translucency(final Translucency translucency) {
    this.translucency.set(translucency.ordinal() + 1);
    return this;
  }

  public ShaderOptionsStandard alpha(final float alpha) {
    this.alpha.set(alpha);
    return this;
  }

  /** Whether or not to use texture's alpha channel */
  public ShaderOptionsStandard useTextureAlpha(final boolean val) {
    this.useTextureAlpha.set(val ? 1.0f : 0.0f);
    return this;
  }
}
