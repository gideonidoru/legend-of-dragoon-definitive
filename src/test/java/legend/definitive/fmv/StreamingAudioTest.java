package legend.definitive.fmv;

import legend.core.audio.GenericSource;
import org.junit.jupiter.api.Test;
import org.lwjgl.openal.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the OpenAL null driver: actual buffer behavior, no sound or window. */
final class StreamingAudioTest {
  private static final class Probe extends GenericSource {
    Runnable beforePosition;
    Runnable afterProcessed;
    Probe() { this(AL_FORMAT_STEREO16); }
    Probe(final int format) { super(format, 48000); }
    void floats(final float[] samples) { super.bufferOutput(org.lwjgl.openal.EXTFloat32.AL_FORMAT_STEREO_FLOAT32, samples, 48000); }
    void initialize() { super.init(); }
    void process() { super.handleProcessedBuffers(); }
    void release() { super.destroy(); }
    @Override public float getPosition() {
      if(this.beforePosition != null) { final Runnable action = this.beforePosition; this.beforePosition = null; action.run(); }
      return super.getPosition();
    }
    @Override protected void handleProcessedBuffers() {
      super.handleProcessedBuffers();
      if(this.afterProcessed != null) { final Runnable action = this.afterProcessed; this.afterProcessed = null; action.run(); }
    }
  }

  private static void queuePacket(final Probe audio, final short[] samples, final int kind) {
    if(kind == 0) audio.bufferOutput(samples);
    else if(kind == 1) {
      final var bytes = java.nio.ByteBuffer.allocateDirect(samples.length * 2).order(java.nio.ByteOrder.nativeOrder());
      bytes.asShortBuffer().put(samples);
      audio.bufferOutput(bytes);
    } else {
      final float[] floats = new float[samples.length];
      for(int i = 0; i < samples.length; i++) floats[i] = samples[i] / 32768.0f;
      audio.floats(floats);
    }
  }

