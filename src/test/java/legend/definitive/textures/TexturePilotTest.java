package legend.definitive.textures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original synthetic fixture, AGPL v3; no game imagery or extracted files. */
class TexturePilotTest {
  @TempDir Path temporary;
  private static byte[] tim() {
    final ByteBuffer data = ByteBuffer.allocate(8 + 12 + 18).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(0x10).putInt(2).putInt(30).putShort((short)0).putShort((short)0).putShort((short)3).putShort((short)3);
    for(final int colour : new int[]{0, 31, 0, 31, 0x8000, 31, 0, 31, 0}) data.putShort((short)colour);
    return data.array();
  }
  @Test void preservesStpAndOnlySelectsExistingColours() throws Exception {
    final TimImage image = TimImage.read(tim(), 0);
    assertEquals(0, image.pixels()[0]); assertEquals(0x00ff0000, image.pixels()[1]); assertEquals(0xff000000, image.pixels()[4]);
    final Set<Integer> colours = new HashSet<>(); for(final int colour : image.pixels()) colours.add(colour);
    final int[] scaled = TextureScaler.twice(image, false); assertEquals(36, scaled.length);
    for(final int colour : scaled) assertTrue(colours.contains(colour));
    assertFalse(Arrays.equals(TextureScaler.twice(image, true), scaled));
  }
  @Test void exactMappingHashesPixelsAndFallbackContract() throws Exception {
    final Path source = temporary.resolve("synthetic.tim"), output = temporary.resolve("pilot"); Files.write(source, tim());
    TexturePilot.main(new String[]{source.toString(), output.toString(), "0", "0", "6", "1"});
    assertNull(TexturePack.read(output, 0, 7, 1, tim()));
    final var replacement = TexturePack.read(output, 0, 6, 1, tim()); assertEquals(6, replacement.width()); assertEquals(144, replacement.rgba().remaining());
    assertThrows(java.io.IOException.class, () -> TexturePack.read(output, 0, 6, 1, new byte[20]));
    Files.writeString(output.resolve("scale2x-engine.png"), "bad");
    assertThrows(java.io.IOException.class, () -> TexturePack.read(output, 0, 6, 1, tim()));
    assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> TexturePilot.main(new String[]{source.toString(), output.toString(), "0"}));
  }
  @Test void rejectsTruncatedUnsupportedAndWrongPalettes() {
    assertThrows(java.io.IOException.class, () -> TimImage.read(Arrays.copyOf(tim(), 23), 0));
    assertThrows(java.io.IOException.class, () -> TimImage.read(tim(), 1));
    final byte[] bad = tim(); bad[4] = 3; assertThrows(java.io.IOException.class, () -> TimImage.read(bad, 0));
    final ByteBuffer indexed = ByteBuffer.allocate(8 + 44 + 14).order(ByteOrder.LITTLE_ENDIAN);
    indexed.putInt(0x10).putInt(8).putInt(44).putShort((short)0).putShort((short)0).putShort((short)16).putShort((short)1);
    for(int i = 0; i < 16; i++) indexed.putShort((short)i);
    indexed.putInt(14).putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)1).putShort((short)0x3210);
    assertDoesNotThrow(() -> { final var image = TimImage.read(indexed.array(), 0); assertEquals(4, image.width()); assertEquals(1, image.paletteCount()); });
    assertThrows(java.io.IOException.class, () -> TimImage.read(indexed.array(), 1));
  }
}
