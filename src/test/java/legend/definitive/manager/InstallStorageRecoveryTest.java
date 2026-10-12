package legend.definitive.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InstallStorageRecoveryTest {
  @TempDir Path temporary;
  @BeforeEach void physicalTemporary() throws Exception { this.temporary = this.temporary.toRealPath(); }

  private InstallStore installed() throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("installation"));
    store.install(fixture.pack("fixture", PackageManifest.hostPlatform()));
    return store;
  }

  @Test void uninstallPreservesUnknownRecoveryPrefixInsteadOfTreatingItsNameAsOwnership() throws Exception {
    final var store = this.installed();
    final Path unknown = Files.createDirectory(store.root().resolve("release-recovery-" + UUID.randomUUID()));
    final Path personal = unknown.resolve("owner-save.dat"); Files.writeString(personal, "owner recovery bytes");
    store.uninstall(false, InstallProgress.NONE);
    assertTrue(Files.isRegularFile(personal), "A matching recovery prefix is not an ownership receipt");
    assertEquals("owner recovery bytes", Files.readString(personal));
    assertTrue(Boolean.parseBoolean(store.state().getProperty("uninstalled")));
  }

  @Test void verifiedStageAdoptsSameDirectoryWithoutCopyingPayloadAndPreservesOwnerPrefixes() throws Exception {
    final var store = this.installed();
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path pack = fixture.pack("next", PackageManifest.hostPlatform());
    final Path unknown = Files.createDirectory(store.root().resolve(".files-owner"));
    Files.writeString(unknown.resolve("save.txt"), "owner bytes");
    store.withOperation(() -> {
      try(final var stage = InstallStage.create(store.root())) {
        InstallStore.copyTree(pack, stage.path());
        final Object before = Files.readAttributes(stage.path(), java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        store.installVerifiedStage(stage, "", null, InstallProgress.NONE, true, null);
        final Path release = store.root().resolve("releases").resolve(store.state().getProperty("version"));
        assertEquals(before, Files.readAttributes(release, java.nio.file.attribute.BasicFileAttributes.class).fileKey());
        PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
        try(final var files = Files.walk(store.data(store.state()))) {
          assertTrue(files.anyMatch(path -> { try { return Files.isRegularFile(path) && Files.readString(path).equals("owner bytes"); } catch(final Exception ignored) { return false; } }));
        }
      }
      return null;
    });
  }

  @Test void ownedInterruptedScratchReclaimsButUnknownAndModifiedLegacyScratchSurvive() throws Exception {
    final var store = this.installed();
    final InstallStage abandoned = store.withOperation(() -> InstallStage.create(store.root()));
    Files.writeString(abandoned.path().resolve("partial-download"), "temporary");
    final Path unknown = Files.createDirectory(store.root().resolve(".package-12345"));
    Files.writeString(unknown.resolve("owner-save"), "personal");
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path legacy = Files.createDirectory(store.root().resolve(".files-54321"));
    InstallStore.copyTree(fixture.pack("legacy", PackageManifest.hostPlatform()), legacy);
    try(final var operation = store.operation()) {
      assertFalse(Files.exists(abandoned.path()));
      assertFalse(Files.exists(legacy));
      assertEquals("personal", Files.readString(unknown.resolve("owner-save")));
    }
  }

  @Test void fileDeliveryBudgetsOnePayloadAndRetainsFullPreparationSafety() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("budget"));
    final long payload = 4473783807L;
    assertEquals(payload + 7L * 1024 * 1024 * 1024, store.requiredFileDeliveryBytes(payload, "alpha-0123456789abcdef"));
    assertTrue(store.requiredFileDeliveryBytes(payload, "alpha-0123456789abcdef") > 9L * 1024 * 1024 * 1024);
    assertThrows(java.io.IOException.class, () -> store.requiredFileDeliveryBytes(-1, ""));
  }

  @Test void boundDownloadSurvivesRestartAndRepairButReinstallRequiresFreshDelivery() throws Exception {
    final var store = this.installed();
    final String digest = "a".repeat(64);
    final Path fresh = store.withOperation(() -> {
      try(final var stage = InstallStage.open(store.root(), digest, true)) {
        Files.writeString(stage.path().resolve("completed"), "downloaded fresh"); return stage.path();
      }
    });
    final var reopened = new InstallStore(store.root());
    reopened.withOperation(() -> {
      try(final var stage = InstallStage.open(reopened.root(), digest, false)) {
        assertEquals(fresh, stage.path()); assertEquals("downloaded fresh", Files.readString(stage.path().resolve("completed")));
      }
      return null;
    });
    reopened.uninstall(false, InstallProgress.NONE); assertFalse(Files.exists(fresh));
    final Path repair = reopened.withOperation(() -> {
      try(final var stage = InstallStage.open(reopened.root(), digest, false)) { return stage.path(); }
    });
    reopened.withOperation(() -> {
      try(final var stage = InstallStage.open(reopened.root(), digest, true)) { assertNotEquals(repair, stage.path()); assertFalse(Files.exists(repair)); }
      return null;
    });
  }


  @Test void sameCandidatePreparedFullReinstallStillReservesFutureExtraction() throws Exception {
    final var store = this.installed();
    final String id = store.state().getProperty("version");
    final Path workspace = store.prepareLaunch();
    Files.writeString(workspace.resolve("files/version"), "fixture prepared");
    assertTrue(store.discsPrepared());
    assertEquals(6L * 1024 * 1024 * 1024, store.requiredFileDeliveryBytes(0, id, true) - store.requiredFileDeliveryBytes(0, id, false));
  }

  @Test void successfulSameVersionRepairDisposesBoundStageEvenWhenReleaseDidNotMove() throws Exception {
    final var store = this.installed();
    final Path release = store.root().resolve("releases").resolve(store.state().getProperty("version"));
    final Path stagePath = store.withOperation(() -> {
      try(final var stage = InstallStage.open(store.root(), "b".repeat(64), false)) {
        InstallStore.copyTree(release, stage.path());
        final Path path = stage.path();
        assertEquals("This version is already installed.", store.installVerifiedStage(stage, "", null, InstallProgress.NONE, false, null));
        return path;
      }
    });
    assertFalse(Files.exists(stagePath), "Successful verified Repair must not leave a second full payload");
    assertFalse(Files.exists(store.root().resolve(stagePath.getFileName() + ".owned-stage.properties")));
  }

  @Test void olderPersonalPreservationCannotConsumePreparationHeadroomBeforeActivation() throws Exception {
    final var original = this.installed();
    final var old = original.state();
    final Path snapshot = Files.createDirectory(original.root().resolve("snapshots/owner-older"));
    final Path personal = Files.writeString(snapshot.resolve("older-save.txt"), "older distinct owner bytes");
    final Path owner = Files.writeString(original.root().resolve("owner-config.txt"), "root settings");
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path next = fixture.pack("headroom-next", PackageManifest.hostPlatform());
    final var store = new InstallStore(original.root(), path -> {
      try(final var entries = Files.list(path)) {
        return entries.anyMatch(entry -> entry.getFileName().toString().startsWith(".reinstall-data-")) ? 6L * 1024 * 1024 * 1024 : Long.MAX_VALUE;
      }
    });
    final var failure = assertThrows(java.io.IOException.class, () -> store.install(next, "", null, InstallProgress.NONE, true, null));
    assertTrue(failure.getMessage().contains("7.00 GiB") && failure.getMessage().contains("6.00 GiB"));
    assertEquals(old, store.state(), "Replacement may not activate without post-preservation headroom");
    assertEquals("older distinct owner bytes", Files.readString(personal));
    assertEquals("root settings", Files.readString(owner));
    original.verifyInstalled();
    assertFalse(Files.exists(original.root().resolve(CleanReinstall.JOURNAL)));
  }

  @Test void olderPersonalCopyStopsBeforeAllocatingIntoPreparationReserve() throws Exception {
    final var original = this.installed();
    final var before = original.state();
    final Path snapshot = Files.createDirectory(original.root().resolve("snapshots/owner-distinct"));
    final Path oldSave = Files.writeString(snapshot.resolve("older-save.dat"), "older private save");
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path next = fixture.pack("progressive-headroom-next", PackageManifest.hostPlatform());
    final var observedPreservedFiles = new java.util.concurrent.atomic.AtomicInteger(-1);
    final var store = new InstallStore(original.root(), root -> {
      try(final var entries = Files.list(root)) {
        final Path preservation = entries.filter(path -> path.getFileName().toString().startsWith(".reinstall-data-")).findFirst().orElse(null);
        if(preservation == null) return Long.MAX_VALUE;
        try(final var files = Files.walk(preservation)) { observedPreservedFiles.set((int)files.filter(Files::isRegularFile).count()); }
        return 2L * 1024 * 1024 * 1024;
      }
    });
    final var failure = assertThrows(java.io.IOException.class, () -> store.install(next, "", null, InstallProgress.NONE, true, null));
    assertEquals(0, observedPreservedFiles.get(), "Stop before copying older owner data into the preparation reserve");
    assertTrue(failure.getMessage().contains("7.00 GiB") && failure.getMessage().contains("2.00 GiB"));
    assertEquals(before, store.state());
    assertEquals("older private save", Files.readString(oldSave));
    original.verifyInstalled();
  }
}
