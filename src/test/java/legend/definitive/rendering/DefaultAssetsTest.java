package legend.definitive.rendering;

import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureLoader;
import legend.game.modding.events.submap.SubmapEnvironmentPreloadEvent;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DefaultAssetsTest {
  @Test void neutralSurfaceMapsAreDeterministicAndBounded() {
    for(final boolean normal : new boolean[] {true,false}) {
      final ByteBuffer pixels=DefaultMaterialMaps.pixels(normal);
      assertEquals(pixels,DefaultMaterialMaps.pixels(normal));
      assertEquals(DefaultMaterialMaps.SIZE*DefaultMaterialMaps.SIZE*4,pixels.remaining());
      int minimum=255, maximum=0;
      while(pixels.hasRemaining()) {
        final int r=pixels.get()&255,g=pixels.get()&255,b=pixels.get()&255,a=pixels.get()&255;
        assertEquals(255,a);
        if(normal) { assertTrue(r>=110 && r<=145); assertTrue(g>=110 && g<=145); assertTrue(b>=250); }
        else { assertEquals(r,g); assertEquals(g,b); assertTrue(r>=70 && r<=185); }
        minimum=Math.min(minimum,r); maximum=Math.max(maximum,r);
      }
      assertTrue(maximum>minimum);
    }
  }
  @Test void sceneProfilesFollowOriginalHueDirectionAndScriptedChanges() {
    final Matrix3f directions=new Matrix3f().zero().m10(-1), colours=new Matrix3f().zero().m00(.8f).m01(.3f).m02(.1f);
    final var warm=NativeSceneLighting.profile(directions,colours,new Vector3f(.1f,.1f,.12f));
    assertEquals(-1,warm.y()); assertEquals(.8f,warm.r()); assertEquals(.1f,warm.b());
    assertEquals(NativeSceneLighting.INFLUENCE,warm.influence());
    colours.zero().m00(.1f).m01(.3f).m02(.8f);
    final var cool=NativeSceneLighting.profile(directions,colours,new Vector3f(.1f));
    assertTrue(cool.b()>cool.r()); assertNotEquals(warm,cool);
    colours.zero(); assertSame(EnvironmentLight.NONE,NativeSceneLighting.profile(directions,colours,new Vector3f()));
    final var ambient=NativeSceneLighting.profile(directions,colours,new Vector3f(.2f,.1f,.3f));
    assertEquals(0,ambient.r()); assertEquals(.3f,ambient.ambientB());
    directions.m00(Float.NaN); assertSame(EnvironmentLight.NONE,NativeSceneLighting.profile(directions,colours,new Vector3f(.2f)));
  }
  @Test void dominantNativeKeyRetainsMagnitudeAndBoundedHue() {
    final Matrix3f directions=new Matrix3f().zero().m00(2).m11(1), colours=new Matrix3f().zero().m00(2).m01(1).m02(.5f);
    final var light=NativeSceneLighting.profile(directions,colours,new Vector3f(2,-1,.2f));
    assertEquals(1,light.x()); assertEquals(1,light.r()); assertEquals(.5f,light.g()); assertEquals(.25f,light.b());
    assertEquals(1,light.ambientR()); assertEquals(0,light.ambientG());
  }
  @Test void opposingKeysFadeContinuouslyInsteadOfSwitchingAbruptly() {
    final Matrix3f directions=new Matrix3f().zero().m00(1).m11(-1), colours=new Matrix3f().zero().m00(1).m10(1);
    // Both rows point along X in opposite directions.
    directions.m11(0).m01(-1);
    assertSame(EnvironmentLight.NONE,NativeSceneLighting.profile(directions,colours,new Vector3f(.1f)));
    colours.m10(.99f);
    final var before=NativeSceneLighting.profile(directions,colours,new Vector3f(.1f));
    assertTrue(before.influence()<.002f); assertTrue(before.x()>0);
    colours.m10(1.01f);
    final var after=NativeSceneLighting.profile(directions,colours,new Vector3f(.1f));
    assertTrue(after.influence()<.002f); assertTrue(after.x()<0);
  }
  private static Tim tim() {
    final ByteBuffer bytes=ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0x10).putInt(8).putInt(44).putShort((short)0).putShort((short)0).putShort((short)16).putShort((short)1);
    for(int i=0;i<16;i++) bytes.putShort((short)(i==0 ? 0 : i==2 ? 0x8000 : i==1 ? 31 : 992));
    bytes.putInt(16).putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)2).putShort((short)0x3210).putShort((short)0x0123);
    return new Tim(new FileData(bytes.array()));
  }
  @Test void firstVisitNativeExpansionMatchesOriginalPixelsAndForegroundClipping() throws Exception {
    final Tim source=tim(); final byte[] original=source.getData().getBytes();
    final var tiles=new NativeEnvironmentImages.Tile[] {new NativeEnvironmentImages.Tile(source,0,0,4,2,false),new NativeEnvironmentImages.Tile(source,1,0,3,99,true),null};
    final var prepared=NativeEnvironmentImages.prewarm(tiles).get(10,TimeUnit.SECONDS);
    final int[] reference=VramTextureLoader.textureFromTim(source).applyPalette(VramTextureLoader.palettesFromTim(source)[0],new Rect4i(0,0,4,2));
    for(int i=0;i<reference.length;i++) if(reference[i] != 0) reference[i] |= 0xff000000;
    assertArrayEquals(reference,prepared[0].rgba()); assertEquals(0,prepared[0].rgba()[0]); assertEquals(0xff000000,prepared[0].rgba()[2]);
    assertEquals(3,prepared[1].width()); assertEquals(2,prepared[1].height()); assertNull(prepared[2]);
    assertArrayEquals(original,source.getData().getBytes());
    assertArrayEquals(prepared[0].rgba(),NativeEnvironmentImages.decode(tiles)[0].rgba());
    assertThrows(IllegalArgumentException.class,()->NativeEnvironmentImages.decode(new NativeEnvironmentImages.Tile[33]));
    assertThrows(IllegalArgumentException.class,()->NativeEnvironmentImages.decode(new NativeEnvironmentImages.Tile[] {new NativeEnvironmentImages.Tile(source,0,0,99,99,false)}));
  }
  @Test void artworkHintsHonorAliasesAndRetainedMemoryBudget() {
    assertTrue(DefaultBackgroundPrewarming.resources(1,15).getFirst().contains("cut37/"));
    assertTrue(DefaultBackgroundPrewarming.resources(1,56).getFirst().contains("cut14/"));
    assertEquals(3,DefaultBackgroundPrewarming.resources(1,31).size());
    assertTrue(DefaultBackgroundPrewarming.resources(99,99999).isEmpty());
  }
  @Test void loadingStageAwaitsHintsAndFailsOpenForMissingArtwork() throws Exception {
    final var event=new SubmapEnvironmentPreloadEvent(null,null,null,1,31);
    final CompletableFuture<Boolean> hint=new CompletableFuture<>(); event.waitFor(hint);
    final var ready=event.preparation(); assertFalse(ready.isDone());
    hint.completeExceptionally(new IllegalArgumentException("Missing artwork")); assertTrue(ready.isDone()); assertDoesNotThrow(ready::join);
    final var canceled=new CompletableFuture<Boolean>(); final var second=new SubmapEnvironmentPreloadEvent(null,null,null,1,31);
    second.waitFor(canceled); final var secondReady=second.preparation(); canceled.cancel(false); assertDoesNotThrow(secondReady::join);
    final var stalled=new CompletableFuture<Boolean>(); final var third=new SubmapEnvironmentPreloadEvent(null,null,null,1,31);
    third.waitFor(stalled); third.preparation().get(7,TimeUnit.SECONDS); assertFalse(stalled.isDone());
  }
  @Test void disabledRetentionCannotBeRepopulatedByLatePrewarming() throws Exception {
    try(final var cache=new PngAssets(64)) {
      cache.retention(false);
      final byte[] encoded=PngAssetsTest.png(1,2,3,255);
      cache.prewarm(()->new java.io.ByteArrayInputStream(encoded)).get(10,TimeUnit.SECONDS);
      assertEquals(0,cache.stats().cachedBytes()); assertEquals(0,cache.stats().liveDecodedBytes());
      cache.retention(true);
      try(final var image=cache.acquire(ByteBuffer.wrap(PngAssetsTest.png(1,2,3,255)))) { assertEquals(4,cache.stats().cachedBytes()); }
    }
  }
}
