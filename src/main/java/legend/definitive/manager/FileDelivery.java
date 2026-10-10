// Definitive per-file delivery (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;

/** Build a complete immutable candidate; activation remains InstallStore's transaction. */
final class FileDelivery {
  private FileDelivery() { }
  static final String SIZES = "definitive-sizes.properties";
  private static final Set<String> ENTRIES = Set.of(PackageManifest.METADATA, PackageManifest.HASHES, SIZES);
  record Contents(PackageManifest manifest, Properties sizes, Map<String, byte[]> documents, long bytes) { }

  static Contents read(final Path archive, final String tag) throws IOException, InterruptedException {
    final Map<String, byte[]> documents = new HashMap<>();
    try(final var zip = new ZipFile(archive.toFile())) {
      final var entries = zip.entries(); long bytes = 0;
      while(entries.hasMoreElements()) {
        final var entry = entries.nextElement();
        if(!ENTRIES.contains(entry.getName()) || documents.containsKey(entry.getName()) || entry.getSize() < 0 || entry.getSize() > 4 * 1024 * 1024) throw new IOException("Invalid release file inventory.");
        bytes += entry.getSize(); if(bytes > 8 * 1024 * 1024) throw new IOException("Release inventory is too large.");
        try(final var input = zip.getInputStream(entry); final var output = new java.io.ByteArrayOutputStream()) {
          DownloadBody.copy(input, output, 4 * 1024 * 1024, Duration.ofSeconds(30));
          documents.put(entry.getName(), output.toByteArray());
        }
      }
    }
    if(!documents.keySet().equals(ENTRIES)) throw new IOException("Release inventory is incomplete.");
    final var metadata = properties(documents.get(PackageManifest.METADATA));
    final var hashes = properties(documents.get(PackageManifest.HASHES));
    final var sizes = properties(documents.get(SIZES));
    final var manifest = new PackageManifest(metadata, hashes);
    if(!"1".equals(metadata.getProperty("format")) || !"25".equals(metadata.getProperty("java")) || !PackageManifest.hostPlatform().equals(manifest.platform()) || !tag.equals(metadata.getProperty("releaseTag")) || !PackageManifest.identity(metadata, hashes).equals(manifest.id())) throw new IOException("Release file inventory identity mismatch.");
    if(!sizes.stringPropertyNames().equals(hashes.stringPropertyNames()) || hashes.size() > 30000 || !hashes.stringPropertyNames().containsAll(PackageManifest.ROOT_FILES)) throw new IOException("Release file inventory is incomplete.");
    long bytes = 0;
    for(final String name : hashes.stringPropertyNames()) {
      if(!PackageManifest.allowed(name) || name.length() > 1024 || name.indexOf(0) >= 0 || !hashes.getProperty(name).matches("[a-f0-9]{64}") || !sizes.getProperty(name).matches("0|[1-9][0-9]{0,10}")) throw new IOException("Invalid release file: " + name);
      final long size = Long.parseLong(sizes.getProperty(name)); bytes += size;
      if(size > 8L * 1024 * 1024 * 1024 || bytes > 8L * 1024 * 1024 * 1024) throw new IOException("Release inventory exceeds the package limit.");
    }
    return new Contents(manifest, sizes, Map.copyOf(documents), bytes);
  }
  private static Properties properties(final byte[] bytes) throws IOException {
    final Properties properties = new Properties() {
      @Override public synchronized Object put(final Object key, final Object value) { if(this.containsKey(key)) throw new IllegalArgumentException("Duplicate property"); return super.put(key, value); }
    };
    try { properties.load(new java.io.ByteArrayInputStream(bytes)); }
    catch(final IllegalArgumentException invalid) { throw new IOException("Invalid release properties.", invalid); }
    return properties;
  }

