package legend.definitive.manager;

import legend.definitive.fmv.FmvSplitFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real FileDelivery activation using publisher-produced synthetic compressed archives. */
class AssemblyDeliveryTest {
  @TempDir Path root;
  private static final String NAME="bundled-mods/Env1HD.zip", SOURCE="a".repeat(40);
  private static final String BASE="https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/";
  private static byte[] resource(final String name) throws IOException {
    try(final var input=AssemblyDeliveryTest.class.getResourceAsStream("assembly/"+name)) {if(input==null)throw new IOException(name);return input.readAllBytes();}
  }
  private static String sha(final byte[] data) {return PackageManifest.digest(data);}
  private static void manifest(final Path pack) throws Exception {
    Files.deleteIfExists(pack.resolve(PackageManifest.METADATA));Files.deleteIfExists(pack.resolve(PackageManifest.HASHES));ManagerMain.makeManifest(pack,PackageManifest.hostPlatform(),SOURCE);
    final var metadata=PackageManifest.readProperties(pack.resolve(PackageManifest.METADATA));metadata.setProperty("releaseTag","definitive-current");metadata.setProperty("id",PackageManifest.identity(metadata,PackageManifest.read(pack).hashes()));InstallStore.atomicProperties(pack.resolve(PackageManifest.METADATA),metadata);
  }
  private record Response(HttpRequest request,InputStream body,HttpHeaders headers,int statusCode) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
    @Override public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}
    @Override public URI uri(){return request.uri();}
    @Override public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
  }
  private final class Fixture {
    final Path old,next;final InstallStore store;final ReleaseUpdates.Candidate candidate;
    final Map<String,byte[]> served=new HashMap<>();final List<String> ranges=new ArrayList<>();long transferred;
    Fixture() throws Exception {
      root=root.toRealPath();final var fixture=new InstallStoreTest();fixture.temporary=root;
      old=fixture.pack("old",PackageManifest.hostPlatform());final var movies=new FmvSplitFixture();movies.split(old,"small code");Files.write(old.resolve(NAME),resource("old.zip"));manifest(old);
      store=new InstallStore(root.resolve("install"));next=root.resolve("next");InstallStore.copyTree(old,next);Files.write(next.resolve(NAME),resource("new.zip"));manifest(next);
      final var inventory=PackageManifest.read(next);final var sizes=new Properties();
      for(final String path:inventory.hashes().stringPropertyNames()) {final byte[] data=Files.readAllBytes(next.resolve(path));sizes.setProperty(path,Integer.toString(data.length));served.put("file-"+sha(data),data);}
      final var contents=new ByteArrayOutputStream();try(final var zip=new ZipOutputStream(contents)) {
        for(final String path:List.of(PackageManifest.METADATA,PackageManifest.HASHES)) {zip.putNextEntry(new ZipEntry(path));Files.copy(next.resolve(path),zip);zip.closeEntry();}
        zip.putNextEntry(new ZipEntry(FileDelivery.SIZES));sizes.store(zip,"Synthetic");zip.closeEntry();
      }
      final byte[] recipe=resource(PackageManifest.hostPlatform()+".json");final var object=(Map<?,?>)new ReleaseUpdates.Json(new String(recipe,java.nio.charset.StandardCharsets.US_ASCII)).read();
      final Map<String,ReleaseUpdates.SourceAsset> sources=new HashMap<>();
      for(final var entry:((Map<?,?>)object.get("sources")).entrySet()) {
        final String key=(String)entry.getKey();final var value=(Map<?,?>)entry.getValue();served.put("file-"+key,resource("file-"+key));sources.put(key,new ReleaseUpdates.SourceAsset((String)value.get("id"),URI.create(BASE+"file-"+key),(Long)value.get("bytes")));
      }
      final Map<String,ReleaseUpdates.MovieSource> films=new HashMap<>();for(final var entry:movies.movies.entrySet()) films.put(entry.getKey(),new ReleaseUpdates.MovieSource(sha(entry.getValue()),entry.getValue().length));
      final String recipeName="assembly-"+PackageManifest.hostPlatform()+"-"+sha(recipe)+".json", contentsName="contents-"+PackageManifest.hostPlatform()+"-"+sha(contents.toByteArray())+".zip";
      served.put(recipeName,recipe);served.put(contentsName,contents.toByteArray());
      candidate=new ReleaseUpdates.Candidate("definitive-current","2",URI.create(BASE+"package-"+PackageManifest.hostPlatform()+"-"+"b".repeat(64)+".zip"),"b".repeat(64),null,"",new ReleaseUpdates.ContentsAsset("3",URI.create(BASE+contentsName),sha(contents.toByteArray())),new ReleaseUpdates.CurrentRevision(2,SOURCE,"c".repeat(64),"42",1,contents.size(),films,new ReleaseUpdates.AssemblyAsset("4",URI.create(BASE+recipeName),sha(recipe),recipe.length),sources));
      served.remove("file-"+sha(resource("new.zip"))); // virtual final archive has no uploaded whole-file object
    }
    ReleaseUpdates.AssetDownload transport(final boolean bad) {
      return request->{
        final String name=Path.of(request.uri().getPath()).getFileName().toString();final byte[] data=served.get(name);if(data==null)throw new IOException("Unexpected request "+name);
        final var range=request.headers().firstValue("Range");if(range.isPresent()) {
          ranges.add(range.get());final var pair=range.get().substring(6).split("-");final int start=Integer.parseInt(pair[0]),end=Integer.parseInt(pair[1]);transferred+=end-start+1;
          return new Response(request,new ByteArrayInputStream(Arrays.copyOfRange(data,start,end+1)),HttpHeaders.of(Map.of("Content-Range",List.of("bytes "+start+"-"+end+"/"+data.length)),(k,v)->true),bad?200:206);
        }
        return new Response(request,new ByteArrayInputStream(data),HttpHeaders.of(Map.of("Content-Length",List.of(Integer.toString(data.length))),(k,v)->true),200);
      };
    }
    Path active() throws Exception {return store.root().resolve("releases/"+store.state().getProperty("version"));}
    void install(final boolean reinstall) throws Exception {FileDelivery.install(store,candidate,InstallProgress.NONE,transport(false),false,reinstall);store.verifyInstalled();assertArrayEquals(resource("new.zip"),Files.readAllBytes(active().resolve(NAME)));}
  }
  @Test void initialInstallDownloadsAllExactRangesWithoutVirtualWholeArchiveObject() throws Exception {
    final var fixture=new Fixture();fixture.install(false);assertEquals(resource("new.zip").length,fixture.transferred);assertNotNull(ArchiveAssembly.cache(fixture.store.root(),fixture.active()));assertEquals("2",fixture.store.state().getProperty("acceptedGeneration"));
  }
  @Test void updateReusesCompressedEntriesRepairFetchesOneChunkReinstallFetchesAllAndKeepsSaves() throws Exception {
    final var fixture=new Fixture();fixture.store.install(fixture.old);Files.writeString(fixture.store.data(fixture.store.state()).resolve("saves/owner"),"keep");
    fixture.install(false);assertEquals(229,fixture.transferred);final var cached=ArchiveAssembly.cache(fixture.store.root(),fixture.active());assertNotNull(cached);
    final var first=cached.files().get(NAME).getFirst();final byte[] damaged=Files.readAllBytes(fixture.active().resolve(NAME));damaged[31]^=1;Files.write(fixture.active().resolve(NAME),damaged);
    fixture.transferred=0;fixture.ranges.clear();fixture.install(false);assertEquals(first.bytes(),fixture.transferred);assertEquals(1,fixture.ranges.size());
    Files.writeString(fixture.store.root().resolve(".archive-assembly/obsolete.json"),"old");Files.writeString(fixture.store.root().resolve(".assembly-owner.json"),"owner notes");fixture.transferred=0;fixture.ranges.clear();fixture.install(true);assertEquals(resource("new.zip").length,fixture.transferred);
    assertFalse(Files.exists(fixture.store.root().resolve(".archive-assembly/obsolete.json")));assertEquals("owner notes",Files.readString(fixture.store.data(fixture.store.state()).resolve("recovered-reinstall/.assembly-owner.json")));assertEquals("keep",Files.readString(fixture.store.data(fixture.store.state()).resolve("saves/owner")));
  }
  @Test void badPartialDownloadLeavesActiveVersionFloorAndPersonalFilesUnchanged() throws Exception {
    final var fixture=new Fixture();fixture.store.install(fixture.old);final var state=fixture.store.state();Files.writeString(fixture.store.data(state).resolve("saves/owner"),"keep");
    assertThrows(IOException.class,()->FileDelivery.install(fixture.store,fixture.candidate,InstallProgress.NONE,fixture.transport(true),false,false));assertEquals(state,fixture.store.state());fixture.store.verifyInstalled();assertEquals("keep",Files.readString(fixture.store.data(state).resolve("saves/owner")));
    try(final var paths=Files.list(fixture.store.root())){assertTrue(paths.noneMatch(path->path.getFileName().toString().startsWith(".assembly-")||path.getFileName().toString().startsWith(".files-")));}
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void malformedOldInventoryCannotPreventRepairOrFullReinstall(final boolean reinstall) throws Exception {
    final var fixture = new Fixture(); fixture.store.install(fixture.old);
    Files.writeString(fixture.active().resolve(PackageManifest.HASHES), "invalid=\\uGGGG\n");
    fixture.install(reinstall);
    assertEquals("2", fixture.store.state().getProperty("acceptedGeneration"));
  }
  @Test void corruptAndLinkedCachesAreOptionalAndNeverReplaceOwnerFiles() throws Exception {
    final var fixture=new Fixture();fixture.store.install(fixture.old);fixture.install(false);final Path cache=fixture.store.root().resolve(".archive-assembly/"+fixture.store.state().getProperty("version")+".json");Files.writeString(cache,"invalid");assertNull(ArchiveAssembly.cache(fixture.store.root(),fixture.active()));
    final Path owner=root.resolve("owner");Files.writeString(owner,"personal");Files.delete(cache);Files.createSymbolicLink(cache,owner);assertNull(ArchiveAssembly.cache(fixture.store.root(),fixture.active()));fixture.install(false);assertEquals("personal",Files.readString(owner));assertTrue(Files.isSymbolicLink(cache));
  }
}
