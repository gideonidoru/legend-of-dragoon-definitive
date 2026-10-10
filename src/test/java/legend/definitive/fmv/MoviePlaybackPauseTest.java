package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoviePlaybackPauseTest {
  @Test void unavailableOutputClockExcludesActualPauseForCinematicsAndIntro() throws Exception {
    final GenericSource unavailable = new GenericSource(org.lwjgl.openal.AL10.AL_FORMAT_STEREO16, 48000);
    for(final long origin : new long[] {0, Long.MAX_VALUE - 250_000_000L}) {
      final AtomicLong now = new AtomicLong(origin);
      try(final var movie = new StreamingMovie(Path.of("gfx/intro.mp4"))) {
        final var playback = new MoviePlayback(movie, now::get);
        assertEquals(0, playback.tick(unavailable, 1));
        now.addAndGet(500_000_000); assertEquals(500_000, playback.tick(unavailable, 1));
        playback.setPaused(true); now.addAndGet(30_000_000_000L);
        assertEquals(500_000, playback.tick(unavailable, 1));
        playback.setPaused(false); now.addAndGet(500_000_000);
        assertEquals(1_000_000, playback.tick(unavailable, 1));
      }
    }
  }
}
