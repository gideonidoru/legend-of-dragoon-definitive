package legend.definitive.artwork;

import legend.game.textures.Image;
import legend.game.textures.NativeUiTextures;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class UiPaletteDetailTest {
  private static Tim source() {
    final var bytes = ByteBuffer.allocate(66).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8).putInt(44).putShort((short)0).putShort((short)300).putShort((short)16).putShort((short)1);
    // Index zero is visible; index one is discard; index two is visible black/STP.
    for(int i = 0; i < 16; i++) bytes.putShort((short)(i == 0 ? 0x801f : i == 1 ? 0 : i == 2 ? 0x8000 : 0x3e0));
    bytes.putInt(14).putShort((short)4).putShort((short)10).putShort((short)1).putShort((short)1).putShort((short)0x3210);
    return new Tim(new FileData(bytes.array()));
  }

  @Test void referenceKeepsExactIndicesAndNativeBlackAndStpClasses() {
    final var tim = source();
    final var original = NativeUiTextures.decode(tim, 0, 0, 0, 4, 1);
    final var reference = NativeUiTextures.paletteDetailReference(tim, 0, 0, 0, 4, 1);
    assertDoesNotThrow(() -> NativeUiTextures.validatePaletteDetailReference(original, reference));
    for(int i = 0; i < 4; i++) assertEquals(i, Byte.toUnsignedInt(reference.data[i * 4]));
    assertEquals(255, Byte.toUnsignedInt(reference.data[1]));
    assertEquals(255, Byte.toUnsignedInt(reference.data[3]));
    assertEquals(0, reference.data[5]); assertEquals(0, reference.data[7]);
    assertEquals(0, reference.data[9]); assertEquals(255, Byte.toUnsignedInt(reference.data[11]));
    assertEquals(255, Byte.toUnsignedInt(reference.data[13])); assertEquals(0, reference.data[15]);
  }

  @Test void rejectsMalformedReferenceRatherThanUploadingUntrustedControl() {
    final var original = NativeUiTextures.decode(source(), 0, 0, 0, 4, 1);
    for(int channel = 0; channel < 4; channel++) {
      final var reference = NativeUiTextures.paletteDetailReference(source(), 0, 0, 0, 4, 1);
      reference.data[channel] = (byte)(channel == 0 ? 16 : channel == 1 ? 1 : channel == 2 ? 1 : 0);
      assertThrows(IllegalArgumentException.class, () -> NativeUiTextures.validatePaletteDetailReference(original, reference));
    }
    assertThrows(IllegalArgumentException.class, () -> NativeUiTextures.validatePaletteDetailReference(original, new Image(new byte[8], 2, 1)));
  }

  @Test void referenceCropTracksNibblesWithoutChangingTheTim() {
    final var tim = source(); final byte[] before = tim.getData().getBytes().clone();
    final var reference = NativeUiTextures.paletteDetailReference(tim, 0, 1, 0, 2, 1);
    assertEquals(1, reference.data[0]); assertEquals(2, reference.data[4]);
    assertArrayEquals(before, tim.getData().getBytes());
    assertThrows(IllegalArgumentException.class, () -> NativeUiTextures.paletteDetailReference(tim, 0, 3, 0, 2, 1));
  }
}
