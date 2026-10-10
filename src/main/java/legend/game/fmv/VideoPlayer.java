package legend.game.fmv;

import legend.definitive.fmv.StreamingMovie;
import legend.definitive.fmv.MoviePlayback;
import legend.core.audio.GenericSource;
import legend.core.gpu.Bpp;
import legend.core.platform.WindowEvents;
import legend.core.renderer.Obj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import legend.game.EngineState;
import legend.game.modding.coremod.CoreMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static legend.core.GameEngine.AUDIO_THREAD;
import static legend.core.GameEngine.CONFIG;
import static legend.core.GameEngine.DISCORD;
import static legend.core.GameEngine.PLATFORM;
import static legend.core.GameEngine.RENDERER;
import static legend.game.Graphics.clearBlue_800babc0;
import static legend.game.Graphics.clearGreen_800bb104;
import static legend.game.Graphics.clearRed_8007a3a8;
import static legend.game.modding.coremod.CoreMod.ALLOW_WIDESCREEN_CONFIG;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;

public final class VideoPlayer {
  private VideoPlayer() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(VideoPlayer.class);

  private static Runnable oldRenderer;
  private static int oldFps;
  private static int oldInputTickRate;
  private static java.util.function.Consumer<Boolean> oldPauseCallback;
  private static boolean oldCinematicPlayback;
  private static boolean oldAllowWidescreen;

  private static StreamingMovie movie;
  private static boolean stopping;
  private static MoviePlayback playback;

  private static int videoWidth;
  private static int videoHeight;

  private static GenericSource source;
  private static ByteBuffer imageBuffer;

  private static WindowEvents.KeyPressed keyPress;
  private static WindowEvents.ButtonPressed buttonPressed;
  private static WindowEvents.Click click;
  private static boolean shouldStop;

  private static Obj texturedObj;
  private static Texture displayTexture;
  private static final Vector2i oldProjectionSize = new Vector2i();
  private static EngineState.RenderMode oldRenderMode;
  private static final Vector3i oldClearColour = new Vector3i();
  private static final Matrix4f transforms = new Matrix4f();

  private static boolean stateCaptured;
  private static Runnable onRender;
  private static Runnable onFinish;

  public static void play(final Path video, @Nullable final Runnable onRender, @Nullable final Runnable onFinish) throws IOException {
    LOGGER.info("Playing FMV %s", video);

    if(movie != null) throw new IOException("A movie is already playing");
    VideoPlayer.onRender = onRender;
    VideoPlayer.onFinish = onFinish;
    shouldStop = false;
    stopping = false;
    stateCaptured = false;

    try {
      movie = new StreamingMovie(video);
      playback = new MoviePlayback(movie);
      videoWidth = movie.width;
      videoHeight = movie.height;
      oldAllowWidescreen = CONFIG.getConfig(ALLOW_WIDESCREEN_CONFIG.get());
      oldFps = RENDERER.window().getFpsLimit();
      oldInputTickRate = PLATFORM.getInputTickRate();
      oldProjectionSize.set(RENDERER.getNativeWidth(), RENDERER.getNativeHeight());
      oldRenderMode = RENDERER.getRenderMode();
      oldClearColour.set(clearRed_8007a3a8, clearGreen_800bb104, clearBlue_800babc0);

      oldRenderer = RENDERER.setRenderCallback(() -> { });
      stateCaptured = true;
      oldCinematicPlayback = RENDERER.setCinematicPlayback(true);
      RENDERER.window().setFpsLimit(60);
      PLATFORM.setInputTickRate(60);
      imageBuffer = MemoryUtil.memAlloc(videoWidth * videoHeight * 3);

      LOGGER.info("Video size %dx%d", videoWidth, videoHeight);

      displayTexture = Texture.create("Video", builder -> {
        builder.size(videoWidth, videoHeight);
        builder.internalFormat(TextureInternalFormat.RGB_8);
        builder.dataFormat(TextureDataFormat.RGB);
        builder.minFilter(true);
        builder.magFilter(true);
      });

      CONFIG.setConfig(ALLOW_WIDESCREEN_CONFIG.get(), true);
      RENDERER.setRenderMode(EngineState.RenderMode.PERSPECTIVE);
      RENDERER.setProjectionSize(320, 240);
      RENDERER.api().clearColour(0.0f, 0.0f, 0.0f);

      keyPress = RENDERER.events().onKeyPress((window, key, scancode, mods, repeat) -> requestStop());
      buttonPressed = RENDERER.events().onButtonPress((window, action, repeat) -> requestStop());
      click = RENDERER.events().onMouseRelease((window, x, y, button, mods) -> requestStop());

      source = AUDIO_THREAD.addSource(new GenericSource(AL_FORMAT_STEREO16, 48_000));
      oldPauseCallback = RENDERER.setCinematicPauseCallback(VideoPlayer::setPaused);
      final float volume = CONFIG.getConfig(CoreMod.FMV_VOLUME_CONFIG.get()) * CONFIG.getConfig(CoreMod.MASTER_VOLUME_CONFIG.get());

      RENDERER.setRenderCallback(() -> {
        try {
          if(onRender != null) {
            onRender.run();
          }

          if(shouldStop) {
            stop();
            return;
          }

          RENDERER.window().setFpsLimit(60);
          PLATFORM.setInputTickRate(60);

          final long playedMicros;
          try { playedMicros = playback.tick(source, volume); }
          catch(final IOException | RuntimeException e) {
            LOGGER.warn("Error while playing video", e);
            stop();
            return;
          }
          final StreamingMovie.VideoFrame image = movie.pollVideo(playedMicros);
          if(image != null) {
            imageBuffer.clear();
            imageBuffer.put(image.rgb()).flip();
            displayTexture.data(0, 0, videoWidth, videoHeight, TextureDataType.UBYTE, imageBuffer);
          }

          if(texturedObj == null) {
            texturedObj = new QuadBuilder("FMV")
              .bpp(Bpp.BITS_24)
              .size(1.0f, 1.0f)
              .build();
          }

          displayTexture.use();

          final float windowHeight = RENDERER.getNativeHeight();
          final float windowWidth = windowHeight * RENDERER.getRenderAspectRatio();

          final float scaleW = windowWidth / videoWidth;
          final float scaleH = windowHeight / videoHeight;
          final float scale = Math.min(scaleW, scaleH);

          final float w = videoWidth * scale;
          final float h = videoHeight * scale;

          final float l = (windowWidth - w) / 2.0f;
          final float t = (windowHeight - h) / 2.0f;

          transforms
            .translation(l, t, 100.0f)
            .scale(w, h, 1.0f)
          ;

          RENDERER.queueOrthoModel(texturedObj, transforms, QueuedModelStandard.class)
            .texture(displayTexture)
          ;

          DISCORD.tick();

          if(movie.drained() && !source.hasQueuedOutput() && playedMicros >= movie.durationMicros) stop();
        } catch(final RuntimeException e) {
          LOGGER.warn("Video rendering failed", e);
          stop();
        }
      });
    } catch(final IOException | RuntimeException e) {
      cleanup();
      restoreRenderer();
      VideoPlayer.onRender = null;
      VideoPlayer.onFinish = null;
      throw new IOException("Could not initialize video playback", e);
    }
  }

