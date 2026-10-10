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
  private static final long MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024;

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
    for(final String dir : new String[]{"releases", "data", "workspaces", "snapshots", "isos"}) Files.createDirectories(this.root.resolve(dir));
    this.checkOwnedPaths();
  }

  public Path root() { return this.root; }
  public Properties state() throws IOException {
    return Files.exists(this.root.resolve("state.properties")) ? PackageManifest.readProperties(this.root.resolve("state.properties")) : new Properties();
  }

  private void checkOwnedPaths() throws IOException {
    for(final String name : new String[]{".definitive-owned", "state.properties", ".operation-lock", "releases", "data", "workspaces", "snapshots", "isos", "definitive-manager.jar", "bootstrap-java", "Play Game.sh", "Manage Installation.sh", ".java-path", "launcher.log"}) {
      if(Files.isSymbolicLink(this.root.resolve(name))) throw new IOException("Unexpected link in installation: " + name);
    }
  }

  public Operation lock() throws IOException {
    this.checkOwnedPaths();
    final FileChannel channel = FileChannel.open(this.root.resolve(".operation-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    final FileLock lock;
    try { lock = channel.tryLock(); }
    catch(final java.nio.channels.OverlappingFileLockException e) { channel.close(); throw new IOException("Close the game or other installation operation first."); }
    if(lock == null) { channel.close(); throw new IOException("Close the game or other installation operation first."); }
    return new Operation(channel, lock);
  }

  public record Operation(FileChannel channel, FileLock lock) implements AutoCloseable {
    @Override public void close() throws IOException { this.lock.release(); this.channel.close(); }
  }

  public String install(final Path source) throws IOException { return this.install(source, ""); }

  String install(final Path source, final String releaseAssetId) throws IOException {
    return this.install(source, releaseAssetId, InstallProgress.NONE);
  }
  String install(final Path source, final String releaseAssetId, final InstallProgress progress) throws IOException {
    try(final var operation = this.lock()) {
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
        final Properties old = this.state();
        final Path release = this.root.resolve("releases").resolve(manifest.id());
        if(Files.exists(release)) {
          if(Files.isSymbolicLink(release)) throw new IOException("Unexpected linked release. No external files were changed.");
          try { PackageManifest.read(release).verify(release, PackageManifest.hostPlatform()); }
          catch(final IOException damaged) {
            // Exact verified package identity under our owned releases directory: repair it as a unit.
            final Path recovery = this.root.resolve("release-recovery-" + UUID.randomUUID());
            Files.move(release, recovery, StandardCopyOption.ATOMIC_MOVE);
            try { Files.move(staged, release, StandardCopyOption.ATOMIC_MOVE); }
            catch(final IOException failure) { try { Files.move(recovery, release, StandardCopyOption.ATOMIC_MOVE); } catch(final IOException restore) { failure.addSuppressed(restore); } throw failure; }
            InstallerLog.write("Repaired damaged managed release; previous files retained at " + recovery);
          }
          if(manifest.id().equals(old.getProperty("version")) && !Boolean.parseBoolean(old.getProperty("uninstalled", "false"))) {
            this.writeLaunchers(release);
            this.verifyInstalled();
            if(!releaseAssetId.isEmpty()) { old.setProperty("releaseAssetId", releaseAssetId); atomicProperties(this.root.resolve("state.properties"), old); }
            progress.phase("Installation verified", this.root.toString(), 100);
            return "This version is already installed.";
          }
        } else Files.move(staged, release, StandardCopyOption.ATOMIC_MOVE);

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
          copyTree(activeData, nextData);
        } else {
          snapshotId = "";
          Files.createDirectory(nextData);
        }
        for(final String name : new String[]{"saves", "mods", "config", "texture-packs"}) Files.createDirectories(nextData.resolve(name));
        final Properties next = new Properties();
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
        if(!releaseAssetId.isEmpty()) next.setProperty("releaseAssetId", releaseAssetId);
        progress.phase("Creating the launcher", "Writing Play Game.sh and activating the verified version", 95);
        this.writeLaunchers(release);
        this.verifyInstalled(next);
        atomicProperties(this.root.resolve("state.properties"), next);
        progress.phase("Installation verified", this.root.toString(), 100);
        return old.containsKey("version") ? "Update installed. Previous engine and pre-update data are retained for rollback." : "Installed. Import your discs, then add Play Game.sh to Steam.";
      } finally { deleteOwnedTree(staged); }
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

  private void verifyBootstrap() throws IOException {
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
          && bootstrapHash.equals(reference.hashes().getProperty("bootstrap-java"))) return;
      }
    }
    throw new IOException("Launcher files are damaged or unrecognized. Retry installation to repair them.");
  }

  public String rollback() throws IOException {
    try(final var operation = this.lock()) {
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
      current.remove("previousVersion");
      current.remove("previousSnapshot");
      atomicProperties(this.root.resolve("state.properties"), current);
      return "Restored the previous engine with pre-update saves/settings. Newer data is retained separately.";
    }
  }

  Path data(final Properties state) throws IOException { return child(this.root.resolve("data"), state.getProperty("data", ""), "data-[a-f0-9-]{36}"); }

  static Path child(final Path parent, final String name, final String pattern) throws IOException {
    if(!name.matches(pattern)) throw new IOException("Invalid installation state. No files changed.");
    final Path path = parent.resolve(name);
    if(Files.isSymbolicLink(path)) throw new IOException("Unexpected linked data path.");
    return path;
  }

  public void setArtwork(final boolean hd) throws IOException {
    try(final var operation = this.lock()) {
      final Properties state = this.state();
      if(!state.containsKey("version")) throw new IOException("Install a package first.");
      state.setProperty("artwork", hd ? "hd" : "original");
      atomicProperties(this.root.resolve("state.properties"), state);
    }
  }

  public Path prepareLaunch() throws IOException {
    final Properties state = this.state();
    final Path release = child(this.root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
    final PackageManifest manifest = PackageManifest.read(release);
    manifest.verify(release, PackageManifest.hostPlatform());
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
    for(final String support : new String[]{"gfx", "patches", "lang", "libs"}) link(workspace.resolve(support), release.resolve(support));
    for(final String file : new String[]{"log4j2.xml", "gamecontrollerdb.txt"}) link(workspace.resolve(file), release.resolve(file));
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
    final boolean bundledEffects = Files.isDirectory(release.resolve("bundled-mods")) && hasBundledEffects(release.resolve("bundled-mods"));
    if(Files.isDirectory(data.resolve("mods"))) try(final var files = Files.list(data.resolve("mods"))) {
      for(final Path mod : files.toList()) if(mod.toString().endsWith(".jar")) {
        // A manual copy of our older artifact must not duplicate the bundled mod ID.
        // Retain the actual file in user data; only rebuild workspace-owned links.
        if(bundledModels && isModelsHdArtifact(mod)) {
          InstallerLog.write("Using the release's ModelsHD version; manual copy retained at " + mod);
          continue;
        }
        if(bundledEffects && isFxHdArtifact(mod)) {
          InstallerLog.write("Using the release's FxHD version; manual copy retained at " + mod);
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

  private static boolean isModelsHdArtifact(final Path file) {
    return file.getFileName().toString().matches("ModelsHD-[0-9]+\\.[0-9]+\\.[0-9]+\\.jar");
  }

  private static boolean hasBundledModels(final Path directory) throws IOException {
    try(final var files = Files.list(directory)) { return files.anyMatch(InstallStore::isModelsHdArtifact); }
  }

  private static boolean isFxHdArtifact(final Path file) throws IOException {
    if(file.getFileName().toString().matches("FxHD-v[0-9]+\\.[0-9]+\\.[0-9]+\\.jar")) return true;
    return file.getFileName().toString().endsWith(".jar") && ModArchiveIdentity.containsClass(file, "fxhd/FxHdMod.class");
  }

  private static boolean hasBundledEffects(final Path directory) throws IOException {
    try(final var files = Files.list(directory)) {
      for(final Path file : files.toList()) if(isFxHdArtifact(file)) return true;
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
    try(final var operation = this.lock()) { final var state = this.state(); state.setProperty("fullscreen", Boolean.toString(enabled)); atomicProperties(this.root.resolve("state.properties"), state); }
  }

  public String uninstall(final boolean deleteIsos, final InstallProgress progress) throws IOException {
    try(final var operation = this.lock()) {
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
    try(final var operation = this.lock()) {
      this.verifyInstalled();
      progress.phase("Verifying four discs", "Checking the US disc headers", 50);
      DiscImporter.validateSet(this.root.resolve("isos"));
      final Path workspace = this.prepareLaunch();
      final Properties state = this.state();
      final Path release = this.root.resolve("releases").resolve(state.getProperty("version"));
      final String gameJar = PackageManifest.read(release).metadata().getProperty("gameJar");
      final Path log = workspace.resolve("preparation.log");
      InstallerLog.write("Disc preparation log: " + log);
      progress.phase("Preparing game files", "Reading all four discs and extracting game assets", 60);
      final var command = java.util.List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx2G", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp", release.resolve(gameJar) + java.io.File.pathSeparator + release.resolve("libs/*"), "legend.definitive.tools.PrepareDiscs");
      final Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
      process.getOutputStream().close();
      try {
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(20);
        try(final var output = new java.io.RandomAccessFile(log.toFile(), "r")) {
          while(!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            String line; String last = null;
            while((line = output.readLine()) != null) if(line.startsWith("DEFINITIVE_STATUS\t") && line.length() > 18) last = line.substring(18);
            if(last != null) {
              final String phase = last.startsWith("Writing") ? "Writing game files" : last.startsWith("Transforming") ? "Converting game assets" : "Reading your discs";
              progress.phase(phase, last.substring(0, Math.min(220, last.length())), last.startsWith("Writing") ? 85 : last.startsWith("Transforming") ? 65 : 60);
            }
            if(System.nanoTime() > deadline) throw new IOException("Disc preparation took too long. Your images are retained; see " + log);
          }
        }
        if(process.exitValue() != 0) throw new IOException("Disc preparation could not finish. Your images are retained; retry with Use installed discs. Details: " + log);
        if(!this.discsPrepared()) throw new IOException("Disc preparation did not create its completion marker. Details: " + log);
        progress.phase("Game files ready", "All four discs are prepared. Launcher: " + this.root.resolve("Play Game.sh"), 100);
        return "Your discs are prepared. Choose whether to add Definitive to Steam.";
      } finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
  }

  public boolean discsPrepared() throws IOException {
    final Properties state = this.state();
    final String version = state.getProperty("version", ""), data = state.getProperty("data", "");
    child(this.root.resolve("releases"), version, "alpha-[a-f0-9]{16}");
    this.data(state);
    return Files.isRegularFile(this.root.resolve("workspaces").resolve(version + "-" + data).resolve("files/version"));
  }

  void invalidatePreparedDiscs() throws IOException {
    try(final var operation = this.lock()) {
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
    try(final var operation = this.lock()) {
      final Properties state = this.state();
      if(!state.containsKey("version")) throw new IOException("Install a package first.");
      state.setProperty("legacyTextures", Boolean.toString(enabled));
      atomicProperties(this.root.resolve("state.properties"), state);
    }
  }

  public int play() throws IOException, InterruptedException {
    try(final var operation = this.lock()) {
      DiscImporter.validateSet(this.root.resolve("isos"));
      final Path workspace = this.prepareLaunch();
      final Properties state = this.state();
      final Path release = this.root.resolve("releases").resolve(state.getProperty("version"));
      final String gameJar = PackageManifest.read(release).metadata().getProperty("gameJar");
      final var command = new ArrayList<String>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      if(PackageManifest.hostPlatform().startsWith("macos")) command.add("-XstartOnFirstThread");
      command.add("-Ddefinitive.legacyTextures=" + Boolean.parseBoolean(state.getProperty("legacyTextures", "false")));
      command.addAll(java.util.List.of("-ea", "-Xmx2G", "-Ddefinitive.managedInstall=true", "-Djoml.fastmath", "-Djoml.sinLookup", "-Djoml.useMathFma", "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", "-cp", release.resolve(gameJar) + java.io.File.pathSeparator + release.resolve("libs/*"), "legend.game.Main"));
      final Path log = workspace.resolve("launcher.log");
      if(Files.isSymbolicLink(log)) throw new IOException("Unexpected linked game log. No game was started.");
      InstallerLog.write("Starting game; details: " + log);
      final var builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
      normalizeSteamOverlay(builder.environment());
      Files.writeString(log, "\nDefinitive game launch: " + java.time.Instant.now() + "\nJava: " + Runtime.version() + "\nWorkspace: " + workspace + "\nPlatform: " + PackageManifest.hostPlatform() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      final Process game = builder.start();
      game.getOutputStream().close();
      try { return game.waitFor(); }
      finally { if(game.isAlive()) { game.destroyForcibly(); game.waitFor(); } }
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
    if(!Files.exists(runtimePath)) Files.writeString(runtimePath, Path.of(System.getProperty("java.home"), "bin", "java") + "\n", StandardOpenOption.CREATE_NEW);
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
    final String scriptTemplate = oldManagedScript
      .replace("run_manager() {", "run_manager() {\n  # Java is 64-bit; Steam also exports a 32-bit overlay. Preserve other preload entries.\n  if [[ ${LD_PRELOAD:-} == *ubuntu12_32/gameoverlayrenderer.so* ]]; then\n    LD_PRELOAD=${LD_PRELOAD//ubuntu12_32\\/gameoverlayrenderer.so/ubuntu12_64\\/gameoverlayrenderer.so}\n    export LD_PRELOAD\n  fi")
      .replace("--manage \"$ROOT\"", "@MODE@ \"$ROOT\"")
      .replace("MESSAGE=", "if [[ @MODE@ == --play && ( $CODE == 130 || $CODE == 143 ) ]]; then printf '%s\\n' 'Stopped from Steam.' >> \"$LOG\"; exit 0; fi\nMESSAGE=");
    for(final String name : new String[]{"Play Game.sh", "Manage Installation.sh"}) {
      final String scriptText = scriptTemplate.replace("@MODE@", name.equals("Play Game.sh") ? "--play" : "--manage");
      final Path script = this.root.resolve(name);
      if(!Files.exists(script)) Files.writeString(script, scriptText, StandardOpenOption.CREATE_NEW);
      else if(Files.size(script) < 16384 && (Files.readString(script).equals(previousScript) || Files.readString(script).equals(oldManagedScript))) {
        final Path next = Files.createTempFile(this.root, ".launcher-", ".tmp");
        try { Files.writeString(next, scriptText); Files.move(next, script, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(next); }
      }
      script.toFile().setExecutable(true, true);
    }
  }

  private static void replaceFile(final Path source, final Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
        if(!PackageManifest.sha256(next).equals(PackageManifest.sha256(source))) throw new IOException("Launcher copy verification failed. Retry installation.");
        if(name.equals("bootstrap-java")) next.toFile().setExecutable(true, true);
        if(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
          final Path backup = Files.createTempFile(this.root, ".bootstrap-backup-", ".tmp"); previous.put(target, backup);
          Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
          if(!PackageManifest.sha256(backup).equals(PackageManifest.sha256(target))) throw new IOException("Launcher recovery copy verification failed. No launcher was changed.");
        } else previous.put(target, null);
      }
      try {
        for(final var entry : pending.entrySet()) this.bootstrapPublisher.publish(entry.getValue(), entry.getKey());
      } catch(final IOException failure) {
        for(final var entry : previous.entrySet()) {
          try { if(entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else replaceFile(entry.getValue(), entry.getKey()); }
          catch(final IOException recovery) { retainRecovery = true; failure.addSuppressed(new IOException("Launcher recovery copy retained at " + entry.getValue(), recovery)); }
        }
        throw failure;
      }
    } finally {
      for(final Path temporary : pending.values()) Files.deleteIfExists(temporary);
      if(!retainRecovery) for(final Path backup : previous.values()) if(backup != null) Files.deleteIfExists(backup);
    }
  }

  static void atomicProperties(final Path path, final Properties properties) throws IOException {
    final Path temporary = Files.createTempFile(path.getParent(), ".state-", ".tmp");
    try {
      try(final var output = Files.newOutputStream(temporary)) { properties.store(output, "Definitive managed state"); }
      try(final var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally { Files.deleteIfExists(temporary); }
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
