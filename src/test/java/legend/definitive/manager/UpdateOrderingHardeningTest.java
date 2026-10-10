package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UpdateOrderingHardeningTest {
  private static String release(final String tag, final int id, final String date) {
    return "{\"draft\":false,\"tag_name\":\"" + tag + "\",\"published_at\":\"" + date + "\",\"body\":\"Verified changes\",\"assets\":[{\"id\":" + id + ",\"name\":\"Legend-of-Dragoon-Definitive-linux-x64.zip\",\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/" + tag + "/package.zip\"}]}";
  }
  private static final String OLD = release("old", 1, "2026-10-09T00:00:00Z");
  private static final String NEW = release("new", 2, "2026-10-10T00:00:00Z");

  @Test void retiredReleaseCannotOfferAnOlderUpdateEvenWithLegacyState() throws Exception {
    assertTrue(ReleaseUpdates.select("[" + OLD + "]", "linux-x64", "2", "new", Instant.parse("2026-10-10T00:00:00Z")).isEmpty());
    assertThrows(IOException.class, () -> ReleaseUpdates.select("[" + OLD + "]", "linux-x64", "2", "new"));
    final var candidate = ReleaseUpdates.select("[" + NEW + "]", "linux-x64", "1", "old", Instant.parse("2026-10-09T00:00:00Z")).orElseThrow();
    assertEquals("new", candidate.tag()); assertEquals(Instant.parse("2026-10-10T00:00:00Z"), candidate.publishedAt()); assertEquals("Verified changes", candidate.releaseNotes());
  }

  private record Response(HttpRequest request, int statusCode, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private static Response response(final HttpRequest request, final int status, final String body, final Map<String,List<String>> headers) {
    return new Response(request, status, new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)), HttpHeaders.of(headers, (key, value) -> true));
  }

  @Test void completePaginationIsCachedAndManualCheckConditionallyRevalidatesEveryPage() throws Exception {
    final var cache = new ReleaseUpdates.MetadataCache(); final AtomicInteger calls = new AtomicInteger();
    final ReleaseUpdates.AssetDownload transport = request -> {
      calls.incrementAndGet(); final boolean first = request.uri().getQuery().endsWith("page=1");
      if(request.headers().firstValue("If-None-Match").isPresent()) return response(request, 304, "", Map.of());
      return response(request, 200, "[" + (first ? OLD : NEW) + "]", first ? Map.of("ETag", List.of("\"one\""), "Link", List.of("<https://api.github.com/repos/gideonidoru/legend-of-dragoon-definitive/releases?per_page=100&page=2>; rel=\"next\"")) : Map.of("ETag", List.of("\"two\"")));
    };
    assertEquals("new", ReleaseUpdates.select(cache.fetch(false, transport), "linux-x64", "1", "old").orElseThrow().tag());
    assertEquals(2, calls.get()); cache.fetch(false, transport); assertEquals(2, calls.get());
    assertEquals("new", ReleaseUpdates.select(cache.fetch(true, transport), "linux-x64", "1", "old").orElseThrow().tag());
    assertEquals(4, calls.get(), "304 must preserve the first page's next-page relationship");
  }

  @Test void incompletePaginationFailsInsteadOfReturningTheOlderFirstPage() {
    final var cache = new ReleaseUpdates.MetadataCache();
    assertThrows(IOException.class, () -> cache.fetch(true, request -> request.uri().getQuery().endsWith("page=1") ? response(request, 200, "[" + OLD + "]", Map.of("Link", List.of("<next>; rel=\"next\""))) : response(request, 403, "", Map.of())));
  }

  @Test void transientFailuresRetryBoundedlyButLongCooldownStopsImmediately() throws Exception {
    final AtomicInteger attempts = new AtomicInteger();
    final HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/fixture")).build();
    try(final var body = ReleaseUpdates.sendWithRetry(request, req -> response(req, attempts.incrementAndGet() < 3 ? 503 : 200, "[]", Map.of())).body()) { assertNotNull(body); }
    assertEquals(3, attempts.get()); attempts.set(0);
    assertThrows(IOException.class, () -> ReleaseUpdates.sendWithRetry(request, req -> { attempts.incrementAndGet(); return response(req, 429, "", Map.of("Retry-After", List.of("60"))); }));
    assertEquals(1, attempts.get());
  }  @Test void discoveryCompactsLargeBlobInventoriesAndUsesSmallPages() throws Exception {
    final var assets = new StringBuilder();
    for(int i = 0; i < 600; i++) { if(i > 0) assets.append(','); assets.append("{\"id\":").append(i + 100).append(",\"name\":\"file-").append("a".repeat(64)).append("\",\"digest\":\"sha256:").append("a".repeat(64)).append("\",\"unused\":\"").append("x".repeat(400)).append("\"}"); }
    final String noisy = NEW.replace("\"assets\":[", "\"assets\":[" + assets + ",");
    final var cache = new ReleaseUpdates.MetadataCache();
    final String summaries = cache.fetch(false, request -> { assertTrue(request.uri().getQuery().startsWith("per_page=10&")); final int page = Integer.parseInt(request.uri().getQuery().replaceFirst(".*page=", "")); return response(request, 200, page <= 4 ? "[" + String.join(",", java.util.Collections.nCopies(10, noisy)) + "]" : "[]", Map.of()); });
    assertTrue(summaries.length() < 40000); assertFalse(summaries.contains("file-")); assertEquals("new", ReleaseUpdates.select(summaries, "linux-x64", "1", "old", Instant.parse("2026-10-09T00:00:00Z")).orElseThrow().tag());
  }

}
