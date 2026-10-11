package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import java.io.IOException;
import java.util.function.LongSupplier;

/** Original media follows played samples; unavailable audio and a silent tail use active time. */
public final class OriginalMoviePlayback {
  public interface Output {
    boolean available();
    int generation();
    long playedMicros();
    int freeBuffers();
    boolean queued();
    void buffer(short[] samples);
  }
  private final OriginalMovie movie;
  private final LongSupplier clock;
  private long playedMicros, lastNanos, tailOffset, tailNanos;
  private boolean started, tail, paused;
  private int generation = -1;
  public OriginalMoviePlayback(final OriginalMovie movie) { this(movie, System::nanoTime); }
  public OriginalMoviePlayback(final OriginalMovie movie, final LongSupplier clock) { this.movie = movie; this.clock = clock; this.lastNanos = clock.getAsLong(); }
  public void setPaused(final boolean paused) { this.advance(); this.paused = paused; }
  private void advance() {
    final long now = this.clock.getAsLong();
    if(this.started && this.tail && !this.paused) this.tailNanos += Math.max(0, now - this.lastNanos);
    this.lastNanos = now;
  }
  public long tick(final GenericSource source, final float volume) throws IOException {
    synchronized(source) {
      return this.tick(new Output() {
        public boolean available() { return source.outputAvailable(); }
        public int generation() { return source.generation(); }
        public long playedMicros() { return (long)(source.getPlaybackPositionSeconds() * 1_000_000); }
        public int freeBuffers() { return source.availableBuffers(); }
        public boolean queued() { return source.hasQueuedOutput(); }
        public void buffer(final short[] samples) { source.bufferOutput(samples); }
      }, volume);
    }
  }
  public long tick(final Output output, final float volume) throws IOException {
    this.advance();
    if(this.movie.failure() != null) throw this.movie.failure();
    if(this.generation == -1) this.generation = output.generation();
    if(this.generation != output.generation()) throw new IOException("Original movie audio output was reinitialized");
    if(this.paused) return this.playedMicros;
    if(output.available()) {
      // Retire completed output before testing capacity. Keep one free for the audio tick.
      this.playedMicros = Math.max(this.playedMicros, output.playedMicros());
      while(output.freeBuffers() > 1 && this.movie.peekAudio() != null) {
        final short[] samples = this.movie.pollAudio();
        for(int i = 0; i < samples.length; i++) samples[i] = (short)((samples[i] >> 1) * volume);
        output.buffer(samples);
      }
    } else {
      while(this.movie.pollAudio() != null) { }
    }
    if(!this.started && this.movie.peekVideo() != null) { this.started = true; this.lastNanos = this.clock.getAsLong(); }
    if(this.started && (!output.available() || this.movie.audioDrained() && !output.queued())) {
      if(!this.tail) { this.tail = true; this.tailOffset = this.playedMicros; this.tailNanos = 0; }
      this.playedMicros = this.tailOffset + this.tailNanos / 1000;
    }
    return this.playedMicros;
  }
  public boolean ended(final boolean queuedAudio) { return this.started && this.movie.drained() && !queuedAudio && this.playedMicros >= this.movie.durationMicros(); }
}
