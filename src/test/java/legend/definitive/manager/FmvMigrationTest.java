package legend.definitive.manager;

import legend.definitive.fmv.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

final class FmvMigrationTest {
  @TempDir Path root;
  private record Setup(Path current, Path staged, PackageManifest next, FmvAssetManifest assets) { }
  private Setup setup(final String id, final FmvSplitFixture fixture, final String defect) throws Exception {
    this.root=this.root.toRealPath();final var packages=new InstallStoreTest();packages.temporary=this.root;
    final Path old=packages.pack(id+"-old",PackageManifest.hostPlatform());
    final Path jar=old.resolve("bundled-mods/FMVHD-v0.1.0.jar");
    final var legacy=fixture.manifest(true);
    if(defect.equals("source") || defect.equals("shared-source")) legacy.setProperty("OPENH.sourceSha256","0".repeat(64));
    try(final var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
      zip.putNextEntry(new ZipEntry("fmvhd/assets.properties"));zip.write(FmvSplitFixture.bytes(legacy));zip.closeEntry();
      for(final var entry:fixture.movies.entrySet()) {
        if(defect.equals("missing") && entry.getKey().equals("OPENH")) continue;
        zip.putNextEntry(new ZipEntry("fmvhd/videos/"+entry.getKey()+".mp4"));
        zip.write(defect.equals("payload") && entry.getKey().equals("OPENH") ? new byte[entry.getValue().length]:entry.getValue());zip.closeEntry();
      }
      if(defect.equals("unsafe")) {zip.putNextEntry(new ZipEntry("../untrusted"));zip.write(1);zip.closeEntry();}
    }
    if(defect.equals("duplicate")) {
      final byte[] bytes=Files.readAllBytes(jar);final byte[] from="DEMOH".getBytes(),to="DEMO2".getBytes();
      for(int i=0;i<=bytes.length-from.length;i++) {
        boolean match=true;for(int j=0;j<from.length;j++) if(bytes[i+j]!=from[j]) match=false;
        if(match) System.arraycopy(to,0,bytes,i,to.length);
      }
      Files.write(jar,bytes);
    }
    if(defect.equals("truncated")) Files.write(jar,Arrays.copyOf(Files.readAllBytes(jar),100));
    Files.delete(old.resolve(PackageManifest.METADATA));Files.delete(old.resolve(PackageManifest.HASHES));ManagerMain.makeManifest(old,PackageManifest.hostPlatform(),"fixture");
    final var store=new InstallStore(this.root.resolve(id+"-install"));store.install(old);
    final Path current=store.root().resolve("releases/"+store.state().getProperty("version"));
    final Path target=packages.pack(id+"-next",PackageManifest.hostPlatform());fixture.split(target,"small code");
    Files.delete(target.resolve(PackageManifest.METADATA));Files.delete(target.resolve(PackageManifest.HASHES));ManagerMain.makeManifest(target,PackageManifest.hostPlatform(),"fixture");
    final Path staged=this.root.resolve(id+"-staged");Files.createDirectories(staged.resolve("gfx/fmvhd/videos"));
    Files.copy(target.resolve(FmvAssetManifest.FILE),staged.resolve(FmvAssetManifest.FILE));
    final var assets=FmvAssetManifest.read(new ByteArrayInputStream(FmvSplitFixture.bytes(fixture.manifest(false))));
    return new Setup(current,staged,PackageManifest.read(target),assets);
  }
  private boolean importFilm(final FmvMigration importer, final Setup setup, final String name) throws Exception {
    final var film=setup.assets.films().get(name);
    return importer.importMovie(film.path(),film.source().sha256(),film.source().bytes(),setup.staged.resolve(film.path()));
  }
  @Test void missingUnsafeDuplicateAndTruncatedArchivesAreRejectedWithoutPartialFiles() throws Exception {
    for(final String defect:List.of("missing","unsafe","duplicate","truncated")) {
      final var setup=setup(defect,new FmvSplitFixture(),defect);
      try(final var importer=new FmvMigration(setup.current,setup.staged,setup.next,true)) {assertFalse(importFilm(importer,setup,"OPENH"),defect);}
      try(final var files=Files.list(setup.staged.resolve("gfx/fmvhd/videos"))) {assertEquals(0,files.count());}
    }
  }
  @Test void nativeSourceMismatchAndCorruptEntryRejectOnlyTheAffectedMovie() throws Exception {
    for(final String defect:List.of("source","payload")) {
      final var setup=setup(defect,new FmvSplitFixture(),defect);
      try(final var importer=new FmvMigration(setup.current,setup.staged,setup.next,true)) {
        assertFalse(importFilm(importer,setup,"OPENH"));assertTrue(importFilm(importer,setup,"DEMOH"));
      }
      try(final var files=Files.list(setup.staged.resolve("gfx/fmvhd/videos"))) {assertEquals(1,files.count());}
    }
  }
  @Test void sharedPayloadCanImportThroughEitherMatchingNativeSourceRecord() throws Exception {
    final var fixture=new FmvSplitFixture();fixture.movies.put("DEMOH",fixture.movies.get("OPENH"));
    final var setup=setup("shared",fixture,"shared-source");
    try(final var importer=new FmvMigration(setup.current,setup.staged,setup.next,true)) {assertTrue(importFilm(importer,setup,"OPENH"));}
  }
  @Test void closedDisabledCancelledAndEscapingDestinationsCannotWrite() throws Exception {
    final var setup=setup("safe",new FmvSplitFixture(),"");
    final var importer=new FmvMigration(setup.current,setup.staged,setup.next,true);importer.close();
    assertThrows(IOException.class,()->importFilm(importer,setup,"OPENH"));
    try(final var disabled=new FmvMigration(setup.current,setup.staged,setup.next,false)) {assertFalse(importFilm(disabled,setup,"OPENH"));}
    try(final var enabled=new FmvMigration(setup.current,setup.staged,setup.next,true)) {
      final var film=setup.assets.films().get("OPENH");
      assertThrows(IOException.class,()->enabled.importMovie(film.path(),film.source().sha256(),film.source().bytes(),this.root.resolve("outside")));
      Thread.currentThread().interrupt();
      try {assertThrows(InterruptedException.class,()->importFilm(enabled,setup,"OPENH"));} finally {Thread.interrupted();}
      final Path videos=setup.staged.resolve("gfx/fmvhd/videos");Files.delete(videos);Files.createSymbolicLink(videos,this.root);
      assertThrows(IOException.class,()->importFilm(enabled,setup,"OPENH"));
    }
    assertFalse(Files.exists(this.root.resolve("outside")));
  }
}
