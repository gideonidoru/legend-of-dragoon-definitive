// Definitive process exit semantics, AGPL v3; see LICENSE.
package legend.definitive.manager;

/** Steam Stop and deliberate interruption are normal session endings. */
final class ProcessResult {
  private ProcessResult() { }
  static boolean successful(final int code) { return code == 0 || code == 130 || code == 143; }
  static void printDiagnostics(final java.nio.file.Path path, final java.io.PrintStream output) {
    output.println("Details: " + path);
    try { output.print(DiagnosticLogs.tail(path, DiagnosticLogs.MAX_TAIL_BYTES, 24)); }
    catch(final java.io.IOException diagnosticFailure) { output.println("Could not read game diagnostics: " + diagnosticFailure.getMessage()); }
  }
}
