package legend.definitive.fmv;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** FMV semantic closure, called after the generic package verifies every listed hash. */
public final class FmvPackageAssets {
  private FmvPackageAssets() { }
  public static void verify(final Path root, final Properties hashes) throws IOException {
    final Set<String> actual = new HashSet<>();
    for(final String name : hashes.stringPropertyNames()) if(name.startsWith(FmvAssetManifest.ROOT)) actual.add(name);
    if(actual.isEmpty()) return; // Existing embedded-only packages have no external data.
    final Path metadata = root.resolve(FmvAssetManifest.FILE);
    if(!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) throw new IOException("FMVHD data manifest is missing");
    final FmvAssetManifest assets;
    try(final var input = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS)) { assets = FmvAssetManifest.read(input); }
    if(!actual.equals(assets.paths())) throw new IOException("FMVHD data closure is incomplete or unexpected");
    if(!assets.provenanceSha256().equals(hashes.getProperty(FmvAssetManifest.ROOT + "provenance.json")) || !assets.noticeSha256().equals(hashes.getProperty(FmvAssetManifest.ROOT + "NOTICE.txt"))) throw new IOException("FMVHD provenance/notice identity mismatch");
    for(final var film : assets.films().values()) {
      final Path movie = root.resolve(film.path());
      if(!film.source().sha256().equals(hashes.getProperty(film.path())) || !Files.isRegularFile(movie, LinkOption.NOFOLLOW_LINKS) || Files.size(movie) != film.source().bytes()) throw new IOException("FMVHD movie identity mismatch");
    }
    final var modules = hashes.stringPropertyNames().stream().filter(n -> n.matches("bundled-mods/FMVHD-v[0-9.]+\\.jar")).toList();
    if(modules.size() != 1) throw new IOException("FMVHD code module is missing or ambiguous");
    try(final var jar = new ZipFile(root.resolve(modules.getFirst()).toFile())) {
      final Set<String> entries = new HashSet<>(); final var iterator = jar.entries();
      while(iterator.hasMoreElements()) {
        final String name = iterator.nextElement().getName();
        if(!entries.add(name) || name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/", -1)).contains("..") || name.startsWith("fmvhd/videos/") || entries.size() > 256) throw new IOException("FMVHD code module has unsafe/duplicate entries or bundled movies");
      }
      final var entry = jar.getEntry("fmvhd/assets.properties");
      if(entry == null || entry.getSize() <= 0 || entry.getSize() > 65536) throw new IOException("FMVHD code manifest is missing or oversized");
      try(final var input = jar.getInputStream(entry)) {
        if(!assets.equals(FmvAssetManifest.read(input))) throw new IOException("FMVHD code and movie manifests disagree");
      }
    }
  }
}
