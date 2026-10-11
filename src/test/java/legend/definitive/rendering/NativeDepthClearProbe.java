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
import java.util.ArrayList;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL20C.GL_BLEND_EQUATION_RGB;

/** Opt-in windowless GPU regression; ordinary tests never open a graphics context. */
public final class NativeDepthClearProbe {
  private static native long open();
  private static native long close(long context, boolean injectDestroyFailure);
  private static final JSONArray cases = new JSONArray();
  private static int failures;
  private record Cleanup(String name, Runnable action) { }

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

  private static void cleanup(final Cleanup step, final JSONArray errors) {
    try {
      step.action.run();
    } catch(final Throwable error) {
      errors.put(new JSONObject().put("step", step.name).put("type", error.getClass().getName())
        .put("message", String.valueOf(error.getMessage())));
    }
  }

  public static void main(final String[] args) throws Exception {
    if(args.length < 2 || args.length > 3 || args.length == 3 && !args[2].equals("--inject-cleanup-failure")) {
      throw new IllegalArgumentException("Supply windowless JNI library and JSON output path, optionally --inject-cleanup-failure");
    }
    final boolean injectCleanupFailure = args.length == 3;
    final var cleanupErrors = new JSONArray();
    final var cleanup = new JSONObject().put("exceptions", cleanupErrors).put("injectedFailure", injectCleanupFailure);
    final var report = new JSONObject().put("cases", cases).put("cleanup", cleanup).put("deckCauseConfirmed", false)
      .put("scope", "Actual GlApi depth readback and real empty RenderEngine.renderBatch transitions; macOS CGL, no window, no gameplay or GLES acceptance");
    final var actions = new ArrayList<Cleanup>();
    long context = 0;
    boolean capabilitiesCreated = false;
    Throwable executionFailure = null;
    try {
      System.load(Path.of(args[0]).toAbsolutePath().toString());
      context = open();
      if(context == 0) throw new IllegalStateException("Windowless accelerated CGL context unavailable; this is not a test pass");
      GL.createCapabilities();
      capabilitiesCreated = true;
      report.put("device", glGetString(GL_RENDERER)).put("version", glGetString(GL_VERSION));
      final GlApi api = new GlApi();
      field("api", api);
      api.init();
      api.resize(4, 4);
      api.viewport(0, 0, 4, 4);
      CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
      field("camera2d", new BasicCamera(0, 0));
      field("camera3d", new QuaternionCamera(0, 0, 0));
      final var transforms = api.makeUniformBuffer(32 * Float.BYTES, ShaderUniformBuffer.TRANSFORM);
      actions.add(new Cleanup("transforms", transforms::delete));
      final var projection = api.makeUniformBuffer(4 * Float.BYTES, ShaderUniformBuffer.PROJECTION_INFO);
      actions.add(new Cleanup("projection", projection::delete));
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
      actions.add(new Cleanup("colour", colour::delete));
      final var depth = api.makeTexture(null, "Depth clear probe depth", 4, 4, TextureInternalFormat.DEPTH_COMPONENT,
        TextureDataFormat.DEPTH_COMPONENT, TextureDataType.FLOAT, false, false, false, false);
      actions.add(new Cleanup("depth", depth::delete));
      final FrameBufferAttachment[] attachments = {
        new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR, colour),
        new FrameBufferAttachment(FrameBufferAttachmentType.DEPTH, depth)
      };
      final var first = api.makeFrameBuffer("Depth clear first pass", attachments);
      actions.add(new Cleanup("first", first::delete));
      final var second = api.makeFrameBuffer("Depth clear shared depth reuse", attachments);
      actions.add(new Cleanup("second", second::delete));
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
    } catch(final Throwable error) {
      executionFailure = error;
      report.put("executionFailure", new JSONObject().put("type", error.getClass().getName())
        .put("message", String.valueOf(error.getMessage())));
    } finally {
      cleanup.put("glCleanupAttempted", capabilitiesCreated).put("nativeCloseAttempted", context != 0);
      if(capabilitiesCreated) {
        for(int i = actions.size() - 1; i >= 0; i--) cleanup(actions.get(i), cleanupErrors);
        cleanup(new Cleanup("retired textures", Texture::deleteTextures), cleanupErrors);
        cleanup(new Cleanup("GL error validation", () -> {
          final var errors = new JSONArray();
          int error;
          for(int i = 0; i < 32 && (error = glGetError()) != GL_NO_ERROR; i++) errors.put(error);
          cleanup.put("glErrors", errors);
          if(!errors.isEmpty()) throw new AssertionError("GPU cleanup errors: " + errors);
        }), cleanupErrors);
      }
      if(context != 0) {
        final long ownedContext = context;
        cleanup(new Cleanup("native context", () -> {
          final long status = close(ownedContext, injectCleanupFailure);
          final int unbindError = (int)(status >>> 32);
          final int destroyError = (int)status;
          cleanup.put("setCurrentContextError", unbindError).put("destroyContextError", destroyError);
          if(unbindError != 0 || destroyError != 0) throw new AssertionError("CGL cleanup errors: " + unbindError + "," + destroyError);
        }), cleanupErrors);
      }
      if(capabilitiesCreated) cleanup(new Cleanup("thread GL capabilities", () -> GL.setCapabilities(null)), cleanupErrors);
    }
    final boolean passed = executionFailure == null && failures == 0 && cases.length() == 26 && cleanupErrors.isEmpty();
    cleanup.put("passed", cleanupErrors.isEmpty());
    report.put("failures", failures).put("passed", passed);
    // No success report exists until GPU resource cleanup AND native teardown finish.
    Files.writeString(Path.of(args[1]), report.toString(2) + System.lineSeparator());
    System.out.printf("%s: %d cases, %d case failures, %d cleanup failures; %s / %s%n", passed ? "PASS" : "FAIL",
      cases.length(), failures, cleanupErrors.length(), report.optString("device", "unavailable"), report.optString("version", "unavailable"));
    if(!passed) {
      final var error = new AssertionError(failures + " depth-clear cases failed; " + cleanupErrors.length() + " cleanup failures");
      if(executionFailure != null) error.initCause(executionFailure);
      throw error;
    }
  }
}
