// Definitive installation diagnostics, AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Persistent, rotated diagnostics survive temporary installer removal. */
final class InstallerLog {
  private InstallerLog() { }
  static Path path() { return Path.of(System.getProperty("definitive.installerLog", Path.of(System.getProperty("user.home"), ".cache", "legend-of-dragoon-definitive", "installer.log").toString())).toAbsolutePath(); }
  static synchronized void write(final String message) {
    try(final var output = DiagnosticLogs.openLog(path())) {
      output.write((java.time.Instant.now() + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
    } catch(final IOException e) { System.err.println("Cannot save installer log: " + e.getMessage()); System.err.println(message); }
  }
  static void failure(final Throwable error) {
    final var text = new StringWriter(); error.printStackTrace(new PrintWriter(text)); write(text.toString());
  }
  static String tail() throws IOException { return DiagnosticLogs.tail(path(), 16000, 200); }
}
