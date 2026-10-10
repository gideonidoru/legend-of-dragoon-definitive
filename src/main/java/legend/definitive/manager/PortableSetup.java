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
    return install(supplied, store, progress, false);
  }
  static String install(final Path supplied, final InstallStore store, final InstallProgress progress, final boolean fullReinstall) throws IOException, InterruptedException {
    InstallerLog.write("Install target: " + store.root() + "; platform: " + PackageManifest.hostPlatform() + "; Java: " + Runtime.version());
    if(supplied != null && Files.isRegularFile(supplied.resolve(PackageManifest.METADATA))) return store.install(supplied, "", null, progress, fullReinstall);
    progress.phase("Checking available space", store.root().toString(), 2);
    if(Files.getFileStore(store.root()).getUsableSpace() < 1024L * 1024 * 1024) throw new IOException("Setup needs at least 1 GB free before downloading. Installation and disc preparation check their full storage needs separately.");
    progress.phase("Finding your build", "Checking GitHub for the " + PackageManifest.hostPlatform() + " package", 5);
    // A network error is actionable; never silently build after a failed release authenticity check.
    final var published = ReleaseUpdates.latest();
    if(published.isPresent()) return fullReinstall ? ReleaseUpdates.reinstall(store, published.get(), progress) : ReleaseUpdates.install(store, published.get(), progress);
    // This pin travels inside the checksum-verified manager JAR, never in a mutable user setting.
    final String revision = fallbackRevision();
    if(Files.getFileStore(store.root()).getUsableSpace() < 12L * 1024 * 1024 * 1024) throw new IOException("A reviewed source build needs at least 12 GB free for source, build output and dependencies. Free space or use a verified platform package.");
    final Path source = Files.createTempDirectory(store.root(), ".source-build-");
    final Path log = source.resolve("setup-build.log");
    final Path checkout = source.resolve("source");
    try {
      progress.phase("Downloading source and HD artwork", "No platform package is available; building locally", 10);
      checkoutPinned("https://github.com/gideonidoru/legend-of-dragoon-definitive.git", revision, checkout, log, progress);
      final List<String> command = new ArrayList<>(List.of("/bin/bash", "./gradlew", "--no-daemon", "--console=plain", "check", "definitivePackage"));
      if(PackageManifest.hostPlatform().equals("linux-x64")) command.addAll(List.of("-Pos=linux", "-Parch=x86_64", "-Psteamdeck=true"));
      progress.phase("Building Definitive", "Java and Gradle are compiling the game; build output follows below", 35);
      execute(command, checkout, log, progress, "Building Definitive", 35, 30);
      return store.install(checkout.resolve("build/definitive/package"), "", null, progress, fullReinstall);
    } catch(final IOException e) { throw new IOException("Setup could not finish its source build. Check your connection and free space. Build details: " + log, e); }
    // Retain build logs/source on failure and success for diagnosis and corresponding source access.
  }
  static String fallbackRevision() throws IOException {
    final Properties pin = new Properties();
    try(final var input = PortableSetup.class.getResourceAsStream("definitive-fallback.properties")) {
      if(input == null) throw new IOException("No reviewed source fallback is included in this installer. Download a verified platform package instead.");
      pin.load(input);
    }
    final String revision = pin.getProperty("sourceRevision", "");
    if(!revision.matches("[a-f0-9]{40}")) throw new IOException("The source fallback has no clean reviewed revision. Download a verified platform package instead.");
    return revision;
  }
  static void checkoutPinned(final String repository, final String revision, final Path checkout, final Path log, final InstallProgress progress) throws IOException, InterruptedException {
    if(!revision.matches("[a-f0-9]{40}")) throw new IOException("Invalid reviewed source revision.");
    Files.createDirectory(checkout);
    final long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
    for(final List<String> command : List.of(
        List.of("git", "init", "--quiet"),
        List.of("git", "remote", "add", "origin", repository),
        List.of("git", "fetch", "--depth=1", "origin", revision),
        List.of("git", "checkout", "--detach", revision),
        List.of("git", "submodule", "update", "--init", "--recursive"))) {
      final long remaining = deadline - System.nanoTime();
      if(remaining <= 0) throw new IOException("Reviewed source checkout exceeded its allowed time.");
      execute(command, checkout, log, progress, "Downloading reviewed source and HD artwork", 10, java.time.Duration.ofNanos(remaining));
    }
  }
  private static void execute(final List<String> command, final Path cwd, final Path log, final InstallProgress progress, final String phase, final int percent, final int minutes) throws IOException, InterruptedException {
    execute(command, cwd, log, progress, phase, percent, java.time.Duration.ofMinutes(minutes));
  }
  private static void execute(final List<String> command, final Path cwd, final Path log, final InstallProgress progress, final String phase, final int percent, final java.time.Duration timeout) throws IOException, InterruptedException {
    final var builder = new ProcessBuilder(command).directory(cwd.toFile());
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    if(command.getFirst().equals("git")) {
      builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
      builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
      builder.environment().put("GIT_TERMINAL_PROMPT", "0");
    }
    try(final var running = ProcessRunner.start(builder, log)) {
      final int result = running.await(timeout, () -> {
        final String last = running.lastLine();
        if(!last.isBlank()) progress.phase(phase, last.substring(0, Math.min(220, last.length())), percent);
      });
      if(result != 0) throw new IOException("Build command failed; details in " + log);
    }
  }
}
