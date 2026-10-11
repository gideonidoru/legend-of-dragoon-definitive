package legend.definitive.fmv;

import fmvhd.VideoCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

final class VideoCacheTest {
  @TempDir Path root;
  @BeforeEach void canonicalTemp() throws Exception { this.root = this.root.toRealPath(); }
  private static final byte[] DATA = "synthetic movie bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  private static String digest() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(DATA)); }

  @Test void validatesAndReusesCompleteCopy() throws Exception {
    final Path cache = this.root.resolve("cache");
    final Path video = VideoCache.materialize(cache, digest(), DATA.length, new ByteArrayInputStream(DATA));
    assertArrayEquals(DATA, Files.readAllBytes(video));
    assertEquals(video, VideoCache.materialize(cache, digest(), DATA.length, new ByteArrayInputStream(new byte[0])));
  }

  @Test void interruptedCopyDoesNotPublishOrLeakTemporaryFiles() throws Exception {
    final Path cache = this.root.resolve("cache");
    assertThrows(IOException.class, () -> VideoCache.materialize(cache, digest(), DATA.length, new ByteArrayInputStream(new byte[]{1})));
    try(final var files = Files.list(cache)) { assertEquals(0, files.count()); }
  }

  @Test void rejectsChecksumMismatchAndOversizeInput() throws Exception {
    final Path cache = this.root.resolve("cache");
    assertThrows(IOException.class, () -> VideoCache.materialize(cache, "0".repeat(64), DATA.length, new ByteArrayInputStream(DATA)));
    assertThrows(IOException.class, () -> VideoCache.materialize(cache, digest(), 1, new ByteArrayInputStream(DATA)));
    try(final var files = Files.list(cache)) { assertEquals(0, files.count()); }
  }

  @Test void repairsCorruptCacheAndRejectsLinkedTargets() throws Exception {
    final Path cache = this.root.resolve("cache"); Files.createDirectory(cache);
    final Path target = cache.resolve(digest() + ".mp4"); Files.write(target, new byte[]{1});
    VideoCache.materialize(cache, digest(), DATA.length, new ByteArrayInputStream(DATA));
    assertArrayEquals(DATA, Files.readAllBytes(target));
    Files.delete(target);
    final Path sentinel = this.root.resolve("sentinel"); Files.write(sentinel, DATA);
    Files.createSymbolicLink(target, sentinel);
    assertThrows(IOException.class, () -> VideoCache.materialize(cache, digest(), DATA.length, new ByteArrayInputStream(DATA)));
    assertArrayEquals(DATA, Files.readAllBytes(sentinel));
  }

  @Test void rejectsLinkedParentAndUnsafeManifestName() throws Exception {
    final Path actual = this.root.resolve("actual"); Files.createDirectory(actual);
    final Path link = this.root.resolve("link"); Files.createSymbolicLink(link, actual);
    assertThrows(IOException.class, () -> VideoCache.materialize(link.resolve("cache"), digest(), DATA.length, new ByteArrayInputStream(DATA)));
    assertThrows(IOException.class, () -> VideoCache.materialize(actual, "../escape", DATA.length, new ByteArrayInputStream(DATA)));
  }
  @Test void installedMovieUsesVerifiedManagedArtworkLinkWithoutMaterialization() throws Exception {
    final Path release = this.root.resolve("release/gfx/fmvhd/videos"); Files.createDirectories(release);
    final Path movie = release.resolve(digest() + ".mp4"); Files.write(movie, DATA);
    final Path workspace = this.root.resolve("workspace"); Files.createDirectory(workspace);
    final Path gfx = workspace.resolve("gfx"); Files.createSymbolicLink(gfx, release.getParent().getParent());
    assertEquals(movie, VideoCache.installed(gfx, digest(), DATA.length));
    assertArrayEquals(DATA, Files.readAllBytes(movie));
    assertFalse(Files.exists(workspace.resolve("cache")));
  }

  @Test void installedLookupRejectsMissingCorruptAndLinkedDataWithoutWriting() throws Exception {
    final Path gfx=this.root.resolve("gfx"), videos=gfx.resolve("fmvhd/videos");Files.createDirectories(videos);
    final Path movie=videos.resolve(digest()+".mp4");
    assertThrows(IOException.class,()->VideoCache.installed(gfx,digest(),DATA.length));
    Files.write(movie,new byte[DATA.length]);assertThrows(IOException.class,()->VideoCache.installed(gfx,digest(),DATA.length));
    Files.delete(movie);final Path outside=this.root.resolve("outside");Files.write(outside,DATA);Files.createSymbolicLink(movie,outside);
    assertThrows(IOException.class,()->VideoCache.installed(gfx,digest(),DATA.length));assertArrayEquals(DATA,Files.readAllBytes(outside));
    Files.delete(movie);Files.delete(videos);Files.createSymbolicLink(videos,this.root);
    assertThrows(IOException.class,()->VideoCache.installed(gfx,digest(),DATA.length));
    assertThrows(IOException.class,()->VideoCache.installed(gfx,"../escape",DATA.length));
    assertThrows(IOException.class,()->VideoCache.installed(gfx,digest(),0));
  }

}
