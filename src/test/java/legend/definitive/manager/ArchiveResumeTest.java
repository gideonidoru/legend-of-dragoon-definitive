package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveResumeTest {
  @TempDir Path root;
  private static final String NAME = "bundled-mods/Env3HD.zip";
  private static final byte[] WIRE = "oneXtwoXend".getBytes(), FILE = "onetwoend".getBytes();
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers, int statusCode) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private static ArchiveAssembly.Recipe recipe() {
    final String sha = PackageManifest.digest(WIRE);
    final List<ArchiveAssembly.Span> spans = new ArrayList<>();
    for(int offset : new int[]{0, 4, 8}) spans.add(new ArchiveAssembly.Span(PackageManifest.digest(Arrays.copyOfRange(WIRE, offset, offset + 3)), 3, sha, offset));
    return new ArchiveAssembly.Recipe(Map.of(sha, new ArchiveAssembly.Source("7", WIRE.length)), Map.of(NAME, spans), new byte[]{1});
  }
  private static ReleaseUpdates.AssetDownload transport(final List<String> requested, final boolean failLast) {
    return request -> {
      final String range = request.headers().firstValue("Range").orElseThrow(); requested.add(range);
      if(failLast && range.equals("bytes=8-10")) throw new IOException("Lost connection after two completed chunks");
      final String[] bounds = range.substring(6).split("-"); final int start = Integer.parseInt(bounds[0]), end = Integer.parseInt(bounds[1]);
      return new Response(request, new ByteArrayInputStream(Arrays.copyOfRange(WIRE, start, end + 1)), HttpHeaders.of(Map.of("Content-Range", List.of("bytes " + start + "-" + end + "/" + WIRE.length)), (k,v) -> true), 206);
    };
  }
  @Test void newAttemptKeepsTwoVerifiedChunksAndTruncatesOnlyUnverifiedTail() throws Exception {
    final Path target = root.resolve("partial"); final var first = new ArrayList<String>();
    assertThrows(IOException.class, () -> new ArchiveAssembly(recipe(), null, null, transport(first, true)).assemble(NAME, target, false, ignored -> {}));
    assertArrayEquals("onetwo".getBytes(), Files.readAllBytes(target));
    Files.writeString(target, "unverified-tail", StandardOpenOption.APPEND);
    final var retry = new ArrayList<String>();
    assertEquals(6, new ArchiveAssembly(recipe(), null, null, transport(retry, false)).assemble(NAME, target, false, ignored -> {}));
    assertEquals(List.of("bytes=8-10"), retry); assertArrayEquals(FILE, Files.readAllBytes(target));
  }
  @Test void interruptedCoalescedResponsePersistsCompletedChunksBeforeItsBodyEnds() throws Exception {
    final String sha = PackageManifest.digest(FILE); final var spans = new ArrayList<ArchiveAssembly.Span>();
    for(int offset : new int[]{0, 3, 6}) spans.add(new ArchiveAssembly.Span(PackageManifest.digest(Arrays.copyOfRange(FILE, offset, offset + 3)), 3, sha, offset));
    final var recipe = new ArchiveAssembly.Recipe(Map.of(sha, new ArchiveAssembly.Source("7", FILE.length)), Map.of(NAME, spans), new byte[]{1});
    final Path target = root.resolve("coalesced"); final var first = new ArrayList<String>();
    assertThrows(IOException.class, () -> new ArchiveAssembly(recipe, null, null, request -> {
      final String range = request.headers().firstValue("Range").orElseThrow(); first.add(range);
      if(!range.equals("bytes=0-8")) throw new IOException("Still disconnected");
      final var input = new InputStream() {
        boolean sent;
        @Override public int read() { throw new AssertionError(); }
        @Override public int read(final byte[] buffer, final int offset, final int length) throws IOException {
          if(this.sent) throw new IOException("Disconnected mid-response");
          System.arraycopy(FILE, 0, buffer, offset, 6); this.sent = true; return 6;
        }
      };
      return new Response(request, input, HttpHeaders.of(Map.of("Content-Range", List.of("bytes 0-8/9")), (k,v) -> true), 206);
    }).assemble(NAME, target, false, ignored -> {}));
    assertArrayEquals("onetwo".getBytes(), Files.readAllBytes(target));
    assertEquals(List.of("bytes=0-8", "bytes=6-8", "bytes=6-8"), first);
    final var retry = new ArrayList<String>();
    assertEquals(6, new ArchiveAssembly(recipe, null, null, request -> {
      retry.add(request.headers().firstValue("Range").orElseThrow());
      return new Response(request, new ByteArrayInputStream("end".getBytes()), HttpHeaders.of(Map.of("Content-Range", List.of("bytes 6-8/9")), (k,v) -> true), 206);
    }).assemble(NAME, target, false, ignored -> {}));
    assertEquals(List.of("bytes=6-8"), retry); assertArrayEquals(FILE, Files.readAllBytes(target));
  }
  @Test void corruptChunkIsRefetchedAndOwnedSymlinkNeverOpenedForResume() throws Exception {
    final Path target = root.resolve("damaged"); Files.writeString(target, "oneBADend"); final var requested = new ArrayList<String>();
    assertEquals(3, new ArchiveAssembly(recipe(), null, null, transport(requested, false)).assemble(NAME, target, false, ignored -> {}));
    assertEquals(List.of("bytes=4-6", "bytes=8-10"), requested); assertArrayEquals(FILE, Files.readAllBytes(target));
    final Path owner = root.resolve("owner"), linked = root.resolve("linked"); Files.writeString(owner, "personal"); Files.createSymbolicLink(linked, owner);
    assertThrows(IOException.class, () -> new ArchiveAssembly(recipe(), null, null, request -> { fail("Linked resume must not request data"); return null; }).assemble(NAME, linked, false, ignored -> {}));
    assertEquals("personal", Files.readString(owner));
  }
  @Test void healthyRequestsContinueAfterMoreThanThirtyMinutesOfPriorInstallWork() throws Exception {
    final var clock = new AtomicLong(); final var requested = new ArrayList<String>();
    final var assembly = new ArchiveAssembly(recipe(), null, null, transport(requested, false), clock::get);
    clock.set(Duration.ofMinutes(31).toNanos());
    assembly.assemble(NAME, root.resolve("healthy"), false, ignored -> {});
    assertEquals(3, requested.size()); assertArrayEquals(FILE, Files.readAllBytes(root.resolve("healthy")));
  }
}
