package legend.core.renderer;

import org.joml.Vector2f;
import org.joml.Vector3f;

public class ShaderOptionsBase implements ShaderOptions {
  public void surface(final SurfaceMaterial material) { }
  private ShaderUniformInt uiLayer;
  private ShaderUniformInt effectArtwork;
  private ShaderUniformFloat emission;
  private ShaderUniformInt hdTexture;
  private ShaderUniformInt normalMap, roughnessMap;
  private ShaderUniformFloat normalStrength;
  private ShaderUniformInt defaultMaps;

  public void metadataUniforms(final Shader<?> shader) {
    this.uiLayer = shader.uniformInt("uiLayer");
    this.effectArtwork = shader.uniformInt("effectArtworkEnabled");
    this.emission = shader.uniformFloat("emission");
    this.hdTexture = shader.uniformInt("hdTexture");
    this.normalMap = shader.uniformInt("normalMapEnabled");
    this.roughnessMap = shader.uniformInt("roughnessMapEnabled");
    this.normalStrength = shader.uniformFloat("normalMapStrength");
    this.defaultMaps = shader.uniformInt("defaultSurfaceMaps");
    shader.use();
    shader.uniformInt("normalMapTex").set(4);
    shader.uniformInt("roughnessMapTex").set(5);
    shader.uniformInt("effectDetailTex").set(6);
  }

  public void effectArtwork(final boolean enabled) { if(this.effectArtwork != null) this.effectArtwork.set(enabled ? 1 : 0); }

  public void materialMaps(final boolean normal, final boolean roughness, final float strength) {
    if(this.normalMap != null) {
      this.normalMap.set(normal ? 1 : 0);
      this.roughnessMap.set(roughness ? 1 : 0);
      this.normalStrength.set(strength);
    }
  }

  public void defaultSurfaceMaps(final boolean enabled) {
    if(this.defaultMaps != null) this.defaultMaps.set(enabled ? 1 : 0);
  }

  public void hdTexture(final boolean enabled) {
    if(this.hdTexture != null) this.hdTexture.set(enabled ? 1 : 0);
  }

  public void renderMetadata(final boolean ui, final float emission) {
    if(this.uiLayer != null) {
      this.uiLayer.set(ui ? 1 : 0);
      this.emission.set(ui ? 0.0f : emission);
    }
  }
  private final ShaderUniformFloat modelIndex;
  private final ShaderUniformVec3 colourUniform;
  private final ShaderUniformVec2 uvOffsetUniform;
  private final ShaderUniformVec2 clutUniform;
  private final ShaderUniformVec2 tpageUniform;
  private final ShaderUniformFloat discardTranslucency;

  public ShaderOptionsBase(final ShaderUniformFloat modelIndex, final ShaderUniformVec3 colourUniform, final ShaderUniformVec2 uvOffsetUniform, final ShaderUniformVec2 clutUniform, final ShaderUniformVec2 tpageUniform, final ShaderUniformFloat discardTranslucency) {
    this.modelIndex = modelIndex;
    this.colourUniform = colourUniform;
    this.uvOffsetUniform = uvOffsetUniform;
    this.clutUniform = clutUniform;
    this.tpageUniform = tpageUniform;
    this.discardTranslucency = discardTranslucency;
  }

  public ShaderOptionsBase modelIndex(final float modelIndex) {
    this.modelIndex.set(modelIndex);
    return this;
  }

  public ShaderOptionsBase colour(final Vector3f colour) {
    this.colourUniform.set(colour);
    return this;
  }

  public ShaderOptionsBase uvOffset(final Vector2f colour) {
    this.uvOffsetUniform.set(colour);
    return this;
  }

  public ShaderOptionsBase clut(final Vector2f clut) {
    this.clutUniform.set(clut);
    return this;
  }

  public ShaderOptionsBase tpage(final Vector2f tpage) {
    this.tpageUniform.set(tpage);
    return this;
  }

  /**
   * <ul>
   *   <li>0: regular rendering, anything rendered will pass through the shader</li>
   *   <li>1: discard translucent pixels, used for rendering translucent primitives that have translucency disabled in their textures</li>
   *   <li>2: discard opaque pixels</li>
   * </ul>
   */
  public ShaderOptionsBase discardMode(final int discardMode) {
    this.discardTranslucency.set(discardMode);
    return this;
  }

  @Override
  public void apply() {

  }
}
