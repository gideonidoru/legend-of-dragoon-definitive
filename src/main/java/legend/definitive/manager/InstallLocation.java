// Definitive installation discovery (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Small per-user location receipt; its contents are a hint, never authorization to remove files. */
final class InstallLocation {
  private InstallLocation() { }
  static Path receipt() { return Path.of(System.getProperty("definitive.locationRegistry", Path.of(System.getProperty("user.home"), ".config", "legend-of-dragoon-definitive", "installation.properties").toString())); }
  static Optional<Path> discover() {
    final Path file = receipt();
    if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
    try {
      if(Files.isSymbolicLink(file) || Files.size(file) > 65536) throw new IOException("Unsupported installation location record.");
      final String value = PackageManifest.readProperties(file).getProperty("root", "");
      final Path root = ManagerView.installationPath(value);
      for(Path part = root; part != null; part = part.getParent()) if(Files.isSymbolicLink(part)) throw new IOException("Recorded installation location is linked.");
      final Path marker = root.resolve(".definitive-owned");
      if(!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 1024 || !Files.readString(marker).equals("Legend of Dragoon Definitive install format 1\n")) throw new IOException("Recorded folder is not a Definitive managed installation.");
      final Properties state = PackageManifest.readProperties(root.resolve("state.properties"));
      InstallStore.child(root.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
      final Path data = InstallStore.child(root.resolve("data"), state.getProperty("data", ""), "data-[a-f0-9-]{36}");
      if(!Files.isDirectory(data, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Recorded installation data is missing.");
      return Optional.of(root);
    } catch(final IOException | RuntimeException failure) { InstallerLog.write("Previous installation location unavailable: " + failure.getMessage()); return Optional.empty(); }
  }
  static void record(final InstallStore store) throws IOException {
    store.verifyInstalled(); final Path file = receipt().toAbsolutePath();
    for(Path part = file; part != null; part = part.getParent()) if(Files.isSymbolicLink(part)) throw new IOException("Installation location record is linked. The game was installed, but its location could not be recorded.");
    Files.createDirectories(file.getParent()); final Properties properties = new Properties(); properties.setProperty("format", "1"); properties.setProperty("root", store.root().toString());
    InstallStore.atomicProperties(file, properties);
  }
}
