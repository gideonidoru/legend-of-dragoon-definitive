// Definitive faithful XA audio (2026-10-10), AGPL v3; see LICENSE.
package legend.core.audio.xa;

import legend.game.unpacker.FileData;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.opus.OpusFile;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/** Reads exact interleaved sample counts; legacy Opus remains supported. */
public interface XaDecoder extends AutoCloseable {
  int channels();
  int read(short[] output);
  void seek(long frame);
  @Override void close();

  static XaDecoder open(final FileData data) {
    if(data.size() < 4 || data.size() > XaPcm.MAX_BYTES + XaPcm.HEADER_BYTES) throw new IllegalArgumentException("Invalid XA audio size");
    return data.readInt(0) == 0x46464952 ? new Pcm(data) : new LegacyOpus(data);
  }

  final class Pcm implements XaDecoder {
    private final FileData data;
    private final int channels;
    private int position = XaPcm.HEADER_BYTES;

    private Pcm(final FileData data) { this.channels = XaPcm.channels(data); this.data = data; }
    @Override public int channels() { return this.channels; }
    @Override public int read(final short[] output) {
      if(output.length < this.channels) throw new IllegalArgumentException("XA output has no complete frame");
      final int count = Math.min(output.length / this.channels * this.channels, (this.data.size() - this.position) / 2);
      for(int i = 0; i < count; i++, this.position += 2) output[i] = this.data.readShort(this.position);
      return count;
    }
    @Override public void seek(final long frame) {
      if(frame < 0 || frame > (this.data.size() - XaPcm.HEADER_BYTES) / (this.channels * 2L)) throw new IllegalArgumentException("Invalid XA seek");
      this.position = XaPcm.HEADER_BYTES + (int)(frame * this.channels * 2);
    }
    @Override public void close() { this.position = this.data.size(); }
  }

  final class LegacyOpus implements XaDecoder {
    // Native decoder borrows this memory until op_free.
    private ByteBuffer data;
    private long handle;
    private final int channels;
    private final ShortBuffer buffer;

    private LegacyOpus(final FileData file) {
      this.data = MemoryUtil.memAlloc(file.size()).put(file.getBytes()).flip();
      try(final var stack = MemoryStack.stackPush()) {
        final var error = stack.mallocInt(1);
        this.handle = OpusFile.op_open_memory(this.data, error);
        if(this.handle == 0 || error.get(0) != 0) { this.close(); throw new IllegalArgumentException("Cannot open XA Opus: " + error.get(0)); }
      }
      try {
        this.channels = OpusFile.op_channel_count(this.handle, 0);
        if(this.channels != 1 && this.channels != 2) throw new IllegalArgumentException("Unsupported XA channel count");
        for(int link = 1; link < OpusFile.op_link_count(this.handle); link++) {
          if(OpusFile.op_channel_count(this.handle, link) != this.channels) throw new IllegalArgumentException("XA channel count changes between links");
        }
        this.buffer = BufferUtils.createShortBuffer(480 * this.channels);
      } catch(final RuntimeException failure) { this.close(); throw failure; }
    }
    @Override public int channels() { return this.channels; }
    @Override public int read(final short[] output) {
      if(this.handle == 0) return 0;
      if(output.length < this.channels) throw new IllegalArgumentException("XA output has no complete frame");
      this.buffer.clear().limit(Math.min(output.length, this.buffer.capacity()));
      int frames = OpusFile.op_read(this.handle, this.buffer, null);
      // OP_HOLE permits recovery, but do not spin forever on corrupt input.
      for(int retry = 0; frames == OpusFile.OP_HOLE && retry < 8; retry++) frames = OpusFile.op_read(this.handle, this.buffer, null);
      if(frames < 0) throw new IllegalArgumentException("XA Opus decode failed: " + frames);
      final int count = frames * this.channels;
      this.buffer.position(0).limit(count).get(output, 0, count);
      return count;
    }
    @Override public void seek(final long frame) {
      if(this.handle == 0 || OpusFile.op_pcm_seek(this.handle, frame) != 0) throw new IllegalArgumentException("Cannot seek XA Opus recording");
    }
    @Override public void close() {
      if(this.handle != 0) { OpusFile.op_free(this.handle); this.handle = 0; }
      if(this.data != null) { MemoryUtil.memFree(this.data); this.data = null; }
    }
  }
}
