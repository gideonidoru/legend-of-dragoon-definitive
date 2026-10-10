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
    return importDiscs(store, selected, InstallProgress.NONE);
  }
  public static String importDiscs(final InstallStore store, final Iterable<Path> selected, final InstallProgress progress) throws IOException {
    return importDiscs(store, selected, progress, false);
  }
  static final class DifferentDiscs extends IOException {
    DifferentDiscs(final String message) { super(message); }
  }
  record Existing(boolean usable, boolean changed, String detail) { }
  static Existing existing(final InstallStore store, final InstallProgress progress) throws IOException {
    final Path folder = store.root().resolve("isos");
    final java.util.List<Path> images;
    try(final var paths = Files.list(folder)) { images = paths.filter(p -> p.getFileName().toString().matches("(?i).*\\.(bin|iso)")).toList(); }
    if(images.isEmpty()) return new Existing(false, false, "No installed discs found.");
    final Map<String, Path> discs;
    try { discs = inspectSet(images); }
    catch(final IOException failure) { return new Existing(false, false, "Installed images need attention: " + failure.getMessage()); }
    final Path receipt = folder.resolve("disc-checksums.properties");
    final java.util.Properties known = Files.exists(receipt, LinkOption.NOFOLLOW_LINKS) ? PackageManifest.readProperties(receipt) : new java.util.Properties();
    final java.util.Properties hashes = new java.util.Properties(); boolean changed = false; int number = 0;
    for(final var disc : discs.entrySet()) {
      progress.phase("Checking installed disc " + (++number) + " of 4", "Verifying image identity and SHA256 checksum", number * 20);
      final String hash = PackageManifest.sha256(disc.getValue()); hashes.setProperty(disc.getKey(), hash);
      if(!known.isEmpty() && !hash.equals(known.getProperty(disc.getKey()))) changed = true;
    }
    if(known.isEmpty()) InstallStore.atomicProperties(receipt, hashes);
    return new Existing(true, changed, changed ? "The installed images differ from their recorded checksums." : "Four US discs found and checked. You can reuse them or select another set.");
  }
  static String importDiscs(final InstallStore store, final Iterable<Path> selected, final InstallProgress progress, final boolean replaceDifferent) throws IOException {
    try(final var operation = store.lock()) {
      final Map<String, Path> discs = inspectSet(selected);
      final Path destination = store.root().resolve("isos");
      final Map<String, Path> installed = new LinkedHashMap<>(); boolean unrecognized = false;
      try(final var paths = Files.list(destination)) {
        for(final Path path : paths.filter(p -> p.getFileName().toString().matches("(?i).*\\.(bin|iso)")).toList()) {
          try { if(installed.put(inspect(path), path) != null) unrecognized = true; }
          catch(final IOException failure) { unrecognized = true; }
        }
      }
      final var hashes = new java.util.Properties(); final var different = new java.util.ArrayList<String>(); int checked = 0;
      for(final var disc : discs.entrySet()) {
        progress.phase("Comparing disc " + (++checked) + " of 4", "Checking selected and installed SHA256 checksums", 20);
        final String hash = PackageManifest.sha256(disc.getValue()); hashes.setProperty(disc.getKey(), hash);
        final Path prior = installed.get(disc.getKey());
        if(prior != null && !hash.equals(PackageManifest.sha256(prior))) different.add(disc.getKey());
      }
      if((unrecognized || !different.isEmpty()) && !replaceDifferent) throw new DifferentDiscs("The selected images differ from the installed set" + (different.isEmpty() ? "." : ": " + different + ".") + " Replace them? The current folder will be retained as a recovery copy.");
      if(!unrecognized && different.isEmpty() && installed.keySet().equals(IDS)) {
        final Path receipt = destination.resolve("disc-checksums.properties");
        if(Files.exists(receipt, LinkOption.NOFOLLOW_LINKS) && !PackageManifest.readProperties(receipt).equals(hashes)) store.invalidatePreparedDiscsLocked();
        InstallStore.atomicProperties(destination.resolve("disc-checksums.properties"), hashes);
        return "The selected discs match the installed images exactly. Reusing them without copying.";
      }
      long total = 0; for(final Path image : discs.values()) total += Files.size(image);
      final long totalBytes = total;
      if(Files.getFileStore(store.root()).getUsableSpace() < totalBytes + 1024L * 1024 * 1024) throw new IOException("Not enough free space to copy your four discs. Free space or choose another installation drive.");
      final Path staged = Files.createTempDirectory(store.root(), ".disc-import-");
      try {
        long copied = 0; int number = 0;
        for(final var disc : discs.entrySet()) {
          final Path copy = staged.resolve(disc.getKey() + ".bin");
          final String phase = "Copying disc " + (++number) + " of 4";
          try(final var input = Files.newInputStream(disc.getValue()); final var output = Files.newOutputStream(copy, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            final byte[] buffer = new byte[1024 * 1024];
            for(int n; (n = input.read(buffer)) != -1;) { output.write(buffer, 0, n); copied += n; progress.bytes(phase, 20, 45, copied, totalBytes); }
          }
          progress.report(new InstallProgress.Update("Verifying disc " + number + " of 4", "Comparing original and installed SHA256 checksums", 20, 45, copied, totalBytes));
          if(!PackageManifest.sha256(copy).equals(hashes.getProperty(disc.getKey())) || !PackageManifest.sha256(disc.getValue()).equals(hashes.getProperty(disc.getKey()))) throw new IOException("Disc copy verification failed. Original inputs are unchanged.");
        }
        validateSet(staged);
        InstallStore.atomicProperties(staged.resolve("disc-checksums.properties"), hashes);
        if(!installed.isEmpty() || unrecognized) store.invalidatePreparedDiscsLocked();
        final Path backup = store.root().resolve("disc-recovery-" + java.util.UUID.randomUUID());
        Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE);
        try { Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch(final IOException failure) { try { Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE); } catch(final IOException recovery) { failure.addSuppressed(recovery); } throw failure; }
        try(final var entries = Files.list(backup)) { if(entries.findAny().isEmpty()) Files.delete(backup); }
        InstallerLog.write("Disc images published; previous folder retained if nonempty: " + backup);
        return "Four discs imported and copy checksums verified. Prepare discs extracts them privately; later launches reuse the extraction.";
      } finally { InstallStore.deleteOwnedTree(staged); }
    }
  }
}
