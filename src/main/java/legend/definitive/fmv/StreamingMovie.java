package legend.definitive.fmv;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Independent audio/image decoder owners; bounded copied payloads; no rendering or audio API calls on workers. */
public final class StreamingMovie implements AutoCloseable {
  public record VideoFrame(long timestamp, byte[] rgb) { }

  private final FFmpegFrameGrabber grabber;
  private final ArrayBlockingQueue<VideoFrame> images = new ArrayBlockingQueue<>(6);
  private final ArrayBlockingQueue<short[]> samples = new ArrayBlockingQueue<>(64);
  private final Thread worker;
  private final Thread audioWorker;
  private final Path path;
  private volatile boolean cancelled;
  private volatile boolean finished;
  private volatile boolean audioFinished;
  private volatile IOException failure;
  public final int width;
  public final int height;
  public final double frameRate;
  public final long durationMicros;

  public StreamingMovie(final Path path) throws IOException {
    this.path = path;
    this.grabber = new FFmpegFrameGrabber(path.toFile());
    try {
      this.grabber.setPixelFormat(avutil.AV_PIX_FMT_RGB24);
      this.grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
      this.grabber.setSampleRate(48_000);
      this.grabber.setAudioChannels(2);
      this.grabber.start();
      this.width = this.grabber.getImageWidth();
      this.height = this.grabber.getImageHeight();
      this.frameRate = this.grabber.getFrameRate();
      this.durationMicros = this.grabber.getLengthInTime();
      if(this.width <= 0 || this.height <= 0 || (long)this.width * this.height > 8_388_608 || !Double.isFinite(this.frameRate) || this.frameRate <= 0 || this.frameRate > 120 || this.durationMicros <= 0 || this.grabber.getSampleRate() != 48_000 || this.grabber.getAudioChannels() != 2) {
        throw new IOException("Unsupported movie dimensions, cadence, duration or stereo audio");
      }
    } catch(final IOException | RuntimeException e) {
      try { this.grabber.release(); } catch(final IOException cleanup) { e.addSuppressed(cleanup); }
      throw e;
    }
    this.worker = new Thread(this::decode, "FMV decoder");
    this.worker.setDaemon(true);
    this.audioWorker = new Thread(this::decodeAudio, "FMV audio decoder");
    this.audioWorker.setDaemon(true);
    final boolean hasAudio = this.grabber.getAudioStream() >= 0;
    this.worker.start();
    if(hasAudio) this.audioWorker.start();
    else this.audioFinished = true;
  }

  private void decode() {
    try {
      Frame frame;
      while(!this.cancelled && (frame = this.grabber.grabImage()) != null) {
        if(frame.image != null) {
          final ByteBuffer input = ((ByteBuffer)frame.image[0]).duplicate();
          final byte[] rgb = new byte[this.width * this.height * 3];
          for(int y = 0; y < this.height; y++) {
            input.position(y * frame.imageStride);
            input.get(rgb, y * this.width * 3, this.width * 3);
          }
          this.put(this.images, new VideoFrame(frame.timestamp, rgb));
        }
      }
    } catch(final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch(final IOException | RuntimeException e) {
      if(!this.cancelled) this.failure = new IOException("Movie decoding failed", e);
    } finally {
      try { this.grabber.release(); } catch(final IOException e) { if(!this.cancelled) this.failure = e; }
      this.finished = true;
    }
  }

  /** Audio EOF is independent of image backpressure, so a video-only tail can advance. */
  private void decodeAudio() {
    try(final FFmpegFrameGrabber audio = new FFmpegFrameGrabber(this.path.toFile())) {
      audio.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
      audio.setSampleRate(48_000);
      audio.setAudioChannels(2);
      audio.start();
      Frame frame;
      while(!this.cancelled && (frame = audio.grabSamples()) != null) {
        if(frame.samples == null) continue;
        if(!(frame.samples[0] instanceof ShortBuffer input) || frame.samples.length != 1) {
          throw new IOException("Decoder did not produce packed signed-16-bit stereo samples");
        }
        final ShortBuffer copy = input.duplicate();
        if(copy.remaining() > 32768) throw new IOException("Audio packet exceeds bounded streaming budget");
        final short[] pcm = new short[copy.remaining()];
        copy.get(pcm);
        this.put(this.samples, pcm);
      }
    } catch(final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch(final IOException | RuntimeException e) {
      if(!this.cancelled) this.failure = new IOException("Movie audio decoding failed", e);
    } finally { this.audioFinished = true; }
  }

  private <T> void put(final ArrayBlockingQueue<T> queue, final T value) throws InterruptedException {
    while(!this.cancelled && !queue.offer(value, 100, TimeUnit.MILLISECONDS)) { /* Bounded backpressure, cancellable. */ }
  }

  public short[] peekAudio() { return this.samples.peek(); }
  public short[] pollAudio() { return this.samples.poll(); }

  /** Keep the newest due image; later timestamps remain queued. */
  public VideoFrame pollVideo(final long playedMicros) {
    VideoFrame result = null;
    while(this.images.peek() != null && this.images.peek().timestamp <= playedMicros + 15_000) result = this.images.poll();
    return result;
  }

  public boolean drained() { return this.finished && this.audioFinished && this.images.isEmpty() && this.samples.isEmpty(); }
  public boolean audioDrained() { return this.audioFinished && this.samples.isEmpty(); }
  public IOException failure() { return this.failure; }
  public int bufferedImages() { return this.images.size(); }
  public int bufferedAudio() { return this.samples.size(); }

  @Override
  public void close() {
    this.cancelled = true;
    this.worker.interrupt();
    this.audioWorker.interrupt();
    try { this.worker.join(1000); this.audioWorker.join(1000); } catch(final InterruptedException e) { Thread.currentThread().interrupt(); }
    this.images.clear();
    this.samples.clear();
  }
}
