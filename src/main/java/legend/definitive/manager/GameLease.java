package legend.definitive.manager;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Durable spawn handoff plus an OS lease owned by the actual game JVM. */
final class GameLease {
  private GameLease() { }
  static final String PENDING = ".launch-pending.properties", RUNNING = ".game-session.properties";
  static String begin(final Path root) throws IOException {
    final String token = UUID.randomUUID().toString(); final Properties receipt = identity(ProcessHandle.current());
    receipt.setProperty("token", token); receipt.setProperty("created", Instant.now().toString());
    InstallStore.atomicProperties(root.resolve(PENDING), receipt); return token;
  }
  static void validatePending(final Path root, final String token) throws IOException {
    for(Path p = root; p != null; p = p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Linked game installation.");
    if(!token.matches("[a-f0-9-]{36}") || !token.equals(PackageManifest.readProperties(root.resolve(PENDING)).getProperty("token"))) throw new IOException("Invalid game launch handoff.");
  }
  static void recordRunning(final Path root, final String token) throws IOException {
    final Properties receipt = identity(ProcessHandle.current()); receipt.setProperty("token", token);
    InstallStore.atomicProperties(root.resolve(RUNNING), receipt);
  }
  private static Properties identity(final ProcessHandle process) throws IOException {
    final Properties receipt = new Properties(); receipt.setProperty("pid", Long.toString(process.pid()));
    receipt.setProperty("start", process.info().startInstant().orElseThrow(() -> new IOException("Cannot verify process identity safely.")).toString()); return receipt;
  }
  private static boolean alive(final Properties receipt) throws IOException {
    try {
      final long pid = Long.parseLong(receipt.getProperty("pid", "")); final Instant start = Instant.parse(receipt.getProperty("start", ""));
      final Optional<ProcessHandle> process = ProcessHandle.of(pid); if(process.isEmpty() || !process.get().isAlive()) return false;
      final Instant actual = process.get().info().startInstant().orElseThrow(() -> new IOException("Cannot verify a recorded running game. Close it before maintenance."));
      return actual.equals(start);
    } catch(final IllegalArgumentException | java.time.DateTimeException failure) { throw new IOException("Invalid game lifecycle record. Maintenance stopped.", failure); }
  }
  static void checkIdle(final Path root) throws IOException {
    final Path file = root.resolve(".game-lock");
    try(final var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
      try(final var lease = channel.tryLock()) { if(lease == null) throw new IOException("Close the managed game before changing this installation."); }
      catch(final OverlappingFileLockException failure) { throw new IOException("Close the managed game before changing this installation."); }
    }
    final Path running = root.resolve(RUNNING);
    if(Files.exists(running, LinkOption.NOFOLLOW_LINKS)) {
      if(alive(PackageManifest.readProperties(running))) throw new IOException("Close the managed game before changing this installation.");
      Files.delete(running); InstallStore.forceDirectory(root);
    }
    final Path pending = root.resolve(PENDING);
    if(!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return;
    final Properties receipt = PackageManifest.readProperties(pending);
    if(alive(receipt)) throw new IOException("Another launcher is starting the game.");
    final String token = receipt.getProperty("token", "");
    if(!token.matches("[a-f0-9-]{36}")) throw new IOException("Invalid game launch handoff. Maintenance stopped.");
    final Instant launcherStarted;
    try { launcherStarted = Instant.parse(receipt.getProperty("start", "")); Instant.parse(receipt.getProperty("created", "")); }
    catch(final java.time.DateTimeException failure) { throw new IOException("Invalid game launch time.", failure); }
    // The parent may die after spawn but before recording the PID. The unique
    // token is already in the child's argument vector, before Java executes it.
    final String user = ProcessHandle.current().info().user().orElseThrow(() -> new IOException("Cannot inspect game launch ownership safely."));
    try(final var processes = ProcessHandle.allProcesses()) {
      for(final ProcessHandle process : processes.toList()) {
        if(!process.isAlive() || process.pid() == ProcessHandle.current().pid()) continue;
        checkInterruptedProcess(token, user, launcherStarted, () -> {
          final var info = process.info();
          return new LaunchProcess(info.user(), info.startInstant(), info.arguments(), info.commandLine(), info.command());
        }, process::isAlive);
      }
    } catch(final RuntimeException unavailable) { throw new IOException("Cannot inspect an interrupted game launch safely. Close surviving game/launcher processes and retry.", unavailable); }
    Files.delete(pending); InstallStore.forceDirectory(root);
  }
  // A process may exit between enumeration, metadata inspection and the
  // ownership decision. Missing metadata from an exited process cannot write
  // into the installation; uncertain metadata from a living one still blocks.
  static void checkInterruptedProcess(final String token, final String user, final Instant launcherStarted,
                                      final java.util.function.Supplier<LaunchProcess> inspect, final java.util.function.BooleanSupplier alive) throws IOException {
    if(!alive.getAsBoolean()) return;
    final LaunchProcess details = inspect.get();
    if(!alive.getAsBoolean()) return;
    try { checkInterruptedProcess(token, user, launcherStarted, details); }
    catch(final IOException uncertain) { if(alive.getAsBoolean()) throw uncertain; }
  }
  record LaunchProcess(Optional<String> user, Optional<Instant> started, Optional<String[]> arguments, Optional<String> commandLine, Optional<String> command) { }
  static void checkInterruptedProcess(final String token, final String user, final Instant launcherStarted, final LaunchProcess process) throws IOException {
    if(process.user().isPresent() && !process.user().get().equals(user)) return;
    final String argument = "definitive.launchToken=" + token;
    // Linux intentionally omits Info.arguments for vectors larger than one
    // page. Its truncated commandLine can still identify the early token.
    if(process.arguments().filter(args -> Arrays.stream(args).anyMatch(a -> a.contains(argument))).isPresent()
      || process.commandLine().filter(line -> line.contains(argument)).isPresent()) throw new IOException("A surviving game process is still starting. Wait for it to finish.");
    // Compare two OS-reported process start times. Linux boot-time and clock
    // tick rounding can place an after-spawn child before receipt.created.
    final boolean couldBeChild = process.started().map(start -> !start.isBefore(launcherStarted)).orElse(true);
    if(!couldBeChild) return;
    final boolean knownArguments = process.arguments().filter(args -> args.length > 0).isPresent();
    final boolean spawning = process.command().map(command -> Path.of(command).getFileName().toString().equals("jspawnhelper")).orElse(false);
    if(process.user().isEmpty() || !knownArguments || spawning) throw new IOException("Cannot resolve an interrupted game launch safely. Close surviving game/launcher processes and retry.");
  }
  static void finished(final Path root, final String token) throws IOException {
    for(final String name : new String[]{RUNNING, PENDING}) {
      final Path path = root.resolve(name);
      if(Files.exists(path, LinkOption.NOFOLLOW_LINKS) && token.equals(PackageManifest.readProperties(path).getProperty("token"))) Files.delete(path);
    }
    InstallStore.forceDirectory(root);
  }
}
