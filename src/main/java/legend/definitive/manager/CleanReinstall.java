// Definitive clean reinstall transaction (2026-10-11), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Preserve personal generations before activation; resume obsolete-file cleanup afterwards. */
final class CleanReinstall {
  static final String JOURNAL = ".clean-reinstall.properties";
  private static final String RECEIPT = ".reinstall-preserved.properties";
  private static final Set<String> KEEP = Set.of(".definitive-owned", "state.properties", ".state-last-good.properties", JOURNAL,
    ".operation-lock", ".game-lock", GameLease.PENDING, GameLease.RUNNING, "isos", "releases", "data", "workspaces", "snapshots",
    "definitive-manager.jar", "bootstrap-java", "Play Game.sh", "Manage Installation.sh", ".java-path");
  private static final Set<String> PRIVATE = Set.of("saves", "savedata", "config", "mods", "texture-packs", "config.conf", "config.dcnf", "definitive-hd-mods.properties");
  private CleanReinstall() { }
  record Prepared(Path staged, String dataId) implements AutoCloseable {
    @Override public void close() throws IOException { InstallStore.deleteOwnedTree(this.staged); }
  }
  static Prepared prepare(final InstallStore store, final Properties old) throws IOException {
    final Path root = store.root();
    final Path preserved = Files.createTempDirectory(root, ".reinstall-data-");
    final Prepared result = new Prepared(preserved, "data-" + UUID.randomUUID());
    try {
      final Path active = old.containsKey("data") ? store.data(old) : null;
      if(active != null) InstallStore.copyTree(active, preserved);
      for(final String name : List.of("saves", "mods", "config", "texture-packs")) Files.createDirectories(preserved.resolve(name));
      try(final var entries = Files.list(root)) {
        for(final Path entry : entries.sorted().toList()) {
          checkCancelled();
          final String name = entry.getFileName().toString();
          if(entry.equals(preserved) || KEEP.contains(name) && !Set.of("data", "snapshots", "workspaces", "releases").contains(name)) continue;
          if(name.equals("data") || name.equals("snapshots")) {
            try(final var generations = Files.list(entry)) {
              for(final Path generation : generations.sorted().toList()) if(!generation.equals(active)) preserveTree(root, generation, preserved, active);
            }
          } else if(name.equals("workspaces") || name.equals("releases")) {
            try(final var workspaces = Files.list(entry)) {
              for(final Path workspace : workspaces.toList()) {
                if(Files.isSymbolicLink(workspace)) throw new IOException("Linked workspace prevents clean reinstall; no personal files were removed.");
                for(final String personal : PRIVATE) {
                  final Path path = workspace.resolve(personal);
                  if(Files.isSymbolicLink(path)) {
                    final Path destination = path.getParent().resolve(Files.readSymbolicLink(path)).normalize();
                    if(!destination.startsWith(root.resolve("data"))) throw new IOException("Unrecognized personal workspace link; no personal files were removed.");
                  } else if(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) preserveTree(root, path, preserved, active);
                }
              }
            }
          } else if(PRIVATE.contains(name)) {
            preserveTree(root, entry, preserved, active);
          } else if(name.startsWith("release-recovery-")) {
            for(final String personal : PRIVATE) if(Files.exists(entry.resolve(personal), LinkOption.NOFOLLOW_LINKS)) preserveTree(root, entry.resolve(personal), preserved, active);
          } else if(!application(name) && !runtimeRoot(root, name)) {
            // Unknown owner files are preserved, never guessed to be obsolete application data.
            preserveTree(root, entry, preserved, active);
          }
        }
      }
      quarantineBundledDuplicates(preserved);
      final Properties receipt = inventory(preserved);
      InstallStore.atomicProperties(preserved.resolve(RECEIPT), receipt);
      verify(preserved);
      InstallStore.forceTree(preserved);
      return result;
    } catch(final IOException | RuntimeException failure) {
      try { result.close(); } catch(final IOException cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }
  private static boolean application(final String name) {
    return PackageManifest.ROOT_FILES.contains(name) || PackageManifest.SUPPORT.contains(name)
      || name.equals(PackageManifest.METADATA) || name.equals(PackageManifest.HASHES) || name.equals(FileDelivery.SIZES)
      || name.matches("lod-game-.*\\.jar") || name.equals("files") || name.equals("steam-artwork") || name.endsWith(".log")
      || name.matches("(?:release-recovery|disc-extraction-recovery|state-recovery)-.*")
      || name.startsWith(".package-") || name.startsWith(".files-") || name.startsWith(".contents-")
      || name.startsWith(".source-build-") || name.startsWith(".bootstrap-") || name.startsWith(".java-path-") || name.startsWith(".state-")
      || name.equals(".disc-transaction.properties") || name.equals(".release-repair-transaction.properties");
  }
  private static boolean runtimeRoot(final Path root, final String name) {
    final Path runtime = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
    return runtime.startsWith(root.resolve(name));
  }
  private static void preserveTree(final Path root, final Path source, final Path staged, final Path active) throws IOException {
    if(Files.isSymbolicLink(source)) throw new IOException("Linked personal data cannot be preserved: " + source);
    try(final var paths = Files.walk(source)) {
      for(final Path path : paths.sorted().toList()) {
        checkCancelled();
        if(Files.isSymbolicLink(path)) {
          final Path destination = path.getParent().resolve(Files.readSymbolicLink(path)).normalize();
          if(source.startsWith(root.resolve("workspaces")) && (destination.startsWith(root.resolve("data")) || destination.startsWith(root.resolve("releases")))) continue;
          throw new IOException("Linked personal data cannot be preserved: " + path);
        }
        if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
        if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported personal data: " + path);
        if(path.getFileName().toString().equals("snapshot-files.properties")) continue;
        // Historical identical data already retained as active data needs no duplicate copy.
        if(active != null && Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
          final Path matching = active.resolve(source.relativize(path));
          if(Files.isRegularFile(matching, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(matching)
            && Files.size(path) == Files.size(matching) && PackageManifest.sha256(path).equals(PackageManifest.sha256(matching))) continue;
        }
        final Path target = staged.resolve("recovered-reinstall").resolve(root.relativize(path));
        Files.createDirectories(target.getParent());
        final long size = Files.size(path);
        if(Files.getFileStore(staged).getUsableSpace() < size + 1024L * 1024 * 1024) throw new IOException("Not enough space to preserve all older personal files before clean reinstall.");
        try(final var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS); final var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
          final byte[] buffer = new byte[65536];
          for(int count; (count = input.read(buffer)) != -1;) { checkCancelled(); output.write(buffer, 0, count); }
        }
        if(Files.size(path) != Files.size(target) || !PackageManifest.sha256(path).equals(PackageManifest.sha256(target))) throw new IOException("Personal data preservation verification failed.");
      }
    }
  }
  private static void quarantineBundledDuplicates(final Path data) throws IOException {
    final Path mods = data.resolve("mods");
    final List<String> identities = List.of("envhd/EnvHdMod.class", "charhd/CharHdMod.class", "uihd/UiHdMod.class",
      "fxhd/FxHdMod.class", "fmvhd/FmvHdMod.class", "modelshd/ModelsHdMod.class", "scbackgroundhd/BackgroundHdMod.class");
    try(final var entries = Files.list(mods)) {
      for(final Path mod : entries.toList()) {
        final String name = mod.getFileName().toString();
        boolean duplicate = name.matches("(?i)(?:(?:EnvHD|CharHD|UIHD|FxHD|FMVHD|ModelsHD|Skurfa)(?:[-v0-9.]*))\\.jar")
          || name.matches("(?i)Env[1-4]HD(?:[-v0-9.]*)\\.zip");
        if(!duplicate && name.endsWith(".jar") && Files.isRegularFile(mod, LinkOption.NOFOLLOW_LINKS)) {
          for(final String identity : identities) if(ModArchiveIdentity.containsClass(mod, identity)) { duplicate = true; break; }
        }
        if(duplicate) {
          final Path inactive = data.resolve("recovered-reinstall/disabled-bundled-mods").resolve(name);
          Files.createDirectories(inactive.getParent());
          Files.move(mod, inactive, StandardCopyOption.ATOMIC_MOVE);
        }
      }
    }
  }
  private static Properties inventory(final Path data) throws IOException {
    final Properties hashes = new Properties();
    try(final var paths = Files.walk(data)) {
      for(final Path path : paths.toList()) {
        if(Files.isSymbolicLink(path)) throw new IOException("Linked preservation data.");
        if(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !path.equals(data.resolve(RECEIPT))) hashes.setProperty(data.relativize(path).toString(), PackageManifest.sha256(path));
      }
    }
    return hashes;
  }
  private static void verify(final Path data) throws IOException {
    if(!inventory(data).equals(PackageManifest.readProperties(data.resolve(RECEIPT)))) throw new IOException("Preserved personal data differs from its verified receipt. Cleanup stopped.");
  }
  static void journal(final Path root, final Properties next) throws IOException {
    final Properties journal = new Properties(); journal.setProperty("format", "1");
    journal.setProperty("version", next.getProperty("version")); journal.setProperty("data", next.getProperty("data"));
    journal.setProperty("receiptSha256", PackageManifest.sha256(root.resolve("data").resolve(next.getProperty("data")).resolve(RECEIPT)));
    InstallStore.atomicProperties(root.resolve(JOURNAL), journal);
  }
  static void recover(final InstallStore store, final InstallProgress progress) throws IOException {
    final Path root = store.root(), file = root.resolve(JOURNAL);
    if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
    if(Files.isSymbolicLink(file) || Files.size(file) > 65536) throw new IOException("Invalid clean reinstall journal.");
    final Properties journal = PackageManifest.readProperties(file);
    if(!journal.stringPropertyNames().equals(Set.of("format", "version", "data", "receiptSha256")) || !"1".equals(journal.getProperty("format"))) throw new IOException("Invalid clean reinstall journal.");
    final Path release = InstallStore.child(root.resolve("releases"), journal.getProperty("version", ""), "alpha-[a-f0-9]{16}");
    final Path data = InstallStore.child(root.resolve("data"), journal.getProperty("data", ""), "data-[a-f0-9-]{36}");
    final Properties state = store.state();
    if(!journal.getProperty("version").equals(state.getProperty("version")) || !journal.getProperty("data").equals(state.getProperty("data"))) {
      // Activation did not commit. Original private data remains authoritative.
      Files.delete(file); InstallStore.forceDirectory(root); return;
    }
    PackageManifest.read(release).verify(release, PackageManifest.hostPlatform());
    if(!PackageManifest.sha256(data.resolve(RECEIPT)).equals(journal.getProperty("receiptSha256"))) throw new IOException("Preservation receipt changed. Cleanup stopped.");
    verify(data);
    InstallStore.atomicProperties(root.resolve(".state-last-good.properties"), state);
    progress.phase("Cleaning old installation files", "The replacement and preserved personal files are verified", 97);
    checkCancelled();
    for(final String container : List.of("releases", "data", "snapshots", "workspaces")) {
      try(final var entries = Files.list(root.resolve(container))) {
        for(final Path path : entries.toList()) if(!path.equals(release) && !path.equals(data)) {
          checkCancelled(); InstallStore.deleteOwnedTree(path);
        }
      }
      InstallStore.forceDirectory(root.resolve(container));
    }
    try(final var entries = Files.list(root)) {
      for(final Path path : entries.toList()) if(!KEEP.contains(path.getFileName().toString()) && !runtimeRoot(root, path.getFileName().toString())) {
        checkCancelled(); InstallStore.deleteOwnedTree(path);
      }
    }
    InstallStore.forceDirectory(root);
    store.prepareCleanWorkspace();
    Files.delete(file); InstallStore.forceDirectory(root);
  }
  private static void checkCancelled() throws IOException {
    if(Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Clean reinstall cancelled. Personal files remain preserved.");
  }
}
