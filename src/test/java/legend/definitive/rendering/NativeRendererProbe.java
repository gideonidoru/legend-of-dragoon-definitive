package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Optional actual-backend GPU acceptance executable, never launched by ordinary tests. */
public final class NativeRendererProbe {
  private static native long open();
  private static native void close(long context);
  private static void require(final boolean value, final String message) {
    if(!value) throw new AssertionError(message);
  }
  private static int program(final Shader<?> shader) throws Exception {
    final var field = shader.getClass().getDeclaredField("shader"); field.setAccessible(true); return field.getInt(shader);
  }
  private static void filtering(final boolean enabled) throws Exception {
    // Exercise live sampler settings without booting mods or posting gameplay events.
    final var setter = legend.game.saves.ConfigCollection.class.getDeclaredMethod("setConfigQuietly", legend.game.saves.ConfigEntry.class, Object.class);
    setter.setAccessible(true);
    setter.invoke(GameEngine.CONFIG, CoreMod.HD_TEXTURE_FILTERING_CONFIG.get(), enabled);
  }

  private static byte[] pixels(final Texture texture) {
    texture.use(0); glActiveTexture(GL_TEXTURE0);
    final ByteBuffer data = ByteBuffer.allocateDirect(texture.width * texture.height * 4);
    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    final byte[] result = new byte[data.capacity()]; data.get(result); return result;
  }

  private static void verifySmaa(final GlApi api, final boolean benchmark) {
    final int size = 64;
    final ByteBuffer data = ByteBuffer.allocateDirect(size * size * 4), mask = ByteBuffer.allocateDirect(size * size);
    for(int y = 0; y < size; y++) for(int x = 0; x < size; x++) {
      final int value = x > y * 0.6f + 10 ? 220 : 20;
      final boolean ui = x >= 40 && x < 48 && y >= 8 && y < 16;
      data.put((byte)(ui ? 240 : value)).put((byte)(ui ? 30 : value)).put((byte)(ui ? 180 : value)).put((byte)255);
      mask.put((byte)(ui ? 255 : 0));
    }
    data.flip(); mask.flip();
    final Texture source = api.makeTexture(data, "SMAA acceptance scene", size, size, TextureInternalFormat.RGBA_8, TextureDataFormat.RGBA, TextureDataType.UBYTE, true, false, false, false);
    final Texture coverage = api.makeTexture(mask, "SMAA acceptance UI", size, size, TextureInternalFormat.R_8, TextureDataFormat.RED, TextureDataType.UBYTE, false, false, false, false);
    final Mesh quad = api.makeMesh("SMAA probe quad", VertexOrder.TRIANGLES, new float[] {-1,-1,0,0, 1,-1,1,0, 1,1,1,1, -1,-1,0,0, 1,1,1,1, -1,1,0,1}, 6);
    quad.attribute(0, 0, 2, 4); quad.attribute(1, 2, 2, 4);
    final SmaaPipeline smaa = new SmaaPipeline();
    final byte[] original = pixels(source), result = pixels(smaa.apply(api, source, coverage, quad, .5f, true));
    require(smaa.available(), "SMAA shipping shaders compile and pipeline remains available");
    int changed = 0;
    for(int y = 0; y < size; y++) for(int x = 0; x < size; x++) {
      final int p = (y * size + x) * 4;
      if(result[p] != original[p]) changed++;
      if(x >= 39 && x <= 48 && y >= 7 && y <= 16 || x < 5 || x > 58) {
        for(int c = 0; c < 4; c++) require(result[p+c] == original[p+c], "SMAA preserves UI/fringe and flat regions at " + x + "," + y + " channel " + c + ": " + (result[p+c]&255) + " vs " + (original[p+c]&255));
      }
    }
    require(changed > 0 && changed < size * size / 4, "SMAA reconstructs diagonal edges without changing the whole image");
    require(java.util.Arrays.equals(result, pixels(smaa.apply(api, source, coverage, quad, .5f, true))), "SMAA is spatial and deterministic");
    require(smaa.apply(api, source, coverage, quad, 0, true) == source, "zero-strength SMAA bypasses all passes");
    source.delete(); coverage.delete();
    if(benchmark) {
      final int w = 1536, h = 960;
      final ByteBuffer large = ByteBuffer.allocateDirect(w*h*4);
      for(int y = 0; y < h; y++) for(int x = 0; x < w; x++) {
        final int value = (x+y/2)/32%2 == 0 ? 30 : 220;
        large.put((byte)value).put((byte)value).put((byte)value).put((byte)255);
      }
      large.flip();
      final Texture scene = api.makeTexture(large,"SMAA timing scene",w,h,TextureInternalFormat.RGBA_8,TextureDataFormat.RGBA,TextureDataType.UBYTE,true,false,false,false);
      final Texture ui = api.makeTexture(ByteBuffer.allocateDirect(w*h),"SMAA timing mask",w,h,TextureInternalFormat.R_8,TextureDataFormat.RED,TextureDataType.UBYTE,false,false,false,false);
      for(int i=0;i<10;i++) smaa.apply(api,scene,ui,quad,.5f,true);
      glFinish();
      final int query = glGenQueries();
      final long[] samples = new long[30];
      for(int i=0;i<samples.length;i++) {
        glBeginQuery(org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED,query);
        smaa.apply(api,scene,ui,quad,.5f,true);
        glEndQuery(org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED);
        samples[i] = org.lwjgl.opengl.GL33C.glGetQueryObjectui64(query,GL_QUERY_RESULT);
      }
      glDeleteQueries(query); java.util.Arrays.sort(samples);
      System.out.printf(java.util.Locale.ROOT,"SMAA 1536x960, 30 GPU samples after 10 warmups: median %.3f ms, p95 %.3f ms; driver %s / %s. Not Steam Deck or whole-frame timings.%n",samples[15]/1e6,samples[28]/1e6,glGetString(GL_RENDERER),glGetString(GL_VERSION));
      scene.delete(); ui.delete();
    }
    smaa.delete(); quad.delete(); Texture.deleteTextures();
    require(glGetError() == GL_NO_ERROR, "SMAA GPU lifecycle has no errors");
    System.out.println("PASS: shipping SMAA shaders and lookup tables reconstruct diagonal edges, preserve UI/fringe/flat colors, repeat exactly and bypass at zero strength.");
  }
  public static final class UiProbeListener {
    boolean enabled;
    @org.legendofdragoon.modloader.events.EventListener
    public void image(final legend.game.textures.UiTextureEvent event) {
      if(!this.enabled) return;
      final var original=event.original();final byte[] rgba=new byte[original.data.length*4];
      for(int y=0;y<original.height*2;y++)for(int x=0;x<original.width*2;x++) {
        final int from=(y/2*original.width+x/2)*4,to=(y*original.width*2+x)*4;
        System.arraycopy(original.data,from,rgba,to,4);
        if(rgba[to+3]==0)rgba[to]=rgba[to+1]=rgba[to+2]=0;
      }
      event.replace(original,new legend.game.textures.Image(rgba,original.width*2,original.height*2));
    }
  }

