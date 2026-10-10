// Definitive faithful XA audio (2026-10-10), AGPL v3; see LICENSE.
package legend.core.audio.xa;

import legend.game.unpacker.FileData;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Canonical lossless XA import: signed 16-bit PCM, 48 kHz, mono or stereo. */
public final class XaPcm {
  public static final int HEADER_BYTES = 44;
  public static final int SAMPLE_RATE = 48_000;
  public static final int MAX_BYTES = 256 * 1024 * 1024;

  private XaPcm() { }

  public static byte[] encode(final byte[] pcm, final int channels) {
    validateShape(channels, pcm.length);
    final ByteBuffer bytes = ByteBuffer.allocate(HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0x46464952).putInt(36 + pcm.length).putInt(0x45564157);
    bytes.putInt(0x20746d66).putInt(16).putShort((short)1).putShort((short)channels);
    bytes.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * channels * 2).putShort((short)(channels * 2)).putShort((short)16);
    bytes.putInt(0x61746164).putInt(pcm.length).put(pcm);
    return bytes.array();
  }

  public static int channels(final FileData data) {
    if(data.size() < HEADER_BYTES) throw new IllegalArgumentException("Truncated XA PCM header");
    return validateHeader(data.slice(0, HEADER_BYTES).getBytes(), data.size());
  }

  public static boolean isComplete(final Path path) {
    if(!Files.isRegularFile(path)) return false;
    try(final var input = Files.newInputStream(path)) {
      validateHeader(input.readNBytes(HEADER_BYTES), Files.size(path));
      return true;
    } catch(final IOException | IllegalArgumentException failure) {
      return false;
    }
  }

  private static int validateHeader(final byte[] header, final long size) {
    if(header.length != HEADER_BYTES || size > MAX_BYTES + HEADER_BYTES) throw new IllegalArgumentException("Invalid XA PCM size");
    final ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
    final int channels = Short.toUnsignedInt(bytes.getShort(22));
    final int length = bytes.getInt(40);
    validateShape(channels, length);
    if(bytes.getInt(0) != 0x46464952 || bytes.getInt(8) != 0x45564157 || bytes.getInt(12) != 0x20746d66
      || bytes.getInt(16) != 16 || bytes.getShort(20) != 1 || bytes.getInt(24) != SAMPLE_RATE
      || bytes.getInt(28) != SAMPLE_RATE * channels * 2 || bytes.getShort(32) != channels * 2
      || bytes.getShort(34) != 16 || bytes.getInt(36) != 0x61746164
      || size != HEADER_BYTES + (long)length || bytes.getInt(4) != 36 + length) {
      throw new IllegalArgumentException("Invalid XA PCM format");
    }
    return channels;
  }

  private static void validateShape(final int channels, final int length) {
    if((channels != 1 && channels != 2) || length <= 0 || length > MAX_BYTES || length % (channels * 2) != 0) {
      throw new IllegalArgumentException("Invalid XA PCM channels or sample count");
    }
  }
}
