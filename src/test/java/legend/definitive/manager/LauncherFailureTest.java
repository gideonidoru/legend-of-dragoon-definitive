package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LauncherFailureTest {
  @TempDir Path temporary;
  private InstallStore store() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("installed"));
    store.install(fixtures.pack("fixture", PackageManifest.hostPlatform())); return store;
  }
  @Test void runtimeFailureHasNonzeroExitAndPersistentLog() throws Exception {
    final var store = this.store();
    // Fixture bootstrap has no working Java command; this cannot open an app.
    final var builder = new ProcessBuilder("/bin/bash", store.root().resolve("Play Game.sh").toString()).redirectErrorStream(true);
    final Path tools = Files.createDirectory(this.temporary.resolve("tools")); Files.createSymbolicLink(tools.resolve("dirname"), Path.of("/usr/bin/dirname"));
    builder.environment().put("PATH", tools.toString());
    final var process = builder.start(); assertTrue(process.waitFor(5, TimeUnit.SECONDS));
    assertNotEquals(0, process.exitValue());
    assertTrue(Files.size(store.root().resolve("launcher.log")) > 0);
    assertTrue(new String(process.getInputStream().readAllBytes()).contains("could not start"));
  }
  @Test void failedGameProcessRetainsLogAndReleasesOperationLock() throws Exception {
    final var store = this.store();
    for(final String id : DiscImporter.IDS) Files.write(store.root().resolve("isos/" + id + ".bin"), InstallStoreTest.disc(id));
    // Manifest-valid synthetic JAR is deliberately not a Java program. No game runs.
    assertNotEquals(0, store.play());
    assertTrue(Files.readString(store.gameLog()).contains("legend.game.Main"));
    try(final var lock = store.lock()) { assertTrue(lock.lock().isValid()); }
  }
  @Test void launcherLogsCannotFollowLinks() throws Exception {
    final var store = this.store(); final Path outside = this.temporary.resolve("protected.txt"); Files.writeString(outside, "retained");
    Files.createSymbolicLink(store.root().resolve("launcher.log"), outside);
    final var process = new ProcessBuilder("/bin/bash", store.root().resolve("Play Game.sh").toString()).redirectErrorStream(true).start();
    assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertNotEquals(0, process.exitValue());
    assertEquals("retained", Files.readString(outside));
  }
  @Test void reinstallRepairsDamagedManagedBootstrapWithoutLosingSavedData() throws Exception {
    final var store = this.store(); final Path source = this.temporary.resolve("fixture");
    final var state = store.state(); final Path save = store.data(state).resolve("saves/retained.txt");
    Files.writeString(save, "retained");
    Files.writeString(store.root().resolve("definitive-manager.jar"), "truncated");
    Files.writeString(store.root().resolve("bootstrap-java"), "truncated");
    assertThrows(java.io.IOException.class, store::verifyInstalled);
    store.install(source); store.verifyInstalled();
    assertEquals(PackageManifest.sha256(source.resolve("definitive-manager.jar")), PackageManifest.sha256(store.root().resolve("definitive-manager.jar")));
    assertEquals(PackageManifest.sha256(source.resolve("bootstrap-java")), PackageManifest.sha256(store.root().resolve("bootstrap-java")));
    assertEquals(state, store.state()); assertEquals("retained", Files.readString(save));
  }
  @Test void failedSecondBootstrapPublicationRestoresPriorPairAndActiveState() throws Exception {
    final var store = this.store(); final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path candidate = fixture.pack("next", PackageManifest.hostPlatform());
    // Recompute the candidate inventory after changing both managed components.
    Files.writeString(candidate.resolve("definitive-manager.jar"), "next manager");
    Files.writeString(candidate.resolve("bootstrap-java"), "next bootstrap");
    Files.delete(candidate.resolve(PackageManifest.METADATA)); Files.delete(candidate.resolve(PackageManifest.HASHES));
    ManagerMain.makeManifest(candidate, PackageManifest.hostPlatform(), "fixture-next");
    final var state = store.state(); final Path saved = store.data(state).resolve("saves/retained.txt"); Files.writeString(saved, "retained");
    final String managerHash = PackageManifest.sha256(store.root().resolve("definitive-manager.jar"));
    final String bootstrapHash = PackageManifest.sha256(store.root().resolve("bootstrap-java"));
    final var failing = new InstallStore(store.root(), (source, target) -> {
      if(target.getFileName().toString().equals("bootstrap-java")) throw new java.io.IOException("fixture second publication failure");
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    });
    assertThrows(java.io.IOException.class, () -> failing.install(candidate));
    assertEquals(state, store.state()); assertEquals("retained", Files.readString(saved));
    assertEquals(managerHash, PackageManifest.sha256(store.root().resolve("definitive-manager.jar")));
    assertEquals(bootstrapHash, PackageManifest.sha256(store.root().resolve("bootstrap-java")));
    store.verifyInstalled(); try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
  }
  @Test void reinstallPreservesCustomLauncherAndRepairsMissingLauncher() throws Exception {
    final var store = this.store(); final Path source = this.temporary.resolve("fixture");
    Files.writeString(store.root().resolve("Play Game.sh"), "#!/bin/bash\n# owner customization\n");
    Files.delete(store.root().resolve("Manage Installation.sh"));
    store.install(source); store.verifyInstalled();
    assertTrue(Files.readString(store.root().resolve("Play Game.sh")).contains("owner customization"));
    assertTrue(Files.readString(store.root().resolve("Manage Installation.sh")).contains("could not start"));
  }
}
