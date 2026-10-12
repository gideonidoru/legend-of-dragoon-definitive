package legend.core.renderer;

import legend.core.LitModel;
import legend.core.gte.MV;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;

public class QueuedModelStandard extends QueuedModel<ShaderOptionsStandard, QueuedModelStandard> implements LitModel {
  private Translucency translucency;
  private boolean hasTranslucencyOverride;
  private float alpha;
  private boolean useTextureAlpha;
  private boolean uiArtwork;
  private boolean nativeUiTracked;
  private boolean uiPaletteDetail;
  private legend.game.textures.NativeSpriteFont.Selection spriteFont;
  private final Vector4f uiArtworkBounds = new Vector4f();

  /** Enhance RGB only; indexed VRAM continues to decide visibility and blend mode. */
  public QueuedModelStandard uiArtwork(final Texture artwork, final Texture source, final float x, final float y, final float width, final float height) {
    if(this.textures[0] != null || this.textures[1] != null || this.textures[2] != null || this.textures[3] != null) return this;
    this.uiArtwork = true;
    this.nativeUiTracked = false;
    this.uiArtworkBounds.set(x, y, width, height);
    this.texture(artwork, 2).texture(source, 3).ui();
    return this;
  }


  /** Engine-owned selections can be retired without invalidating independently authored UI textures. */
  public QueuedModelStandard nativeUiArtwork(final Texture artwork, final Texture source, final float x, final float y, final float width, final float height) {
    return this.nativeUiArtwork(artwork, source, x, y, width, height, false);
  }

  public QueuedModelStandard nativeUiArtwork(final Texture artwork, final Texture source, final float x, final float y, final float width, final float height, final boolean paletteDetail) {
    if(!this.uiArtwork) {
      this.uiArtwork(artwork,source,x,y,width,height);
      if(this.uiArtwork) { this.nativeUiTracked = true; this.uiPaletteDetail = paletteDetail; }
    }
    return this;
  }

  final Matrix4f lightTransforms = new Matrix4f();
  final FloatBuffer lightingBuffer;

  final Matrix4f lightDirection = new Matrix4f();
  final Matrix3f lightColour = new Matrix3f();
  final Vector4f backgroundColour = new Vector4f();
  private boolean lightUsed;

  public QueuedModelStandard(final RenderBatch batch, final Shader<ShaderOptionsStandard> shader, final ShaderOptionsStandard shaderOptions, final FloatBuffer lightingBuffer) {
    super(batch, shader, shaderOptions);
    this.lightingBuffer = lightingBuffer;
  }

  public QueuedModelStandard translucency(@Nullable final Translucency translucency) {
    this.translucency = translucency;
    this.hasTranslucencyOverride = true;
    return this;
  }

  /** Changes translucency mode to true alpha */
  public QueuedModelStandard alpha(final float alpha) {
    this.alpha = alpha;
    return this;
  }

  /** Use texture's alpha channel */
  public QueuedModelStandard useTextureAlpha() {
    this.useTextureAlpha = true;
    return this;
  }

  private void resolveSpriteFont() {
    if(this.spriteFont != null && (!this.uiLayer || this.getLayers() != 1 || this.textures[0] != null || this.textures[1] != null
      || this.textures[2] != this.spriteFont.artwork() || this.textures[3] != this.spriteFont.reference())) this.clearSpriteFont();
    if(!this.uiLayer || this.getLayers() != 1 || this.textures[0] != null || this.textures[1] != null) return;
    final NativeUiQuad quad = this.obj.nativeUiQuad(this.startVertex, this.vertexCount);
    final NativeUiQuad source = quad == null ? null : quad.overridden(this);
    if(this.spriteFont != null && (!this.spriteFont.current() || !this.spriteFont.source().equals(source))) {
      this.clearSpriteFont();
    }
    if(this.spriteFont != null || source == null || this.uiArtwork && !this.nativeUiTracked) return;
    if(!this.nativeUiTracked && (this.textures[2] != null || this.textures[3] != null)) return;
    this.spriteFont = legend.game.textures.NativeSpriteFont.select(source);
    if(this.spriteFont != null) {
      this.uiArtwork = this.nativeUiTracked = false;
      this.uiArtworkBounds.set(source.u(), source.v(), source.width(), source.height());
      this.texture(this.spriteFont.artwork(), 2).texture(this.spriteFont.reference(), 3);
    }
  }

  private void clearSpriteFont() {
    if(this.textures[2] == this.spriteFont.artwork()) this.textures[2] = null;
    if(this.textures[3] == this.spriteFont.reference()) this.textures[3] = null;
    this.spriteFont = null;
  }

  /** Source selection must precede both opaque and translucent pass decisions. */
  @Override public void useShader(final int modelIndex, final int discardMode) {
    this.resolveSpriteFont();
    super.useShader(modelIndex, discardMode);
  }

  private Translucency spriteBlend() {
    if(this.hasTranslucencyOverride) return this.translucency;
    for(final Translucency mode : Translucency.FOR_RENDERING) if(this.obj.shouldRender(mode, 0)) return mode;
    return null;
  }

