package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import java.io.IOException;
import java.util.function.LongSupplier;

/** Shared audio pump and media clock for cinematics and the engine intro. */
public final class MoviePlayback {
  private final StreamingMovie movie;
  private final LongSupplier clock;
  private long playedMicros;
  private long tailStart;
  private long tailOffset;
  private boolean tailStarted;
  private int audioGeneration = -1;
  private boolean paused;
  private long pauseStart;

  public MoviePlayback(final StreamingMovie movie) { this(movie, System::nanoTime); }
  public MoviePlayback(final StreamingMovie movie, final LongSupplier clock) { this.movie = movie; this.clock = clock; }

  /** Pause audio separately; exclude an actual renderer pause from a silent wall-clock tail. */
  public void setPaused(final boolean paused) {
    if(this.paused == paused) return;
    final long now = this.clock.getAsLong();
    if(paused) this.pauseStart = now;
    else if(this.tailStarted) this.tailStart += Math.max(0, now - this.pauseStart);
    this.paused = paused;
  }

  public long tick(final GenericSource source, final float volume) throws IOException {
    if(this.movie.failure() != null) throw this.movie.failure();
    if(this.audioGeneration == -1) this.audioGeneration = source.generation();
    if(source.generation() != this.audioGeneration) throw new IOException("Audio output was reinitialized during video playback");
    if(this.paused) return this.playedMicros;
    if(source.outputAvailable()) {
      synchronized(source) {
        while(source.canBuffer() && source.availableBuffers() > 1 && this.movie.peekAudio() != null) {
          final short[] pcm = this.movie.pollAudio();
          for(int i = 0; i < pcm.length; i++) pcm[i] = (short)(pcm[i] * volume);
          source.bufferOutput(pcm);
        }
      }
      this.playedMicros = Math.max(this.playedMicros, (long)(source.getPlaybackPositionSeconds() * 1_000_000));
    } else {
      // Disabled/unavailable audio must not block video or fill the audio queue forever.
      while(this.movie.pollAudio() != null) { }
    }
    if(!source.outputAvailable() || this.movie.audioDrained() && !source.hasQueuedOutput()) {
      if(!this.tailStarted) {
        this.tailStarted = true;
        this.tailStart = this.clock.getAsLong();
        this.tailOffset = this.playedMicros;
      }
      this.playedMicros = this.tailOffset + (this.clock.getAsLong() - this.tailStart) / 1000;
    }
    return this.playedMicros;
  }
}
