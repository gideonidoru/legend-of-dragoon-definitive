// Definitive owned process supervision (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Owns a process group, drains output through an already opened log, and cancels its workers. */
public final class ProcessRunner {
  private ProcessRunner() { }
  private static final long MAX_LOG_BYTES = 16L * 1024 * 1024;
  // Bash job control gives the command its own process group on both supported platforms.
  // The pipe watcher also cancels workers when the JVM is killed without running shutdown hooks.
  private static final String SUPERVISOR = """
    control=$1; shift
    set -m
    exec 3<&0
    exec 4>&2
    exec 2>/dev/null
    "$@" </dev/null 2>&4 &
    child=$!
    printf '%s\\n' "$child" > "$control"
    (while IFS= read -r line <&3; do :; done; kill -TERM -- "-$child" 2>/dev/null || :; sleep 1; kill -KILL -- "-$child" 2>/dev/null || :) &
    watcher=$!
    trap 'kill -TERM -- "-$child" 2>/dev/null || :; kill -TERM -- "-$watcher" 2>/dev/null || :' TERM HUP INT
    wait "$child"
    status=$?
    kill -TERM -- "-$child" 2>/dev/null || :
    kill -TERM -- "-$watcher" 2>/dev/null || :
    wait "$watcher" 2>/dev/null || :
    sleep 0.2
    kill -KILL -- "-$child" 2>/dev/null || :
    exit "$status"
    """;

  public static Running start(final ProcessBuilder builder, final Path log) throws IOException, InterruptedException {
    return start(builder, log, true);
  }

