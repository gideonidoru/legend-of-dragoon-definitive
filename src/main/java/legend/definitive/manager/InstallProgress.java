// Definitive installation progress (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

/** Completed work within named phases; unknown totals never invent a completion percentage. */
@FunctionalInterface
public interface InstallProgress {
  InstallProgress NONE = update -> { };
  /** Network bytes count received and staged response payload, including retries; -1 denotes local work. */
  record Update(String phase, String detail, int startPercent, int endPercent, long completed, long total, String file, long fileCompleted, long fileTotal, long networkBytes, double networkBytesPerSecond) {
    public Update(final String phase, final String detail, final int startPercent, final int endPercent, final long completed, final long total) { this(phase, detail, startPercent, endPercent, completed, total, "", 0, 0); }
    public Update(final String phase, final String detail, final int startPercent, final int endPercent, final long completed, final long total, final String file, final long fileCompleted, final long fileTotal) { this(phase, detail, startPercent, endPercent, completed, total, file, fileCompleted, fileTotal, -1, 0); }
    public Update scaled(final int start, final int end) { return new Update(this.phase, this.detail, start, end, this.completed, this.total, this.file, this.fileCompleted, this.fileTotal, this.networkBytes, this.networkBytesPerSecond); }
    public Update withNetwork(final Network network) { return new Update(this.phase, this.detail, this.startPercent, this.endPercent, this.completed, this.total, this.file, this.fileCompleted, this.fileTotal, network.bytes(), network.bytesPerSecond()); }
    public int filePercent() { return this.fileTotal <= 0 ? 0 : (int)(100 * Math.min(1.0, (double)this.fileCompleted / this.fileTotal)); }
    public int percent() {
      return this.total <= 0 ? this.startPercent : this.startPercent + (int)((this.endPercent - this.startPercent) * Math.min(1.0, (double)this.completed / this.total));
    }
  }
  record Network(long bytes, double bytesPerSecond) { }

  /** One sampler per operation; each transfer callback starts its own response-body counter. */
  final class NetworkSampler {
    private static final long INTERVAL = 250_000_000L;
    private static final double SMOOTHING_NANOS = 1_500_000_000.0;
    private final java.util.function.LongSupplier clock;
    private long sampledAt, bytes, pending;
    private double rate;
    public NetworkSampler() { this(System::nanoTime); }
    NetworkSampler(final java.util.function.LongSupplier clock) { this.clock = clock; this.sampledAt = clock.getAsLong(); }
    java.util.function.LongConsumer transfer() {
      final long[] previous = {0};
      return completed -> {
        if(completed < previous[0]) throw new IllegalArgumentException("Response-body progress moved backwards.");
        received(completed - previous[0]); previous[0] = completed;
      };
    }
    private synchronized void received(final long count) {
      this.bytes += count; this.pending += count;
      final long now = this.clock.getAsLong(), elapsed = now - this.sampledAt;
      if(elapsed >= INTERVAL) {
        final double observed = this.pending * 1_000_000_000.0 / elapsed;
        final double weight = -Math.expm1(-elapsed / SMOOTHING_NANOS);
        this.rate = this.rate == 0 ? observed : this.rate + weight * (observed - this.rate);
        this.pending = 0; this.sampledAt = now;
      }
    }
    synchronized Network snapshot() {
      final long idle = Math.max(0, this.clock.getAsLong() - this.sampledAt);
      return new Network(this.bytes, this.rate * Math.exp(-idle / SMOOTHING_NANOS));
    }
  }
  void report(Update update);
  default void phase(final String phase, final String detail, final int percent) { this.report(new Update(phase, detail, percent, percent, 0, 0)); }
  default void bytes(final String phase, final int start, final int end, final long completed, final long total) {
    this.report(byteUpdate(phase, start, end, completed, total));
  }
  default void bytes(final String phase, final int start, final int end, final long completed, final long total, final Network network) {
    this.report(byteUpdate(phase, start, end, completed, total).withNetwork(network));
  }
  private static Update byteUpdate(final String phase, final int start, final int end, final long completed, final long total) {
    final String detail = String.format(java.util.Locale.ROOT, "%.1f MB%s", completed / 1048576.0, total > 0 ? String.format(java.util.Locale.ROOT, " of %.1f MB", total / 1048576.0) : " downloaded");
    return new Update(phase, detail, start, end, completed, total);
  }
}
