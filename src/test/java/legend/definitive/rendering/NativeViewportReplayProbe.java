// Optional windowless GPU regression, AGPL v3; see LICENSE.
package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.platform.WindowEvents;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.game.EngineState;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigRegistryEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Runs the real draw listener with unequal scene/output sizes; never creates a window. */
public final class NativeViewportReplayProbe {
  private static final int WIDTH = 1280, HEIGHT = 800;

  private static final class OffscreenApi extends GlApi {
    private int output;
    private final int[] sceneViewport = new int[4];
    @Override public void unbindFramebuffer() { glBindFramebuffer(GL_FRAMEBUFFER, this.output); }
    @Override public void initBatch(final RenderBatch batch) {
      glGetIntegerv(GL_VIEWPORT, this.sceneViewport);
      super.initBatch(batch);
    }
  }

  private static final class HeadlessWindow extends NoopWindow {
    private final OffscreenApi api;
    HeadlessWindow(final OffscreenApi api) { super(new NoopPlatformManager(), WIDTH, HEIGHT); this.api = api; }
    @Override public RenderApi getRenderApi() { return this.api; }
  }

  private static Object field(final Object object, final String name) throws Exception {
    final var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  private static <T> void config(final ConfigEntry<T> entry, final T value) throws Exception {
    final var setter = ConfigCollection.class.getDeclaredMethod("setConfigQuietly", ConfigEntry.class, Object.class);
    setter.setAccessible(true);
    setter.invoke(GameEngine.CONFIG, entry, value);
  }

  private static byte[] pixels(final Texture texture) {
    texture.use(0);
    glActiveTexture(GL_TEXTURE0);
    final ByteBuffer buffer = ByteBuffer.allocateDirect(texture.width * texture.height * 4);
    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    final byte[] pixels = new byte[buffer.capacity()];
    buffer.get(pixels);
    return pixels;
  }

  private static String footprint(final byte[] pixels, final int width) {
    int minX = width, minY = pixels.length / (width * 4), maxX = -1, maxY = -1, count = 0;
    for(int i = 0; i < pixels.length; i += 4) {
      if(pixels[i] != 0 || pixels[i + 1] != 0 || pixels[i + 2] != 0) {
        final int x = i / 4 % width, y = i / 4 / width;
        minX = Math.min(minX, x); minY = Math.min(minY, y);
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); count++;
      }
    }
    if(count == 0) throw new AssertionError("Stationary scene disappeared");
    return count + " pixels, bounds=" + minX + "," + minY + ".." + maxX + "," + maxY;
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 1) throw new IllegalArgumentException("Supply the windowless renderer JNI library");
    System.load(args[0]);
    final var open = NativeRendererProbe.class.getDeclaredMethod("open");
    final var close = NativeRendererProbe.class.getDeclaredMethod("close", long.class);
    open.setAccessible(true); close.setAccessible(true);
    final long context = (long)open.invoke(null);
    if(context == 0) throw new AssertionError("Windowless context unavailable");
    try {
      GL.createCapabilities();
      final RenderEngine renderer = GameEngine.RENDERER;
      System.out.println("GPU " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
      System.out.println("ENGINE " + RenderEngine.class.getProtectionDomain().getCodeSource().getLocation());
      CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      final OffscreenApi api = new OffscreenApi();
      final HeadlessWindow window = new HeadlessWindow(api);
      final var init = RenderEngine.class.getDeclaredMethod("init", Window.class);
      init.setAccessible(true); init.invoke(renderer, window);
      final Texture output = Texture.create("Offscreen presentation", builder -> builder.size(WIDTH, HEIGHT));
      output.persistent = true;
      final FrameBuffer outputBuffer = FrameBuffer.create("Offscreen presentation", builder -> builder.attachment(FrameBufferAttachmentType.COLOUR, output));
      outputBuffer.bind();
      api.output = glGetInteger(GL_FRAMEBUFFER_BINDING);
      GameEngine.GPU.vramTexture15 = api.makeTexture(BufferUtils.createIntBuffer(1024 * 512), "Synthetic VRAM", 1024, 512,
        TextureInternalFormat.R_32_UINT, TextureDataFormat.RED_INT, TextureDataType.UINT, false, false, false, false);
      GameEngine.GPU.vramTexture15.persistent = true;
      final Obj quad = new QuadBuilder("Stationary placement markers").monochrome(1).size(12, 12).build();
      quad.persistent = true;
      final AtomicLong time = new AtomicLong();
      final var clock = RenderEngine.class.getDeclaredField("simulationClock");
      clock.setAccessible(true); clock.set(renderer, new SimulationClock(time::get));
      final int[] ticks = {0};
      renderer.setSimulationCallback(() -> {
        ticks[0]++;
        for(int i = 0; i < 300; i++) {
          renderer.queueUiOrthoModel(quad, new Matrix4f().translation(i % 20 * 15, i / 20 * 15, 100), QueuedModelStandard.class)
            .colour((i % 3 + 1) / 3.0f, (i % 5 + 1) / 5.0f, (i % 7 + 1) / 7.0f);
        }
      });
      final var draw = WindowEvents.class.getDeclaredMethod("onDraw");
      draw.setAccessible(true);
      int presentations = 0, reused = 0, cases = 0;
      for(final Resolution resolution : new Resolution[] {Resolution._1440, Resolution._480, Resolution.NATIVE}) {
        for(final boolean smaa : new boolean[] {false, true}) {
          for(final boolean legacy : new boolean[] {false, true}) {
            config(CoreMod.RESOLUTION_CONFIG.get(), resolution);
            config(CoreMod.SMAA_CONFIG.get(), smaa);
            config(CoreMod.EDGE_SMOOTHING_CONFIG.get(), .5f);
            config(CoreMod.LEGACY_WIDESCREEN_MODE_CONFIG.get(), SubmapWidescreenMode.FORCED_4_3);
            renderer.setRenderMode(legacy ? EngineState.RenderMode.LEGACY : EngineState.RenderMode.PERSPECTIVE);
            window.show();
            byte[] reference = null, outputReference = null;
            int before = ticks[0];
            for(int frame = 0; frame < 80; frame++) {
              time.addAndGet(frame % 10 == 0 ? 100_000_000 : frame % 3 == 0 ? 1_000_000 : 16_666_667);
              final int previousTicks = ticks[0];
              draw.invoke(window.events());
              final int index = (int)field(renderer, "renderBufferIndex");
              final Texture scene = ((Texture[])field(renderer, "renderTextures"))[index];
              final byte[] image = pixels(scene), presentation = pixels(output);
              if(reference == null) { reference = image; outputReference = presentation; }
              else if(!Arrays.equals(reference, image) || !Arrays.equals(outputReference, presentation)) {
                throw new AssertionError("Stationary scene scaled/flickered: resolution=" + resolution + " smaa=" + smaa + " legacy=" + legacy
                  + " frame=" + frame + " gameplaySteps=" + (ticks[0] - previousTicks) + " viewport=" + Arrays.toString(api.sceneViewport)
                  + " reference=" + footprint(reference, scene.width) + " actual=" + footprint(image, scene.width));
              }
              if(ticks[0] == previousTicks) reused++;
              if(glGetError() != GL_NO_ERROR) throw new AssertionError("GPU error");
              presentations++;
            }
            cases++;
            System.out.println("PASS resolution=" + resolution + " internal=" + renderer.getRenderWidth() + "x" + renderer.getRenderHeight()
              + " output=" + WIDTH + "x" + HEIGHT + " smaa=" + smaa + " legacy=" + legacy + " ticks=" + (ticks[0] - before));
          }
        }
      }
      if(reused == 0) throw new AssertionError("No retained simulation frames exercised");
      System.out.println("SUMMARY cases=" + cases + " presentations=" + presentations + " zeroStepPresentations=" + reused + " GLerrors=0 verdict=PASS");
      renderer.delete();
      outputBuffer.delete(); output.delete();
    } finally { close.invoke(null, context); }
  }
}
