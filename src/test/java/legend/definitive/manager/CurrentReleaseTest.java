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

class CurrentReleaseTest {
  @TempDir Path temporary;
  private static final String BASE = "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/";
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public int statusCode() { return 200; }
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private final class Build {
    final Path pack;
    final Map<String,byte[]> served = new HashMap<>();
    final Map<String,Object> pointer = new TreeMap<>();
    final List<Map<String,Object>> assets = new ArrayList<>();
    final Map<String,Object> release = new TreeMap<>();
    String json;
    Build(final String directory, final long generation, final String source, final String engine) throws Exception {
      temporary = temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = temporary;
      this.pack = fixture.pack(directory, PackageManifest.hostPlatform());
      for(final String name : PackageManifest.ROOT_FILES) Files.writeString(this.pack.resolve(name), "shared-" + name);
      Files.writeString(this.pack.resolve("lod-game-test.jar"), engine);
      Files.delete(this.pack.resolve(PackageManifest.METADATA)); Files.delete(this.pack.resolve(PackageManifest.HASHES));
      ManagerMain.makeManifest(this.pack, PackageManifest.hostPlatform(), source);
      final var metadata = PackageManifest.readProperties(this.pack.resolve(PackageManifest.METADATA));
      metadata.setProperty("releaseTag", "definitive-current");
      metadata.setProperty("id", PackageManifest.identity(metadata, PackageManifest.read(this.pack).hashes()));
      InstallStore.atomicProperties(this.pack.resolve(PackageManifest.METADATA), metadata);
      final var manifest = PackageManifest.read(this.pack); final var sizes = new Properties();
      for(final String name : manifest.hashes().stringPropertyNames()) {
        final byte[] data = Files.readAllBytes(this.pack.resolve(name)); sizes.setProperty(name, Integer.toString(data.length)); this.served.put("file-" + manifest.hashes().getProperty(name), data);
      }
      final var bytes = new ByteArrayOutputStream();
      try(final var zip = new ZipOutputStream(bytes)) {
        for(final String name : List.of(PackageManifest.METADATA,PackageManifest.HASHES)) { zip.putNextEntry(new ZipEntry(name)); Files.copy(this.pack.resolve(name),zip); zip.closeEntry(); }
        zip.putNextEntry(new ZipEntry(FileDelivery.SIZES)); sizes.store(zip,"Fixture"); zip.closeEntry();
      }
      final byte[] contents = bytes.toByteArray();
      this.pointer.putAll(Map.of("format",1L,"generation",generation,"sourceRevision",source,"buildRun",42L,"releaseTag","definitive-current","releaseId","9","publishedAt","2026-10-11T00:00:00Z"));
      final Map<String,Object> platforms = new TreeMap<>();
      for(final String platform : List.of("linux-x64","macos-arm64")) {
        final var spec = new TreeMap<String,Object>(); spec.put("package",descriptor("package-"+platform+"-", ".zip", "package".getBytes()));
        spec.put("contents",descriptor("contents-"+platform+"-", ".zip", contents)); platforms.put(platform,spec);
      }
      this.pointer.put("platforms",platforms);
      this.pointer.put("installer",descriptor("installer-",".zip","installer".getBytes()));
      this.pointer.put("entry",descriptor("entry-",".sh","entry".getBytes()));
      this.pointer.put("fmvSource",descriptor("source-fmvhd-",".zip","fmv".getBytes()));
      this.release.putAll(Map.of("id",9L,"tag_name","definitive-current","draft",false,"published_at","2026-10-10T00:00:00Z","assets",this.assets));
      publish();
    }
    private Map<String,Object> descriptor(final String prefix, final String suffix, final byte[] data) {
      final String digest = PackageManifest.digest(data), name = suffix.isEmpty() ? prefix : prefix+digest+suffix;
      final String id = Long.toString(assets.size()+100L);
      final var descriptor = new TreeMap<String,Object>(); descriptor.putAll(Map.of("name",name,"id",id,"sha256",digest,"bytes",Long.valueOf(data.length)));
      this.assets.add(new TreeMap<>(Map.of("name",name,"id",Long.parseLong(id),"digest","sha256:"+digest,"size",Long.valueOf(data.length),"state","uploaded","browser_download_url",BASE+name)));
      this.served.put(name,data); return descriptor;
    }
    void publish() throws Exception {
      this.assets.removeIf(asset -> asset.get("name").toString().startsWith("manifest-"));
      final String exact = ReleaseUpdates.json(this.pointer), digest = PackageManifest.digest(exact.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      this.assets.add(new TreeMap<>(Map.of("name","manifest-"+digest+".json","id",999L,"digest","sha256:"+digest,"size",Long.valueOf(exact.length()),"state","uploaded","browser_download_url",BASE+"manifest-"+digest+".json")));
      this.release.put("body",CurrentRelease.HEADER+exact+CurrentRelease.END+"Verified changes");
      this.json = "["+ReleaseUpdates.json(this.release)+"]";
    }
    ReleaseUpdates.Candidate candidate() throws Exception { return ReleaseUpdates.select(this.json,PackageManifest.hostPlatform(),"","").orElseThrow(); }
    ReleaseUpdates.AssetDownload connection(final List<String> requests) {
      return request -> {
        requests.add(request.uri().toString());
        final byte[] data = request.uri().getHost().equals("api.github.com") ? (request.uri().getPath().contains("/tags/") ? ReleaseUpdates.json(this.release) : this.json).getBytes() : this.served.get(request.uri().getPath().substring(request.uri().getPath().lastIndexOf('/')+1));
        if(data == null) throw new IOException("Unexpected request: "+request.uri());
        return new Response(request,new ByteArrayInputStream(data),HttpHeaders.of(Map.of("Content-Length",List.of(Integer.toString(data.length))),(k,v)->true));
      };
    }
  }
  @Test void newerLogicalBuildIsOfferedWithSameTagCarrierAndDate() throws Exception {
    final var first = new Build("one",1,"a".repeat(40),"one"); final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>()));
    assertTrue(CurrentRelease.select(first.json,PackageManifest.hostPlatform(),store.state(),false).isEmpty());
    final var next = new Build("two",2,"b".repeat(40),"two");
    assertEquals(first.candidate().tag(),next.candidate().tag()); assertEquals(first.candidate().publishedAt(),next.candidate().publishedAt()); assertEquals(first.candidate().assetId(),next.candidate().assetId());
    assertEquals(2,CurrentRelease.select(next.json,PackageManifest.hostPlatform(),store.state(),false).orElseThrow().revision().generation());
    ReleaseUpdates.install(store,next.candidate(),InstallProgress.NONE,next.connection(new ArrayList<>()));
    assertEquals("2",store.state().getProperty("currentGeneration")); store.verifyInstalled();
  }
  @Test void staleOrConflictingGenerationFailsBeforeAnyPayloadDownloadAndRestoreKeepsFloor() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two");
    final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>())); ReleaseUpdates.install(store,next.candidate(),InstallProgress.NONE,next.connection(new ArrayList<>()));
    store.rollback(); assertEquals("1",store.state().getProperty("currentGeneration")); assertEquals("2",store.state().getProperty("acceptedGeneration"));
    assertThrows(IOException.class,()->CurrentRelease.select(first.json,PackageManifest.hostPlatform(),store.state(),false));
    final var conflict = new Build("conflict",2,"c".repeat(40),"conflict"); final List<String> requested = new ArrayList<>();
    assertThrows(IOException.class,()->ReleaseUpdates.install(store,conflict.candidate(),InstallProgress.NONE,conflict.connection(requested))); assertTrue(requested.isEmpty());
    assertTrue(CurrentRelease.select(next.json,PackageManifest.hostPlatform(),store.state(),false).isPresent()); store.verifyInstalled();
  }
  @Test void repairUnderSameCarrierUsesLatestAndKeepsPersonalChoices() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two"); final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>()));
    store.setPreferences(false,true,false); Files.writeString(store.data(store.state()).resolve("saves/owner.txt"),"personal");
    final Path release = store.root().resolve("releases").resolve(store.state().getProperty("version")); Files.delete(release.resolve("lod-game-test.jar"));
    final List<String> requests = new ArrayList<>(); ReleaseUpdates.repair(store,InstallProgress.NONE,next.connection(requests));
    assertEquals("2",store.state().getProperty("currentGeneration")); assertEquals("original",store.state().getProperty("artwork")); assertEquals("true",store.state().getProperty("legacyTextures")); assertEquals("false",store.state().getProperty("fullscreen"));
    assertEquals("personal",Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); assertEquals(1,requests.stream().filter(url->url.contains("/file-")).count()); store.verifyInstalled();
  }
  @Test void reinstallFetchesEveryFileAndPreservesSaves() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one"); final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,build.candidate(),InstallProgress.NONE,build.connection(new ArrayList<>())); Files.writeString(store.data(store.state()).resolve("saves/owner.txt"),"personal");
    final List<String> requests = new ArrayList<>(); FileDelivery.install(store,build.candidate(),InstallProgress.NONE,build.connection(requests),false,true);
    assertEquals(PackageManifest.read(build.pack).hashes().size(),requests.stream().filter(url->url.contains("/file-")).count());
    assertEquals("personal",Files.readString(store.data(store.state()).resolve("saves/owner.txt"))); store.verifyInstalled();
  }
  @Test void mismatchedContentsSourceDoesNotActivateOrChangeState() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one"); build.pointer.put("sourceRevision","b".repeat(40)); build.publish(); final var store = new InstallStore(this.temporary.resolve("installed"));
    assertThrows(IOException.class,()->ReleaseUpdates.install(store,build.candidate(),InstallProgress.NONE,build.connection(new ArrayList<>()))); assertFalse(store.state().containsKey("version"));
  }
  @Test void strictSchemaMetadataAndFramingFailClosed() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one");
    build.pointer.put("extra",true);build.publish();assertThrows(IOException.class,build::candidate);build.pointer.remove("extra");
    build.pointer.put("generation",9007199254740992L);build.publish();assertThrows(IOException.class,build::candidate);build.pointer.put("generation",1L);
    build.pointer.put("releaseId","10");build.publish();assertThrows(IOException.class,build::candidate);build.pointer.put("releaseId","9");build.publish();
    build.assets.getFirst().put("state","new");build.publish();assertThrows(IOException.class,build::candidate);build.assets.getFirst().put("state","uploaded");build.publish();
    build.release.put("body",CurrentRelease.HEADER+"{}"+CurrentRelease.END);build.json="["+ReleaseUpdates.json(build.release)+"]";assertThrows(IOException.class,build::candidate);
    build.release.put("body","human notes "+CurrentRelease.HEADER+"{}"+CurrentRelease.END);build.json="["+ReleaseUpdates.json(build.release)+"]";assertThrows(IOException.class,build::candidate);
  }
  @Test void compactDiscoveryRetainsCurrentBindingsAndIgnoresHundredsOfBlobs() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one");
    for(int i=0;i<600;i++) build.assets.add(new TreeMap<>(Map.of("name","file-"+"f".repeat(64),"id",Long.valueOf(i+1000)))); build.publish();
    final var cache = new ReleaseUpdates.MetadataCache(); final String compact = cache.fetch(false,request -> new Response(request,new ByteArrayInputStream(build.json.getBytes()),HttpHeaders.of(Map.of(),(k,v)->true)));
    assertFalse(compact.contains("file-")); assertEquals(1,ReleaseUpdates.select(compact,PackageManifest.hostPlatform(),"","").orElseThrow().revision().generation());
  }
  @Test void corruptPayloadCannotChangeActiveBuildOrAdvanceFloor() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two");
    final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>()));
    final byte[] before = Files.readAllBytes(store.root().resolve("state.properties"));
    next.served.put("file-"+PackageManifest.sha256(next.pack.resolve("lod-game-test.jar")),"corrupt".getBytes());
    assertThrows(IOException.class,()->ReleaseUpdates.install(store,next.candidate(),InstallProgress.NONE,next.connection(new ArrayList<>())));
    assertArrayEquals(before,Files.readAllBytes(store.root().resolve("state.properties"))); store.verifyInstalled();
  }
  @Test void cancelledUpdateKeepsInstalledBuildAndOrdering() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two");
    final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>()));
    final byte[] before = Files.readAllBytes(store.root().resolve("state.properties"));
    try {
      assertThrows(InterruptedException.class,()->ReleaseUpdates.install(store,next.candidate(), update -> { if(update.phase().equals("File verified")) Thread.currentThread().interrupt(); },next.connection(new ArrayList<>())));
    } finally { Thread.interrupted(); }
    assertArrayEquals(before,Files.readAllBytes(store.root().resolve("state.properties"))); store.verifyInstalled();
  }
  @Test void sameBuildRepairPreservesPreviousRevisionForRestore() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two");
    final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>())); ReleaseUpdates.install(store,next.candidate(),InstallProgress.NONE,next.connection(new ArrayList<>()));
    Files.delete(store.root().resolve("releases").resolve(store.state().getProperty("version")).resolve("lod-game-test.jar"));
    ReleaseUpdates.repair(store,InstallProgress.NONE,next.connection(new ArrayList<>()));
    store.rollback(); assertEquals("1",store.state().getProperty("currentGeneration")); assertEquals("2",store.state().getProperty("acceptedGeneration")); store.verifyInstalled();
  }
  @Test void offlineVerificationAndLaunchPreparationNeedNoReleaseRequest() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one"); final var store = new InstallStore(this.temporary.resolve("installed"));
    final List<String> requests = new ArrayList<>(); ReleaseUpdates.install(store,build.candidate(),InstallProgress.NONE,build.connection(requests));
    requests.clear(); store.verifyInstalled(); assertTrue(Files.isDirectory(store.prepareLaunch())); assertTrue(requests.isEmpty());
  }
  @Test void apiIdentityAndUnknownOrMalformedManifestCannotBecomeLegacyFallback() throws Exception {
    final var build = new Build("one",1,"a".repeat(40),"one");
    final Object id = build.assets.getFirst().get("id"); build.assets.getFirst().put("id",1L); build.publish(); assertThrows(IOException.class,build::candidate); build.assets.getFirst().put("id",id);
    final Object url = build.assets.getFirst().get("browser_download_url"); build.assets.getFirst().put("browser_download_url","https://example.com/package.zip"); build.publish(); assertThrows(IOException.class,build::candidate); build.assets.getFirst().put("browser_download_url",url);
    final Object digest = build.assets.getFirst().get("digest"); build.assets.getFirst().put("digest","sha256:"+"f".repeat(64)); build.publish(); assertThrows(IOException.class,build::candidate); build.assets.getFirst().put("digest",digest);
    for(final String bad : List.of("", "DEFINITIVE_CURRENT_V2\n{}\nEND_DEFINITIVE_CURRENT_V2\n", CurrentRelease.HEADER+"{}", CurrentRelease.HEADER+"{}"+CurrentRelease.END+CurrentRelease.HEADER+"{}"+CurrentRelease.END)) {
      build.release.put("body",bad); build.json="["+ReleaseUpdates.json(build.release)+"]"; assertThrows(IOException.class,build::candidate);
    }
  }

  @Test void retainedLegacyReleaseDoesNotPreventRepairToLatestPersistentBuild() throws Exception {
    final Build first = new Build("legacy",1,"a".repeat(40),"one"), next = new Build("latest",2,"b".repeat(40),"two");
    final var metadata = PackageManifest.readProperties(first.pack.resolve(PackageManifest.METADATA)); metadata.setProperty("releaseTag","retained-legacy");
    metadata.setProperty("id",PackageManifest.identity(metadata,PackageManifest.read(first.pack).hashes())); InstallStore.atomicProperties(first.pack.resolve(PackageManifest.METADATA),metadata);
    final var store = new InstallStore(this.temporary.resolve("installed")); store.install(first.pack);
    final var state = store.state(); state.setProperty("installedReleaseTag","retained-legacy"); InstallStore.atomicProperties(store.root().resolve("state.properties"),state);
    Files.writeString(store.data(state).resolve("saves/owner.txt"),"personal"); store.setPreferences(false,true,false);
    final List<String> requests = new ArrayList<>();
    ReleaseUpdates.repair(store,InstallProgress.NONE,request -> {
      if(request.uri().getPath().contains("/tags/")) {
        requests.add(request.uri().toString());
        return new Response(request,new ByteArrayInputStream("{\"draft\":false,\"tag_name\":\"retained-legacy\",\"assets\":[]}".getBytes()),HttpHeaders.of(Map.of(),(k,v)->true));
      }
      return next.connection(requests).send(request);
    });
    assertTrue(requests.stream().noneMatch(url -> url.contains("/tags/")), "A retained legacy release returning HTTP 200 must not be preferred");
    assertEquals("2",store.state().getProperty("currentGeneration")); assertEquals("personal",Files.readString(store.data(store.state()).resolve("saves/owner.txt")));
    assertEquals("original",store.state().getProperty("artwork")); assertEquals(1,requests.stream().filter(url->url.contains("/file-")).count());store.verifyInstalled();
  }
  @Test void invalidPreviousGenerationCannotChangeReferencesDuringRestore() throws Exception {
    final Build first = new Build("one",1,"a".repeat(40),"one"), next = new Build("two",2,"b".repeat(40),"two");
    final var store = new InstallStore(this.temporary.resolve("installed"));
    ReleaseUpdates.install(store,first.candidate(),InstallProgress.NONE,first.connection(new ArrayList<>())); ReleaseUpdates.install(store,next.candidate(),InstallProgress.NONE,next.connection(new ArrayList<>()));
    final var state = store.state(); state.setProperty("previousCurrentGeneration","3"); InstallStore.atomicProperties(store.root().resolve("state.properties"),state);
    // Without a usable last-good receipt, invalid ordering must fail before Restore.
    Files.delete(store.root().resolve(".state-last-good.properties"));
    final byte[] before = Files.readAllBytes(store.root().resolve("state.properties"));
    assertThrows(IOException.class,store::rollback); assertArrayEquals(before,Files.readAllBytes(store.root().resolve("state.properties")));
  }

  private void formatTwo(final Build build) throws Exception {
    build.pointer.put("format", 2L); build.pointer.remove("fmvSource");
    final var sources = new TreeMap<String,Object>();
    // Two names deliberately share one physical movie object.
    for(final String name : legend.definitive.fmv.FmvAssetManifest.NAMES) {
      final String identity = name.equals("DRAGON2") ? "DEMO2" : name;
      final byte[] bytes = identity.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      final String digest = PackageManifest.digest(bytes), assetName = "file-" + digest;
      Map<String,Object> descriptor = null;
      for(final var prior : sources.values()) if(prior instanceof Map<?,?> map && assetName.equals(map.get("name"))) descriptor = new TreeMap<>((Map<String,Object>)map);
      if(descriptor == null) descriptor = build.descriptor(assetName, "", bytes);
      sources.put(name, descriptor);
    }
    build.pointer.put("fmvSources", sources);
    final var platforms = (Map<String,Object>)build.pointer.get("platforms");
    for(final String platform : List.of("linux-x64", "macos-arm64")) {
      ((Map<String,Object>)platforms.get(platform)).put("assembly", build.descriptor("assembly-" + platform + "-", ".json", "recipe".getBytes()));
    }
    build.publish();
  }
  @Test void formatTwoBindsEighteenNamesSeventeenObjectsAndAssemblyMetadata() throws Exception {
    var build = new Build("split", 2, "a".repeat(40), "engine"); formatTwo(build);
    var candidate = build.candidate(); assertEquals(18, candidate.revision().movieSources().size());
    assertEquals(candidate.revision().movieSources().get("DEMO2"), candidate.revision().movieSources().get("DRAGON2"));
    assertNotNull(candidate.revision().assembly());
    assertThrows(UnsupportedOperationException.class, () -> candidate.revision().movieSources().clear());
    final Set<String> names = CurrentRelease.referencedNames((String)build.release.get("body"));
    assertEquals(17, names.stream().filter(name -> name.startsWith("file-")).count());
    assertEquals(2, names.stream().filter(name -> name.startsWith("assembly-")).count());
    final var requests = new ArrayList<String>();
    final var cache = new ReleaseUpdates.MetadataCache();
    var compact = cache.fetch(true, build.connection(requests));
    assertTrue(CurrentRelease.select(compact, PackageManifest.hostPlatform(), new Properties(), true).isPresent());
    final var hashes = new Properties(); final var sizes = new Properties();
    for(final var source : candidate.revision().movieSources().values()) {
      final String path = "gfx/fmvhd/videos/" + source.sha256() + ".mp4"; hashes.setProperty(path, source.sha256()); sizes.setProperty(path, Long.toString(source.bytes()));
    }
    for(final String name : List.of("manifest.properties", "provenance.json", "NOTICE.txt")) hashes.setProperty("gfx/fmvhd/" + name, "b".repeat(64));
    candidate.revision().validateMovieContents(hashes, sizes);
    sizes.setProperty(sizes.stringPropertyNames().iterator().next(), "999");
    assertThrows(IOException.class, () -> candidate.revision().validateMovieContents(hashes, sizes));
  }
  @Test void formatTwoRejectsMissingExtraOversizedAndCarrierMismatchedSources() throws Exception {
    var build = new Build("split-invalid", 2, "a".repeat(40), "engine"); formatTwo(build);
    var sources = (Map<String,Object>)build.pointer.get("fmvSources"); var saved = sources.remove("OPENH"); build.publish(); assertThrows(IOException.class, build::candidate);
    sources.put("OPENH",saved); sources.put("UNKNOWN",saved); build.publish(); assertThrows(IOException.class, build::candidate); sources.remove("UNKNOWN");
    var descriptor = (Map<String,Object>)saved; var original = descriptor.put("bytes",512L*1024*1024+1); build.publish(); assertThrows(IOException.class,build::candidate); descriptor.put("bytes",original);
    original = descriptor.put("id","9999"); build.publish(); assertThrows(IOException.class,build::candidate); descriptor.put("id",original);
    build.publish(); assertNotNull(build.candidate());
    final var hashes = new Properties(); final var sizes = new Properties();
    assertThrows(IOException.class, () -> build.candidate().revision().validateMovieContents(hashes,sizes));
  }

  @Test void movieSourceNameMappingMustMatchApprovedManifestEvenWhenWholeClosureMatches() throws Exception {
    temporary = temporary.toRealPath();
    final var fixture = new legend.definitive.fmv.FmvSplitFixture();
    final Path staged = Files.createDirectory(temporary.resolve("movie-map")); fixture.split(staged,"code");
    final var sources = new HashMap<String,ReleaseUpdates.MovieSource>();
    final var hashes = new Properties(); final var sizes = new Properties();
    for(final var entry : fixture.movies.entrySet()) {
      final String digest = legend.definitive.fmv.FmvSplitFixture.sha(entry.getValue());
      sources.put(entry.getKey(),new ReleaseUpdates.MovieSource(digest,entry.getValue().length));
      final String name = "gfx/fmvhd/videos/" + digest + ".mp4"; hashes.setProperty(name,digest); sizes.setProperty(name,Integer.toString(entry.getValue().length));
    }
    for(final String name : List.of("manifest.properties","provenance.json","NOTICE.txt")) hashes.setProperty("gfx/fmvhd/"+name,"b".repeat(64));
    final var good = new ReleaseUpdates.CurrentRevision(1,"a".repeat(40),"b".repeat(64),"1",1,1,sources,null);
    good.validateMovieContents(hashes,sizes); good.validateMovieManifest(staged);
    final var first = sources.get("OPENH"); sources.put("OPENH",sources.get("DEMOH")); sources.put("DEMOH",first);
    final var swapped = new ReleaseUpdates.CurrentRevision(1,"a".repeat(40),"b".repeat(64),"1",1,1,sources,null);
    swapped.validateMovieContents(hashes,sizes);
    assertThrows(IOException.class,() -> swapped.validateMovieManifest(staged));
  }

}
