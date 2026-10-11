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
      return recordedAlive(start, process.get()::isAlive, () -> process.get().info().startInstant());
    } catch(final IllegalArgumentException | java.time.DateTimeException failure) { throw new IOException("Invalid game lifecycle record. Maintenance stopped.", failure); }
  }
  static boolean recordedAlive(final Instant expected, final java.util.function.BooleanSupplier alive,
                               final java.util.function.Supplier<Optional<Instant>> inspect) throws IOException {
    if(!alive.getAsBoolean()) return false;
    final Optional<Instant> actual = inspect.get();
    if(!alive.getAsBoolean()) return false;
    try { return actual.orElseThrow(() -> new IOException("Cannot verify a recorded running game. Close it before maintenance.")).equals(expected); }
    catch(final IOException uncertain) { if(!alive.getAsBoolean()) return false; throw uncertain; }
  }
  static final class Busy extends IOException { Busy(final String message) { super(message); } }
  static final class IdleLease implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    IdleLease(final FileChannel channel, final FileLock lock) { this.channel = channel; this.lock = lock; }
    @Override public void close() throws IOException { try { this.lock.release(); } finally { this.channel.close(); } }
  }
  static IdleLease checkIdle(final Path root) throws IOException {
    final Path file = root.resolve(".game-lock");
    final var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    final FileLock held;
    try { held = channel.tryLock(); if(held == null) throw new Busy("Close the managed game before changing this installation."); }
    catch(final OverlappingFileLockException failure) { channel.close(); throw new Busy("Close the managed game before changing this installation."); }
    catch(final IOException | RuntimeException failure) { channel.close(); throw failure; }
    final IdleLease lease = new IdleLease(channel, held);
    try {
      final Path running = root.resolve(RUNNING);
      if(Files.exists(running, LinkOption.NOFOLLOW_LINKS)) {
        if(alive(PackageManifest.readProperties(running))) throw new Busy("Close the managed game before changing this installation.");
        Files.delete(running); InstallStore.forceDirectory(root);
      }
      final Path pending = root.resolve(PENDING);
      if(!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return lease;
      final Properties receipt = PackageManifest.readProperties(pending);
      if(alive(receipt)) throw new Busy("Another launcher is starting the game.");
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
            return new LaunchProcess(info.user(), info.startInstant(), arguments(process, info), info.commandLine(), info.command());
          }, process::isAlive);
        }
      } catch(final RuntimeException unavailable) { throw new IOException("Cannot inspect an interrupted game launch safely. Close surviving game/launcher processes and retry.", unavailable); }
      Files.delete(pending); InstallStore.forceDirectory(root);
      return lease;
    } catch(final IOException | RuntimeException failure) { try { lease.close(); } catch(final IOException cleanup) { failure.addSuppressed(cleanup); } throw failure; }
  }
  // A process may exit between enumeration, metadata inspection and the
  // ownership decision. Missing metadata from an exited process cannot write
  // into the installation; only a surviving token-bound child blocks startup.
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
      || process.commandLine().filter(line -> line.contains(argument)).isPresent()) throw new Busy("A surviving game process is still starting. Wait for it to finish.");
    // Opaque system processes are not evidence of an owned game. Maintenance
    // retains the OS game lease while revoking the pending token; the child
    // validates that token only after obtaining its own game lease.
  }
  private record OwnedProcess(ProcessHandle process, Instant started) {
    boolean alive() throws IOException {
      if(!this.process.isAlive()) return false;
      final var actual = this.process.info().startInstant();
      if(!this.process.isAlive()) return false;
      if(actual.isEmpty()) throw new IOException("Cannot verify the managed game's process identity before stopping it.");
      return actual.get().equals(this.started);
    }
  }
  private static Optional<String[]> arguments(final ProcessHandle process, final ProcessHandle.Info info) {
    if(info.arguments().filter(args -> args.length > 0).isPresent()) return info.arguments();
    // Linux's Java process API omits argv above one page. /proc preserves exact
    // NUL-separated arguments; flattened commandLine cannot distinguish spaces
    // or marker text embedded inside another argument and never authorizes a kill.
    if(!PackageManifest.hostPlatform().equals("linux-x64")) return Optional.empty();
    try(final var input = Files.newInputStream(Path.of("/proc", Long.toString(process.pid()), "cmdline"), LinkOption.NOFOLLOW_LINKS)) {
      final byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
      if(bytes.length == 0 || bytes.length > 2 * 1024 * 1024 || bytes[bytes.length - 1] != 0) return Optional.empty();
      final String value = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
      return Optional.of(value.substring(0, value.length() - 1).split("\0", -1));
    } catch(final IOException | RuntimeException unavailable) { return Optional.empty(); }
  }
  static boolean gameArguments(final Optional<String[]> args, final Path root, final Set<String> tokens) {
    if(args.isEmpty()) return false;
    final List<String> values = Arrays.asList(args.get());
    return values.contains("-Ddefinitive.installRoot=" + root)
      && tokens.stream().anyMatch(token -> values.contains("-Ddefinitive.launchToken=" + token));
  }
  private static boolean launcher(final Optional<String[]> args, final Path root) {
    if(args.isEmpty()) return false;
    final var values = Arrays.asList(args.get());
    final int play = values.indexOf("--play");
    return values.contains(ManagerMain.class.getName()) && play >= 0 && play + 1 < values.size() && values.get(play + 1).equals(root.toString());
  }
  /** Only explicit Repair/Reinstall requests stop a token-bound owned game. */
  static void stopForMaintenance(final Path root, final InstallProgress progress) throws IOException, InterruptedException {
    final Set<String> tokens = new HashSet<>();
    Properties pending = null;
    for(final String name : new String[]{RUNNING, PENDING}) {
      final Path file = root.resolve(name);
      if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
      final Properties receipt;
      try { receipt = PackageManifest.readProperties(file); }
      catch(final IOException failure) { if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue; throw failure; }
      final String token = receipt.getProperty("token", "");
      if(!token.matches("[a-f0-9-]{36}")) throw new IOException("Invalid game launch handoff. No processes were stopped.");
      tokens.add(token);
      if(name.equals(PENDING)) pending = receipt;
    }
    if(tokens.isEmpty()) return;
    final String user = ProcessHandle.current().info().user().orElseThrow(() -> new IOException("Cannot inspect managed game ownership."));
    final Map<Long, OwnedProcess> owned = new LinkedHashMap<>();
    try(final var processes = ProcessHandle.allProcesses()) {
      for(final ProcessHandle process : processes.toList()) {
        if(!process.isAlive() || process.pid() == ProcessHandle.current().pid()) continue;
        final var info = process.info();
        if(!info.user().filter(user::equals).isPresent()) continue;
        final var args = arguments(process, info);
        final boolean game = gameArguments(args, root, tokens);
        final boolean parent = pending != null && Long.toString(process.pid()).equals(pending.getProperty("pid"))
          && info.startInstant().map(Instant::toString).filter(pending.getProperty("start", "")::equals).isPresent()
          && launcher(args, root);
        if(!game && !parent) continue;
        rememberOwned(owned, process, user);
        try(final var descendants = process.descendants()) { for(final ProcessHandle child : descendants.toList()) rememberOwned(owned, child, user); }
      }
    } catch(final RuntimeException failure) { throw new IOException("Could not inspect the managed game before Repair. No installation files were changed.", failure); }
    if(owned.isEmpty()) return;
    progress.phase("Closing the managed game", "Stopping this installation's game before maintenance", 2);
    InstallerLog.write("Maintenance stopping " + owned.size() + " verified game/launcher processes for " + root);
    for(final OwnedProcess process : owned.values()) if(process.alive()) process.process().destroy();
    waitForExit(owned.values(), 2000);
    for(final OwnedProcess process : owned.values()) if(process.alive()) process.process().destroyForcibly();
    waitForExit(owned.values(), 3000);
    for(final OwnedProcess process : owned.values()) if(process.alive()) throw new IOException("The managed game did not stop after termination. No installation files were changed.");
  }
  private static void rememberOwned(final Map<Long, OwnedProcess> owned, final ProcessHandle process, final String user) throws IOException {
    if(!process.isAlive() || process.pid() == ProcessHandle.current().pid()) return;
    final var info = process.info();
    if(!info.user().filter(user::equals).isPresent()) return;
    final var start = info.startInstant();
    if(!process.isAlive()) return;
    if(start.isEmpty()) throw new IOException("Cannot verify a managed game worker's process identity.");
    if(owned.size() >= 256) throw new IOException("Too many managed game workers to stop safely.");
    owned.putIfAbsent(process.pid(), new OwnedProcess(process, start.get()));
  }
  private static void waitForExit(final Collection<OwnedProcess> processes, final int milliseconds) throws IOException, InterruptedException {
    final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(milliseconds);
    while(System.nanoTime() < deadline) {
      boolean alive = false;
      for(final OwnedProcess process : processes) alive |= process.alive();
      if(!alive) return;
      Thread.sleep(20);
    }
  }
  static void finished(final Path root, final String token) throws IOException {
    for(final String name : new String[]{RUNNING, PENDING}) {
      final Path path = root.resolve(name);
      if(Files.exists(path, LinkOption.NOFOLLOW_LINKS) && token.equals(PackageManifest.readProperties(path).getProperty("token"))) Files.delete(path);
    }
    InstallStore.forceDirectory(root);
  }
}
