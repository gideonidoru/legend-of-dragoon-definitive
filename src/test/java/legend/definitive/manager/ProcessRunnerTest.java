package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated OS processes, with no windows, personal data or network access. */
class ProcessRunnerTest {
  @TempDir Path temporary;
  private Path root() throws IOException { return this.temporary.toRealPath(); }
  private static void gone(final long pid) throws Exception {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) Thread.sleep(20);
    assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "Owned descendant survived cancellation: " + pid);
  }
  private static long readPid(final Path file) throws Exception {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while((!Files.isRegularFile(file) || Files.size(file) == 0) && System.nanoTime() < deadline) Thread.sleep(10);
    return Long.parseLong(Files.readString(file).trim());
  }
  private static ProcessBuilder workers(final Path root) {
    return new ProcessBuilder("/bin/bash", "-c", "trap '' TERM; (trap '' TERM; while :; do sleep 1; done) & echo $! > child.pid; wait").directory(root.toFile());
  }

  @Test void timeoutTerminatesTermIgnoringDescendants() throws Exception {
    final Path root = root();
    try(final var process = ProcessRunner.start(workers(root), root.resolve("process.log"))) {
      final long child = readPid(root.resolve("child.pid"));
      try { assertThrows(IOException.class, () -> process.await(Duration.ofMillis(150), () -> { })); gone(child); }
      finally { ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly); }
    }
  }

  @Test void interruptedOperationTerminatesDescendantsAndFinishesDraining() throws Exception {
    final Path root = root();
    final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    final Thread worker = Thread.ofPlatform().start(() -> {
      try(final var running = ProcessRunner.start(workers(root), root.resolve("cancel.log"))) { running.await(); }
      catch(final Throwable thrown) { failure.set(thrown); }
    });
    final long child = readPid(root.resolve("child.pid"));
    try { worker.interrupt(); worker.join(5000); assertFalse(worker.isAlive()); assertInstanceOf(InterruptedException.class, failure.get()); gone(child); }
    finally { ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly); worker.interrupt(); }
  }

  @Test void outputLargerThanPipeAndLogBoundsCannotDeadlock() throws Exception {
    final Path root = root(), log = root.resolve("verbose.log");
    try(final var running = ProcessRunner.start(new ProcessBuilder("/bin/bash", "-c", "head -c 20000000 /dev/zero | tr '\\0' X; echo; echo finished >&2"), log)) {
      assertEquals(0, running.await(Duration.ofSeconds(15), () -> { }));
      assertEquals("finished", running.lastLine());
    }
    assertTrue(Files.size(log) < 17L * 1024 * 1024);
    assertTrue(DiagnosticLogs.tail(log).contains("finished"), "The final diagnostic line must survive the log cap");
  }

  @Test void invalidLinkedLogStopsBeforeSpawningAnyWorker() throws Exception {
    final Path root = root(), protectedFile = root.resolve("protected"), log = root.resolve("linked.log");
    Files.writeString(protectedFile, "retained"); Files.createSymbolicLink(log, protectedFile);
    assertThrows(IOException.class, () -> ProcessRunner.start(new ProcessBuilder("/bin/bash", "-c", "touch spawned").directory(root.toFile()), log));
    assertFalse(Files.exists(root.resolve("spawned"))); assertEquals("retained", Files.readString(protectedFile));
  }

  @Test void supervisorCancelsChildrenWhenOwningJvmIsKilled() throws Exception {
    final Path root = root();
    final String classes = Path.of(ProcessRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + java.io.File.pathSeparator + Path.of(Worker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    final Process owner = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", classes, Worker.class.getName(), root.toString()).redirectErrorStream(true).redirectOutput(root.resolve("owner.log").toFile()).start();
    long child = 0;
    try { child = readPid(root.resolve("child.pid")); owner.destroyForcibly(); assertTrue(owner.waitFor(5, TimeUnit.SECONDS)); gone(child); }
    finally { owner.destroyForcibly(); if(child != 0) ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly); }
  }

  @Test void binaryOutputIsExactAndDoesNotMixStderrOrTruncateAtDiagnosticCap() throws Exception {
    final Path root = root(), data = root.resolve("image.bin"), errors = root.resolve("errors.log");
    try(final var running = ProcessRunner.startBinary(new ProcessBuilder("/bin/bash","-c","head -c 20000000 /dev/zero; echo separate-diagnostic >&2"),data,errors,21000000)) {
      assertEquals(0,running.await(Duration.ofSeconds(15),() -> { }));
    }
    assertEquals(20000000,Files.size(data)); assertEquals("separate-diagnostic\n",Files.readString(errors));
    final var digest = java.security.MessageDigest.getInstance("SHA-256"); final byte[] zero = new byte[8192];
    for(int remaining = 20000000; remaining > 0;) { final int bytes = Math.min(remaining,zero.length); digest.update(zero,0,bytes); remaining -= bytes; }
    assertEquals(java.util.HexFormat.of().formatHex(digest.digest()),PackageManifest.sha256(data));
  }
  @Test void binaryAndStderrBoundsRejectEvenFastSuccessfulHelpers() throws Exception {
    final Path root = root();
    for(final boolean stderr : new boolean[]{false,true}) {
      final Path data = root.resolve("output-"+stderr), errors = root.resolve("errors-"+stderr);
      try(final var running = ProcessRunner.startBinary(new ProcessBuilder("/bin/bash","-c",stderr ? "head -c 2000000 /dev/zero >&2" : "head -c 2000000 /dev/zero"),data,errors,1024*1024)) {
        assertThrows(IOException.class,() -> running.await(Duration.ofSeconds(5),() -> { }));
      }
      assertTrue(Files.size(data) <= 1024*1024); assertTrue(Files.size(errors) <= 1024*1024);
    }
  }
  @Test void binaryHelperCancellationTerminatesDescendantsAndClosesBothOutputs() throws Exception {
    final Path root = root(); final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    final Thread worker = Thread.ofPlatform().start(() -> {
      try(final var running = ProcessRunner.startBinary(workers(root),root.resolve("binary.out"),root.resolve("binary.err"),1024*1024)) { running.await(); }
      catch(final Throwable thrown) { failure.set(thrown); }
    });
    final long child = readPid(root.resolve("child.pid"));
    try { worker.interrupt(); worker.join(6000); assertFalse(worker.isAlive()); assertInstanceOf(InterruptedException.class,failure.get()); gone(child); }
    finally { ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly); worker.interrupt(); }
  }

  @Test void binarySupervisorCancelsChildrenWhenOwningJvmIsKilled() throws Exception {
    final Path root = root();
    final String classes = Path.of(ProcessRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + java.io.File.pathSeparator + Path.of(BinaryWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    final Process owner = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",classes,BinaryWorker.class.getName(),root.toString()).redirectErrorStream(true).redirectOutput(root.resolve("binary-owner.log").toFile()).start();
    long child = 0;
    try { child = readPid(root.resolve("child.pid")); owner.destroyForcibly(); assertTrue(owner.waitFor(5,TimeUnit.SECONDS)); gone(child); }
    finally { owner.destroyForcibly(); if(child != 0) ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly); }
  }
  public static class BinaryWorker {
    public static void main(final String[] arguments) throws Exception {
      final Path root = Path.of(arguments[0]);
      try(final var running = ProcessRunner.startBinary(workers(root),root.resolve("owned.bin"),root.resolve("owned.err"),1024*1024)) { running.await(); }
    }
  }

  public static class Worker {
    public static void main(final String[] arguments) throws Exception {
      final Path root = Path.of(arguments[0]);
      try(final var process = ProcessRunner.start(workers(root), root.resolve("owned.log"))) { process.await(); }
    }
  }
}
