package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CleanReinstallTest {
  @TempDir Path temporary;
  @org.junit.jupiter.api.BeforeEach void realTemporary() throws Exception { temporary = temporary.toRealPath(); }
  private Path pack(String name) throws Exception {
    var fixture = new InstallStoreTest(); fixture.temporary = temporary.toRealPath();
    return fixture.pack(name, PackageManifest.hostPlatform());
  }
  @Test void cleanReinstallConsolidatesConflictingPersonalGenerationsAndRemovesApplicationDebris() throws Exception {
    var store = new InstallStore(temporary.resolve("install"));
    var first = pack("one"); store.install(first);
    var oldData = store.data(store.state()); Files.writeString(oldData.resolve("saves/owner.txt"), "old save");
    Files.writeString(oldData.resolve("config.conf"), "old config");
    var oldWorkspace = store.prepareLaunch(); Files.writeString(oldWorkspace.resolve("files/version"), "5");
    var second = pack("two"); store.install(second);
    var before = store.state(); var current = store.data(before);
    final Path damagedRelease = store.root().resolve("releases").resolve(before.getProperty("version"));
    Files.writeString(damagedRelease.resolve("config.conf"), "stray release config");
    Files.writeString(damagedRelease.resolve("owner-note.txt"), "release note");
    Files.writeString(damagedRelease.resolve("backup.iso"), "release disc");
    Files.createDirectories(oldWorkspace.resolve("owner-folder")); Files.writeString(oldWorkspace.resolve("owner-folder/note.txt"), "workspace note");
    Files.writeString(oldWorkspace.resolve("backup.iso"), "workspace disc");
    Files.writeString(current.resolve("saves/owner.txt"), "current save");
    Files.writeString(current.resolve("config.conf"), "current config");
    Files.writeString(current.resolve("mods/owner.jar"), "custom mod");
    for(final String family : List.of("EnvHD", "CharHD", "UIHD", "FxHD", "FMVHD", "ModelsHD", "Skurfa")) Files.writeString(current.resolve("mods/" + family + "-v0.1.0.jar"), family);
    try(final var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(current.resolve("mods/renamed.jar")))) {
      zip.putNextEntry(new java.util.zip.ZipEntry("envhd/EnvHdMod.class")); zip.write(new byte[]{1}); zip.closeEntry();
    }
    Files.writeString(store.root().resolve("isos/owner.iso"), "private disc");
    Files.writeString(store.root().resolve("loose.iso"), "another disc");
    Files.writeString(store.root().resolve("owner-note.txt"), "personal note");
    Files.createDirectories(store.root().resolve("mods")); Files.writeString(store.root().resolve("mods/custom.jar"), "legacy mod");
    Files.writeString(store.root().resolve("lod-game-obsolete.jar"), "stray old app");
    final List<String> stale = List.of("Definitive-Installer.zip", "Legend-of-Dragoon-Definitive-linux-x64.zip", "Definitive-Contents-macos-arm64.zip", "Env1HD-v0.1.0.zip", "FMVHD-v0.1.0.jar");
    for(final String archive : stale) Files.writeString(store.root().resolve(archive), "obsolete app bytes");
    Files.createDirectories(store.root().resolve(".source-build-obsolete")); Files.writeString(store.root().resolve(".source-build-obsolete/temp"), "old source");
    String result = store.install(second,"",null,InstallProgress.NONE,true);
    assertTrue(result.contains("Old application versions")); store.verifyInstalled();
    var state = store.state(); var data = store.data(state);
    assertEquals("current save",Files.readString(data.resolve("saves/owner.txt")));
    assertEquals("current config",Files.readString(data.resolve("config.conf")));
    assertEquals("stray release config", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/config.conf")));
    assertEquals("custom mod",Files.readString(data.resolve("mods/owner.jar")));
    for(final String family : List.of("EnvHD", "CharHD", "UIHD", "FxHD", "FMVHD", "ModelsHD", "Skurfa")) {
      assertFalse(Files.exists(data.resolve("mods/" + family + "-v0.1.0.jar")));
      assertEquals(family, Files.readString(data.resolve("recovered-reinstall/disabled-bundled-mods/" + family + "-v0.1.0.jar")));
    }
    assertFalse(Files.exists(data.resolve("mods/renamed.jar")));
    assertTrue(Files.exists(data.resolve("recovered-reinstall/disabled-bundled-mods/renamed.jar")));
    assertEquals("old save",Files.readString(data.resolve("recovered-reinstall/data/"+oldData.getFileName()+"/saves/owner.txt")));
    assertEquals("private disc",Files.readString(store.root().resolve("isos/owner.iso")));
    assertEquals("release note", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/owner-note.txt")));
    assertEquals("release disc", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/backup.iso")));
    assertEquals("workspace note", Files.readString(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/owner-folder/note.txt")));
    assertEquals("workspace disc", Files.readString(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/backup.iso")));
    assertEquals("another disc",Files.readString(data.resolve("recovered-reinstall/loose.iso")));
    assertEquals("personal note",Files.readString(data.resolve("recovered-reinstall/owner-note.txt")));
    assertEquals("legacy mod",Files.readString(data.resolve("recovered-reinstall/mods/custom.jar")));
    for(String name:List.of("releases","data","workspaces")) try(var paths=Files.list(store.root().resolve(name))) { assertEquals(1,paths.count(),name); }
    try(var paths=Files.list(store.root().resolve("snapshots"))) {assertEquals(0,paths.count());}
    assertFalse(Files.exists(oldWorkspace)); assertFalse(Files.exists(store.root().resolve("lod-game-obsolete.jar")));
    assertFalse(Files.exists(store.root().resolve(".source-build-obsolete")));
    for(final String archive : stale) { assertFalse(Files.exists(store.root().resolve(archive))); assertFalse(Files.exists(data.resolve("recovered-reinstall/"+archive))); }
    assertFalse(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    assertThrows(IOException.class,store::rollback); assertFalse(store.discsPrepared());
    assertTrue(state.stringPropertyNames().stream().noneMatch(k->k.startsWith("previous")||k.startsWith("retained")));
    assertEquals(state,PackageManifest.readProperties(store.root().resolve(".state-last-good.properties")));
  }
  @Test void corruptionAndCancellationBeforeActivationDoNotCleanOriginals() throws Exception {
    var store=new InstallStore(temporary.resolve("failed"));var pack=pack("failed-pack");store.install(pack);
    var data=store.data(store.state());Files.writeString(data.resolve("saves/owner.txt"),"save");
    Files.writeString(store.root().resolve("lod-game-old.jar"),"old");var before=store.state();
    Files.writeString(pack.resolve("lod-game-test.jar"),"corrupt");
    assertThrows(IOException.class,()->store.install(pack,"",null,InstallProgress.NONE,true));
    assertEquals(before,store.state());assertEquals("save",Files.readString(data.resolve("saves/owner.txt")));assertTrue(Files.exists(store.root().resolve("lod-game-old.jar")));
    var valid=pack("cancel-pack");Thread.currentThread().interrupt();
    try {assertThrows(IOException.class,()->store.install(valid,"",null,InstallProgress.NONE,true));} finally {Thread.interrupted();}
    assertEquals(before,store.state());assertTrue(Files.exists(store.root().resolve("lod-game-old.jar")));
  }
  @Test void personalLinkFailsClosedAndNeverTouchesOutsideFiles() throws Exception {
    var store=new InstallStore(temporary.resolve("linked"));var pack=pack("linked-pack");store.install(pack);
    var outside=temporary.resolve("outside");Files.writeString(outside,"outside content");
    Files.createSymbolicLink(store.root().resolve("owner-file"),outside);var before=store.state();
    assertThrows(IOException.class,()->store.install(pack,"",null,InstallProgress.NONE,true));
    assertEquals(before,store.state());assertEquals("outside content",Files.readString(outside));assertTrue(Files.isSymbolicLink(store.root().resolve("owner-file")));
  }
  @Test void interruptedCleanupResumesUnderOperationLock() throws Exception {
    var store=new InstallStore(temporary.resolve("resume"));var pack=pack("resume-pack");store.install(pack);
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"),"save");
    Files.writeString(store.root().resolve("lod-game-old.jar"),"old app");
    assertThrows(java.io.InterruptedIOException.class,()->store.install(pack,"",null,update->{if(update.phase().equals("Cleaning old installation files"))Thread.currentThread().interrupt();},true));
    Thread.interrupted();assertTrue(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    var resumed=new InstallStore(store.root());resumed.verifyInstalled();
    assertFalse(Files.exists(resumed.root().resolve(CleanReinstall.JOURNAL)));assertFalse(Files.exists(resumed.root().resolve("lod-game-old.jar")));
    assertEquals("save",Files.readString(resumed.data(resumed.state()).resolve("saves/owner.txt")));
  }
  @Test void gameLeaseBlocksCleanReinstallBeforeAnyPreservationOrDeletion() throws Exception {
    var store = new InstallStore(temporary.resolve("busy")); var pack = pack("busy-pack"); store.install(pack);
    var before = store.state(); Files.writeString(store.root().resolve("lod-game-old.jar"), "old app");
    try(var channel = java.nio.channels.FileChannel.open(store.root().resolve(".game-lock"), StandardOpenOption.WRITE);
        var lease = channel.lock()) {
      assertThrows(GameLease.Busy.class, () -> store.install(pack,"",null,InstallProgress.NONE,true));
    }
    assertEquals(before,store.state()); assertTrue(Files.exists(store.root().resolve("lod-game-old.jar")));
  }
  @Test void bootstrapFailureKeepsOriginalPrivateDataAndDoesNotRunCleanup() throws Exception {
    var root = temporary.resolve("bootstrap"); var original = new InstallStore(root); var pack = pack("bootstrap-pack"); original.install(pack);
    var before = original.state(); var data = original.data(before); Files.writeString(data.resolve("saves/owner.txt"), "original save");
    Files.writeString(root.resolve("lod-game-old.jar"), "old app");
    var next = pack("bootstrap-next");
    var failing = new InstallStore(root, (staged,target) -> { throw new IOException("Injected launcher write failure"); });
    assertThrows(IOException.class, () -> failing.install(next,"",null,InstallProgress.NONE,true));
    assertEquals(before, original.state()); assertEquals("original save",Files.readString(data.resolve("saves/owner.txt")));
    assertTrue(Files.exists(root.resolve("lod-game-old.jar"))); assertFalse(Files.exists(root.resolve(CleanReinstall.JOURNAL)));
  }
  @Test void damagedPreservationReceiptPreventsResumedDeletion() throws Exception {
    var store = new InstallStore(temporary.resolve("receipt")); var pack = pack("receipt-pack"); store.install(pack);
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"), "save"); Files.writeString(store.root().resolve("lod-game-old.jar"), "old app");
    assertThrows(java.io.InterruptedIOException.class, () -> store.install(pack,"",null,update -> { if(update.phase().equals("Cleaning old installation files")) Thread.currentThread().interrupt(); },true));
    Thread.interrupted(); var state = PackageManifest.readProperties(store.root().resolve("state.properties"));
    Files.writeString(store.root().resolve("data").resolve(state.getProperty("data")).resolve("saves/owner.txt"), "changed after preservation");
    assertThrows(IOException.class, () -> new InstallStore(store.root()));
    assertTrue(Files.exists(store.root().resolve("lod-game-old.jar"))); assertTrue(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
  }

  @Test void linkedReleaseRecoveryNeverReadsOutsidePersonalData() throws Exception {
    var store = new InstallStore(temporary.resolve("recovery-link")); var pack = pack("recovery-link-pack"); store.install(pack);
    var outside = Files.createDirectories(temporary.resolve("outside-recovery/saves")); Files.writeString(outside.resolve("owner.txt"), "foreign save");
    Files.createSymbolicLink(store.root().resolve("release-recovery-" + UUID.randomUUID()), outside.getParent());
    var before = store.state(); assertThrows(IOException.class, () -> store.install(pack,"",null,InstallProgress.NONE,true));
    assertEquals(before,store.state()); assertEquals("foreign save",Files.readString(outside.resolve("owner.txt")));
  }
  @Test void unknownPersonalLinkInsideOldReleaseFailsBeforeCleanup() throws Exception {
    var store = new InstallStore(temporary.resolve("nested-link")); var pack = pack("nested-link-pack"); store.install(pack);
    var outside = temporary.resolve("outside-note"); Files.writeString(outside,"foreign note");
    var release = store.root().resolve("releases").resolve(store.state().getProperty("version")); Files.createSymbolicLink(release.resolve("owner-note"),outside);
    var before = store.state(); assertThrows(IOException.class, () -> store.install(pack,"",null,InstallProgress.NONE,true));
    assertEquals(before,store.state()); assertEquals("foreign note",Files.readString(outside));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"missing", "corrupt", "partial"})
  void damagedOldMetadataDoesNotBlockPersonalPreservation(String mode) throws Exception {
    var store = new InstallStore(temporary.resolve("metadata-"+mode)); var pack = pack("metadata-pack-"+mode); store.install(pack);
    var release = store.root().resolve("releases").resolve(store.state().getProperty("version"));
    Files.writeString(release.resolve("config.conf"),"stray config"); Files.writeString(release.resolve("owner.iso"),"disc");
    Files.writeString(release.resolve("libs/obsolete.jar"), "old library");
    if(mode.equals("missing")) Files.delete(release.resolve(PackageManifest.METADATA));
    if(mode.equals("corrupt")) Files.writeString(release.resolve(PackageManifest.HASHES),"damaged inventory");
    Path partial = null;
    if(mode.equals("partial")) {partial=Files.createDirectory(store.root().resolve("releases/partial-old"));Files.writeString(partial.resolve("config.conf"),"partial config");}
    store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();var data=store.data(store.state());
    assertEquals("stray config",Files.readString(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/config.conf")));
    assertEquals("disc",Files.readString(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/owner.iso")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/libs/dependency.jar")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/libs/obsolete.jar")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/bundled-mods/Skurfa.jar")));
    if(partial!=null)assertEquals("partial config",Files.readString(data.resolve("recovered-reinstall/releases/partial-old/config.conf")));
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"incomplete", "wrong"})
  void activeDataCopyMustEqualOriginalInventory(String mode) throws Exception {
    var store = new InstallStore(temporary.resolve("copy-"+mode)); var pack = pack("copy-pack-"+mode); store.install(pack);
    var active=store.data(store.state());Files.writeString(active.resolve("saves/owner.txt"),"original");var before=store.state();
    assertThrows(IOException.class,()->CleanReinstall.prepare(store,before,(source,target)-> {
      InstallStore.copyTree(source,target);
      if(mode.equals("incomplete"))Files.delete(target.resolve("saves/owner.txt"));else Files.writeString(target.resolve("saves/owner.txt"),"incorrect");
    }));
    assertEquals(before,store.state());assertEquals("original",Files.readString(active.resolve("saves/owner.txt")));
  }

}
