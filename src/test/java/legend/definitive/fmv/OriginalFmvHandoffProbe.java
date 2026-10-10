package legend.definitive.fmv;

import legend.core.Config;
import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.platform.WindowEvents;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputActionRegistry;
import legend.core.platform.input.InputActionRegistryEvent;
import legend.core.platform.input.InputClass;
import legend.core.renderer.RenderBatch;
import legend.core.renderer.RenderEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;
import legend.core.renderer.Translucency;
import legend.game.fmv.Fmv;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.unpacker.Unpacker;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Private original-disc lifecycle proof in an isolated JVM, without a window or sound output. */
public final class OriginalFmvHandoffProbe {
  private static final class FaultObj extends Obj {
    int attempts;
    FaultObj() { super("One-time graphics retirement failure"); }
    @Override protected void performDelete() {
      if(++this.attempts == 1) throw new IllegalStateException("Injected graphics retirement failure");
    }
    @Override public boolean hasTexture() { return false; }
    @Override public boolean hasTexture(final int index) { return false; }
    @Override public boolean hasTranslucency() { return false; }
    @Override public boolean hasTranslucency(final int index) { return false; }
    @Override public boolean shouldRender(final Translucency translucency) { return false; }
    @Override public boolean shouldRender(final Translucency translucency, final int layer) { return false; }
    @Override public int getLayers() { return 1; }
    @Override public void render(final int layer, final int first, final int count) { }
    @Override public void render(final Translucency translucency, final int layer, final int first, final int count) { }
  }
  private static void require(final boolean condition, final String message) {
    if(!condition) throw new AssertionError(message);
  }
  private static Object get(final Class<?> owner, final Object target, final String name) throws Exception {
    final var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
  }
  private static void set(final Class<?> owner, final Object target, final String name, final Object value) throws Exception {
    final var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 1) throw new IllegalArgumentException("Supply one private original movie");
    final Path original = Path.of(args[0]).toAbsolutePath();
    Unpacker.ROOT = original.getParent();
    CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    CoreMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    final RenderEngine renderer = GameEngine.RENDERER;
    final NoopWindow window = new NoopWindow(new NoopPlatformManager(), 640, 480);
    final var initialize = RenderEngine.class.getDeclaredMethod("init", Window.class); initialize.setAccessible(true); initialize.invoke(renderer, window);
    GameEngine.GPU.init(); GameEngine.DEFAULT_FONT.init();
    final var resize = RenderEngine.class.getDeclaredMethod("onResize", Window.class, int.class, int.class); resize.setAccessible(true); resize.invoke(renderer, window, 640, 480);
    final var draw = NoopWindow.class.getDeclaredMethod("tick"); draw.setAccessible(true);
    final RenderBatch auxiliary = renderer.addBatch();
    final var play = Fmv.class.getDeclaredMethod("play", String.class, boolean.class, boolean.class); play.setAccessible(true);
    final var action = WindowEvents.class.getDeclaredMethod("onInputActionPressed", InputAction.class, boolean.class); action.setAccessible(true);
    final var mouse = WindowEvents.class.getDeclaredMethod("onMouseRelease", int.class, Set.class); mouse.setAccessible(true);
    final int[] outerPauseNotifications = {0}, gameplayTicks = {0};
    final var outerObserver = (java.util.function.Consumer<Boolean>)(paused -> outerPauseNotifications[0]++);
    renderer.setCinematicPauseCallback(outerObserver);
    final List<?> sources = (List<?>)get(GameEngine.AUDIO_THREAD.getClass(), GameEngine.AUDIO_THREAD, "sources");
    final int sourceCount = sources.size();

