package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageHardeningTest {
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
    final Path pack = this.fixtures.pack("lease-package", PackageManifest.hostPlatform());
    final Path source = this.temporary.resolve("fake-source/legend/game/Main.java"); Files.createDirectories(source.getParent());
    Files.writeString(source, "package legend.game; public class Main { public static void main(String[] args) throws Exception { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty(\"definitive.installRoot\"),\"fixture-game.started\"),\"synthetic headless helper\"); Thread.sleep(60000); } }");
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
  @Test void killingInstalledRootRouterStopsInnerManagerAndGameAndReleasesMaintenance() throws Exception {
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
      assertTrue(child.isAlive()); assertThrows(java.io.IOException.class, store::lock); // Child exists, but has not acquired its lease yet.
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while(!Files.exists(store.root().resolve("fixture-game.started")) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
      assertTrue(Files.exists(store.root().resolve("fixture-game.started")), Files.readString(store.root().resolve("handoff-child.log")));
      assertThrows(java.io.IOException.class, () -> store.uninstall(true, InstallProgress.NONE));
      child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS);
      try(final var operation = store.lock()) { assertTrue(operation.lock().isValid()); }
    } finally { if(child.isAlive()) { child.destroyForcibly(); child.onExit().get(10, TimeUnit.SECONDS); } }
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
