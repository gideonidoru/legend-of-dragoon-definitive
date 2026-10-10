package legend.definitive.rendering;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/** Optional local asset measurements. No game boot, graphics context or asset publication. */
public final class AssetStreamingProbe {
  private static String hash(final ByteBuffer pixels) throws Exception {
    final var digest = MessageDigest.getInstance("SHA-256"); digest.update(pixels);
    return HexFormat.of().formatHex(digest.digest());
  }
  public static void main(final String[] args) throws Exception {
    for(final String arg : args) {
      final Path path = Path.of(arg);
      final byte[] bytes = Files.readAllBytes(path);
      final long[] cold = new long[12], warm = new long[12];
      String expected = null;
      int width = 0, height = 0;
      try(final PngAssets cache = new PngAssets(64L * 1024 * 1024)) {
        for(int i = 0; i < cold.length; i++) {
          cache.clear();
          long start = System.nanoTime();
          try(final var image = cache.acquire(ByteBuffer.wrap(bytes))) {
            cold[i] = System.nanoTime() - start;
            width = image.width(); height = image.height();
            final String actual = hash(image.pixels());
            if(expected == null) expected = actual;
            if(!expected.equals(actual)) throw new AssertionError("Cold pixels changed");
          }
          start = System.nanoTime();
          try(final var image = cache.acquire(ByteBuffer.wrap(bytes))) {
            warm[i] = System.nanoTime() - start;
            if(!expected.equals(hash(image.pixels()))) throw new AssertionError("Cached pixels changed");
          }
        }
        cache.clear();
        cache.prewarm(path).get(30, TimeUnit.SECONDS);
        final long hits = cache.stats().hits();
        try(final var image = cache.acquire(ByteBuffer.wrap(bytes))) {
          if(!expected.equals(hash(image.pixels())) || cache.stats().hits() != hits + 1) throw new AssertionError("Prewarm did not reuse exact pixels");
        }
        Arrays.sort(cold); Arrays.sort(warm);
        System.out.printf(java.util.Locale.ROOT, "%s %dx%d: cold median %.3f ms, cached median %.3f ms, cached p95 %.3f ms; RGBA SHA256 %s; %s%n",
          path.getFileName(), width, height, cold[6] / 1e6, warm[6] / 1e6, warm[11] / 1e6, expected, cache.stats());
        cache.clear();
        if(cache.stats().liveDecodedBytes() != 0) throw new AssertionError("Decoded pixels retained after clear");
      }
    }
  }
}
