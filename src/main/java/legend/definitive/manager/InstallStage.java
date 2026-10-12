// Definitive owned download staging (2026-10-12), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;
import java.util.UUID;

/** A persisted ownership receipt permits crash cleanup and same-filesystem adoption. */
final class InstallStage implements AutoCloseable {
  private static final String SUFFIX = ".owned-stage.properties";
  private final Path path;
  private final Path receipt;
  private final String token;
  private final boolean resumable;

  private InstallStage(final Path path, final Path receipt, final String token, final boolean resumable) {
    this.path = path; this.receipt = receipt; this.token = token; this.resumable = resumable;
  }
  Path path() { return this.path; }
  static InstallStage create(final Path root) throws IOException { return create(root, "", false); }
  /** Exact inventory identity and mode keep a failed delivery resumable under the same lock. */
  static InstallStage open(final Path root, final String inventorySha256, final boolean fullReinstall) throws IOException {
    if(!inventorySha256.matches("[a-f0-9]{64}")) throw new IOException("Invalid resumable delivery identity.");
    InstallStage matching = null;
    try(final var entries = Files.list(root)) {
      for(final Path receipt : entries.sorted().toList()) {
        if(!valid(root, receipt, null)) continue;
        final Properties properties = read(receipt);
        if(inventorySha256.equals(properties.getProperty("inventorySha256")) && (!fullReinstall || "true".equals(properties.getProperty("fullReinstall")))) {
          if(matching != null) throw new IOException("Multiple recoverable downloads have the same identity. No files were removed.");
          matching = new InstallStage(root.resolve(properties.getProperty("stage")), receipt, properties.getProperty("token"), true);
        }
      }
    }
    if(matching != null) return matching;
    // A new inventory explicitly supersedes only receipt-owned download scratch.
    try(final var entries = Files.list(root)) {
      for(final Path receipt : entries.toList()) if(valid(root, receipt, null)) {
        final Properties properties = read(receipt);
        InstallStore.deleteOwnedTree(root.resolve(properties.getProperty("stage"))); Files.delete(receipt);
      }
    }
    return create(root, inventorySha256, fullReinstall);
  }
  private static InstallStage create(final Path root, final String inventorySha256, final boolean fullReinstall) throws IOException {
    final Path path = Files.createTempDirectory(root, ".package-");
    final Path receipt = root.resolve(path.getFileName() + SUFFIX);
    final String token = UUID.randomUUID().toString();
    try {
      final Properties properties = new Properties();
      properties.setProperty("format", "1"); properties.setProperty("token", token);
      properties.setProperty("stage", path.getFileName().toString());
      properties.setProperty("fileKey", fileKey(path));
      properties.setProperty("inventorySha256", inventorySha256); properties.setProperty("fullReinstall", Boolean.toString(fullReinstall));
      // Exclusive creation: never replace an existing owner file.
      Files.createFile(receipt);
      InstallStore.atomicProperties(receipt, properties);
      return new InstallStage(path, receipt, token, !inventorySha256.isEmpty());
    } catch(final IOException | RuntimeException failure) {
      InstallStore.deleteOwnedTree(path); throw failure;
    }
  }
  void requireOwned(final Path root) throws IOException {
    if(!this.path.getParent().equals(root) || !valid(root, this.receipt, this.token)) throw new IOException("Download staging ownership changed. No files were activated.");
  }
  static boolean ownedEntry(final Path root, final Path entry) throws IOException {
    final String name = entry.getFileName().toString();
    final Path receipt = name.endsWith(SUFFIX) ? entry : root.resolve(name + SUFFIX);
    return valid(root, receipt, null);
  }
  private static String fileKey(final Path path) throws IOException {
    final Object key = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
    if(key == null) throw new IOException("This filesystem cannot identify owned download staging safely.");
    return key.toString();
  }
  private static boolean valid(final Path root, final Path receipt, final String expectedToken) throws IOException {
    if(!receipt.getParent().equals(root) || !receipt.getFileName().toString().matches("\\.package-[0-9]+\\.owned-stage\\.properties")) return false;
    if(!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS) || Files.size(receipt) > 4096) return false;
    final Properties properties;
    try { properties = read(receipt); } catch(final IllegalArgumentException invalid) { return false; }
    final String name = properties.getProperty("stage", "");
    final String token = properties.getProperty("token", "");
    if(properties.size() != 6 || !properties.getProperty("inventorySha256", "").matches("(?:[a-f0-9]{64})?")
      || !java.util.Set.of("true", "false").contains(properties.getProperty("fullReinstall", "")) || !"1".equals(properties.getProperty("format")) || !token.matches("[a-f0-9-]{36}")
      || expectedToken != null && !expectedToken.equals(token) || !receipt.getFileName().toString().equals(name + SUFFIX)) return false;
    final Path path = root.resolve(name);
    return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
      && properties.getProperty("fileKey", "").equals(fileKey(path));
  }
  private static Properties read(final Path receipt) throws IOException {
    final Properties properties = new Properties();
    try(final var input = Files.newInputStream(receipt, LinkOption.NOFOLLOW_LINKS)) {
      final byte[] bytes = input.readNBytes(4097);
      if(bytes.length > 4096) throw new IOException("Download ownership receipt is too large.");
      properties.load(new java.io.ByteArrayInputStream(bytes));
    }
    return properties;
  }
  /** Called only after transaction recovery while holding the installation operation lock. */
  static void cleanup(final Path root) throws IOException { cleanup(root, false); }
  static void cleanup(final Path root, final boolean discardRecoverable) throws IOException {
    if(Files.exists(root.resolve(".release-repair-transaction.properties"), LinkOption.NOFOLLOW_LINKS)
      || Files.exists(root.resolve(CleanReinstall.JOURNAL), LinkOption.NOFOLLOW_LINKS)) return;
    try(final var entries = Files.list(root)) {
      for(final Path receipt : entries.toList()) {
        // Older deliveries had no ownership receipts. Reclaim only complete,
        // closed, hash-verified packages; partial or owner-modified folders survive.
        if(receipt.getFileName().toString().matches("\\.(?:files|package)-[0-9]+") && Files.isDirectory(receipt, LinkOption.NOFOLLOW_LINKS) && !Files.exists(root.resolve(receipt.getFileName() + SUFFIX), LinkOption.NOFOLLOW_LINKS)) {
          try { PackageManifest.read(receipt).verify(receipt, PackageManifest.hostPlatform()); }
          catch(final IOException | RuntimeException uncertain) { continue; }
          InstallStore.deleteOwnedTree(receipt); continue;
        }
        if(!valid(root, receipt, null) || !discardRecoverable && !read(receipt).getProperty("inventorySha256", "").isEmpty()) continue;
        final String name = receipt.getFileName().toString();
        InstallStore.deleteOwnedTree(root.resolve(name.substring(0, name.length() - SUFFIX.length())));
        Files.delete(receipt);
      }
    }
  }
  /** Success also disposes a verified stage when an already-valid release needed no move. */
  void completed() throws IOException {
    if(Files.exists(this.path, LinkOption.NOFOLLOW_LINKS)) {
      this.requireOwned(this.path.getParent());
      InstallStore.deleteOwnedTree(this.path);
    }
    Files.deleteIfExists(this.receipt);
  }
  @Override public void close() throws IOException {
    if(Files.exists(this.path.getParent().resolve(".release-repair-transaction.properties"), LinkOption.NOFOLLOW_LINKS)) return;
    if(Files.exists(this.path, LinkOption.NOFOLLOW_LINKS)) {
      this.requireOwned(this.path.getParent());
      if(this.resumable) return;
      InstallStore.deleteOwnedTree(this.path);
    }
    // The stage may already have moved into its verified release directory.
    Files.deleteIfExists(this.receipt);
  }
}
