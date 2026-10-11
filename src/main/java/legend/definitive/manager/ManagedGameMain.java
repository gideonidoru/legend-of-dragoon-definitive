package legend.definitive.manager;

import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;

/** The game process itself owns this lease, even if every launcher process is killed. */
public final class ManagedGameMain {
  // Strong roots keep the native lease open through non-daemon workers and all
  // shutdown hooks. Only OS process teardown releases a successfully held lease.
  private static FileChannel lifetimeChannel;
  private static FileLock lifetimeLease;
  private ManagedGameMain() { }
  public static void main(final String[] args) throws Throwable {
    final Path root = Path.of(System.getProperty("definitive.installRoot")).toAbsolutePath().normalize();
    final String token = System.getProperty("definitive.launchToken", "");
    holdForProcessLifetime(root, token);
    try {
      Class.forName("legend.game.Main").getMethod("main", String[].class).invoke(null, (Object)args);
    } catch(final InvocationTargetException failure) { throw failure.getCause(); }
  }
  private static synchronized void holdForProcessLifetime(final Path root, final String token) throws java.io.IOException {
    if(lifetimeChannel != null) throw new java.io.IOException("This process already owns a managed game lease.");
    FileChannel channel = null;
    FileLock lease = null;
    try {
      channel = FileChannel.open(root.resolve(".game-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
      lease = channel.tryLock();
      if(lease == null) throw new java.io.IOException("Another managed game is running.");
      // Maintenance can revoke a stale spawn while holding this same OS lease.
      // Validate inside it, so a delayed child cannot pass an earlier check and
      // start writing after Repair has begun or completed.
      GameLease.validatePending(root, token);
      GameLease.recordRunning(root, token);
      lifetimeLease = lease;
      lifetimeChannel = channel;
    } catch(final java.io.IOException | RuntimeException | Error failure) {
      try { if(lease != null) lease.release(); } catch(final java.io.IOException cleanup) { failure.addSuppressed(cleanup); }
      try { if(channel != null) channel.close(); } catch(final java.io.IOException cleanup) { failure.addSuppressed(cleanup); }
      // The launcher owns its handoff receipt under the operation lock. A
      // rejected/delayed child must not race a newer launch's receipt cleanup.
      throw failure;
    }
  }
}
