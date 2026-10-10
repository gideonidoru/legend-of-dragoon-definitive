package legend.definitive.pacing;

import legend.core.Config;
import legend.core.GameEngine;
import legend.core.gte.GsCOORDINATE2;
import legend.core.gte.ModelPart10;
import legend.core.platform.Action;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.game.Graphics;
import legend.game.Models;
import legend.game.Scus94491BpeSegment;
import legend.game.scripting.ScriptManager;
import legend.game.types.Model124;
import legend.game.types.TmdAnimationFile;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Actual engine schedulers, scripts and model animation; virtual time and no game/window launch. */
final class EnginePacingTest {
  private static Action action(final Runnable callback, final int hz, final AtomicLong now) throws Exception {
    final var constructor = Action.class.getDeclaredConstructor(Runnable.class, int.class, LongSupplier.class);
    constructor.setAccessible(true);
    return constructor.newInstance(callback, hz, (LongSupplier)now::get);
  }

  @Test void everyGameplaySpeedAndStateHasTheExpectedTickBudget() throws Exception {
    for(final int baseHz : new int[]{20, 30, 60}) {
      for(int speed = 1; speed <= 16; speed++) {
        final var now = new AtomicLong();
        final int[] ticks = {0};
        final int hz = baseHz * speed;
        final var scheduler = action(() -> ticks[0]++, hz, now);
        for(int frame = 0; frame < hz; frame++) {
          now.addAndGet(scheduler.nanosUntilNextRun());
          scheduler.tick();
          scheduler.setExpectedFps(hz); // gameLoop sets the rate every callback
          assertFalse(scheduler.isReady(), "One due callback must not create an immediate extra tick");
        }
        assertEquals(hz, ticks[0]);
        assertTrue(Math.abs(now.get() - 1_000_000_000L) < 1_000L,
          "Gameplay tick duration must remain one second, including fast-forward");
      }
    }
  }

  @Test void rateChangesRearmImmediatelyAndRepeatedRatesKeepTheirPhase() throws Exception {
    final var now = new AtomicLong(123_000_000L);
    final int[] ticks = {0};
    final var scheduler = action(() -> ticks[0]++, 15, now);
    for(final int hz : new int[]{960, 20, 60, 15, 480, 30, 60}) {
      scheduler.setExpectedFps(hz);
      final long period = 1_000_000_000L / hz;
      assertEquals(period, scheduler.nanosUntilNextRun(), "A state must own its new deadline");
      now.addAndGet(period / 2);
      final long remaining = scheduler.nanosUntilNextRun();
      scheduler.setExpectedFps(hz);
      assertEquals(remaining, scheduler.nanosUntilNextRun(), "Repeated rate assignments must not defer the callback");
      scheduler.tick();
      assertFalse(scheduler.isReady());
      final int previous = ticks[0];
      now.addAndGet(remaining);
      scheduler.tick();
      assertEquals(previous + 1, ticks[0]);
      assertEquals(period, scheduler.nanosUntilNextRun());
    }
  }

  @Test void longStallsDoNotReplayAnUnboundedBacklogAndClockWrapIsSafe() throws Exception {
    for(final int hz : new int[]{1, 15, 20, 30, 60, 960}) {
      final var now = new AtomicLong(Long.MAX_VALUE - 100_000_000L);
      final int[] ticks = {0};
      final var scheduler = action(() -> ticks[0]++, hz, now);
      now.addAndGet(scheduler.nanosUntilNextRun());
      scheduler.tick(); // monotonic nanoTime may wrap; elapsed subtraction remains valid
      assertEquals(1, ticks[0]);
      now.addAndGet(10_000_000_000L);
      scheduler.tick();
      for(int call = 0; call < 1000; call++) scheduler.tick();
      assertEquals(2, ticks[0], "Resume must not replay ten seconds of stale game or movie callbacks");
      assertEquals(1_000_000_000L / hz, scheduler.nanosUntilNextRun());
      assertThrows(IllegalArgumentException.class, () -> scheduler.setExpectedFps(0));
      assertThrows(IllegalArgumentException.class, () -> scheduler.setExpectedFps(-1));
      assertThrows(IllegalArgumentException.class, () -> scheduler.setExpectedFps(Integer.MAX_VALUE));
      assertEquals(hz, scheduler.getExpectedFps(), "Rejected settings cannot corrupt the current clock");
    }
  }

