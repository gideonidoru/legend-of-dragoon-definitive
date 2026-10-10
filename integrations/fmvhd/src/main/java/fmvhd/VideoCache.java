package fmvhd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content-addressed local materialization because FFmpeg needs a seekable file. */
public final class VideoCache {
  private VideoCache() { }

  public static Path materialize(final Path root, final String digest, final long size, final InputStream resource) throws IOException {
    if(!digest.matches("[0-9a-f]{64}") || size <= 0 || size > 512L * 1024 * 1024) throw new IOException("Invalid FMVHD asset metadata");
    final Path absolute = root.toAbsolutePath().normalize();
    for(Path parent = absolute; parent != null; parent = parent.getParent()) {
      if(Files.isSymbolicLink(parent)) throw new IOException("FMVHD cache must not traverse symbolic links");
    }
    Files.createDirectories(absolute);
    final Path file = absolute.resolve(digest + ".mp4");
    if(Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      if(!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("FMVHD cache target is not a regular file");
      if(Files.size(file) == size && sha256(file).equals(digest)) return file;
    }
    final Path temporary = Files.createTempFile(absolute, "video-", ".partial");
    try {
      try(final var out = Files.newOutputStream(temporary)) {
        final byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while((count = resource.read(buffer)) != -1) {
          total += count;
          if(total > size) throw new IOException("FMVHD resource exceeds declared size");
          out.write(buffer, 0, count);
        }
        if(total != size) throw new IOException("Truncated FMVHD resource");
      }
      if(!sha256(temporary).equals(digest)) throw new IOException("FMVHD resource checksum mismatch");
      // If atomic replacement is unsupported, retain the original cinematic instead of weakening publication.
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return file;
    } finally { Files.deleteIfExists(temporary); }
  }

  public static String sha256(final Path path) throws IOException {
    final MessageDigest digest;
    try { digest = MessageDigest.getInstance("SHA-256"); } catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
    try(final InputStream in = Files.newInputStream(path)) {
      final byte[] bytes = new byte[65536];
      int count;
      while((count = in.read(bytes)) != -1) digest.update(bytes, 0, count);
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
