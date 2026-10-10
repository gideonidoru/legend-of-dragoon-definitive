// Definitive installation verification (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class InstallStoreTest {
  @TempDir Path temporary;
  @org.junit.jupiter.api.BeforeEach void useRealTemporaryPath() throws Exception { this.temporary = this.temporary.toRealPath(); }

  Path pack(final String name, final String platform) throws Exception {
    final Path root = this.temporary.resolve(name); Files.createDirectory(root);
    for(final String path : PackageManifest.ROOT_FILES) Files.writeString(root.resolve(path), name + path);
    Files.writeString(root.resolve("lod-game-test.jar"), name);
    for(final String support : PackageManifest.SUPPORT) Files.createDirectories(root.resolve(support));
    Files.writeString(root.resolve("bundled-mods/Skurfa.jar"), "art");
    Files.writeString(root.resolve("libs/dependency.jar"), "fixture");
    ManagerMain.makeManifest(root, platform, "fixture"); return root;
  }

  @Test void updateAndRollbackPreservePriorAndNewerData() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed"));
    final Path one = this.pack("v1", PackageManifest.hostPlatform());
    store.install(one, "111");
    final Properties state1 = store.state(); final Path data1 = store.data(state1);
    Files.writeString(data1.resolve("saves/campaign.dsav"), "before-update");
    Files.writeString(data1.resolve("config.conf"), "old-settings");
    Files.writeString(data1.resolve("mods/my-mod.jar"), "custom-mod");
    Files.writeString(store.root().resolve("isos/owner.bin"), "private-disc");
    store.setArtwork(false);
    store.setLegacyTextures(true);
    store.install(this.pack("v2", PackageManifest.hostPlatform()), "222");
    final Properties state2 = store.state(); final Path data2 = store.data(state2);
    assertNotEquals(data1, data2);
    assertEquals("before-update", Files.readString(data2.resolve("saves/campaign.dsav")));
    Files.writeString(data2.resolve("saves/campaign.dsav"), "newer-save-format");
    Files.writeString(data2.resolve("config.conf"), "new-settings");
    store.setArtwork(true);
    store.setLegacyTextures(false);
    store.rollback();
    final Properties restored = store.state(); final Path old = store.data(restored);
    assertEquals(state1.getProperty("version"), restored.getProperty("version"));
    assertEquals("before-update", Files.readString(old.resolve("saves/campaign.dsav")));
    assertEquals("old-settings", Files.readString(old.resolve("config.conf")));
    assertEquals("custom-mod", Files.readString(old.resolve("mods/my-mod.jar")));
    assertEquals("newer-save-format", Files.readString(data2.resolve("saves/campaign.dsav")));
    assertEquals("private-disc", Files.readString(store.root().resolve("isos/owner.bin")));
    assertEquals("original", restored.getProperty("artwork"));
    assertEquals("true", restored.getProperty("legacyTextures"));
    assertEquals("111", restored.getProperty("releaseAssetId"));
  }

  @Test void corruptOrExtraPackageNeverChangesActiveState() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed"));
    store.install(this.pack("v1", PackageManifest.hostPlatform()));
    final byte[] before = Files.readAllBytes(store.root().resolve("state.properties"));
    final Path corrupt = this.pack("v2", PackageManifest.hostPlatform());
    Files.writeString(corrupt.resolve("lod-game-test.jar"), "corrupted");
    assertThrows(java.io.IOException.class, () -> store.install(corrupt));
    assertArrayEquals(before, Files.readAllBytes(store.root().resolve("state.properties")));
    final Path extra = this.pack("v3", PackageManifest.hostPlatform()); Files.writeString(extra.resolve("config.dcnf"), "private-overwrite");
    assertThrows(java.io.IOException.class, () -> store.install(extra));
    assertArrayEquals(before, Files.readAllBytes(store.root().resolve("state.properties")));
  }

  @Test void incompatiblePlatformAndIdentityTamperingRejected() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed"));
    final Path wrong = this.pack("other", "wrong-platform");
    assertThrows(java.io.IOException.class, () -> store.install(wrong));
    final Path changed = this.pack("changed", PackageManifest.hostPlatform());
    final Properties metadata = PackageManifest.readProperties(changed.resolve(PackageManifest.METADATA)); metadata.setProperty("skurfa", "unpaired-commit");
    InstallStore.atomicProperties(changed.resolve(PackageManifest.METADATA), metadata);
    assertThrows(java.io.IOException.class, () -> store.install(changed));
    assertFalse(store.state().containsKey("version"));
  }

  @Test void traversalDuplicateAndPrivateZipPathsRejected() throws Exception {
    for(final List<String> names : List.of(List.of("../outside"), List.of("saves/attack"), List.of("libs/x", "libs/x"))) {
      final Path zip = this.temporary.resolve("attack-" + java.util.UUID.randomUUID() + ".zip");
      final boolean duplicate = names.size() == 2;
      try(final var output = new ZipOutputStream(Files.newOutputStream(zip))) {
        for(final String name : duplicate ? List.of("libs/first", "libs/other") : names) { output.putNextEntry(new ZipEntry(name)); output.write(1); output.closeEntry(); }
      }
      if(duplicate) {
        final byte[] bytes = Files.readAllBytes(zip);
        final byte[] oldName = "libs/other".getBytes(); final byte[] newName = "libs/first".getBytes();
        for(int i = 0; i <= bytes.length - oldName.length; i++) {
          if(java.util.Arrays.equals(java.util.Arrays.copyOfRange(bytes, i, i + oldName.length), oldName)) System.arraycopy(newName, 0, bytes, i, newName.length);
        }
        Files.write(zip, bytes);
      }
      final InstallStore store = new InstallStore(this.temporary.resolve("target-" + java.util.UUID.randomUUID()));
      assertThrows(java.io.IOException.class, () -> store.install(zip)); assertFalse(store.state().containsKey("version"));
    }
    assertFalse(Files.exists(this.temporary.resolve("outside")));
  }

  @Test void linksAndUnownedTargetsFailClosed() throws Exception {
    final Path occupied = this.temporary.resolve("occupied"); Files.createDirectory(occupied); Files.writeString(occupied.resolve("save"), "keep");
    assertThrows(java.io.IOException.class, () -> new InstallStore(occupied));
    final Path linked = this.temporary.resolve("linked"); Files.createSymbolicLink(linked, occupied);
    assertThrows(java.io.IOException.class, () -> new InstallStore(linked));
    final Path pack = this.pack("linked-pack", PackageManifest.hostPlatform()); Files.delete(pack.resolve("README.md")); Files.createSymbolicLink(pack.resolve("README.md"), occupied.resolve("save"));
    assertThrows(java.io.IOException.class, () -> new InstallStore(this.temporary.resolve("installed")).install(pack));
    assertEquals("keep", Files.readString(occupied.resolve("save")));
  }

  @Test void operationLockPreventsUpdatesAndRollback() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed"));
    final Path pack = this.pack("v1", PackageManifest.hostPlatform());
    try(final var lock = store.lock()) {
      assertThrows(java.io.IOException.class, () -> store.install(pack));
      assertThrows(java.io.IOException.class, store::rollback);
    }
    store.install(pack); assertTrue(store.state().containsKey("version"));
  }

  @Test void corruptedSnapshotDoesNotRestoreOrReplaceNewerSaves() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(this.pack("v1", PackageManifest.hostPlatform()));
    Files.writeString(store.data(store.state()).resolve("saves/one.dsav"), "before");
    store.install(this.pack("v2", PackageManifest.hostPlatform()));
    final Properties state = store.state(); final Path active = store.data(state);
    Files.writeString(active.resolve("saves/one.dsav"), "newer");
    Files.writeString(store.root().resolve("snapshots").resolve(state.getProperty("previousSnapshot")).resolve("saves/one.dsav"), "corrupted");
    assertThrows(java.io.IOException.class, store::rollback);
    assertEquals(state.getProperty("version"), store.state().getProperty("version")); assertEquals("newer", Files.readString(active.resolve("saves/one.dsav")));
  }

  @Test void workspaceSeparatesExtractionAndArtworkFromPrivateMods() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(this.pack("v1", PackageManifest.hostPlatform()));
    final Path data = store.data(store.state()); Files.writeString(data.resolve("mods/custom.jar"), "custom");
    final Path first = store.prepareLaunch(); assertTrue(Files.isSymbolicLink(first.resolve("mods/Skurfa.jar"))); assertTrue(Files.isSymbolicLink(first.resolve("mods/custom.jar")));
    Files.writeString(first.resolve("files/version"), "old-extraction"); store.setArtwork(false);
    assertEquals(first, store.prepareLaunch()); assertFalse(Files.exists(first.resolve("mods/Skurfa.jar"))); assertEquals("custom", Files.readString(data.resolve("mods/custom.jar")));
    store.install(this.pack("v2", PackageManifest.hostPlatform())); final Path next = store.prepareLaunch();
    assertNotEquals(first, next); assertFalse(Files.exists(next.resolve("files/version"))); assertEquals("old-extraction", Files.readString(first.resolve("files/version")));
  }

  @Test void discSelectionErrorsLeaveOriginalInputsAndDestinationUntouched() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("installed"));
    final var discs = new java.util.ArrayList<Path>();
    for(final String id : DiscImporter.IDS) {
      final byte[] bytes = new byte[17 * 2352]; final int offset = 16 * 2352 + 24;
      bytes[offset] = 1; bytes[offset + 6] = 1;
      System.arraycopy("CD001".getBytes(), 0, bytes, offset + 1, 5);
      System.arraycopy("PLAYSTATION                     ".getBytes(), 0, bytes, offset + 8, 32);
      System.arraycopy((id + " ".repeat(32 - id.length())).getBytes(), 0, bytes, offset + 40, 32);
      final Path disc = this.temporary.resolve(id + ".bin"); Files.write(disc, bytes); discs.add(disc);
    }
    assertThrows(java.io.IOException.class, () -> DiscImporter.importDiscs(store, discs.subList(0, 3)));
    try(final var entries = Files.list(store.root().resolve("isos"))) { assertEquals(0, entries.count()); }
    DiscImporter.importDiscs(store, discs); DiscImporter.validateSet(store.root().resolve("isos"));
    for(final Path disc : discs) assertEquals(PackageManifest.sha256(disc), PackageManifest.sha256(store.root().resolve("isos").resolve(disc.getFileName())));
    assertDoesNotThrow(() -> DiscImporter.importDiscs(store, discs));
  }
  @Test void setupPanelRendersWithoutCreatingAnInstallation() throws Exception {
    final Path target = this.temporary.resolve("not-installed");
    final java.util.concurrent.atomic.AtomicReference<javax.swing.JPanel> panel = new java.util.concurrent.atomic.AtomicReference<>();
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      panel.set(ManagerMain.buildPanel(null, null, target));
      panel.get().setSize(1100, 700); layout(panel.get());
      final var image = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_RGB);
      final var graphics = image.createGraphics(); panel.get().paint(graphics); graphics.dispose();
      try {
        final Path output = Path.of("build/reports/manager-ui.png"); Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(image, "png", output.toFile());
      } catch(final Exception failure) { throw new RuntimeException(failure); }
    });
    assertFalse(Files.exists(target), "Opening setup must not create its suggested installation folder");
    assertEquals(1100, panel.get().getWidth());
  }

  @Test void launcherAndDiscStepRender() throws Exception {
    final InstallStore store = new InstallStore(this.temporary.resolve("preview"));
    store.install(this.pack("preview-package", PackageManifest.hostPlatform()));
    Files.writeString(store.prepareLaunch().resolve("files/version"), "prepared-fixture");
    for(final String id : DiscImporter.IDS) Files.write(store.root().resolve("isos/" + id + ".bin"), disc(id));
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      final var panel = ManagerMain.buildPanel(null, null, store.root());
      panel.setSize(1100, 700); layout(panel);
      final var image = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_RGB);
      final var g = image.createGraphics(); panel.paint(g); g.dispose();
      try { javax.imageio.ImageIO.write(image, "png", Path.of("build/reports/launcher-ui.png").toFile()); }
      catch(final Exception e) { throw new RuntimeException(e); }
    });
  }
  static byte[] disc(final String id) {
    final byte[] bytes = new byte[17 * 2352]; final int pvd = 16 * 2352 + 24;
    bytes[pvd] = 1; bytes[pvd + 6] = 1;
    System.arraycopy("CD001".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, pvd + 1, 5);
    java.util.Arrays.fill(bytes, pvd + 8, pvd + 72, (byte)' ');
    System.arraycopy("PLAYSTATION".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, pvd + 8, 11);
    System.arraycopy(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, pvd + 40, 9);
    return bytes;
  }

  @Test void folderAndActionsStayAlignedAtDeckWindowSizes() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      for(final int width : new int[]{1024, 1100}) {
        final var panel = ManagerMain.buildPanel(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
        panel.setSize(width, 700); layout(panel);
        final java.util.List<java.awt.Component> components = new java.util.ArrayList<>(); collect(panel, components);
        final var field = components.stream().filter(c -> c instanceof javax.swing.JTextField).findFirst().orElseThrow();
        final var install = components.stream().filter(c -> c instanceof javax.swing.JButton b && b.getText().startsWith("Install Definitive")).findFirst().orElseThrow();
        assertEquals(field.getWidth(), install.getWidth()); assertEquals(field.getX(), install.getX());
        assertTrue(install.getHeight() >= 48); assertTrue(field.getX() + field.getWidth() <= field.getParent().getWidth());
      }
    });
  }
  static void collect(final java.awt.Container root, final java.util.List<java.awt.Component> components) {
    for(final var child : root.getComponents()) { components.add(child); if(child instanceof java.awt.Container container) collect(container, components); }
  }

  static void layout(final java.awt.Container root) {
    root.doLayout();
    for(final java.awt.Component child : root.getComponents()) if(child instanceof java.awt.Container container) layout(container);
  }

}
