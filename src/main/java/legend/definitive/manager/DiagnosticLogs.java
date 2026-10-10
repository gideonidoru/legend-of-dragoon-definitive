// Definitive bounded diagnostics, AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;

/** Bounded diagnostics; never follow a linked log or linked parent directory. */
final class DiagnosticLogs {
  static final int MAX_TAIL_BYTES = 64 * 1024;
  static final long ROTATE_BYTES = 4L * 1024 * 1024;
  private DiagnosticLogs() { }

  static Path checked(final Path input) throws IOException {
    final Path path = input.toAbsolutePath().normalize();
    for(Path current = path; current != null; current = current.getParent()) {
      if(Files.isSymbolicLink(current)) throw new IOException("Diagnostic path is linked: " + current);
    }
    if(Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Diagnostic path is not a regular file: " + path);
    return path;
  }

  static synchronized void rotate(final Path input) throws IOException {
    final Path path = checked(input);
    if(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
    try(final FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      if(file.size() < ROTATE_BYTES) return;
    }
    final Path previous = checked(path.resolveSibling(path.getFileName() + ".1"));
    Files.move(path, previous, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  static OutputStream openLog(final Path input) throws IOException { return openLog(input, true); }

  static synchronized OutputStream openLog(final Path input, final boolean append) throws IOException {
    final Path path = checked(input);
    Files.createDirectories(path.getParent());
    checked(path);
    rotate(path);
    return Channels.newOutputStream(FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)));
  }

  static String tail(final Path input) throws IOException { return tail(input, MAX_TAIL_BYTES, 200); }
  static String tail(final Path input, final int requestedBytes, final int requestedLines) throws IOException {
    if(requestedBytes < 1 || requestedLines < 1) throw new IllegalArgumentException("Positive diagnostic bounds required");
    final Path path = checked(input);
    final int bound = Math.min(requestedBytes, MAX_TAIL_BYTES);
    try(final FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      final long size = file.size(), start = Math.max(0, size - bound);
      file.position(start);
      final ByteBuffer data = ByteBuffer.allocate((int)Math.min(size, bound));
      while(data.hasRemaining() && file.read(data) > 0) { }
      final byte[] bytes = new byte[data.position()]; data.flip(); data.get(bytes);
      int begin = 0;
      // A byte-boundary can split UTF-8. Drop continuation bytes and the partial first line.
      if(start > 0) {
        while(begin < bytes.length && (bytes[begin] & 0xc0) == 0x80) begin++;
        int newline = begin; while(newline < bytes.length && bytes[newline] != '\n') newline++;
        if(newline < bytes.length) begin = newline + 1;
      }
      final String text = new String(bytes, begin, bytes.length - begin, StandardCharsets.UTF_8);
      int end = text.length(); if(end > 0 && text.charAt(end - 1) == '\n') end--;
      int lines = 1, first = end;
      while(first > 0) { first--; if(text.charAt(first) == '\n' && ++lines > requestedLines) { first++; break; } }
      return text.substring(first);
    }
  }

  static String availableTail(final Path path) {
    try { return tail(path); }
    catch(final IOException failure) { return "Could not read diagnostics: " + failure.getMessage(); }
  }
}
