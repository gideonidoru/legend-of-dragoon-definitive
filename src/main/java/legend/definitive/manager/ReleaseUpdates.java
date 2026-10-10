// Definitive release checking (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Background checks against our repository. Installation remains an explicit user action. */
public final class ReleaseUpdates {
  private ReleaseUpdates() { }
  private static final String REPO = "gideonidoru/legend-of-dragoon-definitive";
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
  public record Candidate(String tag, String assetId, URI url, String sha256) { }

  public static Optional<Candidate> check(final InstallStore store) throws IOException, InterruptedException {
    final String json = fetch();
    final var state = store.state();
    final Path release = InstallStore.child(store.root().resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
    final var metadata = PackageManifest.read(release).metadata();
    return select(json, PackageManifest.hostPlatform(), state.getProperty("releaseAssetId", ""), metadata.getProperty("releaseTag", ""));
  }
  public static Optional<Candidate> latest() throws IOException, InterruptedException { return select(fetch(), PackageManifest.hostPlatform(), "", ""); }
  private static String fetch() throws IOException, InterruptedException {
    final var request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=20")).timeout(Duration.ofSeconds(20)).header("Accept", "application/vnd.github+json").header("User-Agent", "Legend-of-Dragoon-Definitive").GET().build();
    final var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
    final byte[] body;
    try(final var input = response.body(); final var output = new java.io.ByteArrayOutputStream()) { DownloadBody.copy(input, output, 2 * 1024 * 1024, Duration.ofSeconds(30)); body = output.toByteArray(); }
    if(response.statusCode() != 200) throw new IOException("GitHub release lookup failed (HTTP " + response.statusCode() + "). Check your connection and retry. Installed games can still play offline.");
    return new String(body, java.nio.charset.StandardCharsets.UTF_8);
  }
  static Optional<Candidate> select(final String json, final String platform, final String installedId, final String installedTag) throws IOException {
    final Object parsed = new Json(json).read();
    if(!(parsed instanceof List<?> releases)) throw new IOException("Unexpected update response.");
    for(final Object item : releases) {
      if(!(item instanceof Map<?, ?> release) || Boolean.TRUE.equals(release.get("draft"))) continue;
      final Object tagValue = release.get("tag_name"), assetsValue = release.get("assets");
      if(!(tagValue instanceof String tag) || !(assetsValue instanceof List<?> assets)) continue;
      if(tag.equals(installedTag)) return Optional.empty();
      for(final Object assetValue : assets) {
        if(!(assetValue instanceof Map<?, ?> asset) || !("Legend-of-Dragoon-Definitive-" + platform + ".zip").equals(asset.get("name"))) continue;
        final String id = String.valueOf(asset.get("id"));
        if(id.equals(installedId)) return Optional.empty();
        if(!(asset.get("digest") instanceof String digest) || !digest.matches("sha256:[a-f0-9]{64}") || !(asset.get("browser_download_url") instanceof String url) || !url.startsWith("https://github.com/" + REPO + "/releases/download/")) throw new IOException("Release package lacks verified download metadata.");
        return Optional.of(new Candidate(tag, id, URI.create(url), digest.substring(7)));
      }
    }
    return Optional.empty();
  }
  public static String install(final InstallStore store, final Candidate candidate) throws IOException, InterruptedException {
    return install(store, candidate, InstallProgress.NONE);
  }
  public static String install(final InstallStore store, final Candidate candidate, final InstallProgress progress) throws IOException, InterruptedException {
    return install(store, candidate, progress, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()));
  }
  @FunctionalInterface interface AssetDownload { HttpResponse<java.io.InputStream> send(HttpRequest request) throws IOException, InterruptedException; }
  static String install(final InstallStore store, final Candidate candidate, final InstallProgress progress, final AssetDownload connection) throws IOException, InterruptedException {
    if(!candidate.url().toString().startsWith("https://github.com/" + REPO + "/releases/download/") || !candidate.sha256().matches("[a-f0-9]{64}")) throw new IOException("Invalid release source.");
    progress.phase("Connecting to GitHub", "Release " + candidate.tag(), 8);
    final Path download = Files.createTempFile(store.root(), ".update-", ".zip");
    try {
      final var request = HttpRequest.newBuilder(candidate.url()).timeout(Duration.ofMinutes(15)).GET().build();
      final var response = connection.send(request);
      try(final var input = response.body(); final var output = Files.newOutputStream(download)) {
        if(response.statusCode() != 200) throw new IOException("Update download failed. Your current installation is unchanged.");
        final long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if(size > 1024L * 1024 * 1024) throw new IOException("Release download is larger than the supported package limit.");
        progress.bytes("Downloading game and HD artwork", 10, 60, 0, size);
        DownloadBody.copy(input, output, 1024L * 1024 * 1024, Duration.ofMinutes(15), bytes -> progress.bytes("Downloading game and HD artwork", 10, 60, bytes, size));
      }
      progress.phase("Checking download", "Verifying the GitHub SHA256 checksum", 60);
      if(!PackageManifest.sha256(download).equals(candidate.sha256())) throw new IOException("Update checksum mismatch. Your current installation is unchanged.");
      return store.install(download, candidate.assetId(), progress);
    } finally { Files.deleteIfExists(download); }
  }

