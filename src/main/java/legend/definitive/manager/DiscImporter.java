// Definitive installation tooling (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Matches the upstream raw 2352-byte Mode 2 US-disc reader, not filename guesses. */
public final class DiscImporter {
  private DiscImporter() { }
  static final Set<String> IDS = Set.of("SCUS94491", "SCUS94584", "SCUS94585", "SCUS94586");

  public static String inspect(final Path path) throws IOException {
    if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Choose a regular BIN or raw ISO file: " + path.getFileName());
    try(final var input = new RandomAccessFile(path.toFile(), "r")) {
      if(input.length() < 17L * 2352 || input.length() % 2352 != 0) throw new IOException("Not a raw 2352-byte disc image: " + path.getFileName() + ". Use the supported BIN files, not CUE, CHD or cooked ISO.");
      input.seek(16L * 2352 + 24);
      final byte[] pvd = new byte[72];
      input.readFully(pvd);
      if(pvd[0] != 1 || pvd[6] != 1 || !new String(pvd, 1, 5, StandardCharsets.US_ASCII).equals("CD001") || !new String(pvd, 8, 32, StandardCharsets.US_ASCII).strip().equals("PLAYSTATION")) throw new IOException("Unsupported PlayStation disc image: " + path.getFileName());
      final String id = new String(pvd, 40, 32, StandardCharsets.US_ASCII).strip();
      if(!IDS.contains(id)) throw new IOException("This alpha supports US retail discs only; found " + id + " in " + path.getFileName());
      return id;
    }
  }

  static Map<String, Path> inspectSet(final Iterable<Path> paths) throws IOException {
    final Map<String, Path> discs = new LinkedHashMap<>();
    for(final Path path : paths) {
      final String id = inspect(path);
      if(discs.put(id, path) != null) throw new IOException("Two copies of " + id + " were selected. Select one image for each disc.");
    }
    if(!discs.keySet().equals(IDS)) throw new IOException("Select all four US disc BIN files. Missing: " + IDS.stream().filter(id -> !discs.containsKey(id)).toList());
    return discs;
  }

  public static void validateSet(final Path folder) throws IOException {
    try(final var paths = Files.list(folder)) {
      inspectSet(paths.filter(p -> p.getFileName().toString().matches("(?i).*\\.(bin|iso)")).toList());
    }
  }

  public static String importDiscs(final InstallStore store, final Iterable<Path> selected) throws IOException {
    try(final var operation = store.lock()) {
      final Map<String, Path> discs = inspectSet(selected);
      final Path destination = store.root().resolve("isos");
      try(final var existing = Files.list(destination)) {
        if(existing.findAny().isPresent()) throw new IOException("Disc folder already contains files. Existing discs have been preserved; use them or choose a fresh installation.");
      }
      final Path staged = Files.createTempDirectory(store.root(), ".disc-import-");
      try {
        for(final var disc : discs.entrySet()) {
          final Path copy = staged.resolve(disc.getKey() + ".bin");
          Files.copy(disc.getValue(), copy);
          if(!PackageManifest.sha256(copy).equals(PackageManifest.sha256(disc.getValue()))) throw new IOException("Disc copy verification failed. Original inputs are unchanged.");
        }
        validateSet(staged);
        // Under the operation lock, replace only the known empty managed directory.
        Files.delete(destination);
        try { Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch(final IOException failure) { Files.createDirectories(destination); throw failure; }
        return "Four discs imported and copy checksums verified. Prepare discs extracts them privately; later launches reuse the extraction.";
      } finally { InstallStore.deleteOwnedTree(staged); }
    }
  }
}
