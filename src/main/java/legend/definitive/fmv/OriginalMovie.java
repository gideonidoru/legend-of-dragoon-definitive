package legend.definitive.fmv;

import legend.core.spu.XaAdpcm;
import legend.game.fmv.SectorHeader;
import legend.game.fmv.VideoSector;
import legend.game.unpacker.FileData;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

/** Ordered original-disc decoding, with bounded backpressure and no graphics/audio calls. */
public final class OriginalMovie implements AutoCloseable {
  public record VideoFrame(int number, int width, int height, int[] pixels) {
    public long micros() { return this.number * 1_000_000L / 15; }
  }
  @FunctionalInterface public interface Decoder { VideoFrame decode(byte[] bytes, int size, int number); }
  private final ArrayBlockingQueue<VideoFrame> video = new ArrayBlockingQueue<>(16);
  private final ArrayBlockingQueue<short[]> audio = new ArrayBlockingQueue<>(32);
  private final Thread worker, audioWorker;
  private final int endOffset, totalFrames;
  private volatile boolean closed, finished;
  private volatile boolean audioFinished;
  private volatile IOException failure;
  private volatile int frames;

  public OriginalMovie(final FileData data, final Decoder decoder) {
    if(data.size() == 0 || data.size() % 2352 != 0) throw new IllegalArgumentException("Truncated original movie sectors");
    // Know audio EOF independently: the bounded video queue may fill long before global EOF.
    int lastAudio = -1, end = -1, count = 0;
    for(int offset = 0; offset < data.size(); offset += 2352) {
      final int flags = data.readUByte(offset + 18);
      if((flags & 14) == 4) lastAudio = offset;
      if((flags & 14) == 8 && data.readUShort(offset + 30) > 0 && data.readUShort(offset + 28) == 0) count++;
      if((flags & 128) != 0) { end = offset; break; }
    }
    if(end < 0 || count == 0) throw new IllegalArgumentException("Original movie lacks EOF or video frames");
    this.endOffset = end; this.totalFrames = count;
    this.audioFinished = lastAudio < 0;
    this.worker = new Thread(() -> this.decode(data, decoder), "original-movie-decoder");
    this.audioWorker = new Thread(() -> this.decodeAudio(data), "original-movie-audio-decoder");
    this.worker.setDaemon(true); this.audioWorker.setDaemon(true);
    this.worker.start(); this.audioWorker.start();
  }

  private void decode(final FileData file, final Decoder decoder) {
    final byte[] sector = new byte[2352];
    final SectorHeader header = new SectorHeader(sector);
    final VideoSector chunk = new VideoSector(sector);
    byte[] compressed = null;
    int chunks = 0, size = 0, frameNumber = 0;
    try {
      for(int offset = 0; offset <= this.endOffset && !this.closed; offset += sector.length) {
        file.read(offset, sector, 0, sector.length);
        final int type = sector[18] & 14;
        if(type == 8 && chunk.getChunkCount() != 0) {
          if(chunk.getChunkNumber() == 0) {
            if(compressed != null) throw new IOException("Incomplete original video frame");
            final int count = chunk.getChunkCount();
            if(count > 10 || chunk.getFrameWidth() <= 0 || chunk.getFrameWidth() > 640 || chunk.getFrameHeight() <= 0 || chunk.getFrameHeight() > 480) throw new IOException("Original frame exceeds decode budget");
            compressed = new byte[count * 2016];
            size = chunk.getDemuxedSize();
            if(size <= 10 || size > compressed.length) throw new IOException("Invalid original frame length");
            chunks = 0;
            frameNumber = chunk.getFrameNumber();
          }
          if(compressed == null || chunk.getFrameNumber() != frameNumber || chunk.getChunkNumber() != chunks || chunk.getChunkCount() * 2016 != compressed.length) throw new IOException("Unordered original video chunks");
          chunk.readSector(compressed, chunks++);
          if(chunks * 2016 == compressed.length) {
            final VideoFrame frame = decoder.decode(compressed, size, this.frames);
            if(frame.number() != this.frames || frame.width() <= 0 || frame.width() > 640 || frame.height() <= 0 || frame.height() > 480 || frame.pixels().length != frame.width() * frame.height()) throw new IOException("Invalid decoded original frame");
            if(!this.closed) this.video.put(frame);
            this.frames++;
            compressed = null;
          }
        }
        if(header.submode.isEof()) break;
      }
      if(!this.closed && (compressed != null || this.frames != this.totalFrames)) throw new IOException("Original movie ended without complete frames");
    } catch(final InterruptedException interrupted) {
      if(!this.closed) this.failure = new IOException("Original decoding interrupted", interrupted);
      Thread.currentThread().interrupt();
    } catch(final IOException | RuntimeException failure) {
      if(!this.closed) this.failure = new IOException("Original movie decoding failed", failure);
    } finally { this.finished = true; }
  }

  private void decodeAudio(final FileData file) {
    final byte[] sector = new byte[2352];
    final XaAdpcm.Decoder xa = new XaAdpcm.Decoder();
    try {
      for(int offset = 0; offset <= this.endOffset && !this.closed; offset += sector.length) {
        if((file.readUByte(offset + 18) & 14) != 4) continue;
        file.read(offset, sector, 0, sector.length);
        // Decode the EOF sector too, and never discard PCM when a consumer is full.
        final short[] samples = xa.decode(sector, sector[19]);
        if(samples.length == 0 || samples.length > 16128 || samples.length % 2 != 0) throw new IOException("Invalid original audio sector");
        if(!this.closed) this.audio.put(samples);
      }
    } catch(final InterruptedException interrupted) {
      if(!this.closed) this.failure = new IOException("Original audio decoding interrupted", interrupted);
      Thread.currentThread().interrupt();
    } catch(final IOException | RuntimeException failure) {
      if(!this.closed) this.failure = new IOException("Original audio decoding failed", failure);
    } finally { this.audioFinished = true; }
  }

  public short[] peekAudio() { return this.audio.peek(); }
  public short[] pollAudio() { return this.audio.poll(); }
  public VideoFrame peekVideo() { return this.video.peek(); }
  public VideoFrame pollVideo(final long micros) {
    VideoFrame latest = null;
    while(this.video.peek() != null && this.video.peek().micros() <= micros) latest = this.video.poll();
    return latest;
  }
  public boolean audioDrained() { return this.audioFinished && this.audio.isEmpty(); }
  public boolean drained() { return this.finished && this.audioFinished && this.audio.isEmpty() && this.video.isEmpty(); }
  public long durationMicros() { return this.totalFrames * 1_000_000L / 15; }
  public IOException failure() { return this.failure; }
  public int bufferedVideoFrames() { return this.video.size(); }
  public int bufferedAudioBlocks() { return this.audio.size(); }
  @Override public void close() {
    this.closed = true;
    this.worker.interrupt(); this.audioWorker.interrupt();
    try { this.worker.join(2000); this.audioWorker.join(2000); }
    catch(final InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    this.audio.clear(); this.video.clear();
  }
}
