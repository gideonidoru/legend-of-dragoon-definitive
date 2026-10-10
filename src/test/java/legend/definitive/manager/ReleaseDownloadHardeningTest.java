package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Transfer real package bytes through interrupted bodies, then verify normal activation and restore. */
class ReleaseDownloadHardeningTest {
  @TempDir Path temporary;
  private record Response(HttpRequest request, int statusCode, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private static Response response(final HttpRequest request, final int status, final InputStream body, final Map<String,List<String>> headers) { return new Response(request, status, body, HttpHeaders.of(headers, (key, value) -> true)); }
  private static InputStream dropped(final byte[] bytes, final int at) {
    return new InputStream() {
      int offset;
      @Override public int read() throws IOException { if(this.offset >= at) throw new IOException("Fixture connection dropped"); return bytes[this.offset++] & 255; }
      @Override public int read(final byte[] target, final int start, final int count) throws IOException {
        if(this.offset >= at) throw new IOException("Fixture connection dropped");
        final int copied = Math.min(count, at - this.offset); System.arraycopy(bytes, this.offset, target, start, copied); this.offset += copied; return copied;
      }
    };
  }
  private static ReleaseUpdates.Candidate candidate(final String digest) { return new ReleaseUpdates.Candidate("fixture", "2", URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/fixture/package.zip"), digest, Instant.parse("2026-10-10T00:00:00Z"), "Changes"); }

  @Test void strongEtagContinuationActivatesDigestVerifiedPackageAndRestoresReleaseDate() throws Exception {
    final Path root = this.temporary.toRealPath(); final var fixture = new InstallStoreTest(); fixture.temporary = root;
    final var store = new InstallStore(root.resolve("installed"));
    store.install(fixture.pack("old", PackageManifest.hostPlatform()), "1", Instant.parse("2026-10-09T00:00:00Z"), InstallProgress.NONE);
    final Path source = fixture.pack("next", PackageManifest.hostPlatform()), zip = root.resolve("next.zip");
    try(final var output = new ZipOutputStream(Files.newOutputStream(zip)); final var files = Files.walk(source)) {
      for(final Path file : files.filter(Files::isRegularFile).toList()) { output.putNextEntry(new ZipEntry(source.relativize(file).toString().replace('\\', '/'))); Files.copy(file, output); output.closeEntry(); }
    }
    final byte[] bytes = Files.readAllBytes(zip); final AtomicInteger calls = new AtomicInteger();
    ReleaseUpdates.install(store, candidate(PackageManifest.sha256(zip)), InstallProgress.NONE, request -> {
      if(calls.getAndIncrement() == 0) return response(request, 200, dropped(bytes, 100), Map.of("ETag", List.of("\"original\""), "Content-Length", List.of(Integer.toString(bytes.length))));
      assertEquals("bytes=100-", request.headers().firstValue("Range").orElseThrow()); assertEquals("\"original\"", request.headers().firstValue("If-Range").orElseThrow());
      return response(request, 206, new ByteArrayInputStream(Arrays.copyOfRange(bytes, 100, bytes.length)), Map.of("ETag", List.of("\"original\""), "Content-Range", List.of("bytes 100-" + (bytes.length - 1) + "/" + bytes.length), "Content-Length", List.of(Integer.toString(bytes.length - 100))));
    });
    assertEquals(2, calls.get()); store.verifyInstalled(); assertEquals("2026-10-10T00:00:00Z", store.state().getProperty("releasePublishedAt"));
    assertThrows(IOException.class, () -> store.install(fixture.pack("stale", PackageManifest.hostPlatform()), "3", Instant.parse("2026-10-09T12:00:00Z"), InstallProgress.NONE));
    store.rollback(); assertEquals("2026-10-09T00:00:00Z", store.state().getProperty("releasePublishedAt")); store.verifyInstalled();
  }

  @Test void serverIgnoringRangeRestartsWithoutAppendingAndWeakEtagNeverResumes() throws Exception {
    final Path root = this.temporary.toRealPath(); final byte[] bytes = "verified original content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for(final String validator : new String[]{"\"strong\"", "W/\"weak\""}) {
      final Path file = Files.createTempFile(root, "download-", ".zip"); final AtomicInteger calls = new AtomicInteger();
      ReleaseUpdates.download(candidate("a".repeat(64)), file, InstallProgress.NONE, request -> {
        if(calls.getAndIncrement() == 0) return response(request, 200, dropped(bytes, 5), Map.of("ETag", List.of(validator), "Content-Length", List.of(Integer.toString(bytes.length))));
        assertEquals(!validator.startsWith("W/"), request.headers().firstValue("Range").isPresent());
        return response(request, 200, new ByteArrayInputStream(bytes), Map.of("Content-Length", List.of(Integer.toString(bytes.length))));
      });
      assertArrayEquals(bytes, Files.readAllBytes(file)); assertEquals(2, calls.get());
    }
  }

  @Test void mismatchedEntityOrByteRangeStopsBeforeMixingDownloadBytes() throws Exception {
    final Path root = this.temporary.toRealPath(); final byte[] bytes = "original download".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for(final boolean wrongEntity : new boolean[]{true, false}) {
      final Path file = Files.createTempFile(root, "invalid-range-", ".zip"); final AtomicInteger calls = new AtomicInteger();
      assertThrows(IOException.class, () -> ReleaseUpdates.download(candidate("a".repeat(64)), file, InstallProgress.NONE, request -> {
        if(calls.getAndIncrement() == 0) return response(request, 200, dropped(bytes, 5), Map.of("ETag", List.of("\"original\"")));
        return response(request, 206, new ByteArrayInputStream(bytes), Map.of("ETag", List.of(wrongEntity ? "\"different\"" : "\"original\""), "Content-Range", List.of("bytes 6-16/17")));
      }));
      assertEquals(2, calls.get()); assertArrayEquals(Arrays.copyOf(bytes, 5), Files.readAllBytes(file));
    }
  }
}
