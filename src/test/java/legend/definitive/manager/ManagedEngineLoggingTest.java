package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManagedEngineLoggingTest {
  @TempDir Path temporary;
  public static class Emitter {
    public static void main(final String[] arguments) {
      // Older engines explicitly select this relative config during initialization.
      System.setProperty("log4j2.configurationFile", "log4j2.xml");
      LogManager.getLogger("managed-fixture").info("Synthetic managed {} diagnostics", arguments[0]);
      LogManager.shutdown();
    }
  }
  @Test void managedGameAndPreparationNeverOpenAnEngineDebugLog() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("install"));
    store.install(fixtures.pack("engine-log", PackageManifest.hostPlatform()));
    final Path workspace = store.prepareLaunch();
    assertFalse(Files.isSymbolicLink(workspace.resolve("log4j2.xml")), "The managed config must not route to the old engine's file appender");
    final Path outside = this.temporary.resolve("external-sentinel"); Files.writeString(outside, "retain external data");
    Files.createSymbolicLink(workspace.resolve("debug.log"), outside);
    final String classpath = java.util.stream.Stream.of(Emitter.class, LogManager.class, LoggerContext.class)
      .map(type -> { try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); } catch(final Exception error) { throw new RuntimeException(error); } })
      .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    for(final String mode : List.of("game", "preparation")) {
      final Path log = workspace.resolve(mode + "-fixture.log");
      final var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classpath, Emitter.class.getName(), mode);
      try(final var running = ProcessRunner.start(new ProcessBuilder(command).directory(workspace.toFile()), log, false)) {
        assertEquals(0, running.await(Duration.ofSeconds(10), () -> { }));
      }
      assertTrue(Files.readString(log).contains("Synthetic managed " + mode + " diagnostics"));
      assertEquals("retain external data", Files.readString(outside));
    }
  }
}
