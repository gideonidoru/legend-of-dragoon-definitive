// Definitive installation tooling (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Closed package inventory. Private data is never an accepted package input. */
public record PackageManifest(Properties metadata, Properties hashes) {
  static final String METADATA = "definitive-package.properties";
  static final String HASHES = "definitive-files.properties";
  static final Set<String> SUPPORT = Set.of("gfx", "patches", "lang", "libs", "bundled-mods", "tools");
  static final Set<String> ROOT_FILES = Set.of("definitive-manager.jar", "bootstrap-java", "Install.sh", "LICENSE", "CREDITS", "credits.txt", "README.md", "log4j2.xml", "gamecontrollerdb.txt");

  public static PackageManifest read(final Path root) throws IOException {
    final var manifest = new PackageManifest(readProperties(root.resolve(METADATA)), readProperties(root.resolve(HASHES)));
    if(!"1".equals(manifest.metadata.getProperty("format")) || !"25".equals(manifest.metadata.getProperty("java"))) {
      throw new IOException("This package needs a newer installation manager.");
    }
    final String id = manifest.id();
    if(!id.matches("alpha-[a-f0-9]{16}")) throw new IOException("Invalid package identity.");
    if(!manifest.metadata.getProperty("gameJar", "").matches("lod-game-[A-Za-z0-9._-]+\\.jar")) throw new IOException("Invalid engine filename.");
    return manifest;
  }

  public String id() { return this.metadata.getProperty("id", ""); }
  public String platform() { return this.metadata.getProperty("platform", ""); }

  public static String hostPlatform() {
    final String os = System.getProperty("os.name").toLowerCase();
    final String arch = System.getProperty("os.arch").toLowerCase();
    final String cpu = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : arch.equals("amd64") || arch.equals("x86_64") ? "x64" : "unsupported";
    return (os.contains("mac") ? "macos" : os.contains("linux") ? "linux" : "unsupported") + '-' + cpu;
  }

  static boolean allowed(final String name) {
    if(name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) return false;
    for(final String part : name.split("/", -1)) if(part.isEmpty() || part.equals(".") || part.equals("..")) return false;
    return ROOT_FILES.contains(name) || name.matches("lod-game-[A-Za-z0-9._-]+\\.jar") || name.contains("/") && SUPPORT.contains(name.substring(0, name.indexOf('/')));
  }

  public void verify(final Path root, final String expectedPlatform) throws IOException {
    if(!this.platform().equals(expectedPlatform)) throw new IOException("Choose the " + expectedPlatform + " package; this is " + this.platform() + '.');
    final Set<String> actual = new TreeSet<>();
    try(final var paths = Files.walk(root)) {
      for(final Path path : paths.toList()) {
        if(Files.isSymbolicLink(path)) throw new IOException("Package links are not supported: " + path);
        if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
          final String directory = root.relativize(path).toString().replace('\\', '/');
          if(!directory.isEmpty() && !SUPPORT.contains(directory) && !(directory.contains("/") && SUPPORT.contains(directory.substring(0, directory.indexOf('/'))) && allowed(directory))) throw new IOException("Unexpected package directory: " + directory);
          continue;
        }
        final String name = root.relativize(path).toString().replace('\\', '/');
        if(name.equals(METADATA) || name.equals(HASHES)) continue;
        if(!allowed(name) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected package file: " + name);
        actual.add(name);
        if(!sha256(path).equals(this.hashes.getProperty(name))) throw new IOException("Package checksum mismatch: " + name + ". Download or rebuild it again.");
      }
    }
    if(!actual.equals(this.hashes.stringPropertyNames())) throw new IOException("Package inventory is incomplete.");
    if(!actual.containsAll(ROOT_FILES) || !actual.contains(this.metadata.getProperty("gameJar"))) throw new IOException("Required package files are missing.");
    if(actual.stream().noneMatch(n -> n.startsWith("libs/") && n.endsWith(".jar")) || actual.stream().noneMatch(n -> n.startsWith("bundled-mods/") && n.endsWith(".jar"))) throw new IOException("Engine dependencies or bundled artwork are missing.");
    if(!identity(this.metadata, this.hashes).equals(this.id())) throw new IOException("Package metadata identity mismatch.");
  }

  static String identity(final Properties metadata, final Properties hashes) {
    final StringBuilder canonical = new StringBuilder();
    new TreeSet<>(metadata.stringPropertyNames()).stream().filter(k -> !k.equals("id")).forEach(k -> canonical.append(k).append('=').append(metadata.getProperty(k)).append('\n'));
    new TreeSet<>(hashes.stringPropertyNames()).forEach(k -> canonical.append(k).append('=').append(hashes.getProperty(k)).append('\n'));
    return "alpha-" + digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16);
  }

  static Properties readProperties(final Path path) throws IOException {
    if(Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing file: " + path);
    final Properties properties = new Properties();
    try(final InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
    return properties;
  }

  static String sha256(final Path path) throws IOException {
    final MessageDigest md = sha();
    try(final InputStream stream = Files.newInputStream(path)) {
      final byte[] buffer = new byte[1024 * 1024];
      for(int count; (count = stream.read(buffer)) != -1;) md.update(buffer, 0, count);
    }
    return HexFormat.of().formatHex(md.digest());
  }

  static String digest(final byte[] data) { return HexFormat.of().formatHex(sha().digest(data)); }
  private static MessageDigest sha() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch(final java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
