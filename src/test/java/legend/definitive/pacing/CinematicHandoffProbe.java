package legend.definitive.pacing;

import legend.core.Config;
import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.renderer.RenderEngine;
import legend.game.fmv.VideoPlayer;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import java.io.IOException;
import java.nio.file.Path;

/** Actual streamed-player entry/cleanup, in an isolated headless JVM without a native window. */
public final class CinematicHandoffProbe {
  private static void require(final boolean condition, final String message) {
    if(!condition) throw new AssertionError(message);
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 1) throw new IllegalArgumentException("Supply one synthetic movie fixture");
    final Path fixture = Path.of(args[0]);
    CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    final RenderEngine renderer = GameEngine.RENDERER;
    final NoopWindow window = new NoopWindow(new NoopPlatformManager(), 640, 480);
    final var initialize = RenderEngine.class.getDeclaredMethod("init", Window.class);
    initialize.setAccessible(true);
    initialize.invoke(renderer, window);
    GameEngine.GPU.init();
    GameEngine.DEFAULT_FONT.init();
    final var resize = RenderEngine.class.getDeclaredMethod("onResize", Window.class, int.class, int.class);
    resize.setAccessible(true);
    resize.invoke(renderer, window, 640, 480);
    final var draw = NoopWindow.class.getDeclaredMethod("tick");
    draw.setAccessible(true);
    final var pauseRequest = RenderEngine.class.getDeclaredField("togglePause");
    pauseRequest.setAccessible(true);
    final int[] outerPauseNotifications = {0};
    renderer.setCinematicPauseCallback(paused -> outerPauseNotifications[0]++);

    for(final int base : new int[]{20, 30, 60}) {
      for(int speed = 1; speed <= 16; speed++) {
        Config.setGameSpeedMultiplier(speed);
        renderer.setSimulationCallback(() -> { });
        renderer.setSimulationRate(base * speed);
        GameEngine.PLATFORM.setInputTickRate(base * speed);
        final int inputRate = GameEngine.PLATFORM.getInputTickRate();
        final int presentationRate = window.getFpsLimit();
        final boolean widescreen = GameEngine.CONFIG.getConfig(CoreMod.ALLOW_WIDESCREEN_CONFIG.get());
        final int previousPauseNotifications = outerPauseNotifications[0];
        VideoPlayer.play(fixture, null, null);
        require(!window.simulationConsumesInput(), "Movie callback must own its presentation cadence");
        require(window.getFpsLimit() == 60 && GameEngine.PLATFORM.getInputTickRate() == 60, "Movie entry uses native presentation and input rates");
        pauseRequest.setBoolean(renderer, true);
        draw.invoke(window);
        require(renderer.isPaused(), "Actual presentation pause must enter the movie's pause scope");
        pauseRequest.setBoolean(renderer, true);
        draw.invoke(window);
        require(!renderer.isPaused(), "Actual presentation resume must leave the movie's pause scope");
        require(outerPauseNotifications[0] == previousPauseNotifications, "Movie pause/resume must stay inside its scoped observer");
        VideoPlayer.stop();
        draw.invoke(window); // Deferred production cleanup and renderer restoration.
        require(window.simulationConsumesInput(), "Movie cleanup must restore the saved gameplay callback owner");
        require(window.getFpsLimit() == presentationRate, "Cleanup must restore presentation cadence");
        require(GameEngine.PLATFORM.getInputTickRate() == inputRate, "Cleanup must restore actual input cadence independently of presentation: " + base + "x" + speed);
        require(GameEngine.CONFIG.getConfig(CoreMod.ALLOW_WIDESCREEN_CONFIG.get()) == widescreen, "Cleanup restores configuration scope");
        require(outerPauseNotifications[0] == previousPauseNotifications + 1, "Cleanup restores and initializes the previous pause observer once");
        require(Config.getGameSpeedMultiplier() == speed, "Movies preserve the player's gameplay speed");
      }
    }

    renderer.setRenderCallback(() -> { });
    renderer.setCinematicPlayback(true);
    window.setFpsLimit(30);
    GameEngine.PLATFORM.setInputTickRate(120);
    VideoPlayer.play(fixture, null, null);
    VideoPlayer.stop();
    draw.invoke(window);
    require(renderer.setCinematicPlayback(false), "Nested playback restores the outer cinematic scope");
    require(window.getFpsLimit() == 30 && GameEngine.PLATFORM.getInputTickRate() == 120, "Nested cleanup restores distinct outer rates");
    try {
      VideoPlayer.play(fixture.resolveSibling("missing-cinematic-handoff-fixture.mp4"), null, null);
      throw new AssertionError("Missing media must fail initialization");
    } catch(final IOException expected) {
      require(window.getFpsLimit() == 30 && GameEngine.PLATFORM.getInputTickRate() == 120, "Failed initialization cannot change the previous timing owner");
      require(!window.simulationConsumesInput(), "Failed initialization preserves the previous presentation callback");
    }
    System.out.println("PASS: actual streamed-player entry/cleanup restores independent input/presentation rates for all 48 gameplay combinations, nested cinematic scope and failed media initialization.");
  }
}
