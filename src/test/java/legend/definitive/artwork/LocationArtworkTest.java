package legend.definitive.artwork;

import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureLoader;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import legend.game.textures.Image;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LocationArtworkTest {
  static byte[] source() {
    final var b = ByteBuffer.allocate(11344).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(0, 16); b.putInt(4, 9); b.putInt(8, 524);
    b.putShort(16, (short)256); b.putShort(18, (short)1);
    b.putInt(532, 10812); b.putShort(540, (short)60); b.putShort(542, (short)90);
    b.putShort(22, (short)0x8000); b.putShort(24, (short)31);
    for(int p = 0; p < 10800; p++) b.put(544 + p, (byte)(p % 3));
    return b.array();
  }
  @Test void sourcePixelsAgreeWithActualTimPaletteWalk() throws Exception {
    final byte[] source = source();
    final var tim = new Tim(new FileData(source));
    final var actual = VramTextureLoader.textureFromTim(tim).applyPalette(VramTextureLoader.palettesFromTim(tim)[0], new Rect4i(0, 0, 120, 90));
    final Image decoded = LocationArtwork.decode(source);
    for(int p = 0; p < actual.length; p++) {
      assertEquals(actual[p] & 255, decoded.data[p * 4] & 255);
      assertEquals(actual[p] >>> 8 & 255, decoded.data[p * 4 + 1] & 255);
      assertEquals(actual[p] >>> 16 & 255, decoded.data[p * 4 + 2] & 255);
      assertEquals(actual[p] == 0 ? 0 : 255, decoded.data[p * 4 + 3] & 255);
    }
  }
  @Test void badBoundsPaletteAndSourceSnapshotsFailClosed() throws Exception {
    assertThrows(java.io.IOException.class, () -> LocationArtwork.decode(new byte[11343]));
    for(final int offset : new int[]{0, 4, 8, 16, 18, 532, 540, 542}) {
      final byte[] bad = source(); bad[offset] ^= 1;
      assertThrows(java.io.IOException.class, () -> LocationArtwork.decode(bad));
    }
    final byte[] source = source();
    final var thumbnail = new legend.game.modding.events.wmap.LocationThumbnailTextureEvent(source);
    final var backdrop = new legend.game.modding.events.wmap.WorldBackdropTextureEvent(source);
    source[24] = 0; thumbnail.source()[24] = 0; backdrop.source()[24] = 0;
    assertEquals(31, thumbnail.source()[24]); assertEquals(31, backdrop.source()[24]);
  }
  @Test void sourceOutputReviewAndLayoutAreCheckedBeforeAllocation() throws Exception {
    final byte[] source = source();
    final var image = new java.awt.image.BufferedImage(480, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
    image.setRGB(8, 0, 0xffabcdef);
    final var stream = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", stream);
    final byte[] png = stream.toByteArray();
    final var metadata = new java.util.Properties();
    metadata.setProperty("sourceSha256", legend.definitive.textures.TexturePilot.sha256(source));
    metadata.setProperty("outputSha256", legend.definitive.textures.TexturePilot.sha256(png));
    metadata.setProperty("decodedRgbaSha256", SkySource.fingerprint(LocationArtwork.decode(source)));
    metadata.setProperty("scale", "4"); metadata.setProperty("reviewStatus", "visual-reviewed-native-pending");
    final Image loaded = LocationArtwork.read(metadata, png, source);
    assertEquals(0, loaded.data[3]); assertEquals(-1, loaded.data[4 * 4 + 3]);
    assertEquals(0xab, loaded.data[8 * 4] & 255);
    for(final String key : new String[]{"sourceSha256", "outputSha256", "decodedRgbaSha256", "scale", "reviewStatus"}) {
      final String previous = metadata.getProperty(key); metadata.setProperty(key, "wrong");
      assertThrows(java.io.IOException.class, () -> LocationArtwork.read(metadata, png, source));
      metadata.setProperty(key, previous);
    }
    assertThrows(java.io.IOException.class, () -> LocationArtwork.coverage(source, new Image(new byte[4], 1, 1)));
  }
}
