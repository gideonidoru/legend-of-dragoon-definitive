package legend.core.audio.opus;

import legend.core.audio.AudioSource;
import legend.core.audio.xa.XaDecoder;
import legend.core.audio.xa.XaPcm;
import legend.game.modding.coremod.CoreMod;
import legend.game.unpacker.FileData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Arrays;

import static legend.core.GameEngine.CONFIG;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;

/** Exact-count PCM/legacy Opus playback with final-buffer drain and clean track replacement. */
public final class XaPlayer extends AudioSource {
  private static final Logger LOGGER = LogManager.getFormatterLogger(XaPlayer.class);
  private XaDecoder decoder;
  private int format;
  private short[] pcm;
  private float playerVolume;
  private boolean eof;
  private long resumeFrame;
  private long bufferedFrames;

  public XaPlayer() {
    this(CONFIG.getConfig(CoreMod.SFX_VOLUME_CONFIG.get()) * CONFIG.getConfig(CoreMod.MASTER_VOLUME_CONFIG.get()));
  }

  public XaPlayer(final float volume) { super(8); this.playerVolume = volume; }

  public synchronized void setPlayerVolume(final float volume) { this.playerVolume = volume; }

  public synchronized void loadXa(final FileData fileData) {
    this.stop();
    try { this.decoder = XaDecoder.open(fileData); }
    catch(final RuntimeException failure) { LOGGER.error("Cannot open XA recording", failure); return; }
    this.format = this.decoder.channels() == 2 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16;
    this.pcm = new short[480 * this.decoder.channels()];
    this.setActive(true);
    for(int i = 0; i < 4 && !this.eof && this.decoder != null && this.canBuffer(); i++) this.bufferNext();
    this.finishIfDrained();
    super.tick();
  }

  @Override public synchronized void tick() {
    // Remove exhausted buffers before restarting an underflowed source.
    this.handleProcessedBuffers();
    if(this.decoder != null && !this.eof && this.canBuffer()) this.bufferNext();
    this.finishIfDrained();
    super.tick();
  }

  private void bufferNext() {
    final int count;
    try { count = this.decoder.read(this.pcm); }
    catch(final RuntimeException failure) {
      LOGGER.error("Stopping invalid XA recording", failure);
      this.unloadOpusFile();
      this.eof = true;
      return;
    }
    if(count == 0) { this.eof = true; return; }
    this.bufferedFrames += count / this.decoder.channels();
    for(int i = 0; i < count; i++) this.pcm[i] = (short)Math.clamp(Math.round(this.pcm[i] * this.playerVolume), Short.MIN_VALUE, Short.MAX_VALUE);
    // OpenAL copies the samples immediately; a short final read never queues stale data.
    this.bufferOutput(this.format, count == this.pcm.length ? this.pcm : Arrays.copyOf(this.pcm, count), XaPcm.SAMPLE_RATE);
  }

  private void finishIfDrained() {
    if((this.eof || this.decoder == null) && !this.hasQueuedOutput()) {
      this.unloadOpusFile();
      this.setActive(false);
    }
  }

  @Override public synchronized void stop() {
    this.flushOutput();
    this.unloadOpusFile();
    this.eof = false;
    this.resumeFrame = this.bufferedFrames = 0;
  }

  /** Retained API name for older callers; releases either supported decoder. */
  public synchronized void unloadOpusFile() {
    if(this.decoder != null) { this.decoder.close(); this.decoder = null; }
  }

  @Override protected synchronized void destroy() {
    // Rewind unplayed queued audio before the old device loses it. The next device resumes
    // at the played frame, including a tail whose decoder has already reached EOF.
    if(this.decoder != null) {
      // A naturally stopped queue has zero offset; account for its played buffers first.
      this.handleProcessedBuffers();
      this.resumeFrame = Math.min(this.bufferedFrames, this.resumeFrame + Math.round(this.getPlaybackPositionSeconds() * XaPcm.SAMPLE_RATE));
      try { this.decoder.seek(this.resumeFrame); this.bufferedFrames = this.resumeFrame; this.eof = false; }
      catch(final RuntimeException failure) { LOGGER.error("Cannot resume XA recording", failure); this.unloadOpusFile(); }
    }
    super.destroy();
  }
}