  @Test void actualGameplayTimingSeparatesSimulationFromPresentationDuringFastForward() throws Exception {
    final var windowField = GameEngine.RENDERER.getClass().getDeclaredField("window");
    windowField.setAccessible(true);
    final Object previousWindow = windowField.get(GameEngine.RENDERER);
    final int previousSpeed = Config.getGameSpeedMultiplier();
    final int previousMode = Graphics.vsyncMode_8007a3b8;
    final var inputField = legend.core.platform.PlatformManager.class.getDeclaredField("input");
    inputField.setAccessible(true);
    final Action input = (Action)inputField.get(GameEngine.PLATFORM);
    final int previousInputRate = input.getExpectedFps();
    final int previousSimulationRate = GameEngine.RENDERER.simulationTiming().rate();
    final var configure = Scus94491BpeSegment.class.getDeclaredMethod("setGameplayTiming");
    configure.setAccessible(true);
    final var window = new NoopWindow(new NoopPlatformManager(), 1280, 800);
    final var skipField = GameEngine.RENDERER.getClass().getDeclaredField("frameSkip");
    skipField.setAccessible(true);
    final boolean previousSkip = skipField.getBoolean(GameEngine.RENDERER);
    try {
      windowField.set(GameEngine.RENDERER, window);
      skipField.setBoolean(GameEngine.RENDERER, true);
      for(final int divisor : new int[]{1, 2, 3}) {
        Graphics.vsyncMode_8007a3b8 = divisor;
        for(int speed = 1; speed <= 16; speed++) {
          Config.setGameSpeedMultiplier(speed);
          configure.invoke(null);
          assertEquals(60 / divisor, window.getFpsLimit());
          assertEquals(60 / divisor * speed, input.getExpectedFps());
          assertEquals(input.getExpectedFps(), GameEngine.RENDERER.simulationTiming().rate());
        }
      }
    } finally {
      input.setExpectedFps(previousInputRate);
      Config.setGameSpeedMultiplier(previousSpeed);
      Graphics.vsyncMode_8007a3b8 = previousMode;
      skipField.setBoolean(GameEngine.RENDERER, previousSkip);
      GameEngine.RENDERER.setSimulationRate(previousSimulationRate);
      windowField.set(GameEngine.RENDERER, previousWindow);
    }
  }

  @Test void scriptCadenceAndPauseResumeStayBoundToGameTicks() throws Exception {
    final var managerField = org.legendofdragoon.modloader.events.EventManager.class.getDeclaredField("modManager");
    managerField.setAccessible(true);
    final Object previousManager = managerField.get(GameEngine.EVENTS);
    final var accessField = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    accessField.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)accessField.get(null)).initialize(GameEngine.MODS);
    try {
      final var scripts = new ScriptManager(List.of(), Path.of("patches"));
      scripts.setFramesPerTick(2);
      final int[] ticks = {0}, renders = {0};
      final var state = scripts.allocateScriptState("headless pacing fixture", null);
      final var vmTicks = state.getClass().getDeclaredField("ticks");
      vmTicks.setAccessible(true);
      state.setTicker((s, object) -> ticks[0]++);
      state.setRenderer((s, object) -> renders[0]++);
      for(int frame = 0; frame < 60; frame++) scripts.tick();
      assertEquals(30, vmTicks.getInt(state), "Script VM runs at its original 30 Hz");
      assertEquals(60, ticks[0], "Object tickers run at the enhanced engine cadence");
      assertEquals(60, renders[0]);
      scripts.pause();
      for(int frame = 0; frame < 60; frame++) scripts.tick();
      assertEquals(30, vmTicks.getInt(state));
      assertEquals(60, ticks[0], "Paused scripts cannot advance simulation");
      assertEquals(120, renders[0], "Paused script presentation remains available");
      scripts.resume();
      scripts.setFramesPerTick(1);
      for(int frame = 0; frame < 20; frame++) scripts.tick();
      assertEquals(50, vmTicks.getInt(state));
      assertEquals(80, ticks[0]);
      scripts.stop();
      for(int frame = 0; frame < 20; frame++) scripts.tick();
      assertEquals(50, vmTicks.getInt(state));
      assertEquals(80, ticks[0]);
      assertEquals(140, renders[0]);
    } finally { managerField.set(GameEngine.EVENTS, previousManager); }
  }

  @Test void nativeModelKeyframesKeepTheirDurationAcrossInterpolationRates() {
    final var bytes = ByteBuffer.allocate(16 + 4 * 12).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0, 12).putShort(12, (short)1).putShort(14, (short)8);
    for(int key = 0; key < 4; key++) bytes.putShort(16 + key * 12 + 6, (short)(key * 12));
    final var animation = new TmdAnimationFile(new FileData(bytes.array()));
    for(final int stride : new int[]{2, 4, 6}) {
      final var model = new Model124("pacing fixture");
      model.modelParts_00 = new ModelPart10[]{new ModelPart10()};
      model.modelParts_00[0].coord2_04 = new GsCOORDINATE2();
      Models.loadModelStandardAnimation(model, animation);
      for(int tick = 0; tick < 4 * stride; tick++) Models.animateModel(model, stride);
      assertEquals(4, model.currentKeyframe_94);
      assertEquals(0, model.remainingFrames_9e);
      assertEquals(0, model.subFrameIndex);
      assertEquals(36.0f, model.modelParts_00[0].coord2_04.coord.transfer.x);
    }
  }
}
