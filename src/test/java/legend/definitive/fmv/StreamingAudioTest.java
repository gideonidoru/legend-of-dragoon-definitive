package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import org.junit.jupiter.api.Test;
import org.lwjgl.openal.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the OpenAL null driver: actual buffer behavior, no sound or window. */
final class StreamingAudioTest {
  private static final class Probe extends GenericSource {
    Probe() { super(AL_FORMAT_STEREO16, 48000); }
    void initialize() { super.init(); }
    void process() { super.handleProcessedBuffers(); }
    void release() { super.destroy(); }
  }

  @Test void longSilentVideoTailCompletesWithoutDecoderDeadlock() throws Exception {
    final String file = System.getProperty("fmvhd.tailFixture");
    org.junit.jupiter.api.Assumptions.assumeTrue(file != null, "Supply -PfmvTailFixture for tail regression");
    final long device = alcOpenDevice((java.nio.ByteBuffer)null);
    final var capabilities = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, (java.nio.IntBuffer)null);
    alcMakeContextCurrent(context); AL.createCapabilities(capabilities);
    final Probe audio = new Probe();
    try(final var movie = new StreamingMovie(java.nio.file.Path.of(file))) {
      audio.initialize();
      final var playback = new MoviePlayback(movie);
      final long timeout = System.nanoTime() + 6_000_000_000L;
      long clock = 0;
      while((!movie.drained() || audio.hasQueuedOutput() || clock < movie.durationMicros) && System.nanoTime() < timeout) {
        clock = playback.tick(audio, 1.0f);
        movie.pollVideo(clock);
        audio.tick(); audio.process();
        Thread.sleep(5);
      }
      assertTrue(movie.drained(), "Independent audio EOF must release tail: clock=" + clock + ", images=" + movie.bufferedImages() + ", audio=" + movie.bufferedAudio() + ", audioEOF=" + movie.audioDrained());
      assertFalse(audio.hasQueuedOutput());
      assertTrue(clock >= movie.durationMicros);
    } finally { audio.release(); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void playsWithReservedBufferAndClockSurvivesUnqueue() throws Exception {
    final long device = alcOpenDevice((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final ALCCapabilities capabilities = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, (java.nio.IntBuffer)null);
    assertNotEquals(0, context);
    alcMakeContextCurrent(context);
    AL.createCapabilities(capabilities);
    final Probe audio = new Probe();
    try {
      audio.initialize();
      final short[] packet = new short[960 * 2]; // 20 ms stereo
      while(audio.availableBuffers() > 1) audio.bufferOutput(packet);
      assertEquals(1, audio.availableBuffers());
      assertTrue(audio.canBuffer());
      audio.tick();
      float previous = 0;
      for(int i = 0; i < 40; i++) {
        Thread.sleep(10);
        audio.process();
        final float clock = audio.getPlaybackPosition();
        assertTrue(clock + 0.002 >= previous, "Cumulative audio time must survive buffer removal");
        previous = clock;
      }
      assertEquals(0.3f, audio.getPlaybackPosition(), 0.01f);
      assertFalse(audio.hasQueuedOutput());
      assertEquals(16, audio.availableBuffers());
    } finally {
      audio.release();
      alcMakeContextCurrent(0);
      alcDestroyContext(context);
      alcCloseDevice(device);
    }
  }
}
