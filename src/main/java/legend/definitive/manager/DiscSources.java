// Definitive disc import (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Extracts only potential images into owned staging; never executes or installs archive extras. */
public final class DiscSources {
  private DiscSources() { }
  private static final long LIMIT = 1100L * 1024 * 1024;

  public static String importSelected(final InstallStore store, final List<Path> selected) throws IOException, InterruptedException {
    return importSelected(store, selected, InstallProgress.NONE);
  }
  public static String importSelected(final InstallStore store, final List<Path> selected, final InstallProgress progress) throws IOException, InterruptedException {
    final Path stage = Files.createTempDirectory(store.root(), ".disc-sources-");
    try {
      final List<Path> images = new ArrayList<>();
      for(final Path source : selected) {
        progress.phase("Reading selected files", source.getFileName().toString(), 5);
        if(!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Choose disc images or ZIP, RAR and 7z archives.");
        final String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if(name.endsWith(".zip")) zip(source, stage, images, progress);
        else if(name.endsWith(".rar") || name.endsWith(".7z")) sevenZip(store, source, stage, images, progress);
        else if(image(name)) { DiscImporter.inspect(source); images.add(source); }
        else throw new IOException("Choose BIN / raw ISO images or ZIP, RAR and 7z archives. CUE files aren't needed.");
        if(images.size() > 4) throw new IOException("More than four disc images found. Choose one copy of each disc.");
      }
      return DiscImporter.importDiscs(store, images, progress);
    } finally { InstallStore.deleteOwnedTree(stage); }
  }

  private static boolean image(final String name) { return name.toLowerCase(Locale.ROOT).matches(".*\\.(bin|iso)"); }
  static void safeMember(final String name) throws IOException {
    if(name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.chars().anyMatch(c -> c < 32)) throw new IOException("Unsafe archive filename. Original files are unchanged.");
    for(final String part : name.split("/")) if(part.equals("..") || part.equals(".")) throw new IOException("Unsafe archive path. Original files are unchanged.");
  }

  private static void zip(final Path source, final Path stage, final List<Path> images, final InstallProgress progress) throws IOException {
    try(final var zip = new ZipFile(source.toFile())) {
      final var entries = zip.entries(); int count = 0, candidates = 0; long expanded = 0;
      while(entries.hasMoreElements()) {
        final var entry = entries.nextElement();
        if(++count > 30000) throw new IOException("Archive contains too many entries.");
        safeMember(entry.getName());
        if(entry.isDirectory() || !image(entry.getName())) continue;
        if(++candidates > 128 || entry.getSize() > LIMIT || entry.getSize() < 0 || (expanded += entry.getSize()) > 8L * LIMIT) throw new IOException("Archive has too many or oversized disc candidates.");
        final Path extracted = stage.resolve(UUID.randomUUID() + ".bin");
        progress.phase("Unpacking disc archive", entry.getName(), 10);
        try(final var input = zip.getInputStream(entry); final var output = Files.newOutputStream(extracted, StandardOpenOption.CREATE_NEW)) {
          final byte[] buffer = new byte[1024 * 1024]; long total = 0; final var crc = new java.util.zip.CRC32();
          for(int n; (n = input.read(buffer)) != -1;) {
            if((total += n) > LIMIT) throw new IOException("Disc archive exceeds its size limit.");
            output.write(buffer, 0, n); crc.update(buffer, 0, n);
            progress.bytes("Unpacking " + Path.of(entry.getName()).getFileName(), 10, 20, total, entry.getSize());
          }
          if(total != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Disc archive checksum or size mismatch.");
        }
        // Other games and utilities with .bin extensions are not copied into the installation.
        try { DiscImporter.inspect(extracted); images.add(extracted); }
        catch(final IOException ignored) { Files.delete(extracted); }
      }
    }
  }

  private static void sevenZip(final InstallStore store, final Path source, final Path stage, final List<Path> images, final InstallProgress progress) throws IOException, InterruptedException {
    final String version = store.state().getProperty("version", "");
    final Path release = InstallStore.child(store.root().resolve("releases"), version, "alpha-[a-f0-9]{16}");
    final var manifest = PackageManifest.read(release);
    manifest.verify(release, PackageManifest.hostPlatform());
    final Path helper = release.resolve("tools/7zz");
    if(!Files.isRegularFile(helper, LinkOption.NOFOLLOW_LINKS)) throw new IOException("This package does not include archive support. Install a current package or select raw images / ZIP.");
    if(!helper.toFile().setExecutable(true, true)) throw new IOException("Cannot enable the packaged archive helper.");
    final Path listing = stage.resolve("listing.txt");
    progress.phase("Reading archive contents", source.getFileName().toString(), 5);
    run(List.of(helper.toString(), "l", "-slt", "-ba", "-pDefinitive-no-encrypted-input", "--", source.toAbsolutePath().toString()), listing, stage.resolve("list-errors.txt"), 4 * 1024 * 1024, 30, InstallProgress.NONE, "", -1);
    int candidates = 0; long expanded = 0;
    for(final String block : Files.readString(listing).split("\\R\\s*\\R")) {
      final Map<String, String> fields = new HashMap<>();
      for(final String line : block.split("\\R")) {
        final int equal = line.indexOf(" = ");
        if(equal > 0) fields.put(line.substring(0, equal), line.substring(equal + 3));
      }
      final String name = fields.get("Path"); if(name == null) continue;
      safeMember(name);
      if(fields.containsKey("Symbolic Link") || fields.containsKey("Hard Link")) throw new IOException("Linked archive entries are unsupported.");
      if(!image(name) || "+".equals(fields.get("Folder"))) continue;
      final long size;
      try { size = Long.parseLong(fields.getOrDefault("Size", "-1")); }
      catch(final NumberFormatException e) { throw new IOException("Invalid archive image size."); }
      if(size < 0 || size > LIMIT || ++candidates > 128 || (expanded += size) > 8L * LIMIT) throw new IOException("Archive has too many or oversized disc candidates.");
      final Path extracted = stage.resolve(UUID.randomUUID() + ".bin");
      // stdout extraction cannot write an archive-controlled pathname or unpack unrelated content.
      run(List.of(helper.toString(), "x", "-so", "-y", "-spd", "-pDefinitive-no-encrypted-input", "--", source.toAbsolutePath().toString(), name), extracted, stage.resolve("extract-errors.txt"), LIMIT, 1200, progress, "Unpacking " + Path.of(name).getFileName(), size);
      if(Files.size(extracted) != size) throw new IOException("Archive extraction size mismatch.");
      try { DiscImporter.inspect(extracted); images.add(extracted); }
      catch(final IOException ignored) { Files.delete(extracted); }
    }
  }

  private static void run(final List<String> command, final Path output, final Path errors, final long limit, final int seconds, final InstallProgress progress, final String phase, final long total) throws IOException, InterruptedException {
    final Process process = new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
    process.getOutputStream().close();
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    try {
      while(!process.waitFor(100, TimeUnit.MILLISECONDS)) {
        progress.bytes(phase, 10, 20, Files.size(output), total);
        if(Files.size(output) > limit || Files.size(errors) > 1024 * 1024 || System.nanoTime() > deadline) throw new IOException("Archive extraction exceeded its time or size limit.");
      }
      if(process.exitValue() != 0) throw new IOException("Cannot read this archive. Check that it is complete and unencrypted, or select the raw disc images.");
      if(Files.size(output) > limit) throw new IOException("Archive output exceeded its size limit.");
    } finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
  }
}
