package legend.definitive.fmv;

import legend.core.Config;
import legend.core.GameEngine;
import legend.core.platform.Action;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.game.fmv.Fmv;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the production cinematic scheduler with a virtual window; no SDL, GPU, audio or game launch. */
final class CinematicCadenceTest {
  @Test void originalMovieKeepsItsCadenceAtEightTimesGameplaySpeed() throws Exception {
    final String threadName = Thread.currentThread().getName();
    final var windowField = GameEngine.RENDERER.getClass().getDeclaredField("window");
    windowField.setAccessible(true);
    final var previousWindow = windowField.get(GameEngine.RENDERER);
    final int previousSpeed = Config.getGameSpeedMultiplier();
    final var inputField = legend.core.platform.PlatformManager.class.getDeclaredField("input");
    inputField.setAccessible(true);
    final Action input = (Action)inputField.get(GameEngine.PLATFORM);
    final int previousInputRate = input.getExpectedFps();
    final var window = new NoopWindow(new NoopPlatformManager(), 1280, 800);
    final var configure = Fmv.class.getDeclaredMethod("setPlaybackTiming", boolean.class);
    configure.setAccessible(true);
    try {
      windowField.set(GameEngine.RENDERER, window);
      Config.setGameSpeedMultiplier(8);
      configure.invoke(null, false);
      final int[] frames = {0};
      final Action originalFrames = new Action(() -> frames[0]++, window.getFpsLimit());
      final long end = System.nanoTime() + 1_000_000_000L;
      while(System.nanoTime() < end) { originalFrames.tick(); Thread.sleep(1); }
      assertTrue(frames[0] >= 10 && frames[0] <= 20,
        "One second must advance about 15 original movie frames, even with gameplay speed 8; advanced " + frames[0]);
      assertEquals(8, Config.getGameSpeedMultiplier(), "Cinematics must preserve the player's gameplay setting");
      assertEquals(15, input.getExpectedFps(), "Skip input uses the same cinematic cadence");
      for(int speed : new int[]{1, 3, 8, 16}) {
        Config.setGameSpeedMultiplier(speed);
        configure.invoke(null, true);
        assertEquals(60, window.getFpsLimit(), "Enhanced movie rendering must also be independent of gameplay speed");
        assertEquals(60, input.getExpectedFps());
      }
    } finally {
      windowField.set(GameEngine.RENDERER, previousWindow);
      input.setExpectedFps(previousInputRate);
      Config.setGameSpeedMultiplier(previousSpeed);
      Thread.currentThread().setName(threadName);
    }
  }
}
