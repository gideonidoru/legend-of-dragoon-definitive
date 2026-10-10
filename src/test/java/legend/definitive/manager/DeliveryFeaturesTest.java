// Definitive delivery fixtures (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryFeaturesTest {
  @TempDir Path temporary;
  @BeforeEach void realPath() throws Exception { this.temporary = this.temporary.toRealPath(); }
  @Test void zipImportsOnlyTheFourValidDiscs() throws Exception {
    final Path archive = this.temporary.resolve("my-discs.zip");
    try(final var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      for(final String id : DiscImporter.IDS) { zip.putNextEntry(new ZipEntry("images/" + id + ".bin")); zip.write(InstallStoreTest.disc(id)); zip.closeEntry(); }
      zip.putNextEntry(new ZipEntry("extras/emulator.exe")); zip.write("not installed".getBytes()); zip.closeEntry();
      zip.putNextEntry(new ZipEntry("extras/bios.bin")); zip.write("not a game disc".getBytes()); zip.closeEntry();
    }
    final var store = new InstallStore(this.temporary.resolve("install"));
    DiscSources.importSelected(store, List.of(archive)); DiscImporter.validateSet(store.root().resolve("isos"));
    try(final var files = Files.list(store.root().resolve("isos"))) { assertEquals(4, files.filter(p -> p.toString().endsWith(".bin")).count()); }
    assertTrue(Files.exists(archive));
  }
  @Test void unsafeArchiveAndDuplicateDiscsLeaveDestinationEmpty() throws Exception {
    final Path archive = this.temporary.resolve("unsafe.zip");
    try(final var zip = new ZipOutputStream(Files.newOutputStream(archive))) { zip.putNextEntry(new ZipEntry("../evil.bin")); zip.write(InstallStoreTest.disc("SCUS94491")); zip.closeEntry(); }
    final var store = new InstallStore(this.temporary.resolve("install"));
    assertThrows(java.io.IOException.class, () -> DiscSources.importSelected(store, List.of(archive)));
    try(final var files = Files.list(store.root().resolve("isos"))) { assertEquals(0, files.count()); }
    assertFalse(Files.exists(this.temporary.resolve("evil.bin")));
  }
  @Test void steamPreservesExistingDataBacksUpAndDoesNotDuplicate() throws Exception {
    final Path config = Files.createDirectory(this.temporary.resolve("steam-config"));
    final var existing = new ArrayList<SteamLibrary.Value>();
    existing.add(new SteamLibrary.Value(0, "0", new ArrayList<>(List.of(new SteamLibrary.Value(1, "AppName", "Existing Game"), new SteamLibrary.Value(7, "unknown-but-supported", new byte[]{1,2,3,4,5,6,7,8})))));
    final var root = List.of(new SteamLibrary.Value(0, "shortcuts", existing)); final byte[] before = SteamLibrary.encode(root);
    assertArrayEquals(before, SteamLibrary.encode(SteamLibrary.decode(before)));
    Files.write(config.resolve("shortcuts.vdf"), before);
    final Path install = Files.createDirectory(this.temporary.resolve("game")); Files.writeString(install.resolve("Play Game.sh"), "#!/bin/bash");
    final var account = new SteamLibrary.Account(config);
    assertThrows(java.io.IOException.class, () -> SteamLibrary.add(account, install, () -> true));
    assertArrayEquals(before, Files.readAllBytes(config.resolve("shortcuts.vdf")));
    SteamLibrary.add(account, install, () -> false);
    final byte[] after = Files.readAllBytes(config.resolve("shortcuts.vdf"));
    @SuppressWarnings("unchecked") final var entries = (List<SteamLibrary.Value>)SteamLibrary.decode(after).getFirst().data;
    assertEquals(2, entries.size()); assertArrayEquals(SteamLibrary.encode(existing.subList(0, 1)), SteamLibrary.encode(entries.subList(0, 1)));
    SteamLibrary.add(account, install, () -> false); assertArrayEquals(after, Files.readAllBytes(config.resolve("shortcuts.vdf")));
    try(final var files = Files.list(config)) { final Path backup = files.filter(p -> p.getFileName().toString().startsWith("shortcuts.vdf.definitive-backup-")).findFirst().orElseThrow(); assertArrayEquals(before, Files.readAllBytes(backup)); }
  }
  @Test void corruptSteamDataIsPreserved() throws Exception {
    final Path config = Files.createDirectory(this.temporary.resolve("config")); final byte[] broken = {0, 0, 0}; Files.write(config.resolve("shortcuts.vdf"), broken);
    final Path install = Files.createDirectory(this.temporary.resolve("game")); Files.writeString(install.resolve("Play Game.sh"), "launcher");
    assertThrows(java.io.IOException.class, () -> SteamLibrary.add(new SteamLibrary.Account(config), install, () -> false));
    assertArrayEquals(broken, Files.readAllBytes(config.resolve("shortcuts.vdf")));
  }
  @Test void releaseSelectionRequiresOurPlatformAndDigest() throws Exception {
    final String json = "[{\"draft\":false,\"published_at\":\"2026-10-09T12:00:00Z\",\"tag_name\":\"alpha1\",\"assets\":[{\"id\":42,\"name\":\"Legend-of-Dragoon-Definitive-linux-x64.zip\",\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/alpha1/package.zip\"}]}]";
    assertEquals("42", ReleaseUpdates.select(json, "linux-x64", "", "").orElseThrow().assetId());
    assertTrue(ReleaseUpdates.select(json, "macos-arm64", "", "").isEmpty());
    assertTrue(ReleaseUpdates.select(json, "linux-x64", "42", "").isEmpty());
    assertThrows(java.io.IOException.class, () -> ReleaseUpdates.select(json.replace("sha256:", "invalid:"), "linux-x64", "", ""));
    assertThrows(java.io.IOException.class, () -> ReleaseUpdates.select(json.replace("https://github.com/gideonidoru", "https://github.com/other"), "linux-x64", "", ""));
  }
  @Test void controllerButtonsAndDeadZoneHaveExplicitNavigationActions() throws Exception {
    assertEquals(java.awt.event.KeyEvent.VK_ENTER, DeckControls.buttonKey(0));
    assertEquals(java.awt.event.KeyEvent.VK_ESCAPE, DeckControls.buttonKey(1));
    assertEquals(java.awt.event.KeyEvent.VK_UP, DeckControls.buttonKey(11));
    assertEquals(java.awt.event.KeyEvent.VK_DOWN, DeckControls.buttonKey(12));
    assertEquals(0, DeckControls.direction(12000)); assertEquals(1, DeckControls.direction(25000)); assertEquals(-1, DeckControls.direction(-25000));
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      final var list = new javax.swing.JList<>(new String[]{"one", "two", "three"}); list.setSelectedIndex(0);
      DeckControls.moveSelection(list, java.awt.event.KeyEvent.VK_DOWN); assertEquals(1, list.getSelectedIndex());
      DeckControls.moveSelection(list, java.awt.event.KeyEvent.VK_PAGE_DOWN); assertEquals(2, list.getSelectedIndex());
      DeckControls.moveSelection(list, java.awt.event.KeyEvent.VK_PAGE_UP); assertEquals(0, list.getSelectedIndex());
    });
  }

}
