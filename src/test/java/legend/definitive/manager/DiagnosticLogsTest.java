package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticLogsTest {
  @TempDir Path temporary;
  @BeforeEach void realPath() throws Exception { this.temporary = this.temporary.toRealPath(); }
  @Test void sparseHugeLogTailRemainsBoundedAndKeepsOnlyRequestedLines() throws Exception {
    final Path log = this.temporary.resolve("large.log");
    try(final var file = new java.io.RandomAccessFile(log.toFile(), "rw")) {
      file.setLength(256L * 1024 * 1024); file.seek(file.length()); file.write("\nignored\n".getBytes(StandardCharsets.UTF_8));
      for(int line = 0; line < 30; line++) file.write(("line " + line + " 🗡\n").getBytes(StandardCharsets.UTF_8));
    }
    final String tail = DiagnosticLogs.tail(log, 4096, 24);
    assertTrue(tail.startsWith("line 6 ")); assertTrue(tail.endsWith("line 29 🗡\n")); assertEquals(24, tail.lines().count()); assertTrue(tail.getBytes(StandardCharsets.UTF_8).length < 4096);
  }
  @Test void byteTailDoesNotSplitLeadingUtf8Characters() throws Exception {
    final Path log = this.temporary.resolve("unicode.log"); Files.writeString(log, "🗡".repeat(40) + "\nlast 🗡\n");
    assertEquals("last 🗡\n", DiagnosticLogs.tail(log, 13, 2));
  }
  @Test void rotationRetainsOnePreviousLogAndTruncationIsExplicit() throws Exception {
    final Path log = this.temporary.resolve("installer.log");
    try(final var file = new java.io.RandomAccessFile(log.toFile(), "rw")) { file.setLength(DiagnosticLogs.ROTATE_BYTES); }
    try(final var out = DiagnosticLogs.openLog(log)) { out.write("new\n".getBytes(StandardCharsets.UTF_8)); }
    assertEquals(DiagnosticLogs.ROTATE_BYTES, Files.size(log.resolveSibling("installer.log.1"))); assertEquals("new\n", Files.readString(log));
    try(final var out = DiagnosticLogs.openLog(log, false)) { out.write("replacement\n".getBytes(StandardCharsets.UTF_8)); }
    assertEquals("replacement\n", Files.readString(log));
  }
  @Test void linkedLeafParentAndRotationTargetAreRejectedWithoutChangingProtectedFile() throws Exception {
    final Path protectedFile = this.temporary.resolve("protected.txt"); Files.writeString(protectedFile, "protected");
    final Path link = this.temporary.resolve("linked.log"); Files.createSymbolicLink(link, protectedFile);
    assertThrows(IOException.class, () -> DiagnosticLogs.openLog(link)); assertThrows(IOException.class, () -> DiagnosticLogs.tail(link));
    final Path real = Files.createDirectory(this.temporary.resolve("real")), parent = this.temporary.resolve("parent"); Files.createSymbolicLink(parent, real);
    assertThrows(IOException.class, () -> DiagnosticLogs.openLog(parent.resolve("log"))); assertFalse(Files.exists(real.resolve("log")));
    final Path log = this.temporary.resolve("rotate.log"); try(final var file = new java.io.RandomAccessFile(log.toFile(), "rw")) { file.setLength(DiagnosticLogs.ROTATE_BYTES); }
    Files.createSymbolicLink(log.resolveSibling("rotate.log.1"), protectedFile); assertThrows(IOException.class, () -> DiagnosticLogs.rotate(log)); assertEquals("protected", Files.readString(protectedFile));
  }
  @Test void unavailableGameDiagnosticsRemainActionableWithoutThrowing() {
    final var bytes = new java.io.ByteArrayOutputStream();
    assertDoesNotThrow(() -> ProcessResult.printDiagnostics(this.temporary.resolve("missing.log"), new java.io.PrintStream(bytes)));
    assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Could not read game diagnostics"));
  }
  @Test void supportReportContainsGameAndPreparationTailWithoutPrivateGameData() throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("installation")); store.install(fixture.pack("package", PackageManifest.hostPlatform())); store.prepareLaunch();
    Files.writeString(store.gameLog(), "actionable game failure\n"); Files.writeString(store.preparationLog(), "actionable preparation failure\n");
    Files.writeString(store.data(store.state()).resolve("saves/private.dsav"), "private save content");
    final String report = DiagnosticsReport.collect(store.root());
    assertTrue(report.contains("actionable game failure")); assertTrue(report.contains("actionable preparation failure")); assertFalse(report.contains("private save content"));
  }
  @Test void exportIsPrivateBoundedAndNeverOverwritesAnotherFile() throws Exception {
    final Path one = DiagnosticsReport.export(this.temporary, "report one"), two = DiagnosticsReport.export(this.temporary, "report two");
    assertNotEquals(one, two); assertEquals("report one", Files.readString(one)); assertEquals("report two", Files.readString(two));
    assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(one));
    assertThrows(IOException.class, () -> DiagnosticsReport.export(this.temporary, "x".repeat(512 * 1024 + 1)));
  }
}
