package legend.definitive.fmv;

import legend.game.modding.events.fmv.FmvPlaybackEvent;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class StreamingMovieTest {
  private static Path fixture() {
    final String file = System.getProperty("fmvhd.fixture");
    assumeTrue(file != null, "Supply -PfmvFixture for actual headless decoder checks");
    return Path.of(file);
  }

  @Test void sourceIdentityMatchesKnownDigest() {
    final var event = new FmvPlaybackEvent("STR/TEST.IKI", new byte[0]);
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", event.sourceSha256());
  }

  @Test void decodedQueuesRemainBoundedAndCancellationReturns() throws Exception {
    final StreamingMovie movie = new StreamingMovie(fixture());
    try {
      Thread.sleep(250);
      assertTrue(movie.bufferedImages() <= 6);
      assertTrue(movie.bufferedAudio() <= 64);
      assertNotNull(movie.pollVideo(0));
      final long start = System.nanoTime();
      movie.close();
      assertTrue(System.nanoTime() - start < 2_000_000_000L);
      assertEquals(0, movie.bufferedImages());
      assertEquals(0, movie.bufferedAudio());
    } finally { movie.close(); }
  }

  @Test void decodesWholeFixtureAndKeepsFutureFramesQueued() throws Exception {
    try(final StreamingMovie movie = new StreamingMovie(fixture())) {
      long clock = 0; int images = 0, audioSamples = 0;
      final long timeout = System.nanoTime() + 10_000_000_000L;
      while(!movie.drained() && System.nanoTime() < timeout) {
        short[] audio;
        while((audio = movie.pollAudio()) != null) audioSamples += audio.length;
        final var frame = movie.pollVideo(clock);
        if(frame != null) { assertEquals(movie.width * movie.height * 3, frame.rgb().length); images++; }
        assertNull(movie.failure());
        clock += 10_000;
        Thread.sleep(1);
      }
      assertTrue(movie.drained());
      assertTrue(images > 0);
      assertTrue(audioSamples > 0);
    }
  }
}
