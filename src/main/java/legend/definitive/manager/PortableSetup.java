// Definitive portable setup (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Prefer our paired release package; a reproducible source build is the fallback. */
public final class PortableSetup {
  private PortableSetup() { }
  public static String install(final Path supplied, final InstallStore store) throws IOException, InterruptedException {
    if(supplied != null && Files.isRegularFile(supplied.resolve(PackageManifest.METADATA))) return store.install(supplied);
    // A network error is actionable; never silently build after a failed release authenticity check.
    final var published = ReleaseUpdates.latest();
    if(published.isPresent()) return ReleaseUpdates.install(store, published.get());
    final Path source = Files.createTempDirectory(store.root(), ".source-build-");
    final Path log = source.resolve("setup-build.log");
    final Path checkout = source.resolve("source");
    try {
      execute(List.of("git", "clone", "--recursive", "https://github.com/gideonidoru/legend-of-dragoon-definitive.git", checkout.toString()), source, log);
      final List<String> command = new ArrayList<>(List.of("/bin/bash", "./gradlew", "--no-daemon", "--console=plain", "definitivePackage"));
      if(PackageManifest.hostPlatform().equals("linux-x64")) command.addAll(List.of("-Pos=linux", "-Parch=x86_64", "-Psteamdeck=true"));
      execute(command, checkout, log);
      return store.install(checkout.resolve("build/definitive/package"));
    } catch(final IOException e) { throw new IOException("Setup could not finish its source build. Check your connection and free space. Build details: " + log, e); }
    // Retain build logs/source on failure and success for diagnosis and corresponding source access.
  }
  private static void execute(final List<String> command, final Path cwd, final Path log) throws IOException, InterruptedException {
    final var builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    final Process process = builder.start(); process.getOutputStream().close();
    try { if(process.waitFor() != 0) throw new IOException("Build command failed; details in " + log); }
    finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
  }
}
