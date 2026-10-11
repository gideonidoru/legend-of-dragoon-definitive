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
    final var modules = hashes.stringPropertyNames().stream().filter(n -> n.matches("bundled-mods/FMVHD-v[0-9.]+\\.jar")).toList();
    if(actual.isEmpty()) {
      if(modules.isEmpty()) return;
      if(modules.size() != 1) throw new IOException("FMVHD module is ambiguous");
      // No external paths is legitimate only for a genuine embedded legacy module.
      // A regenerated generic inventory must not hide a stripped split package.
      try(final var jar = new ZipFile(root.resolve(modules.getFirst()).toFile())) {
        entries(jar);
        final var entry = manifest(jar);
        final Map<String, FmvAssetManifest.Source> legacy;
        try(final var input = jar.getInputStream(entry)) { legacy = FmvAssetManifest.readLegacy(input); }
        for(final var film : legacy.entrySet()) {
          final var movie = jar.getEntry("fmvhd/videos/" + film.getKey() + ".mp4");
          if(movie == null || movie.isDirectory() || movie.getSize() != film.getValue().bytes()) throw new IOException("Embedded legacy FMVHD movie closure is incomplete");
        }
      }
      return;
    }
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
    if(modules.size() != 1) throw new IOException("FMVHD code module is missing or ambiguous");
    try(final var jar = new ZipFile(root.resolve(modules.getFirst()).toFile())) {
      if(entries(jar).stream().anyMatch(name -> name.startsWith("fmvhd/videos/"))) throw new IOException("FMVHD split code includes bundled movies");
      final var entry = manifest(jar);
      try(final var input = jar.getInputStream(entry)) {
        if(!assets.equals(FmvAssetManifest.read(input))) throw new IOException("FMVHD code and movie manifests disagree");
      }
    }
  }
  private static Set<String> entries(final ZipFile jar) throws IOException {
    final Set<String> entries = new HashSet<>(); final var iterator = jar.entries();
    while(iterator.hasMoreElements()) {
      final String name = iterator.nextElement().getName();
      if(!entries.add(name) || name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/", -1)).contains("..") || entries.size() > 256) throw new IOException("FMVHD module has unsafe or duplicate entries");
    }
    return entries;
  }
  private static java.util.zip.ZipEntry manifest(final ZipFile jar) throws IOException {
    final var entry = jar.getEntry("fmvhd/assets.properties");
    if(entry == null || entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > 65536) throw new IOException("FMVHD code manifest is missing or oversized");
    return entry;
  }
}
