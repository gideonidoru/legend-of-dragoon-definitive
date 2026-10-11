package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageHardeningTest {
  @Test void opaqueUnrelatedProcessesCannotBlockStaleLaunchRecovery() throws Exception {
    final var unknown = new GameLease.LaunchProcess(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(UUID.randomUUID().toString(), "fixture-user", Instant.now(), () -> unknown, () -> true));
  }
  @Test void terminationRequiresExactRootAndTokenArgumentsIncludingSpacesAndPrefixPaths() {
    final Path root = Path.of("/tmp/game"); final String token = UUID.randomUUID().toString();
    assertTrue(GameLease.gameArguments(Optional.of(new String[]{"-Ddefinitive.installRoot=" + root, "-Ddefinitive.launchToken=" + token}), root, Set.of(token)));
    for(final String other : new String[]{"/tmp/game -copy", "/tmp/game-other", "/tmp/game/child"}) {
      assertFalse(GameLease.gameArguments(Optional.of(new String[]{"-Ddefinitive.installRoot=" + other, "-Ddefinitive.launchToken=" + token}), root, Set.of(token)));
    }
    assertFalse(GameLease.gameArguments(Optional.of(new String[]{"-Dother=-Ddefinitive.installRoot=" + root, "-Ddefinitive.launchToken=" + token}), root, Set.of(token)));
    assertFalse(GameLease.gameArguments(Optional.of(new String[]{"-Ddefinitive.installRoot=" + root, "-Dother=-Ddefinitive.launchToken=" + token}), root, Set.of(token)));
    assertFalse(GameLease.gameArguments(Optional.empty(), root, Set.of(token)));
    final Path spaced = Path.of("/tmp/game with spaces");
    assertTrue(GameLease.gameArguments(Optional.of(new String[]{"-Ddefinitive.installRoot=" + spaced, "-Ddefinitive.launchToken=" + token}), spaced, Set.of(token)));
  }
  @TempDir Path temporary;
  private InstallStoreTest fixtures;
  @BeforeEach void prepare() throws Exception { this.temporary = this.temporary.toRealPath(); this.fixtures = new InstallStoreTest(); this.fixtures.temporary = this.temporary; }
  private InstallStore install(final String name) throws Exception { final var store = new InstallStore(this.temporary.resolve(name)); store.install(this.fixtures.pack(name + "-pack", PackageManifest.hostPlatform())); return store; }
  private List<Path> discs(final String prefix) throws Exception {
    final var paths = new ArrayList<Path>();
    for(final String id : DiscImporter.IDS) { final Path path = this.temporary.resolve(prefix + id + ".bin"); Files.write(path, InstallStoreTest.disc(id)); paths.add(path); }
    return paths;
  }
  private Process start(final String mode, final Path root, final List<Path> inputs) throws Exception {
    final var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), StorageCrashMain.class.getName(), mode, root.toString()));
    for(final Path path : inputs) command.add(path.toString());
    return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(this.temporary.resolve(mode + "-child.log").toFile()).start();
  }
  @Test void preparationRejectsLinkedOutputBeforeStartingAndKeepsExternalBytes() throws Exception {
    final var store = install("linked-log"); DiscImporter.importDiscs(store, discs("log-"));
    final Path external = this.temporary.resolve("protected-save"); Files.writeString(external, "preserve owner bytes");
    Files.createSymbolicLink(store.prepareLaunch().resolve("preparation.log"), external);
    assertThrows(java.io.IOException.class, store::prepareDiscs); assertEquals("preserve owner bytes", Files.readString(external));
    assertFalse(store.discsPrepared());
  }
  @Test void fullscreenRollbackRestoresNativeSettingsAndNewerStateIsRetained() throws Exception {
    final var store = install("fullscreen"); store.setFullscreen(false); InstallStore.configureFullscreen(store.data(store.state()), false);
    store.install(this.fixtures.pack("fullscreen-v2", PackageManifest.hostPlatform())); store.setPreferences(true, true, true);
    final Path newer = store.data(store.state()); store.rollback();
    assertEquals("false", store.state().getProperty("fullscreen")); assertEquals("false", store.state().getProperty("legacyTextures"));
    final Path restored = store.data(store.state()); InstallStore.configureFullscreen(restored, Boolean.parseBoolean(store.state().getProperty("fullscreen")));
    final byte[] config = Files.readAllBytes(restored.resolve("config.dcnf")); assertEquals(0, config[config.length - 1]); assertTrue(Files.isDirectory(newer));
  }
  @Test void fullMaintenanceLockIsReentrantAndBlocksOtherStores() throws Exception {
    final var store = install("maintenance"); final var concurrent = new InstallStore(store.root());
    store.withOperation(() -> {
      store.setPreferences(false, true, false);
      assertThrows(java.io.IOException.class, concurrent::lock);
      assertEquals("original", store.state().getProperty("artwork")); assertEquals("true", store.state().getProperty("legacyTextures")); assertEquals("false", store.state().getProperty("fullscreen"));
      store.uninstall(false, InstallProgress.NONE); return null;
    });
    assertTrue(Boolean.parseBoolean(store.state().getProperty("uninstalled")));
  }
  @Test void killedBootstrapPublicationReplaysCompleteVerifiedPair() throws Exception {
    final var store = install("bootstrap"); final Properties old = store.state(); final Path candidate = this.fixtures.pack("bootstrap-v2", PackageManifest.hostPlatform());
    final Process child = start("bootstrap", store.root(), List.of(candidate)); assertTrue(child.waitFor(15, TimeUnit.SECONDS)); assertEquals(91, child.exitValue());
    assertTrue(Files.exists(store.root().resolve(".bootstrap-transaction.properties")));
    final var reopened = new InstallStore(store.root()); reopened.verifyInstalled(); assertEquals(old, reopened.state());
    assertFalse(Files.exists(store.root().resolve(".bootstrap-transaction.properties")));
    assertEquals(PackageManifest.sha256(candidate.resolve("bootstrap-java")), PackageManifest.sha256(store.root().resolve("bootstrap-java")));
  }
  @Test void killedDiscPublicationRecoversVerifiedStagingBeforeRecreatingIsos() throws Exception {
    final var store = install("discs"); final var inputs = discs("crash-");
    final Process child = start("discs", store.root(), inputs); assertTrue(child.waitFor(15, TimeUnit.SECONDS)); assertEquals(92, child.exitValue());
    assertFalse(Files.exists(store.root().resolve("isos"))); assertTrue(Files.exists(store.root().resolve(".disc-transaction.properties")));
    final var reopened = new InstallStore(store.root()); DiscImporter.validateSet(reopened.root().resolve("isos")); assertFalse(DiscImporter.existing(reopened, InstallProgress.NONE).changed());
    assertFalse(Files.exists(store.root().resolve(".disc-transaction.properties")));
  }
  @Test void killedExactReleaseRepairReplaysVerifiedCandidateWithoutChangingData() throws Exception {
    final var store = install("repair"); final Properties state = store.state(); final Path save = store.data(state).resolve("saves/owner"); Files.writeString(save, "preserved");
    final Path release = store.root().resolve("releases").resolve(state.getProperty("version")); Files.writeString(release.resolve("lod-game-test.jar"), "corrupted");
    final Process child = start("repair", store.root(), List.of(this.temporary.resolve("repair-pack"))); assertTrue(child.waitFor(15, TimeUnit.SECONDS)); assertEquals(95, child.exitValue());
    assertFalse(Files.exists(release)); assertTrue(Files.exists(store.root().resolve(".release-repair-transaction.properties")));
    final var reopened = new InstallStore(store.root()); reopened.verifyInstalled(); assertEquals(state, reopened.state()); assertEquals("preserved", Files.readString(save));
    assertFalse(Files.exists(store.root().resolve(".release-repair-transaction.properties")));
  }
  @Test void missingDiscStageRestoresOnlyTheTokenOwnedPriorBackup() throws Exception {
    final var store = install("disc-backup"); DiscImporter.importDiscs(store, discs("prior-")); final Properties prior = DiscImporter.currentHashes(store);
    final var replacement = discs("replacement-"); final byte[] bytes = Files.readAllBytes(replacement.getFirst()); bytes[0] = 1; Files.write(replacement.getFirst(), bytes);
    final Process child = start("replace-discs", store.root(), replacement); assertTrue(child.waitFor(15, TimeUnit.SECONDS)); assertEquals(92, child.exitValue());
    final Properties transaction = PackageManifest.readProperties(store.root().resolve(".disc-transaction.properties"));
    InstallStore.deleteOwnedTree(store.root().resolve(transaction.getProperty("staged")));
    final var reopened = new InstallStore(store.root()); assertEquals(prior, DiscImporter.currentHashes(reopened));
    assertFalse(Files.exists(reopened.root().resolve("isos/.definitive-disc-backup"))); assertFalse(Files.exists(reopened.root().resolve(".disc-transaction.properties")));
  }
  @Test void missingRepairStageCannotAdoptAnOrphanBackupDirectory() throws Exception {
    final var store = install("orphan-repair"); final Path release = store.root().resolve("releases").resolve(store.state().getProperty("version"));
    Files.writeString(release.resolve("lod-game-test.jar"), "corrupted");
    final Process child = start("repair", store.root(), List.of(this.temporary.resolve("orphan-repair-pack"))); assertTrue(child.waitFor(15, TimeUnit.SECONDS)); assertEquals(95, child.exitValue());
    final Properties transaction = PackageManifest.readProperties(store.root().resolve(".release-repair-transaction.properties"));
    InstallStore.deleteOwnedTree(store.root().resolve(transaction.getProperty("staged")));
    final Path backup = store.root().resolve(transaction.getProperty("backup")); Files.delete(backup.resolve(".definitive-release-backup")); Files.writeString(backup.resolve("owner-sentinel"), "keep");
    assertThrows(java.io.IOException.class, () -> new InstallStore(store.root())); assertFalse(Files.exists(release)); assertEquals("keep", Files.readString(backup.resolve("owner-sentinel")));
    assertTrue(Files.exists(store.root().resolve(".release-repair-transaction.properties")));
  }
  @Test void malformedDiscJournalCannotTouchAnExternalDirectory() throws Exception {
    final var store = install("bad-journal"); final Path outside = this.temporary.resolve("external"); Files.createDirectory(outside); Files.writeString(outside.resolve("save"), "keep");
    final Properties journal = new Properties(); journal.setProperty("staged", "../external"); journal.setProperty("backup", "disc-recovery-" + UUID.randomUUID());
    InstallStore.atomicProperties(store.root().resolve(".disc-transaction.properties"), journal);
    assertThrows(java.io.IOException.class, () -> new InstallStore(store.root())); assertEquals("keep", Files.readString(outside.resolve("save")));
  }
  @Test void damagedStateRestoresLastGoodReferencesAndPreservesNewerData() throws Exception {
    final var store = install("state"); Files.writeString(store.data(store.state()).resolve("saves/owner"), "old-save");
    store.install(this.fixtures.pack("state-v2", PackageManifest.hostPlatform())); final Path newer = store.data(store.state()); Files.writeString(newer.resolve("saves/owner"), "newer-save");
    Files.writeString(store.root().resolve("state.properties"), "broken properties"); final Properties recovered = store.state();
    assertEquals("old-save", Files.readString(store.data(recovered).resolve("saves/owner"))); assertEquals("newer-save", Files.readString(newer.resolve("saves/owner"))); store.verifyInstalled();
    assertEquals("true", recovered.getProperty("recoveryPending")); assertTrue(recovered.getProperty("recoveryPreservedLocations").contains(newer.toString()));
    assertTrue(assertThrows(java.io.IOException.class, store::play).getMessage().contains("explicitly confirm"));
    assertEquals("true", new InstallStore(store.root()).state().getProperty("recoveryPending"));
    store.acknowledgeRecovery(); assertEquals("false", store.state().getProperty("recoveryPending"));
    assertTrue(store.state().getProperty("recoveryPreservedLocations").contains(newer.toString()));
  }
  @Test void retentionPreservesPrivateDataAndFailsClosedOnUnknownOwnership() throws Exception {
    final var store = install("retention"); store.install(this.fixtures.pack("retention-v2", PackageManifest.hostPlatform())); store.install(this.fixtures.pack("retention-v3", PackageManifest.hostPlatform()));
    final var inventory = store.retentionInventory(); assertTrue(inventory.complete()); assertTrue(inventory.items().stream().filter(item -> item.path().startsWith("data/") || item.path().startsWith("snapshots/")).noneMatch(InstallStore.RetainedItem::removable));
    final String privatePath = inventory.items().stream().filter(item -> item.path().startsWith("data/")).findFirst().orElseThrow().path(); assertThrows(java.io.IOException.class, () -> store.pruneRetained(List.of(privatePath), InstallProgress.NONE));
    Files.createDirectory(store.root().resolve("releases/unknown-owner")); assertFalse(store.retentionInventory().complete());
    assertThrows(java.io.IOException.class, () -> store.pruneRetained(List.of(), InstallProgress.NONE));
  }
  @Test void spaceEstimateIncludesTwoCopiesOfCustomPrivateData() throws Exception {
    final var store = install("space"); final Path candidate = this.fixtures.pack("space-v2", PackageManifest.hostPlatform()); final long before = store.requiredInstallBytes(candidate);
    final byte[] custom = new byte[1024 * 1024]; Files.write(store.data(store.state()).resolve("texture-packs/custom.bin"), custom);
    assertEquals(2L * custom.length, store.requiredInstallBytes(candidate) - before);
  }
  private Path leasePackage() throws Exception {
    return this.leasePackage(false);
  }
  @Test void maintenanceRetainsGameLeaseAndReleasesBothLocksOnFailure() throws Exception {
    final var store = install("maintenance-game-lease");
    assertThrows(java.io.IOException.class, () -> store.withMaintenance(InstallProgress.NONE, () -> {
      try(final var channel = java.nio.channels.FileChannel.open(store.root().resolve(".game-lock"), StandardOpenOption.WRITE)) {
        assertThrows(java.nio.channels.OverlappingFileLockException.class, channel::tryLock);
      }
      throw new java.io.IOException("Injected maintenance failure");
    }));
    try(final var operation = new InstallStore(store.root()).lock()) { assertTrue(operation.lock().isValid()); }
  }
  @Test void normalGameExitWithStaleReceiptsRepairsAutomatically() throws Exception {
    final var store = install("normal-exit-repair"); final Properties before = store.state();
    final Path sentinel = store.data(before).resolve("saves/normal-exit-owner"); Files.writeString(sentinel, "preserve normal quit data");
    final Process game = start("normal-game-exit", store.root(), List.of()); assertTrue(game.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, game.exitValue());
    assertTrue(Files.exists(store.root().resolve(GameLease.PENDING))); assertTrue(Files.exists(store.root().resolve(GameLease.RUNNING)));
    store.withMaintenance(InstallProgress.NONE, () -> { store.verifyInstalled(); return null; });
    assertFalse(Files.exists(store.root().resolve(GameLease.PENDING))); assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)));
    assertEquals(before.getProperty("data"), store.state().getProperty("data")); assertEquals("preserve normal quit data", Files.readString(sentinel));
  }
  @Test void repairStopsPublishedLegacyChildPausedAfterItsOldTokenValidation() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("legacy-paused-repair")); final Path pack = leasePackage();
    final String original;
    try(final var input = StorageHardeningTest.class.getResourceAsStream("managed-game-main-5c.java")) { assertNotNull(input); original = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    final String gate = "GameLease.validatePending(root, token);\n    Files.writeString(root.resolve(\"fixture-legacy-validated\"), \"legacy entry validated before the game lease\");\n    while(!Files.exists(root.resolve(\"fixture-legacy-continue\"))) Thread.sleep(20);";
    final Path source = this.temporary.resolve("legacy-source/legend/definitive/manager/ManagedGameMain.java"); Files.createDirectories(source.getParent()); Files.writeString(source, original.replace("GameLease.validatePending(root, token);", gate));
    assertNotEquals(original, Files.readString(source)); final Path classes = this.temporary.resolve("legacy-classes"); Files.createDirectories(classes);
    assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), source.toString()));
    final Path manager = pack.resolve("definitive-manager.jar"), replacement = pack.resolve("legacy-manager.jar");
    try(final var old = new JarFile(manager.toFile()); final var changed = new JarOutputStream(Files.newOutputStream(replacement))) {
      final var entries = old.entries(); while(entries.hasMoreElements()) {
        final var entry = entries.nextElement(); changed.putNextEntry(new JarEntry(entry.getName()));
        if(entry.getName().equals("legend/definitive/manager/ManagedGameMain.class")) changed.write(Files.readAllBytes(classes.resolve(entry.getName())));
        else try(final var input = old.getInputStream(entry)) { input.transferTo(changed); }
        changed.closeEntry();
      }
    }
    Files.move(replacement, manager, StandardCopyOption.REPLACE_EXISTING); Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES)); ManagerMain.makeManifest(pack, PackageManifest.hostPlatform(), "legacy-fixture"); store.install(pack);
    final Process parent = start("handoff", store.root(), List.of()); assertTrue(parent.waitFor(10, TimeUnit.SECONDS)); assertEquals(94, parent.exitValue());
    final ProcessHandle child = ProcessHandle.of(Long.parseLong(Files.readString(store.root().resolve("fixture-child.pid")))).orElseThrow();
    try {
      Files.writeString(store.root().resolve("fixture-spawn-gap.continue"), "continue into the old pre-lease validation boundary");
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-legacy-validated")) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-legacy-validated")), Files.readString(store.root().resolve("handoff-child.log")));
      assertTrue(child.isAlive()); assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)));
      if(PackageManifest.hostPlatform().equals("linux-x64")) assertTrue(child.info().arguments().isEmpty(), "The 16K legacy command must exercise exact /proc argv recovery");
      store.withMaintenance(InstallProgress.NONE, () -> { assertFalse(child.isAlive()); store.verifyInstalled(); return null; });
      assertFalse(Files.exists(store.root().resolve("fixture-game.started"))); assertFalse(Files.exists(store.root().resolve(GameLease.PENDING)));
    } finally { if(child.isAlive()) { child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS); } }
  }
  @Test void explicitRepairStopsOnlyOwnedGameAndKeepsPersonalData() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("repair-owned-game")); store.install(leasePackage()); DiscImporter.importDiscs(store, discs("repair-owned-"));
    final Path workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"), "5");
    final Properties before = store.state();
    final Path sentinel = store.data(before).resolve("saves/repair-owner"); Files.writeString(sentinel, "retain personal data");
    final Process unrelated = new ProcessBuilder("/bin/sleep", "60").start();
    final Process outer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true", "-Ddefinitive.installerLog=" + this.temporary.resolve("installer.log"), "-jar", store.root().resolve("definitive-manager.jar").toString(), "--play", store.root().toString()).redirectErrorStream(true).redirectOutput(this.temporary.resolve("repair-router-output.log").toFile()).start();
    try {
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-game.started")) && outer.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-game.started")), Files.readString(this.temporary.resolve("repair-router-output.log")));
      final Properties receipt = PackageManifest.readProperties(store.root().resolve(GameLease.RUNNING));
      final ProcessHandle game = ProcessHandle.of(Long.parseLong(receipt.getProperty("pid"))).orElseThrow();
      assertTrue(game.isAlive());
      final Set<String> phases = new HashSet<>();
      store.withMaintenance(update -> phases.add(update.phase()), () -> { store.verifyInstalled(); return null; });
      assertFalse(game.isAlive()); assertTrue(outer.waitFor(10, TimeUnit.SECONDS)); assertTrue(unrelated.isAlive());
      assertTrue(phases.contains("Closing the managed game"));
      assertEquals(before.getProperty("version"), store.state().getProperty("version"));
      assertEquals(before.getProperty("data"), store.state().getProperty("data"));
      assertEquals("retain personal data", Files.readString(sentinel)); assertEquals(workspace, store.prepareLaunch());
      assertFalse(Files.exists(store.root().resolve(GameLease.PENDING))); assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)));
    } finally { outer.destroyForcibly(); outer.waitFor(10, TimeUnit.SECONDS); unrelated.destroyForcibly(); unrelated.waitFor(10, TimeUnit.SECONDS); }
  }
  private Path leasePackage(final boolean returningWorker) throws Exception {
    final Path pack = this.fixtures.pack("lease-package", PackageManifest.hostPlatform());
    final Path source = this.temporary.resolve("fake-source/legend/game/Main.java"); Files.createDirectories(source.getParent());
    final String lifetime = returningWorker ? "new Thread(() -> { try { while(true) { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty(\"definitive.installRoot\"), \"fixture-worker.progress\"), Long.toString(System.nanoTime())); Thread.sleep(30); } } catch(Exception failure) { throw new RuntimeException(failure); } }, \"synthetic-unpack-worker\").start();" : "Thread.sleep(60000);";
    Files.writeString(source, "package legend.game; public class Main { public static void main(String[] args) throws Exception { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty(\"definitive.installRoot\"),\"fixture-game.started\"),\"synthetic headless helper\"); " + lifetime + " } }");
    final Path classes = this.temporary.resolve("fake-classes"); Files.createDirectories(classes);
    assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    try(final var jar = new JarOutputStream(Files.newOutputStream(pack.resolve("lod-game-test.jar")))) { jar.putNextEntry(new JarEntry("legend/game/Main.class")); jar.write(Files.readAllBytes(classes.resolve("legend/game/Main.class"))); jar.closeEntry(); }
    final Path output = Path.of(InstallStore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    final var manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version", "1.0"); manifest.getMainAttributes().putValue("Main-Class", ManagerMain.class.getName());
    try(final var jar = new JarOutputStream(Files.newOutputStream(pack.resolve("definitive-manager.jar")), manifest); final var paths = Files.walk(output.resolve("legend/definitive/manager"))) {
      for(final Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) { jar.putNextEntry(new JarEntry(output.relativize(path).toString())); jar.write(Files.readAllBytes(path)); jar.closeEntry(); }
      jar.putNextEntry(new JarEntry("legend/definitive/manager/managed-log4j2.xml"));
      try(final var resource = InstallStore.class.getResourceAsStream("managed-log4j2.xml")) { assertNotNull(resource); resource.transferTo(jar); } jar.closeEntry();
    }
    Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES)); ManagerMain.makeManifest(pack, PackageManifest.hostPlatform(), "fixture"); return pack;
  }
  @org.junit.jupiter.api.RepeatedTest(5) void killingInstalledRootRouterStopsInnerManagerAndGameAndReleasesMaintenance() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("root-router")); store.install(leasePackage()); DiscImporter.importDiscs(store, discs("router-"));
    final Path workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"), "5");
    final Process outer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true", "-Ddefinitive.installerLog=" + this.temporary.resolve("installer.log"), "-jar", store.root().resolve("definitive-manager.jar").toString(), "--play", store.root().toString()).redirectErrorStream(true).redirectOutput(this.temporary.resolve("root-router-output.log").toFile()).start();
    final var descendants = new ArrayList<ProcessHandle>();
    try {
      final long started = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-game.started")) && outer.isAlive() && System.nanoTime() < started) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-game.started")), Files.readString(this.temporary.resolve("root-router-output.log")) + "\n" + DiagnosticLogs.availableTail(store.root().resolve("manager-router.log")));
      outer.descendants().forEach(descendants::add); assertTrue(descendants.size() >= 3, "The real router, inner manager and game must exist");
      assertThrows(java.io.IOException.class, store::lock);
      outer.destroyForcibly(); assertTrue(outer.waitFor(5, TimeUnit.SECONDS));
      final long stopped = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < stopped) Thread.sleep(20);
      assertFalse(descendants.stream().anyMatch(ProcessHandle::isAlive), "An owned inner manager or game survived the outer router");
      try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
    } finally { outer.destroyForcibly(); descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly); }
  }
  @Test void deadLauncherBeforeSpawnReconcilesWithoutLeavingPermanentLock() throws Exception {
    final var store = install("pending-spawn"); final Process child = start("pending", store.root(), List.of()); assertTrue(child.waitFor(10, TimeUnit.SECONDS)); assertEquals(93, child.exitValue());
    try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
    assertFalse(Files.exists(store.root().resolve(GameLease.PENDING)));
  }
  @Test void killedLauncherSpawnGapAndActualChildLeaseBothExcludeMaintenance() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("orphan-game")); store.install(leasePackage());
    final Process parent = start("handoff", store.root(), List.of()); assertTrue(parent.waitFor(10, TimeUnit.SECONDS)); assertEquals(94, parent.exitValue());
    final long pid = Long.parseLong(Files.readString(store.root().resolve("fixture-child.pid"))); final ProcessHandle child = ProcessHandle.of(pid).orElseThrow();
    try {
      final long waiting = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-spawn-gap.ready")) && child.isAlive() && System.nanoTime() < waiting) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-spawn-gap.ready")), Files.readString(store.root().resolve("handoff-child.log")));
      assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)), "The synthetic child must still be in the pre-lease spawn gap.");
      if(PackageManifest.hostPlatform().equals("linux-x64")) assertTrue(child.info().arguments().isEmpty(), "Exercise Linux's truncated long-command metadata.");
      assertTrue(child.isAlive()); assertThrows(java.io.IOException.class, store::lock); // Child exists, but has not acquired its lease yet.
      assertTrue(Files.exists(store.root().resolve(GameLease.PENDING)), "An unresolved spawn must retain its durable handoff.");
      Files.writeString(store.root().resolve("fixture-spawn-gap.continue"), "Continue synthetic child.");
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-game.started")) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-game.started")), Files.readString(store.root().resolve("handoff-child.log")));
      assertThrows(java.io.IOException.class, () -> store.uninstall(true, InstallProgress.NONE));
      child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS);
      try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
    } finally { if(child.isAlive()) { child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS); } }
  }
  @Test void returnedEntryPointKeepsGameLeaseWhileNonDaemonWorkerWritesAfterLauncherDeath() throws Exception {
    final var store = new InstallStore(this.temporary.resolve("returning-game")); store.install(leasePackage(true));
    final Process parent = start("handoff", store.root(), List.of()); assertTrue(parent.waitFor(10, TimeUnit.SECONDS)); assertEquals(94, parent.exitValue());
    final ProcessHandle child = ProcessHandle.of(Long.parseLong(Files.readString(store.root().resolve("fixture-child.pid")))).orElseThrow();
    try {
      Files.writeString(store.root().resolve("fixture-spawn-gap.continue"), "Continue synthetic child.");
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while((!Files.exists(store.root().resolve("fixture-entry.returned")) || !Files.exists(store.root().resolve("fixture-worker.progress"))) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-entry.returned")), Files.readString(store.root().resolve("handoff-child.log"))); assertTrue(child.isAlive());
      assertTrue(Files.exists(store.root().resolve(GameLease.PENDING))); assertTrue(Files.exists(store.root().resolve(GameLease.RUNNING)));
      assertEquals(Long.toString(child.pid()), PackageManifest.readProperties(store.root().resolve(GameLease.RUNNING)).getProperty("pid"));
      try(final var channel = java.nio.channels.FileChannel.open(store.root().resolve(".game-lock"), StandardOpenOption.WRITE); final var lease = channel.tryLock()) { assertNull(lease, "The actual child must retain its OS lease after Main.main returns and GC runs."); }
      assertThrows(java.io.IOException.class, store::lock); assertThrows(java.io.IOException.class, () -> store.uninstall(true, InstallProgress.NONE));
      final Path progress = store.root().resolve("fixture-worker.progress"); final String first = Files.readString(progress); final long writing = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while(Files.readString(progress).equals(first) && System.nanoTime() < writing) Thread.sleep(20);
      assertNotEquals(first, Files.readString(progress), "A non-daemon worker must still be writing after entry return.");
      child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS);
      try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
      assertFalse(Files.exists(store.root().resolve(GameLease.PENDING))); assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)));
    } finally { if(child.isAlive()) { child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS); } }
  }
  @Test void failedLeaseAcquisitionLeavesParentHandoffForSerializedRecoveryAndKeepsOtherLease() throws Exception {
    final var store = install("lease-failure");
    try(final var channel = java.nio.channels.FileChannel.open(store.root().resolve(".game-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE); final var lease = channel.lock()) {
      final Process child = start("lease-failure", store.root(), List.of()); assertTrue(child.waitFor(10, TimeUnit.SECONDS)); assertEquals(96, child.exitValue());
      assertTrue(lease.isValid()); assertTrue(Files.exists(store.root().resolve(GameLease.PENDING))); assertFalse(Files.exists(store.root().resolve(GameLease.RUNNING)));
    }
    try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
    assertFalse(Files.exists(store.root().resolve(GameLease.PENDING)));
  }
  @Test void interruptedLaunchUsesOsStartClockAndTruncatedCommandLineFailClosed() throws Exception {
    final Instant launcher = Instant.parse("2026-10-10T16:00:00Z"); final String token = UUID.randomUUID().toString();
    final var child = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(launcher.plusMillis(100)), Optional.empty(), Optional.of("java -Ddefinitive.launchToken=" + token + " -cp truncated"), Optional.of("/jdk/bin/java"));
    assertTrue(assertThrows(java.io.IOException.class, () -> GameLease.checkInterruptedProcess(token, "fixture-user", launcher, child)).getMessage().contains("surviving game"));
    final var hiddenArguments = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(launcher.plusMillis(100)), Optional.empty(), Optional.empty(), Optional.of("/jdk/bin/java"));
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", launcher, hiddenArguments));
    final var hiddenOwner = new GameLease.LaunchProcess(Optional.empty(), Optional.of(launcher), Optional.of(new String[]{"unresolved-helper"}), Optional.empty(), Optional.empty());
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", launcher, hiddenOwner));
    final var beforeExec = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(launcher), Optional.of(new String[]{"25", "pipes"}), Optional.empty(), Optional.of("/jdk/lib/jspawnhelper"));
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", launcher, beforeExec));
    final var older = new GameLease.LaunchProcess(Optional.empty(), Optional.of(launcher.minusMillis(1)), Optional.empty(), Optional.empty(), Optional.empty());
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", launcher, older));
  }
  @Test void unrelatedExecutableWithHiddenArgumentsIsNotAnInterruptedGameChild() throws Exception {
    final Instant started = Instant.parse("2026-10-10T18:12:20.010Z"); final String token = UUID.randomUUID().toString();
    final var git = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(started), Optional.empty(), Optional.empty(), Optional.of("/usr/bin/git"));
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, git));
    for(final String command : new String[]{"/jdk/bin/java", "/jdk/lib/jspawnhelper", "/bin/bash"}) {
      final var uncertain = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(started), Optional.empty(), Optional.empty(), Optional.of(command));
      assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, uncertain));
    }
    final var hiddenExecutable = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(started), Optional.of(new String[]{"25", "pipes"}), Optional.empty(), Optional.empty());
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, hiddenExecutable));
    final var tokenBearing = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(started), Optional.empty(), Optional.of("git -Ddefinitive.launchToken=" + token), Optional.of("/usr/bin/git"));
    assertThrows(java.io.IOException.class, () -> GameLease.checkInterruptedProcess(token, "fixture-user", started, tokenBearing), "Positive token evidence must still block regardless of executable");
  }
  @Test void recordedProcessExitDuringIdentityReadIsIdleButLivingMissingIdentityBlocks() throws Exception {
    final Instant started = Instant.now(); final var reads = new java.util.concurrent.atomic.AtomicInteger();
    assertFalse(GameLease.recordedAlive(started, () -> false, () -> { fail("Dead process must not be inspected"); return Optional.empty(); }));
    assertFalse(GameLease.recordedAlive(started, () -> reads.incrementAndGet() == 1, Optional::empty));
    reads.set(0); assertFalse(GameLease.recordedAlive(started, () -> reads.incrementAndGet() < 3, Optional::empty));
    assertThrows(java.io.IOException.class, () -> GameLease.recordedAlive(started, () -> true, Optional::empty));
    assertTrue(GameLease.recordedAlive(started, () -> true, () -> Optional.of(started)));
    assertFalse(GameLease.recordedAlive(started, () -> true, () -> Optional.of(started.plusSeconds(1))), "A reused PID must not match the recorded identity");
  }
  @Test void exitingAndOpaqueUnrelatedProcessesDoNotBlockInterruptedLaunchRecovery() throws Exception {
    final Instant started = Instant.now(); final String token = UUID.randomUUID().toString();
    final var unknown = new GameLease.LaunchProcess(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    final var reads = new java.util.concurrent.atomic.AtomicInteger();
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, () -> { fail("A dead process must not be inspected"); return unknown; }, () -> false));
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, () -> unknown, () -> reads.incrementAndGet() == 1));
    reads.set(0);
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, () -> unknown, () -> reads.incrementAndGet() < 3));
    assertDoesNotThrow(() -> GameLease.checkInterruptedProcess(token, "fixture-user", started, () -> unknown, () -> true));
    final var livingChild = new GameLease.LaunchProcess(Optional.of("fixture-user"), Optional.of(started), Optional.of(new String[]{"-Ddefinitive.launchToken=" + token}), Optional.empty(), Optional.of("/jdk/bin/java"));
    assertThrows(java.io.IOException.class, () -> GameLease.checkInterruptedProcess(token, "fixture-user", started, () -> livingChild, () -> true));
  }
  @Test void successfulPreparationCommitsAcceptedDiscHashesAndNextReinstallReusesFiles() throws Exception {
    final Path pack = leasePackage();
    final Path source = this.temporary.resolve("fake-source/legend/definitive/tools/PrepareDiscs.java"); Files.createDirectories(source.getParent());
    Files.writeString(source, "package legend.definitive.tools; public class PrepareDiscs { public static void main(String[] args) throws Exception { java.nio.file.Files.writeString(java.nio.file.Path.of(\"files/version\"),\"5\"); System.out.println(\"Synthetic private preparation only\"); } }");
    final Path classes = this.temporary.resolve("fake-classes"); assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    try(final var jar = new JarOutputStream(Files.newOutputStream(pack.resolve("lod-game-test.jar")))) {
      jar.putNextEntry(new JarEntry("legend/definitive/tools/PrepareDiscs.class")); jar.write(Files.readAllBytes(classes.resolve("legend/definitive/tools/PrepareDiscs.class"))); jar.closeEntry();
    }
    Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES)); ManagerMain.makeManifest(pack, PackageManifest.hostPlatform(), "fixture");
    final var store = new InstallStore(this.temporary.resolve("accepted-discs")); store.install(pack); DiscImporter.importDiscs(store, discs("accepted-"));
    final Path disc = store.root().resolve("isos/SCUS94491.bin"); final byte[] bytes = Files.readAllBytes(disc); bytes[0] = 1; Files.write(disc, bytes);
    assertTrue(DiscImporter.existing(store, InstallProgress.NONE).changed()); assertThrows(DiscImporter.DifferentDiscs.class, () -> store.prepareInstalledDiscs(false, InstallProgress.NONE));
    store.prepareInstalledDiscs(true, InstallProgress.NONE); assertFalse(DiscImporter.existing(store, InstallProgress.NONE).changed());
    final Path marker = store.prepareLaunch().resolve("files/version"); final var time = Files.getLastModifiedTime(marker);
    assertEquals("Existing game files are ready.", store.prepareInstalledDiscs(false, InstallProgress.NONE)); assertEquals(time, Files.getLastModifiedTime(marker));
  }
  @Test void failedPreparationPreservesAcceptedHashesDuringIdenticalChangedReuse() throws Exception {
    final var store = install("failed-acceptance"); DiscImporter.importDiscs(store, discs("failed-"));
    final Properties accepted = DiscImporter.currentHashes(store); InstallStore.atomicProperties(store.root().resolve("isos/disc-checksums.properties"), accepted);
    final Path disc = store.root().resolve("isos/SCUS94491.bin"); final byte[] bytes = Files.readAllBytes(disc); bytes[0] = 1; Files.write(disc, bytes);
    final var sameInstalledImages = DiscImporter.IDS.stream().map(id -> store.root().resolve("isos/" + id + ".bin")).toList();
    DiscImporter.importDiscs(store, sameInstalledImages);
    assertThrows(java.io.IOException.class, () -> store.prepareInstalledDiscs(true, InstallProgress.NONE));
    assertEquals(accepted, PackageManifest.readProperties(store.root().resolve("isos/disc-checksums.properties")));
    assertTrue(DiscImporter.existing(store, InstallProgress.NONE).changed()); assertFalse(store.discsPrepared());
  }
  @Test void workspaceLoggingMigratesOnlyExactManagedLinkAndRejectsCustomFiles() throws Exception {
    final var store = install("managed-logging"); final Path workspace = store.prepareLaunch(); final Path config = workspace.resolve("log4j2.xml");
    assertFalse(Files.isSymbolicLink(config)); final String trusted = Files.readString(config); assertFalse(trusted.contains("<File"));
    Files.delete(config); Files.createSymbolicLink(config, store.root().resolve("releases").resolve(store.state().getProperty("version")).resolve("log4j2.xml"));
    store.prepareLaunch(); assertFalse(Files.isSymbolicLink(config)); assertEquals(trusted, Files.readString(config));
    Files.writeString(config, "player configuration"); assertThrows(java.io.IOException.class, store::prepareLaunch); assertEquals("player configuration", Files.readString(config));
    Files.delete(config); final Path sentinel = this.temporary.resolve("linked-logging-owner"); Files.writeString(sentinel, "keep"); Files.createSymbolicLink(config, sentinel);
    assertThrows(java.io.IOException.class, store::prepareLaunch); assertEquals("keep", Files.readString(sentinel));
  }
  @Test void generatedLaunchLogsAreBoundedRotateAndNeverTruncateLinkedTargets() throws Exception {
    final var store = install("shell-log");
    final Path java = this.temporary.resolve("synthetic-java"); Files.writeString(java, "#!/bin/bash\nhead -c 18000000 /dev/zero\nexit 0\n"); java.toFile().setExecutable(true);
    Files.writeString(store.root().resolve("bootstrap-java"), "#!/bin/bash\nprintf '%s\\n' '" + java + "'\n"); store.root().resolve("bootstrap-java").toFile().setExecutable(true);
    for(int run = 0; run < 2; run++) { final var process = new ProcessBuilder("/bin/bash", store.root().resolve("Play Game.sh").toString()).redirectErrorStream(true).start(); assertTrue(process.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes())); assertTrue(Files.size(store.root().resolve("launcher.log")) <= 16777216); }
    assertTrue(Files.size(store.root().resolve("launcher.log.1")) <= 16777216);
    final Path outside = this.temporary.resolve("external-shell-save"); Files.writeString(outside, "keep"); Files.delete(store.root().resolve("launcher.log")); Files.createSymbolicLink(store.root().resolve("launcher.log"), outside);
    final var process = new ProcessBuilder("/bin/bash", store.root().resolve("Play Game.sh").toString()).redirectErrorStream(true).start(); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertNotEquals(0, process.exitValue()); assertEquals("keep", Files.readString(outside));
  }
  @Test void capturedBaselineManagedLauncherMigratesWhilePlayerScriptsStayUntouched() throws Exception {
    final var store = install("migration");
    try(final var input = StorageHardeningTest.class.getResourceAsStream("managed-launcher-v1.sh")) { assertNotNull(input); Files.write(store.root().resolve("Play Game.sh"), input.readAllBytes()); }
    Files.writeString(store.root().resolve("Manage Installation.sh"), "#!/bin/bash\n# player custom script\nexit 0\n");
    store.install(this.temporary.resolve("migration-pack"));
    assertTrue(Files.readString(store.root().resolve("Play Game.sh")).contains("LOGDIR=")); assertTrue(Files.readString(store.root().resolve("Play Game.sh")).contains("--play"));
    assertEquals("#!/bin/bash\n# player custom script\nexit 0\n", Files.readString(store.root().resolve("Manage Installation.sh")));
  }
}
