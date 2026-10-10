package legend.core.renderer;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Monotonic gameplay debt, independent of presentation frequency, with bounded recovery. */
public final class SimulationClock {
  public static final long MAX_DEBT_NANOS = 250_000_000L;
  public static final long MAX_WORK_NANOS = 50_000_000L;
  public static final int MAX_STEPS = 256;

  private final LongSupplier clock;
  private int hz = 60;
  private long period = 1_000_000_000L / 60;
  private long last;
  private long debt;
  private int generation;
  private long totalSteps;
  private long droppedNanos;
  private boolean budgetLimited;

  public record Snapshot(int rate, long pendingNanos, long totalSteps, long droppedNanos, boolean budgetLimited) { }
  public Snapshot snapshot() { return new Snapshot(this.hz, this.debt, this.totalSteps, this.droppedNanos, this.budgetLimited); }

  public SimulationClock() { this(System::nanoTime); }
  public SimulationClock(final LongSupplier clock) {
    this.clock = Objects.requireNonNull(clock);
    this.reset(true);
  }

  public int rate() { return this.hz; }

  public void setRate(final int hz) {
    if(hz < 1 || hz > 960) throw new IllegalArgumentException("Gameplay rate must be between 1 and 960 Hz");
    if(this.hz == hz) return;
    this.hz = hz;
    this.period = 1_000_000_000L / hz;
    this.reset(false);
  }

  /** Discard the old domain's backlog on pause, callback handoff or cadence change. */
  public void reset(final boolean immediate) {
    this.last = this.clock.getAsLong();
    this.debt = immediate ? this.period : 0;
    this.budgetLimited = false;
    this.generation++;
  }

  public int advance(final Runnable step) {
    final long start = this.clock.getAsLong();
    final long elapsed = start - this.last;
    this.last = start;
    if(elapsed < 0) { this.reset(false); return 0; }
    final long accepted = Math.min(elapsed, MAX_DEBT_NANOS);
    final long combined = this.debt + accepted;
    final long dropped = elapsed - accepted + Math.max(0, combined - MAX_DEBT_NANOS);
    this.droppedNanos = dropped > Long.MAX_VALUE - this.droppedNanos ? Long.MAX_VALUE : this.droppedNanos + dropped;
    this.debt = Math.min(MAX_DEBT_NANOS, combined);
    this.budgetLimited = false;
    final int generation = this.generation;
    int steps = 0;
    while(this.debt >= this.period && steps < MAX_STEPS) {
      this.debt -= this.period;
      step.run();
      steps++;
      this.totalSteps++;
      if(generation != this.generation) break;
      if(this.clock.getAsLong() - start >= MAX_WORK_NANOS) {
        this.budgetLimited = this.debt >= this.period;
        break;
      }
    }
    if(steps == MAX_STEPS && this.debt >= this.period) this.budgetLimited = true;
    return steps;
  }
}
