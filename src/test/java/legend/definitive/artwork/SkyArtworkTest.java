package legend.definitive.artwork;

import legend.game.modding.events.battle.BattleSkyTextureEvent;
import legend.game.textures.Image;
import legend.game.types.McqHeader;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SkyArtworkTest {
  private static byte[] source() {
    final var b = ByteBuffer.allocate(40 + 16 * 64 * 2).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(0, 0x151434d); b.putInt(4, 40);
    b.putShort(8, (short)16); b.putShort(10, (short)64); b.putShort(14, (short)40);
    b.putShort(20, (short)16); b.putShort(22, (short)32);
    for(int y = 0; y < 32; y++) for(int x = 0; x < 4; x++) b.putShort(40 + (y * 16 + x) * 2, (short)(y < 16 ? 0x1111 : 0x2222));
    b.putShort(40, (short)0x1110); // zero palette entry is a hole; next is visible black
    b.putShort(40 + (40 * 16 + 1) * 2, (short)0x8000);
    b.putShort(40 + (41 * 16 + 2) * 2, (short)31);
    return b.array();
  }
  private static byte[] png() throws Exception {
    final var im = new java.awt.image.BufferedImage(32, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
    im.setRGB(2, 0, 0xff123456);
    final var out = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(im, "png", out); return out.toByteArray();
  }
  private static byte[] manifest(final byte[] png, final String status) {
    return ("{\"sourceMcqSha256\":\"" + legend.definitive.textures.TexturePilot.sha256(source()) + "\",\"outputSha256\":\"" + legend.definitive.textures.TexturePilot.sha256(png) + "\",\"scale\":2,\"reviewStatus\":\"" + status + "\"}").getBytes(StandardCharsets.UTF_8);
  }
  @Test void columnMajorTilesUseTheirOwnPaletteAndPreserveVisibleBlack() throws Exception {
    final Image im = SkySource.decode(source());
    assertEquals(16, im.width); assertEquals(32, im.height);
    assertArrayEquals(new byte[4], java.util.Arrays.copyOfRange(im.data, 0, 4));
    assertArrayEquals(new byte[]{0, 0, 0, -1}, java.util.Arrays.copyOfRange(im.data, 4, 8));
    final int p = 16 * 16 * 4;
    assertArrayEquals(new byte[]{(byte)248, 0, 0, -1}, java.util.Arrays.copyOfRange(im.data, p, p + 4));
  }
  @Test void reviewHashAndLayoutGateArtworkWhileRgbGetsOpaqueAlpha() throws Exception {
    final byte[] png = png();
    final Image im = SkyImage.read(manifest(png, "visual-reviewed-native-pending"), png, source());
    assertArrayEquals(new byte[8], java.util.Arrays.copyOfRange(im.data, 0, 8));
    assertArrayEquals(new byte[]{0x12, 0x34, 0x56, -1}, java.util.Arrays.copyOfRange(im.data, 8, 12));
    assertThrows(java.io.IOException.class, () -> SkyImage.read(manifest(png, "revision-needed"), png, source()));
    final byte[] wrongSource = source(); wrongSource[24] = 1;
    assertThrows(java.io.IOException.class, () -> SkyImage.read(manifest(png, "native-accepted"), png, wrongSource));
    final byte[] wrongPng = png.clone(); wrongPng[wrongPng.length - 1] ^= 1;
    assertThrows(java.io.IOException.class, () -> SkyImage.read(manifest(png, "native-accepted"), wrongPng, source()));
    assertThrows(java.io.IOException.class, () -> SkySource.applyCoverage(source(), new Image(new byte[32 * 32 * 4], 32, 32)));
    final String duplicate = new String(manifest(png, "native-accepted"), StandardCharsets.UTF_8).replace("\"scale\":2", "\"scale\":2,\"scale\":2");
    assertThrows(java.io.IOException.class, () -> SkyImage.read(duplicate.getBytes(StandardCharsets.UTF_8), png, source()));
  }
  @Test void truncatedOutOfBoundsAndSkippedSmallSourcesFailClosed() {
    assertThrows(java.io.IOException.class, () -> SkySource.decode(new byte[8]));
    final byte[] small = source(); small[20] = 8;
    assertThrows(java.io.IOException.class, () -> SkySource.decode(small));
    final byte[] out = source(); out[12] = 8;
    assertThrows(java.io.IOException.class, () -> SkySource.decode(out));
    final byte[] truncated = java.util.Arrays.copyOf(source(), source().length - 1);
    assertThrows(java.io.IOException.class, () -> SkySource.decode(truncated));
  }
  @Test void originalDataAndEventInputAreSnapshots() {
    final byte[] data = source(); final McqHeader header = new McqHeader(new FileData(data));
    final var event = new BattleSkyTextureEvent(data);
    data[24] = 77; assertEquals(0, header.source()[24]); assertEquals(0, event.source()[24]);
    final byte[] returned = event.source(); returned[24] = 88; assertEquals(0, event.source()[24]);
  }
}
