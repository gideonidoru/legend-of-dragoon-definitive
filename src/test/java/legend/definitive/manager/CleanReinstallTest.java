package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CleanReinstallTest {
  @TempDir Path temporary;
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"ordinary owner notes", "not-a-complete-inventory=owner value", "saves/owner.txt=1:deadbeef"})
  void historicalLegacySnapshotRootMetadataNameIsPreservedWithoutProvenOwnership(String bytes) throws Exception {
    var store = new InstallStore(temporary.resolve("historical-root")); var pack = pack("historical-root-pack"); store.install(pack);
    final String id = "snapshot-" + UUID.randomUUID(); final Path snapshot = store.root().resolve("snapshots").resolve(id);
    Files.createDirectory(snapshot); Files.writeString(snapshot.resolve("snapshot-files.properties"), bytes);
    store.install(pack, "", null, InstallProgress.NONE, true); store.verifyInstalled();
    assertEquals(bytes, Files.readString(store.data(store.state()).resolve("recovered-reinstall/snapshots").resolve(id).resolve("snapshot-files.properties")));
  }
  @Test void ownerRootReceiptFilenameSurvivesCompleteReinstallAndRepeat() throws Exception {
    var store = new InstallStore(temporary.resolve("owner-receipt")); var pack = pack("owner-receipt-pack"); store.install(pack);
    final String name = ".reinstall-preserved.properties";
    Files.writeString(store.data(store.state()).resolve(name), "owner receipt notes");
    store.install(pack, "", null, InstallProgress.NONE, true); store.verifyInstalled();
    assertEquals("owner receipt notes", Files.readString(store.data(store.state()).resolve(name)));
    store.install(pack, "", null, InstallProgress.NONE, true); store.verifyInstalled();
    assertEquals("owner receipt notes", Files.readString(store.data(store.state()).resolve(name)));
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"campaign-notes.log", "snapshot-files.properties", ".reinstall-preserved.properties"})
  void repeatedReinstallPreservesDistinctRootNotesAndIdenticalDuplicates(String name) throws Exception {
    var store = new InstallStore(temporary.resolve("repeat-notes")); var pack = pack("repeat-notes-pack");store.install(pack);
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"), "save remains");
    for(int generation=1;generation<=3;generation++) {
      Files.writeString(store.root().resolve(name), "owner version " + generation);
      store.install(pack, "", null, InstallProgress.NONE, true);store.verifyInstalled();
      final Path data=store.data(store.state());
      for(int expected=1;expected<=generation;expected++) {
        final String namespace=expected==1 ? "recovered-reinstall" : "recovered-reinstall-"+expected;
        assertEquals("owner version "+expected,Files.readString(data.resolve(namespace).resolve(name)));
      }
      assertEquals("save remains",Files.readString(data.resolve("saves/owner.txt")));
    }
    Files.writeString(store.root().resolve(name), "owner version 2");
    store.install(pack, "", null, InstallProgress.NONE, true);store.verifyInstalled();
    assertFalse(Files.exists(store.data(store.state()).resolve("recovered-reinstall-4")));
    assertFalse(Files.exists(store.root().resolve(name)));
  }
  @Test void repeatedIdenticalRootRecoveryUsesExistingVerifiedCopy() throws Exception {
    var store=new InstallStore(temporary.resolve("identical-notes"));var pack=pack("identical-pack");store.install(pack);
    for(int attempt=0;attempt<3;attempt++) {
      Files.writeString(store.root().resolve("campaign-notes.log"),"personal campaign notes");
      store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();
      assertEquals("personal campaign notes",Files.readString(store.data(store.state()).resolve("recovered-reinstall/campaign-notes.log")));
      assertFalse(Files.exists(store.data(store.state()).resolve("recovered-reinstall-2")));
    }
  }
  @Test void recoveryFileAndDirectoryCollisionsPreserveOwnerReservedNamespaces() throws Exception {
    for(final boolean blockedRoot:List.of(false,true)) {
      var store=new InstallStore(temporary.resolve("blocked-"+blockedRoot));var pack=pack("blocked-pack-"+blockedRoot);store.install(pack);
      final Path active=store.data(store.state());
      final Path blocker=active.resolve(blockedRoot ? "recovered-reinstall" : "recovered-reinstall/notes");
      Files.createDirectories(blocker.getParent());Files.writeString(blocker,"owner blocking file");
      Files.createDirectories(active.resolve("recovered-reinstall-2/notes"));
      Files.writeString(active.resolve("recovered-reinstall-2/notes/snapshot-files.properties"),"owner reserved namespace");
      Files.createDirectories(store.root().resolve("notes"));
      Files.writeString(store.root().resolve("notes/snapshot-files.properties"),"new nested owner notes");
      store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();
      final Path data=store.data(store.state());
      assertEquals("owner blocking file",Files.readString(data.resolve(active.relativize(blocker))));
      assertEquals("owner reserved namespace",Files.readString(data.resolve("recovered-reinstall-2/notes/snapshot-files.properties")));
      assertEquals("new nested owner notes",Files.readString(data.resolve("recovered-reinstall-3/notes/snapshot-files.properties")));
      store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();
      assertEquals("new nested owner notes",Files.readString(store.data(store.state()).resolve("recovered-reinstall-3/notes/snapshot-files.properties")));
    }
  }
  @Test void existingRecoveryDirectoryAndNewOwnerFileBothSurvive() throws Exception {
    var store=new InstallStore(temporary.resolve("directory-collision"));var pack=pack("directory-collision-pack");store.install(pack);
    final Path recovered=store.data(store.state()).resolve("recovered-reinstall/notes");Files.createDirectories(recovered);
    Files.writeString(recovered.resolve("owner.txt"),"old directory notes");
    Files.writeString(store.root().resolve("notes"),"new owner file");
    store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();
    final Path data=store.data(store.state());
    assertEquals("old directory notes",Files.readString(data.resolve("recovered-reinstall/notes/owner.txt")));
    assertEquals("new owner file",Files.readString(data.resolve("recovered-reinstall-2/notes")));
  }
  @Test void interruptedCleanupRetainsBothRecoveryVersionsAndResumesWithReceipt() throws Exception {
    var store=new InstallStore(temporary.resolve("collision-resume"));var pack=pack("collision-resume-pack");store.install(pack);
    Files.writeString(store.root().resolve("campaign-notes.log"),"earlier notes");
    store.install(pack,"",null,InstallProgress.NONE,true);
    Files.writeString(store.root().resolve("campaign-notes.log"),"new notes");
    assertThrows(java.io.InterruptedIOException.class,()->store.install(pack,"",null,update->{
      if(update.phase().equals("Cleaning old installation files"))Thread.currentThread().interrupt();
    },true));
    Thread.interrupted();assertTrue(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    var reopened=new InstallStore(store.root());reopened.verifyInstalled();
    final Path data=reopened.data(reopened.state());
    assertEquals("earlier notes",Files.readString(data.resolve("recovered-reinstall/campaign-notes.log")));
    assertEquals("new notes",Files.readString(data.resolve("recovered-reinstall-2/campaign-notes.log")));
    assertFalse(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    assertFalse(Files.exists(store.root().resolve("campaign-notes.log")));
  }
  @Test void interruptedLegacyReceiptJournalStillRecovers() throws Exception {
    var store = new InstallStore(temporary.resolve("legacy-receipt")); var pack = pack("legacy-receipt-pack"); store.install(pack);
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"), "legacy save");
    assertThrows(java.io.InterruptedIOException.class, () -> store.install(pack, "", null, update -> {
      if(update.phase().equals("Cleaning old installation files")) Thread.currentThread().interrupt();
    }, true));
    Thread.interrupted();
    var journal = PackageManifest.readProperties(store.root().resolve(CleanReinstall.JOURNAL));
    final Path data = store.root().resolve("data").resolve(journal.getProperty("data"));
    Files.move(store.root().resolve(".reinstall-receipt-" + journal.getProperty("data") + ".properties"), data.resolve(".reinstall-preserved.properties"));
    journal.setProperty("format", "1"); InstallStore.atomicProperties(store.root().resolve(CleanReinstall.JOURNAL), journal);
    final var recovered = new InstallStore(store.root()); recovered.verifyInstalled();
    assertFalse(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    assertEquals("legacy save", Files.readString(recovered.data(recovered.state()).resolve("saves/owner.txt")));
  }
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
    Files.writeString(oldWorkspace.resolve("files/owner-note.txt"), "extraction-adjacent note");
    Files.createDirectories(oldWorkspace.resolve("files/SECT/DRGN0.BIN/1")); Files.writeString(oldWorkspace.resolve("files/SECT/DRGN0.BIN/1/0"), "old extracted data");
    Files.writeString(damagedRelease.resolve("bundled-mods/MyCustomMod.jar"), "owner custom mod");
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
    Files.writeString(store.root().resolve("campaign-notes.log"), "owner journal");
    Files.createDirectories(store.root().resolve("owner-folder")); Files.writeString(store.root().resolve("owner-folder/snapshot-files.properties"), "owner properties");
    Files.createDirectories(store.root().resolve("files/SECT/DRGN0.BIN/1")); Files.writeString(store.root().resolve("files/SECT/DRGN0.BIN/1/0"), "old flat extracted data");
    Files.writeString(store.root().resolve("files/owner-note.txt"), "flat note"); Files.writeString(store.root().resolve("files/owner.iso"), "flat disc");
    Files.createDirectories(store.root().resolve("gfx")); Files.writeString(store.root().resolve("gfx/personal-note.txt"), "flat gfx note");
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
      try(final var files = Files.walk(data)) { assertFalse(files.anyMatch(file -> file.getFileName().toString().equals(family + "-v0.1.0.jar")), "No old bundled bytes remain in any preserved generation"); }
    }
    assertFalse(Files.exists(data.resolve("mods/renamed.jar")));
    try(final var files = Files.walk(data)) { assertFalse(files.anyMatch(file -> file.getFileName().toString().equals("renamed.jar"))); }
    assertEquals("old save",Files.readString(data.resolve("recovered-reinstall/data/"+oldData.getFileName()+"/saves/owner.txt")));
    assertEquals("private disc",Files.readString(store.root().resolve("isos/owner.iso")));
    assertEquals("release note", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/owner-note.txt")));
    assertEquals("release disc", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/backup.iso")));
    assertEquals("workspace note", Files.readString(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/owner-folder/note.txt")));
    assertEquals("workspace disc", Files.readString(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/backup.iso")));
    assertEquals("extraction-adjacent note", Files.readString(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/files/owner-note.txt")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/workspaces/" + oldWorkspace.getFileName() + "/files/SECT/DRGN0.BIN/1/0")));
    assertEquals("owner custom mod", Files.readString(data.resolve("recovered-reinstall/releases/" + before.getProperty("version") + "/bundled-mods/MyCustomMod.jar")));
    assertEquals("another disc",Files.readString(data.resolve("recovered-reinstall/loose.iso")));
    assertEquals("personal note",Files.readString(data.resolve("recovered-reinstall/owner-note.txt")));
    assertEquals("owner journal", Files.readString(data.resolve("recovered-reinstall/campaign-notes.log")));
    assertEquals("owner properties", Files.readString(data.resolve("recovered-reinstall/owner-folder/snapshot-files.properties")));
    assertEquals("legacy mod",Files.readString(data.resolve("recovered-reinstall/mods/custom.jar")));
    assertEquals("flat note", Files.readString(data.resolve("recovered-reinstall/files/owner-note.txt")));
    assertEquals("flat disc", Files.readString(data.resolve("recovered-reinstall/files/owner.iso")));
    assertEquals("flat gfx note", Files.readString(data.resolve("recovered-reinstall/gfx/personal-note.txt")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/files/SECT/DRGN0.BIN/1/0")));
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
    Files.writeString(store.data(store.state()).resolve(".reinstall-preserved.properties"),"owner receipt notes");
    Files.writeString(store.root().resolve("lod-game-old.jar"),"old app");
    assertThrows(java.io.InterruptedIOException.class,()->store.install(pack,"",null,update->{if(update.phase().equals("Cleaning old installation files"))Thread.currentThread().interrupt();},true));
    Thread.interrupted();assertTrue(Files.exists(store.root().resolve(CleanReinstall.JOURNAL)));
    var resumed=new InstallStore(store.root());resumed.verifyInstalled();
    assertFalse(Files.exists(resumed.root().resolve(CleanReinstall.JOURNAL)));assertFalse(Files.exists(resumed.root().resolve("lod-game-old.jar")));
    assertEquals("save",Files.readString(resumed.data(resumed.state()).resolve("saves/owner.txt")));
    assertEquals("owner receipt notes",Files.readString(resumed.data(resumed.state()).resolve(".reinstall-preserved.properties")));
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
    Files.writeString(data.resolve(".reinstall-preserved.properties"), "owner receipt notes");
    Files.writeString(root.resolve("lod-game-old.jar"), "old app");
    var next = pack("bootstrap-next");
    var failing = new InstallStore(root, (staged,target) -> { throw new IOException("Injected launcher write failure"); });
    assertThrows(IOException.class, () -> failing.install(next,"",null,InstallProgress.NONE,true));
    assertEquals(before, original.state()); assertEquals("original save",Files.readString(data.resolve("saves/owner.txt")));
    assertTrue(Files.exists(root.resolve("lod-game-old.jar"))); assertFalse(Files.exists(root.resolve(CleanReinstall.JOURNAL)));
    assertEquals("owner receipt notes", Files.readString(data.resolve(".reinstall-preserved.properties")));
    original.install(next, "", null, InstallProgress.NONE, true); original.verifyInstalled();
    assertEquals("owner receipt notes", Files.readString(original.data(original.state()).resolve(".reinstall-preserved.properties")));
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
    Files.writeString(release.resolve("libs/dependency.jar"), "old library");
    if(mode.equals("missing")) Files.delete(release.resolve(PackageManifest.METADATA));
    if(mode.equals("corrupt")) Files.writeString(release.resolve(PackageManifest.HASHES),"damaged inventory");
    Path partial = null;
    if(mode.equals("partial")) {partial=Files.createDirectory(store.root().resolve("releases/partial-old"));Files.writeString(partial.resolve("config.conf"),"partial config");}
    store.install(pack,"",null,InstallProgress.NONE,true);store.verifyInstalled();var data=store.data(store.state());
    assertEquals("stray config",Files.readString(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/config.conf")));
    assertEquals("disc",Files.readString(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/owner.iso")));
    assertFalse(Files.exists(data.resolve("recovered-reinstall/releases/"+release.getFileName()+"/libs/dependency.jar")));
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

  @Test void archiveRouteDoesNotPreserveCurrentOrInterruptedUpdateArchives() throws Exception {
    var store = new InstallStore(temporary.resolve("archive")); var pack = pack("archive-pack"); store.install(pack);
    Files.writeString(store.data(store.state()).resolve("saves/owner.txt"),"save"); Files.writeString(store.root().resolve("isos/owner.iso"),"disc");
    var archive = Files.createTempFile(store.root(),".update-",".zip");
    try(var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive));var paths=Files.walk(pack)) {
      for(var path:paths.filter(Files::isRegularFile).toList()) {zip.putNextEntry(new java.util.zip.ZipEntry(pack.relativize(path).toString()));Files.copy(path,zip);zip.closeEntry();}
    }
    var stale=store.root().resolve(".update-123.zip");Files.writeString(stale,"interrupted old application archive");
    store.install(archive,"",null,InstallProgress.NONE,true);store.verifyInstalled();var data=store.data(store.state());
    assertFalse(Files.exists(archive));assertFalse(Files.exists(stale));
    assertFalse(Files.exists(data.resolve("recovered-reinstall").resolve(archive.getFileName())));assertFalse(Files.exists(data.resolve("recovered-reinstall/.update-123.zip")));
    assertEquals("save",Files.readString(data.resolve("saves/owner.txt")));assertEquals("disc",Files.readString(store.root().resolve("isos/owner.iso")));
  }

  @Test void invalidatedExtractionBackupKeepsUnknownPersonalFiles() throws Exception {
    var store = new InstallStore(temporary.resolve("extraction-backup")); var pack = pack("extraction-backup-pack"); store.install(pack);
    var workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"),"5");
    Files.writeString(workspace.resolve("files/owner-note.txt"),"backup note"); Files.writeString(workspace.resolve("files/config.conf"),"backup config");
    Files.writeString(workspace.resolve("files/owner.iso"),"backup disc");
    Files.createDirectories(workspace.resolve("files/SECT/DRGN0.BIN/1")); Files.writeString(workspace.resolve("files/SECT/DRGN0.BIN/1/0"),"generated data");
    store.invalidatePreparedDiscs();
    Path backup;try(var paths=Files.list(store.root())){backup=paths.filter(path->path.getFileName().toString().startsWith("disc-extraction-recovery-")).findFirst().orElseThrow();}
    store.install(pack,"",null,InstallProgress.NONE,true);var data=store.data(store.state());var preserved=data.resolve("recovered-reinstall").resolve(backup.getFileName());
    assertEquals("backup note",Files.readString(preserved.resolve("owner-note.txt")));assertEquals("backup config",Files.readString(preserved.resolve("config.conf")));assertEquals("backup disc",Files.readString(preserved.resolve("owner.iso")));
    assertFalse(Files.exists(backup));assertFalse(Files.exists(preserved.resolve("SECT/DRGN0.BIN/1/0")));store.verifyInstalled();
  }

  @Test void fullBundledInstallQuarantinesUninspectableOwnerModAndRemainsUsable() throws Exception {
    var pack = pack("seven-families");
    for(final String family : List.of("EnvHD", "CharHD", "UIHD", "FxHD", "FMVHD", "ModelsHD", "Skurfa")) {
      Files.writeString(pack.resolve("bundled-mods/" + family + "-v0.1.0.jar"), "fresh " + family);
    }
    new legend.definitive.fmv.FmvSplitFixture().jar(pack, true, "legacy synthetic films");
    Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES));
    ManagerMain.makeManifest(pack, PackageManifest.hostPlatform(), "seven-family-fixture");
    var store = new InstallStore(temporary.resolve("seven-install")); store.install(pack);
    var old = store.data(store.state());
    final byte[] uncertain = new byte[64]; uncertain[0] = 0x50; uncertain[1] = 0x4b; uncertain[2] = 3; uncertain[3] = 4;
    Files.write(old.resolve("mods/uncertain.jar"), uncertain);
    for(final String name : List.of("custom.jar", "renamed.jar")) {
      try(var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(old.resolve("mods/" + name)))) {
        zip.putNextEntry(new java.util.zip.ZipEntry(name.equals("custom.jar") ? "owner/CustomMod.class" : "fxhd/FxHdMod.class"));
        zip.write(new byte[]{1, 2, 3}); zip.closeEntry();
      }
    }
    final byte[] custom = Files.readAllBytes(old.resolve("mods/custom.jar"));
    store.install(pack, "", null, InstallProgress.NONE, true);
    var fresh = store.data(store.state());
    assertFalse(Files.exists(fresh.resolve("mods/uncertain.jar"))); assertFalse(Files.exists(fresh.resolve("mods/renamed.jar")));
    try(var paths = Files.walk(fresh.resolve("recovered-reinstall/inactive-mods"))) {
      var preserved = paths.filter(Files::isRegularFile).toList(); assertEquals(1, preserved.size()); assertArrayEquals(uncertain, Files.readAllBytes(preserved.getFirst()));
    }
    assertArrayEquals(custom, Files.readAllBytes(fresh.resolve("mods/custom.jar")));
    assertFalse(Files.exists(store.root().resolve(CleanReinstall.JOURNAL))); assertFalse(Files.exists(old));
    var reopened = new InstallStore(store.root()); reopened.verifyInstalled();
    var workspace = reopened.prepareLaunch();
    assertArrayEquals(custom, Files.readAllBytes(workspace.resolve("mods/custom.jar")));
    assertFalse(Files.exists(workspace.resolve("mods/uncertain.jar"))); assertFalse(Files.exists(workspace.resolve("mods/renamed.jar")));
    try(var paths = Files.list(workspace.resolve("mods"))) { assertEquals(9, paths.count()); }
    try(var paths = Files.list(store.root().resolve("releases"))) { assertEquals(1, paths.count()); }
    try(var paths = Files.list(store.root().resolve("data"))) { assertEquals(1, paths.count()); }
    try(var paths = Files.list(store.root().resolve("workspaces"))) { assertEquals(1, paths.count()); }
  }

}
