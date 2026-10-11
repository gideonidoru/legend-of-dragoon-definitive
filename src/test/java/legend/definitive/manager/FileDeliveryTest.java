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
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers, int statusCode) implements HttpResponse<InputStream> {
    Response(final HttpRequest request, final InputStream body, final HttpHeaders headers) { this(request, body, headers, 200); }
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
  private static String releaseJson(final ReleaseUpdates.Candidate candidate, final String date) {
    return "{\"draft\":false,\"tag_name\":\"" + candidate.tag() + "\",\"published_at\":\"" + date + "\",\"assets\":[{\"id\":2,\"name\":\"Legend-of-Dragoon-Definitive-" + PackageManifest.hostPlatform() + ".zip\",\"digest\":\"sha256:" + candidate.sha256() + "\",\"browser_download_url\":\"" + candidate.url() + "\"},{\"id\":3,\"name\":\"Definitive-Contents-" + PackageManifest.hostPlatform() + ".zip\",\"digest\":\"sha256:" + candidate.contents().sha256() + "\",\"browser_download_url\":\"" + candidate.contents().url() + "\"}]}";
  }
  @Test void repairOfRetiredReleaseSelectivelyRestoresCurrentReleaseAndKeepsPersonalData() throws Exception {
    final Path original = pack(); final var store = new InstallStore(this.temporary.resolve("retired-install")); store.install(original);
    final Path oldData = store.data(store.state()); Files.writeString(oldData.resolve("saves/owner.txt"), "personal");
    final var state = store.state(); state.setProperty("installedReleaseTag", "retired-fixture"); state.setProperty("releasePublishedAt", "2026-10-09T00:00:00Z"); InstallStore.atomicProperties(store.root().resolve("state.properties"), state);
    final Path next = this.temporary.resolve("current"); InstallStore.copyTree(original, next); Files.writeString(next.resolve("lod-game-test.jar"), "fixed manager and engine fixture"); refresh(next);
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(next, served); final List<String> requested = new ArrayList<>();
    final String currentJson = "[" + releaseJson(candidate, "2026-10-10T00:00:00Z") + "]";
    final ReleaseUpdates.AssetDownload transport = request -> {
      if(request.uri().getHost().equals("api.github.com")) {
        final boolean retired = request.uri().getPath().contains("/tags/");
        return new Response(request, new ByteArrayInputStream((retired ? "{}" : currentJson).getBytes(java.nio.charset.StandardCharsets.UTF_8)), HttpHeaders.of(Map.of(), (key, value) -> true), retired ? 404 : 200);
      }
      return connection(served, requested).send(request);
    };
    final String result = ReleaseUpdates.repair(store, InstallProgress.NONE, transport);
    assertTrue(result.startsWith("Repair complete using the current release."));
    assertEquals(List.of("Definitive-Contents-" + PackageManifest.hostPlatform() + ".zip", "file-" + PackageManifest.sha256(next.resolve("lod-game-test.jar"))), requested);
    assertEquals(PackageManifest.read(next).id(), store.state().getProperty("version")); assertEquals("personal", Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); store.verifyInstalled();
  }
  @Test void repairDoesNotTreatPermissionFailureAsRetirementOrChangeTheVersion() throws Exception {
    final Path original = pack(); final var store = new InstallStore(this.temporary.resolve("repair-forbidden")); store.install(original); final var before = store.state();
    final List<URI> requested = new ArrayList<>();
    assertThrows(IOException.class, () -> ReleaseUpdates.repair(store, InstallProgress.NONE, request -> { requested.add(request.uri()); return new Response(request, InputStream.nullInputStream(), HttpHeaders.of(Map.of(), (key, value) -> true), 403); }));
    assertEquals(1, requested.size()); assertEquals("/repos/gideonidoru/legend-of-dragoon-definitive/releases", requested.getFirst().getPath()); assertEquals(before, store.state()); store.verifyInstalled();
  }
  @Test void retiredRepairRejectsCurrentReleaseOlderThanVerifiedInstalledDate() throws Exception {
    final Path original = pack(); final var store = new InstallStore(this.temporary.resolve("repair-downgrade")); store.install(original);
    final var state = store.state(); state.setProperty("installedReleaseTag", "retired-fixture"); state.setProperty("releasePublishedAt", "2026-10-11T00:00:00Z"); InstallStore.atomicProperties(store.root().resolve("state.properties"), state);
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(original, served); final String json = "[" + releaseJson(candidate, "2026-10-10T00:00:00Z") + "]";
    assertThrows(IOException.class, () -> ReleaseUpdates.repair(store, InstallProgress.NONE, request -> {
      assertEquals("api.github.com", request.uri().getHost()); final boolean retired = request.uri().getPath().contains("/tags/");
      return new Response(request, new ByteArrayInputStream((retired ? "{}" : json).getBytes(java.nio.charset.StandardCharsets.UTF_8)), HttpHeaders.of(Map.of(), (key, value) -> true), retired ? 404 : 200);
    }));
    assertEquals(state, store.state()); store.verifyInstalled();
  }

  private Path online(final Path pack, final Map<String, byte[]> served, final ReleaseUpdates.Candidate candidate) throws Exception {
    final Path root=this.temporary.resolve("online");Files.createDirectory(root);
    final var manifest=PackageManifest.read(pack);
    for(final String name : manifest.hashes().stringPropertyNames()) {
      if(!Set.of("definitive-manager.jar","bootstrap-java","Install.sh","LICENSE","CREDITS").contains(name) && !name.startsWith("libs/")) continue;
      Files.createDirectories(root.resolve(name).getParent());Files.copy(pack.resolve(name),root.resolve(name));
    }
    for(final String name : List.of(PackageManifest.METADATA,PackageManifest.HASHES)) Files.copy(pack.resolve(name),root.resolve(name));
    try(final var zip=new ZipInputStream(new ByteArrayInputStream(served.get(candidate.contents().url().getPath().substring(candidate.contents().url().getPath().lastIndexOf('/')+1))))) {
      for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) if(entry.getName().equals(FileDelivery.SIZES)) Files.copy(zip,root.resolve(FileDelivery.SIZES));
    }
    return root;
  }
  @Test void suppliedOnlinePackageInstallsAndReinstallsEveryPayloadAgainstPublishedInventory() throws Exception {
    final Path pack=pack();final var store=new InstallStore(this.temporary.resolve("online-install"));
    final Map<String,byte[]> served=new HashMap<>();final var candidate=contents(pack,served);final Path online=online(pack,served,candidate);
    assertFalse(Files.exists(online.resolve("lod-game-test.jar")));
    final List<String> requested=new ArrayList<>();
    PortableSetup.installOnline(online,store,candidate,InstallProgress.NONE,connection(served,requested),false);
    final int completeCount=PackageManifest.read(pack).hashes().size()+1;
    assertEquals(completeCount,requested.size());store.verifyInstalled();
    final Path before=store.data(store.state());Files.writeString(before.resolve("saves/owner.txt"),"personal");
    requested.clear();PortableSetup.installOnline(online,store,candidate,InstallProgress.NONE,connection(served,requested),true);
    assertEquals(completeCount,requested.size());assertNotEquals(before,store.data(store.state()));
    assertEquals("personal",Files.readString(store.data(store.state()).resolve("saves/owner.txt")));store.verifyInstalled();
  }
  @Test void suppliedOnlinePackageRejectsDifferentPublishedIdentityBeforeFetchingPayloads() throws Exception {
    final Path pack=pack();final var store=new InstallStore(this.temporary.resolve("online-mismatch"));store.install(pack);
    final byte[] before=Files.readAllBytes(store.root().resolve("state.properties"));
    final Map<String,byte[]> served=new HashMap<>();final var original=contents(pack,served);final Path online=online(pack,served,original);
    final Path next=this.temporary.resolve("next");InstallStore.copyTree(pack,next);Files.writeString(next.resolve("lod-game-test.jar"),"changed");refresh(next);
    final var changed=contents(next,served);final List<String> requested=new ArrayList<>();
    assertThrows(IOException.class,()->PortableSetup.installOnline(online,store,changed,InstallProgress.NONE,connection(served,requested),false));
    assertEquals(1,requested.size());assertArrayEquals(before,Files.readAllBytes(store.root().resolve("state.properties")));store.verifyInstalled();
  }
  @Test void suppliedOnlinePackageRejectsStaleTagBeforeDownloading() throws Exception {
    final Path pack=pack();final var store=new InstallStore(this.temporary.resolve("online-stale"));
    final Map<String,byte[]> served=new HashMap<>();final var candidate=contents(pack,served);final Path online=online(pack,served,candidate);
    final var newer=new ReleaseUpdates.Candidate("newer",candidate.assetId(),candidate.url(),candidate.sha256(),null,"",candidate.contents());
    final List<String> requested=new ArrayList<>();
    assertThrows(IOException.class,()->PortableSetup.installOnline(online,store,newer,InstallProgress.NONE,connection(served,requested),false));
    assertTrue(requested.isEmpty());assertFalse(store.state().containsKey("version"));
  }

  @Test void reinstallRejectsExternalSourceBeforeDownloading() throws Exception {
    final var store = new InstallStore(this.temporary.toRealPath().resolve("uninstalled"));
    final URI base = URI.create("https://example.com/releases/fixture/");
    final var candidate = new ReleaseUpdates.Candidate("fixture", "2", base.resolve("package.zip"), "a".repeat(64), null, "", new ReleaseUpdates.ContentsAsset("3", base.resolve("Definitive-Contents-" + PackageManifest.hostPlatform() + ".zip"), "b".repeat(64)));
    assertThrows(IOException.class, () -> ReleaseUpdates.reinstall(store, candidate, InstallProgress.NONE));
    assertTrue(store.state().getProperty("version", "").isEmpty());
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
  @Test void freshInstallUsesCompleteVerifiedFilesWithoutRetrievingTheFullArchive() throws Exception {
    final Path pack=pack();final InstallStore store=new InstallStore(this.temporary.resolve("fresh"));
    final Map<String,byte[]> served=new HashMap<>();final List<String> requested=new ArrayList<>();final var candidate=contents(pack,served);
    ReleaseUpdates.install(store,candidate,InstallProgress.NONE,connection(served,requested));
    assertTrue(requested.stream().noneMatch(name->name.startsWith("Legend-of-Dragoon-Definitive-")));
    store.verifyInstalled();assertTrue(store.state().containsKey("version"));
  }
  @Test void completeFileReinstallRetainsSavesAndReplacesManagedExtraction() throws Exception {
    final Path pack=pack();final InstallStore store=new InstallStore(this.temporary.resolve("reinstall-files"));store.install(pack);
    final Path before=store.data(store.state());Files.writeString(before.resolve("saves/owner.txt"),"personal");
    final Map<String,byte[]> served=new HashMap<>();final var candidate=contents(pack,served);
    final List<String> requested=new ArrayList<>();
    FileDelivery.install(store,candidate,InstallProgress.NONE,connection(served,requested),false,true);
    final var hashes=PackageManifest.read(pack).hashes();
    assertEquals(hashes.size()+1,requested.size(),"Full Reinstall fetches the inventory and every payload, including healthy files");
    final Map<String,Long> counts=new HashMap<>();
    requested.forEach(name->counts.merge(name,1L,Long::sum));
    for(final String digest : new HashSet<>(hashes.values().stream().map(Object::toString).toList()))
      assertEquals(hashes.values().stream().filter(digest::equals).count(),counts.get("file-"+digest));
    assertNotEquals(before,store.data(store.state()));assertEquals("personal",Files.readString(store.data(store.state()).resolve("saves/owner.txt")));store.verifyInstalled();
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
    try(final var files = Files.list(store.root())) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("release-recovery-"))); }
  }
  @Test void repairCannotSilentlyUpgradeToAnotherPackageIdentity() throws Exception {
    final Path pack = pack(); final InstallStore store = new InstallStore(this.temporary.resolve("installed")); store.install(pack);
    final Path next = this.temporary.resolve("next"); InstallStore.copyTree(pack, next); Files.writeString(next.resolve("lod-game-test.jar"), "new"); refresh(next);
    final Map<String, byte[]> served = new HashMap<>(); final var candidate = contents(next, served); final List<String> requested = new ArrayList<>();
    assertThrows(IOException.class, () -> FileDelivery.install(store, candidate, InstallProgress.NONE, connection(served, requested), true)); assertEquals(1, requested.size()); store.verifyInstalled();
  }
}
