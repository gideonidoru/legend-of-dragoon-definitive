package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated Steam library and client stand-ins; never control the owner's Steam. */
class SteamIntegrationTest {
  @TempDir Path temporary;
  private SteamLibrary.Account account;
  private Path install;
  @BeforeEach void prepare() throws Exception {
    this.temporary = this.temporary.toRealPath(); this.account = new SteamLibrary.Account(Files.createDirectories(this.temporary.resolve("userdata/123/config")));
    this.install = Files.createDirectory(this.temporary.resolve("installed")); Files.writeString(this.install.resolve("Play Game.sh"), "fixture launcher");
  }
  private class Client implements SteamIntegration.Client {
    boolean running, refuseStop, refuseStart, startFailure, shutdownFailure;
    int stops, starts, probes;
    @Override public boolean running() { this.probes++; return this.running; }
    @Override public boolean started() { return this.running && !this.refuseStart; }
    @Override public void shutdown() throws IOException {
      this.stops++; assertFalse(Files.exists(account.config().resolve("shortcuts.vdf")), "Library cannot be written before Steam stops");
      if(this.shutdownFailure) throw new IOException("fixture shutdown failure");
      if(!this.refuseStop) this.running = false;
    }
    @Override public void start() throws IOException {
      this.starts++; if(this.startFailure) throw new IOException("fixture restart failure"); this.running = true;
    }
  }
  private String add(final SteamIntegration.Client client, final List<String> phases) throws Exception {
    return SteamIntegration.add(this.account, this.install, update -> phases.add(update.phase()), client, Duration.ZERO, Duration.ZERO);
  }
  @Test void closesSteamWritesOneVerifiedShortcutThenRestarts() throws Exception {
    final var client = new Client(); client.running = true; final var phases = new ArrayList<String>();
    this.add(client, phases); assertEquals(1, client.stops); assertEquals(1, client.starts); assertTrue(client.running);
    SteamLibrary.verifyShortcut(this.account, this.install);
    assertEquals(List.of("Closing Steam", "Adding to your library", "Starting Steam", "Steam shortcut ready"), phases);
  }
  @Test void alreadyStoppedSteamIsStartedAfterTheShortcutIsVerified() throws Exception {
    final var client = new Client(); this.add(client, new ArrayList<>());
    assertEquals(0, client.stops); assertEquals(1, client.starts); SteamLibrary.verifyShortcut(this.account, this.install);
  }
  @Test void repeatedAdditionKeepsExactlyOneShortcut() throws Exception {
    this.add(new Client(), new ArrayList<>()); final byte[] before = Files.readAllBytes(this.account.config().resolve("shortcuts.vdf"));
    this.add(new Client(), new ArrayList<>()); assertArrayEquals(before, Files.readAllBytes(this.account.config().resolve("shortcuts.vdf"))); SteamLibrary.verifyShortcut(this.account, this.install);
  }
  @Test void refusedShutdownNeverWritesTheLibrary() throws Exception {
    final var client = new Client(); client.running = true; client.refuseStop = true;
    assertThrows(IOException.class, () -> this.add(client, new ArrayList<>())); assertEquals(0, client.starts); assertTrue(client.running); assertFalse(Files.exists(this.account.config().resolve("shortcuts.vdf")));
  }
  @Test void failedShutdownNeverWritesTheLibrary() throws Exception {
    final var client = new Client(); client.running = true; client.shutdownFailure = true;
    assertThrows(IOException.class, () -> this.add(client, new ArrayList<>())); assertFalse(Files.exists(this.account.config().resolve("shortcuts.vdf"))); assertTrue(client.running);
  }
  @Test void failedLibraryEditRestartsAPreviouslyRunningClient() throws Exception {
    final var client = new Client() {
      @Override public void shutdown() { this.stops++; this.running = false; }
    };
    client.running = true; final byte[] original = {1,2,3}; Files.write(this.account.config().resolve("shortcuts.vdf"), original);
    assertThrows(IOException.class, () -> this.add(client, new ArrayList<>())); assertArrayEquals(original, Files.readAllBytes(this.account.config().resolve("shortcuts.vdf"))); assertEquals(1, client.starts); assertTrue(client.running);
  }
  @Test void failedEditWaitsForRecoveryAndReportsWhenSteamDoesNotReturn() throws Exception {
    final var client = new Client() { @Override public void shutdown() { this.stops++; this.running = false; } };
    client.running = true; client.refuseStart = true;
    final byte[] original = {1,2,3}; Files.write(this.account.config().resolve("shortcuts.vdf"), original);
    final IOException failure = assertThrows(IOException.class, () -> this.add(client, new ArrayList<>()));
    assertTrue(failure.getMessage().contains("Steam also couldn’t reopen"));
    assertNotNull(failure.getCause()); assertEquals(1, failure.getCause().getSuppressed().length);
    assertTrue(failure.getCause().getSuppressed()[0].getMessage().contains("could not reopen"));
    assertArrayEquals(original, Files.readAllBytes(this.account.config().resolve("shortcuts.vdf"))); assertEquals(1, client.starts);
  }
  @Test void restartFailureRetainsShortcutAndCannotReportSuccess() throws Exception {
    final var client = new Client(); client.startFailure = true; final var phases = new ArrayList<String>();
    assertThrows(IOException.class, () -> this.add(client, phases)); SteamLibrary.verifyShortcut(this.account, this.install); assertFalse(phases.contains("Steam shortcut ready"));
  }
  @Test void restartTimeoutRetainsShortcutAndCannotReportSuccess() throws Exception {
    final var client = new Client(); client.refuseStart = true; final var phases = new ArrayList<String>();
    assertThrows(IOException.class, () -> this.add(client, phases)); SteamLibrary.verifyShortcut(this.account, this.install); assertFalse(phases.contains("Steam shortcut ready"));
  }
  @Test void anotherIntegrationCannotCloseSteamOrEditItsLibrary() throws Exception {
    final var client = new Client(); final Path path = this.temporary.resolve("userdata/.definitive-steam-integration-lock");
    try(final var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE); final var lock = channel.lock()) {
      assertThrows(IOException.class, () -> this.add(client, new ArrayList<>())); assertEquals(0, client.stops); assertEquals(0, client.starts); assertFalse(Files.exists(this.account.config().resolve("shortcuts.vdf")));
    }
    this.add(client, new ArrayList<>()); SteamLibrary.verifyShortcut(this.account, this.install);
  }
  @Test void nativeCommandsUseShutdownArgumentAndRetainTheirOutput() throws Exception {
    final Path helper = this.temporary.resolve("client-fixture.sh"), log = this.install.resolve("steam-integration.log");
    Files.writeString(helper, "#!/bin/sh\nprintf 'arguments: %s\\n' \"$*\"\n"); assertTrue(helper.toFile().setExecutable(true));
    final var client = new SteamIntegration.DesktopClient(List.of(helper.toString()), log); client.shutdown(); client.start();
    final long end = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while((!Files.exists(log) || Files.readString(log).lines().count() < 2) && System.nanoTime() < end) Thread.sleep(20);
    assertEquals(List.of("arguments: -shutdown", "arguments: "), Files.readAllLines(log));
  }
}
