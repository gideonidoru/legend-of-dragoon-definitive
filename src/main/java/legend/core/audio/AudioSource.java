package legend.core.audio;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED;
import static org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED;
import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_SIZE;
import static org.lwjgl.openal.AL10.AL_BITS;
import static org.lwjgl.openal.AL10.AL_CHANNELS;
import static org.lwjgl.openal.AL10.AL_FREQUENCY;
import static org.lwjgl.openal.AL10.alGetBufferi;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_STOPPED;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetSourcef;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourcePause;
import static org.lwjgl.openal.AL10.alSourceRewind;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL10.alSourceStop;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.AL10.alSourceUnqueueBuffers;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;
import static org.lwjgl.system.MemoryUtil.memFree;

public abstract class AudioSource {
  private final int[] buffers;
  private final int[] allocatedBuffers;
  private int bufferIndex;
  private int sourceId;

  private boolean active;
  private boolean playbackPaused;

  private IntBuffer tmp;

  private double playTime;
  private int generation;

  public AudioSource(final int bufferCount) {
    this.buffers = new int[bufferCount];
    this.allocatedBuffers = new int[bufferCount];
  }

  protected boolean isInitialized() {
    return this.sourceId != 0;
  }

  protected void init() {
    this.generation++;
    this.sourceId = alGenSources();
    this.tmp = MemoryUtil.memAllocInt(1);

    alGenBuffers(this.allocatedBuffers);
    System.arraycopy(this.allocatedBuffers, 0, this.buffers, 0, this.buffers.length);
    this.bufferIndex = this.buffers.length - 1;

    this.playTime = 0.0f;
  }

  protected void destroy() {
    this.active = false;
    alSourceStop(this.sourceId);

    // Detach even an INITIAL queue: stopping an unstarted source need not mark it processed.
    alSourcei(this.sourceId, AL_BUFFER, 0);
    alDeleteBuffers(this.allocatedBuffers);
    alDeleteSources(this.sourceId);

    memFree(this.tmp);

    Arrays.fill(this.buffers, 0);
    Arrays.fill(this.allocatedBuffers, 0);
    this.sourceId = 0;
    this.tmp = null;

    this.playTime = 0.0f;
  }

  public void tick() {
    // Restart playback if stopped
    if(this.isActive()) {
      this.play();
    }
  }

  public int generation() { synchronized(this) { return this.generation; } }
  public boolean outputAvailable() { synchronized(this) { return this.isInitialized(); } }

  /** Number of free OpenAL buffers. Streaming callers leave one free so the audio tick can start playback. */
  public int availableBuffers() {
    synchronized(this) { return this.isInitialized() ? this.bufferIndex + 1 : 0; }
  }

  public boolean canBuffer() {
    if(!this.active || !this.isInitialized()) {
      return false;
    }

    return this.bufferIndex >= 0;
  }

  protected void handleProcessedBuffers() {
    if(this.isInitialized() && this.bufferIndex < this.buffers.length - 1) {
      alGetSourcei(this.sourceId, AL_BUFFERS_PROCESSED, this.tmp);
      final int processedBufferCount = this.tmp.get(0);

      for(int buffer = 0; buffer < processedBufferCount; buffer++) {
        final int unqueuedBufferId = alSourceUnqueueBuffers(this.sourceId);

        final int sizeBytes = alGetBufferi(unqueuedBufferId, AL_SIZE);
        final int channels = alGetBufferi(unqueuedBufferId, AL_CHANNELS);
        final int frequency = alGetBufferi(unqueuedBufferId, AL_FREQUENCY);
        if(channels > 0 && frequency > 0) {
          final int bits = alGetBufferi(unqueuedBufferId, AL_BITS);
          this.playTime += (double)sizeBytes / ((bits / 8.0) * channels * frequency);
        }

        this.buffers[++this.bufferIndex] = unqueuedBufferId;
      }
    }
  }

  protected void bufferOutput(final int format, final ByteBuffer buffer, final int sampleRate) {
    this.queueOutput(bufferId -> alBufferData(bufferId, format, buffer, sampleRate));
  }

  protected void bufferOutput(final int format, final short[] buffer, final int sampleRate) {
    this.queueOutput(bufferId -> alBufferData(bufferId, format, buffer, sampleRate));
  }

