package legend.definitive.audio;

import legend.core.audio.AudioThread;
import legend.core.audio.EffectsOverTimeGranularity;
import legend.core.audio.InterpolationPrecision;
import legend.core.audio.PitchResolution;
import legend.core.audio.opus.XaPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Runs the real audio worker in its unavailable-output wait, without opening an audio device. */
final class AudioShutdownTest {
  private static Field field(final String name) throws Exception {
    final Field field = AudioThread.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static final class Fixture implements AutoCloseable {
    private final AudioThread audio = new AudioThread(true, 24, InterpolationPrecision.Double, PitchResolution.Quadruple, EffectsOverTimeGranularity.Double);
    private final Thread worker = new Thread(this.audio, "audio-shutdown-regression");

    Fixture() throws Exception {
      // init() normally constructs this source even when all output devices fail.
      field("xaPlayer").set(this.audio, new XaPlayer(1.0f));
      field("paused").setBoolean(this.audio, true);
      this.worker.setDaemon(true);
      this.worker.start();
    }

    void awaitPaused() throws Exception {
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while(this.worker.getState() != Thread.State.WAITING && this.worker.isAlive() && System.nanoTime() < deadline) {
        Thread.sleep(1);
      }
      assertEquals(Thread.State.WAITING, this.worker.getState(), "Worker must reach the unavailable-output wait before shutdown");
    }

    void assertTerminated() throws Exception {
      this.worker.join(2_000);
      assertFalse(this.worker.isAlive(), "Shutdown must release the paused worker without the gameplay close handler calling stop()");
    }

    @Override public void close() throws Exception {
      this.audio.stop();
      this.audio.destroy();
      this.worker.join(2_000);
      assertFalse(this.worker.isAlive(), "Regression fixture must not leak a worker");
    }
  }

  @Test void destroyReleasesPausedWorkerWithoutGameplayStop() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.awaitPaused();
      fixture.audio.destroy();
      fixture.assertTerminated();
      fixture.audio.destroy();
    }
  }

  @Test void pendingRetryNotificationCannotKeepWorkerAliveAfterShutdown() throws Exception {
    for(int iteration = 0; iteration < 20; iteration++) {
      try(final Fixture fixture = new Fixture()) {
        fixture.awaitPaused();
        // The worker cannot consume a queued retry until the same monitor is released.
        synchronized(fixture.audio) {
          field("deviceChanged").setBoolean(fixture.audio, true);
          fixture.audio.notify();
          fixture.audio.destroy();
        }
        fixture.assertTerminated();
      }
    }
  }

  @Test void ordinaryStopStillReleasesPausedWorker() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.awaitPaused();
      fixture.audio.stop();
      fixture.assertTerminated();
    }
  }
}
