package legend.definitive.artwork;

import legend.definitive.materials.MaterialAtlas;
import legend.game.textures.Image;
import legend.game.textures.ReplaceAtlasTexturesEvent;
import org.junit.jupiter.api.Test;
import org.legendofdragoon.modloader.registries.RegistryId;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkContractsTest {
  @Test void packedUvSeparatesPalettesAndRetainsFractionalPosition() {
    final var a = new MaterialAtlas.Region(0, -8, -8, 24, 24, 0, 0, 128, 128);
    final var b = new MaterialAtlas.Region(4, -8, -8, 24, 24, 128, 0, 128, 128);
    assertArrayEquals(new float[]{0.1875f, 0.375f}, MaterialUv.coordinates(a, 4, 256, 128, 4, 4));
    assertArrayEquals(new float[]{0.6875f, 0.375f}, MaterialUv.coordinates(b, 4, 256, 128, 4, 4));
    assertTrue(MaterialUv.coordinates(a, 4, 256, 128, 3.99f, 4)[0] < 0.1875f);
  }
  @Test void uiReplacementRetainsAnEarlierModAndDoesNotCreateMissingEntries() {
    final var map = new HashMap<RegistryId, Image>(); final var id = new RegistryId("lod", "stone");
    final var original = new Image(new byte[]{1, 2, 3, -1}, 1, 1);
    final var other = new Image(new byte[]{4, 5, 6, -1}, 1, 1);
    final var candidate = new Image(new byte[16], 2, 2); final var event = new ReplaceAtlasTexturesEvent(map);
    assertFalse(event.replace(id, original, candidate));
    map.put(id, other); assertFalse(event.replace(id, original, candidate)); assertSame(other, map.get(id));
    map.put(id, original); assertTrue(event.replace(id, original, candidate)); assertSame(candidate, map.get(id));
  }
  @Test void boundedPngRejectsDimensionsBeforeDecodingAndRetainsTransparentRgb() throws Exception {
    final var image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0x00112233); image.setRGB(1, 0, 0xff000000);
    final var png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", png);
    assertThrows(java.io.IOException.class, () -> ArtworkResources.image(png.toByteArray(), 4096, 4096));
    assertThrows(java.io.IOException.class, () -> ArtworkResources.image(new byte[8], 2, 1));
    assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0, 0, 0, 0, -1}, ArtworkResources.image(png.toByteArray(), 2, 1).data);
    assertThrows(java.io.IOException.class, () -> ArtworkResources.hash(png.toByteArray(), "wrong"));
  }
  private static byte[] model(final int clut, final int page) {
    final var b = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(0, 12); b.putInt(12, 0x41); b.putInt(20, 1); b.putInt(40, 28); b.putInt(44, 1);
    b.putInt(52, 0x24000300); b.putShort(58, (short)clut); b.putShort(62, (short)page); return b.array();
  }
  private static byte[] tim() {
    final var b = ByteBuffer.allocate(2080).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(4, 8); b.putInt(8, 2060); b.putShort(12, (short)448); b.putShort(14, (short)240);
    b.putShort(16, (short)64); b.putShort(18, (short)16);
    b.putShort(2072, (short)448); b.putShort(2074, (short)0); b.putShort(2076, (short)64); b.putShort(2078, (short)256); return b.array();
  }
  @Test void stageReferencesRejectAnimationAndOtherVramPages() throws Exception {
    final int clut = 240 * 64 + 448 / 16;
    StageArtwork.validateReferences(model(clut, 7), tim());
    assertThrows(java.io.IOException.class, () -> StageArtwork.validateReferences(model(clut, 8), tim()));
    assertThrows(java.io.IOException.class, () -> StageArtwork.validateReferences(model(clut + 4, 7), tim()));
    final byte[] animated = model(clut, 7); animated[8] = 1;
    assertThrows(java.io.IOException.class, () -> StageArtwork.validateReferences(animated, tim()));
    assertThrows(java.io.IOException.class, () -> StageArtwork.validateReferences(new byte[6], tim()));
  }
}
