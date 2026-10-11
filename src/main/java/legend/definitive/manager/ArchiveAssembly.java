// Definitive exact-byte assembly (2026-10-11), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Recipes describe bytes only. Inventory verification and activation stay in FileDelivery. */
final class ArchiveAssembly {
  static final int MAX_BYTES = 16 * 1024 * 1024, MAX_SPANS = 100000;
  private static final long MAX_SOURCE = 2L * 1024 * 1024 * 1024 - 1;
  private static final String BASE = "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-current/";
  record Span(String sha256, int bytes, String source, long offset) { }
  record Source(String id, long bytes) { }
  record Recipe(Map<String, Source> sources, Map<String, List<Span>> files, byte[] document) {
    Recipe { sources = Map.copyOf(sources); files = Map.copyOf(files); document = document.clone(); }
    @Override public byte[] document() { return this.document.clone(); }
  }
  private record Chunk(String sha256, int bytes) { }
  private record Local(Path path, long offset) { }
  private final Recipe recipe;
  private final Path current;
  private final Map<Chunk, List<Local>> local = new HashMap<>();
  private final Set<String> indexed = new HashSet<>();
  private final ReleaseUpdates.AssetDownload connection;
  private final long deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos();
  private int requests, localEntries;
  private long indexedBytes;

  ArchiveAssembly(final Recipe recipe, final Path current, final Recipe cache, final ReleaseUpdates.AssetDownload connection) throws IOException {
    this.recipe = recipe; this.current = current; this.connection = connection;
    if(current != null && cache != null) for(final var entry : cache.files().entrySet()) {
      long offset = 0;
      for(final Span span : entry.getValue()) {
        PackageManifest.checkCancelled();
        remember(span.sha256(), span.bytes(), current.resolve(entry.getKey()), offset);
        offset += span.bytes();
      }
    }
  }
  static Recipe read(final byte[] bytes, final String source, final String platform, final Properties hashes, final Properties sizes,
                     final Map<String, ReleaseUpdates.SourceAsset> catalog) throws IOException {
    if(bytes.length == 0 || bytes.length > MAX_BYTES) throw bad();
    // Publisher uses ASCII with JSON escapes; no serializer-dependent byte normalization.
    for(final byte value : bytes) if(value < 0) throw bad();
    final var root = object(new ReleaseUpdates.Json(new String(bytes, StandardCharsets.US_ASCII)).read());
    fields(root, "format", "sourceRevision", "platform", "releaseTag", "sources", "files");
    if(!Long.valueOf(1).equals(root.get("format")) || !source.equals(root.get("sourceRevision")) || !platform.equals(root.get("platform")) || !"definitive-current".equals(root.get("releaseTag"))) throw bad();
    final var rawSources = object(root.get("sources"));
    if(rawSources.size() > 1000) throw bad();
    final Map<String, Source> sources = new HashMap<>(); final Set<String> ids = new HashSet<>();
    for(final var entry : rawSources.entrySet()) {
      PackageManifest.checkCancelled();
      final String sha = digest(entry.getKey()); final var value = object(entry.getValue()); fields(value, "id", "bytes");
      final String id = text(value.get("id"));
      if(!id.matches("[1-9][0-9]{0,15}") || Long.parseLong(id) > 9007199254740991L || !ids.add(id)) throw bad();
      final long size = number(value.get("bytes"), MAX_SOURCE, false);
      if(catalog != null) {
        final var asset = catalog.get(sha);
        if(asset == null || !asset.id().equals(id) || asset.bytes() != size || !asset.url().equals(URI.create(BASE + "file-" + sha))) throw bad();
      }
      sources.put(sha, new Source(id, size));
    }
    final var rawFiles = object(root.get("files"));
    if(rawFiles.size() > 30000) throw bad();
    final Map<String, List<Span>> files = new HashMap<>(); final Set<String> used = new HashSet<>();
    final Map<String, TreeMap<Long, Span>> intervals = new HashMap<>(); int count = 0;
    for(final var entry : rawFiles.entrySet()) {
      final String name = text(entry.getKey());
      if(!PackageManifest.allowed(name) || name.length() > 1024 || name.chars().anyMatch(c -> c < 32 || c > 126) || name.split("/").length > 64 || !hashes.containsKey(name) || !sizes.containsKey(name) || !(entry.getValue() instanceof List<?> rows) || rows.isEmpty()) throw bad();
      final List<Span> spans = new ArrayList<>(); long total = 0;
      for(final Object item : rows) {
        PackageManifest.checkCancelled();
        if(++count > MAX_SPANS) throw bad();
        final var row = object(item); fields(row, "sha256", "bytes", "source", "offset");
        final String sha = digest(row.get("sha256")), key = digest(row.get("source"));
        final int size = (int)number(row.get("bytes"), MAX_BYTES, false);
        final long offset = number(row.get("offset"), MAX_SOURCE, true);
        final Source identity = sources.get(key);
        if(identity == null || offset > identity.bytes() - size) throw bad();
        final Span span = new Span(sha, size, key, offset);
        final var tree = intervals.computeIfAbsent(key, unused -> new TreeMap<>());
        final var prior = tree.floorEntry(offset); final var next = tree.ceilingEntry(offset);
        if(prior != null && prior.getKey() + prior.getValue().bytes() > offset && !prior.getValue().equals(span)
          || next != null && offset + size > next.getKey() && !next.getValue().equals(span)) throw bad();
        tree.put(offset, span); used.add(key); spans.add(span); total += size;
        if(total > MAX_SOURCE) throw bad();
      }
      if(!Long.toString(total).equals(sizes.getProperty(name))) throw bad();
      files.put(name, List.copyOf(spans));
    }
    for(final String name : files.keySet()) {
      int slash = name.indexOf('/');
      while(slash >= 0) { if(files.containsKey(name.substring(0, slash))) throw bad(); slash = name.indexOf('/', slash + 1); }
    }
    if(!used.equals(sources.keySet())) throw bad();
    return new Recipe(sources, files, bytes);
  }
  static Recipe fetch(final ReleaseUpdates.CurrentRevision revision, final FileDelivery.Contents contents, final Path scratch,
                      final ReleaseUpdates.AssetDownload connection) throws IOException, InterruptedException {
    final var descriptor = revision.assembly();
    if(descriptor == null) return null;
    final Path path = Files.createTempFile(scratch, ".assembly-", ".json");
    try {
      ReleaseUpdates.download(new ReleaseUpdates.Candidate("definitive-current", descriptor.id(), descriptor.url(), descriptor.sha256()), path, InstallProgress.NONE, connection, MAX_BYTES);
      if(Files.size(path) != descriptor.bytes() || !PackageManifest.sha256(path).equals(descriptor.sha256())) throw bad();
      return read(Files.readAllBytes(path), revision.sourceRevision(), contents.manifest().platform(), contents.manifest().hashes(), contents.sizes(), revision.sources());
    } finally { Files.deleteIfExists(path); }
  }
  boolean contains(final String name) { return this.recipe.files().containsKey(name); }

