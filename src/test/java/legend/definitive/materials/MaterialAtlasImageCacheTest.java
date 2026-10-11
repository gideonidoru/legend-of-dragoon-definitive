package legend.definitive.materials;

import legend.core.GameEngine;
import legend.core.renderer.Texture;
import legend.definitive.rendering.PngAssets;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import com.google.gson.Gson;
import legend.definitive.textures.TexturePilot;

import static org.junit.jupiter.api.Assertions.*;

class MaterialAtlasImageCacheTest {
  @TempDir Path temporary;

  private static void caching(final boolean enabled) throws Exception {
    if(CoreMod.IMAGE_CACHE_CONFIG.isValid()) {
      final var setter = ConfigCollection.class.getDeclaredMethod("setConfigQuietly", ConfigEntry.class, Object.class);
      setter.setAccessible(true);
      setter.invoke(GameEngine.CONFIG, CoreMod.IMAGE_CACHE_CONFIG.get(), enabled);
    }
    PngAssets.SHARED.retention(enabled);
  }

  private static byte[] pixels(final ByteBuffer buffer) {
    final byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }

  @Test void repeatedMaterialPreparationReusesDecodedPixelsAndOwnsUploadStorage() throws Exception {
    final boolean previous = Texture.imageCachingEnabled();
    try {
      caching(true);
      PngAssets.SHARED.clear();
      final var fixture = MaterialAtlasFixture.create(this.temporary, 4);
      final var before = PngAssets.SHARED.stats();
      final var first = MaterialAtlasFixture.read(fixture);
      final var second = MaterialAtlasFixture.read(fixture);
      assertEquals(before.misses() + 1, PngAssets.SHARED.stats().misses(), "One decode for repeated material preparation");
      assertEquals(before.hits() + 1, PngAssets.SHARED.stats().hits(), "Reuse the existing bounded image cache");
      assertTrue(first.rgba().isDirect());
      assertTrue(second.rgba().isReadOnly());
      assertArrayEquals(pixels(first.rgba()), pixels(second.rgba()));
      first.rgba().position(4);
      assertEquals(0, second.rgba().position());
      PngAssets.SHARED.clear();
      assertEquals(0, PngAssets.SHARED.stats().liveDecodedBytes(), "No decoder lease survives preparation");
      assertArrayEquals(pixels(first.rgba()), pixels(second.rgba()), "Upload buffers survive cache retirement");
    } finally { caching(previous); }
  }

  @Test void cacheDisabledMaterialPreparationRetainsNoDecodedPixels() throws Exception {
    final boolean previous = Texture.imageCachingEnabled();
    try {
      caching(false);
      final var fixture = MaterialAtlasFixture.create(this.temporary, 2);
      assertArrayEquals(pixels(MaterialAtlasFixture.read(fixture).rgba()), pixels(MaterialAtlasFixture.read(fixture).rgba()));
      assertEquals(0, PngAssets.SHARED.stats().cachedBytes());
      assertEquals(0, PngAssets.SHARED.stats().liveDecodedBytes());
      assertEquals(0, PngAssets.UNCACHED.stats().liveDecodedBytes());
    } finally { caching(previous); }
  }

  @Test void nativeUploadBytesMatchTheFullPngOracleIncludingTransparentColours() throws Exception {
    for(final int scale : new int[] {2, 4}) {
      final var fixture = MaterialAtlasFixture.create(this.temporary, scale);
      final var oracle = ImageIO.read(fixture.folder().resolve("atlas-engine-stp.png").toFile());
      final var atlas = MaterialAtlasFixture.read(fixture);
      final var actual = atlas.rgba();
      for(int y = 0; y < atlas.height(); y++) for(int x = 0; x < atlas.width(); x++) {
        final int expected = oracle.getRGB(x, y), offset = (y * atlas.width() + x) * 4;
        assertEquals((byte)(expected >>> 16), actual.get(offset));
        assertEquals((byte)(expected >>> 8), actual.get(offset + 1));
        assertEquals((byte)expected, actual.get(offset + 2));
        assertEquals((byte)(expected >>> 24), actual.get(offset + 3));
      }
    }
  }

  @Test void cachedPixelsStillRequireCurrentSourceCoverageValidation() throws Exception {
    final boolean previous = Texture.imageCachingEnabled();
    try {
      caching(true);
      PngAssets.SHARED.clear();
      final var fixture = MaterialAtlasFixture.create(this.temporary, 2);
      fixture.manifest().addProperty("algorithm", "neural");
      fixture.manifest().addProperty("strength", 0.5);
      fixture.manifest().addProperty("weightsSha256", "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d");
      fixture.manifest().addProperty("paramsSha256", "b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04");
      final var region = SourceMaterials.layout(fixture.model(), fixture.tim(), 2).regions().get(0);
      final Path png = fixture.folder().resolve("atlas-engine-stp.png");
      final var image = ImageIO.read(png.toFile());
      image.setRGB(region.x() + 18, region.y() + 16, 0x0000ff00);
      assertTrue(ImageIO.write(image, "png", png.toFile()));
      fixture.manifest().addProperty("atlasEngineSha256", TexturePilot.sha256(Files.readAllBytes(png)));
      MaterialAtlasFixture.save(fixture);
      assertNotNull(MaterialAtlasFixture.read(fixture));
      final long hits = PngAssets.SHARED.stats().hits();
      fixture.manifest().add("preservedPalettes", new Gson().toJsonTree(List.of(0)));
      MaterialAtlasFixture.save(fixture);
      assertThrows(IOException.class, () -> MaterialAtlasFixture.read(fixture));
      assertEquals(hits + 1, PngAssets.SHARED.stats().hits(), "Rejected source checks still run on a cache hit");
      PngAssets.SHARED.clear();
      assertEquals(0, PngAssets.SHARED.stats().liveDecodedBytes(), "Failed validation retains no decoder lease");
    } finally { caching(previous); }
  }
}
