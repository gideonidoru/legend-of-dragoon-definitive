// Definitive managed Steam integration (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Graceful client lifecycle around the existing backed-up, atomic library edit. */
final class SteamIntegration {
  private SteamIntegration() { }
  interface Client {
    boolean running();
    boolean started();
    void shutdown() throws IOException, InterruptedException;
    void start() throws IOException;
  }
  static String add(final SteamLibrary.Account account, final Path install, final InstallProgress progress) throws IOException, InterruptedException {
    return add(account, install, progress, new DesktopClient(install), Duration.ofSeconds(60), Duration.ofSeconds(30));
  }
  static String add(final SteamLibrary.Account account, final Path install, final InstallProgress progress, final Client client, final Duration stopTimeout, final Duration startTimeout) throws IOException, InterruptedException {
    // All accounts share a userdata directory: serialize the whole restart, not
    // just the individual account's file edit. Never delete a kernel lock inode.
    final Path lockPath = account.config().toRealPath().getParent().getParent().resolve(".definitive-steam-integration-lock");
    if(Files.isSymbolicLink(lockPath)) throw new IOException("Unexpected linked Steam integration lock.");
    try(final var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      final FileLock lock;
      try { lock = channel.tryLock(); } catch(final OverlappingFileLockException failure) { throw new IOException("Another installer is refreshing Steam. Wait for it to finish, then retry."); }
      if(lock == null) throw new IOException("Another installer is refreshing Steam. Wait for it to finish, then retry.");
      try(lock) {
        final boolean wasRunning = client.running();
        boolean restarting = false;
        try {
          if(wasRunning) {
            progress.phase("Closing Steam", "Requesting a graceful exit before updating your library", 10);
            client.shutdown();
            await(() -> !client.running(), stopTimeout, "Steam hasn’t closed yet. Finish any game or Steam prompt, then try Add to Steam again. Your library has not been changed.");
          }
          progress.phase("Adding to your library", "Backing up existing shortcuts and adding Definitive", 50);
          SteamLibrary.add(account, install, client::running);
          SteamLibrary.verifyShortcut(account, install);
          progress.phase("Starting Steam", "Refreshing your library with the verified shortcut", 85);
          restarting = true;
          try { client.start(); }
          catch(final IOException failure) { throw new IOException("The shortcut was added, but Steam couldn’t restart. Retry Add to Steam. Details: " + failure.getMessage(), failure); }
          await(client::started, startTimeout, "The shortcut was added, but Steam did not restart. Try Add to Steam again. Details: " + install.resolve("steam-integration.log"));
          progress.phase("Steam shortcut ready", "Definitive is added. Return to Gaming Mode to play.", 100);
          return "Added to Steam. Return to Gaming Mode to play.";
        } catch(final IOException | InterruptedException failure) {
          // A failed file edit must not leave a previously running client closed.
          if(wasRunning && !restarting && !client.running()) {
            try {
              progress.phase("Reopening Steam", "Restoring the client after the failed library step", 85);
              client.start();
              await(client::started, startTimeout, "Steam could not reopen after the failed library step. Retry Add to Steam. Details: " + install.resolve("steam-integration.log"));
            } catch(final IOException | InterruptedException recovery) {
              failure.addSuppressed(recovery);
              if(failure instanceof IOException) throw new IOException(failure.getMessage() + " Steam also couldn’t reopen; see error details before retrying.", failure);
            }
          }
          throw failure;
        }
      }
    }
  }
  private static void await(final java.util.function.BooleanSupplier ready, final Duration timeout, final String error) throws IOException, InterruptedException {
    final long end = System.nanoTime() + timeout.toNanos();
    while(!ready.getAsBoolean()) {
      if(System.nanoTime() >= end) throw new IOException(error);
      Thread.sleep(100);
    }
  }
  static final class DesktopClient implements Client {
    private final List<String> launch;
    private final Path log;
    DesktopClient(final Path install) throws IOException {
      this.log = install.resolve("steam-integration.log");
      if(Files.isSymbolicLink(this.log)) throw new IOException("Steam integration log is a link. Choose a normal log file and retry.");
      final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
      if(os.contains("linux")) {
        final List<Path> candidates = new ArrayList<>(List.of(Path.of("/usr/bin/steam"), Path.of("/bin/steam")));
        for(final String folder : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) if(!folder.isBlank()) candidates.add(Path.of(folder).resolve("steam"));
        this.launch = List.of(candidates.stream().filter(Files::isExecutable).findFirst().orElseThrow(() -> new IOException("Steam wasn’t found. Install and sign in to Steam, then retry Add to Steam.")).toString());
      } else if(os.contains("mac")) {
        this.launch = List.of(java.util.stream.Stream.of(Path.of("/Applications/Steam.app/Contents/MacOS/steam_osx"), Path.of(System.getProperty("user.home"), "Applications/Steam.app/Contents/MacOS/steam_osx")).filter(Files::isExecutable).findFirst().orElseThrow(() -> new IOException("Steam wasn’t found in Applications. Install and sign in to Steam, then retry Add to Steam.")).toString());
      } else throw new IOException("Automatic Steam setup is supported on SteamOS, Linux and macOS.");
    }
    DesktopClient(final List<String> launch, final Path log) { this.launch = List.copyOf(launch); this.log = log; }
    @Override public boolean running() { return SteamLibrary.steamRunning(); }
    @Override public boolean started() { return SteamLibrary.steamClientRunning(); }
    private Process command(final List<String> arguments) throws IOException {
      if(Files.isSymbolicLink(this.log)) throw new IOException("Steam integration log changed to a link.");
      InstallerLog.write("Steam integration: " + arguments);
      return new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(this.log.toFile())).start();
    }
    @Override public void shutdown() throws IOException, InterruptedException {
      final var arguments = new ArrayList<>(this.launch); arguments.add("-shutdown"); final Process request = this.command(arguments);
      if(!request.waitFor(15, TimeUnit.SECONDS)) { request.destroy(); throw new IOException("Steam did not respond to the exit request. Finish any Steam prompt, then retry. Details: " + this.log); }
      if(request.exitValue() != 0) throw new IOException("Steam could not close cleanly. Details: " + this.log);
    }
    @Override public void start() throws IOException { this.command(this.launch); }
  }
}
