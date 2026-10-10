package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import java.io.IOException;

/** Shared audio pump and media clock for cinematics and the engine intro. */
public final class MoviePlayback {
  private final StreamingMovie movie;
  private long playedMicros;
  private long tailStart;
  private long tailOffset;
  private int audioGeneration = -1;

  public MoviePlayback(final StreamingMovie movie) { this.movie = movie; }

  public long tick(final GenericSource source, final float volume) throws IOException {
    if(this.movie.failure() != null) throw this.movie.failure();
    if(this.audioGeneration == -1) this.audioGeneration = source.generation();
    if(source.generation() != this.audioGeneration) throw new IOException("Audio output was reinitialized during video playback");
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
      if(this.tailStart == 0) {
        this.tailStart = System.nanoTime();
        this.tailOffset = this.playedMicros;
      }
      this.playedMicros = this.tailOffset + (System.nanoTime() - this.tailStart) / 1000;
    }
    return this.playedMicros;
  }
}
