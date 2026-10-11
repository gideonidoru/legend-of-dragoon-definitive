package legend.definitive.fmv;

import legend.core.spu.XaAdpcm;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class OriginalMovieTest {
  static byte[] recording(final int frames) {
    final byte[] bytes = new byte[frames * 2 * 2352];
    final ByteBuffer out = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    for(int frame = 0; frame < frames; frame++) {
      final int video = frame * 4704;
      bytes[video + 18] = 8;
      out.putShort(video + 28, (short)0).putShort(video + 30, (short)1).putInt(video + 32, frame + 1).putInt(video + 36, 32).putShort(video + 40, (short)16).putShort(video + 42, (short)16);
      final int audio = video + 2352;
      bytes[audio + 18] = (byte)(4 | (frame == frames - 1 ? 128 : 0));
      bytes[audio + 19] = 1;
      for(int group = 0; group < 18; group++) {
        for(int data = 16; data < 128; data++) bytes[audio + 24 + group * 128 + data] = (byte)(frame + 1);
      }
    }
    return bytes;
  }
  static OriginalMovie movie(final int frames) {
    return new OriginalMovie(new FileData(recording(frames)), (data, size, number) -> new OriginalMovie.VideoFrame(number, 16, 16, new int[256]));
  }
  static void await(final java.util.function.BooleanSupplier condition) throws Exception {
    final long deadline = System.nanoTime() + 2_000_000_000L;
    while(!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
    assertTrue(condition.getAsBoolean(), "Decoder did not reach the expected bounded state");
  }
  static final class Output implements OriginalMoviePlayback.Output {
    final ArrayDeque<Integer> samples = new ArrayDeque<>();
    boolean available = true;
    int generation, blocks;
    long playedSamples;
    public boolean available() { return this.available; }
    public int generation() { return this.generation; }
    public long playedMicros() { return this.playedSamples * 1_000_000L / 37800; }
    public int freeBuffers() { return 16 - this.samples.size(); }
    public boolean queued() { return !this.samples.isEmpty(); }
    public void buffer(final short[] data) { this.samples.add(data.length / 2); this.blocks++; }
    void advance(final long micros) {
      long remaining = micros * 37800 / 1_000_000;
      while(remaining > 0 && !this.samples.isEmpty()) {
        final int head = this.samples.remove();
        final int played = (int)Math.min(head, remaining);
        this.playedSamples += played; remaining -= played;
        if(head > played) this.samples.addFirst(head - played);
      }
    }
  }
  @Test void eofAudioAndPredictorOrderingSurviveBoundedBackpressure() throws Exception {
    final byte[] bytes = recording(64);
    final XaAdpcm.Decoder expected = new XaAdpcm.Decoder();
    try(final var movie = movie(64)) {
      await(() -> movie.bufferedVideoFrames() == 16);
      assertTrue(movie.bufferedAudioBlocks() <= 32);
      for(int frame = 0; frame < 64; frame++) {
        await(() -> movie.peekVideo() != null && movie.peekAudio() != null);
        final byte[] sector = java.util.Arrays.copyOfRange(bytes, frame * 4704 + 2352, (frame + 1) * 4704);
        assertArrayEquals(expected.decode(sector, (byte)1), movie.pollAudio());
        assertEquals(frame, movie.pollVideo(frame * 1_000_000L / 15).number());
      }
      await(movie::drained);
      assertNull(movie.failure());
      assertEquals(64 * 1_000_000L / 15, movie.durationMicros());
    }
    final byte[] sector = java.util.Arrays.copyOfRange(bytes, 2352, 4704);
    assertArrayEquals(new XaAdpcm.Decoder().decode(sector, (byte)1), new XaAdpcm.Decoder().decode(sector, (byte)1));
  }
  @Test void everyPresentationRateUsesPlayedSamplesAndDrainsTheFinalTail() throws Exception {
    for(final int rate : new int[] {5, 10, 15, 30, 60, 120}) {
      try(final var movie = movie(60)) {
        await(() -> movie.bufferedVideoFrames() == 16);
        await(() -> movie.bufferedAudioBlocks() >= 16);
        final AtomicLong now = new AtomicLong();
        final var playback = new OriginalMoviePlayback(movie, now::get);
        final Output output = new Output();
        long media = playback.tick(output, 1), previous = 0;
        int lastFrame = -1;
        for(int tick = 0; tick < rate * 8 && !playback.ended(output.queued()); tick++) {
          final var image = movie.pollVideo(media);
          if(image != null) { assertTrue(image.number() > lastFrame); lastFrame = image.number(); }
          Thread.sleep(1);
          now.addAndGet(1_000_000_000L / rate); output.advance(1_000_000L / rate);
          media = playback.tick(output, 1);
          assertTrue(media >= previous); previous = media;
        }
        movie.pollVideo(media);
        assertTrue(playback.ended(output.queued()), "Rate " + rate);
        assertEquals(60, output.blocks, "No PCM is dropped even at " + rate);
        assertTrue(media >= 4_000_000 && media <= 4_000_000 + 2_000_000 / rate, "Native duration at " + rate + ": " + media);
      }
    }
  }
  @Test void saturationAndStarvationFreezeSampleTimeWithoutLosingPendingPcm() throws Exception {
    try(final var movie = movie(24)) {
      await(() -> movie.bufferedVideoFrames() == 16);
      await(() -> movie.bufferedAudioBlocks() == 24);
      final AtomicLong now = new AtomicLong(); final Output output = new Output();
      final var playback = new OriginalMoviePlayback(movie, now::get);
      playback.tick(output, 1);
      assertEquals(15, output.samples.size());
      final int pending = movie.bufferedAudioBlocks();
      now.set(5_000_000_000L);
      assertEquals(0, playback.tick(output, 1));
      assertTrue(movie.bufferedAudioBlocks() >= pending);
      assertEquals(15, output.blocks);
      output.advance(200_000); final long position = playback.tick(output, 1);
      assertEquals(200_000, position);
      assertTrue(output.blocks > 15);
    }
  }
  @Test void silentPlaybackExcludesPauseAndSkipTerminatesABlockedDecoder() throws Exception {
    try(final var movie = movie(64)) {
      await(() -> movie.bufferedVideoFrames() == 16);
      final AtomicLong now = new AtomicLong(); final Output output = new Output(); output.available = false;
      final var playback = new OriginalMoviePlayback(movie, now::get);
      assertEquals(0, playback.tick(output, 1));
      now.set(500_000_000); assertEquals(500_000, playback.tick(output, 1));
      playback.setPaused(true); now.set(8_500_000_000L);
      assertEquals(500_000, playback.tick(output, 1));
      playback.setPaused(false); now.addAndGet(500_000_000);
      assertEquals(1_000_000, playback.tick(output, 1));
      assertTimeout(Duration.ofSeconds(1), movie::close);
      assertEquals(0, movie.bufferedVideoFrames()); assertEquals(0, movie.bufferedAudioBlocks());
    }
  }
  @Test void corruptChunksAndDeviceGenerationFailInsteadOfHanging() throws Exception {
    final byte[] bytes = recording(1); bytes[36] = 0;
    try(final var movie = new OriginalMovie(new FileData(bytes), (data, size, number) -> { throw new AssertionError(); })) {
      await(() -> movie.failure() != null);
      assertThrows(IOException.class, () -> new OriginalMoviePlayback(movie).tick(new Output(), 1));
    }
    try(final var movie = movie(2)) {
      await(() -> movie.peekVideo() != null);
      final Output output = new Output(); final var playback = new OriginalMoviePlayback(movie);
      playback.tick(output, 1); output.generation++;
      assertThrows(IOException.class, () -> playback.tick(output, 1));
    }
  }
  @Test void knownSilentTailAndVideoOnlyMoviesAdvanceBeyondTheVideoQueue() throws Exception {
    for(final boolean noAudio : new boolean[] {false, true}) {
      final byte[] bytes = recording(64);
      for(int frame = noAudio ? 0 : 1; frame < 64; frame++) {
        final int offset = frame * 4704 + 2352;
        java.util.Arrays.fill(bytes, offset, offset + 2352, (byte)0);
        bytes[offset + 18] = (byte)(8 | (frame == 63 ? 128 : 0));
      }
      try(final var movie = new OriginalMovie(new FileData(bytes), (data, size, number) -> new OriginalMovie.VideoFrame(number, 16, 16, new int[256]))) {
        await(() -> movie.bufferedVideoFrames() == 16);
        final AtomicLong now = new AtomicLong(); final Output output = new Output();
        final var playback = new OriginalMoviePlayback(movie, now::get);
        long media = playback.tick(output, 1);
        for(int tick = 0; tick < 100 && !playback.ended(output.queued()); tick++) {
          movie.pollVideo(media); Thread.sleep(1);
          now.addAndGet(100_000_000); output.advance(100_000);
          media = playback.tick(output, 1);
        }
        movie.pollVideo(media);
        assertTrue(playback.ended(output.queued()), "Tail must not wait for a blocked video producer");
        assertEquals(noAudio ? 0 : 1, output.blocks);
      }
    }
  }
  @Test void knownFutureAudioPreventsFalseWallClockRecoveryDuringStarvation() throws Exception {
    final byte[] bytes = recording(64);
    for(int frame = 0; frame < 40; frame++) {
      final int offset = frame * 4704 + 2352;
      java.util.Arrays.fill(bytes, offset, offset + 2352, (byte)0); bytes[offset + 18] = 8;
    }
    try(final var movie = new OriginalMovie(new FileData(bytes), (data, size, number) -> new OriginalMovie.VideoFrame(number, 16, 16, new int[256]))) {
      await(() -> movie.bufferedVideoFrames() == 16);
      final AtomicLong now = new AtomicLong(); final Output output = new Output();
      final var playback = new OriginalMoviePlayback(movie, now::get);
      await(() -> movie.peekAudio() != null);
      final long played = playback.tick(output, 1);
      now.set(30_000_000_000L);
      assertEquals(played, playback.tick(output, 1), "Known future audio must not be skipped as a silent tail");
      assertTrue(output.queued(), "Audio producer reaches delayed audio beyond a full video queue");
      assertEquals(0, played);
    }
  }
}
