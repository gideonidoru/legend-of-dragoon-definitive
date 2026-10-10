package legend.core.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.LongSupplier;

public class Action {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Action.class);

  private final Runnable action;
  private final LongSupplier clock;
  private int expectedFps;
  private long nanosPerTick;
  private long nextRunTime;

  public Action(final Runnable action, final int expectedFps) {
    this(action, expectedFps, System::nanoTime);
  }

  // The same monotonic scheduler can be exercised without sleeping or starting a platform.
  Action(final Runnable action, final int expectedFps, final LongSupplier clock) {
    this.action = Objects.requireNonNull(action);
    this.clock = Objects.requireNonNull(clock);
    this.setExpectedFps(expectedFps);
  }

  public void setExpectedFps(final int expectedFps) {
    if(expectedFps < 1 || expectedFps > 1_000_000_000) {
      throw new IllegalArgumentException("Tick rate must be between 1 and 1,000,000,000 Hz");
    }
    // Gameplay supplies its rate every callback. Re-arming an unchanged rate causes drift.
    if(this.expectedFps == expectedFps) {
      return;
    }
    this.expectedFps = expectedFps;
    this.nanosPerTick = 1_000_000_000L / this.expectedFps;
    // A new state owns its next deadline; never inherit the previous movie/game cadence.
    this.nextRunTime = this.clock.getAsLong() + this.nanosPerTick;
  }

  public int getExpectedFps() {
    return this.expectedFps;
  }

  public void tick() {
    if(this.isReady()) {
      this.run();
    }
  }

  public long nanosUntilNextRun() {
    return this.nextRunTime - this.clock.getAsLong();
  }

  public boolean isReady() {
    return this.nanosUntilNextRun() <= 0;
  }

  private void run() {
    this.updateTimer();
    this.action.run();
  }

  private void updateTimer() {
    this.nextRunTime += this.nanosPerTick;

    final long now = this.clock.getAsLong();
    if(now - this.nextRunTime > this.nanosPerTick * 2) {
      LOGGER.debug("Action running behind, skipping ticks to catch up");
      this.nextRunTime = now + this.nanosPerTick;
    }
  }
}
