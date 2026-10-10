package legend.definitive.rendering;

import legend.core.renderer.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.file.Path;

/** SMAA 1x High: three spatial passes, no jitter, history or extra buffered frames. */
public final class SmaaPipeline {
  private static final Logger LOGGER = LogManager.getLogger();
  private static ShaderType<ShaderOptions> type(final String name, final String fragment) {
    return new ShaderType<>(options -> RenderEngine.loadShader(name, "post", fragment, options), shader -> () -> () -> { });
  }
  private static final ShaderType<ShaderOptions> EDGE = type("SMAA edges", "smaa_edge");
  private static final ShaderType<ShaderOptions> WEIGHTS = type("SMAA weights", "smaa_weights");
  private static final ShaderType<ShaderOptions> BLEND = type("SMAA blend", "smaa_blend");
  private Shader<?> edge, weights, blend;
  private ShaderUniformVec4 edgeMetrics, weightMetrics, blendMetrics;
  private ShaderUniformInt edgeProtect, blendProtect;
  private ShaderUniformInt edgeColour, edgeCoverage, weightEdges, weightArea, weightSearch, blendColour, blendWeights, blendCoverage;
  private ShaderUniformFloat blendStrength;
  private Texture area, search, edges, blending, output;
  private FrameBuffer edgeBuffer, weightBuffer, outputBuffer;
  private boolean failed;
  private int width, height;

  public boolean available() { return !this.failed; }

  private void initialize() {
    if(this.edge != null) return;
    this.edge = ShaderManager.addShader(EDGE);
    this.weights = ShaderManager.addShader(WEIGHTS);
    this.blend = ShaderManager.addShader(BLEND);
    this.edgeMetrics = this.edge.uniformVec4("metrics");
    this.weightMetrics = this.weights.uniformVec4("metrics");
    this.blendMetrics = this.blend.uniformVec4("metrics");
    this.edgeProtect = this.edge.uniformInt("protectInterface");
    this.blendProtect = this.blend.uniformInt("protectInterface");
    this.blendStrength = this.blend.uniformFloat("strength");
    this.edgeColour = this.edge.uniformInt("colourTex"); this.edgeCoverage = this.edge.uniformInt("coverageTex");
    this.weightEdges = this.weights.uniformInt("edgesTex"); this.weightArea = this.weights.uniformInt("areaTex"); this.weightSearch = this.weights.uniformInt("searchTex");
    this.blendColour = this.blend.uniformInt("colourTex"); this.blendWeights = this.blend.uniformInt("weightsTex"); this.blendCoverage = this.blend.uniformInt("coverageTex");
    this.area = Texture.filteredPng("SMAA area lookup", Path.of("gfx/textures/smaa/AreaTex.png"));
    this.search = Texture.filteredPng("SMAA search lookup", Path.of("gfx/textures/smaa/SearchTex.png"));
    this.area.persistent = this.search.persistent = true;
  }

  private static FrameBuffer target(final String name, final Texture texture) {
    return FrameBuffer.create(name, builder -> builder.attachment(FrameBufferAttachmentType.COLOUR, texture));
  }

  private void resize(final int w, final int h) {
    if(this.width == w && this.height == h && this.output != null) return;
    this.releaseTargets();
    this.edges = Texture.filteredEmpty("SMAA edges", w, h);
    this.blending = Texture.filteredEmpty("SMAA weights", w, h);
    this.output = Texture.create("SMAA result", builder -> { builder.size(w, h); builder.minFilter(true); });
    this.edges.persistent = this.blending.persistent = this.output.persistent = true;
    this.edgeBuffer = target("SMAA edge buffer", this.edges);
    this.weightBuffer = target("SMAA weight buffer", this.blending);
    this.outputBuffer = target("SMAA result buffer", this.output);
    this.width = w; this.height = h;
  }

  public void releaseTargets() {
    if(this.edgeBuffer != null) this.edgeBuffer.delete();
    if(this.weightBuffer != null) this.weightBuffer.delete();
    if(this.outputBuffer != null) this.outputBuffer.delete();
    if(this.edges != null) this.edges.delete();
    if(this.blending != null) this.blending.delete();
    if(this.output != null) this.output.delete();
    this.edgeBuffer = this.weightBuffer = this.outputBuffer = null;
    this.edges = this.blending = this.output = null;
    this.width = this.height = 0;
  }

  public void delete() {
    this.releaseTargets();
    if(this.area != null) this.area.delete();
    if(this.search != null) this.search.delete();
  }

  public Texture apply(final RenderApi api, final Texture scene, final Texture coverage, final Mesh quad, final float amount, final boolean protect) {
    if(this.failed || !Float.isFinite(amount) || amount <= 0) return scene;
    try {
      this.initialize();
      this.resize(scene.width, scene.height);
      api.viewport(0, 0, this.width, this.height);
      api.backfaceCulling(false);
      scene.linearSampling(true);
      this.edgeBuffer.bind();
      // Edge shader discards non-edges. Clear explicitly rather than inheriting scene clear color.
      api.clearColourAttachment(0);
      this.edge.use();
      this.edgeColour.set(0); this.edgeCoverage.set(3);
      this.edgeMetrics.set(1.0f / this.width, 1.0f / this.height, this.width, this.height);
      this.edgeProtect.set(protect ? 1 : 0);
      scene.use(0); coverage.use(3); quad.draw();

      this.weightBuffer.bind(); this.weights.use();
      this.weightEdges.set(0); this.weightArea.set(1); this.weightSearch.set(2);
      this.weightMetrics.set(1.0f / this.width, 1.0f / this.height, this.width, this.height);
      this.edges.use(0); this.area.use(1); this.search.use(2); quad.draw();

      this.outputBuffer.bind(); this.blend.use();
      this.blendColour.set(0); this.blendWeights.set(1); this.blendCoverage.set(3);
      this.blendMetrics.set(1.0f / this.width, 1.0f / this.height, this.width, this.height);
      this.blendProtect.set(protect ? 1 : 0); this.blendStrength.set(Math.min(1, amount));
      scene.use(0); this.blending.use(1); coverage.use(3); quad.draw();
      return this.output;
    } catch(final RuntimeException failure) {
      this.failed = true;
      LOGGER.error("SMAA unavailable; retaining spatial edge smoothing", failure);
      this.releaseTargets();
      if(this.area != null) this.area.delete();
      if(this.search != null) this.search.delete();
      return scene;
    } finally {
      scene.linearSampling(false);
      api.unbindFramebuffer();
    }
  }
}
