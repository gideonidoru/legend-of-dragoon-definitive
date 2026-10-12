// Definitive per-file delivery (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;

/** Build a complete immutable candidate; activation remains InstallStore's transaction. */
final class FileDelivery {
  static final class DownloadPaused extends IOException {
    DownloadPaused(final IOException cause) { super(cause.getMessage() + " Completed files are saved. Retry to continue.", cause); }
  }
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
    return install(store,candidate,progress,connection,repairing,false);
  }
  static String install(final InstallStore store, final ReleaseUpdates.Candidate candidate, final InstallProgress progress, final ReleaseUpdates.AssetDownload connection, final boolean repairing, final boolean fullReinstall) throws IOException, InterruptedException {
    return install(store, candidate, progress, connection, repairing, fullReinstall, "");
  }
  static String install(final InstallStore store, final ReleaseUpdates.Candidate candidate, final InstallProgress progress, final ReleaseUpdates.AssetDownload connection, final boolean repairing, final boolean fullReinstall, final String expectedIdentity) throws IOException, InterruptedException {
    return store.withOperation(() -> {
      final Properties state = store.state();
      if(candidate.revision() != null) { ReleaseUpdates.validateContents(candidate); CurrentRelease.requireAllowed(state, candidate.revision()); }
      else if(CurrentRelease.generation(state, "acceptedGeneration") > 0) throw new IOException("Choose a verified current build before updating this installation.");
      final Path current = state.containsKey("version") ? InstallStore.child(store.root().resolve("releases"), state.getProperty("version"), "alpha-[a-f0-9]{16}") : null;
      final InstallStage deliveryStage = InstallStage.open(store.root(), candidate.contents().sha256(), fullReinstall);
      final Path staged = deliveryStage.path();
      final Path inventory = staged.resolve(".download-contents.zip");
      final var network = new InstallProgress.NetworkSampler();
      try {
        final var asset = candidate.contents();
        deliveryStage.requireOwned(store.root()); requireStagedFile(staged, inventory);
        if(!Files.isRegularFile(inventory, LinkOption.NOFOLLOW_LINKS) || Files.size(inventory) > 8 * 1024 * 1024 || !PackageManifest.sha256(inventory).equals(asset.sha256())) {
          Files.deleteIfExists(inventory); Files.createFile(inventory);
          ReleaseUpdates.download(new ReleaseUpdates.Candidate(candidate.tag(), asset.id(), asset.url(), asset.sha256()), inventory, update -> progress.report(new InstallProgress.Update("Checking release inventory", candidate.tag(), 8, 8, 0, 0, "Release inventory", update.completed(), update.total(), update.networkBytes(), update.networkBytesPerSecond())), connection, 8 * 1024 * 1024, network);
        } else progress.phase("Resuming verified download", "Using the verified release inventory", 8);
        if(!PackageManifest.sha256(inventory).equals(asset.sha256())) throw new IOException("Release inventory checksum mismatch. Your current installation is unchanged.");
        if(candidate.revision() != null && Files.size(inventory) != candidate.revision().contentsBytes()) throw new IOException("Current build inventory size mismatch.");
        final Contents contents = read(inventory, candidate.tag());
        if(candidate.revision() != null) candidate.revision().validateMovieContents(contents.manifest().hashes(), contents.sizes());
        if(candidate.revision() != null && !candidate.revision().sourceRevision().equals(contents.manifest().metadata().getProperty("sourceRevision"))) throw new IOException("Current build inventory source mismatch.");
        if(!expectedIdentity.isEmpty() && !expectedIdentity.equals(contents.manifest().id())) throw new IOException("Downloaded inventory differs from the supplied online package. Download the current installer again.");
        if(repairing && candidate.revision() == null && !contents.manifest().id().equals(state.getProperty("version"))) throw new IOException("Repair must match the installed release exactly. Use Update for another release.");
        final var recipe = candidate.revision() == null ? null : ArchiveAssembly.fetch(candidate.revision(), contents, store.root(), connection,
          update -> progress.report(new InstallProgress.Update("Checking archive recipe", candidate.tag(), 8, 8, 0, 0, "Archive recipe", update.completed(), update.total(), update.networkBytes(), update.networkBytesPerSecond())), network);
        final var assembler = recipe == null ? null : new ArchiveAssembly(recipe, current, fullReinstall ? null : ArchiveAssembly.cache(store.root(), current), connection);
        long retained = 0;
        for(final String name : contents.manifest().hashes().stringPropertyNames()) {
          final Path target = staged.resolve(name); requireStagedFile(staged, target);
          final long size = Long.parseLong(contents.sizes().getProperty(name));
          if(reusable(target, staged, size, contents.manifest().hashes().getProperty(name))) retained += size;
          else if(assembler != null && assembler.contains(name)) retained += assembler.retainVerifiedPrefix(name, target);
          else Files.deleteIfExists(target);
        }
        store.requireFileDeliverySpace(contents.bytes() - retained, contents.manifest().id(), fullReinstall);
        final Properties previousHashes = fullReinstall ? null : previousHashes(current);
        for(final String support : PackageManifest.SUPPORT) { requireStagedPath(staged, staged.resolve(support)); Files.createDirectories(staged.resolve(support)); }
        for(final String name : List.of(PackageManifest.METADATA, PackageManifest.HASHES)) {
          final Path target = staged.resolve(name); requireStagedFile(staged, target);
          Files.write(target, contents.documents().get(name), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        }
        long completed = 0; int fetched = 0, reused = 0;
        try(final var movies = new FmvMigration(current, staged, contents.manifest(), !fullReinstall)) {
          for(final String name : new TreeSet<>(contents.manifest().hashes().stringPropertyNames())) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedException("File delivery cancelled.");
            final long size = Long.parseLong(contents.sizes().getProperty(name));
            final String digest = contents.manifest().hashes().getProperty(name);
            final Path target = staged.resolve(name); requireStagedFile(staged, target); Files.createDirectories(target.getParent());
            final Path prior = current == null ? null : current.resolve(name);
            final long base = completed;
            if(reusable(target, staged, size, digest)) {
              progress.report(new InstallProgress.Update("Resuming verified files", module(name) + " · " + name, 10, 60, completed + size, contents.bytes(), name, size, size));
              reused++;
            } else if(!fullReinstall && (assembler == null || !assembler.contains(name) || previousHashes == null || digest.equals(previousHashes.getProperty(name))) && reusable(prior, current, size, digest)) {
              Files.deleteIfExists(target);
              progress.report(new InstallProgress.Update("Reusing verified files", module(name) + " · " + name, 10, 60, completed, contents.bytes(), name, 0, size));
              try(final var input = Files.newInputStream(prior, LinkOption.NOFOLLOW_LINKS); final var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                DownloadBody.copy(input, output, size, Duration.ofMinutes(15), bytes -> progress.report(new InstallProgress.Update("Reusing verified files", module(name) + " · " + name, 10, 60, base + bytes, contents.bytes(), name, bytes, size)));
              }
              reused++;
            } else if(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && movies.importMovie(name, digest, size, target)) {
              progress.report(new InstallProgress.Update("Reusing verified movies", "FMVHD · " + name, 10, 60, completed, contents.bytes(), name, size, size));
              reused++;
            } else if(assembler != null && assembler.contains(name)) {
              final long reusedBytes = assembler.assemble(name, target, !fullReinstall,
                bytes -> progress.report(new InstallProgress.Update("Updating " + module(name), name, 10, 60, base + bytes, contents.bytes(), name, bytes, size)),
                (bytes, telemetry) -> progress.report(new InstallProgress.Update("Updating " + module(name), name, 10, 60, base + bytes, contents.bytes(), name, bytes, size).withNetwork(telemetry)), network);
              if(reusedBytes == size) reused++; else fetched++;
            } else {
              final var blob = new ReleaseUpdates.Candidate(candidate.tag(), "", candidate.url().resolve("file-" + digest), digest);
              Files.createFile(target);
              ReleaseUpdates.download(blob, target, update -> progress.report(new InstallProgress.Update("Downloading " + module(name), name, 10, 60, base + Math.min(size, update.completed()), contents.bytes(), name, update.completed(), size, update.networkBytes(), update.networkBytesPerSecond())), connection, size, network);
              fetched++;
            }
            if(Files.size(target) != size || !PackageManifest.sha256(target).equals(digest)) throw new IOException("File checksum mismatch: " + name + ". Your current installation is unchanged.");
            completed += size;
            progress.report(new InstallProgress.Update("File verified", module(name) + " · " + name, 10, 60, completed, contents.bytes(), name, size, size));
          }
        }
        Files.delete(inventory);
        contents.manifest().verify(staged, PackageManifest.hostPlatform());
        if(candidate.revision() != null) candidate.revision().validateMovieManifest(staged);
        final String result = store.installVerifiedStage(deliveryStage, candidate.assetId(), candidate.publishedAt(), progress, fullReinstall, candidate.revision());
        try { ArchiveAssembly.saveCache(store.root(), store.data(store.state()), contents.manifest().id(), recipe); }
        catch(final IOException optionalCache) { progress.phase("Installation complete", "Archive reuse cache will be rebuilt on the next update", 100); }
        return (repairing ? "Repair complete. " : fullReinstall ? "Reinstall complete. " : state.containsKey("version") ? "Update complete. " : "Install complete. ") + fetched + " files downloaded; " + reused + " verified files reused. " + result;
      } catch(final ReleaseUpdates.TransportFailure paused) { throw new DownloadPaused(paused); }
      finally { deliveryStage.close(); }
    });
  }
  private static void requireStagedPath(final Path staged, final Path target) throws IOException {
    if(!target.startsWith(staged)) throw new IOException("Download scratch path differs from its owned stage.");
    for(Path part = target; part != null && part.startsWith(staged); part = part.getParent()) if(Files.isSymbolicLink(part)) throw new IOException("Download scratch contains a linked path. No files were activated.");
  }
  private static void requireStagedFile(final Path staged, final Path target) throws IOException {
    requireStagedPath(staged, target);
    if(Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Download scratch is not a regular file. No files were activated.");
  }
  private static Properties previousHashes(final Path current) throws IOException {
    if(current == null) return null;
    try { return PackageManifest.read(current).hashes(); }
    catch(final java.io.InterruptedIOException cancelled) { throw cancelled; }
    catch(final IOException | IllegalArgumentException unavailable) { return null; }
  }
  private static boolean reusable(final Path prior, final Path release, final long size, final String digest) throws IOException {
    if(prior == null) return false;
    for(Path part = prior; part != null && part.startsWith(release); part = part.getParent()) if(Files.isSymbolicLink(part)) return false;
    return Files.isRegularFile(prior, LinkOption.NOFOLLOW_LINKS) && Files.size(prior) == size && PackageManifest.sha256(prior).equals(digest);
  }
  static String module(final String name) {
    if(name.startsWith("bundled-mods/")) return name.substring(13).replaceFirst("(?:-v?\\d.*)?\\.jar$", "");
    if(name.startsWith("libs/")) return "engine libraries";
    if(name.startsWith("gfx/fmvhd/")) return "FMVHD";
    if(name.startsWith("gfx/")) return "engine artwork";
    return "Definitive engine";
  }
}
