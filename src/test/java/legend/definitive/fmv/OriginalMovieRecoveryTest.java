package legend.definitive.fmv;

import org.junit.jupiter.api.Test;
import legend.core.audio.AudioSource;
import legend.core.audio.GenericSource;
import legend.core.spu.XaAdpcm;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Drives the original movie clock used by Fmv.renderOriginal; no window or audible output. */
class OriginalMovieRecoveryTest {
  private static final class PcmOutput implements OriginalMoviePlayback.Output {
    final ArrayDeque<short[]> buffers = new ArrayDeque<>();
    final ArrayList<Short> heard = new ArrayList<>();
    boolean available = true, loseNextBuffer;
    int generation, offset;
    long playedSamples;
    public boolean available() { return this.available; }
    public int generation() { return this.generation; }
    public long playedMicros() { return this.playedSamples * 1_000_000 / 37800; }
    public int freeBuffers() { return 16 - this.buffers.size(); }
    public boolean queued() { return !this.buffers.isEmpty(); }
    public void buffer(final short[] samples) {
      if(this.loseNextBuffer) { this.loseNextBuffer = false; this.reset(false); }
      else this.buffers.add(samples.clone());
    }
    void reset(final boolean available) {
      this.buffers.clear(); this.offset = 0; this.playedSamples = 0;
      this.available = available; this.generation++;
    }
    void advance(final int frames) {
      for(int frame = 0; frame < frames && !this.buffers.isEmpty(); frame++) {
        final short[] head = this.buffers.peek();
        this.heard.add(head[this.offset++]); this.heard.add(head[this.offset++]);
        this.playedSamples++;
        if(this.offset == head.length) { this.buffers.remove(); this.offset = 0; }
      }
    }
  }
  private static short[] expected(final int frames, final float volume) {
    final byte[] bytes = OriginalMovieTest.recording(frames);
    final var xa = new XaAdpcm.Decoder();
    final var samples = new ArrayList<Short>();
    for(int frame = 0; frame < frames; frame++) {
      for(final short sample : xa.decode(Arrays.copyOfRange(bytes, frame * 4704 + 2352, (frame + 1) * 4704), (byte)1)) samples.add((short)((sample >> 1) * volume));
    }
    return shorts(samples);
  }
  private static short[] shorts(final ArrayList<Short> samples) {
    final short[] result = new short[samples.size()];
    for(int i = 0; i < result.length; i++) result[i] = samples.get(i);
    return result;
  }
  private static void bounded(final OriginalMoviePlayback playback) throws Exception {
    final var field = OriginalMoviePlayback.class.getDeclaredField("retained"); field.setAccessible(true);
    assertTrue(((ArrayDeque<?>)field.get(playback)).size() <= 15, "Recovery history must stay within the native queue budget");
  }
  @Test void outputRecreationKeepsTheRemainingStoryMovieAtItsCurrentPosition() throws Exception {
    try(final var movie = OriginalMovieTest.movie(24)) {
      OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 24);
      final AtomicLong now = new AtomicLong();
      final var output = new OriginalMovieTest.Output();
      final var playback = new OriginalMoviePlayback(movie, now::get);
      playback.tick(output, 1);
      output.advance(100_000);
      now.set(100_000_000);
      final long before = playback.tick(output, 1);
      movie.pollVideo(before);
      // The real AudioSource destroy/init lifecycle drops queued PCM and resets its clock.
      output.samples.clear(); output.playedSamples = 0; output.generation++;
      assertEquals(before, playback.tick(output, 1), "Recreation must preserve the scene position instead of throwing into Fmv.stop()");
      output.advance(100_000); now.addAndGet(100_000_000);
      assertEquals(before + 100_000, playback.tick(output, 1), "Lost queued PCM must be recovered so story playback continues normally");
      assertFalse(playback.ended(output.queued()));
    }
  }
  @Test void repeatedPartialBufferResetsPreserveEveryUnplayedSampleAndScaleOnlyOnce() throws Exception {
    try(final var movie = OriginalMovieTest.movie(64)) {
      OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 32);
      final AtomicLong now = new AtomicLong(); final PcmOutput output = new PcmOutput();
      final var playback = new OriginalMoviePlayback(movie, now::get);
      long media = playback.tick(output, .5f); int lastFrame = -1;
      for(int tick = 0; tick < 260 && !playback.ended(output.queued()); tick++) {
        final var image = movie.pollVideo(media);
        if(image != null) { assertTrue(image.number() > lastFrame); lastFrame = image.number(); }
        output.advance(1000); now.addAndGet(1_000_000_000L * 1000 / 37800);
        media = playback.tick(output, .5f); bounded(playback);
        if(tick % 7 == 0 && output.queued()) {
          output.reset(true);
          assertEquals(media, playback.tick(output, .5f), "Reset cannot rewind or skip the scene");
        }
        Thread.sleep(1);
      }
      final var image = movie.pollVideo(media); if(image != null) lastFrame = image.number();
      assertTrue(playback.ended(output.queued())); assertEquals(63, lastFrame);
      assertArrayEquals(expected(64, .5f), shorts(output.heard), "Repeated recovery must not drop/repeat PCM or halve volume again");
    }
  }
  @Test void returningOutputAlignsWithSilentMovieTimeAndExcludesPausedTime() throws Exception {
    try(final var movie = OriginalMovieTest.movie(24)) {
      OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 24);
      final AtomicLong now = new AtomicLong(); final PcmOutput output = new PcmOutput();
      final var playback = new OriginalMoviePlayback(movie, now::get);
      playback.tick(output, 1); output.advance(3780); now.set(100_000_000);
      assertEquals(100_000, playback.tick(output, 1));
      output.reset(false); playback.tick(output, 1);
      now.set(300_000_000); assertEquals(300_000, playback.tick(output, 1));
      playback.setPaused(true); now.set(8_300_000_000L);
      output.reset(true); assertEquals(300_000, playback.tick(output, 1));
      assertFalse(output.queued(), "Paused recovery cannot start audio");
      playback.setPaused(false); now.addAndGet(100_000_000);
      assertEquals(400_000, playback.tick(output, 1), "Recovery must include active silent time since the last tick");
      output.advance(3780); now.addAndGet(100_000_000);
      assertEquals(500_000, playback.tick(output, 1));
      final short[] expected = expected(24, 1);
      assertArrayEquals(Arrays.copyOfRange(expected, 37800 * 4 / 10 * 2, 37800 * 5 / 10 * 2), Arrays.copyOfRange(shorts(output.heard), 3780 * 2, 7560 * 2));
    }
  }
  @Test void disconnectDuringBufferUploadRetainsTheUnacceptedPcm() throws Exception {
    try(final var movie = OriginalMovieTest.movie(2)) {
      OriginalMovieTest.await(() -> movie.peekVideo() != null && movie.bufferedAudioBlocks() == 2);
      final PcmOutput output = new PcmOutput(); final AtomicLong now = new AtomicLong();
      output.loseNextBuffer = true;
      final var playback = new OriginalMoviePlayback(movie, now::get);
      assertEquals(0, playback.tick(output, 1));
      output.reset(true); assertEquals(0, playback.tick(output, 1));
      output.advance(4032); playback.tick(output, 1);
      assertArrayEquals(expected(2, 1), shorts(output.heard)); bounded(playback);
    }
  }
  @Test void completeSilentOutageFinishesEveryVideoFrameWithBoundedHistory() throws Exception {
    try(final var movie = OriginalMovieTest.movie(64)) {
      OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 32);
      final PcmOutput output = new PcmOutput(); output.available = false;
      final AtomicLong now = new AtomicLong(); final var playback = new OriginalMoviePlayback(movie, now::get);
      long media = playback.tick(output, 1); int lastFrame = -1;
      for(int tick = 0; tick < 60 && !playback.ended(false); tick++) {
        final var image = movie.pollVideo(media); if(image != null) lastFrame = image.number();
        now.addAndGet(100_000_000); media = playback.tick(output, 1); bounded(playback);
        Thread.sleep(1);
      }
      final var image = movie.pollVideo(media); if(image != null) lastFrame = image.number();
      assertEquals(63, lastFrame); assertTrue(playback.ended(false)); assertTrue(output.heard.isEmpty());
      assertTrue(media >= movie.durationMicros() && media < movie.durationMicros() + 200_000);
    }
  }
  private static void lifecycle(final GenericSource source, final Class<?> owner, final String method) throws Exception {
    final var operation = owner.getDeclaredMethod(method); operation.setAccessible(true); operation.invoke(source);
  }
  @Test void actualNativeSourceRecreationRecoversAtTheMoviePositionWithoutAudibleOutput() throws Exception {
    long device = 0, context = 0; GenericSource source = null; boolean initialized = false;
    try {
      device = alcLoopbackOpenDeviceSOFT((ByteBuffer)null); assertNotEquals(0, device);
      final var capabilities = ALC.createCapabilities(device);
      context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 37800, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
      assertNotEquals(0, context); alcMakeContextCurrent(context); AL.createCapabilities(capabilities);
      source = new GenericSource(AL_FORMAT_STEREO16, 37800);
      lifecycle(source, GenericSource.class, "init"); initialized = true;
      try(final var movie = OriginalMovieTest.movie(24)) {
        OriginalMovieTest.await(() -> movie.bufferedVideoFrames() == 16 && movie.bufferedAudioBlocks() == 24);
        final AtomicLong now = new AtomicLong(); final var playback = new OriginalMoviePlayback(movie, now::get);
        playback.tick(source, 1); source.tick();
        alcRenderSamplesSOFT(device, new float[3780 * 2], 3780); now.set(100_000_000);
        final long before = playback.tick(source, 1); assertEquals(100_000, before, 30);
        lifecycle(source, AudioSource.class, "destroy"); initialized = false;
        lifecycle(source, GenericSource.class, "init"); initialized = true;
        assertEquals(before, playback.tick(source, 1)); assertTrue(source.hasQueuedOutput()); source.tick();
        alcRenderSamplesSOFT(device, new float[3780 * 2], 3780); now.addAndGet(100_000_000);
        assertEquals(200_000, playback.tick(source, 1), 30);
        assertFalse(playback.ended(source.hasQueuedOutput())); assertEquals(AL_NO_ERROR, alGetError());
      }
    } finally {
      try { if(initialized) lifecycle(source, AudioSource.class, "destroy"); }
      finally {
        try { if(context != 0) { alcMakeContextCurrent(0); alcDestroyContext(context); } }
        finally { if(device != 0) alcCloseDevice(device); }
      }
    }
  }
}
