package legend.definitive.fmv;

import legend.core.Config;
import legend.core.renderer.RenderEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual renderer buffer scheduler without a window, GPU or game session. */
final class CinematicRenderCadenceTest {
  private static int index(final RenderEngine renderer, final String field) throws Exception {
    final var value = RenderEngine.class.getDeclaredField(field);
    value.setAccessible(true);
    return value.getInt(renderer);
  }

  @Test void everyCinematicFrameIsPresentedAndGameplaySkippingReturnsAfterward() throws Exception {
    final int previousSpeed = Config.getGameSpeedMultiplier();
    final var advance = RenderEngine.class.getDeclaredMethod("advanceRenderBuffer");
    advance.setAccessible(true);
    try {
      for(final int speed : new int[]{1, 3, 8, 16}) {
        Config.setGameSpeedMultiplier(speed);
        final RenderEngine renderer = new RenderEngine();
        // Enter a movie during a skipped gameplay frame, as real state transitions do.
        if(speed > 1) advance.invoke(renderer);
        final var batch = renderer.addBatch();
        batch.modelPool.ignoreQueues = true;
        batch.orthoPool.ignoreQueues = true;
        final boolean oldState = renderer.setCinematicPlayback(true);
        assertFalse(oldState);
        assertFalse(batch.modelPool.ignoreQueues, "The first cinematic callback must accept models immediately");
        assertFalse(batch.orthoPool.ignoreQueues, "The first cinematic callback must accept video quads immediately");
        final var speedMethod = RenderEngine.class.getDeclaredMethod("getRenderSpeedMultiplier");
        speedMethod.setAccessible(true);
        assertEquals(1, speedMethod.invoke(renderer), "Cinematic vsync and FPS accounting must use real time");
        for(int frame = 0; frame < 60; frame++) {
          assertEquals(0, index(renderer, "frameSkipIndex"),
            "Movie frame " + frame + " must render even at gameplay speed " + speed);
          final int oldBuffer = index(renderer, "renderBufferIndex");
          advance.invoke(renderer);
          assertNotEquals(oldBuffer, index(renderer, "renderBufferIndex"), "Every movie callback must advance its presentation buffer");
        }
        assertEquals(speed, Config.getGameSpeedMultiplier());
        assertTrue(renderer.setCinematicPlayback(oldState));
        assertEquals(speed, speedMethod.invoke(renderer), "Gameplay vsync and FPS accounting must resume");
        for(int frame = 0; frame < speed * 2; frame++) {
          assertEquals(frame % speed, index(renderer, "frameSkipIndex"), "Gameplay frame skipping must resume");
          advance.invoke(renderer);
        }
      }
    } finally { Config.setGameSpeedMultiplier(previousSpeed); }
  }

  @Test void nestedPlaybackRestoresTheOuterScopeAndDisabledSkippingStaysDisabled() throws Exception {
    final int previousSpeed = Config.getGameSpeedMultiplier();
    final var advance = RenderEngine.class.getDeclaredMethod("advanceRenderBuffer");
    advance.setAccessible(true);
    try {
      Config.setGameSpeedMultiplier(8);
      final var renderer = new RenderEngine();
      renderer.setFrameSkipOption(false);
      final boolean outer = renderer.setCinematicPlayback(true);
      final boolean inner = renderer.setCinematicPlayback(true);
      assertTrue(inner);
      renderer.setCinematicPlayback(inner);
      advance.invoke(renderer);
      assertEquals(0, index(renderer, "frameSkipIndex"));
      renderer.setCinematicPlayback(outer);
      for(int frame = 0; frame < 16; frame++) {
        final int previousBuffer = index(renderer, "renderBufferIndex");
        advance.invoke(renderer);
        assertEquals(0, index(renderer, "frameSkipIndex"));
        assertNotEquals(previousBuffer, index(renderer, "renderBufferIndex"));
      }
    } finally { Config.setGameSpeedMultiplier(previousSpeed); }
  }
}
