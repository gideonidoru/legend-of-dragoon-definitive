package legend.definitive.manager;

import legend.definitive.fmv.FmvAssetManifest;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Imports only verified legacy movie bytes into an unactivated candidate. */
final class FmvMigration implements AutoCloseable {
  private final Path current, staged;
  private final PackageManifest next;
  private final boolean enabled;
  private boolean attempted, closed;
  private ZipFile archive;
  private Map<String, FmvAssetManifest.Source> legacy;
  private FmvAssetManifest target;

  FmvMigration(final Path current, final Path staged, final PackageManifest next, final boolean enabled) {
    this.current = current; this.staged = staged; this.next = next; this.enabled = enabled;
  }
  private static boolean ordinary(final Path root, final Path file) {
    for(Path part = file; part != null && part.startsWith(root); part = part.getParent()) if(Files.isSymbolicLink(part)) return false;
    return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
  }
  private void openLegacy() throws IOException {
    final PackageManifest manifest = PackageManifest.read(this.current);
    if(!manifest.id().equals(this.current.getFileName().toString()) || !PackageManifest.identity(manifest.metadata(), manifest.hashes()).equals(manifest.id())) throw new IOException("Legacy release identity mismatch");
    final var jars = manifest.hashes().stringPropertyNames().stream().filter(n -> n.matches("bundled-mods/FMVHD-v[0-9.]+\\.jar")).toList();
    if(jars.size() != 1) throw new IOException("Legacy FMVHD module is missing or ambiguous");
    final Path jar = this.current.resolve(jars.getFirst());
    if(!ordinary(this.current, jar) || Files.size(jar) > 2L * 1024 * 1024 * 1024 || !PackageManifest.sha256(jar).equals(manifest.hashes().getProperty(jars.getFirst()))) throw new IOException("Legacy FMVHD module differs from its installed hash pin");
    final ZipFile opened = new ZipFile(jar.toFile());
    try {
      final Set<String> entries = new HashSet<>(); final var iterator = opened.entries();
      while(iterator.hasMoreElements()) {
        final ZipEntry entry = iterator.nextElement(); final String name = entry.getName();
        if(!entries.add(name) || name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/", -1)).contains("..") || entries.size() > 256) throw new IOException("Unsafe or duplicate legacy FMVHD entry");
      }
      final ZipEntry metadata = opened.getEntry("fmvhd/assets.properties");
      if(metadata == null || metadata.getSize() <= 0 || metadata.getSize() > 65536) throw new IOException("Legacy movie manifest is missing or oversized");
      try(final var input = opened.getInputStream(metadata)) { this.legacy = FmvAssetManifest.readLegacy(input); }
      for(final String name : FmvAssetManifest.NAMES) {
        final ZipEntry entry = opened.getEntry("fmvhd/videos/" + name + ".mp4");
        if(entry == null || entry.isDirectory() || entry.getSize() != this.legacy.get(name).bytes()) throw new IOException("Legacy movie inventory is incomplete");
      }
      if(entries.stream().filter(n -> n.startsWith("fmvhd/videos/") && !n.endsWith("/")).count() != 18) throw new IOException("Legacy movie inventory has unexpected entries");
      this.archive = opened;
    } catch(final IOException | RuntimeException failure) {
      try { opened.close(); } catch(final IOException closeFailure) { failure.addSuppressed(closeFailure); }
      throw failure;
    }
  }
  boolean importMovie(final String name, final String digest, final long bytes, final Path destination) throws IOException, InterruptedException {
    if(this.closed) throw new IOException("FMV import is closed");
    if(!destination.toAbsolutePath().normalize().equals(this.staged.resolve(name).toAbsolutePath().normalize())
      || this.staged.equals(this.current) || Files.isSymbolicLink(this.staged) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Movie import must target a new staged file");
    if(!this.enabled || this.current == null || !name.startsWith(FmvAssetManifest.ROOT + "videos/")) return false;
    if(this.target == null) {
      final Path metadata = this.staged.resolve(FmvAssetManifest.FILE);
      if(!ordinary(this.staged, metadata) || !PackageManifest.sha256(metadata).equals(this.next.hashes().getProperty(FmvAssetManifest.FILE))) throw new IOException("Unverified candidate FMVHD manifest");
      try(final var input = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS)) { this.target = FmvAssetManifest.read(input); }
    }
    final var film = this.target.films().values().stream().filter(f -> f.path().equals(name)).findFirst().orElseThrow(() -> new IOException("Unexpected candidate movie path"));
    if(!film.source().sha256().equals(digest) || film.source().bytes() != bytes) throw new IOException("Candidate movie identity mismatch");
    if(!this.attempted) {
      this.attempted = true;
      try { openLegacy(); }
      catch(final IOException | RuntimeException failure) { InstallerLog.write("FMVHD local import unavailable; verified downloads retained: " + failure.getMessage()); }
    }
    if(this.archive == null || !film.source().equals(this.legacy.get(film.name()))) return false;
    final Path temporary = Files.createTempFile(destination.getParent(), ".fmv-import-", ".partial");
    try {
      try(final var input = this.archive.getInputStream(this.archive.getEntry("fmvhd/videos/" + film.name() + ".mp4")); final var output = Files.newOutputStream(temporary)) {
        DownloadBody.copy(input, output, bytes, Duration.ofMinutes(15));
      }
      if(Files.size(temporary) != bytes || !PackageManifest.sha256(temporary).equals(digest)) throw new IOException("Imported movie checksum mismatch");
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch(final IOException failure) {
      InstallerLog.write("FMVHD local movie import rejected; verified download retained: " + film.name());
      return false;
    } finally { Files.deleteIfExists(temporary); }
  }
  @Override public void close() throws IOException {
    this.closed = true;
    if(this.archive != null) { final ZipFile opened = this.archive; this.archive = null; opened.close(); }
  }
}
