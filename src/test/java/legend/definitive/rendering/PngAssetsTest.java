package legend.definitive.rendering;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PngAssetsTest {
  static byte[] png(final int r, final int g, final int b, final int a) throws IOException {
    final var bytes=new ByteArrayOutputStream(); final var out=new DataOutputStream(bytes);
    out.writeLong(0x89504e470d0a1a0aL);
    final var header=new ByteArrayOutputStream(); final var h=new DataOutputStream(header);
    h.writeInt(1); h.writeInt(1); h.write(new byte[] {8,6,0,0,0}); chunk(out,"IHDR",header.toByteArray());
    final var raw=new ByteArrayOutputStream();
    try(final var compressed=new DeflaterOutputStream(raw)) { compressed.write(new byte[] {0,(byte)r,(byte)g,(byte)b,(byte)a}); }
    chunk(out,"IDAT",raw.toByteArray()); chunk(out,"IEND",new byte[0]); return bytes.toByteArray();
  }
  private static void chunk(final DataOutputStream out, final String type, final byte[] data) throws IOException {
    final byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII); final CRC32 crc=new CRC32(); crc.update(name); crc.update(data);
    out.writeInt(data.length); out.write(name); out.write(data); out.writeInt((int)crc.getValue());
  }
  @Test void exactPixelsAlphaAndCallerPositionsSurviveCacheHits() throws Exception {
    try(final var assets=new PngAssets(4)) {
      final ByteBuffer encoded=ByteBuffer.wrap(png(20,40,80,0));
      try(final var first=assets.acquire(encoded); final var second=assets.acquire(encoded)) {
        assertEquals(0,encoded.position()); assertEquals(1,first.width()); assertEquals(1,first.height());
        assertEquals(first.pixels(),second.pixels()); assertTrue(first.pixels().isReadOnly());
        assertEquals(20,first.pixels().get(0)&255); assertEquals(0,first.pixels().get(3));
      }
      assertEquals(1,assets.stats().misses()); assertEquals(1,assets.stats().hits()); assertEquals(4,assets.stats().liveDecodedBytes());
      assets.clear(); assertEquals(0,assets.stats().liveDecodedBytes());
    }
  }
  @Test void evictionRetainsActiveUploadPixelsUntilTheirLeaseCloses() throws Exception {
    try(final var assets=new PngAssets(4)) {
      final var first=assets.acquire(ByteBuffer.wrap(png(255,0,0,255)));
      try(final var second=assets.acquire(ByteBuffer.wrap(png(0,255,0,255)))) {
        assertEquals(4,assets.stats().cachedBytes()); assertEquals(8,assets.stats().liveDecodedBytes());
        assertEquals(255,first.pixels().get(0)&255); assertEquals(255,second.pixels().get(1)&255);
        first.close(); first.close(); assertEquals(4,assets.stats().liveDecodedBytes()); assertEquals(1,assets.stats().evictions());
        assets.clear(); assertEquals(4,assets.stats().liveDecodedBytes());
      }
      assertEquals(0,assets.stats().liveDecodedBytes());
    }
  }
  @Test void prewarmingIsReusableAndFailuresDoNotPoisonLaterLoads() throws Exception {
    try(final var assets=new PngAssets(4)) {
      final byte[] encoded=png(1,2,3,255);
      assertTrue(assets.prewarm(() -> new ByteArrayInputStream(encoded)).get(5,TimeUnit.SECONDS));
      try(final var image=assets.acquire(ByteBuffer.wrap(encoded))) { assertEquals(2,image.pixels().get(1)); }
      assertEquals(1,assets.stats().hits());
      assertThrows(java.util.concurrent.ExecutionException.class,()->assets.prewarm(()->new ByteArrayInputStream(new byte[] {1,2,3})).get(5,TimeUnit.SECONDS));
      assertThrows(IllegalArgumentException.class,()->assets.acquire(ByteBuffer.wrap(new byte[] {1,2,3})));
      try(final var image=assets.acquire(ByteBuffer.wrap(encoded))) { assertEquals(3,image.pixels().get(2)); }
    }
  }
  @Test void zeroBudgetAndClosingWhilePixelsAreInUseReleaseMemoryExactly() throws Exception {
    final var assets=new PngAssets(0); final var image=assets.acquire(ByteBuffer.wrap(png(1,2,3,255)));
    assertEquals(0,assets.stats().cachedBytes()); assertEquals(4,assets.stats().liveDecodedBytes());
    assets.close(); assertEquals(1,image.pixels().get(0)); image.close(); assertEquals(0,assets.stats().liveDecodedBytes());
    assertThrows(IllegalStateException.class,()->assets.acquire(ByteBuffer.wrap(png(1,2,3,255))));
  }
}
