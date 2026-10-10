// Definitive installation diagnostics (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.nio.file.*;

/** Persistent diagnostics survive the temporary portable installer being removed. */
final class InstallerLog {
  private InstallerLog() { }
  static Path path() { return Path.of(System.getProperty("definitive.installerLog", Path.of(System.getProperty("user.home"), ".cache", "legend-of-dragoon-definitive", "installer.log").toString())).toAbsolutePath(); }
  static synchronized void write(final String message) {
    try {
      final Path path = path(); Files.createDirectories(path.getParent());
      if(Files.isSymbolicLink(path)) throw new IOException("Diagnostic log is a link");
      Files.writeString(path, java.time.Instant.now() + " " + message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch(final IOException e) { System.err.println("Cannot save installer log: " + e.getMessage()); System.err.println(message); }
  }
  static void failure(final Throwable error) {
    final var text = new StringWriter(); error.printStackTrace(new PrintWriter(text)); write(text.toString());
  }
  static String tail() throws IOException {
    try(final var file = new RandomAccessFile(path().toFile(), "r")) {
      file.seek(Math.max(0, file.length() - 16000));
      final byte[] bytes = new byte[(int)(file.length() - file.getFilePointer())]; file.readFully(bytes);
      return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
