package legend.definitive.manager;

import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** The game process itself owns this lease, even if every launcher process is killed. */
public final class ManagedGameMain {
  private ManagedGameMain() { }
  public static void main(final String[] args) throws Throwable {
    final Path root = Path.of(System.getProperty("definitive.installRoot")).toAbsolutePath().normalize();
    final String token = System.getProperty("definitive.launchToken", "");
    GameLease.validatePending(root, token);
    try(final var channel = FileChannel.open(root.resolve(".game-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        final var lease = channel.tryLock()) {
      if(lease == null) throw new java.io.IOException("Another managed game is running.");
      GameLease.recordRunning(root, token);
      try {
        Class.forName("legend.game.Main").getMethod("main", String[].class).invoke(null, (Object)args);
      } catch(final InvocationTargetException failure) { throw failure.getCause(); }
      finally { GameLease.finished(root, token); }
    }
  }
}
