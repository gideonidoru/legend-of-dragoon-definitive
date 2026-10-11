package legend.definitive.manager;

import legend.definitive.fmv.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real update transaction using only synthetic movie bytes. */
final class FmvSplitDeliveryTest {
  @TempDir Path root;
  @BeforeEach void realRoot() throws Exception { this.root=this.root.toRealPath(); }
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public int statusCode() { return 200; }
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private Path pack(final String name) throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary=this.root;
    return fixture.pack(name, PackageManifest.hostPlatform());
  }
  private static void refresh(final Path pack) throws Exception {
    Files.delete(pack.resolve(PackageManifest.METADATA)); Files.delete(pack.resolve(PackageManifest.HASHES));
    ManagerMain.makeManifest(pack,PackageManifest.hostPlatform(),"fixture");
  }
  private record Delivery(ReleaseUpdates.Candidate candidate, Map<String,byte[]> served, List<String> requests) {
    ReleaseUpdates.AssetDownload transport() {
      return request -> {
        final String name=Path.of(request.uri().getPath()).getFileName().toString();this.requests.add(name);
        final byte[] bytes=this.served.get(name);
        if(bytes==null) throw new IOException("Unexpected request "+name);
        return new Response(request,new ByteArrayInputStream(bytes),HttpHeaders.of(Map.of("Content-Length",List.of(Integer.toString(bytes.length))), (k,v)->true));
      };
    }
  }
  private Delivery delivery(final Path pack) throws Exception {
    final var manifest=PackageManifest.read(pack); final var sizes=new Properties(); final Map<String,byte[]> served=new HashMap<>();
    for(final String path:manifest.hashes().stringPropertyNames()) {
      final byte[] bytes=Files.readAllBytes(pack.resolve(path));sizes.setProperty(path,Integer.toString(bytes.length));served.put("file-"+manifest.hashes().getProperty(path),bytes);
    }
    final var inventory=new ByteArrayOutputStream();
    try(final var zip=new ZipOutputStream(inventory)) {
      for(final String path:List.of(PackageManifest.METADATA,PackageManifest.HASHES)) {
        zip.putNextEntry(new ZipEntry(path));Files.copy(pack.resolve(path),zip);zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry(FileDelivery.SIZES));sizes.store(zip,"synthetic");zip.closeEntry();
    }
    final String name="Definitive-Contents-"+PackageManifest.hostPlatform()+".zip";served.put(name,inventory.toByteArray());
    final String tag=manifest.metadata().getProperty("releaseTag");final URI base=URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/"+tag+"/");
    return new Delivery(new ReleaseUpdates.Candidate(tag,"2",base.resolve("package.zip"),"a".repeat(64),null,"",new ReleaseUpdates.ContentsAsset("3",base.resolve(name),PackageManifest.digest(inventory.toByteArray()))),served,new ArrayList<>());
  }
  private static long movieRequests(final Delivery delivery, final FmvSplitFixture movies) throws Exception {
    final Set<String> names=new HashSet<>();for(final var bytes:movies.movies.values()) names.add("file-"+FmvSplitFixture.sha(bytes));
    return delivery.requests.stream().filter(names::contains).count();
  }
  private Path active(final InstallStore store) throws Exception { return store.root().resolve("releases/"+store.state().getProperty("version")); }
  private void apply(final InstallStore store, final Delivery delivery, final boolean repair, final boolean reinstall) throws Exception {
    FileDelivery.install(store,delivery.candidate,InstallProgress.NONE,delivery.transport(),repair,reinstall);store.verifyInstalled();
    FmvPackageAssets.verify(active(store),PackageManifest.read(active(store)).hashes());
  }
  @Test void migrationCodeChangeOneFilmRepairAndReinstallHaveExactMovieRequestCounts() throws Exception {
    final var movies=new FmvSplitFixture();final Path old=pack("old");movies.jar(old,true,"legacy");refresh(old);
    final var store=new InstallStore(this.root.resolve("install"));store.install(old);
    final Path oldActive=active(store);final String oldJarSha=PackageManifest.sha256(oldActive.resolve("bundled-mods/FMVHD-v0.1.0.jar"));
    Files.writeString(store.data(store.state()).resolve("saves/owner"),"preserve me");
    final Path split=pack("split");movies.split(split,"small code v1");refresh(split);
    final var initial=delivery(split);apply(store,initial,false,false);assertEquals(0,movieRequests(initial,movies));
    final Path workspace=store.prepareLaunch();assertTrue(Files.isSymbolicLink(workspace.resolve("gfx")));
    assertEquals(oldJarSha,PackageManifest.sha256(oldActive.resolve("bundled-mods/FMVHD-v0.1.0.jar")));
    movies.jar(split,false,"small code v2");refresh(split);
    final var code=delivery(split);apply(store,code,false,false);assertEquals(0,movieRequests(code,movies));
    final String oldMovie=FmvSplitFixture.sha(movies.movies.get("OPENH"));
    Files.delete(split.resolve("gfx/fmvhd/videos/"+oldMovie+".mp4"));movies.movies.put("OPENH","one changed movie".getBytes(java.nio.charset.StandardCharsets.UTF_8));movies.split(split,"small code v3");refresh(split);
    final var one=delivery(split);apply(store,one,false,false);assertEquals(1,movieRequests(one,movies));
    final Path movie=active(store).resolve("gfx/fmvhd/videos/"+FmvSplitFixture.sha(movies.movies.get("OPENH"))+".mp4");
    for(final boolean missing:new boolean[]{false,true}) {
      if(missing) Files.delete(movie);else Files.writeString(movie,"corrupt");
      final var repair=delivery(split);apply(store,repair,true,false);assertEquals(1,movieRequests(repair,movies));
    }
    final var reinstall=delivery(split);apply(store,reinstall,false,true);assertEquals(18,movieRequests(reinstall,movies));
    assertEquals("preserve me",Files.readString(store.data(store.state()).resolve("saves/owner")));
  }
  @Test void invalidLegacyJarPinFallsBackToEighteenVerifiedObjects() throws Exception {
    final var movies=new FmvSplitFixture();final Path old=pack("old");movies.jar(old,true,"legacy");refresh(old);
    final var store=new InstallStore(this.root.resolve("install"));store.install(old);
    Files.writeString(active(store).resolve("bundled-mods/FMVHD-v0.1.0.jar"),"not the pinned archive");
    final Path split=pack("split");movies.split(split,"small code");refresh(split);
    final var delivery=delivery(split);apply(store,delivery,false,false);assertEquals(18,movieRequests(delivery,movies));
  }
  @Test void failedMovieDownloadNeverActivatesPartialCandidateOrChangesSaves() throws Exception {
    final Path old=pack("old");final var store=new InstallStore(this.root.resolve("install"));store.install(old);
    final var before=store.state();Files.writeString(store.data(before).resolve("saves/owner"),"keep");
    final var movies=new FmvSplitFixture();final Path split=pack("split");movies.split(split,"small code");refresh(split);
    final var delivery=delivery(split);delivery.served.put("file-"+FmvSplitFixture.sha(movies.movies.get("OPENH")),new byte[]{1});
    assertThrows(IOException.class,()->apply(store,delivery,false,false));assertEquals(before,store.state());store.verifyInstalled();
    assertEquals("keep",Files.readString(store.data(before).resolve("saves/owner")));
    try(final var entries=Files.list(store.root())) {assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith(".files-")));}
  }
}
