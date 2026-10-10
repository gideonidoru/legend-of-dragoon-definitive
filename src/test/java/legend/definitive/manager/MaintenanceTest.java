package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real owned installations, synthetic discs and isolated Steam libraries. */
class MaintenanceTest {
  @TempDir Path temporary;
  private InstallStore store; private Path pack; private String registry;
  @BeforeEach void prepare() throws Exception {
    this.temporary = this.temporary.toRealPath(); final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    this.pack = fixtures.pack("package", PackageManifest.hostPlatform()); this.store = new InstallStore(this.temporary.resolve("installed")); this.store.install(this.pack);
    this.registry = System.getProperty("definitive.locationRegistry"); System.setProperty("definitive.locationRegistry", this.temporary.resolve("location.properties").toString());
  }
  @AfterEach void restore() { if(this.registry == null) System.clearProperty("definitive.locationRegistry"); else System.setProperty("definitive.locationRegistry", this.registry); }
  @Test void reinstallRepairsTheExactManagedReleaseAndRetainsTheDamagedCopy() throws Exception {
    final Path release = this.store.root().resolve("releases").resolve(this.store.state().getProperty("version")); Files.writeString(release.resolve("lod-game-test.jar"), "damaged");
    final Path saved = this.store.data(this.store.state()).resolve("saves/owner.txt"); Files.writeString(saved, "retained");
    this.store.install(this.pack); this.store.verifyInstalled(); assertEquals("retained", Files.readString(saved));
    try(final var roots = Files.list(this.store.root())) { final Path backup = roots.filter(p -> p.getFileName().toString().startsWith("release-recovery-")).findFirst().orElseThrow(); assertEquals("damaged", Files.readString(backup.resolve("lod-game-test.jar"))); }
  }
  @Test void uninstallKeepsDiscsSavesModsAndUnknownFilesAndReinstallRestoresThem() throws Exception {
    final Path data = this.store.data(this.store.state()); Files.writeString(data.resolve("saves/owner.txt"), "save"); Files.writeString(data.resolve("mods/owner.jar"), "mod");
    Files.writeString(this.store.root().resolve("isos/owner.iso"), "private image"); Files.writeString(this.store.root().resolve("owner-note.txt"), "unknown");
    this.store.prepareLaunch(); this.store.uninstall(false, InstallProgress.NONE);
    assertFalse(Files.exists(this.store.root().resolve("Play Game.sh"))); assertEquals("private image", Files.readString(this.store.root().resolve("isos/owner.iso"))); assertEquals("unknown", Files.readString(this.store.root().resolve("owner-note.txt")));
    this.store.install(this.pack); this.store.verifyInstalled(); final Path restored = this.store.data(this.store.state()); assertEquals("save", Files.readString(restored.resolve("saves/owner.txt"))); assertEquals("mod", Files.readString(restored.resolve("mods/owner.jar")));
  }
  @Test void deleteIsosIsExplicitAndIncludesDiscRecoveryCopies() throws Exception {
    Files.writeString(this.store.root().resolve("isos/owner.iso"), "private image"); final Path recovery = Files.createDirectory(this.store.root().resolve("disc-recovery-" + UUID.randomUUID())); Files.writeString(recovery.resolve("owner.iso"), "older image");
    this.store.uninstall(true, InstallProgress.NONE); assertFalse(Files.exists(recovery)); try(final var paths = Files.list(this.store.root().resolve("isos"))) { assertEquals(0, paths.count()); }
    assertTrue(Files.isDirectory(this.store.data(this.store.state())));
  }
  @Test void uninstallCannotRunWhileAnOperationOrGameHoldsTheLock() throws Exception {
    try(final var operation = this.store.lock()) { assertThrows(java.io.IOException.class, () -> this.store.uninstall(true, InstallProgress.NONE)); }
    this.store.verifyInstalled();
  }
  @Test void unexpectedUninstallTargetFailsBeforeDeletingAnything() throws Exception {
    Files.createDirectory(this.store.root().resolve("releases/owner-files")); final var state = this.store.state();
    assertThrows(java.io.IOException.class, () -> this.store.uninstall(true, InstallProgress.NONE)); assertEquals(state, this.store.state()); this.store.verifyInstalled();
  }
  @Test void locationReceiptFindsACustomInstallAndRetainedUninstalledData() throws Exception {
    InstallLocation.record(this.store); assertEquals(this.store.root(), InstallLocation.discover().orElseThrow());
    this.store.uninstall(false, InstallProgress.NONE); assertEquals(this.store.root(), InstallLocation.discover().orElseThrow());
    Files.writeString(this.store.root().resolve(".definitive-owned"), "forged"); assertTrue(InstallLocation.discover().isEmpty());
  }
  @Test void staleOrLinkedLocationReceiptNeverSelectsAnUnownedFolder() throws Exception {
    final var properties = new Properties(); properties.setProperty("root", this.temporary.toString()); InstallStore.atomicProperties(InstallLocation.receipt(), properties); assertTrue(InstallLocation.discover().isEmpty());
    Files.delete(InstallLocation.receipt()); Files.createSymbolicLink(InstallLocation.receipt(), this.pack.resolve("README.md")); assertTrue(InstallLocation.discover().isEmpty());
  }
  @Test void fullscreenChangesOnlyItsNativeSettingAndKeepsAnOriginalBackup() throws Exception {
    final Path data = this.store.data(this.store.state()); final Path config = data.resolve("config.dcnf"); Files.deleteIfExists(config);
    InstallStore.configureFullscreen(data, false); final byte[] before = Files.readAllBytes(config);
    InstallStore.configureFullscreen(data, true); final byte[] after = Files.readAllBytes(config);
    assertEquals(0, before[before.length - 1]); assertEquals(1, after[after.length - 1]); assertArrayEquals(Arrays.copyOf(before, before.length - 1), Arrays.copyOf(after, after.length - 1)); assertArrayEquals(before, Files.readAllBytes(data.resolve("config.dcnf.before-fullscreen")));
    final var nativeConfig = new legend.game.unpacker.FileData(after); assertEquals(1, nativeConfig.readInt(0)); assertEquals("lod_core:fullscreen", nativeConfig.readAscii(4));
    Files.write(config, new byte[]{1, 2, 3}); assertThrows(java.io.IOException.class, () -> InstallStore.configureFullscreen(data, true)); assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(config));
  }
  @Test void steamRemovalRetainsAnUnrelatedEntryAndPlayerArtwork() throws Exception {
    final var account = new SteamLibrary.Account(Files.createDirectory(this.temporary.resolve("steam-config"))); SteamLibrary.add(account, this.store.root(), () -> false);
    final Path file = account.config().resolve("shortcuts.vdf"); final var root = SteamLibrary.decode(Files.readAllBytes(file));
    @SuppressWarnings("unchecked") final var entries = (List<SteamLibrary.Value>)root.getFirst().data;
    entries.add(new SteamLibrary.Value(0, "1", new ArrayList<>(List.of(new SteamLibrary.Value(1, "AppName", "Unrelated game"))))); Files.write(file, SteamLibrary.encode(root));
    final Path custom; try(final var art = Files.list(account.config().resolve("grid"))) { custom = art.filter(p -> p.toString().endsWith("p.png")).findFirst().orElseThrow(); } Files.writeString(custom, "player art");
    SteamLibrary.remove(account, this.store.root(), () -> false); assertFalse(SteamLibrary.hasShortcut(account, this.store.root())); assertEquals("player art", Files.readString(custom));
    @SuppressWarnings("unchecked") final var retained = (List<SteamLibrary.Value>)SteamLibrary.decode(Files.readAllBytes(file)).getFirst().data; assertEquals(1, retained.size()); assertArrayEquals(SteamLibrary.encode(List.of(entries.getLast())), SteamLibrary.encode(retained));
  }
  @Test void maintenanceScreenOffersReinstallAndUninstall() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var view = new ManagerView(null, this.pack, this.store.root()); view.setSize(1100, 700); InstallStoreTest.layout(view);
      final var all = new ArrayList<java.awt.Component>(); InstallStoreTest.collect(view, all);
      assertTrue(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Reinstall"))); assertTrue(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Uninstall")));
      final var image = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_RGB); final var g = image.createGraphics(); view.paint(g); g.dispose();
      try { javax.imageio.ImageIO.write(image, "png", Path.of("build/reports/installer-maintenance.png").toFile()); } catch(final java.io.IOException failure) { throw new RuntimeException(failure); }
    });
  }
}
