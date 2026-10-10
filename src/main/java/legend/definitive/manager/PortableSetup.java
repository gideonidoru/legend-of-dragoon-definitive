// Definitive portable setup (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Prefer our paired release package; a reproducible source build is the fallback. */
public final class PortableSetup {
  private PortableSetup() { }
  public static String install(final Path supplied, final InstallStore store) throws IOException, InterruptedException {
    return install(supplied, store, InstallProgress.NONE);
  }
  public static String install(final Path supplied, final InstallStore store, final InstallProgress progress) throws IOException, InterruptedException {
    InstallerLog.write("Install target: " + store.root() + "; platform: " + PackageManifest.hostPlatform() + "; Java: " + Runtime.version());
    if(supplied != null && Files.isRegularFile(supplied.resolve(PackageManifest.METADATA))) return store.install(supplied, "", progress);
    progress.phase("Checking available space", store.root().toString(), 2);
    if(Files.getFileStore(store.root()).getUsableSpace() < 12L * 1024 * 1024 * 1024) throw new IOException("Setup needs at least 12 GB free in the installation folder. Free space or choose another drive, then retry.");
    progress.phase("Finding your build", "Checking GitHub for the " + PackageManifest.hostPlatform() + " package", 5);
    // A network error is actionable; never silently build after a failed release authenticity check.
    final var published = ReleaseUpdates.latest();
    if(published.isPresent()) return ReleaseUpdates.install(store, published.get(), progress);
    final Path source = Files.createTempDirectory(store.root(), ".source-build-");
    final Path log = source.resolve("setup-build.log");
    final Path checkout = source.resolve("source");
    try {
      progress.phase("Downloading source and HD artwork", "No platform package is available; building locally", 10);
      execute(List.of("git", "clone", "--recursive", "https://github.com/gideonidoru/legend-of-dragoon-definitive.git", checkout.toString()), source, log, progress, "Downloading source and HD artwork", 10, 10);
      final List<String> command = new ArrayList<>(List.of("/bin/bash", "./gradlew", "--no-daemon", "--console=plain", "definitivePackage"));
      if(PackageManifest.hostPlatform().equals("linux-x64")) command.addAll(List.of("-Pos=linux", "-Parch=x86_64", "-Psteamdeck=true"));
      progress.phase("Building Definitive", "Java and Gradle are compiling the game; build output follows below", 35);
      execute(command, checkout, log, progress, "Building Definitive", 35, 30);
      return store.install(checkout.resolve("build/definitive/package"), "", progress);
    } catch(final IOException e) { throw new IOException("Setup could not finish its source build. Check your connection and free space. Build details: " + log, e); }
    // Retain build logs/source on failure and success for diagnosis and corresponding source access.
  }
  private static void execute(final List<String> command, final Path cwd, final Path log, final InstallProgress progress, final String phase, final int percent, final int minutes) throws IOException, InterruptedException {
    final var builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    final Process process = builder.start(); process.getOutputStream().close();
    try {
      final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(minutes);
      long offset = 0;
      try(final var output = new java.io.RandomAccessFile(log.toFile(), "r")) {
        while(!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
          output.seek(offset); String line; String last = null;
          while((line = output.readLine()) != null) if(!line.isBlank()) last = line;
          offset = output.getFilePointer();
          if(last != null) progress.phase(phase, last.substring(0, Math.min(220, last.length())), percent);
          if(System.nanoTime() > deadline) throw new IOException(phase + " exceeded " + minutes + " minutes. Details: " + log);
        }
      }
      if(process.exitValue() != 0) throw new IOException("Build command failed; details in " + log);
    }
    finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
  }
}
