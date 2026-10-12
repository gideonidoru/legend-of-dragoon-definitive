package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class NetworkTelemetryTest {
  @TempDir Path root;
  private static final String BASE = "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/";
  private record Response(HttpRequest request, InputStream body, HttpHeaders headers, int statusCode) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  /** Small reads exercise real transport callbacks; the clock eliminates timing-sensitive assertions. */
  private static InputStream body(final byte[] bytes, final AtomicLong clock, final boolean fail) {
    return new InputStream() {
      int at;
      @Override public int read() { throw new AssertionError("Expected buffered read"); }
      @Override public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if(fail && this.at > 0) throw new IOException("Interrupted response fixture");
        if(this.at == bytes.length) return -1;
        final int count = Math.min(Math.min(3, length), bytes.length - this.at);
        System.arraycopy(bytes, this.at, buffer, offset, count); this.at += count;
        clock.addAndGet(250_000_000L); return count;
      }
    };
  }
  private static Response response(final HttpRequest request, final InputStream body, final Map<String, List<String>> headers, final int status) {
    return new Response(request, body, HttpHeaders.of(headers, (key, value) -> true), status);
  }
  @Test void localConstructorsAndScalingKeepLogicalAndNetworkWorkSeparate() {
    final var local = new InstallProgress.Update("Copying", "Local", 10, 60, 8, 16, "asset.zip", 8, 16);
    assertEquals(-1, local.networkBytes()); assertEquals(0, local.networkBytesPerSecond());
    final var scaled = local.withNetwork(new InstallProgress.Network(12, 100)).scaled(20, 80);
    assertEquals(12, scaled.networkBytes()); assertEquals(100, scaled.networkBytesPerSecond());
    assertEquals(8, scaled.fileCompleted()); assertEquals(16, scaled.fileTotal()); assertEquals(50, scaled.percent());
    assertEquals(-1, new InstallProgress.Update("Phase", "", 0, 0, 0, 0).networkBytes());
  }
  @Test void samplerUsesElapsedTimeSmoothsAndNeverCountsResponseOffsetsTwice() {
    final var clock = new AtomicLong(); final var sampler = new InstallProgress.NetworkSampler(clock::get);
    final var first = sampler.transfer(); clock.addAndGet(250_000_000L); first.accept(100);
    assertEquals(400, sampler.snapshot().bytesPerSecond(), 0.001);
    first.accept(100); assertEquals(100, sampler.snapshot().bytes());
    clock.addAndGet(250_000_000L); first.accept(1100);
    final double smoothed = sampler.snapshot().bytesPerSecond(); assertTrue(smoothed > 400 && smoothed < 4000);
    final var continuation = sampler.transfer(); clock.addAndGet(250_000_000L); continuation.accept(50);
    assertEquals(1150, sampler.snapshot().bytes());
    clock.addAndGet(5_000_000_000L); assertTrue(sampler.snapshot().bytesPerSecond() < smoothed);
    clock.set(-1); continuation.accept(51);
    assertEquals(1151, sampler.snapshot().bytes()); assertTrue(Double.isFinite(sampler.snapshot().bytesPerSecond()));
  }
  @ParameterizedTest @ValueSource(booleans = {false, true})
  void completeAndResumedDownloadsCountActualRetryTraffic(final boolean resume) throws Exception {
    final byte[] bytes = "abcdefgh".getBytes(); final Path target = Files.createFile(root.resolve("download"));
    final var clock = new AtomicLong(); final var sampler = new InstallProgress.NetworkSampler(clock::get);
    final var updates = new ArrayList<InstallProgress.Update>(); final int[] calls = {0};
    ReleaseUpdates.download(new ReleaseUpdates.Candidate("definitive-current", "7", URI.create(BASE + "package.zip"), PackageManifest.digest(bytes)), target, updates::add, request -> {
      final int attempt = calls[0]++;
      if(attempt == 0) {
        assertTrue(request.headers().firstValue("Range").isEmpty());
        final var headers = new HashMap<String, List<String>>(); headers.put("Content-Length", List.of("8"));
        if(resume) headers.put("ETag", List.of("\"stable\""));
        return response(request, body(bytes, clock, true), headers, 200);
      }
      if(resume) {
        assertEquals("bytes=3-", request.headers().firstValue("Range").orElseThrow());
        assertEquals("\"stable\"", request.headers().firstValue("If-Range").orElseThrow());
        return response(request, body(Arrays.copyOfRange(bytes, 3, bytes.length), clock, false), Map.of("ETag", List.of("\"stable\""), "Content-Length", List.of("5"), "Content-Range", List.of("bytes 3-7/8")), 206);
      }
      assertTrue(request.headers().firstValue("Range").isEmpty());
      return response(request, body(bytes, clock, false), Map.of("Content-Length", List.of("8")), 200);
    }, 1024, sampler);
    assertEquals(2, calls[0]); assertArrayEquals(bytes, Files.readAllBytes(target));
    final var last = updates.getLast(); assertEquals(8, last.completed()); assertEquals(8, last.total());
    assertEquals(resume ? 8 : 11, last.networkBytes()); assertTrue(last.networkBytesPerSecond() > 0);
    long prior = 0;
    for(final var update : updates) { assertTrue(update.networkBytes() >= prior); prior = update.networkBytes(); assertTrue(Double.isFinite(update.networkBytesPerSecond())); }
  }
  @Test void unknownResponseSizeDoesNotInventACompletionPercentage() throws Exception {
    final byte[] bytes = "unknown".getBytes(); final var clock = new AtomicLong(); final var updates = new ArrayList<InstallProgress.Update>();
    ReleaseUpdates.download(new ReleaseUpdates.Candidate("definitive-current", "7", URI.create(BASE + "package.zip"), PackageManifest.digest(bytes)), Files.createFile(root.resolve("unknown")), updates::add,
      request -> response(request, body(bytes, clock, false), Map.of(), 200), 1024, new InstallProgress.NetworkSampler(clock::get));
    assertEquals(bytes.length, updates.getLast().networkBytes()); assertEquals(-1, updates.getLast().total()); assertEquals(10, updates.getLast().percent());
  }
  @Test void rawAssemblySeparatesCachedWorkAndRetriedRangeBodyBytes() throws Exception {
    final byte[] cached = "OLD".getBytes(), remote = "NEWbytes".getBytes(), wire = "OLDNEWbytes".getBytes();
    final String name = "bundled-mods/Env1HD.zip", digest = PackageManifest.digest(wire);
    final var spans = List.of(new ArchiveAssembly.Span(PackageManifest.digest(cached), cached.length, digest, 0), new ArchiveAssembly.Span(PackageManifest.digest(remote), remote.length, digest, cached.length));
    final var recipe = new ArchiveAssembly.Recipe(Map.of(digest, new ArchiveAssembly.Source("7", wire.length)), Map.of(name, spans), new byte[]{1});
    final Path current = Files.createDirectory(root.resolve("current")); Files.createDirectories(current.resolve(name).getParent()); Files.write(current.resolve(name), "OLDwrongxxx".getBytes());
    final var clock = new AtomicLong(); final var sampler = new InstallProgress.NetworkSampler(clock::get);
    final var logical = new ArrayList<Long>(); final var actual = new ArrayList<InstallProgress.Network>(); final int[] calls = {0};
    final var assembler = new ArchiveAssembly(recipe, current, recipe, request -> {
      assertEquals("bytes=3-10", request.headers().firstValue("Range").orElseThrow());
      return response(request, body(remote, clock, calls[0]++ == 0), Map.of("Content-Range", List.of("bytes 3-10/11")), 206);
    });
    assertEquals(3, assembler.assemble(name, root.resolve("assembled"), true, logical::add, (completed, telemetry) -> { assertTrue(completed <= wire.length); actual.add(telemetry); }, sampler));
    assertEquals(2, calls[0]); assertArrayEquals(wire, Files.readAllBytes(root.resolve("assembled")));
    assertEquals(List.of(3L, 11L), logical); assertEquals(11, actual.getLast().bytes());
    assertTrue(actual.stream().anyMatch(network -> network.bytesPerSecond() > 0));
    final var reuseOnly = new ArchiveAssembly(recipe, current, recipe, request -> { throw new AssertionError("Local copy is not network transport"); });
    Files.write(current.resolve(name), wire); final var noNetwork = new InstallProgress.NetworkSampler(clock::get);
    assertEquals(wire.length, reuseOnly.assemble(name, root.resolve("reused"), true, ignored -> {}, (completed, telemetry) -> fail("Cached bytes reported as network"), noNetwork));
    assertEquals(0, noNetwork.snapshot().bytes());
  }
}