  /** Bounded JSON reader for GitHub metadata; no executable expressions or external dependencies. */
  private static final class Json {
    private final String source; private int position; private int count;
    Json(final String source) { this.source = source; }
    Object read() throws IOException { final Object value = value(0); white(); if(this.position != this.source.length()) throw bad(); return value; }
    private void white() { while(this.position < this.source.length() && Character.isWhitespace(this.source.charAt(this.position))) this.position++; }
    private char take() throws IOException { if(this.position >= this.source.length()) throw bad(); return this.source.charAt(this.position++); }
    private IOException bad() { return new IOException("Invalid update metadata."); }
    private Object value(final int depth) throws IOException {
      if(depth > 32 || ++this.count > 100000) throw bad(); white(); final char c = take();
      if(c == '"') return string();
      if(c == '{') {
        final Map<String, Object> map = new LinkedHashMap<>(); white(); if(peek('}')) return map;
        do { white(); if(take() != '"') throw bad(); final String key = string(); white(); if(take() != ':') throw bad(); if(map.containsKey(key)) throw bad(); map.put(key, value(depth + 1)); white(); if(peek('}')) return map; } while(peek(',')); throw bad();
      }
      if(c == '[') { final List<Object> list = new ArrayList<>(); white(); if(peek(']')) return list; do { list.add(value(depth + 1)); white(); if(peek(']')) return list; } while(peek(',')); throw bad(); }
      final int start = this.position - 1; while(this.position < this.source.length() && ",]} \n\r\t".indexOf(this.source.charAt(this.position)) < 0) this.position++;
      final String token = this.source.substring(start, this.position);
      return switch(token) { case "true" -> true; case "false" -> false; case "null" -> null; default -> { if(!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw bad(); yield token; } };
    }
    private boolean peek(final char c) { if(this.position < this.source.length() && this.source.charAt(this.position) == c) { this.position++; return true; } return false; }
    private String string() throws IOException {
      final StringBuilder out = new StringBuilder();
      for(char c; (c = take()) != '"';) {
        if(c < 32) throw bad();
        if(c == '\\') { c = take(); switch(c) { case '"', '\\', '/' -> out.append(c); case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> { if(this.position + 4 > this.source.length()) throw bad(); try { out.append((char)Integer.parseInt(this.source.substring(this.position, this.position + 4), 16)); } catch(final NumberFormatException e) { throw bad(); } this.position += 4; } default -> throw bad(); } }
        else out.append(c);
      }
      return out.toString();
    }
  }
}