  protected void bufferOutput(final int format, final float[] buffer, final int sampleRate) {
    this.queueOutput(bufferId -> alBufferData(bufferId, format, buffer, sampleRate));
  }

  private void queueOutput(final java.util.function.IntConsumer upload) {
    synchronized(this) {
      if(!this.isInitialized()) return;
      final boolean resume = alGetSourcei(this.sourceId, AL_SOURCE_STATE) == AL_PLAYING;
      // The Java monitor does not stop the native mixer. Preserve its sample position
      // during this bounded refill, so it cannot enter STOPPED between retirement and append.
      if(resume) alSourcePause(this.sourceId);
      try {
        this.handleProcessedBuffers();
        if(this.bufferIndex < 0) return;
        // Buffers appended to STOPPED are considered processed despite never playing.
        if(alGetSourcei(this.sourceId, AL_BUFFERS_QUEUED) == 0) alSourceRewind(this.sourceId);
        final int bufferId = this.buffers[this.bufferIndex--];
        upload.accept(bufferId);
        alSourceQueueBuffers(this.sourceId, bufferId);
      } finally {
        if(resume && alGetSourcei(this.sourceId, AL_BUFFERS_QUEUED) > 0) alSourcePlay(this.sourceId);
      }
    }
  }

  protected void play() {
    synchronized(this) {
      if(!this.isInitialized() || this.playbackPaused) return;
      final int state = alGetSourcei(this.sourceId, AL_SOURCE_STATE);
      if(state == AL_PLAYING) return;
      // EOF may happen after the caller's retirement query; never restart that old tail.
      if(state == AL_STOPPED) this.handleProcessedBuffers();
      // Playing an empty queue changes INITIAL to STOPPED before the decoder can fill it.
      if(alGetSourcei(this.sourceId, AL_BUFFERS_QUEUED) > 0) alSourcePlay(this.sourceId);
    }
  }

  /** Pause a cinematic without discarding buffers or resetting its played-sample position. */
  public void setPlaybackPaused(final boolean paused) {
    synchronized(this) {
      this.playbackPaused = paused;
      if(paused && this.isInitialized()) alSourcePause(this.sourceId);
      else if(!paused && this.active) this.play();
    }
  }

  protected void stop() {
    this.active = false;
    this.playTime = 0.0f;

    if(this.isInitialized()) {
      alSourceStop(this.sourceId);
    }
  }

  /** Discard queued audio when replacing a recording, including a never-started queue. */
  protected void flushOutput() {
    synchronized(this) {
      this.active = false;
      if(this.isInitialized()) {
        alSourceStop(this.sourceId);
        alSourcei(this.sourceId, AL_BUFFER, 0);
        System.arraycopy(this.allocatedBuffers, 0, this.buffers, 0, this.buffers.length);
        this.bufferIndex = this.buffers.length - 1;
      }
      this.playTime = 0.0f;
    }
  }

  protected void setActive(final boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return this.active;
  }

  /** Total played time across processed buffers; callers must not use wall time during underflow. */
  public float getPlaybackPosition() {
    return (float)this.getPlaybackPositionSeconds();
  }

  /** Precise accumulated clock for frame-accurate recovery of long recordings. */
  public double getPlaybackPositionSeconds() {
    synchronized(this) {
      this.handleProcessedBuffers();
      final float offset = this.getPosition();
      // Playback can reach EOF after the first processed-buffer query. At STOPPED,
      // OpenAL resets the offset; retire that final tail before reporting its clock.
      if(this.isInitialized() && alGetSourcei(this.sourceId, AL_SOURCE_STATE) == AL_STOPPED) {
        this.handleProcessedBuffers();
        return this.playTime;
      }
      return this.playTime + offset;
    }
  }

  public boolean hasQueuedOutput() {
    synchronized(this) {
      return this.isInitialized() && alGetSourcei(this.sourceId, AL_BUFFERS_QUEUED) > 0;
    }
  }

  /** NOTE: this method will return the play time of the current buffer, so if you're using more than one buffer it's likely not going to return what you expect */
  public float getPosition() {
    synchronized(this) {
      if(!this.isInitialized()) {
        return 0.0f;
      }

      return alGetSourcef(this.sourceId, AL_SEC_OFFSET);
    }
  }
}
