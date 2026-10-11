package legend.definitive.artwork;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;
class BattleMaterialSourceTest {
  private static byte[] overlappingTim() {
    final var bytes=ByteBuffer.allocate(66).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8).putInt(44).putShort((short)64).putShort((short)0).putShort((short)16).putShort((short)1);
    for(int i=0;i<16;i++) bytes.putShort((short)(i==0?0x8001:i==1?0x801f:i==8?0x83e0:0));
    bytes.putInt(14).putShort((short)64).putShort((short)0).putShort((short)1).putShort((short)1).putShort((short)0);
    return bytes.array();
  }
  @Test void actualPaletteUploadOverwritesIntersectingImageWords() throws Exception {
    final var image=BattleMaterialAtlas.decodeSource(overlappingTim(),1,4);
    assertEquals(4,image.width);assertEquals(1,image.height);
    assertArrayEquals(new byte[]{-1,0,0,-1,8,0,0,-1,8,0,0,-1,0,-1,0,-1},image.data);
  }
  @Test void uninitializedPaletteAndMalformedSourceReject() {
    assertThrows(IOException.class,()->BattleMaterialAtlas.decodeSource(overlappingTim(),1,5));
    final byte[] trailing=java.util.Arrays.copyOf(overlappingTim(),67);
    assertThrows(IOException.class,()->BattleMaterialAtlas.decodeSource(trailing,1,4));
    assertThrows(IOException.class,()->BattleMaterialAtlas.decodeSource(new byte[0],1,4));
  }
}
