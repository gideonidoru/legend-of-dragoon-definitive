package legend.definitive.audio;

import legend.core.audio.*;
import legend.core.audio.opus.XaPlayer;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Actual owner shutdown and native retirement with inaudible loopback output; no game or original assets. */
final class AudioCleanupFailureTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"workerError", "workerRuntime", "workerMultiple", "directError", "directRuntime", "directMultiple", "workerHealthy", "directHealthy"})
  void actualOwnerCleanup(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      AudioCleanupFailureTest.class.getName(), scenario).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Cleanup fixture must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Cleanup child must retire");
    }
  }

  private static Field field(final Class<?> owner, final String name) throws Exception {
    final Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static Field field(final String name) throws Exception { return field(AudioThread.class, name); }

  private static final class FaultSource extends AudioSource {
    private final Throwable failure;
    private boolean armed;
    FaultSource(final Throwable failure) { super(0); this.failure = failure; this.armed = failure != null; }
    @Override protected boolean isInitialized() { return this.armed; }
    @Override protected void destroy() {
      this.armed = false;
      if(this.failure instanceof Error error) throw error;
      if(this.failure instanceof RuntimeException exception) throw exception;
    }
  }

  private static final class NativeSource extends GenericSource {
    private final long context;
    private boolean retired;
    NativeSource(final long context) { super(AL_FORMAT_STEREO16, 48000); this.context = context; }
    void initialize() { super.init(); }
    @Override protected void destroy() {
      // Bind only this fixture's loopback context for source deletion on the actual worker.
      alcMakeContextCurrent(this.context);
      try { super.destroy(); assertEquals(AL_NO_ERROR, alGetError()); this.retired = true; }
      finally { alcMakeContextCurrent(0); }
    }
  }

  private static final class Decoder implements XaDecoder {
    private boolean closed;
    @Override public int channels() { return 2; }
    @Override public int read(final short[] output) { throw new AssertionError("Shutdown must not decode"); }
    @Override public void seek(final long frame) { throw new AssertionError("Shutdown must not seek"); }
    @Override public void close() { this.closed = true; }
  }

  @SuppressWarnings("unchecked")
  public static void main(final String[] args) throws Exception {
    final String scenario = args[0];
    final boolean direct = scenario.startsWith("direct"), healthy = scenario.endsWith("Healthy"), multiple = scenario.endsWith("Multiple");
    final Throwable failure = healthy ? null : scenario.endsWith("Runtime") ? new IllegalStateException("controlled source cleanup failure") : new AssertionError("controlled source cleanup error");
    final Throwable second = multiple ? new IllegalArgumentException("second cleanup failure") : null;
    final FaultSource firstFault = new FaultSource(failure), secondFault = new FaultSource(second);
    final Decoder decoder = new Decoder();
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
      field("audioDevice").setLong(audio, device);
      field("audioContext").setLong(audio, context);
      field("tmp").set(audio, MemoryUtil.memAllocInt(1));
      final XaPlayer player = new XaPlayer(1.0f);
      field(XaPlayer.class, "decoder").set(player, decoder);
      field("xaPlayer").set(audio, player);
      field("paused").setBoolean(audio, true);
      final var sources = (List<AudioSource>)field("sources").get(audio);
      sources.add(firstFault); sources.add(nativeSource); sources.add(secondFault);
      if(!direct) {
        worker = new Thread(audio, "audio-cleanup-worker");
        worker.setDaemon(true);
        worker.setUncaughtExceptionHandler((thread, thrown) -> workerFailure.set(thrown));
        worker.start();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while(worker.isAlive() && worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.WAITING, worker.getState(), "Reach the real unavailable-output wait");
      }
      final AudioThread owner = audio;
      closer = new Thread(() -> { try { owner.destroy(); } catch(final Throwable thrown) { closeFailure.set(thrown); } }, "audio-cleanup-caller");
      closer.setDaemon(true);
      closer.start();
      closer.join(1_000);
      if(worker != null) worker.join(1_000);
      assertFalse(closer.isAlive(), "A cleanup failure must not leave the close caller waiting");
      assertTrue(nativeSource.retired, "A failed earlier source must not skip later native source retirement");
      assertEquals(0, field("audioDevice").getLong(audio));
      assertEquals(0, field("audioContext").getLong(audio));
      assertTrue(decoder.closed, "Terminal cleanup must release the retained XA decoder");
      assertSame(failure, direct ? closeFailure.get() : workerFailure.get(), "Preserve the first cleanup failure");
      assertNull(direct ? workerFailure.get() : closeFailure.get());
      if(multiple) assertArrayEquals(new Throwable[] {second}, failure.getSuppressed(), "Retain subsequent cleanup failures in order");
      if(!healthy) assertNull(field("tmp").get(audio), "Clear the retired native buffer reference");
      audio.destroy();
      System.out.println("PASS " + scenario);
    } finally {
      if(audio != null) {
        // Allow unchanged-source negative controls to retire their remaining real native resources.
        firstFault.armed = false; secondFault.armed = false;
        audio.stop(); audio.destroy();
        if(worker != null) worker.join(2_000);
        if(closer != null) closer.join(2_000);
        assertTrue(worker == null || !worker.isAlive(), "Fixture must not leak an audio worker");
        assertTrue(closer == null || !closer.isAlive(), "Fixture must not leak a close caller");
        if(field("audioDevice").getLong(audio) == 0) { device = 0; context = 0; }
      }
      if(context != 0) { alcMakeContextCurrent(0); alcDestroyContext(context); }
      if(device != 0) assertTrue(alcCloseDevice(device));
    }
  }
}
