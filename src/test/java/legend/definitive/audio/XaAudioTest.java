package legend.definitive.audio;

import legend.core.audio.opus.XaPlayer;
import legend.core.audio.AudioSource;
import legend.core.audio.GenericSource;
import legend.core.audio.xa.*;
import legend.game.unpacker.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import org.lwjgl.util.opus.Opus;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

final class XaAudioTest {
  @TempDir Path temporary;

  private static FileData wave(final int frames, final int channels) {
    final var pcm = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN);
    for(int i = 0; i < frames * channels; i++) pcm.putShort((short)(i * 7 - 100));
    return new FileData(XaPcm.encode(pcm.array(), channels));
  }

  private static FileData opus(final int channels) {
    final long encoder = Opus.opus_encoder_create(48000, channels, Opus.OPUS_APPLICATION_AUDIO, null);
    try {
      final var input = BufferUtils.createShortBuffer(960 * channels);
      final var output = BufferUtils.createByteBuffer(4000);
      final int count = Opus.opus_encode(encoder, input, 960, output);
      assertTrue(count > 0);
      final byte[] packet = new byte[count]; output.get(packet);
      final var file = new legend.core.audio.opus.OpusFile((byte)channels, (short)312, 37800);
      file.addOpusSegment(packet);
      return new FileData(file.toBytes());
    } finally { Opus.opus_encoder_destroy(encoder); }
  }

  @Test void losslessFramesRemainExactAndSeekableForMonoAndStereo() {
    for(final int channels : new int[]{1, 2}) {
      try(final var decoder = XaDecoder.open(wave(497, channels))) {
        final short[] output = new short[480 * channels]; Arrays.fill(output, (short)12345);
        assertEquals(480 * channels, decoder.read(output));
        assertEquals(17 * channels, decoder.read(output));
        for(int i = 0; i < 17 * channels; i++) assertEquals((short)((480 * channels + i) * 7 - 100), output[i]);
        assertEquals(0, decoder.read(output));
        decoder.seek(480); assertEquals(17 * channels, decoder.read(output));
      }
    }
  }

  @Test void legacyOpusUsesReturnedCountsAndReleasesHandles() {
    for(final int channels : new int[]{1, 2}) {
      final var decoder = XaDecoder.open(opus(channels));
      final short[] output = new short[480 * channels];
      int total = 0, count;
      while((count = decoder.read(output)) > 0) total += count;
      assertEquals((960 - 312) * channels, total);
      decoder.seek(480); assertEquals(168 * channels, decoder.read(output));
      decoder.close(); decoder.close(); assertEquals(0, decoder.read(output));
    }
    assertThrows(IllegalArgumentException.class, () -> XaDecoder.open(new FileData(new byte[40])));
  }

  @Test void partialOrInvalidWaveCannotBeAcceptedAsACompleteImport() throws Exception {
    final var bytes = wave(17, 2).getBytes();
    final Path file = this.temporary.resolve("1.wav");
    Files.write(file, bytes); assertTrue(XaPcm.isComplete(file));
    Files.write(file, Arrays.copyOf(bytes, bytes.length - 1)); assertFalse(XaPcm.isComplete(file));
    assertThrows(IllegalArgumentException.class, () -> XaDecoder.open(new FileData(Arrays.copyOf(bytes, bytes.length - 1))));
    bytes[24] = 0; assertThrows(IllegalArgumentException.class, () -> XaDecoder.open(new FileData(bytes)));
    assertThrows(IllegalArgumentException.class, () -> XaPcm.encode(new byte[3], 2));
  }

  @Test void selectiveUpgradeRequiresOnlyMissingOrIncompleteXaTracks() throws Exception {
    final Path directory = Files.createDirectory(this.temporary.resolve("LODXA03.XA"));
    Files.write(directory.resolve("1.opus"), opus(2).getBytes());
    assertTrue(XaTranscoder.needsConversion(directory));
    Files.write(directory.resolve("1.wav"), wave(17, 2).getBytes());
    assertFalse(XaTranscoder.needsConversion(directory));
    assertTrue(Files.exists(directory.resolve("1.opus")), "Old engine rollback retains its input");
    Files.write(directory.resolve("1.wav"), new byte[2]); assertTrue(XaTranscoder.needsConversion(directory));
    assertFalse(XaTranscoder.needsConversion(this.temporary.resolve("OTHER.BIN")));
  }

  @Test void syntheticXaImportsHaveExactUnpaddedMonoAndStereoFrameCounts() {
    for(final int archive : new int[]{0, 3}) {
      final int channels = archive == 3 ? 2 : 1, interleave = archive == 3 ? 4 : 16;
      final byte[] sectors = new byte[interleave * 0x930];
      final PathNode root = new PathNode("", "", null, null);
      final var queue = new LinkedList<PathNode>();
      XaTranscoder.transform(new PathNode("XA/LODXA0" + archive + ".XA", "", new FileData(sectors), root), new Transformations(root, queue));
      final PathNode imported = queue.stream().filter(node -> node.fullPath.endsWith("/1.wav")).findFirst().orElseThrow();
      assertEquals(channels, XaPcm.channels(imported.data));
      final int expectedFrames = (18 * 112 * (3 - channels) * 80 - 160 + 62) / 63;
      assertEquals(44 + expectedFrames * channels * 2, imported.data.size());
      assertTrue(queue.stream().noneMatch(node -> node.fullPath.endsWith(".opus")));
    }
  }

  private static final class Probe {
    private final XaPlayer player = new XaPlayer(1.0f);
    private void invoke(final String name, final Class<?>[] types, final Object... args) {
      try { final var method = AudioSource.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(this.player, args); }
      catch(final ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    void initialize() { this.invoke("init", new Class<?>[0]); }
    void release() { this.invoke("destroy", new Class<?>[0]); this.player.unloadOpusFile(); }
    void recreate() { this.invoke("destroy", new Class<?>[0]); this.initialize(); this.invoke("setActive", new Class<?>[]{boolean.class}, true); }
    int sourceId() {
      try { final var field = AudioSource.class.getDeclaredField("sourceId"); field.setAccessible(true); return field.getInt(this.player); }
      catch(final ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    void rewindQueuedOutput() { alSourceRewind(this.sourceId()); }
    void loadXa(final FileData data) { this.player.loadXa(data); }
    void tick() { this.player.tick(); }
    void stop() { this.player.stop(); }
    boolean isActive() { return this.player.isActive(); }
    boolean hasQueuedOutput() { return this.player.hasQueuedOutput(); }
    int availableBuffers() { return this.player.availableBuffers(); }
    float getPlaybackPosition() { return this.player.getPlaybackPosition(); }
  }

  private static void withAudio(final java.util.function.Consumer<Probe> test) {
    final long device = alcOpenDevice((ByteBuffer)null); assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, (IntBuffer)null); assertNotEquals(0, context);
    alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final var player = new Probe();
    try { player.initialize(); test.accept(player); }
    finally { player.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

  private static void drain(final Probe player) {
    final long deadline = System.nanoTime() + 2_000_000_000L;
    while(player.isActive() && System.nanoTime() < deadline) {
      player.tick();
      try { Thread.sleep(1); } catch(final InterruptedException e) { throw new AssertionError(e); }
    }
    assertFalse(player.isActive()); assertFalse(player.hasQueuedOutput()); assertEquals(8, player.availableBuffers());
  }

  @Test void shortFinalBufferDrainsWithoutReplayAndReplacementFlushesOldAudio() {
    withAudio(player -> {
      player.loadXa(wave(4800, 1));
      player.loadXa(wave(17, 2));
      assertTrue(player.isActive(), "EOF does not stop queued tail");
      drain(player);
      assertEquals(17 / 48000.0, player.getPlaybackPosition(), 0.000001);
      player.loadXa(opus(1)); drain(player);
      assertEquals(648 / 48000.0, player.getPlaybackPosition(), 0.000001);
      player.loadXa(wave(4800, 1)); player.stop(); assertFalse(player.hasQueuedOutput());
      player.loadXa(new FileData(new byte[40])); assertFalse(player.isActive()); assertFalse(player.hasQueuedOutput());
    });
  }

  @Test void sourceRecreationPreservesAnUnplayedFinalTail() {
    withAudio(player -> {
      player.loadXa(wave(17, 2)); player.rewindQueuedOutput();
      player.recreate(); drain(player);
      assertEquals(17 / 48000.0, player.getPlaybackPosition(), 0.000001);
    });
  }
  @Test void recoveryDoesNotReplayProcessedBuffersAfterNaturalUnderflow() {
    withAudio(player -> {
      player.loadXa(wave(4800, 1));
      final long deadline = System.nanoTime() + 2_000_000_000L;
      while(alGetSourcei(player.sourceId(), AL_SOURCE_STATE) != AL_STOPPED && System.nanoTime() < deadline) {
        try { Thread.sleep(2); } catch(final InterruptedException e) { throw new AssertionError(e); }
      }
      assertEquals(AL_STOPPED, alGetSourcei(player.sourceId(), AL_SOURCE_STATE));
      assertEquals(4, alGetSourcei(player.sourceId(), AL_BUFFERS_PROCESSED));
      player.recreate(); drain(player);
      assertEquals((4800 - 4 * 480) / 48000.0, player.getPlaybackPosition(), 0.000001);
    });
  }

  private static final class ClockProbe extends GenericSource {
    ClockProbe() { super(AL_FORMAT_MONO16, 48000); }
    void initialize() { super.init(); }
    void process() { super.handleProcessedBuffers(); }
    void release() { super.destroy(); }
  }

  @Test void longPlaybackClockCountsActualLoopbackFramesWithoutFloatAccumulationDrift() {
    final long device = alcLoopbackOpenDeviceSOFT((ByteBuffer)null); assertNotEquals(0, device);
    final var caps = ALC.createCapabilities(device);
    final long context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
    assertNotEquals(0, context); alcMakeContextCurrent(context); AL.createCapabilities(caps);
    final var clock = new ClockProbe();
    try {
      clock.initialize();
      final short[] input = new short[480]; final float[] output = new float[960];
      for(int tick = 0; tick < 35300; tick++) {
        clock.bufferOutput(input); clock.tick(); alcRenderSamplesSOFT(device, output, 480); clock.process();
      }
      assertEquals(353.0, clock.getPlaybackPositionSeconds(), 0.0000001);
      assertEquals(353L * 48000, Math.round(clock.getPlaybackPositionSeconds() * 48000));
    } finally { clock.release(); assertEquals(AL_NO_ERROR, alGetError()); alcMakeContextCurrent(0); alcDestroyContext(context); alcCloseDevice(device); }
  }

}
