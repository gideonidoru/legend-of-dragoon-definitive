package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise download -> digest -> activation/rollback with isolated transport and real packages. */
class UpdateFailureTest {
  @TempDir Path temporary;
  private InstallStoreTest fixtures;
  private InstallStore store;
  private byte[] before;
  @BeforeEach void initialInstallation() throws Exception {
    this.temporary = this.temporary.toRealPath(); this.fixtures = new InstallStoreTest(); this.fixtures.temporary = this.temporary;
    this.store = new InstallStore(this.temporary.resolve("installed")); this.store.install(this.fixtures.pack("v1", PackageManifest.hostPlatform()));
    Files.writeString(this.store.data(this.store.state()).resolve("saves/campaign.dsav"), "owner data");
    this.before = Files.readAllBytes(this.store.root().resolve("state.properties"));
  }
  private static ReleaseUpdates.Candidate candidate(final String digest) { return new ReleaseUpdates.Candidate("fixture", "asset", URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/fixture/package.zip"), digest); }
  private void unchanged() throws Exception {
    assertArrayEquals(this.before, Files.readAllBytes(this.store.root().resolve("state.properties")));
    assertEquals("owner data", Files.readString(this.store.data(this.store.state()).resolve("saves/campaign.dsav")));
    this.store.verifyInstalled();
    try(final var entries = Files.list(this.store.root())) { assertFalse(entries.anyMatch(p -> p.getFileName().toString().startsWith(".update-"))); }
  }
  private record Response(HttpRequest request, int statusCode, InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {
    @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return this.request.uri(); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
  private static Response response(final HttpRequest request, final int status, final InputStream body, final long length) { return new Response(request, status, body, HttpHeaders.of(length >= 0 ? Map.of("Content-Length", List.of(Long.toString(length))) : Map.of(), (key, value) -> true)); }
  @Test void failedHttpCorruptAndInterruptedDownloadsCannotChangeActiveVersion() throws Exception {
    assertThrows(IOException.class, () -> ReleaseUpdates.install(this.store, candidate("a".repeat(64)), InstallProgress.NONE, request -> response(request, 503, InputStream.nullInputStream(), 0))); unchanged();
    assertThrows(IOException.class, () -> ReleaseUpdates.install(this.store, candidate("a".repeat(64)), InstallProgress.NONE, request -> response(request, 200, new ByteArrayInputStream(new byte[]{1,2,3}), 3))); unchanged();
    final InputStream interrupted = new InputStream() { @Override public int read() throws IOException { throw new IOException("Connection dropped"); } };
    assertThrows(IOException.class, () -> ReleaseUpdates.install(this.store, candidate("a".repeat(64)), InstallProgress.NONE, request -> response(request, 200, interrupted, -1))); unchanged();
    assertThrows(IOException.class, () -> ReleaseUpdates.install(this.store, candidate("a".repeat(64)), InstallProgress.NONE, request -> response(request, 200, InputStream.nullInputStream(), 9L * 1024 * 1024 * 1024))); unchanged();
  }
  @Test void bundledFmvPackageAboveOneGigabyteReachesTheBoundedDownload() throws Exception {
    final InputStream body = new InputStream() { @Override public int read() throws IOException { throw new IOException("Fixture body reached"); } };
    final IOException failure = assertThrows(IOException.class, () -> ReleaseUpdates.install(this.store, candidate("a".repeat(64)), InstallProgress.NONE,
      request -> response(request, 200, body, 1700000000L)));
    Throwable cause = failure; while(cause.getCause() != null) cause = cause.getCause(); assertEquals("Fixture body reached", cause.getMessage());
    unchanged();
  }
  @Test void verifiedDownloadPreservesDataAndCanRestorePriorVersion() throws Exception {
    final Path source = this.fixtures.pack("v2", PackageManifest.hostPlatform()); final Path archive = this.temporary.resolve("v2.zip");
    try(final var zip = new ZipOutputStream(Files.newOutputStream(archive)); final var files = Files.walk(source)) {
      for(final Path file : files.filter(Files::isRegularFile).toList()) { zip.putNextEntry(new ZipEntry(source.relativize(file).toString().replace('\\', '/'))); Files.copy(file, zip); zip.closeEntry(); }
    }
    final var phases = new ArrayList<String>();
    ReleaseUpdates.install(this.store, candidate(PackageManifest.sha256(archive)), update -> phases.add(update.phase()), request -> response(request, 200, Files.newInputStream(archive), Files.size(archive)));
    this.store.verifyInstalled(); assertEquals("asset", this.store.state().getProperty("releaseAssetId"));
    assertEquals("owner data", Files.readString(this.store.data(this.store.state()).resolve("saves/campaign.dsav")));
    assertTrue(phases.contains("Downloading Definitive + HD mods")); assertTrue(phases.contains("Installation verified"));
    this.store.rollback(); this.store.verifyInstalled();
    assertEquals("owner data", Files.readString(this.store.data(this.store.state()).resolve("saves/campaign.dsav")));
  }
}
