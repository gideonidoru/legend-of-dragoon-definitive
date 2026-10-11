package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InterruptedIOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated files only; no desktop, game or network. */
class InstallerAuditTest {
  @TempDir Path temporary;
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
