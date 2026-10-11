package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InterruptedIOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated files only; no desktop, game or network. */
class InstallerAuditTest {
  @TempDir Path temporary;
  @Test void updateAndCleanReinstallPreserveReadOnlyPersonalFile() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed")); store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    final Path note = store.data(store.state()).resolve("config/owner.txt"); Files.writeString(note, "read-only owner notes");
    final var permissions = java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"); Files.setPosixFilePermissions(note, permissions);
    final Path next = fixture.pack("new", PackageManifest.hostPlatform()); store.install(next);
    store.install(next, "", null, InstallProgress.NONE, true); store.verifyInstalled();
    final Path preserved = store.data(store.state()).resolve("config/owner.txt");
    assertEquals("read-only owner notes", Files.readString(preserved)); assertEquals(permissions, Files.getPosixFilePermissions(preserved));
  }
  @Test void updateAndRestorePreserveOwnerSnapshotFilenameAtDataRoot() throws Exception {
    temporary = temporary.toRealPath();
    final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed"));
    store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    final Path old = store.data(store.state()); Files.writeString(old.resolve("snapshot-files.properties"), "owner root notes");
    store.install(fixture.pack("new", PackageManifest.hostPlatform()));
    assertEquals("owner root notes", Files.readString(old.resolve("snapshot-files.properties")));
    assertEquals("owner root notes", Files.readString(store.data(store.state()).resolve("snapshot-files.properties")));
    assertTrue(store.hasRestorableVersion());
    Files.writeString(store.data(store.state()).resolve("snapshot-files.properties"), "newer owner notes");
    store.rollback(); store.verifyInstalled();
    assertEquals("owner root notes", Files.readString(store.data(store.state()).resolve("snapshot-files.properties")));
    assertEquals("newer owner notes", Files.readString(store.root().resolve("data").resolve(store.state().getProperty("retainedNewerData")).resolve("snapshot-files.properties")));
  }
  @Test void missingReceiptAndTamperedRootOwnerFileRefuseRestoreWithoutChangingCurrentData() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed")); store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    Files.writeString(store.data(store.state()).resolve("snapshot-files.properties"), "owner root notes");
    store.install(fixture.pack("new", PackageManifest.hostPlatform())); final var current = store.state();
    final Path snapshot = store.root().resolve("snapshots").resolve(current.getProperty("previousSnapshot"));
    final Path receipt = snapshot.getParent().resolve(".inventories").resolve(snapshot.getFileName() + ".properties");
    final byte[] inventory = Files.readAllBytes(receipt); Files.delete(receipt);
    assertFalse(store.hasRestorableVersion()); assertThrows(java.io.IOException.class, store::rollback); assertEquals(current, store.state());
    Files.write(receipt, inventory); Files.writeString(snapshot.resolve("snapshot-files.properties"), "tampered notes");
    assertFalse(store.hasRestorableVersion()); assertThrows(java.io.IOException.class, store::rollback); assertEquals(current, store.state());
    assertEquals("owner root notes", Files.readString(store.data(current).resolve("snapshot-files.properties")));
  }
  @Test void legacySnapshotLayoutStillRestoresAndRemovesOnlyItsGeneratedRootReceipt() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed")); store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"), "old save");
    store.install(fixture.pack("new", PackageManifest.hostPlatform())); final var state = store.state();
    final Path snapshot = store.root().resolve("snapshots").resolve(state.getProperty("previousSnapshot"));
    final Path legacy = snapshot.resolveSibling(snapshot.getFileName().toString().replace("snapshot2-", "snapshot-"));
    Files.move(snapshot, legacy); Files.move(snapshot.getParent().resolve(".inventories").resolve(snapshot.getFileName() + ".properties"), legacy.resolve("snapshot-files.properties"));
    state.setProperty("previousSnapshot", legacy.getFileName().toString()); InstallStore.atomicProperties(store.root().resolve("state.properties"), state);
    assertTrue(store.hasRestorableVersion()); store.rollback(); store.verifyInstalled();
    assertEquals("old save", Files.readString(store.data(store.state()).resolve("saves/owner.txt")));
    assertFalse(Files.exists(store.data(store.state()).resolve("snapshot-files.properties")));
  }
  @Test void cleanReinstallPreservesHistoricalRootOwnerSnapshotFilename() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed")); store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    Files.writeString(store.data(store.state()).resolve("snapshot-files.properties"), "historical notes");
    final Path latest = fixture.pack("new", PackageManifest.hostPlatform()); store.install(latest);
    final String snapshot = store.state().getProperty("previousSnapshot");
    Files.writeString(store.data(store.state()).resolve("snapshot-files.properties"), "current notes");
    store.install(latest, "", null, InstallProgress.NONE, true); store.verifyInstalled();
    final Path data = store.data(store.state());
    assertEquals("current notes", Files.readString(data.resolve("snapshot-files.properties")));
    assertEquals("historical notes", Files.readString(data.resolve("recovered-reinstall/snapshots").resolve(snapshot).resolve("snapshot-files.properties")));
  }
  @Test void linkedSnapshotInventoryDirectoryCannotReceiveWrites() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed")); store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    final var before = store.state(); final Path outside = Files.createDirectory(temporary.resolve("outside"));
    Files.createSymbolicLink(store.root().resolve("snapshots/.inventories"), outside);
    assertThrows(java.io.IOException.class, () -> store.install(fixture.pack("new", PackageManifest.hostPlatform())));
    assertEquals(before, store.state());
    try(final var paths = Files.list(outside)) { assertEquals(0, paths.count()); }
  }
  @Test void updateAndRestorePreserveNestedOwnerFileNamedLikeSnapshotMetadata() throws Exception {
    temporary = temporary.toRealPath();
    final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("installed"));
    store.install(fixture.pack("old", PackageManifest.hostPlatform()));
    final Path note = store.data(store.state()).resolve("config/campaign/snapshot-files.properties");
    Files.createDirectories(note.getParent()); Files.writeString(note, "owner campaign notes");
    store.install(fixture.pack("new", PackageManifest.hostPlatform()));
    assertEquals("owner campaign notes", Files.readString(store.data(store.state()).resolve("config/campaign/snapshot-files.properties")));
    store.rollback(); store.verifyInstalled();
    assertEquals("owner campaign notes", Files.readString(store.data(store.state()).resolve("config/campaign/snapshot-files.properties")));
  }
  @Test void interruptedHashStopsWithoutClearingCancellation() throws Exception {
    Path file = temporary.resolve("large.bin"); Files.write(file, new byte[2*1024*1024]);
    Thread.currentThread().interrupt();
    try { assertThrows(InterruptedIOException.class, () -> PackageManifest.sha256(file)); assertTrue(Thread.currentThread().isInterrupted()); }
    finally { Thread.interrupted(); }
  }
  @Test void interruptedCopyDoesNotCreateDestination() throws Exception {
    Path source = Files.createDirectory(temporary.resolve("source")); Files.write(source.resolve("large.bin"),new byte[2*1024*1024]);
    Path target = temporary.resolve("target"); Thread.currentThread().interrupt();
    try { assertThrows(InterruptedIOException.class, () -> InstallStore.copyTree(source,target)); assertFalse(Files.exists(target)); assertTrue(Thread.currentThread().isInterrupted()); }
    finally { Thread.interrupted(); }
  }
  @Test void copyCannotWriteThroughLinkedDestinationParentOrIntoItself() throws Exception {
    temporary = temporary.toRealPath(); Path source = Files.createDirectory(temporary.resolve("source")); Files.writeString(source.resolve("owner.txt"),"keep");
    Path outside = Files.createDirectory(temporary.resolve("outside")); Path alias = temporary.resolve("alias"); Files.createSymbolicLink(alias,outside);
    assertThrows(java.io.IOException.class, () -> InstallStore.copyTree(source,alias.resolve("copied")));
    assertFalse(Files.exists(outside.resolve("copied")));
    assertThrows(java.io.IOException.class, () -> InstallStore.copyTree(source,source.resolve("nested")));
    assertFalse(Files.exists(source.resolve("nested"))); assertEquals("keep",Files.readString(source.resolve("owner.txt")));
  }
  @Test void slowStartupInspectionRunsOffInterfaceThreadAndKeepsItResponsive() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("startup")); store.install(fixture.pack("startup-pack", PackageManifest.hostPlatform()));
    final var started = new java.util.concurrent.CountDownLatch(1); final var finish = new java.util.concurrent.CountDownLatch(1);
    final var completed = new java.util.concurrent.CountDownLatch(1); final var responsive = new java.util.concurrent.CountDownLatch(1);
    javax.swing.SwingUtilities.invokeAndWait(() -> new ManagerView(null,null,store.root(),() -> { },root -> {
      assertFalse(javax.swing.SwingUtilities.isEventDispatchThread()); started.countDown();
      try { if(!finish.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Fixture inspection timeout"); return ManagerView.inspectInstallation(root); }
      catch(final InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
      finally { completed.countDown(); }
    }));
    try {
      assertTrue(started.await(3,java.util.concurrent.TimeUnit.SECONDS));
      javax.swing.SwingUtilities.invokeLater(responsive::countDown);
      assertTrue(responsive.await(1,java.util.concurrent.TimeUnit.SECONDS),"Slow recovery/inspection must not freeze UI events");
    } finally { finish.countDown(); assertTrue(completed.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
  }

  @Test void discArchiveStagingCannotRaceAnotherInstallationOperation() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("disc-lock")); store.install(fixture.pack("disc-lock-pack",PackageManifest.hostPlatform()));
    final Path zip = temporary.resolve("discs.zip"); Files.writeString(zip,"not reached while busy");
    try(final var lock = store.lock()) {
      assertThrows(GameLease.Busy.class, () -> DiscSources.importSelected(new InstallStore(store.root()),java.util.List.of(zip)));
      try(final var paths = Files.list(store.root())) { assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".disc-sources-"))); }
    }
  }

  @Test void cancellingZipDiscExtractionLeavesStateInputsAndPrivateDataIntact() throws Exception {
    temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
    final var store = new InstallStore(temporary.resolve("disc-cancel")); store.install(fixture.pack("disc-cancel-pack",PackageManifest.hostPlatform()));
    final var before = store.state(); final Path save = store.data(before).resolve("saves/owner.txt"); Files.writeString(save,"save");
    final Path archive = temporary.resolve("disc.zip");
    try(final var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new java.util.zip.ZipEntry("disc.bin")); zip.write(InstallStoreTest.disc("SCUS94491")); zip.closeEntry();
    }
    final String original = PackageManifest.sha256(archive);
    try {
      assertThrows(InterruptedIOException.class,() -> DiscSources.importSelected(store,java.util.List.of(archive),update -> {
        if(update.phase().startsWith("Unpacking ") && update.completed() > 0) Thread.currentThread().interrupt();
      }));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
    assertEquals(before,store.state()); assertEquals("save",Files.readString(save)); assertEquals(original,PackageManifest.sha256(archive));
    try(final var paths = Files.list(store.root())) { assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".disc-sources-"))); }
    try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
  }
  @Test void malformedLauncherArgumentsFailBeforeOpeningAnyInterfaceOrInstallation() {
    for(final String[] args : new String[][]{{"--unknown"},{"--play"},{"--manage","folder","extra"},{"--install","only-one-folder"}}) {
      assertThrows(java.io.IOException.class,() -> ManagerMain.main(args));
    }
  }

}