  static String install(final InstallStore store, final ReleaseUpdates.Candidate candidate, final InstallProgress progress, final ReleaseUpdates.AssetDownload connection, final boolean repairing) throws IOException, InterruptedException {
    return store.withOperation(() -> {
      final Properties state = store.state();
      final Path current = state.containsKey("version") ? InstallStore.child(store.root().resolve("releases"), state.getProperty("version"), "alpha-[a-f0-9]{16}") : null;
      final Path inventory = Files.createTempFile(store.root(), ".contents-", ".zip");
      final Path staged = Files.createTempDirectory(store.root(), ".files-");
      try {
        final var asset = candidate.contents();
        ReleaseUpdates.download(new ReleaseUpdates.Candidate(candidate.tag(), asset.id(), asset.url(), asset.sha256()), inventory, update -> progress.phase("Checking release inventory", candidate.tag(), 8), connection, 8 * 1024 * 1024);
        if(!PackageManifest.sha256(inventory).equals(asset.sha256())) throw new IOException("Release inventory checksum mismatch. Your current installation is unchanged.");
        final Contents contents = read(inventory, candidate.tag());
        if(repairing && !contents.manifest().id().equals(state.getProperty("version"))) throw new IOException("Repair must match the installed release exactly. Use Update for another release.");
        store.requireFileDeliverySpace(contents.bytes(), contents.manifest().id());
        for(final String support : PackageManifest.SUPPORT) Files.createDirectories(staged.resolve(support));
        for(final String name : List.of(PackageManifest.METADATA, PackageManifest.HASHES)) Files.write(staged.resolve(name), contents.documents().get(name));
        long completed = 0; int fetched = 0, reused = 0;
        for(final String name : new TreeSet<>(contents.manifest().hashes().stringPropertyNames())) {
          if(Thread.currentThread().isInterrupted()) throw new InterruptedException("File delivery cancelled.");
          final long size = Long.parseLong(contents.sizes().getProperty(name));
          final String digest = contents.manifest().hashes().getProperty(name);
          final Path target = staged.resolve(name); Files.createDirectories(target.getParent());
          final Path prior = current == null ? null : current.resolve(name);
          final long base = completed;
          if(reusable(prior, current, size, digest)) {
            progress.report(new InstallProgress.Update("Reusing verified files", module(name) + " · " + name, 10, 60, completed, contents.bytes(), name, 0, size));
            try(final var input = Files.newInputStream(prior, LinkOption.NOFOLLOW_LINKS); final var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
              DownloadBody.copy(input, output, size, Duration.ofMinutes(15), bytes -> progress.report(new InstallProgress.Update("Reusing verified files", module(name) + " · " + name, 10, 60, base + bytes, contents.bytes(), name, bytes, size)));
            }
            reused++;
          } else {
            final var blob = new ReleaseUpdates.Candidate(candidate.tag(), "", candidate.url().resolve("file-" + digest), digest);
            Files.createFile(target);
            ReleaseUpdates.download(blob, target, update -> progress.report(new InstallProgress.Update("Downloading " + module(name), name, 10, 60, base + Math.min(size, update.completed()), contents.bytes(), name, update.completed(), size)), connection, size);
            fetched++;
          }
          if(Files.size(target) != size || !PackageManifest.sha256(target).equals(digest)) throw new IOException("File checksum mismatch: " + name + ". Your current installation is unchanged.");
          completed += size;
          progress.report(new InstallProgress.Update("File verified", module(name) + " · " + name, 10, 60, completed, contents.bytes(), name, size, size));
        }
        contents.manifest().verify(staged, PackageManifest.hostPlatform());
        final String result = store.install(staged, candidate.assetId(), candidate.publishedAt(), progress);
        return (repairing ? "Repair complete. " : "Update complete. ") + fetched + " files downloaded; " + reused + " verified files reused. " + result;
      } finally { Files.deleteIfExists(inventory); InstallStore.deleteOwnedTree(staged); }
    });
  }
  private static boolean reusable(final Path prior, final Path release, final long size, final String digest) throws IOException {
    if(prior == null) return false;
    for(Path part = prior; part != null && part.startsWith(release); part = part.getParent()) if(Files.isSymbolicLink(part)) return false;
    return Files.isRegularFile(prior, LinkOption.NOFOLLOW_LINKS) && Files.size(prior) == size && PackageManifest.sha256(prior).equals(digest);
  }
  static String module(final String name) {
    if(name.startsWith("bundled-mods/")) return name.substring(13).replaceFirst("(?:-v?\\d.*)?\\.jar$", "");
    if(name.startsWith("libs/")) return "engine libraries";
    if(name.startsWith("gfx/")) return "engine artwork";
    return "Definitive engine";
  }
}
