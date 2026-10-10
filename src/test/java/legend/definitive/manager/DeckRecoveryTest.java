package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduce the reported reinstall and Steam entry behavior without game assets or Steam. */
class DeckRecoveryTest {
  @TempDir Path temporary;
  private InstallStore store() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("installed"));
    store.install(fixtures.pack("package", PackageManifest.hostPlatform())); return store;
  }
  @Test void selectingTheSameDiscsAgainSucceedsWithoutRecopying() throws Exception {
    final var store = this.store(); final var sources = new ArrayList<Path>();
    for(final String id : DiscImporter.IDS) {
      final Path source = this.temporary.resolve(id + ".iso"); Files.write(source, InstallStoreTest.disc(id)); sources.add(source);
    }
    DiscImporter.importDiscs(store, sources);
    final Path retained = store.root().resolve("isos/SCUS94491.bin");
    final var time = Files.getLastModifiedTime(retained);
    assertDoesNotThrow(() -> DiscImporter.importDiscs(store, sources));
    assertEquals(time, Files.getLastModifiedTime(retained));
  }
  @Test void steamPlayScriptStartsTheGameInsteadOfTheSwingManager() throws Exception {
    final var store = this.store();
    assertTrue(Files.readString(store.root().resolve("Play Game.sh")).contains("--play"), "Steam Play must launch the game, not a Swing window in Gaming Mode");
    assertTrue(Files.readString(store.root().resolve("Manage Installation.sh")).contains("--manage"));
    final Path fakeJava = this.temporary.resolve("java-fixture"); Files.writeString(fakeJava, "#!/bin/bash\nprintf '%s\\n' \"$@\"\n"); assertTrue(fakeJava.toFile().setExecutable(true));
    Files.writeString(store.root().resolve("bootstrap-java"), "#!/bin/bash\nprintf '%s\\n' '" + fakeJava + "'\n");
    final var process = new ProcessBuilder("/bin/bash", store.root().resolve("Play Game.sh").toString()).start(); assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
    final String arguments = Files.readString(store.root().resolve("launcher.log")); assertTrue(arguments.contains("--play")); assertFalse(arguments.contains("--manage"));
  }
  @Test void bundledOriginalArtworkProducesEverySteamSurfaceWithoutNetwork() throws Exception {
    final var store = this.store(); SteamArtwork.prepare(store.root(), InstallProgress.NONE);
    final var sizes = Map.of("portrait.png", new java.awt.Dimension(600, 900), "landscape.png", new java.awt.Dimension(920, 430), "hero.png", new java.awt.Dimension(1920, 620), "icon.png", new java.awt.Dimension(512, 512), "logo.png", new java.awt.Dimension(640, 160));
    for(final var entry : sizes.entrySet()) {
      final Path path = store.root().resolve("steam-artwork").resolve(entry.getKey()); final var image = javax.imageio.ImageIO.read(path.toFile()); assertEquals(entry.getValue().width, image.getWidth()); assertEquals(entry.getValue().height, image.getHeight());
      Files.createDirectories(Path.of("build/reports")); Files.copy(path, Path.of("build/reports/installer-steam-" + entry.getKey()), StandardCopyOption.REPLACE_EXISTING);
    }
    assertTrue(Files.readString(store.root().resolve("steam-artwork/source.txt")).contains("Sony"));
  }
  @Test void steamEntryHasAnArtworkIcon() throws Exception {
    final var store = this.store(); final var account = new SteamLibrary.Account(Files.createDirectory(this.temporary.resolve("steam-config")));
    SteamLibrary.add(account, store.root(), () -> false);
    @SuppressWarnings("unchecked") final var entries = (List<SteamLibrary.Value>)SteamLibrary.decode(Files.readAllBytes(account.config().resolve("shortcuts.vdf"))).getFirst().data;
    @SuppressWarnings("unchecked") final var fields = (List<SteamLibrary.Value>)entries.getFirst().data;
    assertTrue(fields.stream().anyMatch(v -> v.key.equals("icon") && v.data instanceof String icon && !icon.isBlank()), "Steam shortcut should have artwork");
  }
  @Test void duplicateSteamKeyboardAndControllerPressMovesOnlyOneRow() {
    final var input = new NavigationInput(); final var list = new javax.swing.JList<>(new String[]{"First", "Wanted", "Skipped"}); list.setSelectedIndex(0);
    final long start = 1_000_000_000L; final int down = java.awt.event.KeyEvent.VK_DOWN;
    if(input.press(NavigationInput.Source.BUTTON, down, start)) DeckControls.moveSelection(list, down);
    if(input.press(NavigationInput.Source.KEYBOARD, down, start + 20_000_000)) DeckControls.moveSelection(list, down);
    if(input.press(NavigationInput.Source.AXIS, down, start + 40_000_000)) DeckControls.moveSelection(list, down);
    assertEquals(1, list.getSelectedIndex());
    assertFalse(input.press(NavigationInput.Source.BUTTON, down, start + 300_000_000), "No repeat before the initial hold delay");
    assertTrue(input.press(NavigationInput.Source.KEYBOARD, down, start + 450_000_000));
    assertFalse(input.press(NavigationInput.Source.AXIS, down, start + 470_000_000));
    assertTrue(input.press(NavigationInput.Source.BUTTON, down, start + 630_000_000));
  }
  @Test void confirmCannotRepeatIntoTheNewPicker() {
    final var input = new NavigationInput(); final int enter = java.awt.event.KeyEvent.VK_ENTER; final long start = 1_000_000_000L;
    assertTrue(input.press(NavigationInput.Source.BUTTON, enter, start));
    assertFalse(input.press(NavigationInput.Source.KEYBOARD, enter, start + 30_000_000));
    assertFalse(input.press(NavigationInput.Source.BUTTON, enter, start + 1_000_000_000));
    input.release(NavigationInput.Source.BUTTON, enter); input.release(NavigationInput.Source.KEYBOARD, enter);
    assertTrue(input.press(NavigationInput.Source.KEYBOARD, enter, start + 1_100_000_000));
  }
  @Test void differentDiscsWarnAndRetainOriginalUntilExplicitReplacement() throws Exception {
    final var store = this.store(); final var paths = new ArrayList<Path>();
    for(final String id : DiscImporter.IDS) { final Path file = this.temporary.resolve(id + ".bin"); Files.write(file, InstallStoreTest.disc(id)); paths.add(file); }
    DiscImporter.importDiscs(store, paths);
    final Path changed = paths.getFirst(); final byte[] bytes = Files.readAllBytes(changed); bytes[12] = 17; Files.write(changed, bytes);
    final String before = PackageManifest.sha256(store.root().resolve("isos").resolve(changed.getFileName()));
    assertThrows(DiscImporter.DifferentDiscs.class, () -> DiscImporter.importDiscs(store, paths));
    assertEquals(before, PackageManifest.sha256(store.root().resolve("isos").resolve(changed.getFileName())));
    DiscImporter.importDiscs(store, paths, InstallProgress.NONE, true);
    assertEquals(PackageManifest.sha256(changed), PackageManifest.sha256(store.root().resolve("isos").resolve(changed.getFileName())));
    try(final var roots = Files.list(store.root())) { final Path recovery = roots.filter(p -> p.getFileName().toString().startsWith("disc-recovery-")).findFirst().orElseThrow(); assertEquals(before, PackageManifest.sha256(recovery.resolve(changed.getFileName()))); }
  }
  @Test void installedDiscInspectionDetectsLaterContentChanges() throws Exception {
    final var store = this.store(); final var paths = new ArrayList<Path>();
    for(final String id : DiscImporter.IDS) { final Path path = this.temporary.resolve(id + ".bin"); Files.write(path, InstallStoreTest.disc(id)); paths.add(path); }
    DiscImporter.importDiscs(store, paths); assertTrue(DiscImporter.existing(store, InstallProgress.NONE).usable()); assertFalse(DiscImporter.existing(store, InstallProgress.NONE).changed());
    final Path installed = store.root().resolve("isos/SCUS94491.bin"); final byte[] bytes = Files.readAllBytes(installed); bytes[12] = 19; Files.write(installed, bytes);
    assertTrue(DiscImporter.existing(store, InstallProgress.NONE).changed());
  }
  @Test void wrongArchitectureOverlayIsRemovedAndOtherPreloadsArePreserved() throws Exception {
    final var environment = new HashMap<String, String>(); final Path thirtyTwo = this.temporary.resolve("ubuntu12_32/gameoverlayrenderer.so");
    environment.put("LD_PRELOAD", thirtyTwo + ":/example/other.so"); InstallStore.normalizeSteamOverlay(environment); assertEquals("/example/other.so", environment.get("LD_PRELOAD"));
    final Path sixtyFour = this.temporary.resolve("ubuntu12_64/gameoverlayrenderer.so"); Files.createDirectories(sixtyFour.getParent()); Files.writeString(sixtyFour, "fixture");
    environment.put("LD_PRELOAD", thirtyTwo.toString()); InstallStore.normalizeSteamOverlay(environment); assertEquals(sixtyFour.toString(), environment.get("LD_PRELOAD"));
  }
}
