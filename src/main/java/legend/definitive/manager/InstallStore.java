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
  private static final long MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024;

  public InstallStore(final Path root) throws IOException {
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
    for(final String dir : new String[]{"releases", "data", "workspaces", "snapshots", "isos"}) Files.createDirectories(this.root.resolve(dir));
    this.checkOwnedPaths();
  }

  public Path root() { return this.root; }
  public Properties state() throws IOException {
    return Files.exists(this.root.resolve("state.properties")) ? PackageManifest.readProperties(this.root.resolve("state.properties")) : new Properties();
  }

  private void checkOwnedPaths() throws IOException {
    for(final String name : new String[]{".definitive-owned", "state.properties", ".operation-lock", "releases", "data", "workspaces", "snapshots", "isos", "definitive-manager.jar", "bootstrap-java", "Play Game.sh", "Manage Installation.sh", ".java-path"}) {
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
    try(final var operation = this.lock()) {
      final Path staged = Files.createTempDirectory(this.root, ".package-");
      try {
        if(Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
          PackageManifest.read(source).verify(source, PackageManifest.hostPlatform());
          if(this.root.startsWith(source.toAbsolutePath().normalize())) throw new IOException("Choose a package outside the installation folder.");
          copyTree(source, staged);
        }
        else extractZip(source, staged);
        final PackageManifest manifest = PackageManifest.read(staged);
        manifest.verify(staged, PackageManifest.hostPlatform());
        final Properties old = this.state();
        final Path release = this.root.resolve("releases").resolve(manifest.id());
        if(Files.exists(release)) {
          PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
          if(manifest.id().equals(old.getProperty("version"))) {
            if(!releaseAssetId.isEmpty()) { old.setProperty("releaseAssetId", releaseAssetId); atomicProperties(this.root.resolve("state.properties"), old); }
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
        next.setProperty("previousVersion", old.getProperty("version", ""));
        next.setProperty("previousSnapshot", snapshotId);
        next.setProperty("previousArtwork", old.getProperty("artwork", "hd"));
        next.setProperty("previousReleaseAssetId", old.getProperty("releaseAssetId", ""));
        next.setProperty("artwork", old.getProperty("artwork", "hd"));
        next.setProperty("legacyTextures", old.getProperty("legacyTextures", "false"));
        next.setProperty("previousLegacyTextures", old.getProperty("legacyTextures", "false"));
        if(!releaseAssetId.isEmpty()) next.setProperty("releaseAssetId", releaseAssetId);
        this.writeLaunchers(release);
        atomicProperties(this.root.resolve("state.properties"), next);
        return old.containsKey("version") ? "Update installed. Previous engine and pre-update data are retained for rollback." : "Installed. Import your discs, then add Play Game.sh to Steam.";
      } finally { deleteOwnedTree(staged); }
    }
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
    if(Files.isDirectory(data.resolve("mods"))) try(final var files = Files.list(data.resolve("mods"))) {
      for(final Path mod : files.toList()) if(mod.toString().endsWith(".jar")) link(mods.resolve(mod.getFileName()), mod);
    }
    if(!"original".equals(state.getProperty("artwork", "hd"))) try(final var files = Files.list(release.resolve("bundled-mods"))) {
      for(final Path mod : files.toList()) {
        if(Files.exists(mods.resolve(mod.getFileName()), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Duplicate bundled artwork mod. Remove its custom copy before playing.");
        link(mods.resolve(mod.getFileName()), mod);
      }
    }
    return workspace;
  }

  public String prepareDiscs() throws IOException, InterruptedException {
    try(final var operation = this.lock()) {
      DiscImporter.validateSet(this.root.resolve("isos"));
      final Path workspace = this.prepareLaunch();
      final Properties state = this.state();
      final Path release = this.root.resolve("releases").resolve(state.getProperty("version"));
      final String gameJar = PackageManifest.read(release).metadata().getProperty("gameJar");
      final Path log = workspace.resolve("preparation.log");
      final var command = java.util.List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx2G", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp", release.resolve(gameJar) + java.io.File.pathSeparator + release.resolve("libs/*"), "legend.definitive.tools.PrepareDiscs");
      final Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
      process.getOutputStream().close();
      try {
        if(!process.waitFor(20, java.util.concurrent.TimeUnit.MINUTES)) throw new IOException("Disc preparation took too long. Your images are retained; see " + log);
        if(process.exitValue() != 0) throw new IOException("Disc preparation could not finish. Your images are retained; retry with Use installed discs. Details: " + log);
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
      final Process game = new ProcessBuilder(command).directory(workspace.toFile()).inheritIO().start();
      try { return game.waitFor(); }
      finally { if(game.isAlive()) { game.destroyForcibly(); game.waitFor(); } }
    }
  }

  private static void link(final Path link, final Path target) throws IOException {
    if(Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
      if(!Files.isSymbolicLink(link) || !Files.readSymbolicLink(link).equals(target)) throw new IOException("Unexpected workspace path: " + link);
    } else Files.createSymbolicLink(link, target.toAbsolutePath());
  }

  private void writeLaunchers(final Path release) throws IOException {
    final Path runtimePath = this.root.resolve(".java-path");
    if(!Files.exists(runtimePath)) Files.writeString(runtimePath, Path.of(System.getProperty("java.home"), "bin", "java") + "\n", StandardOpenOption.CREATE_NEW);
    final Path manager = this.root.resolve("definitive-manager.jar");
    if(!Files.exists(manager)) Files.copy(release.resolve(manager.getFileName()), manager);
    final Path bootstrap = this.root.resolve("bootstrap-java");
    if(!Files.exists(bootstrap)) Files.copy(release.resolve(bootstrap.getFileName()), bootstrap);
    bootstrap.toFile().setExecutable(true, true);
    for(final String name : new String[]{"Play Game.sh", "Manage Installation.sh"}) {
      final Path script = this.root.resolve(name);
      if(!Files.exists(script)) Files.writeString(script, "#!/bin/bash\nset -euo pipefail\nROOT=\"$(cd -- \"$(dirname -- \"${BASH_SOURCE[0]}\")\" && pwd)\"\nJAVA=\"$(\"$ROOT/bootstrap-java\" \"$ROOT\")\"\nexec \"$JAVA\" -jar \"$ROOT/definitive-manager.jar\" " + "--manage" + " \"$ROOT\"\n", StandardOpenOption.CREATE_NEW);
      script.toFile().setExecutable(true, true);
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

  private static void extractZip(final Path archive, final Path target) throws IOException {
    long total = 0;
    int count = 0;
    try(final var input = new ZipInputStream(Files.newInputStream(archive))) {
      for(java.util.zip.ZipEntry entry; (entry = input.getNextEntry()) != null;) {
        final String name = entry.getName();
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
