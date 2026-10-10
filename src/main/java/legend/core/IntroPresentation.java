package legend.core;

/** Neutral, elapsed-time animation for the intro/loading presentation callback. */
public final class IntroPresentation {
  private final java.util.function.LongSupplier clock;
  private long last;
  private double elapsed;
  private double hue;
  private float eyeFade;
  private float loadingFade;
  private boolean wasLoading;
  private boolean wasFadingLoading;

  public IntroPresentation() { this(System::nanoTime); }
  public IntroPresentation(final java.util.function.LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }
  public void advance(final boolean loading, final boolean cinematicFinished) { this.advance(this.clock.getAsLong(), loading, cinematicFinished); }

  public void reset(final long now) {
    this.last = now;
    this.elapsed = this.hue = 0;
    this.eyeFade = this.loadingFade = 0;
    this.wasLoading = this.wasFadingLoading = false;
  }

  public void skip() { this.eyeFade = this.loadingFade = 1; }
  public float eyeFade() { return this.eyeFade; }
  public float loadingFade() { return this.loadingFade; }
  public float hue() { return (float)this.hue; }

  public void advance(final long now, final boolean loading, final boolean cinematicFinished) {
    final double seconds = Math.max(0, now - this.last) / 1_000_000_000.0;
    this.last = now;
    final double previous = this.elapsed;
    this.elapsed += seconds;
    this.eyeFade = Math.max(this.eyeFade, (float)Math.min(1, this.elapsed * 0.3));
    // A newly observed loading state starts here, rather than claiming time from an old owner.
    if(this.wasFadingLoading && loading && cinematicFinished) {
      this.loadingFade = (float)Math.min(1, this.loadingFade + seconds * 1.2);
    }
    if(this.wasLoading && loading) {
      // Integrate the original clipped sine exactly. Presentation caps do not change colour speed.
      this.hue = (this.hue + 0.036 * (primitive(this.elapsed / 0.3) - primitive(previous / 0.3))) % 1;
    }
    this.wasLoading = loading;
    this.wasFadingLoading = loading && cinematicFinished;
  }

  private static double primitive(final double phase) {
    final double period = Math.PI * 2;
    final double end = Math.PI + Math.asin(1.0 / 3);
    final double start = period - Math.asin(1.0 / 3);
    final double cycles = Math.floor(phase / period);
    final double x = phase - cycles * period;
    final double total = integral(end) + integral(period) - integral(start);
    final double remainder = x <= end ? integral(x) : x <= start ? integral(end) : integral(end) + integral(x) - integral(start);
    return cycles * total + remainder;
  }

  private static double integral(final double x) { return 0.75 * (1 - Math.cos(x)) + 0.25 * x; }
}
