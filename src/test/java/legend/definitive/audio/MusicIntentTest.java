package legend.definitive.audio;

import legend.core.audio.AudioThread;
import legend.core.audio.AudioSource;
import legend.core.audio.EffectsOverTimeGranularity;
import legend.core.audio.InterpolationPrecision;
import legend.core.audio.PitchResolution;
import legend.core.audio.opus.XaPlayer;
import legend.core.audio.sequencer.Sequencer;
import legend.core.audio.sequencer.assets.BackgroundMusic;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Actual owner and sequencer commands with no native output, using synthetic music data. */
final class MusicIntentTest {
  private static Field field(final Class<?> type, final String name) throws Exception {
    final Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static BackgroundMusic music(final int id) {
    final var identity = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)id);
    final var sequence = ByteBuffer.allocate(0x113).order(ByteOrder.LITTLE_ENDIAN);
    sequence.put(0, (byte)0x80).putShort(2, (short)48).putShort(4, (short)120);
    for(int channel = 0; channel < 16; channel++) {
      sequence.put(16 + channel * 16 + 1, (byte)channel);
      sequence.put(16 + channel * 16 + 2, (byte)-1);
    }
    sequence.put(0x110, (byte)0xff).put(0x111, (byte)0x2f).put(0x112, (byte)0);
    final var sound = ByteBuffer.allocate(0xa6).order(ByteOrder.LITTLE_ENDIAN);
    sound.putInt(16, 0x20).putInt(20, 0x24).putInt(24, -1);
    sound.putShort(0x22, (short)-1); // One absent instrument; no sample bank or game assets.
    return new BackgroundMusic(List.of(new FileData(identity.array()), new FileData(sequence.array()), new FileData(sound.array()), new FileData(new byte[0])), 0);
  }

  private static final class Fixture implements AutoCloseable {
    private final AudioThread audio = new AudioThread(true, 24, InterpolationPrecision.Double, PitchResolution.Quadruple, EffectsOverTimeGranularity.Double);
    private final Sequencer sequencer = new Sequencer(true, 24, InterpolationPrecision.Double, PitchResolution.Quadruple, EffectsOverTimeGranularity.Double);

    Fixture() throws Exception {
      field(AudioThread.class, "sequencer").set(this.audio, this.sequencer);
      field(AudioThread.class, "xaPlayer").set(this.audio, new XaPlayer(1.0f));
      this.audio.addSource(this.sequencer);
      assertFalse(this.sequencer.outputAvailable());
    }

    @Override public void close() { this.audio.destroy(); }
  }

  @Test void loadAndStartBeforeFirstOutputRetainSongAndPlaybackIntent() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.audio.loadBackgroundMusic(music(11));
      fixture.audio.startSequence();
      assertEquals(11, fixture.audio.getSongId());
      assertTrue(fixture.audio.isMusicPlaying());
      assertFalse(fixture.sequencer.outputAvailable());
    }
  }

  @Test void outageReplacementKeepsLatestSongAndStopRequest() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.sequencer.loadBackgroundMusic(music(11));
      fixture.sequencer.startSequence();
      fixture.audio.loadBackgroundMusic(music(12));
      fixture.audio.loadBackgroundMusic(music(13));
      fixture.audio.stopSequence();
      assertEquals(13, fixture.audio.getSongId());
      assertFalse(fixture.audio.isMusicPlaying());
      assertTrue(field(Sequencer.class, "paused").getBoolean(fixture.sequencer));
    }
  }

  @Test void outageStopThenStartUsesLatestRequest() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.sequencer.loadBackgroundMusic(music(11));
      fixture.audio.stopSequence();
      fixture.audio.startSequence();
      assertTrue(fixture.audio.isMusicPlaying());
      fixture.audio.stopSequence();
      assertFalse(fixture.audio.isMusicPlaying());
    }
  }

  @Test void outageUnloadCancelsRetainedMusic() throws Exception {
    try(final Fixture fixture = new Fixture()) {
      fixture.audio.loadBackgroundMusic(music(11));
      fixture.audio.startSequence();
      assertEquals(11, fixture.audio.getSongId());
      fixture.audio.unloadMusic();
      assertEquals(-1, fixture.audio.getSongId());
      assertFalse(fixture.audio.isMusicPlaying());
      assertTrue(field(Sequencer.class, "paused").getBoolean(fixture.sequencer));
    }
  }

  private static void nativeLifecycle(final Sequencer sequencer, final String method) throws Exception {
    final var lifecycle = AudioSource.class.getDeclaredMethod(method);
    lifecycle.setAccessible(true);
    lifecycle.invoke(sequencer);
  }

  @Test void latestCommandsSurviveNativeSourceRecreationWithoutAudibleOutput() throws Exception {
    long device = 0;
    long context = 0;
    try {
      device = alcLoopbackOpenDeviceSOFT((ByteBuffer)null);
      assertNotEquals(0, device);
      final var capabilities = ALC.createCapabilities(device);
      context = alcCreateContext(device, new int[]{ALC_FREQUENCY, 48000, ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT, 0});
      assertNotEquals(0, context);
      alcMakeContextCurrent(context);
      AL.createCapabilities(capabilities);
      try(final Fixture fixture = new Fixture()) {
        try {
          nativeLifecycle(fixture.sequencer, "init");
          fixture.audio.loadBackgroundMusic(music(11));
          fixture.audio.startSequence();
          nativeLifecycle(fixture.sequencer, "destroy");

          fixture.audio.loadBackgroundMusic(music(12));
          fixture.audio.startSequence();
          nativeLifecycle(fixture.sequencer, "init");
          assertTrue(fixture.sequencer.outputAvailable());
          assertEquals(12, fixture.audio.getSongId());
          assertTrue(fixture.audio.isMusicPlaying());
          nativeLifecycle(fixture.sequencer, "destroy");

          fixture.audio.loadBackgroundMusic(music(13));
          fixture.audio.stopSequence();
          nativeLifecycle(fixture.sequencer, "init");
          assertEquals(13, fixture.audio.getSongId());
          assertFalse(fixture.audio.isMusicPlaying());
          nativeLifecycle(fixture.sequencer, "destroy");

          fixture.audio.unloadMusic();
          nativeLifecycle(fixture.sequencer, "init");
          assertEquals(-1, fixture.audio.getSongId());
          assertFalse(fixture.audio.isMusicPlaying());
        } finally {
          if(fixture.sequencer.outputAvailable()) nativeLifecycle(fixture.sequencer, "destroy");
        }
        assertEquals(AL_NO_ERROR, alGetError());
      }
    } finally {
      if(context != 0) {
        alcMakeContextCurrent(0);
        alcDestroyContext(context);
      }
      if(device != 0) alcCloseDevice(device);
    }
  }
}
