package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.definitive.rendering.PngAssets;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigEntry;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class ArtworkImageCacheTest {
  private static byte[] png(final boolean alpha) throws Exception {
    final var image = new BufferedImage(17, 11, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    for(int y = 0; y < 11; y++) for(int x = 0; x < 17; x++) {
      image.setRGB(x, y, (alpha && (x + y) % 2 == 0 ? 0 : 0xff000000) | (x * 13 << 16) | (y * 19 << 8) | (x * y));
    }
    final var bytes = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, "png", bytes));
    return bytes.toByteArray();
  }

  private static void caching(final boolean enabled) throws Exception {
    if(CoreMod.IMAGE_CACHE_CONFIG.isValid()) {
      final var setter = ConfigCollection.class.getDeclaredMethod("setConfigQuietly", ConfigEntry.class, Object.class);
      setter.setAccessible(true);
      setter.invoke(GameEngine.CONFIG, CoreMod.IMAGE_CACHE_CONFIG.get(), enabled);
    }
    PngAssets.SHARED.retention(enabled);
  }

  @Test void repeatedImageLoadsReuseTheEngineDecodeAndOwnTheirReturnedPixels() throws Exception {
    final boolean previous = legend.core.renderer.Texture.imageCachingEnabled();
    try {
      caching(true);
      PngAssets.SHARED.clear();
      final var before = PngAssets.SHARED.stats();
      final byte[] encoded = png(true);
      final var first = ArtworkResources.image(encoded, 17, 11);
      final var second = ArtworkResources.image(encoded, 17, 11);
      assertArrayEquals(first.data, second.data);
      assertNotSame(first.data, second.data);
      assertEquals(before.misses() + 1, PngAssets.SHARED.stats().misses(), "One actual decode for repeated artwork");
      assertEquals(before.hits() + 1, PngAssets.SHARED.stats().hits(), "Use the existing bounded engine image cache");
      final byte[] original = second.data.clone();
      Arrays.fill(first.data, (byte)99);
      assertArrayEquals(original, ArtworkResources.image(encoded, 17, 11).data, "Caller edits cannot mutate cached decoded pixels");
      PngAssets.SHARED.clear();
      assertEquals(0, PngAssets.SHARED.stats().liveDecodedBytes(), "All decoder leases released by image loading");
    } finally { caching(previous); }
  }

  @Test void disablingCachingDoesNotRetainArtworkDecodes() throws Exception {
    final boolean previous = legend.core.renderer.Texture.imageCachingEnabled();
    try {
      caching(false);
      final byte[] encoded = png(true);
      assertArrayEquals(ArtworkResources.image(encoded, 17, 11).data, ArtworkResources.image(encoded, 17, 11).data);
      assertEquals(0, PngAssets.SHARED.stats().cachedBytes());
      assertEquals(0, PngAssets.SHARED.stats().liveDecodedBytes());
      assertEquals(0, PngAssets.UNCACHED.stats().liveDecodedBytes());
    } finally { caching(previous); }
  }

  @Test void rgbAndTransparentRgbStpBytesMatchThePngPixelOracle() throws Exception {
    for(final boolean alpha : new boolean[] {false, true}) {
      final byte[] encoded = png(alpha);
      final var oracle = ImageIO.read(new java.io.ByteArrayInputStream(encoded));
      final var actual = ArtworkResources.image(encoded, 17, 11);
      for(int y = 0; y < 11; y++) for(int x = 0; x < 17; x++) {
        final int packed = oracle.getRGB(x, y), offset = (y * 17 + x) * 4;
        assertArrayEquals(new byte[] {(byte)(packed >>> 16), (byte)(packed >>> 8), (byte)packed, (byte)(packed >>> 24)},
          Arrays.copyOfRange(actual.data, offset, offset + 4));
      }
      assertThrows(java.io.IOException.class, () -> ArtworkResources.image(encoded, 17, 12));
      assertThrows(java.io.IOException.class, () -> ArtworkResources.image(Arrays.copyOf(encoded, 33), 17, 11));
    }
  }
}
