// Definitive raw byte indexing (2026-10-11), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;

/** Optional raw local-entry boundaries. Never inflates entries or treats metadata as a checksum. */
final class RawArchive {
  record Range(long offset, int bytes) { }
  private RawArchive() { }
  static byte[] read(final FileChannel channel, final long offset, final int bytes) throws IOException {
    if(offset < 0 || bytes < 0 || bytes > 32 * 1024 * 1024 || offset > channel.size() - bytes) throw bad();
    final var buffer = ByteBuffer.allocate(bytes);
    while(buffer.hasRemaining()) {
      PackageManifest.checkCancelled();
      final int count = channel.read(buffer, offset + buffer.position());
      if(count < 0) throw bad();
      if(count == 0) throw new IOException("Local archive read did not progress.");
    }
    return buffer.array();
  }
  static List<Range> ranges(final FileChannel channel, final boolean archive) throws IOException {
    final long size = channel.size();
    if(size <= 0 || size >= 2L * 1024 * 1024 * 1024) throw bad();
    final List<Range> result = new ArrayList<>();
    if(!archive) { append(result, 0, size); return result; }
    final int tailSize = (int)Math.min(size, 65557);
    final var tail = little(read(channel, size - tailSize, tailSize));
    int at = tailSize - 22;
    for(; at >= 0; at--) if(tail.getInt(at) == 0x06054b50 && at + 22 + unsignedShort(tail, at + 20) == tailSize) break;
    if(at < 0 || unsignedShort(tail, at + 4) != 0 || unsignedShort(tail, at + 6) != 0) throw bad();
    long count = unsignedShort(tail, at + 10), length = unsignedInt(tail, at + 12), directory = unsignedInt(tail, at + 16);
    final long diskCount = unsignedShort(tail, at + 8); long end = size - tailSize + at;
    if(count == 65535 || diskCount == 65535 || length == 0xffffffffL || directory == 0xffffffffL) {
      if(end < 76) throw bad();
      final var locator = little(read(channel, end - 20, 20));
      final long record = locator.getLong(8);
      if(locator.getInt(0) != 0x07064b50 || locator.getInt(4) != 0 || locator.getInt(16) != 1 || record < 0 || record != end - 76) throw bad();
      final var zip64 = little(read(channel, record, 56));
      if(zip64.getInt(0) != 0x06064b50 || zip64.getLong(4) != 44 || zip64.getInt(16) != 0 || zip64.getInt(20) != 0 || zip64.getLong(24) != zip64.getLong(32)) throw bad();
      if(count != 65535 && count != zip64.getLong(32) || diskCount != 65535 && diskCount != zip64.getLong(24)
        || length != 0xffffffffL && length != zip64.getLong(40) || directory != 0xffffffffL && directory != zip64.getLong(48)) throw bad();
      count = zip64.getLong(32); length = zip64.getLong(40); directory = zip64.getLong(48); end = record;
    } else if(count != diskCount) throw bad();
    if(count < 0 || count > 100000 || length < 0 || length > 32 * 1024 * 1024 || directory < 0 || directory > end || length != end - directory || (count == 0) != (length == 0)) throw bad();
    final var central = little(read(channel, directory, (int)length));
    final TreeSet<Long> offsets = new TreeSet<>(); int position = 0;
    for(long entry = 0; entry < count; entry++) {
      if(position > length - 46 || central.getInt(position) != 0x02014b50) throw bad();
      final int name = unsignedShort(central, position + 28), extra = unsignedShort(central, position + 30), comment = unsignedShort(central, position + 32);
      final int endRecord = position + 46 + name + extra + comment;
      if(name == 0 || endRecord > length || unsignedShort(central, position + 34) != 0) throw bad();
      long offset = unsignedInt(central, position + 42);
      if(offset == 0xffffffffL) {
        int cursor = position + 46 + name; boolean found = false;
        while(cursor < position + 46 + name + extra) {
          if(cursor > position + 46 + name + extra - 4) throw bad();
          final int field = unsignedShort(central, cursor), bytes = unsignedShort(central, cursor + 2); cursor += 4;
          if(cursor + bytes > position + 46 + name + extra) throw bad();
          if(field == 1) {
            if(found) throw bad(); found = true;
            int skip = 0;
            if(unsignedInt(central, position + 24) == 0xffffffffL) skip += 8;
            if(unsignedInt(central, position + 20) == 0xffffffffL) skip += 8;
            if(bytes < skip + 8) throw bad(); offset = central.getLong(cursor + skip);
          }
          cursor += bytes;
        }
        if(!found) throw bad();
      }
      if(offset < 0 || offset >= directory || !offsets.add(offset)) throw bad();
      position = endRecord;
    }
    if(position != length || !offsets.isEmpty() && offsets.first() != 0 || offsets.isEmpty() && directory != 0) throw bad();
    long start = 0;
    for(final long offset : offsets) { if(offset > start) append(result, start, offset - start); start = offset; }
    if(directory > start) append(result, start, directory - start);
    append(result, directory, size - directory);
    return List.copyOf(result);
  }
  private static void append(final List<Range> ranges, long start, long length) throws IOException {
    while(length > 0) {
      if(ranges.size() >= ArchiveAssembly.MAX_SPANS) throw bad();
      final int bytes = (int)Math.min(length, ArchiveAssembly.MAX_BYTES);
      ranges.add(new Range(start, bytes)); start += bytes; length -= bytes;
    }
  }
  private static ByteBuffer little(final byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
  private static int unsignedShort(final ByteBuffer bytes, final int at) { return Short.toUnsignedInt(bytes.getShort(at)); }
  private static long unsignedInt(final ByteBuffer bytes, final int at) { return Integer.toUnsignedLong(bytes.getInt(at)); }
  private static IOException bad() { return new IOException("Local archive metadata is unavailable or exceeds its indexing budget."); }
}