  public static void stop() {
    if(stopping || movie == null) return;
    stopping = true;
    RENDERER.setRenderCallback(() -> { });
    RENDERER.addTask(() -> {
      cleanup();
      restoreRenderer();
      final Runnable render = onRender, finish = onFinish;
      onRender = null;
      onFinish = null;
      try { if(render != null) render.run(); }
      finally { if(finish != null) finish.run(); }
    });
  }

  private static void requestStop() {
    shouldStop = true;
    stop();
  }

  private static void setPaused(final boolean paused) {
    if(playback != null) playback.setPaused(paused);
    if(source != null) source.setPlaybackPaused(paused);
  }

  private static void restoreRenderer() {
    if(!stateCaptured) return;
    stateCaptured = false;
    CONFIG.setConfig(ALLOW_WIDESCREEN_CONFIG.get(), oldAllowWidescreen);
    RENDERER.setRenderCallback(oldRenderer);
    RENDERER.setCinematicPlayback(oldCinematicPlayback);
    RENDERER.window().setFpsLimit(oldFps);
    PLATFORM.setInputTickRate(oldInputTickRate);
    RENDERER.setRenderMode(oldRenderMode);
    RENDERER.setProjectionSize(oldProjectionSize.x, oldProjectionSize.y);
    clearRed_8007a3a8 = oldClearColour.x;
    clearGreen_800bb104 = oldClearColour.y;
    clearBlue_800babc0 = oldClearColour.z;
    oldRenderer = null;
  }

  private static void safely(final Runnable cleanup) {
    try { cleanup.run(); } catch(final RuntimeException e) { LOGGER.warn("Video resource cleanup failed", e); }
  }

  private static void cleanup() {
    if(stateCaptured) safely(RENDERER::discardCinematicFrame);
    if(oldPauseCallback != null) {
      safely(() -> RENDERER.setCinematicPauseCallback(oldPauseCallback));
      oldPauseCallback = null;
    }
    if(movie != null) { safely(movie::close); movie = null; playback = null; }
    if(texturedObj != null) { safely(texturedObj::delete); texturedObj = null; }
    if(displayTexture != null) { safely(displayTexture::delete); displayTexture = null; }
    if(stateCaptured) {
      safely(Obj::deleteObjects);
      safely(Texture::deleteTextures);
    }
    if(keyPress != null) { safely(() -> RENDERER.events().removeKeyPress(keyPress)); keyPress = null; }
    if(click != null) { safely(() -> RENDERER.events().removeMouseRelease(click)); click = null; }
    if(buttonPressed != null) { safely(() -> RENDERER.events().removeButtonPress(buttonPressed)); buttonPressed = null; }
    if(imageBuffer != null) { MemoryUtil.memFree(imageBuffer); imageBuffer = null; }
    if(source != null) { safely(() -> AUDIO_THREAD.removeSource(source)); source = null; }
  }
}
