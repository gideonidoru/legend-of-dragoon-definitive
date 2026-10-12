package legend.definitive.font;

import legend.game.textures.NativeUiTextureEvent;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

/** Oracle controls must fail independently when word order, crop bounds or CLUT rows differ. */
class UiHdPreparedFontCatalogProbeTest {
  private static Tim source() {
    final var data = ByteBuffer.allocate(8 + 12 + 6 * 32 + 12 + 4).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(16).putInt(8).putInt(12 + 6 * 32).putShort((short)0).putShort((short)0).putShort((short)16).putShort((short)6);
    data.position(8 + 12 + 6 * 32); data.putInt(16).putShort((short)0).putShort((short)0).putShort((short)2).putShort((short)1).putShort((short)0x3210).putShort((short)0xfedc);
    return new Tim(new FileData(data.array()));
  }
  @Test void independentWordOracleHandlesOddCropsAcrossWordBoundaries() {
    final var indices = UiHdPreparedFontCatalogProbe.sourceIndices(source(), 3, 0, 4, 1);
    assertArrayEquals(new byte[]{3, 0, 0, -1, 12, 0, 0, -1, 13, 0, 0, -1, 14, 0, 0, -1}, indices.data);
  }
  @Test void independentOracleRejectsOutOfBoundsAndEmptyCrops() {
    assertThrows(AssertionError.class, () -> UiHdPreparedFontCatalogProbe.sourceIndices(source(), 7, 0, 2, 1));
    assertThrows(AssertionError.class, () -> UiHdPreparedFontCatalogProbe.sourceIndices(source(), 0, 1, 1, 1));
    assertThrows(AssertionError.class, () -> UiHdPreparedFontCatalogProbe.sourceIndices(source(), 0, 0, 0, 1));
  }
  @Test void physicalPaletteAliasUsesColumnAndRowRatherThanPackedConstant() {
    final var event = new NativeUiTextureEvent("basic", source(), 832, 256, 896, 480, 4);
    final var entry = new JSONObject().put("crop", new org.json.JSONArray(new int[]{0, 0, 8, 1})).put("palette", 5);
    final var quad = UiHdPreparedFontCatalogProbe.quad(event, entry);
    assertEquals(912, quad.clutX()); assertEquals(481, quad.clutY());
    assertNotEquals(485, quad.clutY(), "Changing only row would sample another source bank");
  }
  @Test void physicalPageBoundaryPreservesAbsolutePixelCoordinates() {
    final var event = new NativeUiTextureEvent("menu", source(), 64, 256, 144, 496, 15);
    final var entry = new JSONObject().put("crop", new org.json.JSONArray(new int[]{256, 0, 8, 1})).put("palette", 14);
    final var quad = UiHdPreparedFontCatalogProbe.quad(event, entry);
    assertEquals(128, quad.pageX()); assertEquals(0, quad.u()); assertEquals(510, quad.clutY());
  }
}