  public static Running start(final ProcessBuilder builder, final Path log, final boolean append) throws IOException, InterruptedException {
    // Open before starting any child: an invalid path must not cause an unlogged operation.
    final OutputStream output = DiagnosticLogs.openLog(log, append);
    Path controlDirectory = null;
    Process process = null;
    try {
      controlDirectory = Files.createTempDirectory(log.toAbsolutePath().getParent(), ".process-");
      final Path control = controlDirectory.resolve("group");
      final List<String> command = new ArrayList<>(List.of("/bin/bash", "-c", SUPERVISOR, "definitive-process", control.toString()));
      command.addAll(builder.command());
      builder.environment().remove("BASH_ENV"); builder.environment().remove("ENV");
      builder.command(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.PIPE).redirectInput(ProcessBuilder.Redirect.PIPE);
      process = builder.start();
      final Running running = new Running(process, output, controlDirectory);
      try {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while(!Files.exists(control) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
        if(!Files.isRegularFile(control, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(control)) throw new IOException("Could not supervise the setup process.");
        final String group = Files.readString(control).trim();
        if(!group.matches("[1-9][0-9]{0,18}")) throw new IOException("Invalid setup process group.");
        running.group = Long.parseLong(group);
        running.rememberChildren();
        return running;
      } catch(final IOException | InterruptedException | RuntimeException failure) { running.close(); throw failure; }
    } catch(final IOException | InterruptedException | RuntimeException failure) {
      if(process == null) {
        output.close();
        if(controlDirectory != null) Files.deleteIfExists(controlDirectory);
      }
      throw failure;
    }
  }

  public static final class Running implements AutoCloseable {
    private final Process process;
    private final Path controlDirectory;
    private final Thread drain;
    private final Map<Long, ProcessHandle> children = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicReference<String> lastLine = new AtomicReference<>("");
    private final AtomicReference<IOException> outputFailure = new AtomicReference<>();
    private long group;
    private boolean closed;

    private Running(final Process process, final OutputStream output, final Path controlDirectory) {
      this.process = process; this.controlDirectory = controlDirectory;
      this.drain = Thread.ofVirtual().name("definitive-process-output").start(() -> {
        long logged = 0; boolean capped = false;
        final byte[] tail = new byte[65536]; int tailPosition = 0, tailCount = 0;
        final StringBuilder line = new StringBuilder();
        try(final InputStream input = process.getInputStream(); output) {
          final byte[] bytes = new byte[8192];
          for(int count; (count = input.read(bytes)) != -1;) {
            final int accepted = (int)Math.min(count, Math.max(0, MAX_LOG_BYTES - logged));
            if(accepted > 0) { output.write(bytes, 0, accepted); logged += accepted; }
            if(accepted < count && !capped) { output.write("\n[Further output omitted from this bounded log.]\n".getBytes(StandardCharsets.UTF_8)); capped = true; }
            final int first = Math.min(count, tail.length - tailPosition);
            System.arraycopy(bytes, 0, tail, tailPosition, first);
            if(first < count) System.arraycopy(bytes, first, tail, 0, count - first);
            tailPosition = (tailPosition + count) % tail.length; tailCount = Math.min(tail.length, tailCount + count);
            // Keep at most one small line for progress; never allocate a full unbounded build line.
            for(int i = 0; i < count; i++) {
              final int value = bytes[i] & 255;
              if(value == '\n' || value == '\r') { if(!line.isEmpty()) { this.lastLine.set(line.toString()); line.setLength(0); } }
              else if(line.length() < 512) line.append(value < 32 ? ' ' : (char)value);
            }
            output.flush();
          }
          if(!line.isEmpty()) this.lastLine.set(line.toString());
          if(capped) {
            output.write("\n[Final output tail from the bounded log]\n".getBytes(StandardCharsets.UTF_8));
            final int start = (tailPosition + tail.length - tailCount) % tail.length;
            final int first = Math.min(tailCount, tail.length - start);
            output.write(tail, start, first);
            if(first < tailCount) output.write(tail, 0, tailCount - first);
          }
        } catch(final IOException failure) { this.outputFailure.compareAndSet(null, failure); }
      });
    }

    public Process process() { return this.process; }
    public String lastLine() { return this.lastLine.get(); }
    private void rememberChildren() {
      try { this.process.descendants().forEach(child -> this.children.put(child.pid(), child)); }
      catch(final RuntimeException restrictedEnumeration) {
        // Sandboxed macOS can deny the process-table syscall. The isolated group and pipe
        // watcher remain the primary containment; enumeration is an additional safeguard.
      }
    }

    public int await() throws IOException, InterruptedException { return await(null, () -> { }); }
    public int await(final Duration timeout, final Runnable tick) throws IOException, InterruptedException {
      final long deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
      try {
        while(this.process.isAlive()) {
          rememberChildren();
          if(this.outputFailure.get() != null) throw new IOException("Could not save process diagnostics.", this.outputFailure.get());
          if(System.nanoTime() >= deadline) throw new IOException("Setup process exceeded its allowed time.");
          this.process.waitFor(50, TimeUnit.MILLISECONDS);
          tick.run();
        }
        return this.process.exitValue();
      } finally { close(); }
    }

    private void signalGroup(final String signal) throws IOException, InterruptedException {
      if(this.group == 0) return;
      final Process signaler = new ProcessBuilder("/bin/kill", "-" + signal, "--", "-" + this.group).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
      if(!signaler.waitFor(1, TimeUnit.SECONDS)) signaler.destroyForcibly();
    }

    @Override public synchronized void close() throws IOException {
      if(this.closed) return;
      this.closed = true;
      boolean interrupted = Thread.interrupted();
      IOException failure = null;
      try {
        rememberChildren();
        this.process.getOutputStream().close(); // The supervisor's watcher treats EOF as cancellation.
        signalGroup("TERM");
        if(!this.process.waitFor(1, TimeUnit.SECONDS)) this.process.destroy();
        final long grace = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while(this.children.values().stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < grace) Thread.sleep(20);
        signalGroup("KILL");
        this.children.values().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if(this.process.isAlive()) this.process.destroyForcibly();
        this.process.waitFor(2, TimeUnit.SECONDS);
        this.drain.join(2000);
        if(this.drain.isAlive()) { this.process.getInputStream().close(); this.drain.interrupt(); this.drain.join(1000); }
        if(this.drain.isAlive()) throw new IOException("Setup output did not close after cancellation.");
        if(this.outputFailure.get() != null) throw new IOException("Could not save process diagnostics.", this.outputFailure.get());
      } catch(final InterruptedException e) {
        interrupted = true;
        this.children.values().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        this.process.destroyForcibly();
      } catch(final IOException e) { failure = e; }
      finally {
        try { Files.deleteIfExists(this.controlDirectory.resolve("group")); Files.deleteIfExists(this.controlDirectory); }
        catch(final IOException e) { if(failure == null) failure = e; else failure.addSuppressed(e); }
        if(interrupted) Thread.currentThread().interrupt();
      }
      if(failure != null) throw failure;
    }
  }
}