  /** SHA-checked bounded buffers prevent local mutation between verification and copying. */
  long assemble(final String name, final Path target, final boolean reuse, final java.util.function.LongConsumer progress) throws IOException, InterruptedException {
    final List<Span> spans = this.recipe.files().get(name); long reused = 0, completed = 0;
    if(reuse) index(name);
    try(final var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
      for(int at = 0; at < spans.size();) {
        PackageManifest.checkCancelled();
        final Span first = spans.get(at); final byte[] cached = reuse ? local(first) : null;
        if(cached != null) { output.write(cached); reused += cached.length; completed += cached.length; progress.accept(completed); at++; continue; }
        int end = at + 1, length = first.bytes();
        while(end < spans.size()) {
          final Span next = spans.get(end);
          if(!next.source().equals(first.source()) || next.offset() != first.offset() + length || length + next.bytes() > MAX_BYTES || reuse && hasLocal(next)) break;
          length += next.bytes(); end++;
        }
        final byte[] bytes = range(first.source(), first.offset(), length);
        int offset = 0;
        for(int row = at; row < end; row++) {
          final Span span = spans.get(row);
          if(!digest(bytes, offset, span.bytes()).equals(span.sha256())) throw new InvalidRange("Archive chunk checksum mismatch.");
          offset += span.bytes();
        }
        output.write(bytes); completed += length; progress.accept(completed); at = end;
      }
    }
    return reused;
  }
  private void remember(final String digest, final int bytes, final Path path, final long offset) {
    if(this.localEntries >= MAX_SPANS) return;
    final var positions = this.local.computeIfAbsent(new Chunk(digest, bytes), unused -> new ArrayList<>());
    if(positions.size() < 8) { positions.add(new Local(path, offset)); this.localEntries++; }
  }
  private boolean hasLocal(final Span span) { return this.local.containsKey(new Chunk(span.sha256(), span.bytes())); }
  private byte[] local(final Span span) throws IOException {
    final var positions = this.local.get(new Chunk(span.sha256(), span.bytes()));
    if(positions == null) return null;
    for(final Local position : positions) {
      PackageManifest.checkCancelled();
      try {
        if(!safe(position.path())) continue;
        try(final var channel = FileChannel.open(position.path(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
          if(position.offset() > channel.size() - span.bytes()) continue;
          final byte[] bytes = RawArchive.read(channel, position.offset(), span.bytes());
          if(PackageManifest.digest(bytes).equals(span.sha256())) return bytes;
        }
      } catch(final java.io.InterruptedIOException cancelled) { throw cancelled; }
      catch(final IOException unavailable) { /* Optional local reuse; remote digest remains authoritative. */ }
    }
    return null;
  }
  private boolean safe(final Path path) {
    if(this.current == null || !path.startsWith(this.current)) return false;
    for(Path part = path; part != null && part.startsWith(this.current); part = part.getParent()) if(Files.isSymbolicLink(part)) return false;
    return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
  }
  private void index(final String name) throws IOException {
    if(this.current == null || !this.indexed.add(name)) return;
    final Path path = this.current.resolve(name); if(!safe(path)) return;
    try(final var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      for(final var range : RawArchive.ranges(channel, name.endsWith(".zip") || name.endsWith(".jar"))) {
        PackageManifest.checkCancelled();
        if(this.localEntries >= MAX_SPANS || this.indexedBytes + range.bytes() > 8L * 1024 * 1024 * 1024 || System.nanoTime() >= this.deadline) return;
        this.indexedBytes += range.bytes();
        remember(PackageManifest.digest(RawArchive.read(channel, range.offset(), range.bytes())), range.bytes(), path, range.offset());
      }
    } catch(final java.io.InterruptedIOException cancelled) { throw cancelled; }
    catch(final IOException unavailable) { /* Malformed old archives do not block a verified download. */ }
  }
  private byte[] range(final String source, final long offset, final int bytes) throws IOException, InterruptedException {
    final Source identity = this.recipe.sources().get(source);
    final long end = offset + bytes - 1; IOException last = null;
    for(int attempt = 0; attempt < 3; attempt++) {
      PackageManifest.checkCancelled();
      final long remaining = this.deadline - System.nanoTime();
      if(remaining <= 0 || ++this.requests > MAX_SPANS * 3) throw new IOException("Archive assembly exceeded its transfer budget.");
      final var request = HttpRequest.newBuilder(URI.create(BASE + "file-" + source))
        .header("Range", "bytes=" + offset + "-" + end).header("Accept-Encoding", "identity")
        .timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(30).toNanos()))).GET().build();
      try {
        final var response = this.connection.send(request);
        try(final var input = response.body()) {
          if(Set.of(429, 500, 502, 503, 504).contains(response.statusCode())) {
            final String cooldown = response.headers().firstValue("Retry-After").orElse("");
            if(cooldown.matches("[0-9]{1,8}") && Long.parseLong(cooldown) > 2) throw new InvalidRange("Server requests a longer wait. Retry this update later.");
            throw new IOException("Archive source is temporarily unavailable.");
          }
          if(response.statusCode() != 206 || !response.headers().allValues("Content-Range").equals(List.of("bytes " + offset + "-" + end + "/" + identity.bytes()))
            || response.headers().allValues("Content-Encoding").stream().anyMatch(value -> !value.equalsIgnoreCase("identity"))) throw new InvalidRange("Archive source returned an invalid byte range.");
          final var lengths = response.headers().allValues("Content-Length");
          if(!lengths.isEmpty() && !lengths.equals(List.of(Integer.toString(bytes)))) throw new InvalidRange("Archive source returned an invalid body size.");
          final var output = new ByteArrayOutputStream(bytes);
          final long timeout = this.deadline - System.nanoTime(); if(timeout <= 0) throw new IOException("Archive transfer took too long.");
          DownloadBody.copy(input, output, bytes, Duration.ofNanos(timeout));
          if(output.size() != bytes) throw new InvalidRange("Archive source returned a truncated byte range.");
          return output.toByteArray();
        }
      } catch(final InvalidRange | DownloadBody.SizeLimitException | DownloadBody.WorkerCleanupException | DownloadBody.WriteException permanent) { throw permanent; }
      catch(final IOException retry) { last = retry; }
      if(attempt < 2) Thread.sleep(250L << attempt);
    }
    throw new IOException("Archive transfer failed after bounded retries.", last);
  }
  static Recipe cache(final Path root, final Path current) throws IOException {
    if(current == null) return null;
    for(Path part = current; part != null && part.startsWith(root); part = part.getParent()) if(Files.isSymbolicLink(part)) return null;
    final Path directory = root.resolve(".archive-assembly");
    final String identity = current.getFileName().toString(); final Path path = directory.resolve(identity + ".json");
    try {
      if(Files.isSymbolicLink(directory) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES * 2L) return null;
      final var envelope = object(new ReleaseUpdates.Json(cacheText(path)).read());
      fields(envelope, "format", "package", "recipeSha256", "recipe");
      if(!Long.valueOf(1).equals(envelope.get("format")) || !identity.equals(envelope.get("package"))) return null;
      final byte[] bytes = Base64.getDecoder().decode(text(envelope.get("recipe")));
      if(!PackageManifest.digest(bytes).equals(digest(envelope.get("recipeSha256")))) return null;
      final var manifest = PackageManifest.read(current); final var sizes = new Properties();
      for(final String name : manifest.hashes().stringPropertyNames()) {
        final Path file = current.resolve(name);
        if(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) sizes.setProperty(name, Long.toString(Files.size(file)));
      }
      return read(bytes, manifest.metadata().getProperty("sourceRevision"), manifest.platform(), manifest.hashes(), sizes, null);
    } catch(final java.io.InterruptedIOException cancelled) { throw cancelled; }
    catch(final IllegalArgumentException | IOException unavailable) { return null; }
  }
  private static String cacheText(final Path path) throws IOException {
    try(final var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS); final var output = new ByteArrayOutputStream()) {
      final byte[] buffer = new byte[65536]; int total = 0;
      for(int count; (count = input.read(buffer)) != -1;) {
        PackageManifest.checkCancelled(); total += count;
        if(total > MAX_BYTES * 2) throw bad(); output.write(buffer, 0, count);
      }
      return output.toString(StandardCharsets.US_ASCII);
    }
  }
  static void saveCache(final Path root, final String identity, final Recipe recipe) throws IOException {
    if(recipe == null) return;
    final Path directory = root.resolve(".archive-assembly");
    if(Files.isSymbolicLink(directory)) throw new IOException("Archive cache is linked.");
    Files.createDirectories(directory);
    final byte[] bytes = recipe.document();
    final String envelope = ReleaseUpdates.json(Map.of("format", 1L, "package", identity, "recipeSha256", PackageManifest.digest(bytes), "recipe", Base64.getEncoder().encodeToString(bytes)));
    final Path target = InstallStore.child(directory, identity + ".json", "alpha-[a-f0-9]{16}\\.json");
    if(Files.isSymbolicLink(target)) throw new IOException("Archive cache receipt is linked.");
    final Path temporary = Files.createTempFile(directory, ".receipt-", ".json");
    try {
      Files.writeString(temporary, envelope, StandardCharsets.US_ASCII);
      try(final var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally { Files.deleteIfExists(temporary); }
  }
  private static String digest(final byte[] bytes, final int offset, final int length) {
    try { final var sha = java.security.MessageDigest.getInstance("SHA-256"); sha.update(bytes, offset, length); return HexFormat.of().formatHex(sha.digest()); }
    catch(final java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
  private static IOException bad() { return new IOException("Archive assembly recipe disagrees with the verified release inventory."); }
  private static Map<?, ?> object(final Object value) throws IOException { if(value instanceof Map<?, ?> map) return map; throw bad(); }
  private static String text(final Object value) throws IOException { if(value instanceof String text) return text; throw bad(); }
  private static String digest(final Object value) throws IOException { final String text = text(value); if(!text.matches("[a-f0-9]{64}")) throw bad(); return text; }
  private static void fields(final Map<?, ?> value, final String... names) throws IOException { if(!value.keySet().equals(Set.of(names))) throw bad(); }
  private static long number(final Object value, final long max, final boolean zero) throws IOException { if(value instanceof Long number && number >= (zero ? 0 : 1) && number <= max) return number; throw bad(); }
  private static final class InvalidRange extends IOException { InvalidRange(final String message) { super(message); } }
}