  @Override
  void useTexture() {
    final boolean retired = this.nativeUiTracked && this.uiArtwork && !legend.game.textures.NativeUiTextures.touch(this.textures[2]);
    if(retired) {
      this.uiArtwork = false; this.textures[2] = this.textures[3] = null;
    }
    if(this.uiLayer && !this.uiArtwork && this.textures[0] == null && this.textures[1] == null && this.textures[2] == null && this.textures[3] == null) {
      final NativeUiQuad source = this.obj.nativeUiQuad(this.startVertex, this.vertexCount);
      if(source != null) {
        final NativeUiQuad draw = source.overridden(this);
        legend.game.textures.NativeUiTextures.apply(this, draw.pageX(), draw.pageY(), draw.clutX(), draw.clutY(), draw.u(), draw.v(), draw.width(), draw.height());
      }
    }
    if(this.uiArtwork || this.nativeUiTracked) legend.core.GameEngine.GPU.useVramTexture();
    super.useTexture();
  }

  @Override
  void acquire(final Obj obj, final int sequence) {
    super.acquire(obj, sequence);
    this.hasTranslucencyOverride = false;
    this.alpha = -1.0f;
    this.useTextureAlpha = false;
    this.uiArtwork = false;
    this.nativeUiTracked = false;
    this.uiPaletteDetail = false;
    this.spriteFont = null;
  }

  @Override
  public boolean hasTranslucency() {
    this.resolveSpriteFont();
    if(this.spriteFont != null) return true;
    return super.hasTranslucency() || this.hasTranslucencyOverride;
  }

  @Override
  public boolean hasTranslucency(final int index) {
    this.resolveSpriteFont();
    if(this.spriteFont != null) return true;
    return super.hasTranslucency(index) || this.hasTranslucencyOverride;
  }

  @Override
  public boolean shouldRender(@Nullable final Translucency translucency) {
    this.resolveSpriteFont();
    if(this.spriteFont != null) return translucency == null || translucency == Translucency.HALF_B_PLUS_HALF_F || translucency == this.spriteBlend();
    if(this.hasTranslucencyOverride) {
      return this.translucency == translucency;
    }

    return super.shouldRender(translucency);
  }

  @Override
  public boolean shouldRender(@Nullable final Translucency translucency, final int layer) {
    this.resolveSpriteFont();
    if(this.spriteFont != null) return layer == 0 && (translucency == null || translucency == Translucency.HALF_B_PLUS_HALF_F || translucency == this.spriteBlend());
    if(this.hasTranslucencyOverride) {
      return this.translucency == translucency;
    }

    return super.shouldRender(translucency, layer);
  }


  public QueuedModelStandard lightDirection(final Matrix3f lightDirection) {
    this.lightDirection.set(lightDirection).mul(this.lightTransforms).setTranslation(0.0f, 0.0f, 0.0f);
    this.lightUsed = true;
    return this;
  }

  public QueuedModelStandard lightColour(final Matrix3f lightColour) {
    this.lightColour.set(lightColour);
    this.lightUsed = true;
    return this;
  }

  public QueuedModelStandard backgroundColour(final Vector3f backgroundColour) {
    this.backgroundColour.set(backgroundColour, 0.0f);
    this.lightUsed = true;
    return this;
  }

  @Override
  public void setLightTransforms(MV light) {
    this.lightTransforms.set(light).setTranslation(light.transfer);
  }

  @Override
  void storeTransforms(final int modelIndex, final FloatBuffer transforms2Buffer) {
    super.storeTransforms(modelIndex, transforms2Buffer);

    if(this.lightUsed) {
      this.lightDirection.get(modelIndex * 32, this.lightingBuffer);
      this.lightColour.get3x4(modelIndex * 32 + 16, this.lightingBuffer);
      this.backgroundColour.get(modelIndex * 32 + 28, this.lightingBuffer);
    }
  }

  @Override
  void render(@Nullable final Translucency translucency, final int layer) {
    if(translucency != null) {
      this.shaderOptions.translucency(translucency);
    } else {
      this.shaderOptions.opaque();
    }

    this.shaderOptions.uiArtwork(this.uiArtwork, this.uiArtworkBounds);
    this.shaderOptions.uiPaletteDetail(this.uiArtwork && this.uiPaletteDetail);
    this.shaderOptions.spriteFont(this.spriteFont != null, this.spriteBlend(), this.uiArtworkBounds);
    this.shaderOptions.alpha(this.alpha);
    this.shaderOptions.useTextureAlpha(this.useTextureAlpha);

    if(this.hasTranslucencyOverride || this.spriteFont != null) {
      // Translucency override
      this.updateColours(this.spriteFont != null && (translucency == null || translucency == Translucency.HALF_B_PLUS_HALF_F) ? this.spriteBlend() : translucency);
      this.obj.render(layer, this.startVertex, this.vertexCount);
      return;
    }

    super.render(translucency, layer);
  }
}
