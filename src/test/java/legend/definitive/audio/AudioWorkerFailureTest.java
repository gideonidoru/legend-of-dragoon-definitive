package legend.definitive.audio;

import legend.core.audio.*;
import legend.core.audio.opus.XaPlayer;
import legend.core.audio.sequencer.Sequencer;
import legend.core.audio.xa.XaDecoder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Actual processing-loop failures and native retirement with inaudible loopback output. */
final class AudioWorkerFailureTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"canBufferError", "canBufferRuntime", "tickError", "tickRuntime", "processedError", "processedRuntime", "tickCleanupErrors", "processedCleanupRuntime", "tickErrorCloseRace", "healthyStop", "healthyDestroy"})
  void actualWorkerFailure(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      AudioWorkerFailureTest.class.getName(), scenario).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Processing fixture must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Processing child must retire");
    }
  }

  private static Field field(final Class<?> owner, final String name) throws Exception {
    final Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
  private static Field field(final String name) throws Exception { return field(AudioThread.class, name); }

  private static void rethrow(final Throwable failure) {
    if(failure instanceof Error error) throw error;
    if(failure instanceof RuntimeException exception) throw exception;
  }

  private static final class ProcessingSource extends AudioSource {
    private final String scenario;
    private final Throwable failure, cleanupFailure;
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private AudioThread owner;
    private boolean retired, cleanupArmed;
    ProcessingSource(final String scenario, final Throwable failure, final Throwable cleanupFailure) {
      super(0); this.scenario = scenario; this.failure = failure; this.cleanupFailure = cleanupFailure; this.cleanupArmed = cleanupFailure != null;
    }
    private void failAt(final String stage) {
      if(this.scenario.startsWith(stage)) {
        this.entered.countDown();
        if(this.scenario.endsWith("CloseRace")) {
          try { assertTrue(this.release.await(3, TimeUnit.SECONDS), "Release the injected error after close contends for the owner"); }
          catch(final InterruptedException failure) { throw new AssertionError(failure); }
        }
        rethrow(this.failure);
      }
    }
    @Override public boolean canBuffer() { this.failAt("canBuffer"); return true; }
    @Override public void tick() {
      this.failAt("tick");
      if(this.scenario.equals("healthyStop")) this.owner.stop();
    }
    @Override protected void handleProcessedBuffers() { this.entered.countDown(); this.failAt("processed"); }
    @Override protected boolean isInitialized() { return !this.retired; }
    @Override protected void destroy() {
      this.retired = true;
      if(this.cleanupArmed) { this.cleanupArmed = false; rethrow(this.cleanupFailure); }
    }
  }

  private static final class CleanupSource extends AudioSource {
    private final Throwable failure;
    private boolean armed;
    CleanupSource(final Throwable failure) { super(0); this.failure = failure; this.armed = failure != null; }
    @Override protected boolean isInitialized() { return this.armed; }
    @Override protected void destroy() { this.armed = false; rethrow(this.failure); }
  }

  private static final class NativeSource extends GenericSource {
    private final long context;
    private boolean retired;
    NativeSource(final long context) { super(AL_FORMAT_STEREO16, 48000); this.context = context; }
    void initialize() { super.init(); }
    @Override protected void destroy() {
      alcMakeContextCurrent(this.context);
      try { super.destroy(); assertEquals(AL_NO_ERROR, alGetError()); this.retired = true; }
      finally { alcMakeContextCurrent(0); }
    }
  }

  private static final class Decoder implements XaDecoder {
    private final Throwable failure;
    private boolean closed, failureThrown;
    Decoder(final Throwable failure) { this.failure = failure; }
    @Override public int channels() { return 2; }
    @Override public int read(final short[] output) { throw new AssertionError("Fixture must not decode"); }
    @Override public void seek(final long frame) { throw new AssertionError("Fixture must not seek"); }
    @Override public void close() {
      this.closed = true;
      if(!this.failureThrown) { this.failureThrown = true; rethrow(this.failure); }
    }
  }

  @SuppressWarnings("unchecked")
  public static void main(final String[] args) throws Exception {
    final String scenario = args[0];
    final boolean healthy = scenario.startsWith("healthy"), combined = scenario.contains("Cleanup"), race = scenario.endsWith("CloseRace");
    final Throwable failure = healthy ? null : scenario.endsWith("Runtime") ? new IllegalStateException("controlled processing failure") : new AssertionError("controlled processing error");
    final Throwable cleanupFailure = combined ? new IllegalArgumentException("first cleanup failure") : null;
    final Throwable secondCleanup = combined ? new AssertionError("second cleanup error") : null;
    final Throwable decoderFailure = combined ? new IllegalStateException("decoder cleanup failure") : null;
    final ProcessingSource source = new ProcessingSource(scenario, failure, cleanupFailure);
    final CleanupSource lastSource = new CleanupSource(secondCleanup);
    final Decoder decoder = new Decoder(decoderFailure);
    final AtomicReference<Throwable> workerFailure = new AtomicReference<>(), closeFailure = new AtomicReference<>();
    long device = 0, context = 0;
    AudioThread audio = null;
    Thread worker = null, closer = null;
    try {
      device = alcLoopbackOpenDeviceSOFT((ByteBuffer)null);
      assertNotEquals(0, device, "Inaudible loopback device must be available");
      final var capabilities = ALC.createCapabilities(device);
      context = alcCreateContext(device, new int[] {ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
      assertNotEquals(0, context);
      alcMakeContextCurrent(context);
      AL.createCapabilities(capabilities);
      final NativeSource nativeSource = new NativeSource(context);
      nativeSource.initialize();
      alcMakeContextCurrent(0);
      audio = new AudioThread(true, 24, InterpolationPrecision.Double, PitchResolution.Quadruple, EffectsOverTimeGranularity.Double);
      source.owner = audio;
      field("audioDevice").setLong(audio, device);
      field("audioContext").setLong(audio, context);
      field("alcCapabilities").set(audio, capabilities);
      field("tmp").set(audio, MemoryUtil.memAllocInt(1));
      final XaPlayer player = new XaPlayer(1.0f);
      field(XaPlayer.class, "decoder").set(player, decoder);
      field("xaPlayer").set(audio, player);
      field("sequencer").set(audio, new Sequencer(true, 24, InterpolationPrecision.Double, PitchResolution.Quadruple, EffectsOverTimeGranularity.Double));
      final var sources = (List<AudioSource>)field("sources").get(audio);
      sources.add(source); sources.add(nativeSource); sources.add(lastSource);
      worker = new Thread(audio, "audio-processing-worker");
      worker.setDaemon(true);
      worker.setUncaughtExceptionHandler((thread, thrown) -> workerFailure.set(thrown));
      worker.start();
      if(race || scenario.equals("healthyDestroy")) assertTrue(source.entered.await(2, TimeUnit.SECONDS), "Reach actual processing before close");
      else worker.join(2_000);
      final AudioThread owner = audio;
      closer = new Thread(() -> { try { owner.destroy(); } catch(final Throwable thrown) { closeFailure.set(thrown); } }, "audio-processing-caller");
      closer.setDaemon(true); closer.start();
      if(race) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while(closer.isAlive() && closer.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.BLOCKED, closer.getState(), "Close must contend for the actual processing monitor");
        source.release.countDown();
      }
      closer.join(1_000); worker.join(1_000);
      System.out.println("callerAlive=" + closer.isAlive() + " workerAlive=" + worker.isAlive() + " laterNativeSourceRetired=" + nativeSource.retired + " deviceRetired=" + (field("audioDevice").getLong(audio) == 0) + " originalWorkerFailure=" + (workerFailure.get() == failure));
      assertFalse(closer.isAlive(), "Processing failure must not leave the close caller waiting");
      assertFalse(worker.isAlive(), "Processing worker must retire");
      assertTrue(source.retired && nativeSource.retired, "Processing failure must retire later native sources");
      assertEquals(0, field("audioDevice").getLong(audio));
      assertEquals(0, field("audioContext").getLong(audio));
      assertNull(field("tmp").get(audio));
      assertTrue(decoder.closed, "Terminal cleanup must attempt retained decoder release");
      assertSame(failure, workerFailure.get(), "The processing failure must remain the worker's primary error");
      assertNull(closeFailure.get(), "A later close must not manufacture a caller failure");
      if(combined) {
        assertArrayEquals(new Throwable[] {cleanupFailure}, failure.getSuppressed());
        assertArrayEquals(new Throwable[] {secondCleanup, decoderFailure}, cleanupFailure.getSuppressed(), "Preserve cleanup suppression order under the primary processing failure");
      }
      assertFalse(field("running").getBoolean(audio));
      assertTrue(field("shutdownRequested").getBoolean(audio), "Worker failure is terminal, not permission to restart a retired owner");
      audio.run(); audio.destroy();
      System.out.println("PASS " + scenario);
    } finally {
      source.release.countDown(); source.cleanupArmed = false; lastSource.armed = false; decoder.failureThrown = true;
      if(audio != null) {
        audio.stop(); audio.destroy();
        if(worker != null) worker.join(2_000);
        if(closer != null) closer.join(2_000);
        assertTrue(worker == null || !worker.isAlive(), "Fixture must not leak a worker");
        assertTrue(closer == null || !closer.isAlive(), "Fixture must not leak a close caller");
        if(field("audioDevice").getLong(audio) == 0) { device = 0; context = 0; }
      }
      if(context != 0) { alcMakeContextCurrent(0); alcDestroyContext(context); }
      if(device != 0) assertTrue(alcCloseDevice(device));
    }
  }
}
