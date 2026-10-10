// Definitive actionable support diagnostics, AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.UUID;

/** Plain text reports contain bounded current logs, never saves, discs, or personal mod contents. */
final class DiagnosticsReport {
  private DiagnosticsReport() { }
  static String collect(final Path root) {
    final var report = new StringBuilder("Legend of Dragoon: Definitive diagnostics\nTime: ").append(java.time.Instant.now()).append("\nInstallation: ").append(root).append("\nJava: ").append(Runtime.version()).append('\n');
    section(report, "Installer", InstallerLog.path());
    section(report, "Outer launcher", root.resolve("launcher.log"));
    section(report, "Manager router", root.resolve("manager-router.log"));
    try {
      final var store = new InstallStore(root); final var state = store.state();
      for(final String key : java.util.List.of("recoveryPending", "recoveryNotice", "recoveryRestoredRelease", "recoveryRestoredData", "recoveryPreservedLocations")) if(state.containsKey(key)) report.append("\n").append(key).append(": ").append(state.getProperty(key)).append("\n");
      report.append("\nActive package: ").append(state.getProperty("version", "unknown")).append("\nData generation: ").append(state.getProperty("data", "unknown")).append('\n');
      section(report, "Game", store.gameLog());
      section(report, "Disc preparation", store.preparationLog());
    } catch(final IOException failure) { report.append("\nInstallation inspection failed: ").append(failure.getMessage()).append('\n'); }
    return report.toString();
  }
  private static void section(final StringBuilder report, final String name, final Path path) {
    report.append("\n--- ").append(name).append(" ---\n").append(path).append('\n').append(DiagnosticLogs.availableTail(path)).append('\n');
  }
  static Path export(final Path directory, final String report) throws IOException {
    if(report.getBytes(StandardCharsets.UTF_8).length > 512 * 1024) throw new IOException("Diagnostic report exceeds its supported size");
    final Path file = DiagnosticLogs.checked(directory.resolve("Definitive-diagnostics-" + java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now()) + '-' + UUID.randomUUID().toString().substring(0, 8) + ".txt"));
    try(final var output = privateFile(file)) {
      final ByteBuffer bytes = ByteBuffer.wrap(report.getBytes(StandardCharsets.UTF_8)); while(bytes.hasRemaining()) output.write(bytes); output.force(true);
    }
    return file;
  }
  private static FileChannel privateFile(final Path file) throws IOException {
    final java.util.Set<java.nio.file.OpenOption> options = java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    try { return FileChannel.open(file, options, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))); }
    catch(final UnsupportedOperationException failure) { return FileChannel.open(file, options); }
  }
}
