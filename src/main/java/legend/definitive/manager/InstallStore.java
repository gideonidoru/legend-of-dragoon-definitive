// Definitive installation tooling (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipInputStream;

/** Immutable packages, separate private data generations, atomic activation. */
public final class InstallStore {
  private final Path root;
  @FunctionalInterface interface BootstrapPublisher { void publish(Path staged, Path target) throws IOException; }
  private final BootstrapPublisher bootstrapPublisher;
  private final ThreadLocal<HeldOperation> held = new ThreadLocal<>();
  private static final long MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024;
  static final long PREPARATION_RESERVE_BYTES = 6L * 1024 * 1024 * 1024;

  public InstallStore(final Path root) throws IOException { this(root, InstallStore::replaceFile); }
  InstallStore(final Path root, final BootstrapPublisher bootstrapPublisher) throws IOException {
    this.bootstrapPublisher = bootstrapPublisher;
    this.root = root.toAbsolutePath().normalize();
    if(this.root.toString().matches("(?s).*[:\\n\\r].*")) throw new IOException("Choose a folder without colons or line breaks.");
    for(Path p = this.root; p != null; p = p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Choose a real installation folder, not a linked folder.");
    if(Files.exists(this.root) && !Files.exists(this.root.resolve(".definitive-owned"))) {
      try(final var entries = Files.list(this.root)) {
        if(entries.findAny().isPresent()) throw new IOException("Choose an empty folder or an existing Definitive managed installation.");
      }
    }
    Files.createDirectories(this.root);
    final Path marker = this.root.resolve(".definitive-owned");
    if(!Files.exists(marker)) Files.writeString(marker, "Legend of Dragoon Definitive install format 1\n", StandardOpenOption.CREATE_NEW);
    if(Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 1024 || !Files.readString(marker).equals("Legend of Dragoon Definitive install format 1\n")) throw new IOException("This folder does not have a recognized Definitive ownership record. No installation files were replaced.");
    this.checkOwnedPaths();
    for(final String dir : new String[]{"releases", "data", "workspaces", "snapshots"}) Files.createDirectories(this.root.resolve(dir));
    if(Files.exists(this.root.resolve(".disc-transaction.properties"), LinkOption.NOFOLLOW_LINKS)
      || Files.exists(this.root.resolve(".release-repair-transaction.properties"), LinkOption.NOFOLLOW_LINKS)
      || Files.exists(this.root.resolve(".bootstrap-transaction.properties"), LinkOption.NOFOLLOW_LINKS)
      || !Files.exists(this.root.resolve("state.properties")) && Files.exists(this.root.resolve(".state-last-good.properties"))) {
      try(final var operation = this.operation()) { /* Recovery runs before missing paths are created. */ }
    }
    if(!Files.exists(this.root.resolve(".disc-transaction.properties"), LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(this.root.resolve("isos"));
  }

  public Path root() { return this.root; }
  public Properties state() throws IOException {
    try {
      final Properties current = readState(this.root.resolve("state.properties"));
      if(current.isEmpty() && Files.exists(this.root.resolve(".state-last-good.properties"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Installation state is missing.");
      return current;
    }
    catch(final IOException failure) {
      if(!Files.exists(this.root.resolve(".state-last-good.properties"), LinkOption.NOFOLLOW_LINKS)) throw failure;
      try(final var operation = this.operation()) { return readState(this.root.resolve("state.properties")); }
    }
  }

  private void checkOwnedPaths() throws IOException {
    for(final String name : new String[]{".definitive-owned", "state.properties", ".state-last-good.properties", ".disc-transaction.properties", ".release-repair-transaction.properties", ".bootstrap-transaction.properties", ".game-lock", GameLease.PENDING, GameLease.RUNNING, ".operation-lock", "releases", "data", "workspaces", "snapshots", "isos", "definitive-manager.jar", "bootstrap-java", "Play Game.sh", "Manage Installation.sh", ".java-path", "launcher.log"}) {
      if(Files.isSymbolicLink(this.root.resolve(name))) throw new IOException("Unexpected link in installation: " + name);
    }
  }

  public Operation lock() throws IOException {
    final HeldOperation existing = this.held.get();
    if(existing != null) { if(!existing.reentrant) throw new IOException("Close the game or other installation operation first."); existing.depth++; return new Operation(existing); }
    this.checkOwnedPaths();
    final FileChannel channel = FileChannel.open(this.root.resolve(".operation-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    final FileLock lock;
    try { lock = channel.tryLock(); }
    catch(final java.nio.channels.OverlappingFileLockException e) { channel.close(); throw new IOException("Close the game or other installation operation first."); }
    if(lock == null) { channel.close(); throw new IOException("Close the game or other installation operation first."); }
    final HeldOperation owned = new HeldOperation(channel, lock); this.held.set(owned);
    try {
      GameLease.checkIdle(this.root);
      this.recoverReleaseRepair();
      this.recoverState();
      this.recoverDiscs();
      this.recoverBootstrap();
      return new Operation(owned);
    } catch(final IOException | RuntimeException failure) {
      try { lock.release(); } finally { channel.close(); this.held.remove(); }
      throw failure;
    }
  }

  Operation operation() throws IOException {
    final Operation operation = this.lock(); operation.operation.reentrant = true; return operation;
  }

  private static final class HeldOperation {
    final FileChannel channel; final FileLock lock; int depth = 1; boolean reentrant;
    HeldOperation(final FileChannel channel, final FileLock lock) { this.channel = channel; this.lock = lock; }
  }
  public final class Operation implements AutoCloseable {
    private final HeldOperation operation; private boolean closed;
    private Operation(final HeldOperation operation) { this.operation = operation; }
    public FileChannel channel() { return this.operation.channel; }
    public FileLock lock() { return this.operation.lock; }
    @Override public void close() throws IOException {
      if(this.closed) return;
      if(held.get() != this.operation) throw new IOException("Installation operation closed from the wrong thread.");
      this.closed = true;
      if(--this.operation.depth == 0) try { this.operation.lock.release(); } finally { this.operation.channel.close(); held.remove(); }
    }
  }
  @FunctionalInterface public interface Work<T> { T run() throws IOException, InterruptedException; }
  public <T> T withOperation(final Work<T> work) throws IOException, InterruptedException {
    try(final var operation = this.operation()) { return work.run(); }
  }

  public String install(final Path source) throws IOException { return this.install(source, ""); }

  String install(final Path source, final String releaseAssetId) throws IOException {
    return this.install(source, releaseAssetId, InstallProgress.NONE);
  }
  String install(final Path source, final String releaseAssetId, final InstallProgress progress) throws IOException {
    return this.install(source, releaseAssetId, null, progress);
  }
  String install(final Path source, final String releaseAssetId, final java.time.Instant publishedAt, final InstallProgress progress) throws IOException {
    try(final var operation = this.operation()) {
      final Properties active = this.state();
      if(publishedAt != null && !active.getProperty("releasePublishedAt", "").isEmpty()) {
        final java.time.Instant installed;
        try { installed = java.time.Instant.parse(active.getProperty("releasePublishedAt")); }
        catch(final java.time.DateTimeException failure) { throw new IOException("Invalid installed release ordering. No update was activated.", failure); }
        if(!releaseAssetId.equals(active.getProperty("releaseAssetId", "")) && !publishedAt.isAfter(installed)) throw new IOException("A newer release is already installed. Check for updates again.");
      }
      this.requireInstallSpace(source);
      progress.phase("Installing game and HD artwork", "Unpacking the verified package into " + this.root, 65);
      final Path staged = Files.createTempDirectory(this.root, ".package-");
      try {
        if(Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
          PackageManifest.read(source).verify(source, PackageManifest.hostPlatform());
          if(this.root.startsWith(source.toAbsolutePath().normalize())) throw new IOException("Choose a package outside the installation folder.");
          copyTree(source, staged);
        }
        else extractZip(source, staged, progress);
        progress.phase("Verifying installed files", "Checking the engine, libraries, artwork and license inventory", 82);
        final PackageManifest manifest = PackageManifest.read(staged);
        manifest.verify(staged, PackageManifest.hostPlatform());
        forceTree(staged);
        final Properties old = this.state();
        final Path release = this.root.resolve("releases").resolve(manifest.id());
        if(Files.exists(release)) {
          if(Files.isSymbolicLink(release)) throw new IOException("Unexpected linked release. No external files were changed.");
          try { PackageManifest.read(release).verify(release, PackageManifest.hostPlatform()); }
          catch(final IOException damaged) {
            // Exact verified package identity under our owned releases directory: repair it as a unit.
            final Path recovery = this.root.resolve("release-recovery-" + UUID.randomUUID());
            final Properties journal = new Properties(); journal.setProperty("format", "1"); journal.setProperty("release", manifest.id());
            final String repairToken = UUID.randomUUID().toString(); journal.setProperty("token", repairToken);
            journal.setProperty("staged", staged.getFileName().toString()); journal.setProperty("backup", recovery.getFileName().toString());
            atomicProperties(this.root.resolve(".release-repair-transaction.properties"), journal);
            markRecoveryBackup(release, ".definitive-release-backup", repairToken);
            Files.move(release, recovery, StandardCopyOption.ATOMIC_MOVE); forceDirectory(this.root); forceDirectory(release.getParent());
            progress.phase("Repairing verified release", "Replacing the damaged engine as one package", 90);
            try { Files.move(staged, release, StandardCopyOption.ATOMIC_MOVE); forceDirectory(release.getParent()); Files.delete(this.root.resolve(".release-repair-transaction.properties")); forceDirectory(this.root); }
            catch(final IOException failure) { try { verifyRecoveryBackup(recovery, ".definitive-release-backup", repairToken); Files.move(recovery, release, StandardCopyOption.ATOMIC_MOVE); Files.delete(release.resolve(".definitive-release-backup")); forceDirectory(release); forceDirectory(release.getParent()); Files.deleteIfExists(this.root.resolve(".release-repair-transaction.properties")); forceDirectory(this.root); } catch(final IOException restore) { failure.addSuppressed(restore); } throw failure; }
            InstallerLog.write("Repaired damaged managed release; previous files retained at " + recovery);
          }
          if(manifest.id().equals(old.getProperty("version")) && !Boolean.parseBoolean(old.getProperty("uninstalled", "false"))) {
            this.writeLaunchers(release);
            this.verifyInstalled();
            if(!releaseAssetId.isEmpty()) { old.setProperty("releaseAssetId", releaseAssetId); if(publishedAt != null) old.setProperty("releasePublishedAt", publishedAt.toString()); atomicProperties(this.root.resolve("state.properties"), old); }
            progress.phase("Installation verified", this.root.toString(), 100);
            return "This version is already installed.";
          }
        } else { Files.move(staged, release, StandardCopyOption.ATOMIC_MOVE); forceDirectory(release.getParent()); }

        final String dataId = "data-" + UUID.randomUUID();
        final Path nextData = this.root.resolve("data").resolve(dataId);
        final String snapshotId;
        if(old.containsKey("version")) {
          final Path activeData = this.data(old);
          snapshotId = "snapshot-" + UUID.randomUUID();
          final Path snapshot = this.root.resolve("snapshots").resolve(snapshotId);
          copyTree(activeData, snapshot);
          writeInventory(snapshot);
          verifyInventory(snapshot);
          forceTree(snapshot);
          copyTree(activeData, nextData);
        } else {
          snapshotId = "";
          Files.createDirectory(nextData);
        }
        for(final String name : new String[]{"saves", "mods", "config", "texture-packs"}) Files.createDirectories(nextData.resolve(name));
        final Properties next = new Properties();
        for(final String property : old.stringPropertyNames()) if(property.startsWith("recovery")) next.setProperty(property, old.getProperty(property));
        next.setProperty("version", manifest.id());
        next.setProperty("data", dataId);
        next.setProperty("previousVersion", Boolean.parseBoolean(old.getProperty("uninstalled", "false")) ? "" : old.getProperty("version", ""));
        next.setProperty("previousSnapshot", snapshotId);
        next.setProperty("previousArtwork", old.getProperty("artwork", "hd"));
        next.setProperty("previousReleaseAssetId", old.getProperty("releaseAssetId", ""));
        next.setProperty("artwork", old.getProperty("artwork", "hd"));
        next.setProperty("legacyTextures", old.getProperty("legacyTextures", "false"));
        next.setProperty("fullscreen", old.getProperty("fullscreen", "true"));
        next.setProperty("previousLegacyTextures", old.getProperty("legacyTextures", "false"));
        next.setProperty("previousFullscreen", old.getProperty("fullscreen", "true"));
        next.setProperty("previousReleasePublishedAt", old.getProperty("releasePublishedAt", ""));
        if(!releaseAssetId.isEmpty()) next.setProperty("releaseAssetId", releaseAssetId);
        if(publishedAt != null) next.setProperty("releasePublishedAt", publishedAt.toString());
        progress.phase("Creating the launcher", "Writing Play Game.sh and activating the verified version", 95);
        this.writeLaunchers(release);
        forceTree(nextData);
        this.verifyInstalled(next);
        atomicProperties(this.root.resolve("state.properties"), next);
        progress.phase("Installation verified", this.root.toString(), 100);
        return old.containsKey("version") ? "Update installed. Previous engine and pre-update data are retained for rollback." : "Installed. Import your discs, then add Play Game.sh to Steam.";
      } finally { if(!Files.exists(this.root.resolve(".release-repair-transaction.properties"), LinkOption.NOFOLLOW_LINKS)) deleteOwnedTree(staged); }
    }
  }

  public void verifyInstalled() throws IOException {
    this.verifyInstalled(this.state());
  }
  private void verifyInstalled(final Properties state) throws IOException {
    if(Boolean.parseBoolean(state.getProperty("uninstalled", "false"))) throw new IOException("Definitive is uninstalled. Choose Reinstall to restore the game.");
    final Path release = child(this.root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
    PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
    if(!Files.isDirectory(this.data(state))) throw new IOException("Installation data folder is missing. Retry installation.");
    for(final String name : new String[]{"Play Game.sh", "Manage Installation.sh", "definitive-manager.jar", "bootstrap-java"}) if(!Files.isRegularFile(this.root.resolve(name), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Launcher file is missing: " + name + ". Retry installation.");
    this.verifyBootstrap();
    if(!Files.isExecutable(this.root.resolve("Play Game.sh")) || !Files.isExecutable(this.root.resolve("bootstrap-java"))) throw new IOException("This drive does not allow executable launchers. Choose an executable filesystem, then retry installation.");
  }

  private void verifyBootstrap() throws IOException { this.verifiedRouterRelease(); }
  Path verifiedRouterRelease() throws IOException {
    this.checkOwnedPaths();
    final String managerHash = PackageManifest.sha256(this.root.resolve("definitive-manager.jar"));
    final String bootstrapHash = PackageManifest.sha256(this.root.resolve("bootstrap-java"));
    // The router can be newer than the active engine after rollback. Require a
    // matching pair from an installed package's platform-valid closed inventory.
    try(final var releases = Files.list(this.root.resolve("releases"))) {
      for(final Path candidate : releases.toList()) {
        if(Files.isSymbolicLink(candidate) || !candidate.getFileName().toString().matches("alpha-[a-f0-9]{16}")) continue;
        final PackageManifest reference;
        try { reference = PackageManifest.read(candidate); } catch(final IOException ignored) { continue; }
        if(reference.platform().equals(PackageManifest.hostPlatform()) && candidate.getFileName().toString().equals(reference.id())
          && PackageManifest.identity(reference.metadata(), reference.hashes()).equals(reference.id())
          && managerHash.equals(reference.hashes().getProperty("definitive-manager.jar"))
          && bootstrapHash.equals(reference.hashes().getProperty("bootstrap-java"))) { reference.verifyManagerDependencies(candidate); return candidate; }
      }
    }
    throw new IOException("Launcher files are damaged or unrecognized. Retry installation to repair them.");
  }

  public String rollback() throws IOException {
    try(final var operation = this.operation()) {
      final Properties current = this.state();
      final String version = current.getProperty("previousVersion", "");
      final String snapshot = current.getProperty("previousSnapshot", "");
      if(version.isEmpty() || snapshot.isEmpty()) throw new IOException("There is no previous update to restore.");
      final Path release = child(this.root.resolve("releases"), version, "alpha-[a-f0-9]{16}");
      PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
      final Path oldData = child(this.root.resolve("snapshots"), snapshot, "snapshot-[a-f0-9-]{36}");
      verifyInventory(oldData);
      final String dataId = "data-" + UUID.randomUUID();
      copyTree(oldData, this.root.resolve("data").resolve(dataId));
      Files.delete(this.root.resolve("data").resolve(dataId).resolve("snapshot-files.properties"));
      current.setProperty("retainedNewerVersion", current.getProperty("version"));
      current.setProperty("retainedNewerData", current.getProperty("data"));
      current.setProperty("version", version);
      current.setProperty("data", dataId);
      current.setProperty("artwork", current.getProperty("previousArtwork", "hd"));
      current.setProperty("releaseAssetId", current.getProperty("previousReleaseAssetId", ""));
      current.remove("previousReleaseAssetId");
      current.remove("previousArtwork");
      current.setProperty("legacyTextures", current.getProperty("previousLegacyTextures", "false"));
      current.remove("previousLegacyTextures");
      current.setProperty("fullscreen", current.getProperty("previousFullscreen", "true"));
      current.remove("previousFullscreen");
      current.setProperty("releasePublishedAt", current.getProperty("previousReleasePublishedAt", ""));
      current.remove("previousReleasePublishedAt");
      forceTree(this.root.resolve("data").resolve(dataId));
      current.remove("previousVersion");
      current.remove("previousSnapshot");
      atomicProperties(this.root.resolve("state.properties"), current);
      return "Restored the previous engine with pre-update saves/settings. Newer data is retained separately.";
    }
  }

  public boolean hasRestorableVersion() {
    try {
      final Properties state = this.state();
      final Path release = child(this.root.resolve("releases"), state.getProperty("previousVersion", ""), "alpha-[a-f0-9]{16}");
      final Path snapshot = child(this.root.resolve("snapshots"), state.getProperty("previousSnapshot", ""), "snapshot-[a-f0-9-]{36}");
      PackageManifest.read(release).verify(release, PackageManifest.hostPlatform()); verifyInventory(snapshot); return true;
    } catch(final IOException | RuntimeException failure) { return false; }
  }

  public Path preparationLog() throws IOException { return this.gameLog().resolveSibling("preparation.log"); }

  public void acknowledgeRecovery() throws IOException {
    try(final var operation = this.operation()) {
      final Properties state = this.state();
      if(Boolean.parseBoolean(state.getProperty("recoveryPending", "false"))) {
        state.setProperty("recoveryPending", "false");
        atomicProperties(this.root.resolve("state.properties"), state);
      }
    }
  }

  public String prepareInstalledDiscs(final boolean acceptChanged, final InstallProgress progress) throws IOException, InterruptedException {
    try(final var operation = this.operation()) {
      final DiscImporter.Existing existing = DiscImporter.existing(this, progress);
      if(!existing.usable()) throw new IOException(existing.detail());
      if(existing.changed() && !acceptChanged) throw new DiscImporter.DifferentDiscs("Installed disc contents changed. Confirm these images before preparing them.");
      if(existing.changed()) this.invalidatePreparedDiscsLocked();
      if(!this.discsPrepared()) return this.prepareDiscs(progress);
      final Path receipt = this.root.resolve("isos/disc-checksums.properties");
      if(!Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) atomicProperties(receipt, DiscImporter.currentHashes(this));
      return "Existing game files are ready.";
    }
  }

  /** Space for a fully staged package, a snapshot and next data generation. */
  public long requiredInstallBytes(final Path source) throws IOException {
    long payload = 0;
    String candidateId = "";
    if(Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) { payload = treeBytes(source); candidateId = PackageManifest.read(source).id(); }
    else try(final var zip = new java.util.zip.ZipFile(source.toFile())) {
      final var metadataEntry = zip.getEntry(PackageManifest.METADATA);
      if(metadataEntry != null && metadataEntry.getSize() >= 0 && metadataEntry.getSize() <= 65536) {
        final Properties metadata = new Properties(); try(final var input = zip.getInputStream(metadataEntry)) { metadata.load(input); } candidateId = metadata.getProperty("id", "");
      }
      for(final var entries = zip.entries(); entries.hasMoreElements();) {
        final var entry = entries.nextElement(); if(entry.isDirectory()) continue;
        if(entry.getSize() < 0) throw new IOException("Package expanded size is unknown.");
        payload = addBytes(payload, entry.getSize()); if(payload > MAX_ARCHIVE_BYTES) throw new IOException("Archive exceeds the package size limit.");
      }
    }
    final Properties state = this.state();
    final long privateBytes = state.containsKey("data") ? treeBytes(this.data(state)) : 0;
    final long preparation = candidateId.equals(state.getProperty("version", "")) && this.discsPrepared() ? 0 : PREPARATION_RESERVE_BYTES;
    return addBytes(addBytes(addBytes(payload, addBytes(privateBytes, privateBytes)), preparation), 1024L * 1024 * 1024);
  }
  public void requireInstallSpace(final Path source) throws IOException {
    final long required = this.requiredInstallBytes(source);
    if(Files.getFileStore(this.root).getUsableSpace() < required) throw new IOException("Installation needs " + String.format(java.util.Locale.ROOT, "%.1f GB", required / 1073741824.0) + " free, including retained saves, custom artwork and recovery copies.");
  }
  private static long addBytes(final long one, final long two) throws IOException {
    try { return Math.addExact(one, two); } catch(final ArithmeticException failure) { throw new IOException("Installation size exceeds supported limits.", failure); }
  }
  private static long treeBytes(final Path root) throws IOException {
    if(!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Installation directory is missing or linked: " + root);
    long bytes = 0;
    try(final var paths = Files.walk(root)) {
      for(final Path path : paths.toList()) {
        if(Files.isSymbolicLink(path)) throw new IOException("Linked private data cannot be copied or pruned.");
        if(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) bytes = addBytes(bytes, Files.size(path));
        else if(!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported installation entry: " + path);
      }
    }
    return bytes;
  }

  public record RetainedItem(String path, long bytes, boolean removable, String reason) { }
  public record RetentionInventory(java.util.List<RetainedItem> items, boolean complete, String detail) { }

  /** Private data and snapshots remain protected even when no current state points at them. */
  public RetentionInventory retentionInventory() throws IOException {
    try(final var operation = this.operation()) { return this.retentionInventoryLocked(); }
  }
  private RetentionInventory retentionInventoryLocked() throws IOException {
    final var protectedReleases = new java.util.HashSet<String>();
    final var protectedWorkspaces = new java.util.HashSet<String>();
    boolean complete = true; final var items = new ArrayList<RetainedItem>();
    for(final String name : new String[]{"state.properties", ".state-last-good.properties"}) {
      final Path stateFile = this.root.resolve(name);
      if(!Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS)) continue;
      final Properties state;
      try { state = readState(stateFile); }
      catch(final IOException failure) { complete = false; continue; }
      for(final String key : new String[]{"version", "previousVersion", "retainedNewerVersion"}) if(!state.getProperty(key, "").isEmpty()) protectedReleases.add(state.getProperty(key));
      if(state.containsKey("version") && state.containsKey("data")) protectedWorkspaces.add(state.getProperty("version") + '-' + state.getProperty("data"));
      if(state.containsKey("retainedNewerVersion") && state.containsKey("retainedNewerData")) protectedWorkspaces.add(state.getProperty("retainedNewerVersion") + '-' + state.getProperty("retainedNewerData"));
    }
    // A retained root router is itself a live reference to its source package.
    final String manager = Files.isRegularFile(this.root.resolve("definitive-manager.jar"), LinkOption.NOFOLLOW_LINKS) ? PackageManifest.sha256(this.root.resolve("definitive-manager.jar")) : "";
    final String bootstrap = Files.isRegularFile(this.root.resolve("bootstrap-java"), LinkOption.NOFOLLOW_LINKS) ? PackageManifest.sha256(this.root.resolve("bootstrap-java")) : "";
    for(final String parent : new String[]{"releases", "data", "snapshots", "workspaces"}) {
      try(final var children = Files.list(this.root.resolve(parent))) {
        for(final Path path : children.toList()) {
          final String id = path.getFileName().toString(); final String relative = parent + '/' + id;
          boolean removable = false; String reason;
          if(parent.equals("data") || parent.equals("snapshots")) reason = "Private saves, settings, mods and snapshots are always retained.";
          else if(parent.equals("releases")) {
            try {
              if(!id.matches("alpha-[a-f0-9]{16}")) throw new IOException("Unknown release directory.");
              final PackageManifest manifest = PackageManifest.read(path); manifest.verify(path, PackageManifest.hostPlatform());
              if(!manifest.id().equals(id)) throw new IOException("Release directory identity mismatch.");
              if(manager.equals(manifest.hashes().getProperty("definitive-manager.jar")) && bootstrap.equals(manifest.hashes().getProperty("bootstrap-java"))) protectedReleases.add(id);
              removable = !protectedReleases.contains(id); reason = removable ? "Inactive verified engine; no state or router references it." : "Active, restorable, retained-newer or last-good engine/router.";
            } catch(final IOException failure) { complete = false; reason = "Unresolved release ownership: " + failure.getMessage(); }
          } else {
            if(!id.matches("alpha-[a-f0-9]{16}-data-[a-f0-9-]{36}") || Files.isSymbolicLink(path)) { complete = false; reason = "Unresolved workspace ownership."; }
            else { removable = !protectedWorkspaces.contains(id) && !protectedReleases.contains(id.substring(0, 22)); reason = removable ? "Inactive generated extraction; private data stays retained." : "Workspace belongs to a protected engine or data generation."; }
          }
          long size = 0;
          try { size = parent.equals("workspaces") ? workspaceBytes(path) : treeBytes(path); }
          catch(final IOException failure) { complete = false; removable = false; reason = "Incomplete discovery: " + failure.getMessage(); }
          items.add(new RetainedItem(relative, size, removable, reason));
        }
      }
    }
    // Uninstall covers recovery directories. Pruning intentionally retains them:
    // an abandoned transaction or damaged state may still need their contents.
    if(!complete) {
      for(int i = 0; i < items.size(); i++) { final var item = items.get(i); items.set(i, new RetainedItem(item.path(), item.bytes(), false, item.removable() ? "Pruning blocked by incomplete ownership discovery." : item.reason())); }
    }
    return new RetentionInventory(java.util.List.copyOf(items), complete, complete ? "Ownership and active references verified. Private data is protected." : "Pruning stopped: ownership discovery is incomplete.");
  }
  private static long workspaceBytes(final Path root) throws IOException {
    if(!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid workspace directory.");
    long bytes = 0;
    try(final var paths = Files.walk(root)) {
      for(final Path path : paths.toList()) if(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) bytes = addBytes(bytes, Files.size(path));
    }
    return bytes;
  }
  public String pruneRetained(final java.util.List<String> selected, final InstallProgress progress) throws IOException {
    try(final var operation = this.operation()) {
      final RetentionInventory inventory = this.retentionInventoryLocked();
      if(!inventory.complete()) throw new IOException(inventory.detail());
      final var targets = new ArrayList<Path>(); final var seen = new java.util.HashSet<String>();
      for(final String name : selected) {
        if(!seen.add(name)) continue;
        final RetainedItem item = inventory.items().stream().filter(entry -> entry.path().equals(name)).findFirst().orElseThrow(() -> new IOException("Unknown retained item. No files were removed."));
        if(!item.removable()) throw new IOException("Protected retained item: " + name + ". No files were removed.");
        targets.add(this.root.resolve(name));
      }
      int count = 0;
      for(final Path target : targets) { progress.phase("Removing inactive game files", this.root.relativize(target).toString(), ++count * 100 / Math.max(1, targets.size())); deleteOwnedTree(target); forceDirectory(target.getParent()); }
      return "Removed " + count + " inactive game items. All private data and snapshots were retained.";
    }
  }

  Path data(final Properties state) throws IOException { return child(this.root.resolve("data"), state.getProperty("data", ""), "data-[a-f0-9-]{36}"); }

  static Path child(final Path parent, final String name, final String pattern) throws IOException {
    if(!name.matches(pattern)) throw new IOException("Invalid installation state. No files changed.");
    final Path path = parent.resolve(name);
    if(Files.isSymbolicLink(path)) throw new IOException("Unexpected linked data path.");
    return path;
  }

  public void setPreferences(final boolean hd, final boolean legacyTextures, final boolean fullscreen) throws IOException {
    try(final var operation = this.operation()) {
      final Properties state = this.state();
      if(!state.containsKey("version")) throw new IOException("Install a package first.");
      state.setProperty("artwork", hd ? "hd" : "original");
      state.setProperty("legacyTextures", Boolean.toString(legacyTextures));
      state.setProperty("fullscreen", Boolean.toString(fullscreen));
      atomicProperties(this.root.resolve("state.properties"), state);
    }
  }
  public void setArtwork(final boolean hd) throws IOException {
    try(final var operation = this.operation()) { final var state = this.state(); this.setPreferences(hd, Boolean.parseBoolean(state.getProperty("legacyTextures", "false")), Boolean.parseBoolean(state.getProperty("fullscreen", "true"))); }
  }

  public Path prepareLaunch() throws IOException {
    try(final var operation = this.operation()) {
      final Properties state = this.state();
      final Path release = child(this.root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
      final PackageManifest manifest = PackageManifest.read(release); manifest.verify(release, PackageManifest.hostPlatform());
      return this.prepareLaunch(state, release, manifest);
    }
  }
  private Path prepareLaunch(final Properties state, final Path release, final PackageManifest manifest) throws IOException {
    final Path data = this.data(state);
    if(PackageManifest.hostPlatform().equals("linux-x64")) configureFullscreen(data, Boolean.parseBoolean(state.getProperty("fullscreen", "true")));
    try(final var entries = Files.walk(data)) {
      if(entries.anyMatch(Files::isSymbolicLink)) throw new IOException("Linked private data is not supported in a managed installation.");
    }
    final Path workspace = this.root.resolve("workspaces").resolve(manifest.id() + '-' + data.getFileName());
    if(Files.isSymbolicLink(workspace)) throw new IOException("Unexpected linked workspace.");
    Files.createDirectories(workspace);
    for(final String name : new String[]{"files", "mods"}) if(Files.isSymbolicLink(workspace.resolve(name))) throw new IOException("Unexpected linked workspace directory: " + name);
    Files.createDirectories(workspace.resolve("files"));
    try(final var extracted = Files.walk(workspace.resolve("files"))) { if(extracted.anyMatch(Files::isSymbolicLink)) throw new IOException("Unexpected linked extracted game data."); }
    for(final String support : new String[]{"gfx", "patches", "lang", "libs"}) link(workspace.resolve(support), release.resolve(support));
    managedLogging(workspace.resolve("log4j2.xml"), release.resolve("log4j2.xml"));
    link(workspace.resolve("gamecontrollerdb.txt"), release.resolve("gamecontrollerdb.txt"));
    link(workspace.resolve("isos"), this.root.resolve("isos"));
    for(final String privateName : new String[]{"saves", "config", "texture-packs", "config.conf", "config.dcnf"}) link(workspace.resolve(privateName), data.resolve(privateName));
    final Path mods = workspace.resolve("mods");
    Files.createDirectories(mods);
    // Workspace-owned links only; never remove or modify user mod files.
    try(final var entries = Files.list(mods)) {
      for(final Path entry : entries.toList()) {
        if(!Files.isSymbolicLink(entry)) throw new IOException("Unexpected file in managed workspace mods: " + entry);
        Files.delete(entry);
      }
    }
    final boolean bundledModels = Files.isDirectory(release.resolve("bundled-mods")) && hasBundledModels(release.resolve("bundled-mods"));
    if(Files.isDirectory(data.resolve("mods"))) try(final var files = Files.list(data.resolve("mods"))) {
      for(final Path mod : files.toList()) if(mod.toString().endsWith(".jar")) {
        // A manual copy of our older artifact must not duplicate the bundled mod ID.
        // Retain the actual file in user data; only rebuild workspace-owned links.
        if(bundledModels && isModelsHdArtifact(mod)) {
          InstallerLog.write("Using the release's ModelsHD version; manual copy retained at " + mod);
          continue;
        }
        link(mods.resolve(mod.getFileName()), mod);
      }
    }
    try(final var files = Files.list(release.resolve("bundled-mods"))) {
      for(final Path mod : files.toList()) {
        if("original".equals(state.getProperty("artwork", "hd")) && !mod.getFileName().toString().matches("FMVHD-v[0-9.]+\\.jar")) continue;
        if(Files.exists(mods.resolve(mod.getFileName()), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Duplicate bundled artwork mod. Remove its custom copy before playing.");
        link(mods.resolve(mod.getFileName()), mod);
      }
    }
    return workspace;
  }

  /** Only the current manager's console configuration may run in a managed workspace. */
  private static void managedLogging(final Path target, final Path previousManagedSource) throws IOException {
    final byte[] trusted;
    try(final var input = InstallStore.class.getResourceAsStream("managed-log4j2.xml")) {
      if(input == null) throw new IOException("The managed diagnostics configuration is unavailable.");
      trusted = input.readAllBytes();
    }
    if(Files.isSymbolicLink(target)) {
      if(!Files.readSymbolicLink(target).equals(previousManagedSource)) throw new IOException("Unexpected linked logging configuration. No external files were changed.");
    } else if(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      if(!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != trusted.length) throw new IOException("Unexpected custom logging configuration. Preserve it outside the managed workspace before continuing.");
      final byte[] existing;
      try(final var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) { existing = input.readAllBytes(); }
      if(!java.util.Arrays.equals(existing, trusted)) throw new IOException("Unexpected custom logging configuration. Preserve it outside the managed workspace before continuing.");
      return;
    }
    final Path temporary = Files.createTempFile(target.getParent(), ".managed-logging-", ".tmp");
    try {
      Files.write(temporary, trusted); forceFile(temporary);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      forceDirectory(target.getParent());
    } finally { Files.deleteIfExists(temporary); }
  }

  private static boolean isModelsHdArtifact(final Path file) throws IOException {
    // Keep compatibility with the historical filename and non-ZIP test fixtures.
    if(file.getFileName().toString().matches("ModelsHD-[0-9]+\\.[0-9]+\\.[0-9]+\\.jar")) return true;
    if(!file.getFileName().toString().endsWith(".jar")) return false;
    // Official versions all declare modelshd through this exact class. Inspect
    // directory names only: never load a user's classes or inflate asset payloads.
    try(final var input = new java.io.RandomAccessFile(file.toFile(), "r")) {
      final long size = input.length();
      if(size < 22) return false;
      final byte[] tail = new byte[(int)Math.min(size, 65557)];
      input.seek(size - tail.length); input.readFully(tail);
      final var end = java.nio.ByteBuffer.wrap(tail).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      int position = tail.length - 22;
      while(position >= 0 && (end.getInt(position) != 0x06054b50 || position + 22 + Short.toUnsignedInt(end.getShort(position + 20)) != tail.length)) position--;
      if(position < 0) {
        input.seek(0);
        if(input.readInt() == 0x504b0304) throw new IOException("Custom mod has an incomplete ZIP directory: " + file.getFileName());
        return false;
      }
      if(end.getShort(position + 4) != 0 || end.getShort(position + 6) != 0 || end.getShort(position + 8) != end.getShort(position + 10)) throw new IOException("Split custom mod archives are unsupported.");
      long count = Short.toUnsignedInt(end.getShort(position + 10));
      long length = Integer.toUnsignedLong(end.getInt(position + 12)), offset = Integer.toUnsignedLong(end.getInt(position + 16));
      final long endOffset = size - tail.length + position;
      if(count == 65535 || length == 0xffffffffL || offset == 0xffffffffL) {
        // ZIP64 permits asset-heavy mods without a blanket archive-size limit.
        if(endOffset < 20) throw new IOException("Custom mod ZIP64 directory is missing.");
        final byte[] locatorBytes = new byte[20]; input.seek(endOffset - 20); input.readFully(locatorBytes);
        final var locator = java.nio.ByteBuffer.wrap(locatorBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        final long recordOffset = locator.getLong(8);
        if(locator.getInt(0) != 0x07064b50 || locator.getInt(4) != 0 || locator.getInt(16) != 1 || recordOffset < 0 || recordOffset > endOffset - 76) throw new IOException("Invalid custom mod ZIP64 directory.");
        final byte[] recordBytes = new byte[56]; input.seek(recordOffset); input.readFully(recordBytes);
        final var record = java.nio.ByteBuffer.wrap(recordBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(record.getInt(0) != 0x06064b50 || record.getLong(4) < 44 || record.getInt(16) != 0 || record.getInt(20) != 0 || record.getLong(24) != record.getLong(32)) throw new IOException("Invalid custom mod ZIP64 directory.");
        count = record.getLong(32); length = record.getLong(40); offset = record.getLong(48);
      }
      if(count < 0 || count > 100000 || length < 0 || length > 32L * 1024 * 1024 || offset < 0 || offset > endOffset || length > endOffset - offset) throw new IOException("Custom mod identity directory exceeds safe inspection bounds.");
      final long limit = offset + length;
      final byte[] headerBytes = new byte[46];
      final byte[] marker = "modelshd/ModelsHdMod.class".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      boolean found = false;
      for(long entry = 0; entry < count; entry++) {
        if(offset > limit - 46) throw new IOException("Truncated custom mod identity directory.");
        input.seek(offset); input.readFully(headerBytes);
        final var header = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(header.getInt(0) != 0x02014b50 || header.getShort(34) != 0) throw new IOException("Invalid custom mod identity directory.");
        final int nameLength = Short.toUnsignedInt(header.getShort(28));
        final long next = offset + 46 + nameLength + Short.toUnsignedInt(header.getShort(30)) + Short.toUnsignedInt(header.getShort(32));
        if(next > limit) throw new IOException("Truncated custom mod identity entry.");
        if(nameLength == marker.length) {
          final byte[] name = new byte[marker.length]; input.readFully(name);
          if(java.util.Arrays.equals(marker, name)) {
            if(found) throw new IOException("Ambiguous duplicate ModelsHD identity in " + file.getFileName());
            found = true;
          }
        }
        offset = next;
      }
      if(offset != limit) {
        // The optional central-directory signature is metadata, not a payload.
        if(limit - offset < 6) throw new IOException("Unexpected custom mod directory metadata.");
        final byte[] signatureBytes = new byte[6]; input.seek(offset); input.readFully(signatureBytes);
        final var signature = java.nio.ByteBuffer.wrap(signatureBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(signature.getInt(0) != 0x05054b50 || 6L + Short.toUnsignedInt(signature.getShort(4)) != limit - offset) throw new IOException("Unexpected custom mod directory metadata.");
      }
      return found;
    }
  }

  private static boolean hasBundledModels(final Path directory) throws IOException {
    try(final var files = Files.list(directory)) {
      for(final Path file : files.toList()) if(isModelsHdArtifact(file)) return true;
      return false;
    }
  }

  static void configureFullscreen(final Path data, final boolean fullscreen) throws IOException {
    final Path config = data.resolve("config.dcnf");
    if(Files.isSymbolicLink(config)) throw new IOException("Unexpected linked game configuration.");
    final byte[] id = "lod_core:fullscreen".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    final byte[] original = Files.exists(config) ? Files.readAllBytes(config) : new byte[4];
    if(original.length < 4 || original.length > 100 * 1024) throw new IOException("Game settings need attention. Fullscreen settings were not changed.");
    final var input = java.nio.ByteBuffer.wrap(original).order(java.nio.ByteOrder.LITTLE_ENDIAN); final int count = input.getInt();
    if(count < 0 || count > 1000) throw new IOException("Invalid game settings. No settings were changed.");
    int valueOffset = -1;
    try {
      for(int i = 0; i < count; i++) {
        final int length = Byte.toUnsignedInt(input.get()) | Byte.toUnsignedInt(input.get()) << 8 | Byte.toUnsignedInt(input.get()) << 16;
        if(length > input.remaining()) throw new IOException("Truncated game settings."); final byte[] name = new byte[length]; input.get(name);
        final int size = input.getInt(); if(size < 0 || size > input.remaining()) throw new IOException("Invalid game setting length.");
        if(java.util.Arrays.equals(name, id)) { if(size != 1) throw new IOException("Invalid fullscreen setting."); valueOffset = input.position(); }
        input.position(input.position() + size);
      }
    } catch(final java.nio.BufferUnderflowException | IllegalArgumentException failure) { throw new IOException("Truncated game settings. No settings were changed.", failure); }
    if(input.hasRemaining()) throw new IOException("Unexpected game settings data. No settings were changed.");
    final byte value = (byte)(fullscreen ? 1 : 0); final byte[] updated;
    if(valueOffset >= 0) { if(original[valueOffset] == value) return; updated = original.clone(); updated[valueOffset] = value; }
    else {
      final var output = java.nio.ByteBuffer.allocate(original.length + 3 + id.length + 5).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      output.put(original); output.putInt(0, count + 1); output.put((byte)id.length).put((byte)0).put((byte)0).put(id).putInt(1).put(value); updated = output.array();
    }
    final Path backup = data.resolve("config.dcnf.before-fullscreen");
    if(Files.isSymbolicLink(backup)) throw new IOException("Unexpected linked settings backup.");
    if(Files.exists(config) && !Files.exists(backup)) Files.write(backup, original, StandardOpenOption.CREATE_NEW);
    final Path pending = Files.createTempFile(data, ".fullscreen-", ".tmp");
    try { Files.write(pending, updated); replaceFile(pending, config); } finally { Files.deleteIfExists(pending); }
  }

  public void setFullscreen(final boolean enabled) throws IOException {
    try(final var operation = this.operation()) { final var state = this.state(); this.setPreferences(!"original".equals(state.getProperty("artwork", "hd")), Boolean.parseBoolean(state.getProperty("legacyTextures", "false")), enabled); }
  }

  public String uninstall(final boolean deleteIsos, final InstallProgress progress) throws IOException {
    try(final var operation = this.operation()) {
      final Properties state = this.state(); this.data(state); // Requires a recognized data generation, even for a damaged install.
      final var targets = new ArrayList<Path>();
      try(final var releases = Files.list(this.root.resolve("releases"))) {
        for(final Path release : releases.toList()) {
          if(!release.getFileName().toString().matches("alpha-[a-f0-9]{16}") || Files.isSymbolicLink(release)) throw new IOException("Unexpected release folder. Uninstall stopped before changing files.");
          targets.add(release);
        }
      }
      try(final var workspaces = Files.list(this.root.resolve("workspaces"))) {
        for(final Path workspace : workspaces.toList()) {
          if(!workspace.getFileName().toString().matches("alpha-[a-f0-9]{16}-data-[a-f0-9-]{36}") || Files.isSymbolicLink(workspace)) throw new IOException("Unexpected workspace folder. Uninstall stopped before changing files.");
          targets.add(workspace);
        }
      }
      for(final String name : new String[]{"Play Game.sh", "Manage Installation.sh", "definitive-manager.jar", "bootstrap-java", "steam-artwork"}) {
        final Path target = this.root.resolve(name); if(Files.isSymbolicLink(target)) throw new IOException("Unexpected linked installation file. No files were removed."); if(Files.exists(target)) targets.add(target);
      }
      if(deleteIsos) targets.add(this.root.resolve("isos"));
      try(final var paths = Files.list(this.root)) {
        for(final Path path : paths.toList()) if(path.getFileName().toString().matches("(?:release-recovery|disc-extraction-recovery" + (deleteIsos ? "|disc-recovery" : "") + ")-[a-f0-9-]{36}")) {
          if(Files.isSymbolicLink(path)) throw new IOException("Unexpected linked recovery folder. No files were removed."); targets.add(path);
        }
      }
      // Recoverable state is published before removal so an interrupted uninstall offers Reinstall.
      state.setProperty("uninstalled", "true"); atomicProperties(this.root.resolve("state.properties"), state);
      int number = 0;
      for(final Path target : targets) { progress.phase("Removing Definitive", target.getFileName().toString(), 10 + ++number * 85 / Math.max(1, targets.size())); deleteOwnedTree(target); }
      Files.createDirectories(this.root.resolve("isos"));
      progress.phase("Uninstalled", "Saves, settings and custom mods are retained" + (deleteIsos ? ". Selected ISO removal completed." : ". Disc images are retained."), 100);
      return "Definitive was uninstalled. Your saves, settings and custom mods are retained at " + this.root.resolve("data");
    }
  }

  public String prepareDiscs() throws IOException, InterruptedException {
    return this.prepareDiscs(InstallProgress.NONE);
  }
  public String prepareDiscs(final InstallProgress progress) throws IOException, InterruptedException {
    try(final var operation = this.operation()) {
      if(!this.discsPrepared()) this.requirePreparationSpace();
      progress.phase("Verifying four discs", "Checking the US disc headers", 50);
      DiscImporter.validateSet(this.root.resolve("isos"));
      final Properties state = this.state();
      if(Boolean.parseBoolean(state.getProperty("recoveryPending", "false"))) throw new IOException("Installation state was recovered. Review the preserved data and explicitly confirm the verified recovered state in Manage before playing.");
      final Path release = child(this.root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
      final PackageManifest manifest = PackageManifest.read(release); manifest.verify(release, PackageManifest.hostPlatform());
      final Path workspace = this.prepareLaunch(state, release, manifest);
      final String gameJar = manifest.metadata().getProperty("gameJar");
      final Path log = workspace.resolve("preparation.log");
      InstallerLog.write("Disc preparation log: " + log);
      progress.phase("Preparing game files", "Reading all four discs and extracting game assets", 60);
      final var command = java.util.List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx2G", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp", release.resolve(gameJar) + java.io.File.pathSeparator + release.resolve("libs/*"), "legend.definitive.tools.PrepareDiscs");
      final Properties expectedDiscs = DiscImporter.currentHashes(this);
      try(final var running = ProcessRunner.start(new ProcessBuilder(command).directory(workspace.toFile()), log, false)) {
        final int code = running.await(java.time.Duration.ofMinutes(20), () -> {
          final String line = running.lastLine();
          if(line.startsWith("DEFINITIVE_STATUS\t") && line.length() > 18) {
            final String last = line.substring(18);
            final String phase = last.startsWith("Writing") ? "Writing game files" : last.startsWith("Transforming") ? "Converting game assets" : "Reading your discs";
            progress.phase(phase, last.substring(0, Math.min(220, last.length())), last.startsWith("Writing") ? 85 : last.startsWith("Transforming") ? 65 : 60);
          }
        });
        if(code != 0) throw new IOException("Disc preparation could not finish. Your images are retained; retry with Use installed discs. Details: " + log);
        if(!this.discsPrepared()) throw new IOException("Disc preparation did not create its completion marker. Details: " + log);
        if(!expectedDiscs.equals(DiscImporter.currentHashes(this))) {
          this.invalidatePreparedDiscsLocked(); throw new IOException("Disc images changed during preparation. Prepared data was retained separately; retry with stable images.");
        }
        forceTree(workspace.resolve("files"));
        atomicProperties(this.root.resolve("isos/disc-checksums.properties"), expectedDiscs);
        progress.phase("Game files ready", "All four discs are prepared. Launcher: " + this.root.resolve("Play Game.sh"), 100);
        return "Your discs are prepared. Choose whether to add Definitive to Steam.";
      }
    }
  }
  void requirePreparationSpace() throws IOException {
    if(Files.getFileStore(this.root).getUsableSpace() < PREPARATION_RESERVE_BYTES + 1024L * 1024 * 1024) throw new IOException("Disc preparation needs at least 7 GB free for extracted game files and recovery reserve. Free space or choose another installation drive.");
  }

  public boolean discsPrepared() throws IOException {
    final Properties state = this.state();
    final String version = state.getProperty("version", ""), data = state.getProperty("data", "");
    child(this.root.resolve("releases"), version, "alpha-[a-f0-9]{16}");
    this.data(state);
    final Path marker = this.root.resolve("workspaces").resolve(version + "-" + data).resolve("files/version");
    for(Path path = marker; path != null && path.startsWith(this.root); path = path.getParent()) if(Files.isSymbolicLink(path)) throw new IOException("Unexpected linked prepared game data.");
    return Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS);
  }

  void invalidatePreparedDiscs() throws IOException {
    try(final var operation = this.operation()) {
      this.invalidatePreparedDiscsLocked();
    }
  }
  void invalidatePreparedDiscsLocked() throws IOException {
      if(!this.state().containsKey("version")) return;
      final Path workspace = this.prepareLaunch();
      final Path extracted = workspace.resolve("files");
      if(Files.isSymbolicLink(extracted)) throw new IOException("Unexpected linked game files.");
      // Retain the entire previous extraction; a new unpack must not reuse mixed disc contents.
      Files.move(extracted, this.root.resolve("disc-extraction-recovery-" + UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
      Files.createDirectory(extracted);
  }

  public void setLegacyTextures(final boolean enabled) throws IOException {
    try(final var operation = this.operation()) { final var state = this.state(); this.setPreferences(!"original".equals(state.getProperty("artwork", "hd")), enabled, Boolean.parseBoolean(state.getProperty("fullscreen", "true"))); }
  }

  public int play() throws IOException, InterruptedException {
    try(final var operation = this.operation()) {
      final Properties state = this.state();
      if(Boolean.parseBoolean(state.getProperty("recoveryPending", "false"))) throw new IOException("Installation state was recovered. Review the preserved data and explicitly confirm the verified recovered state in Manage before playing.");
      DiscImporter.validateSet(this.root.resolve("isos"));
      final Path release = child(this.root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
      final PackageManifest manifest = PackageManifest.read(release); manifest.verify(release, PackageManifest.hostPlatform());
      final Path workspace = this.prepareLaunch(state, release, manifest);
      final String gameJar = manifest.metadata().getProperty("gameJar");
      final var command = new ArrayList<String>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      if(PackageManifest.hostPlatform().startsWith("macos")) command.add("-XstartOnFirstThread");
      command.add("-Ddefinitive.legacyTextures=" + Boolean.parseBoolean(state.getProperty("legacyTextures", "false")));
      final Path log = workspace.resolve("launcher.log");
      DiagnosticLogs.checked(log);
      this.verifiedRouterRelease();
      final String token = GameLease.begin(this.root);
      try {
        command.add("-Ddefinitive.installRoot=" + this.root);
        command.add("-Ddefinitive.launchToken=" + token);
        command.addAll(java.util.List.of("-ea", "-Xmx2G", "-Ddefinitive.managedInstall=true", "-Djoml.fastmath", "-Djoml.sinLookup", "-Djoml.useMathFma", "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", "-cp", this.root.resolve("definitive-manager.jar") + java.io.File.pathSeparator + release.resolve(gameJar) + java.io.File.pathSeparator + release.resolve("libs/*"), ManagedGameMain.class.getName()));
        InstallerLog.write("Starting game; details: " + log);
        try(final var output = DiagnosticLogs.openLog(log)) { output.write(("\nDefinitive game launch: " + java.time.Instant.now() + "\nJava: " + Runtime.version() + "\nWorkspace: " + workspace + "\nGame entry: legend.game.Main\nPlatform: " + PackageManifest.hostPlatform() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        final var builder = new ProcessBuilder(command).directory(workspace.toFile()); normalizeSteamOverlay(builder.environment());
        try(final var running = ProcessRunner.start(builder, log)) { return running.await(); }
      } finally { GameLease.finished(this.root, token); }
    }
  }

  static void normalizeSteamOverlay(final java.util.Map<String, String> environment) {
    final String preload = environment.get("LD_PRELOAD"); if(preload == null) return;
    final var retained = new ArrayList<String>();
    for(final String entry : preload.split("[ :]+")) {
      if(entry.isEmpty()) continue;
      if(entry.endsWith("/ubuntu12_32/gameoverlayrenderer.so")) {
        final String compatible = entry.replace("/ubuntu12_32/", "/ubuntu12_64/");
        if(Files.isRegularFile(Path.of(compatible))) retained.add(compatible);
      } else retained.add(entry);
    }
    if(retained.isEmpty()) environment.remove("LD_PRELOAD"); else environment.put("LD_PRELOAD", String.join(":", retained));
  }

  public Path gameLog() throws IOException {
    final var state = this.state();
    final String version = state.getProperty("version", ""), data = state.getProperty("data", "");
    child(this.root.resolve("releases"), version, "alpha-[a-f0-9]{16}"); this.data(state);
    return this.root.resolve("workspaces").resolve(version + '-' + data).resolve("launcher.log");
  }

  private static void link(final Path link, final Path target) throws IOException {
    if(Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
      if(!Files.isSymbolicLink(link) || !Files.readSymbolicLink(link).equals(target)) throw new IOException("Unexpected workspace path: " + link);
    } else Files.createSymbolicLink(link, target.toAbsolutePath());
  }

  private void writeLaunchers(final Path release) throws IOException {
    final Path runtimePath = this.root.resolve(".java-path");
    // The JVM performing the verified update is already usable on this host.
    // Refresh the receipt so a fresh portable setup migrates an older cache to
    // its pinned runtime; an offline update retains its working Java 25 path.
    if(Files.isSymbolicLink(runtimePath) || Files.exists(runtimePath, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(runtimePath, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected runtime receipt. No launcher files were changed.");
    final Path runtimeReceipt = Files.createTempFile(this.root, ".java-path-", ".tmp");
    try {
      Files.writeString(runtimeReceipt, Path.of(System.getProperty("java.home"), "bin", "java") + "\n");
      try(final var channel = FileChannel.open(runtimeReceipt, StandardOpenOption.WRITE)) { channel.force(true); }
      Files.move(runtimeReceipt, runtimePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); forceDirectory(this.root);
    } finally { Files.deleteIfExists(runtimeReceipt); }
    // Managed routers are repaired from the verified package. Format-1 routers must
    // continue routing every retained format-1 version, including after rollback.
    this.repairBootstrap(release);
    this.root.resolve("bootstrap-java").toFile().setExecutable(true, true);
    final String previousScript = "#!/bin/bash\nset -euo pipefail\nROOT=\"$(cd -- \"$(dirname -- \"${BASH_SOURCE[0]}\")\" && pwd)\"\nJAVA=\"$(\"$ROOT/bootstrap-java\" \"$ROOT\")\"\nexec \"$JAVA\" -jar \"$ROOT/definitive-manager.jar\" --manage \"$ROOT\"\n";
    final String oldManagedScript = """
      #!/bin/bash
      set -euo pipefail
      ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
      LOG="$ROOT/launcher.log"
      [[ ! -L "$LOG" ]] || { printf '%s\\n' 'Unexpected linked launcher log. No game was started.' >&2; exit 1; }
      run_manager() {
        JAVA="$("$ROOT/bootstrap-java" "$ROOT")" || return
        "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$ROOT/definitive-manager.jar" --manage "$ROOT"
      }
      if run_manager >> "$LOG" 2>&1; then exit 0; else CODE=$?; fi
      MESSAGE="Definitive could not start (code $CODE). Details: $LOG"
      if command -v kdialog >/dev/null; then kdialog --error "$MESSAGE" || true
      elif command -v zenity >/dev/null; then zenity --error --text="$MESSAGE" || true
      else printf '%s\\n' "$MESSAGE" >&2; fi
      exit "$CODE"
      """;
    final String legacyTemplate = oldManagedScript
      .replace("run_manager() {", "run_manager() {\n  # Java is 64-bit; Steam also exports a 32-bit overlay. Preserve other preload entries.\n  if [[ ${LD_PRELOAD:-} == *ubuntu12_32/gameoverlayrenderer.so* ]]; then\n    LD_PRELOAD=${LD_PRELOAD//ubuntu12_32\\/gameoverlayrenderer.so/ubuntu12_64\\/gameoverlayrenderer.so}\n    export LD_PRELOAD\n  fi")
      .replace("--manage \"$ROOT\"", "@MODE@ \"$ROOT\"")
      .replace("MESSAGE=", "if [[ @MODE@ == --play && ( $CODE == 130 || $CODE == 143 ) ]]; then printf '%s\\n' 'Stopped from Steam.' >> \"$LOG\"; exit 0; fi\nMESSAGE=");
    final String scriptTemplate = """
      #!/bin/bash
      set -euo pipefail
      ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
      LOG="$ROOT/launcher.log"
      [[ ! -L "$LOG" && ( ! -e "$LOG" || -f "$LOG" ) ]] || { printf '%s\\n' 'Unexpected launcher log. No game was started.' >&2; exit 1; }
      PREVIOUS="$LOG.1"
      [[ ! -L "$PREVIOUS" && ( ! -e "$PREVIOUS" || -f "$PREVIOUS" ) ]] || { printf '%s\\n' 'Unexpected previous launcher log.' >&2; exit 1; }
      LOGDIR="$(mktemp -d "$ROOT/.launcher-log.XXXXXXXX")"
      TEMPLOG="$LOGDIR/output"
      exec 4>"$TEMPLOG"
      cleanup() { exec 4>&-; rm -f -- "$TEMPLOG"; rmdir -- "$LOGDIR" 2>/dev/null || true; }
      trap cleanup EXIT
      publish() {
        if [[ $(uname -s) == Linux ]]; then mv -Tf -- "$1" "$2"
        else mv -fh -- "$1" "$2"; fi
      }
      run_manager() {
        # Keep Steam's overlay compatible with the 64-bit runtime.
        if [[ ${LD_PRELOAD:-} == *ubuntu12_32/gameoverlayrenderer.so* ]]; then
          LD_PRELOAD=${LD_PRELOAD//ubuntu12_32\\/gameoverlayrenderer.so/ubuntu12_64\\/gameoverlayrenderer.so}
          export LD_PRELOAD
        fi
        JAVA="$("$ROOT/bootstrap-java" "$ROOT")" || return
        "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$ROOT/definitive-manager.jar" @MODE@ "$ROOT"
      }
      set +e
      run_manager 2>&1 | { head -c 16777216; cat >/dev/null; } >&4
      CODE=${PIPESTATUS[0]}
      set -e
      if [[ @MODE@ == --play && ( $CODE == 130 || $CODE == 143 ) ]]; then printf '%s\\n' 'Stopped from Steam.' >&4; CODE=0; fi
      exec 4>&-
      [[ ! -L "$LOG" && ( ! -e "$LOG" || -f "$LOG" ) ]] || { printf '%s\\n' "Launcher output retained at $TEMPLOG" >&2; trap - EXIT; exit 1; }
      [[ ! -L "$PREVIOUS" && ( ! -e "$PREVIOUS" || -f "$PREVIOUS" ) ]] || { printf '%s\\n' "Launcher output retained at $TEMPLOG" >&2; trap - EXIT; exit 1; }
      if [[ -f "$LOG" ]]; then publish "$LOG" "$PREVIOUS"; fi
      publish "$TEMPLOG" "$LOG"
      if [[ $CODE == 0 ]]; then exit 0; fi
      MESSAGE="Definitive could not start (code $CODE). Details: $LOG"
      if command -v kdialog >/dev/null; then kdialog --error "$MESSAGE" || true
      elif command -v zenity >/dev/null; then zenity --error --text="$MESSAGE" || true
      else printf '%s\\n' "$MESSAGE" >&2; fi
      exit "$CODE"
      """;
    for(final String name : new String[]{"Play Game.sh", "Manage Installation.sh"}) {
      final String scriptText = scriptTemplate.replace("@MODE@", name.equals("Play Game.sh") ? "--play" : "--manage");
      final Path script = this.root.resolve(name);
      if(!Files.exists(script)) Files.writeString(script, scriptText, StandardOpenOption.CREATE_NEW);
      else if(Files.size(script) < 16384 && (Files.readString(script).equals(previousScript) || Files.readString(script).equals(oldManagedScript) || Files.readString(script).equals(legacyTemplate.replace("@MODE@", name.equals("Play Game.sh") ? "--play" : "--manage")))) {
        final Path next = Files.createTempFile(this.root, ".launcher-", ".tmp");
        try { Files.writeString(next, scriptText); Files.move(next, script, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(next); }
      }
      script.toFile().setExecutable(true, true);
    }
  }

  private static void replaceFile(final Path source, final Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    forceDirectory(target.getParent());
  }
  private void repairBootstrap(final Path release) throws IOException {
    final var pending = new java.util.LinkedHashMap<Path, Path>();
    final var previous = new java.util.LinkedHashMap<Path, Path>();
    boolean retainRecovery = false;
    try {
      // Prepare the complete pair and its recovery copies before publishing either file.
      for(final String name : new String[]{"definitive-manager.jar", "bootstrap-java"}) {
        final Path target = this.root.resolve(name), source = release.resolve(name);
        if(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && PackageManifest.sha256(target).equals(PackageManifest.sha256(source))) continue;
        final Path next = Files.createTempFile(this.root, ".bootstrap-", ".tmp"); pending.put(target, next);
        Files.copy(source, next, StandardCopyOption.REPLACE_EXISTING);
        forceFile(next);
        if(!PackageManifest.sha256(next).equals(PackageManifest.sha256(source))) throw new IOException("Launcher copy verification failed. Retry installation.");
        if(name.equals("bootstrap-java")) next.toFile().setExecutable(true, true);
        if(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
          final Path backup = Files.createTempFile(this.root, ".bootstrap-backup-", ".tmp"); previous.put(target, backup);
          Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
          forceFile(backup);
          if(!PackageManifest.sha256(backup).equals(PackageManifest.sha256(target))) throw new IOException("Launcher recovery copy verification failed. No launcher was changed.");
        } else previous.put(target, null);
      }
      final Properties journal = new Properties(); journal.setProperty("format", "1"); journal.setProperty("release", release.getFileName().toString());
      atomicProperties(this.root.resolve(".bootstrap-transaction.properties"), journal);
      try {
        for(final var entry : pending.entrySet()) this.bootstrapPublisher.publish(entry.getValue(), entry.getKey());
      } catch(final IOException failure) {
        for(final var entry : previous.entrySet()) {
          try { if(entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else replaceFile(entry.getValue(), entry.getKey()); }
          catch(final IOException recovery) { retainRecovery = true; failure.addSuppressed(new IOException("Launcher recovery copy retained at " + entry.getValue(), recovery)); }
        }
        if(!retainRecovery) { Files.deleteIfExists(this.root.resolve(".bootstrap-transaction.properties")); forceDirectory(this.root); }
        throw failure;
      }
      Files.deleteIfExists(this.root.resolve(".bootstrap-transaction.properties")); forceDirectory(this.root);
    } finally {
      for(final Path temporary : pending.values()) Files.deleteIfExists(temporary);
      if(!retainRecovery) for(final Path backup : previous.values()) if(backup != null) Files.deleteIfExists(backup);
    }
  }

  static void atomicProperties(final Path path, final Properties properties) throws IOException {
    if(Files.isSymbolicLink(path)) throw new IOException("Unexpected linked properties file: " + path);
    if(path.getFileName().toString().equals("state.properties") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      final Properties previous = readState(path);
      if(previous.containsKey("version")) atomicProperties(path.resolveSibling(".state-last-good.properties"), Boolean.parseBoolean(properties.getProperty("uninstalled", "false")) ? properties : previous);
    }
    final Path temporary = Files.createTempFile(path.getParent(), ".state-", ".tmp");
    try {
      try(final var output = Files.newOutputStream(temporary)) { properties.store(output, "Definitive managed state"); }
      try(final var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      forceDirectory(path.getParent());
    } finally { Files.deleteIfExists(temporary); }
  }

  private static Properties readState(final Path path) throws IOException {
    if(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new Properties();
    if(Files.size(path) > 65536) throw new IOException("Installation state is oversized.");
    final Properties state;
    try { state = PackageManifest.readProperties(path); }
    catch(final IllegalArgumentException failure) { throw new IOException("Installation state is damaged.", failure); }
    if(!state.getProperty("version", "").matches("alpha-[a-f0-9]{16}") || !state.getProperty("data", "").matches("data-[a-f0-9-]{36}")) throw new IOException("Installation state is damaged. Recovery is required.");
    for(final String name : new String[]{"previousVersion", "retainedNewerVersion"}) if(!state.getProperty(name, "").isEmpty() && !state.getProperty(name).matches("alpha-[a-f0-9]{16}")) throw new IOException("Invalid retained release reference.");
    for(final String name : new String[]{"previousSnapshot"}) if(!state.getProperty(name, "").isEmpty() && !state.getProperty(name).matches("snapshot-[a-f0-9-]{36}")) throw new IOException("Invalid snapshot reference.");
    if(!state.getProperty("retainedNewerData", "").isEmpty() && !state.getProperty("retainedNewerData").matches("data-[a-f0-9-]{36}")) throw new IOException("Invalid retained private data reference.");
    return state;
  }
  private void recoverState() throws IOException {
    final Path active = this.root.resolve("state.properties"), backup = this.root.resolve(".state-last-good.properties");
    try { if(Files.exists(active, LinkOption.NOFOLLOW_LINKS)) { readState(active); return; } if(!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) return; }
    catch(final IOException damaged) { if(!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) throw damaged; }
    final Properties last = readState(backup);
    if(!Files.isDirectory(this.data(last), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Last known installation data is unavailable. No recovery was applied.");
    if(!Boolean.parseBoolean(last.getProperty("uninstalled", "false"))) {
      final Path release = child(this.root.resolve("releases"), last.getProperty("version"), "alpha-[a-f0-9]{16}");
      PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
    }
    final var preserved = new ArrayList<String>();
    for(final String name : new String[]{"data", "snapshots"}) try(final var entries = Files.list(this.root.resolve(name))) {
      for(final Path path : entries.sorted().toList()) if(!path.equals(this.data(last))) preserved.add(path.toString());
    }
    if(Files.exists(active, LinkOption.NOFOLLOW_LINKS)) {
      final Path damaged = this.root.resolve("state-recovery-" + UUID.randomUUID() + ".properties");
      Files.move(active, damaged, StandardCopyOption.ATOMIC_MOVE); preserved.add(damaged.toString()); forceDirectory(this.root);
    }
    last.setProperty("recoveryPending", "true");
    last.setProperty("recoveryNotice", "Installation state was damaged. The last verified engine and data references were recovered. Other private generations and snapshots remain preserved; they were not automatically adopted.");
    last.setProperty("recoveryRestoredRelease", last.getProperty("version"));
    last.setProperty("recoveryRestoredData", this.data(last).toString());
    last.setProperty("recoveryPreservedLocations", String.join("\n", preserved));
    atomicProperties(active, last); InstallerLog.write("Recovered last known installation state; newer private generations were retained.");
  }
  private void recoverBootstrap() throws IOException {
    final Path journal = this.root.resolve(".bootstrap-transaction.properties");
    if(!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return;
    final Properties transaction = PackageManifest.readProperties(journal);
    if(!"1".equals(transaction.getProperty("format"))) throw new IOException("Unrecognized bootstrap transaction. No files were changed.");
    final Path release = child(this.root.resolve("releases"), transaction.getProperty("release", ""), "alpha-[a-f0-9]{16}");
    PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
    this.repairBootstrap(release);
  }
  private void recoverReleaseRepair() throws IOException {
    final Path file = this.root.resolve(".release-repair-transaction.properties");
    if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
    final Properties journal = PackageManifest.readProperties(file);
    if(!"1".equals(journal.getProperty("format"))) throw new IOException("Unrecognized release repair. No files were changed.");
    final String id = journal.getProperty("release", "");
    final String token = journal.getProperty("token", "");
    if(!token.matches("[a-f0-9-]{36}")) throw new IOException("Unrecognized release repair ownership. No files were changed.");
    final Path release = child(this.root.resolve("releases"), id, "alpha-[a-f0-9]{16}");
    final Path staged = child(this.root, journal.getProperty("staged", ""), "\\.package-[0-9]+");
    final Path backup = child(this.root, journal.getProperty("backup", ""), "release-recovery-[a-f0-9-]{36}");
    if(Files.exists(release, LinkOption.NOFOLLOW_LINKS)) {
      try {
        final PackageManifest manifest = PackageManifest.read(release); manifest.verify(release, PackageManifest.hostPlatform());
        if(!manifest.id().equals(id)) throw new IOException("Repair identity mismatch.");
        Files.delete(file); forceDirectory(this.root); return;
      } catch(final IOException damaged) { if(Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Ambiguous release repair. Recovery directories were retained.", damaged); }
    }
    if(Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
      final PackageManifest manifest = PackageManifest.read(staged); manifest.verify(staged, PackageManifest.hostPlatform());
      if(!manifest.id().equals(id)) throw new IOException("Pending repair package has a different identity.");
      if(Files.exists(release, LinkOption.NOFOLLOW_LINKS)) { markRecoveryBackup(release, ".definitive-release-backup", token); Files.move(release, backup, StandardCopyOption.ATOMIC_MOVE); forceDirectory(this.root); forceDirectory(release.getParent()); }
      Files.move(staged, release, StandardCopyOption.ATOMIC_MOVE); forceDirectory(this.root); forceDirectory(release.getParent());
    } else if(!Files.exists(release, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) {
      // Restore the exact pre-operation folder, even if it still needs repair.
      verifyRecoveryBackup(backup, ".definitive-release-backup", token);
      Files.move(backup, release, StandardCopyOption.ATOMIC_MOVE); Files.delete(release.resolve(".definitive-release-backup")); forceDirectory(release); forceDirectory(this.root); forceDirectory(release.getParent());
    } else throw new IOException("Release repair recovery is incomplete. No files were removed.");
    Files.delete(file); forceDirectory(this.root);
  }
  private void recoverDiscs() throws IOException {
    final Path journal = this.root.resolve(".disc-transaction.properties");
    if(!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return;
    final Properties transaction = PackageManifest.readProperties(journal);
    if(!"1".equals(transaction.getProperty("format")) || !transaction.getProperty("token", "").matches("[a-f0-9-]{36}")) throw new IOException("Unrecognized disc transaction. No images were changed.");
    final Path staged = child(this.root, transaction.getProperty("staged", ""), "\\.disc-import-[0-9]+");
    final Path backup = child(this.root, transaction.getProperty("backup", ""), "disc-recovery-[a-f0-9-]{36}");
    final Path destination = this.root.resolve("isos");
    if(Files.isSymbolicLink(destination)) throw new IOException("Unexpected linked disc destination.");
    if(Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && DiscImporter.matchesReceipt(destination, transaction)) {
      Files.delete(journal); forceDirectory(this.root); return;
    }
    if(Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
      if(!DiscImporter.matchesReceipt(staged, transaction)) throw new IOException("Pending disc publication is damaged. Recovery files were retained.");
      if(Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        if(Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Ambiguous disc recovery. No images were changed.");
        markRecoveryBackup(destination, ".definitive-disc-backup", transaction.getProperty("token"));
        Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE); forceDirectory(this.root);
      }
      Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE); forceDirectory(this.root);
    } else if(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) {
      verifyRecoveryBackup(backup, ".definitive-disc-backup", transaction.getProperty("token"));
      Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE); Files.delete(destination.resolve(".definitive-disc-backup")); forceDirectory(destination); forceDirectory(this.root);
    } else if(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Disc publication recovery is incomplete. No files were removed.");
    Files.delete(journal); forceDirectory(this.root);
  }
  static void markRecoveryBackup(final Path folder, final String name, final String token) throws IOException {
    final Path marker = folder.resolve(name);
    if(Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) { verifyRecoveryBackup(folder, name, token); return; }
    Files.writeString(marker, token, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    forceFile(marker); forceDirectory(folder);
  }
  static void verifyRecoveryBackup(final Path folder, final String name, final String token) throws IOException {
    final Path marker = folder.resolve(name);
    if(!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) != token.length()) throw new IOException("Recovery backup ownership is unresolved. No directory was adopted.");
    try(final var input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
      if(!new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals(token)) throw new IOException("Recovery backup token does not match. No directory was adopted.");
    }
  }
  static void forceDirectory(final Path path) throws IOException {
    if(Files.isSymbolicLink(path)) throw new IOException("Cannot synchronize a linked directory.");
    try(final var channel = FileChannel.open(path, StandardOpenOption.READ)) { channel.force(true); }
  }
  private static void forceFile(final Path path) throws IOException {
    try(final var channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
  }
  static void forceTree(final Path root) throws IOException {
    try(final var paths = Files.walk(root)) {
      for(final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        if(Files.isSymbolicLink(path)) throw new IOException("Cannot synchronize linked installation data.");
        if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) forceDirectory(path);
        else try(final var channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
      }
    }
    forceDirectory(root.getParent());
  }

  static void copyTree(final Path from, final Path to) throws IOException {
    if(Files.isSymbolicLink(from)) throw new IOException("Linked source data is not supported.");
    Files.createDirectories(to);
    try(final var paths = Files.walk(from)) {
      for(final Path path : paths.toList()) {
        if(Files.isSymbolicLink(path)) throw new IOException("Linked source data is not supported: " + path);
        final Path target = to.resolve(from.relativize(path));
        if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
        else if(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) Files.copy(path, target);
        else throw new IOException("Unsupported source data: " + path);
      }
    }
  }

  static void deleteOwnedTree(final Path path) throws IOException {
    if(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
    try(final var files = Files.walk(path)) {
      for(final Path entry : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(entry);
    }
  }

  private static void extractZip(final Path archive, final Path target, final InstallProgress progress) throws IOException {
    long total = 0;
    int count = 0;
    try(final var input = new ZipInputStream(Files.newInputStream(archive))) {
      for(java.util.zip.ZipEntry entry; (entry = input.getNextEntry()) != null;) {
        final String name = entry.getName();
        progress.phase("Installing game and HD artwork", "Unpacking " + name + " · " + count + " files", 70);
        if(++count > 30_000 || !(PackageManifest.allowed(entry.isDirectory() ? name.replaceAll("/$", "") + "/placeholder" : name) || name.equals(PackageManifest.METADATA) || name.equals(PackageManifest.HASHES))) throw new IOException("Unexpected archive path: " + name);
        final Path destination = target.resolve(name).normalize();
        if(!destination.startsWith(target)) throw new IOException("Archive escapes the package folder.");
        if(entry.isDirectory()) { Files.createDirectories(destination); continue; }
        Files.createDirectories(destination.getParent());
        try(final var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
          final byte[] buffer = new byte[1024 * 1024];
          for(int n; (n = input.read(buffer)) != -1;) {
            total += n;
            if(total > MAX_ARCHIVE_BYTES) throw new IOException("Archive exceeds the package size limit.");
            output.write(buffer, 0, n);
          }
        }
      }
    }
  }

  private static void writeInventory(final Path root) throws IOException {
    final Properties hashes = new Properties();
    try(final var files = Files.walk(root)) {
      for(final Path file : files.filter(Files::isRegularFile).toList()) hashes.setProperty(root.relativize(file).toString(), PackageManifest.sha256(file));
    }
    atomicProperties(root.resolve("snapshot-files.properties"), hashes);
  }

  private static void verifyInventory(final Path root) throws IOException {
    final Properties hashes = PackageManifest.readProperties(root.resolve("snapshot-files.properties"));
    final java.util.Set<String> actual = new java.util.HashSet<>();
    try(final var paths = Files.walk(root)) {
      for(final Path file : paths.toList()) {
        if(Files.isSymbolicLink(file)) throw new IOException("Linked snapshot file.");
        if(!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || file.getFileName().toString().equals("snapshot-files.properties")) continue;
        final String name = root.relativize(file).toString();
        actual.add(name);
        if(!PackageManifest.sha256(file).equals(hashes.getProperty(name))) throw new IOException("Snapshot checksum mismatch; original data has been retained.");
      }
    }
    if(!actual.equals(hashes.stringPropertyNames())) throw new IOException("Snapshot is incomplete; original data has been retained.");
  }
}
