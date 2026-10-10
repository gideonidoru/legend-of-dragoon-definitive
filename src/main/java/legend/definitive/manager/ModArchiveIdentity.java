// Bounded directory-only mod identity inspection (2026-10-10), AGPL v3.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.Path;

/** Never loads user classes or inflates artwork. Recognizes known bundled adapter names. */
final class ModArchiveIdentity {
  private ModArchiveIdentity() { }
  static boolean containsClass(final Path file, final String entryName) throws IOException {
    try(final var input = new java.io.RandomAccessFile(file.toFile(), "r")) {
      final long size = input.length();
      if(size < 22) return false;
      final byte[] tail = new byte[(int)Math.min(size, 65557)];
      input.seek(size - tail.length); input.readFully(tail);
      final var end = java.nio.ByteBuffer.wrap(tail).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      int position = tail.length - 22;
      while(position >= 0 && (end.getInt(position) != 0x06054b50 || position + 22 + Short.toUnsignedInt(end.getShort(position + 20)) != tail.length)) position--;
      if(position < 0) {
        input.seek(0);
        if(input.readInt() == 0x504b0304) throw new IOException("Custom mod has an incomplete ZIP directory: " + file.getFileName());
        return false;
      }
      if(end.getShort(position + 4) != 0 || end.getShort(position + 6) != 0 || end.getShort(position + 8) != end.getShort(position + 10)) throw new IOException("Split custom mod archives are unsupported.");
      long count = Short.toUnsignedInt(end.getShort(position + 10));
      long length = Integer.toUnsignedLong(end.getInt(position + 12)), offset = Integer.toUnsignedLong(end.getInt(position + 16));
      final long endOffset = size - tail.length + position;
      if(count == 65535 || length == 0xffffffffL || offset == 0xffffffffL) {
        // ZIP64 permits asset-heavy mods without a blanket archive-size limit.
        if(endOffset < 20) throw new IOException("Custom mod ZIP64 directory is missing.");
        final byte[] locatorBytes = new byte[20]; input.seek(endOffset - 20); input.readFully(locatorBytes);
        final var locator = java.nio.ByteBuffer.wrap(locatorBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        final long recordOffset = locator.getLong(8);
        if(locator.getInt(0) != 0x07064b50 || locator.getInt(4) != 0 || locator.getInt(16) != 1 || recordOffset < 0 || recordOffset > endOffset - 76) throw new IOException("Invalid custom mod ZIP64 directory.");
        final byte[] recordBytes = new byte[56]; input.seek(recordOffset); input.readFully(recordBytes);
        final var record = java.nio.ByteBuffer.wrap(recordBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(record.getInt(0) != 0x06064b50 || record.getLong(4) < 44 || record.getInt(16) != 0 || record.getInt(20) != 0 || record.getLong(24) != record.getLong(32)) throw new IOException("Invalid custom mod ZIP64 directory.");
        count = record.getLong(32); length = record.getLong(40); offset = record.getLong(48);
      }
      if(count < 0 || count > 100000 || length < 0 || length > 32L * 1024 * 1024 || offset < 0 || offset > endOffset || length > endOffset - offset) throw new IOException("Custom mod identity directory exceeds safe inspection bounds.");
      final long limit = offset + length;
      final byte[] headerBytes = new byte[46];
      final byte[] marker = entryName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      boolean found = false;
      for(long entry = 0; entry < count; entry++) {
        if(offset > limit - 46) throw new IOException("Truncated custom mod identity directory.");
        input.seek(offset); input.readFully(headerBytes);
        final var header = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(header.getInt(0) != 0x02014b50 || header.getShort(34) != 0) throw new IOException("Invalid custom mod identity directory.");
        final int nameLength = Short.toUnsignedInt(header.getShort(28));
        final long next = offset + 46 + nameLength + Short.toUnsignedInt(header.getShort(30)) + Short.toUnsignedInt(header.getShort(32));
        if(next > limit) throw new IOException("Truncated custom mod identity entry.");
        if(nameLength == marker.length) {
          final byte[] name = new byte[marker.length]; input.readFully(name);
          if(java.util.Arrays.equals(marker, name)) {
            if(found) throw new IOException("Ambiguous duplicate bundled mod identity in " + file.getFileName());
            found = true;
          }
        }
        offset = next;
      }
      if(offset != limit) {
        // The optional central-directory signature is metadata, not a payload.
        if(limit - offset < 6) throw new IOException("Unexpected custom mod directory metadata.");
        final byte[] signatureBytes = new byte[6]; input.seek(offset); input.readFully(signatureBytes);
        final var signature = java.nio.ByteBuffer.wrap(signatureBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(signature.getInt(0) != 0x05054b50 || 6L + Short.toUnsignedInt(signature.getShort(4)) != limit - offset) throw new IOException("Unexpected custom mod directory metadata.");
      }
      return found;
    }
  }
}
