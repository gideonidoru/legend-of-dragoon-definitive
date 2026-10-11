package legend.definitive.artwork;
import legend.core.gpu.Rect4i;
import legend.definitive.rendering.NativeEnvironmentImages.Segment;
import legend.game.textures.Image;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;
class FieldImagesTest {
  @Test void transparentBackgroundOverwritesAndForegroundKeepsIndependentSlotAndPlacement() {
    final var s=FieldImages.compose(new Rect4i(-2,-1,4,3),2,new Segment[]{new Segment(2,1,new int[]{0xff0000f8,0xff0000f8}),new Segment(1,1,new int[]{0}),null,new Segment(1,1,new int[]{0xff00f800})},new int[][]{{-2,-1},{-1,-1},null,{1,1}});
    assertArrayEquals(new byte[]{(byte)248,0,0,-1,0,0,0,0},java.util.Arrays.copyOf(s.background().data,8));
    assertEquals(2,s.foregrounds().length);assertNull(s.foregrounds()[0]);
    assertEquals(1,s.foregrounds()[1].placement().x);assertEquals(1,s.foregrounds()[1].placement().y);
    assertArrayEquals(new byte[]{0,(byte)248,0,-1},s.foregrounds()[1].image().data);
    final var bytes=ByteBuffer.allocate(8+s.background().data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(4).putInt(3).put(s.background().data);
    assertEquals(legend.definitive.textures.TexturePilot.sha256(bytes.array()),FieldImages.fingerprint(s.background()));
  }
  @Test void scaleVisibilityAndVisibleBlackMustMatchEachNativeTexel() throws Exception {
    final Image source=new Image(new byte[]{0,0,0,0,0,0,0,-1,31,0,0,-1},3,1);
    final byte[] rgba=new byte[12*4*4];for(int y=0;y<4;y++) for(int x=0;x<12;x++) System.arraycopy(source.data,x/4*4,rgba,(y*12+x)*4,4);
    FieldImages.validate(source,new Image(rgba,12,4));
    for(int[] change:new int[][]{{3,255},{16,1},{32,0}}) {
      final byte[] altered=rgba.clone();altered[change[0]]=(byte)change[1];
      assertThrows(IOException.class,()->FieldImages.validate(source,new Image(altered,12,4)));
    }
    assertThrows(IOException.class,()->FieldImages.validate(source,new Image(rgba,6,8)));
  }
}
