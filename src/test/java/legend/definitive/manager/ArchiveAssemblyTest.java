package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent byte fixtures exercise transport, cache and transaction behavior. */
class ArchiveAssemblyTest {
  @TempDir Path root;
  private static final String SOURCE = "a".repeat(40), NAME = "bundled-mods/Env1HD.zip";
  private static final String BASE = "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/";
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers, int statusCode) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private static String sha(final byte[] bytes) { return PackageManifest.digest(bytes); }
  private static Map<String,Object> row(final byte[] bytes, final String source, final long offset) { return new TreeMap<>(Map.of("sha256", sha(bytes), "bytes", (long)bytes.length, "source", source, "offset", offset)); }
  private static Map<String,Object> recipe(final byte[] wire, final List<?> spans) {
    return new TreeMap<>(Map.of("format", 1L, "sourceRevision", SOURCE, "platform", PackageManifest.hostPlatform(), "releaseTag", "definitive-current", "sources", Map.of(sha(wire), Map.of("id", "7", "bytes", (long)wire.length)), "files", Map.of(NAME, spans)));
  }
  private static ArchiveAssembly.Recipe read(final Map<String,Object> document, final byte[] target, final byte[] wire) throws Exception {
    final var hashes = new Properties(); hashes.setProperty(NAME, sha(target));
    final var sizes = new Properties(); sizes.setProperty(NAME, Integer.toString(target.length));
    return ArchiveAssembly.read(ReleaseUpdates.json(document).getBytes(java.nio.charset.StandardCharsets.US_ASCII), SOURCE, PackageManifest.hostPlatform(), hashes, sizes,
      Map.of(sha(wire), new ReleaseUpdates.SourceAsset("7", URI.create(BASE + "file-" + sha(wire)), wire.length)));
  }
  private static ReleaseUpdates.AssetDownload transport(final byte[] wire, final List<String> requested) {
    return request -> {
      assertEquals(BASE + "file-" + sha(wire), request.uri().toString());
      final String range = request.headers().firstValue("Range").orElseThrow(); requested.add(range);
      final var pair = range.substring(6).split("-"); final int start = Integer.parseInt(pair[0]), end = Integer.parseInt(pair[1]);
      return new Response(request, new ByteArrayInputStream(Arrays.copyOfRange(wire, start, end + 1)), HttpHeaders.of(Map.of("Content-Range", List.of("bytes " + start + "-" + end + "/" + wire.length)), (k,v)->true), 206);
    };
  }
  @Test void adjacentRemoteSpansCoalesceAndReconstructApprovedBytes() throws Exception {
    final byte[] one = "first".getBytes(), two = "second".getBytes(), wire = "firstsecond".getBytes();
    final var recipe = read(recipe(wire, List.of(row(one, sha(wire), 0), row(two, sha(wire), one.length))), wire, wire);
    final var requested = new ArrayList<String>(); final Path target = root.resolve("target");
    assertEquals(0, new ArchiveAssembly(recipe, null, null, transport(wire, requested)).assemble(NAME, target, true, ignored -> {}));
    assertArrayEquals(wire, Files.readAllBytes(target)); assertEquals(List.of("bytes=0-10"), requested);
  }
  @Test void cachedRawSpansReuseAcrossBundleGenerationsAndRepairOnlyOneCorruptSpan() throws Exception {
    root=root.toRealPath(); final byte[] first="first".getBytes(), second="second".getBytes(), wire="firstsecond".getBytes();
    final var recipe=read(recipe(wire,List.of(row(first,sha(wire),0),row(second,sha(wire),first.length))),wire,wire);
    final Path current=Files.createDirectory(root.resolve("current"));Files.createDirectories(current.resolve(NAME).getParent());Files.write(current.resolve(NAME),wire);
    final var requested=new ArrayList<String>();
    assertEquals(wire.length,new ArchiveAssembly(recipe,current,recipe,transport(wire,requested)).assemble(NAME,root.resolve("reuse"),true,ignored->{}));assertTrue(requested.isEmpty());
    Files.write(current.resolve(NAME),"Xirstsecond".getBytes());requested.clear();
    assertEquals(second.length,new ArchiveAssembly(recipe,current,recipe,transport(wire,requested)).assemble(NAME,root.resolve("repair"),true,ignored->{}));
    assertEquals(List.of("bytes=0-4"),requested);assertArrayEquals(wire,Files.readAllBytes(root.resolve("repair")));
    requested.clear();assertEquals(0,new ArchiveAssembly(recipe,current,recipe,transport(wire,requested)).assemble(NAME,root.resolve("reinstall"),false,ignored->{}));assertEquals(List.of("bytes=0-10"),requested);
  }
  @ParameterizedTest @ValueSource(strings={"status","range","total","encoding","length","short","extra","corrupt"})
  void invalidPartialResponsesCannotComplete(final String defect) throws Exception {
    final byte[] wire="archive".getBytes();final var recipe=read(recipe(wire,List.of(row(wire,sha(wire),0))),wire,wire);
    final int[] requests={0}; final var headers=new HashMap<String,List<String>>();headers.put("Content-Range",List.of("bytes 0-6/7"));
    if(defect.equals("range")) headers.put("Content-Range",List.of("bytes 1-7/8"));
    if(defect.equals("total")) headers.put("Content-Range",List.of("bytes 0-6/8"));
    if(defect.equals("encoding")) headers.put("Content-Encoding",List.of("gzip"));
    if(defect.equals("length")) headers.put("Content-Length",List.of("8"));
    final byte[] body=switch(defect){case "short"->Arrays.copyOf(wire,6);case "extra"->Arrays.copyOf(wire,8);case "corrupt"->new byte[7];default->wire;};
    final var assembly=new ArchiveAssembly(recipe,null,null,request->{requests[0]++;return new Response(request,new ByteArrayInputStream(body),HttpHeaders.of(headers,(k,v)->true),defect.equals("status")?200:206);});
    assertThrows(IOException.class,()->assembly.assemble(NAME,root.resolve("bad"),false,ignored->{}));assertEquals(1,requests[0]);
  }
  @ParameterizedTest @ValueSource(strings={"source","platform","tag","extra","offset","spanSize","sum","id","unused","overlap","unknownPath","duplicate"})
  void malformedRecipesFailBeforePayloadRequests(final String defect) throws Exception {
    final byte[] wire="abcdef".getBytes();final var span=row(wire,sha(wire),0);final var document=recipe(wire,List.of(span));
    switch(defect) {
      case "source"->document.put("sourceRevision","b".repeat(40));case "platform"->document.put("platform","other");case "tag"->document.put("releaseTag","other");case "extra"->document.put("unknown",1L);
      case "offset"->span.put("offset",1L);case "spanSize"->span.put("bytes",16L*1024*1024+1);case "sum"->span.put("bytes",5L);
      case "id"->document.put("sources",Map.of(sha(wire),Map.of("id","8","bytes",6L)));
      case "unused"->document.put("sources",Map.of(sha(wire),Map.of("id","7","bytes",6L),"b".repeat(64),Map.of("id","8","bytes",1L)));
      case "overlap"->document.put("files",Map.of(NAME,List.of(row("abc".getBytes(),sha(wire),0),row("def".getBytes(),sha(wire),2))));
      case "unknownPath"->document.put("files",Map.of("../owner",List.of(span)));
      case "duplicate"->{final String json=ReleaseUpdates.json(document).replace("\"format\":1","\"format\":1,\"format\":1");final var hashes=new Properties();final var sizes=new Properties();assertThrows(IOException.class,()->ArchiveAssembly.read(json.getBytes(),SOURCE,PackageManifest.hostPlatform(),hashes,sizes,Map.of()));return;}
    }
    assertThrows(IOException.class,()->read(document,wire,wire));
  }
  private Path zip(final String name, final String changed) throws Exception {
    final Path path=root.resolve(name);
    try(final var zip=new ZipOutputStream(Files.newOutputStream(path))) {
      for(final var entry:Map.of("one","unchanged one","two",changed,"three","unchanged three").entrySet()) {
        final var item=new ZipEntry(entry.getKey());item.setTime(0);zip.putNextEntry(item);zip.write(entry.getValue().getBytes());zip.closeEntry();
      }
    }
    return path;
  }
  @Test void rawZipIndexNeverInflatesAndPartitionsLocalEntriesAndCentralTail() throws Exception {
    final Path path=zip("raw.zip","changed");final byte[] bytes=Files.readAllBytes(path);
    try(final var channel=FileChannel.open(path)) {
      final var ranges=RawArchive.ranges(channel,true);assertEquals(4,ranges.size());long offset=0;
      for(final var range:ranges){assertEquals(offset,range.offset());assertEquals('P',bytes[(int)offset]);assertEquals('K',bytes[(int)offset+1]);offset+=range.bytes();}
      assertEquals(bytes.length,offset);final long central=ranges.getLast().offset();assertEquals(1,bytes[(int)central+2]);assertEquals(2,bytes[(int)central+3]);
    }
  }
  @Test void malformedLocalZipAndLinkedLocalFilesFallBackToVerifiedRemoteBytes() throws Exception {
    final byte[] wire="complete".getBytes();final var recipe=read(recipe(wire,List.of(row(wire,sha(wire),0))),wire,wire);
    final Path current=Files.createDirectory(root.resolve("current"));Files.createDirectories(current.resolve(NAME).getParent());Files.write(current.resolve(NAME),new byte[]{'P','K',3,4});
    var requests=new ArrayList<String>();new ArchiveAssembly(recipe,current,null,transport(wire,requests)).assemble(NAME,root.resolve("one"),true,ignored->{});assertEquals(1,requests.size());
    Files.delete(current.resolve(NAME));Files.write(root.resolve("owner"),wire);Files.createSymbolicLink(current.resolve(NAME),root.resolve("owner"));
    requests.clear();new ArchiveAssembly(recipe,current,recipe,transport(wire,requests)).assemble(NAME,root.resolve("two"),true,ignored->{});assertEquals(1,requests.size());assertArrayEquals(wire,Files.readAllBytes(root.resolve("owner")));
  }
  @Test void cancellationNeverStartsNetworkOrWritesTarget() throws Exception {
    final byte[] wire="complete".getBytes();final var recipe=read(recipe(wire,List.of(row(wire,sha(wire),0))),wire,wire);final int[] requests={0};
    Thread.currentThread().interrupt();try{assertThrows(InterruptedIOException.class,()->new ArchiveAssembly(recipe,null,null,request->{requests[0]++;throw new AssertionError();}).assemble(NAME,root.resolve("cancel"),false,ignored->{}));}finally{Thread.interrupted();}
    assertEquals(0,requests[0]);assertTrue(!Files.exists(root.resolve("cancel")) || Files.size(root.resolve("cancel"))==0);
  }
}
