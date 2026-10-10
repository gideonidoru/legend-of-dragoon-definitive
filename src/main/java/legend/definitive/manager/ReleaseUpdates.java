// Definitive release checking (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Background checks against our repository. Installation remains an explicit user action. */
public final class ReleaseUpdates {
  private ReleaseUpdates() { }
  private static final long MAX_PACKAGE_BYTES = 8L * 1024 * 1024 * 1024;
  private static final String REPO = "gideonidoru/legend-of-dragoon-definitive";
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
  public record ContentsAsset(String id, URI url, String sha256) { }
  public record Candidate(String tag, String assetId, URI url, String sha256, Instant publishedAt, String releaseNotes, ContentsAsset contents) {
    public Candidate(final String tag, final String assetId, final URI url, final String sha256, final Instant publishedAt, final String releaseNotes) { this(tag, assetId, url, sha256, publishedAt, releaseNotes, null); }
    public Candidate(final String tag, final String assetId, final URI url, final String sha256) { this(tag, assetId, url, sha256, null, ""); }
  }
  private static final MetadataCache CACHE = new MetadataCache();

  public static Optional<Candidate> check(final InstallStore store) throws IOException, InterruptedException {
    return check(store, false);
  }
  /** A manual retry revalidates cached pages immediately; background checks reuse a short-lived cache. */
  public static Optional<Candidate> check(final InstallStore store, final boolean force) throws IOException, InterruptedException {
    final String json = CACHE.fetch(force, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()));
    final var state = store.state();
    final Path release = InstallStore.child(store.root().resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
    final var metadata = PackageManifest.read(release).metadata();
    final Instant installedDate = parseDate(state.getProperty("releasePublishedAt", ""));
    return select(json, PackageManifest.hostPlatform(), state.getProperty("releaseAssetId", ""), metadata.getProperty("releaseTag", ""), installedDate);
  }
  public static Optional<Candidate> latest() throws IOException, InterruptedException { return select(CACHE.fetch(false, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())), PackageManifest.hostPlatform(), "", ""); }
  private static Instant parseDate(final String date) throws IOException {
    if(date.isEmpty()) return null;
    try { return Instant.parse(date); } catch(final DateTimeParseException failure) { throw new IOException("Installed release date is invalid. Verify or reinstall this installation before updating.", failure); }
  }
  static Optional<Candidate> select(final String json, final String platform, final String installedId, final String installedTag) throws IOException {
    return select(json, platform, installedId, installedTag, null);
  }
  static Optional<Candidate> select(final String json, final String platform, final String installedId, final String installedTag, final Instant knownInstalledDate) throws IOException {
    final Object parsed = new Json(json).read();
    if(!(parsed instanceof List<?> releases)) throw new IOException("Unexpected update response.");
    DatedAsset newest = null; boolean ambiguous = false; Instant installedDate = knownInstalledDate;
    for(final Object item : releases) {
      if(!(item instanceof Map<?, ?> release) || Boolean.TRUE.equals(release.get("draft"))) continue;
      final Object tagValue = release.get("tag_name"), assetsValue = release.get("assets");
      if(!(tagValue instanceof String tag) || !(assetsValue instanceof List<?> assets)) continue;
      for(final Object assetValue : assets) {
        if(!(assetValue instanceof Map<?, ?> asset) || !("Legend-of-Dragoon-Definitive-" + platform + ".zip").equals(asset.get("name"))) continue;
        final Instant published;
        if(!(release.get("published_at") instanceof String date)) throw new IOException("Release package lacks a valid publication date. Retry the update check.");
        try { published = Instant.parse(date); }
        catch(final DateTimeParseException failure) { throw new IOException("Release package lacks a valid publication date. Retry the update check.", failure); }
        final String id = String.valueOf(asset.get("id"));
        if(tag.equals(installedTag) || id.equals(installedId)) {
          if(installedDate == null || published.isAfter(installedDate)) installedDate = published;
        }
        if(newest == null || published.isAfter(newest.published())) { newest = new DatedAsset(tag, asset, published); ambiguous = false; }
        else if(published.equals(newest.published()) && !tag.equals(newest.tag())) ambiguous = true;
      }
    }
    if(newest == null) return Optional.empty();
    if(ambiguous) throw new IOException("Compatible releases have ambiguous publication dates. Retry the update check.");
    final String id = String.valueOf(newest.asset().get("id"));
    if(!id.matches("[1-9][0-9]*") || !(newest.asset().get("digest") instanceof String digest) || !digest.matches("sha256:[a-f0-9]{64}") || !(newest.asset().get("browser_download_url") instanceof String url) || !url.startsWith("https://github.com/" + REPO + "/releases/download/" + newest.tag() + "/")) throw new IOException("Release package lacks verified download metadata.");
    final URI downloadUrl;
    try { downloadUrl = URI.create(url); }
    catch(final IllegalArgumentException failure) { throw new IOException("Release package has an invalid download URL.", failure); }
    if(newest.tag().equals(installedTag) || id.equals(installedId)) return Optional.empty();
    if(!installedTag.isEmpty() || !installedId.isEmpty()) {
      if(installedDate == null) throw new IOException("The installed release is no longer listed and has no verified publication date. Updates have stopped to prevent a downgrade. Reinstall a verified package or use Restore.");
      if(!newest.published().isAfter(installedDate)) return Optional.empty();
    }
    final String selectedTag = newest.tag();
    final Object notes = releases.stream().filter(item -> item instanceof Map<?, ?> release && newestTag(release, selectedTag)).findFirst().orElse(null);
    final String releaseNotes = notes instanceof Map<?, ?> release && release.get("body") instanceof String body ? body.substring(0, Math.min(12000, body.length())) : "";
    ContentsAsset contents = null;
    if(notes instanceof Map<?, ?> release && release.get("assets") instanceof List<?> assets) for(final Object item : assets) {
      if(item instanceof Map<?, ?> asset && ("Definitive-Contents-" + platform + ".zip").equals(asset.get("name"))) {
        if(contents != null || !(asset.get("digest") instanceof String checksum) || !checksum.matches("sha256:[a-f0-9]{64}") || !String.valueOf(asset.get("id")).matches("[1-9][0-9]*") || !(asset.get("browser_download_url") instanceof String address) || !address.equals(downloadUrl.resolve("Definitive-Contents-" + platform + ".zip").toString())) throw new IOException("Release file inventory lacks verified metadata.");
        contents = new ContentsAsset(String.valueOf(asset.get("id")), URI.create(address), checksum.substring(7));
      }
    }
    return Optional.of(new Candidate(newest.tag(), id, downloadUrl, digest.substring(7), newest.published(), releaseNotes, contents));
  }
  private static boolean newestTag(final Map<?, ?> release, final String tag) { return tag.equals(release.get("tag_name")); }
  private record DatedAsset(String tag, Map<?, ?> asset, Instant published) { }
  public static String install(final InstallStore store, final Candidate candidate) throws IOException, InterruptedException {
    return install(store, candidate, InstallProgress.NONE);
  }
  public static String install(final InstallStore store, final Candidate candidate, final InstallProgress progress) throws IOException, InterruptedException {
    return install(store, candidate, progress, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()));
  }
  @FunctionalInterface interface AssetDownload { HttpResponse<java.io.InputStream> send(HttpRequest request) throws IOException, InterruptedException; }
  /** Cache only complete, bounded scans; a failed/partial scan never becomes update metadata. */
  static final class MetadataCache {
    private final Map<Integer, CachedPage> pages = new HashMap<>();
    private String complete;
    private long checkedAt;
    synchronized String fetch(final boolean force, final AssetDownload connection) throws IOException, InterruptedException {
      if(!force && this.complete != null && System.nanoTime() - this.checkedAt < Duration.ofMinutes(10).toNanos()) return this.complete;
      this.complete = null;
      final long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
      final Map<Integer, CachedPage> validated = new HashMap<>();
      final List<String> bodies = new ArrayList<>();
      long bytes = 0;
      for(int page = 1; page <= 100; page++) {
        final long remaining = deadline - System.nanoTime();
        if(remaining <= 0) throw new IOException("Release check took too long. Installed games can still play offline.");
        final CachedPage prior = this.pages.get(page);
        final var builder = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=10&page=" + page)).timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(20).toNanos()))).header("Accept", "application/vnd.github+json").header("User-Agent", "Legend-of-Dragoon-Definitive");
        if(prior != null && !prior.etag().isEmpty()) builder.header("If-None-Match", prior.etag());
        final var response = sendWithRetry(builder.GET().build(), connection);
        CachedPage next;
        try(final var input = response.body()) {
          if(response.statusCode() == 304) {
            if(prior == null) throw new IOException("GitHub returned an uncached release page.");
            next = prior;
          } else {
            if(response.statusCode() != 200) throw new IOException("GitHub release lookup failed (HTTP " + response.statusCode() + "). Check your connection and retry. Installed games can still play offline.");
            final var output = new java.io.ByteArrayOutputStream();
            final long bodyRemaining = deadline - System.nanoTime();
            if(bodyRemaining <= 0) throw new IOException("Release check took too long.");
            DownloadBody.copy(input, output, 16 * 1024 * 1024, Duration.ofNanos(Math.min(bodyRemaining, Duration.ofSeconds(30).toNanos())));
            next = new CachedPage(compactPage(output.toString(java.nio.charset.StandardCharsets.UTF_8).trim()), response.headers().firstValue("ETag").orElse(""), response.headers().allValues("Link").stream().anyMatch(link -> link.contains("rel=\"next\"")));
          }
        }
        final Object parsed = new Json(next.body()).read();
        if(!(parsed instanceof List<?> list) || list.size() > 10) throw new IOException("Unexpected release page.");
        bytes += next.body().length();
        if(bytes > 8 * 1024 * 1024) throw new IOException("Release metadata exceeds the supported limit.");
        next = new CachedPage(next.body(), next.etag(), next.hasNext() || list.size() == 10);
        validated.put(page, next);
        if(!list.isEmpty()) bodies.add(next.body().substring(1, next.body().length() - 1));
        final boolean hasNext = next.hasNext();
        if(!hasNext) {
          final String combined = "[" + String.join(",", bodies) + "]";
          new Json(combined).read();
          this.pages.clear(); this.pages.putAll(validated); this.complete = combined; this.checkedAt = System.nanoTime();
          return combined;
        }
      }
      throw new IOException("Release history is too large to check safely. Update discovery has stopped without selecting an older package.");
    }
  }
  /** Blob asset metadata is irrelevant to discovery: authenticated contents inventory binds it. */
  private static String compactPage(final String body) throws IOException {
    final Object parsed = new Json(body).read();
    if(!(parsed instanceof List<?> releases) || releases.size() > 10) throw new IOException("Unexpected release page.");
    final var compact = new ArrayList<Map<String, Object>>();
    for(final Object item : releases) {
      if(!(item instanceof Map<?, ?> release)) throw new IOException("Invalid release summary.");
      final var summary = new LinkedHashMap<String, Object>();
      for(final String field : List.of("draft", "tag_name", "published_at", "body")) {
        final Object value = release.get(field);
        summary.put(field, value instanceof String text && field.equals("body") ? text.substring(0, Math.min(12000, text.length())) : value);
      }
      final var packages = new ArrayList<Object>();
      if(release.get("assets") instanceof List<?> assets) for(final Object assetValue : assets) {
        if(assetValue instanceof Map<?, ?> asset && asset.get("name") instanceof String name && (name.matches("Legend-of-Dragoon-Definitive-(?:linux|macos)-(?:x64|arm64)\\.zip") || name.matches("Definitive-Contents-(?:linux|macos)-(?:x64|arm64)\\.zip"))) {
          final var selected = new LinkedHashMap<String, Object>();
          for(final String field : List.of("name", "id", "digest", "browser_download_url")) selected.put(field, asset.get(field));
          packages.add(selected);
        }
      }
      summary.put("assets", packages); compact.add(summary);
    }
    return json(compact);
  }
  private static String json(final Object value) throws IOException {
    if(value == null) return "null";
    if(value instanceof Boolean bool) return bool.toString();
    if(value instanceof String text) {
      final var out = new StringBuilder("\"");
      for(int i = 0; i < text.length(); i++) {
        final char c = text.charAt(i);
        if(c == '"' || c == '\\') out.append('\\').append(c);
        else if(c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
        else out.append(c);
      }
      return out.append('"').toString();
    }
    if(value instanceof List<?> list) { final var entries = new ArrayList<String>(); for(final Object item : list) entries.add(json(item)); return "[" + String.join(",", entries) + "]"; }
    if(value instanceof Map<?, ?> map) { final var entries = new ArrayList<String>(); for(final var item : map.entrySet()) entries.add(json(item.getKey()) + ":" + json(item.getValue())); return "{" + String.join(",", entries) + "}"; }
    throw new IOException("Invalid release summary value.");
  }
  private record CachedPage(String body, String etag, boolean hasNext) { }
  private static boolean retryable(final int status) { return status == 429 || status == 500 || status == 502 || status == 503 || status == 504; }
  static HttpResponse<java.io.InputStream> sendWithRetry(final HttpRequest request, final AssetDownload connection) throws IOException, InterruptedException {
    IOException failure = null;
    for(int attempt = 0; attempt < 3; attempt++) {
      try {
        final var response = connection.send(request);
        if(!retryable(response.statusCode()) || attempt == 2) return response;
        response.body().close();
        final String retry = response.headers().firstValue("Retry-After").orElse("");
        // Respect long server cooldowns by stopping; never hammer a limited endpoint.
        if(retry.matches("[0-9]+") && retry.length() < 9 && Long.parseLong(retry) > 2) throw new Cooldown("GitHub requests a longer wait. Retry the update check later.");
        long pause = 250L << attempt;
        if(retry.matches("[0-9]{1,3}")) pause = Math.max(pause, Long.parseLong(retry) * 1000);
        Thread.sleep(pause);
        continue;
      } catch(final Cooldown cooldown) { throw cooldown; }
      catch(final IOException e) { failure = e; }
      if(attempt < 2) Thread.sleep(250L << attempt);
    }
    throw new IOException("GitHub could not be reached after bounded retries. Installed games can still play offline.", failure);
  }
  private static final class Cooldown extends IOException { Cooldown(final String message) { super(message); } }
  static String install(final InstallStore store, final Candidate candidate, final InstallProgress progress, final AssetDownload connection) throws IOException, InterruptedException {
    if(!candidate.url().toString().startsWith("https://github.com/" + REPO + "/releases/download/") || !candidate.sha256().matches("[a-f0-9]{64}")) throw new IOException("Invalid release source.");
    if(candidate.contents() != null && store.state().containsKey("version")) {
      validateContents(candidate);
      return FileDelivery.install(store, candidate, progress, connection, false);
    }
    return installArchive(store, candidate, progress, connection, false);
  }
  private static void validateContents(final Candidate candidate) throws IOException {
    if(!candidate.tag().matches("[A-Za-z0-9._-]+") || !candidate.contents().sha256().matches("[a-f0-9]{64}") || !candidate.contents().url().equals(candidate.url().resolve("Definitive-Contents-" + PackageManifest.hostPlatform() + ".zip"))) throw new IOException("Invalid release inventory source.");
  }
  public static String repair(final InstallStore store, final InstallProgress progress) throws IOException, InterruptedException {
    return store.withOperation(() -> {
      final var state = store.state();
      final Path current = InstallStore.child(store.root().resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
      final String tag = state.getProperty("installedReleaseTag", "").isEmpty() ? PackageManifest.read(current).metadata().getProperty("releaseTag", "") : state.getProperty("installedReleaseTag");
      if(!tag.matches("[A-Za-z0-9._-]+")) throw new IOException("Installed release has no published repair identity. Choose Reinstall.");
      final var response = sendWithRetry(HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/tags/" + tag)).timeout(Duration.ofSeconds(20)).GET().build(), request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()));
      final String json;
      try(final var input = response.body(); final var output = new java.io.ByteArrayOutputStream()) {
        if(response.statusCode() != 200) throw new IOException("Installed release is unavailable for repair. Retry or choose Reinstall.");
        DownloadBody.copy(input, output, 16 * 1024 * 1024, Duration.ofSeconds(30));
        json = "[" + output.toString(java.nio.charset.StandardCharsets.UTF_8) + "]";
      }
      final Candidate candidate = select(json, PackageManifest.hostPlatform(), "", "").orElseThrow(() -> new IOException("No compatible repair files are published."));
      if(!tag.equals(candidate.tag()) || candidate.contents() == null) throw new IOException("This older release has no selective repair inventory. Choose a complete Reinstall.");
      validateContents(candidate);
      return FileDelivery.install(store, candidate, progress, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()), true);
    });
  }
  public static String reinstall(final InstallStore store, final Candidate candidate, final InstallProgress progress) throws IOException, InterruptedException {
    return installArchive(store, candidate, progress, request -> HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream()), true);
  }
  static String installArchive(final InstallStore store, final Candidate candidate, final InstallProgress progress, final AssetDownload connection, final boolean fullReinstall) throws IOException, InterruptedException {
    if(!candidate.url().toString().startsWith("https://github.com/" + REPO + "/releases/download/") || !candidate.sha256().matches("[a-f0-9]{64}")) throw new IOException("Invalid release source.");
    progress.phase("Connecting to GitHub", "Release " + candidate.tag(), 8);
    final Path download = Files.createTempFile(store.root(), ".update-", ".zip");
    try {
      download(candidate, download, progress, connection);
      progress.phase("Checking download", "Verifying the GitHub SHA256 checksum", 60);
      if(!PackageManifest.sha256(download).equals(candidate.sha256())) throw new IOException("Update checksum mismatch. Your current installation is unchanged.");
      return store.install(download, candidate.assetId(), candidate.publishedAt(), progress, fullReinstall);
    } finally { Files.deleteIfExists(download); }
  }
  /** Resume only the unique owned staging file, with a strong entity validator and whole-file digest. */
  static void download(final Candidate candidate, final Path file, final InstallProgress progress, final AssetDownload connection) throws IOException, InterruptedException {
    download(candidate, file, progress, connection, MAX_PACKAGE_BYTES);
  }
  static void download(final Candidate candidate, final Path file, final InstallProgress progress, final AssetDownload connection, final long maxBytes) throws IOException, InterruptedException {
    final long deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos();
    String etag = ""; IOException failure = null;
    for(int attempt = 0; attempt < 3; attempt++) {
      final long remaining = deadline - System.nanoTime();
      if(remaining <= 0) break;
      final long offset = etag.isEmpty() ? 0 : Files.size(file);
      final var builder = HttpRequest.newBuilder(candidate.url()).timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(30).toNanos())));
      if(offset > 0) builder.header("Range", "bytes=" + offset + "-").header("If-Range", etag);
      try {
        final var response = connection.send(builder.GET().build());
        try(final var input = response.body()) {
          if(retryable(response.statusCode())) {
            final String cooldown = response.headers().firstValue("Retry-After").orElse("");
            if(cooldown.matches("[0-9]{1,8}") && Long.parseLong(cooldown) > 2) throw new Cooldown("GitHub requests a longer wait. Retry this download later.");
            throw new IOException("GitHub temporarily could not serve the package (HTTP " + response.statusCode() + ").");
          }
          if(response.statusCode() != 200 && response.statusCode() != 206) throw new InvalidDownload("Update download failed (HTTP " + response.statusCode() + "). Your current installation is unchanged.");
          final long length;
          try { length = response.headers().firstValueAsLong("Content-Length").orElse(-1); }
          catch(final NumberFormatException malformed) { throw new InvalidDownload("Release has an invalid download size."); }
          long start = 0, total = length;
          if(response.statusCode() == 206) {
            if(offset == 0 || !etag.equals(response.headers().firstValue("ETag").orElse(""))) throw new InvalidDownload("Release continuation has a different identity.");
            final var range = java.util.regex.Pattern.compile("bytes ([0-9]{1,12})-([0-9]{1,12})/([0-9]{1,12})").matcher(response.headers().firstValue("Content-Range").orElse(""));
            if(!range.matches()) throw new InvalidDownload("Release continuation has an invalid byte range.");
            start = Long.parseLong(range.group(1)); final long end = Long.parseLong(range.group(2)); total = Long.parseLong(range.group(3));
            if(start != offset || end < start || end >= total || (length >= 0 && length != end - start + 1)) throw new InvalidDownload("Release continuation does not match the staged bytes.");
          } else {
            final String validator = response.headers().firstValue("ETag").orElse("");
            etag = validator.matches("\"[^\"\\r\\n]+\"") ? validator : "";
          }
          if(total > maxBytes || start > maxBytes || length < -1) throw new InvalidDownload("Release download is larger than the supported package limit.");
          final long usable = Files.getFileStore(file).getUsableSpace();
          final long downloadReserve = 1024L * 1024 * 1024;
          if(usable < downloadReserve || total >= 0 && total - start > usable - downloadReserve) throw new InvalidDownload("Not enough free installation storage to download this release safely. Free space and retry; the current game is unchanged.");
          final long completed = start, size = total;
          progress.bytes("Downloading Definitive + HD mods", 10, 60, completed, size);
          final long bodyRemaining = deadline - System.nanoTime();
          if(bodyRemaining <= 0) throw new IOException("Release download took too long.");
          try(final var output = Files.newOutputStream(file, StandardOpenOption.WRITE, start == 0 ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            DownloadBody.copy(input, output, maxBytes - completed, Duration.ofNanos(bodyRemaining), bytes -> progress.bytes("Downloading Definitive + HD mods", 10, 60, completed + bytes, size));
          }
          if(total >= 0 && Files.size(file) != total) throw new IOException("Release transfer stopped before its expected size.");
          return;
        }
      } catch(final InvalidDownload | Cooldown | DownloadBody.SizeLimitException | DownloadBody.WriteException permanent) { throw permanent; }
      catch(final IOException transientFailure) { failure = transientFailure; }
      if(attempt < 2) Thread.sleep(250L << attempt);
    }
    throw new IOException("Release download could not complete after bounded retries. Your current installation is unchanged.", failure);
  }
  private static final class InvalidDownload extends IOException { InvalidDownload(final String message) { super(message); } }

  /** Bounded JSON reader for GitHub metadata; no executable expressions or external dependencies. */
  private static final class Json {
    private final String source; private int position; private int count;
    Json(final String source) { this.source = source; }
    Object read() throws IOException { final Object value = value(0); white(); if(this.position != this.source.length()) throw bad(); return value; }
    private void white() { while(this.position < this.source.length() && Character.isWhitespace(this.source.charAt(this.position))) this.position++; }
    private char take() throws IOException { if(this.position >= this.source.length()) throw bad(); return this.source.charAt(this.position++); }
    private IOException bad() { return new IOException("Invalid update metadata."); }
    private Object value(final int depth) throws IOException {
      if(depth > 32 || ++this.count > 1000000) throw bad(); white(); final char c = take();
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
