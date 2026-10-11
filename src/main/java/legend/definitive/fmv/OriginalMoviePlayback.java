package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
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
  private static final int SAMPLE_RATE = 37800;
  private static final int RETAINED_BLOCK_LIMIT = 15;
  private static final class AudioBlock {
    final long firstSample;
    final short[] samples;
    boolean submitted;
    AudioBlock(final long firstSample, final short[] samples) { this.firstSample = firstSample; this.samples = samples; }
    long endSample() { return this.firstSample + this.samples.length / 2; }
  }
  // Keep only the bounded, unplayed native queue. The decoder retains its own backpressure.
  private final ArrayDeque<AudioBlock> retained = new ArrayDeque<>();
  private long decodedSamples, outputOffsetMicros;
  private long playedMicros, lastNanos, tailOffset, tailNanos;
  private boolean started, tail, paused, rebaseOutput;
  private int generation = -1;
  public OriginalMoviePlayback(final OriginalMovie movie) { this(movie, System::nanoTime); }
  public OriginalMoviePlayback(final OriginalMovie movie, final LongSupplier clock) { this.movie = movie; this.clock = clock; this.lastNanos = clock.getAsLong(); }
  public void setPaused(final boolean paused) { this.advance(); this.paused = paused; }
  private void advance() {
    final long now = this.clock.getAsLong();
    if(this.started && this.tail && !this.paused) {
      this.tailNanos += Math.max(0, now - this.lastNanos);
      this.playedMicros = this.tailOffset + this.tailNanos / 1000;
    }
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
    boolean available = output.available();
    if(this.generation != output.generation() || !available) this.invalidateOutput(output.generation());
    if(this.paused) return this.playedMicros;
    if(available && !this.rebaseOutput) {
      // Retire completed output before testing capacity. Keep one free for the audio tick.
      final long position = this.outputOffsetMicros + output.playedMicros();
      available = output.available() && this.generation == output.generation();
      if(available) this.playedMicros = Math.max(this.playedMicros, position);
      else this.invalidateOutput(output.generation());
    }
    final long firstUnplayed = (this.playedMicros * SAMPLE_RATE + 999_999) / 1_000_000;
    this.retirePlayed(firstUnplayed);
    if(available) {
      if(this.rebaseOutput) {
        this.outputOffsetMicros = firstUnplayed * 1_000_000 / SAMPLE_RATE;
        this.rebaseOutput = false;
      }
      for(final AudioBlock block : this.retained) {
        if(!block.submitted && output.freeBuffers() > 1) {
          if(!this.submit(output, block, firstUnplayed, volume)) { available = false; break; }
        }
      }
      while(available && output.freeBuffers() > 1 && this.retained.size() < RETAINED_BLOCK_LIMIT && this.movie.peekAudio() != null) {
        final AudioBlock block = this.retainNext();
        if(block.endSample() <= firstUnplayed) { this.retirePlayed(firstUnplayed); continue; }
        available = this.submit(output, block, firstUnplayed, volume);
      }
    } else {
      // Silent playback advances normally, but discard only audio already passed on that
      // timeline. Keep future PCM so a returning device can resume at the current scene.
      while(this.decodedSamples < firstUnplayed && this.movie.peekAudio() != null) {
        this.retainNext(); this.retirePlayed(firstUnplayed);
      }
    }
    if(!this.started && this.movie.peekVideo() != null) { this.started = true; this.lastNanos = this.clock.getAsLong(); }
    if(this.started && (!available || this.movie.audioDrained() && this.retained.isEmpty() && !output.queued())) {
      if(!this.tail) { this.tail = true; this.tailOffset = this.playedMicros; this.tailNanos = 0; }
      this.playedMicros = this.tailOffset + this.tailNanos / 1000;
    } else this.tail = false;
    return this.playedMicros;
  }
  private void invalidateOutput(final int generation) {
    this.generation = generation;
    this.rebaseOutput = true;
    for(final AudioBlock block : this.retained) block.submitted = false;
  }
  private AudioBlock retainNext() {
    final AudioBlock block = new AudioBlock(this.decodedSamples, this.movie.pollAudio());
    this.decodedSamples = block.endSample(); this.retained.addLast(block);
    return block;
  }
  private void retirePlayed(final long firstUnplayed) {
    while(!this.retained.isEmpty() && this.retained.peekFirst().endSample() <= firstUnplayed) this.retained.removeFirst();
  }
  private boolean submit(final Output output, final AudioBlock block, final long firstUnplayed, final float volume) {
    final int skipped = (int)Math.max(0, firstUnplayed - block.firstSample);
    final short[] samples = Arrays.copyOfRange(block.samples, skipped * 2, block.samples.length);
    for(int i = 0; i < samples.length; i++) samples[i] = (short)((samples[i] >> 1) * volume);
    output.buffer(samples);
    if(!output.available() || output.generation() != this.generation) {
      this.invalidateOutput(output.generation());
      return false;
    }
    block.submitted = true;
    return true;
  }
  public boolean ended(final boolean queuedAudio) { return this.started && this.movie.drained() && this.retained.isEmpty() && !queuedAudio && this.playedMicros >= this.movie.durationMicros(); }
}