  private static void verifyUiLifecycle() throws Exception {
    final var eventAccessField=GameEngine.class.getDeclaredField("EVENT_ACCESS");eventAccessField.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)eventAccessField.get(null)).initialize(GameEngine.MODS);
    final UiProbeListener owner=new UiProbeListener();GameEngine.EVENTS.register(owner);
    final var reload=GameEngine.class.getDeclaredMethod("reloadUiTexture");reload.setAccessible(true);
    reload.invoke(null);final Texture original=GameEngine.getUiTexture();require(original.width==32&&original.height==16,"disabled UI owner loads original checkbox pixels");
    owner.enabled=true;reload.invoke(null);final Texture restored=GameEngine.getUiTexture();
    require(restored.width==64&&restored.height==32&&GameEngine.getUiWidth()==32&&GameEngine.getUiHeight()==16,"enabled UI owner enlarges artwork but preserves logical coordinates");
    original.use(0);final int oldId=glGetInteger(GL_TEXTURE_BINDING_2D);Texture.deleteTextures();require(!glIsTexture(oldId),"persistent checkbox predecessor retires after replacement");
    owner.enabled=false;reload.invoke(null);require(GameEngine.getUiTexture().width==32,"disabling UI owner restores source sheet");
    owner.enabled=true;reload.invoke(null);require(GameEngine.getUiTexture().width==64,"re-enabling UI owner restores enlarged sheet");

    final var source=new legend.game.textures.Image(new byte[]{(byte)131,0,0,0},1,1);
    final var enhanced=new legend.game.textures.Image(new byte[]{(byte)255,0,80,0,(byte)255,0,80,0,(byte)255,0,80,0,(byte)255,0,80,0},2,2);
    final var binding=new legend.game.textures.NativeUiTextures.Binding(0,0,0,0,1,1);
    require(legend.game.textures.NativeUiTextures.register(binding,source,enhanced),"native region registers once");
    require(!legend.game.textures.NativeUiTextures.register(binding,source,enhanced),"earlier native owner wins");
    final var regions=legend.game.textures.NativeUiTextures.class.getDeclaredField("REGIONS");regions.setAccessible(true);
    final Object pending=((java.util.List<?>)regions.get(null)).getFirst();
    final var prepare=legend.game.textures.NativeUiTextures.class.getDeclaredMethod("prepare",pending.getClass());prepare.setAccessible(true);
    final var artwork=pending.getClass().getDeclaredField("artworkTexture");artwork.setAccessible(true);
    legend.game.textures.NativeUiTextures.clear();prepare.invoke(null,pending);
    require(artwork.get(pending)==null&&legend.game.textures.NativeUiTextures.allocatedBytes()==0,"retired preparation cannot resurrect native textures");
    require(legend.game.textures.NativeUiTextures.register(binding,source,enhanced),"native selection can register after reboot");
    final Object live=((java.util.List<?>)regions.get(null)).getFirst();prepare.invoke(null,live);
    final Texture nativeTexture=(Texture)artwork.get(live);require(java.util.Arrays.equals(enhanced.data,pixels(nativeTexture)),"native artwork uploads exactly without interpreting STP as PNG opacity");
    nativeTexture.use(0);final int nativeId=glGetInteger(GL_TEXTURE_BINDING_2D);
    legend.game.textures.NativeUiTextures.clear();Texture.deleteTextures();require(!glIsTexture(nativeId),"native selection releases persistent GPU textures");
    GameEngine.getUiTexture().delete();Texture.deleteTextures();
    require(glGetError()==GL_NO_ERROR,"UIHD backend lifecycle has no GL errors");
    System.out.println("PASS: source-bound UI disable/re-enable, logical dimensions, predecessor deletion, native first-owner precedence, stale preparation rejection and exact STP upload/deletion.");
  }

  public static void main(final String[] args) throws Exception {
    System.load(args[0]);
    final long context = open(); require(context != 0, "windowless context");
    try {
      GL.createCapabilities();
      final GlApi api = new GlApi();
      final var apiField = GameEngine.RENDERER.getClass().getDeclaredField("api"); apiField.setAccessible(true); apiField.set(GameEngine.RENDERER, api);
      CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      final Path directory = Files.createTempDirectory("renderer-probe-");
      final Path vertex = directory.resolve("probe.vsh"), fragment = directory.resolve("probe.fsh");
      final String vs = "#version 330 core\nout vec2 linkValue; void main(){ linkValue=vec2(1); gl_Position=vec4(0,0,0,1); }";
      final String fs = "#version 330 core\nin vec2 linkValue; uniform float shade; out vec4 colour; void main(){ colour=vec4(shade*linkValue,0,1); }";
      Files.writeString(vertex, vs); Files.writeString(fragment, fs);
      final Shader<ShaderOptions> shader = api.makeShader("reload probe", vertex, fragment, current -> () -> () -> {});
      final ShaderUniformFloat handle = shader.uniformFloat("shade");
      require(handle == shader.uniformFloat("shade"), "uniform handles are cached");
      final int original = program(shader);
      Files.writeString(fragment, "#version 330 core\ninvalid shader"); shader.reload();
      require(program(shader) == original && glIsProgram(original), "compile failure retains original program");
      Files.delete(fragment);
      try {
        shader.reload();
        throw new AssertionError("missing shader should report an IO failure");
      } catch(final java.io.IOException expected) {
        require(program(shader) == original && glIsProgram(original), "missing source retains original program");
      }
      Files.writeString(vertex, vs.replace("out vec2", "out vec3").replace("linkValue=vec2", "linkValue=vec3")); Files.writeString(fragment, fs); shader.reload();
      require(program(shader) == original && glIsProgram(original), "link failure retains original program");
      Files.writeString(vertex, vs);
      Files.writeString(fragment, fs.replace("uniform float shade;", "uniform vec3 earlier; uniform float shade;").replace("vec4(shade*linkValue,0,1)", "vec4(shade*linkValue,earlier.x,1)"));
      shader.reload(); shader.use(); handle.set(.75f);
      final int replacement = program(shader);
      require(replacement != original && !glIsProgram(original), "successful reload replaces and deletes old program");
      require(handle == shader.uniformFloat("shade"), "existing options retain the same refreshed handle");
      require(glGetUniformf(replacement, glGetUniformLocation(replacement, "shade")) == .75f, "old handle writes the new program's correct uniform");
      shader.delete();
      System.out.println("PASS: actual OpenGL backend uniform caching, compile/link rollback, successful replacement and existing-handle refresh.");

      final Texture colour = api.makeTexture(ByteBuffer.allocateDirect(64 * 64 * 4), "color", 64, 64, TextureInternalFormat.RGBA_8, TextureDataFormat.RGBA, TextureDataType.UBYTE, false, false, false, false);
      final Texture glow = api.makeTexture(null, "glow", 64, 64, TextureInternalFormat.RGBA_8, TextureDataFormat.RGBA, TextureDataType.UBYTE, false, false, false, false);
      final Texture mask = api.makeTexture(null, "mask", 64, 64, TextureInternalFormat.R_8, TextureDataFormat.RED, TextureDataType.UBYTE, false, false, false, false);
      final FrameBuffer buffer = api.makeFrameBuffer("MRT", new FrameBufferAttachment[] {new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR, colour), new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR, glow), new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR, mask)});
      buffer.bind(); require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "backend MRT/R8 completeness");
      api.translucency(Translucency.B_PLUS_F); require(!glIsEnabledi(GL_BLEND, 2), "mask blending disabled when scene blending enabled");
      api.postProcessMask(false); final ByteBuffer flags = ByteBuffer.allocateDirect(4); glGetBooleani_v(GL_COLOR_WRITEMASK, 2, flags); require(flags.get(0) == 0, "mask writes disabled");
      api.postProcessMask(true); glGetBooleani_v(GL_COLOR_WRITEMASK, 2, flags); require(flags.get(0) == 1, "mask writes restored");
      api.clearPostProcessTargets(); glReadBuffer(GL_COLOR_ATTACHMENT2); final ByteBuffer pixel = ByteBuffer.allocateDirect(4); glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel); require(pixel.get(0) == 0, "mask cleared to zero");
      api.translucency(null); glBindFramebuffer(GL_FRAMEBUFFER, 0);
      colour.hdAtlasFiltering(8); colour.use(0);
      require(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) == GL_LINEAR_MIPMAP_LINEAR, "backend trilinear filtering");
      require(glGetTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL) == 1, "gutter bounds anisotropic mip footprint");
      filtering(false); colour.use(0);
      require(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) == GL_NEAREST, "off restores original filtering");
      filtering(true); colour.use(0);
      mask.use(1); // Leave another unit active while colour is already cached on unit zero.
      colour.data(0,0,1,1,TextureDataType.UBYTE,new int[] {0xff00ff00}); colour.use(0);
      glActiveTexture(GL_TEXTURE0);
      final ByteBuffer uploaded = ByteBuffer.allocateDirect(64 * 64 * 4);
      glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, uploaded);
      require(uploaded.get(0) == 0 && (uploaded.get(1) & 0xff) == 255 && uploaded.get(2) == 0 && (uploaded.get(3) & 0xff) == 255, "updates reach the correct cached texture after a unit switch");
      glGetTexImage(GL_TEXTURE_2D, 1, GL_RGBA, GL_UNSIGNED_BYTE, uploaded);
      require((uploaded.get(1) & 0xff) > 0, "updated HD pixels regenerate mipmaps");
      require(glGetError() == GL_NO_ERROR, "backend lifecycle has no GL errors");
      buffer.delete(); colour.delete(); glow.delete(); mask.delete(); Texture.deleteTextures();
      Files.delete(vertex); Files.delete(fragment); Files.delete(directory);
      System.out.println("PASS: actual OpenGL backend MRT/R8 setup, blend/mask state, auxiliary clears, atlas mip cap, filtering toggles, texture updates and deletion.");
      verifyUiLifecycle();
      verifySmaa(api, args.length > 1 && args[1].equals("benchmark"));
      require(DefaultMaterialMaps.bind(),"shared default surface maps load");
      glActiveTexture(GL_TEXTURE4); final int normalId=glGetInteger(GL_TEXTURE_BINDING_2D);
      final ByteBuffer normals=ByteBuffer.allocateDirect(DefaultMaterialMaps.SIZE*DefaultMaterialMaps.SIZE*4);
      glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,normals);
      require(normals.equals(DefaultMaterialMaps.pixels(true)),"uploaded default normals match generated linear data exactly");
      require(glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER)==GL_LINEAR_MIPMAP_LINEAR,"default surface maps use bounded mip filtering");
      glActiveTexture(GL_TEXTURE5); final int roughnessId=glGetInteger(GL_TEXTURE_BINDING_2D);
      glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,normals);
      require(normals.equals(DefaultMaterialMaps.pixels(false)),"uploaded default roughness matches generated data exactly");
      require(DefaultMaterialMaps.bind(),"default maps reuse existing textures");
      glActiveTexture(GL_TEXTURE4); require(glGetInteger(GL_TEXTURE_BINDING_2D)==normalId,"default map reuse does not allocate again");
      DefaultMaterialMaps.delete(); Texture.deleteTextures();
      require(!glIsTexture(normalId)&&!glIsTexture(roughnessId)&&glGetError()==GL_NO_ERROR,"default map ownership and deletion have no GL errors");
      System.out.println("PASS: generated default maps upload exactly, use mip filtering, reuse shared textures and delete cleanly.");
    } finally { close(context); }
  }
}
