package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class FileDeliveryTest {
  @TempDir Path temporary;
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public int statusCode() { return 200; }
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private Path pack() throws Exception {
    this.temporary = this.temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path pack = fixture.pack("pack", PackageManifest.hostPlatform()); Files.writeString(pack.resolve("gfx/obsolete.txt"), "old"); refresh(pack); return pack;
  }
  private static void refresh(final Path pack) throws Exception {
    Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES)); ManagerMain.makeManifest(pack, PackageManifest.hostPlatform(), "fixture");
  }
  private ReleaseUpdates.Candidate contents(final Path pack, final Map<String, byte[]> served) throws Exception {
    final PackageManifest manifest = PackageManifest.read(pack); final Properties sizes = new Properties();
    for(final String name : manifest.hashes().stringPropertyNames()) {
      final byte[] bytes = Files.readAllBytes(pack.resolve(name)); sizes.setProperty(name, Integer.toString(bytes.length)); served.put("file-" + manifest.hashes().getProperty(name), bytes);
    }
    final var bytes = new ByteArrayOutputStream();
    try(final var zip = new ZipOutputStream(bytes)) {
      for(final String name : List.of(PackageManifest.METADATA, PackageManifest.HASHES)) { zip.putNextEntry(new ZipEntry(name)); Files.copy(pack.resolve(name), zip); zip.closeEntry(); }
      zip.putNextEntry(new ZipEntry(FileDelivery.SIZES)); sizes.store(zip, "Fixture"); zip.closeEntry();
    }
    final String inventory = "Definitive-Contents-" + PackageManifest.hostPlatform() + ".zip"; served.put(inventory, bytes.toByteArray());
    final String tag = manifest.metadata().getProperty("releaseTag"); final URI base = URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/" + tag + "/");
    return new ReleaseUpdates.Candidate(tag, "2", base.resolve("package.zip"), "a".repeat(64), null, "", new ReleaseUpdates.ContentsAsset("3", base.resolve(inventory), PackageManifest.digest(bytes.toByteArray())));
  }
  private static ReleaseUpdates.AssetDownload connection(final Map<String, byte[]> served, final List<String> requested) {
    return request -> {
      final String name = request.uri().getPath().substring(request.uri().getPath().lastIndexOf('/') + 1); requested.add(name);
      final byte[] bytes = served.get(name); if(bytes == null) throw new IOException("Unexpected request: " + name);
      return new Response(request, new ByteArrayInputStream(bytes), HttpHeaders.of(Map.of("Content-Length", List.of(Integer.toString(bytes.length))), (key, value) -> true));
    };
  }

  @Test void updateDownloadsOnlyChangedAndNewFilesAndRemovesObsoleteFiles() throws Exception {
    final Path one = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(one);
    final Path data = store.data(store.state()); Files.writeString(data.resolve("saves/owner.txt"), "personal");
    final Path next = this.temporary.resolve("next"); InstallStore.copyTree(one, next); Files.writeString(next.resolve("lod-game-test.jar"), "new engine"); Files.delete(next.resolve("gfx/obsolete.txt")); Files.writeString(next.resolve("gfx/added.txt"), "new art"); refresh(next);
    final Map<String, byte[]> served = new HashMap<>(); final List<String> requested = new ArrayList<>(); final var candidate = contents(next, served);
    final List<InstallProgress.Update> progress = new ArrayList<>(); ReleaseUpdates.install(store, candidate, progress::add, connection(served, requested));
    assertEquals(Set.of(candidate.contents().url().getPath().substring(candidate.contents().url().getPath().lastIndexOf('/') + 1), "file-" + PackageManifest.sha256(next.resolve("lod-game-test.jar")), "file-" + PackageManifest.sha256(next.resolve("gfx/added.txt"))), new HashSet<>(requested)); assertEquals(3, requested.size());
    final Path active = store.root().resolve("releases").resolve(store.state().getProperty("version")); assertFalse(Files.exists(active.resolve("gfx/obsolete.txt"))); assertEquals("new art", Files.readString(active.resolve("gfx/added.txt")));
    assertEquals("personal", Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); assertTrue(progress.stream().anyMatch(update -> !update.file().isEmpty() && update.filePercent() == 100)); store.verifyInstalled();
    store.rollback(); assertTrue(Files.exists(store.root().resolve("releases").resolve(store.state().getProperty("version")).resolve("gfx/obsolete.txt")));
  }
  @Test void repairDownloadsOnlyCorruptOrMissingFilesAndKeepsDataAndExtraction() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack);
    final Properties before = store.state(); final Path data = store.data(before); Files.writeString(data.resolve("saves/owner.txt"), "personal");
    final Path workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"), "5"); Files.writeString(workspace.resolve("files/owner-extraction"), "retained");
    final Path active = store.root().resolve("releases").resolve(before.getProperty("version")); Files.writeString(active.resolve("lod-game-test.jar"), "bad"); Files.delete(active.resolve("gfx/obsolete.txt")); Files.delete(active.resolve(PackageManifest.METADATA));
    final Map<String, byte[]> served = new HashMap<>(); final List<String> requested = new ArrayList<>(); final var candidate = contents(pack, served);
    FileDelivery.install(store, candidate, InstallProgress.NONE, connection(served, requested), true);
    assertEquals(3, requested.size()); assertEquals(before.getProperty("data"), store.state().getProperty("data")); assertEquals("personal", Files.readString(data.resolve("saves/owner.txt"))); assertEquals("retained", Files.readString(workspace.resolve("files/owner-extraction"))); store.verifyInstalled();
    requested.clear(); FileDelivery.install(store, candidate, InstallProgress.NONE, connection(served, requested), true); assertEquals(1, requested.size(), "Healthy repair retrieves only the small verified inventory");
  }
  @Test void repairOfAnEntireMissingReleaseKeepsThePreparedWorkspaceAndPrivateGeneration() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack); final Properties before = store.state();
    final Path workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"), "5");
    Files.writeString(store.data(before).resolve("saves/owner.txt"), "save");
    InstallStore.deleteOwnedTree(store.root().resolve("releases").resolve(before.getProperty("version")));
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(pack, served);
    FileDelivery.install(store, candidate, InstallProgress.NONE, connection(served, new ArrayList<>()), true);
    assertEquals(before.getProperty("data"), store.state().getProperty("data")); assertEquals(workspace, store.prepareLaunch()); assertTrue(store.discsPrepared());
    assertEquals("save", Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); store.verifyInstalled();
  }
  @Test void corruptNetworkFileCannotActivateOrModifyExistingRelease() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack); final byte[] state = Files.readAllBytes(store.root().resolve("state.properties"));
    final Path next = this.temporary.resolve("next"); InstallStore.copyTree(pack, next); Files.writeString(next.resolve("lod-game-test.jar"), "new"); refresh(next);
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(next, served); served.put("file-" + PackageManifest.sha256(next.resolve("lod-game-test.jar")), "bad".getBytes());
    assertThrows(IOException.class, () -> ReleaseUpdates.install(store, candidate, InstallProgress.NONE, connection(served, new ArrayList<>()))); assertArrayEquals(state, Files.readAllBytes(store.root().resolve("state.properties"))); store.verifyInstalled();
    try(final var files = Files.list(store.root())) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".files-"))); }
  }
  @Test void fullReinstallReplacesEvenHealthyApplicationAndCreatesFreshWorkspace() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack); final Properties before = store.state();
    Files.writeString(store.data(before).resolve("saves/owner.txt"), "save"); final Path workspace = store.prepareLaunch(); Files.writeString(workspace.resolve("files/version"), "5");
    assertTrue(store.install(pack, "", null, InstallProgress.NONE, true).contains("Complete reinstall")); assertNotEquals(before.getProperty("data"), store.state().getProperty("data")); assertEquals("save", Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); assertFalse(store.discsPrepared()); store.verifyInstalled();
    try(final var files = Files.list(store.root())) { assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("release-recovery-"))); }
  }
  @Test void repairCannotSilentlyUpgradeToAnotherPackageIdentity() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack);
    final Path next = this.temporary.resolve("next"); InstallStore.copyTree(pack, next); Files.writeString(next.resolve("lod-game-test.jar"), "new"); refresh(next);
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(next, served); final List<String> requested = new ArrayList<>();
    assertThrows(IOException.class, () -> FileDelivery.install(store, candidate, InstallProgress.NONE, connection(served, requested), true)); assertEquals(1, requested.size()); store.verifyInstalled();
  }
}
