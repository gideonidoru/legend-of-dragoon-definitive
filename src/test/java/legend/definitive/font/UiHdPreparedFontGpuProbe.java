package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.gte.MV;
import legend.core.platform.*;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.definitive.rendering.NativeRendererProbe;
import legend.game.textures.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.opengl.GL;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import static legend.definitive.font.UiHdPreparedFontCatalogProbe.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Optional windowless component proof: no window, game loop, or original-data publication. */
final class UiHdPreparedFontGpuProbe {
  private static final int WIDTH = 192, HEIGHT = 96;
  private static final class Api extends GlApi {
    int output;
    @Override public void unbindFramebuffer() { glBindFramebuffer(GL_FRAMEBUFFER, this.output); }
  }
  private static final class OffscreenWindow extends NoopWindow {
    final Api api;
    OffscreenWindow(final Api api) { super(new NoopPlatformManager(), WIDTH, HEIGHT); this.api = api; }
    @Override public RenderApi getRenderApi() { return this.api; }
  }
  record Pixels(byte[] colour, byte[] emission) { }
  private static Pixels draw(final Obj obj, final RenderBatch batch, final Api api, final FrameBuffer target,
                             final Texture colour, final Texture emission, final boolean ui, final boolean controlled) throws Exception {
    reset(); GameEngine.RENDERER.scissorStack.reset(); target.bind(); api.viewport(0, 0, WIDTH, HEIGHT);
    api.clearColour(.2f, .2f, .2f); api.clear(true, true, false);
    final var transform = new MV(); transform.transfer.set(8, 8, 124);
    final var queued = ui ? GameEngine.RENDERER.queueUiOrthoModel(obj, transform, QueuedModelStandard.class)
      : GameEngine.RENDERER.queueOrthoModel(obj, transform, QueuedModelStandard.class);
    if(controlled) queued.translucency(Translucency.HALF_B_PLUS_HALF_F).alpha(.5f).useTextureAlpha().monochrome(.75f).scissor(10, 8, 30, 16);
    queued.hasTranslucency();
    if(ui) require(field(queued, QueuedModelStandard.class, "spriteFont") != null, "GPU eligible queue must use packaged coverage");
    final var render = RenderEngine.class.getDeclaredMethod("renderBatch", RenderBatch.class); render.setAccessible(true); render.invoke(GameEngine.RENDERER, batch);
    glFinish(); require(glGetError() == GL_NO_ERROR, "Prepared font GL draw");
    final byte[][] bytes = new byte[2][];
    for(int i = 0; i < 2; i++) {
      (i == 0 ? colour : emission).use(0); final var read = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
      glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read); bytes[i] = new byte[read.capacity()]; read.get(bytes[i]);
    }
    return new Pixels(bytes[0], bytes[1]);
  }
  private static int[] memory(final NativeUiTextureEvent event) {
    final int[] memory = new int[1024 * 512]; final var tim = event.tim(); final var rect = tim.getImageRect();
    final byte[] pixels = tim.getImageData().getBytes(), palette = tim.getClutData().getBytes();
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      final int offset = (y * rect.w + x) * 2;
      memory[(event.imageY + y) * 1024 + event.imageX + x] = (pixels[offset] & 255) | (pixels[offset + 1] & 255) << 8;
    }
    for(int row = 0; row < palette.length / 32; row++) for(int x = 0; x < 16; x++) {
      final int offset = (row * 16 + x) * 2;
      memory[(event.clutY + row % event.clutRows) * 1024 + event.clutX + row / event.clutRows * 16 + x]
        = (palette[offset] & 255) | (palette[offset + 1] & 255) << 8;
    }
    return memory;
  }
  private static JSONObject pin(final Pixels pixels) throws Exception {
    return new JSONObject().put("colourSha256", hash(pixels.colour)).put("emissionSha256", hash(pixels.emission));
  }
  private static int foreground(final Pixels pixels) {
    int count = 0;
    for(int p = 0; p < pixels.colour.length; p += 4) {
      if((pixels.colour[p] & 255) != 51 || (pixels.colour[p + 1] & 255) != 51 || (pixels.colour[p + 2] & 255) != 51) count++;
    }
    return count;
  }
  private static void save(final Path directory, final String name, final Pixels pixels) throws Exception {
    Files.write(directory.resolve(name + ".rgba"), pixels.colour, StandardOpenOption.CREATE_NEW);
    Files.write(directory.resolve(name + "-emission.rgba"), pixels.emission, StandardOpenOption.CREATE_NEW);
  }
  private static Image nearest(final Image reference) {
    final byte[] bytes = new byte[reference.width * reference.height * 16];
    for(int y = 0; y < reference.height * 2; y++) for(int x = 0; x < reference.width * 2; x++) {
      final int from = (y / 2 * reference.width + x / 2) * 4, to = (y * reference.width * 2 + x) * 4;
      System.arraycopy(reference.data, from, bytes, to, 4);
    }
    return new Image(bytes, reference.width * 2, reference.height * 2);
  }
  static void run(final String library, final Object module, final Method listener, final List<Glyph> glyphs,
                  final Path proof, final JSONObject report) throws Throwable {
    System.load(library); final var open = NativeRendererProbe.class.getDeclaredMethod("open"); open.setAccessible(true);
    final long context = (long)open.invoke(null); require(context != 0, "Windowless context unavailable");
    final var gpuProof = proof.resolve("gpu"); Files.createDirectory(gpuProof); Api api = null; FrameBuffer target = null;
    Throwable failure = null;
    try {
      GL.createCapabilities(); report.put("glVendor", glGetString(GL_VENDOR)).put("glRenderer", glGetString(GL_RENDERER)).put("glVersion", glGetString(GL_VERSION));
      final var config = legend.game.saves.ConfigCollection.class.getDeclaredMethod("setConfigQuietly", legend.game.saves.ConfigEntry.class, Object.class); config.setAccessible(true);
      config.invoke(GameEngine.CONFIG, legend.game.modding.coremod.CoreMod.RESOLUTION_CONFIG.get(), Resolution.ORIGINAL);
      config.invoke(GameEngine.CONFIG, legend.game.modding.coremod.CoreMod.ALLOW_WIDESCREEN_CONFIG.get(), false);
      api = new Api(); final var init = RenderEngine.class.getDeclaredMethod("init", Window.class); init.setAccessible(true); init.invoke(GameEngine.RENDERER, new OffscreenWindow(api));
      GameEngine.RENDERER.updateResolution();
      final Texture colour = Texture.empty("Prepared glyph component output", WIDTH, HEIGHT), emission = Texture.empty("Prepared glyph emission", WIDTH, HEIGHT);
      final Texture depth = Texture.create("Prepared glyph depth", builder -> {
        builder.size(WIDTH, HEIGHT); builder.internalFormat(TextureInternalFormat.DEPTH_COMPONENT); builder.dataFormat(TextureDataFormat.DEPTH_COMPONENT); builder.dataType(TextureDataType.FLOAT);
      });
      target = FrameBuffer.create("Prepared glyph output", builder -> {
        builder.attachment(FrameBufferAttachmentType.COLOUR, colour); builder.attachment(FrameBufferAttachmentType.COLOUR, emission); builder.attachment(FrameBufferAttachmentType.DEPTH, depth);
      }); target.bind(); api.output = glGetInteger(GL_FRAMEBUFFER_BINDING);
      GameEngine.GPU.vramTexture15 = api.makeTexture(org.lwjgl.BufferUtils.createIntBuffer(1024 * 512), "Source-bound font VRAM", 1024, 512,
        TextureInternalFormat.R_32_UINT, TextureDataFormat.RED_INT, TextureDataType.UINT, false, false, false, false); GameEngine.GPU.vramTexture15.persistent = true;
      final var batch = (RenderBatch)field(GameEngine.RENDERER, RenderEngine.class, "mainBatch"); batch.setProjectionSize(WIDTH / 2, HEIGHT / 2); batch.setProjectionDepth(256);
      final var draws = new JSONArray(); final var controls = new JSONArray(); final Set<String> retained = new HashSet<>();
      for(final String family : glyphs.stream().map(g -> g.source().event().id).distinct().toList()) {
        clear(); final var familyGlyphs = glyphs.stream().filter(g -> g.source().event().id.equals(family)).toList(); final var event = familyGlyphs.getFirst().source().event();
        final int[] original = memory(event); GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, original); invoke(module, listener, event);
        for(final Glyph glyph : familyGlyphs) {
          reset(); final var selection = selected(glyph); final Obj obj = object(glyph);
          try {
            final Pixels authored = draw(obj, batch, api, target, colour, emission, true, false);
            draws.put(pin(authored).put("id", glyph.entry().getString("id")).put("resource", glyph.entry().getString("resource")).put("quad", glyph.quad().toString()));
            final String name = glyph.entry().getString("resource").replace(".png", "");
            if(!retained.add(name)) continue;
            final Pixels nativePixels = draw(obj, batch, api, target, colour, emission, false, false);
            save(gpuProof, name + "-native", nativePixels); save(gpuProof, name + "-authored", authored);
            final var record = new JSONObject().put("resource", name).put("id", glyph.entry().getString("id")).put("native", pin(nativePixels)).put("authored", pin(authored));
            // Give every actual authored index a decisive, explicitly diagnostic live palette.
            final int[] live = original.clone(); final int clutAddress = glyph.quad().clutY() * 1024 + glyph.quad().clutX();
            for(int index = 0; index < 16; index++) live[clutAddress + index] = index == 0 ? 0 : 0x03e0;
            GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, live);
            final Pixels green = draw(obj, batch, api, target, colour, emission, true, false);
            for(int index = 1; index < 16; index++) live[clutAddress + index] = 0x001f;
            GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, live);
            final Pixels red = draw(obj, batch, api, target, colour, emission, true, false);
            require(!Arrays.equals(green.colour, red.colour), "Every authored resource follows live CLUT: " + name);
            save(gpuProof, name + "-live-green", green); save(gpuProof, name + "-live-red", red);
            // A changed original index must suppress authored coverage at that native cell.
            final int[] overwritten = original.clone(); final int u = glyph.entry().getJSONArray("crop").getInt(0), v = glyph.entry().getJSONArray("crop").getInt(1);
            for(int y = 0; y < glyph.indices().height; y++) for(int x = 0; x < glyph.indices().width; x++) {
              final int address = (event.imageY + v + y) * 1024 + event.imageX + (u + x) / 4, shift = (u + x) % 4 * 4;
              final int old = overwritten[address] >>> shift & 15; overwritten[address] = overwritten[address] & ~(15 << shift) | (old + 1 & 15) << shift;
            }
            GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, overwritten);
            final Pixels overwrittenNative = draw(obj, batch, api, target, colour, emission, false, false), overwrittenAuthored = draw(obj, batch, api, target, colour, emission, true, false);
            require(Arrays.equals(overwrittenNative.colour, overwrittenAuthored.colour) && Arrays.equals(overwrittenNative.emission, overwrittenAuthored.emission), "Source overwrite restores exact native attachments: " + name);
            save(gpuProof, name + "-overwrite-native", overwrittenNative); save(gpuProof, name + "-overwrite-authored", overwrittenAuthored);
            GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, original);
            record.put("liveGreen", pin(green)).put("liveRed", pin(red)).put("overwriteNative", pin(overwrittenNative)).put("overwriteAuthored", pin(overwrittenAuthored)); controls.put(record);
          } finally { reset(); obj.delete(); }
        }
        // This synthetic nearest-index control checks blend/scissor/tint/alpha equivalence;
        // its pixels are retained separately and never described as approved redraw artwork.
        final Glyph one = familyGlyphs.getFirst(); clear(); final Obj obj = object(one);
        try {
          final int[] controlledMemory = original.clone(); final int clutAddress = one.quad().clutY() * 1024 + one.quad().clutX();
          controlledMemory[clutAddress] = 0;
          for(int index = 1; index < 16; index++) controlledMemory[clutAddress + index] = 0x83e0;
          GameEngine.GPU.vramTexture15.data(0, 0, 1024, 512, TextureDataType.UINT, controlledMemory);
          final Pixels baseline = draw(obj, batch, api, target, colour, emission, false, true);
          require(NativeSpriteFont.register(UiHdPreparedFontGpuProbe.class, "synthetic-nearest", one.quad(), one.indices(), nearest(one.indices())), "Synthetic coverage control");
          final Pixels coverage = draw(obj, batch, api, target, colour, emission, true, true);
          require(foreground(baseline) > 0 && foreground(coverage) > 0, "Controlled blend/scissor/tint oracle must render visible native and coverage pixels");
          require(Arrays.equals(baseline.colour, coverage.colour) && Arrays.equals(baseline.emission, coverage.emission), "Controlled blend/scissor/tint preserves native RGBA attachments: " + family);
          save(gpuProof, family + "-synthetic-native", baseline); save(gpuProof, family + "-synthetic-coverage", coverage);
          controls.put(new JSONObject().put("family", family).put("syntheticControlOnly", true).put("diagnosticLivePalette", "index0 transparent; indices1..15 STP green0x83e0")
            .put("blend", "HALF_B_PLUS_HALF_F").put("alpha", .5).put("tint", .75).put("scissor", new int[]{10, 8, 30, 16})
            .put("nativeForegroundPixels", foreground(baseline)).put("coverageForegroundPixels", foreground(coverage)).put("native", pin(baseline)).put("coverage", pin(coverage)));
        } finally { clear(); obj.delete(); }
      }
      report.put("gpuSelectors", draws).put("gpuResourceControls", controls).put("gpuUniqueResources", retained.size()).put("gpuDimensions", new int[]{WIDTH, HEIGHT});
    } catch(final Throwable problem) { failure = problem; throw problem; }
    finally {
      try { clear(); if(api != null) api.output = 0; if(target != null) target.delete(); GameEngine.RENDERER.delete(); Obj.deleteObjects(); Texture.deleteTextures(); require(glGetError() == GL_NO_ERROR, "GPU cleanup"); }
      catch(final Throwable problem) { if(failure == null) throw problem; failure.addSuppressed(problem); }
      finally { final var close = NativeRendererProbe.class.getDeclaredMethod("close", long.class); close.setAccessible(true); close.invoke(null, context); GL.setCapabilities(null); }
    }
  }
}