    for(final int base : new int[]{20, 30, 60}) {
      for(int speed = 1; speed <= 16; speed++) {
        Config.setGameSpeedMultiplier(speed);
        renderer.setSimulationCallback(() -> gameplayTicks[0]++);
        renderer.setSimulationRate(base * speed); GameEngine.PLATFORM.setInputTickRate(base * speed);
        final Runnable savedCallback = (Runnable)get(RenderEngine.class, renderer, "renderCallback");
        final int inputRate = GameEngine.PLATFORM.getInputTickRate(), presentationRate = window.getFpsLimit();
        final int projectionWidth = renderer.getNativeWidth(), projectionHeight = renderer.getNativeHeight();
        final var mode = renderer.getRenderMode();
        final boolean widescreen = GameEngine.CONFIG.getConfig(CoreMod.ALLOW_WIDESCREEN_CONFIG.get());
        final int pauseNotifications = outerPauseNotifications[0];
        set(Fmv.class, null, "isPlaying", true);
        play.invoke(null, original.getFileName().toString(), true, false);
        final OriginalMovie movie = (OriginalMovie)get(Fmv.class, null, "originalMovie");
        require(movie != null, "Original runtime movie must initialize");
        OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 32);
        final Thread videoWorker = (Thread)get(OriginalMovie.class, movie, "worker"), audioWorker = (Thread)get(OriginalMovie.class, movie, "audioWorker");
        draw.invoke(window);
        require(get(Fmv.class, null, "displayTexture") != null, "Actual original frame must upload through the headless renderer");
        set(RenderEngine.class, renderer, "togglePause", true); draw.invoke(window);
        require(renderer.isPaused(), "Pause must remain active during cleanup");
        // Debug presentation can retain the frozen movie quad while the renderer is paused.
        set(RenderEngine.class, renderer, "frameAdvanceSingle", true); draw.invoke(window);
        final RenderBatch batch = (RenderBatch)get(RenderEngine.class, renderer, "mainBatch");
        require(batch.orthoPool.size() > 0, "Fixture must retain an actual frozen movie quad before cleanup");
        final Obj movieObj = (Obj)get(Fmv.class, null, "texturedObj");
        final Texture movieTexture = (Texture)get(Fmv.class, null, "displayTexture");
        auxiliary.queueOrthoModel(movieObj, QueuedModelStandard.class).texture(movieTexture);
        // A pending debug step belongs to this movie, and cannot transfer to paused gameplay.
        set(RenderEngine.class, renderer, "frameAdvanceSingle", true);
        set(RenderEngine.class, renderer, "frameAdvance", true);
        final FaultObj fault = speed == 16 ? new FaultObj() : null;
        if(fault != null) fault.delete();
        final int ticksBeforeCleanup = gameplayTicks[0];
        if(speed % 3 == 0) {
          Fmv.stop(); Fmv.stop(); // Idempotence must queue only one cleanup.
        } else if(speed % 3 == 1) {
          set(Fmv.class, null, "skipText", "Private lifecycle skip"); set(Fmv.class, null, "currentInputSource", InputClass.KEYBOARD);
          action.invoke(window.events(), CoreMod.INPUT_ACTION_FMV_SKIP.get(), false);
        } else {
          set(Fmv.class, null, "skipText", null);
          mouse.invoke(window.events(), 0, Set.of());
          require(get(Fmv.class, null, "skipText") != null && get(Fmv.class, null, "originalMovie") == movie && !((boolean)get(Fmv.class, null, "stopping")), "First mouse input shows the prompt without stopping");
          mouse.invoke(window.events(), 0, Set.of());
        }
        draw.invoke(window); // Production task cleanup runs before the paused callback gate.
        require(renderer.isPaused() && gameplayTicks[0] == ticksBeforeCleanup, "Cleanup must preserve pause and never advance gameplay");
        require(!(boolean)get(RenderEngine.class, renderer, "frameAdvanceSingle") && !(boolean)get(RenderEngine.class, renderer, "frameAdvance"), "Cleanup discards pending cinematic debug steps");
        require(!Fmv.isPlaying && get(Fmv.class, null, "originalMovie") == null && get(Fmv.class, null, "source") == null, "Paused stop/skip must release movie and audio ownership");
        require(!videoWorker.isAlive() && !audioWorker.isAlive(), "Blocked producer workers must terminate");
        require(sources.size() == sourceCount, "Movie source must leave the audio thread");
        require(get(RenderEngine.class, renderer, "renderCallback") == savedCallback && window.simulationConsumesInput(), "Cleanup must restore the exact gameplay callback");
        require(window.getFpsLimit() == presentationRate && GameEngine.PLATFORM.getInputTickRate() == inputRate, "Cleanup restores distinct presentation and input rates");
        require(renderer.getNativeWidth() == projectionWidth && renderer.getNativeHeight() == projectionHeight && renderer.getRenderMode() == mode, "Cleanup restores projection and render mode");
        require(GameEngine.CONFIG.getConfig(CoreMod.ALLOW_WIDESCREEN_CONFIG.get()) == widescreen && Config.getGameSpeedMultiplier() == speed, "Cleanup restores config and preserves speed");
        require(get(RenderEngine.class, renderer, "cinematicPauseCallback") == outerObserver && outerPauseNotifications[0] == pauseNotifications + 1, "Cleanup restores and initializes the outer paused observer exactly once");
        require(batch.modelPool.size() == 0 && batch.orthoPool.size() == 0, "Paused presentation cannot retain disposed movie objects or textures");
        require(auxiliary.modelPool.size() == 0 && auxiliary.orthoPool.size() == 0, "Cleanup clears auxiliary presentation batches too");
        require(!((List<?>)get(Obj.class, null, "objList")).contains(movieObj) && !((List<?>)get(Texture.class, null, "texList")).contains(movieTexture), "Paused cleanup must retire movie graphics ownership without waiting for resume");
        if(fault != null) require(fault.attempts == 2 && !((List<?>)get(Obj.class, null, "objList")).contains(fault), "A renderer retirement exception cannot prevent remaining cleanup and restoration");
        set(RenderEngine.class, renderer, "togglePause", true); draw.invoke(window);
        require(!renderer.isPaused(), "Restored gameplay must resume normally");
      }
    }
    System.out.println("PASS: actual original-FMV paused stop/action skip/mouse skip for all 48 gameplay rate/speed combinations; no gameplay advancement; exact callback, pause observer, projection, config and rates restored; workers/source/retained movie queues released.");
  }
}