  @Test void cinematicPauseKeepsSamplesAndAudioTickCannotRestartIt() {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try {
      audio.initialize(); audio.bufferOutput(new short[960 * 2]); audio.tick();
      final float[] output = new float[9600];
      alcRenderSamplesSOFT(device, output, 480);
      final double position = audio.getPlaybackPositionSeconds();
      audio.setPlaybackPaused(true); audio.bufferOutput(new short[960 * 2]);
      for(int tick = 0; tick < 10; tick++) { audio.tick(); alcRenderSamplesSOFT(device, output, 480); }
      assertEquals(position, audio.getPlaybackPositionSeconds(), 0.000001);
      assertTrue(audio.hasQueuedOutput());
      audio.setPlaybackPaused(false); audio.tick(); alcRenderSamplesSOFT(device, output, 1920);
      assertEquals(0.04, audio.getPlaybackPositionSeconds(), 0.000001);
      assertFalse(audio.hasQueuedOutput());
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void refillingAllSampleFormatsPreservesContinuousAudioSamples() {
    for(int kind = 0; kind < 3; kind++) {
      assertArrayEquals(captureWaveform(kind, false), captureWaveform(kind, true), 0.00001f,
        "Refill must preserve continuous waveform for sample format " + kind);
    }
  }

  /** Each capture starts with fresh native mixer history; no sound output. */
  private static float[] captureWaveform(final int kind, final boolean refill) {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe(kind == 2 ? org.lwjgl.openal.EXTFloat32.AL_FORMAT_STEREO_FLOAT32 : AL_FORMAT_STEREO16);
    try {
      final short[] packet = new short[960 * 2];
      for(int i = 0; i < 960; i++) packet[2 * i] = packet[2 * i + 1] = (short)(8000 * Math.sin(i * Math.PI / 24));
      final float[] output = new float[7680];
      audio.initialize();
      if(!refill) {
        for(int i = 0; i < 4; i++) queuePacket(audio, packet, kind);
        audio.tick(); alcRenderSamplesSOFT(device, output, 3840);
      } else {
        queuePacket(audio, packet, kind); audio.tick();
        final float[] half = new float[960];
        alcRenderSamplesSOFT(device, half, 480); System.arraycopy(half, 0, output, 0, half.length);
        final float[] whole = new float[1920];
        for(int i = 0; i < 3; i++) {
          queuePacket(audio, packet, kind); audio.tick();
          alcRenderSamplesSOFT(device, whole, 960); System.arraycopy(whole, 0, output, 960 + i * 1920, whole.length);
        }
        alcRenderSamplesSOFT(device, half, 480); System.arraycopy(half, 0, output, 6720, half.length);
      }
      assertEquals(0.08, audio.getPlaybackPositionSeconds(), 0.000001);
      return output;
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void refillPreservesAudioWhenTheOldTailFinishesInsideQueuePreparation() {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try {
      audio.initialize();
      final short[] packet = new short[960 * 2];
      audio.bufferOutput(packet); audio.tick();
      final float[] output = new float[2880];
      alcRenderSamplesSOFT(device, output, 480);
      audio.afterProcessed = () -> alcRenderSamplesSOFT(device, output, 480);
      audio.bufferOutput(packet); audio.tick();
      assertTrue(audio.getPlaybackPositionSeconds() <= 0.020001,
        "Only actually played old audio may advance the clock during refill");
      assertTrue(audio.hasQueuedOutput(), "Fresh audio must remain playable across the native underflow boundary");
      alcRenderSamplesSOFT(device, output, 1440); audio.process();
      assertEquals(0.04, audio.getPlaybackPositionSeconds(), 0.000001);
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void tickDoesNotReplayAnOldTailThatFinishesBetweenRetirementAndPlay() {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try {
      audio.initialize(); audio.bufferOutput(new short[960 * 2]); audio.tick();
      final float[] output = new float[960];
      alcRenderSamplesSOFT(device, output, 480);
      audio.afterProcessed = () -> alcRenderSamplesSOFT(device, output, 480);
      audio.tick();
      assertFalse(audio.hasQueuedOutput(), "A completed old queue must drain rather than replay");
      assertEquals(0.02, audio.getPlaybackPositionSeconds(), 0.000001);
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void clockIncludesTailFinishingDuringPositionQuery() {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try {
      audio.initialize();
      audio.bufferOutput(new short[960 * 2]);
      audio.tick();
      final float[] output = new float[960];
      alcRenderSamplesSOFT(device, output, 480);
      assertEquals(0.01, audio.getPlaybackPositionSeconds(), 0.000001);
      audio.beforePosition = () -> alcRenderSamplesSOFT(device, output, 480);
      assertEquals(0.02, audio.getPlaybackPositionSeconds(), 0.000001,
        "Finishing after the processed-buffer query must preserve the complete played tail");
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void emptyStartAndUnderflowCannotCountUnplayedRefillsAsPlayed() {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try {
      audio.initialize();
      final short[] packet = new short[960 * 2];
      final float[] output = new float[960 * 2];
      for(int refill = 0; refill < 4; refill++) {
        audio.tick(); // First tick is empty; subsequent refills follow natural underflow.
        final double playedBefore = refill * 0.02;
        audio.bufferOutput(packet);
        audio.tick();
        assertEquals(playedBefore, audio.getPlaybackPositionSeconds(), 0.000001,
          "Newly queued audio must not advance the movie clock before any samples play, refill " + refill);
        assertTrue(audio.hasQueuedOutput(), "Refill must remain queued for actual playback");
        alcRenderSamplesSOFT(device, output, 960);
        audio.process();
        assertEquals(playedBefore + 0.02, audio.getPlaybackPositionSeconds(), 0.000001);
      }
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  @Test void shippedIntroAdvancesAtPlayedAudioSpeed() throws Exception {
    assertPlayedAudioSpeed(java.nio.file.Path.of("gfx/intro.mp4"));
  }

  @Test void shippedOpeningCinematicAdvancesAtPlayedAudioSpeed(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path temporary) throws Exception {
    final var source = java.nio.file.Path.of("build/fmvhd/videos.zip");
    org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(source), "Build the pinned FMV payload before the actual opening check");
    final var opening = temporary.resolve("opening.mp4");
    try(final var zip = new java.util.zip.ZipFile(source.toFile()); final var input = zip.getInputStream(zip.getEntry("fmvhd/videos/OPENH.mp4"))) {
      java.nio.file.Files.copy(input, opening);
    }
    assertPlayedAudioSpeed(opening);
  }

  private static void assertPlayedAudioSpeed(final java.nio.file.Path video) throws Exception {
    assertPlayedAudioSpeed(video, 8, 100);
  }

  @Test void everyShippedFilmKeepsItsClockAcrossRenderRates(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path temporary) throws Exception {
    final var payload = java.nio.file.Path.of("build/fmvhd/videos.zip");
    org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(payload), "Build the pinned FMV payload before checking all films");
    try(final var zip = new java.util.zip.ZipFile(payload.toFile())) {
      final var films = zip.stream().filter(entry -> entry.getName().endsWith(".mp4")).toList();
      assertEquals(18, films.size(), "Every shipped film must participate in the pacing check");
      int index = 0;
      for(final var film : films) {
        final var video = temporary.resolve("film.mp4");
        try {
          try(final var input = zip.getInputStream(film)) { java.nio.file.Files.copy(input, video); }
          assertPlayedAudioSpeed(video, 1, new int[]{30, 60, 120}[index++ % 3]);
        } finally { java.nio.file.Files.deleteIfExists(video); }
      }
    }
  }

  private static void assertPlayedAudioSpeed(final java.nio.file.Path video, final int seconds, final int renderHz) throws Exception {
    final long device = alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer)null);
    assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final Probe audio = new Probe();
    try(final var movie = new StreamingMovie(video)) {
      audio.initialize();
      final var playback = new MoviePlayback(movie);
      final int samplesPerFrame = 48_000 / renderHz;
      final float[] output = new float[samplesPerFrame * 2];
      long renderedMicros = 0;
      long lastImage = 0;
      for(int tick = 0; tick < seconds * renderHz; tick++) {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while((movie.bufferedImages() == 0 || movie.bufferedAudio() < 16) && !movie.audioDrained() && System.nanoTime() < deadline) Thread.sleep(1);
        final long clock = playback.tick(audio, 1.0f);
        assertEquals(renderedMicros, clock, 1000, "Movie clock must follow samples played, not queued or render ticks");
        final var image = movie.pollVideo(clock);
        if(image != null) {
          lastImage = image.timestamp();
          assertTrue(lastImage <= renderedMicros + 15_000, "Movie must not display a future frame");
        }
        audio.tick();
        alcRenderSamplesSOFT(device, output, samplesPerFrame);
        renderedMicros = (tick + 1L) * samplesPerFrame * 1_000_000L / 48_000;
        audio.process();
      }
      assertTrue(lastImage >= seconds * 1_000_000L - 100_000, "Video frames must track played audio at " + renderHz + " Hz");
      assertFalse(movie.drained(), "A longer movie cannot finish before the played duration");
    } finally { audio.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
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
      final long started = System.nanoTime();
      final long timeout = System.nanoTime() + 6_000_000_000L;
      long clock = 0;
      while((!movie.drained() || audio.hasQueuedOutput() || clock < movie.durationMicros) && System.nanoTime() < timeout) {
        clock = playback.tick(audio, 1.0f);
        final long elapsed = (System.nanoTime() - started) / 1000;
        assertTrue(clock <= elapsed + 100_000, "Movie clock accelerated: elapsed=" + elapsed + ", clock=" + clock + ", source=" + audio.getPlaybackPositionSeconds() + ", queued=" + audio.hasQueuedOutput() + ", audioDrained=" + movie.audioDrained());
        movie.pollVideo(clock);
        audio.tick(); audio.process();
        Thread.sleep(5);
      }
      assertTrue(movie.drained(), "Independent audio EOF must release tail: clock=" + clock + ", images=" + movie.bufferedImages() + ", audio=" + movie.bufferedAudio() + ", audioEOF=" + movie.audioDrained());
      assertFalse(audio.hasQueuedOutput());
      assertTrue(clock >= movie.durationMicros);
      final long elapsedMicros = (System.nanoTime() - started) / 1000;
      assertTrue(elapsedMicros >= movie.durationMicros * 0.95,
        "The streamed movie must take at least 95 percent of its media duration to play: elapsed=" + elapsedMicros + ", duration=" + movie.durationMicros + ", clock=" + clock);
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
