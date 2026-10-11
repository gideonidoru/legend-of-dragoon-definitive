package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL20C.GL_BLEND_EQUATION_RGB;

/** Opt-in windowless GPU regression; ordinary tests never open a graphics context. */
public final class NativeDepthClearProbe {
  private static native long open();
  private static native void close(long context);
  private static final JSONArray cases = new JSONArray();
  private static int failures;

  private static void field(final String name, final Object value) throws ReflectiveOperationException {
    final var field = GameEngine.RENDERER.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(GameEngine.RENDERER, value);
  }

  private static float depth() {
    final var value = ByteBuffer.allocateDirect(Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    glReadPixels(0, 0, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, value);
    return value.get(0);
  }

  private static void check(final String name, final float expectedDepth, final boolean writable, final boolean blending, final int[] blendState) {
    final float actualDepth = depth();
    final boolean actualWritable = glGetBoolean(GL_DEPTH_WRITEMASK);
    final boolean actualBlending = glIsEnabled(GL_BLEND);
    final int error = glGetError();
    final boolean unchangedBlend = blendState == null || blendState[0] == glGetInteger(GL_BLEND_SRC_RGB)
      && blendState[1] == glGetInteger(GL_BLEND_DST_RGB) && blendState[2] == glGetInteger(GL_BLEND_EQUATION_RGB);
    final boolean passed = Math.abs(actualDepth - expectedDepth) < .001f && actualWritable == writable
      && actualBlending == blending && unchangedBlend && error == GL_NO_ERROR;
    cases.put(new JSONObject().put("name", name).put("expectedDepth", expectedDepth).put("depth", actualDepth)
      .put("depthWritable", actualWritable).put("blending", actualBlending).put("blendFunctionPreserved", unchangedBlend)
      .put("glError", error).put("passed", passed));
    System.out.printf("%s %s: depth=%.3f expected=%.3f writable=%s blend=%s error=%d%n",
      passed ? "PASS" : "FAIL", name, actualDepth, expectedDepth, actualWritable, actualBlending, error);
    if(!passed) failures++;
  }

  private static void seed(final GlApi api, final float value) {
    api.translucency(null);
    glClearDepth(value);
    api.clear(false, true, false);
  }

  private static int[] blendState() {
    return new int[] {glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB), glGetInteger(GL_BLEND_EQUATION_RGB)};
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("Supply windowless JNI library and JSON output path");
    System.load(Path.of(args[0]).toAbsolutePath().toString());
    final long context = open();
    if(context == 0) throw new IllegalStateException("Windowless accelerated CGL context unavailable; this is not a test pass");
    try {
      GL.createCapabilities();
      final GlApi api = new GlApi();
      field("api", api);
      api.init();
      api.resize(4, 4);
      api.viewport(0, 0, 4, 4);
      CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
      field("camera2d", new BasicCamera(0, 0));
      field("camera3d", new QuaternionCamera(0, 0, 0));
      final var transforms = api.makeUniformBuffer(32 * Float.BYTES, ShaderUniformBuffer.TRANSFORM);
      final var projection = api.makeUniformBuffer(4 * Float.BYTES, ShaderUniformBuffer.PROJECTION_INFO);
      field("transformsUniform", transforms);
      field("projectionUniform", projection);
      GameEngine.GTE.setProjectionPlaneDistance(256);
      final var renderBatch = GameEngine.RENDERER.getClass().getDeclaredMethod("renderBatch", RenderBatch.class);
      renderBatch.setAccessible(true);
      final var mainBatchField = GameEngine.RENDERER.getClass().getDeclaredField("mainBatch");
      mainBatchField.setAccessible(true);
      final var mainBatch = (RenderBatch)mainBatchField.get(GameEngine.RENDERER);

      final var colour = api.makeTexture(null, "Depth clear probe colour", 4, 4, TextureInternalFormat.RGBA_8,
        TextureDataFormat.RGBA, TextureDataType.UBYTE, false, false, false, false);
      final var depth = api.makeTexture(null, "Depth clear probe depth", 4, 4, TextureInternalFormat.DEPTH_COMPONENT,
        TextureDataFormat.DEPTH_COMPONENT, TextureDataType.FLOAT, false, false, false, false);
      final FrameBufferAttachment[] attachments = {
        new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR, colour),
        new FrameBufferAttachment(FrameBufferAttachmentType.DEPTH, depth)
      };
      final var first = api.makeFrameBuffer("Depth clear first pass", attachments);
      final var second = api.makeFrameBuffer("Depth clear shared depth reuse", attachments);
      try {
        first.bind();
        seed(api, .25f);
        check("ordinary opaque depth clear", .25f, true, false, null);
        // An inherited driver write mask must not prevent an API-requested opaque clear.
        glDepthMask(false);
        glClearDepth(1);
        api.clear(false, true, false);
        check("opaque clear with inherited disabled write mask", 1, true, false, null);

        for(final Translucency mode : new Translucency[] {
          Translucency.HALF_B_PLUS_HALF_F, Translucency.B_PLUS_F, Translucency.B_MINUS_F
        }) {
          seed(api, .25f);
          api.translucency(mode);
          final int[] state = blendState();
          glClearDepth(1);
          api.clear(false, true, false);
          check(mode + " depth clear", 1, false, true, state);
          api.translucency(mode); // Cached state must agree with the driver after clearing.
          check(mode + " continued translucent pass", 1, false, true, state);

          seed(api, .5f);
          api.translucency(mode);
          api.clear(true, false, false);
          check(mode + " colour-only clear", .5f, false, true, state);

          // Exercise the real batch entry point (empty pools deliberately retain
          // translucency), not a copied approximation of its clear ordering.
          seed(api, .125f);
          api.translucency(mode);
          glClearDepth(1);
          renderBatch.invoke(GameEngine.RENDERER, mainBatch);
          check(mode + " actual empty renderBatch boundary", 1, false, true, state);

          second.bind();
          for(int pass = 0; pass < 3; pass++) {
            seed(api, .2f + pass * .1f);
            api.translucency(mode);
            glClearDepth(1);
            renderBatch.invoke(GameEngine.RENDERER, new RenderBatch(GameEngine.RENDERER, java.nio.FloatBuffer.allocate(32)));
            check(mode + " shared depth batch reuse " + pass, 1, false, true, state);
          }
          first.bind();
          api.translucency(null);
          check(mode + " following opaque pass", 1, true, false, null);
        }
        final var report = new JSONObject().put("device", glGetString(GL_RENDERER)).put("version", glGetString(GL_VERSION))
          .put("cases", cases).put("failures", failures).put("passed", failures == 0).put("deckCauseConfirmed", false)
          .put("scope", "Actual GlApi depth readback and real empty RenderEngine.renderBatch transitions; macOS CGL, no window, no gameplay or GLES acceptance");
        Files.writeString(Path.of(args[1]), report.toString(2) + System.lineSeparator());
        System.out.printf("%s: %d cases, %d failures; %s / %s%n", failures == 0 ? "PASS" : "FAIL", cases.length(), failures,
          glGetString(GL_RENDERER), glGetString(GL_VERSION));
      } finally {
        first.delete(); second.delete(); colour.delete(); depth.delete(); Texture.deleteTextures();
        transforms.delete(); projection.delete();
      }
      if(glGetError() != GL_NO_ERROR) throw new AssertionError("GPU cleanup error");
      if(failures != 0) throw new AssertionError(failures + " depth-clear regression cases failed");
    } finally {
      close(context);
    }
  }
}
