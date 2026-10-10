package legend.definitive.artwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import legend.game.modding.events.wmap.WorldTerrainTextureEvent;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorldTerrainTest {
  static byte[] tim(final int bpp, final int colour) {
    final int palettes = bpp == 0 ? 16 : 256, block = 12 + palettes * 2;
    final var bytes = ByteBuffer.allocate(8 + block + 16).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8 | bpp).putInt(block).putShort((short)0).putShort((short)480).putShort((short)palettes).putShort((short)1);
    bytes.putShort((short)0).putShort((short)0x8000).putShort((short)colour).putShort((short)(colour | 0x8000));
    bytes.position(8 + block);
    bytes.putInt(16).putShort((short)64).putShort((short)0).putShort((short)2).putShort((short)1).putShort((short)(bpp == 0 ? 0x3210 : 0x0100)).putShort((short)(bpp == 0 ? 0x3210 : 0x0302));
    return bytes.array();
  }
  static byte[] model(final int page, final int endU, final boolean ocean) {
    final var bytes = ByteBuffer.allocate(84).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0,65).putInt(8,2).putInt(12 + (ocean ? 0 : 28) + 16,56).putInt(12 + (ocean ? 0 : 28) + 20,1);
    bytes.putInt(68,0x24000300); bytes.put(76,(byte)endU);
    bytes.putShort(74,(short)(480 << 6)); bytes.putShort(78,(short)page);
    return bytes.array();
  }
  static JsonArray numbers(final int... values) { final var array = new JsonArray(); for(final int value : values) array.add(value); return array; }
  record Fixture(byte[] model, List<byte[]> bank, JsonObject manifest, java.awt.image.BufferedImage image) {
    byte[] png() throws Exception { final var out = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(this.image,"png",out); return out.toByteArray(); }
    WorldTerrainAtlas read() throws Exception { final byte[] png = this.png(); this.manifest.addProperty("atlasSha256",legend.definitive.textures.TexturePilot.sha256(png)); return WorldTerrainAtlas.read(this.manifest.toString().getBytes(StandardCharsets.UTF_8),png,this.model,this.bank); }
  }
  static Fixture fixture() throws Exception {
    final byte[] model = model(1,7,false); final List<byte[]> bank = List.of(tim(0,31)); final var source = WorldTerrainSource.decode(model,bank);
    final var material = source.materials().getFirst(); final var root = new JsonObject();
    root.addProperty("schema",1);root.addProperty("pipeline","envhd-static-world-atlas-1");root.addProperty("modelSha256",source.modelHash());root.addProperty("bankSha256",source.bankHash());
    root.addProperty("scale",4);root.addProperty("paddingSourceTexels",8);root.add("atlasSize",numbers(4096,68));
    final var entry = new JsonObject();entry.addProperty("page",1);entry.addProperty("clut",480 << 6);entry.addProperty("status","hd");entry.addProperty("decodedRgbaSha256",material.fingerprint());
    entry.add("sourceSize",numbers(8,1));entry.add("sourceUvOrigin",numbers(0,0));entry.add("atlasRect",numbers(32,32,32,4));
    final var entries = new JsonArray();entries.add(entry);root.add("materials",entries);
    final var image = new java.awt.image.BufferedImage(4096,68,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<68;y++) for(int x=0;x<96;x++) {
      final int q=Math.clamp((x-32)/4,0,7)*4; final byte[] data=material.image().data;
      image.setRGB(x,y,(data[q+3]&255)<<24 | (data[q]&255)<<16 | (data[q+1]&255)<<8 | data[q+2]&255);
    }
    return new Fixture(model,bank,root,image);
  }
  @Test void orderedUploadFourAndEightBitColoursMatchShader() throws Exception {
    for(int bpp=0;bpp<2;bpp++) {
      final byte[] later=tim(bpp,31<<5);
      ByteBuffer.wrap(later).order(ByteOrder.LITTLE_ENDIAN).putShort(8+12+(bpp==0?16:256)*2+4,(short)128);
      final var scene=WorldTerrainSource.decode(model(1 | bpp<<7,bpp==0?7:3,false),List.of(tim(bpp,31),later));
      assertArrayEquals(new byte[]{0,0,0,0,0,0,0,-1,0,-1,0,0,0,-1,0,-1},java.util.Arrays.copyOf(scene.materials().getFirst().image().data,16));
    }
  }
  @Test void animatedOceanAndWrappedUvsRetainNative() throws Exception {
    assertTrue(WorldTerrainSource.decode(model(1,7,true),List.of(tim(0,31))).materials().isEmpty());
    assertTrue(WorldTerrainSource.decode(model(1,248,false),List.of(tim(0,31))).materials().getFirst().held());
    assertThrows(IOException.class,()->WorldTerrainSource.decode(new byte[4],List.of(tim(0,31))));
    final byte[] truncated=java.util.Arrays.copyOf(tim(0,31),40);
    assertThrows(IOException.class,()->WorldTerrainSource.decode(model(1,7,false),List.of(truncated)));
  }
  @Test void packedMappingKeysPageAndClutRetainsFractionalUvsAndOwnsPixels() throws Exception {
    final var atlas=fixture().read();
    assertArrayEquals(new float[]{(32+3.5f*4)/4096,(32+0.25f*4)/68},atlas.map(1|0x60,480<<6,3.5f,0.25f));
    assertNull(atlas.map(2,480<<6,3,0));assertNull(atlas.map(1,(480<<6)+1,3,0));
    assertThrows(IllegalArgumentException.class,()->atlas.map(1,480<<6,8,0));
    assertThrows(IllegalArgumentException.class,()->atlas.map(1,480<<6,Float.NaN,0));
    assertTrue(atlas.rgba().isReadOnly());assertTrue(atlas.replacesPart(1));assertFalse(atlas.replacesPart(0));
  }
  @Test void sourceMismatchIncompleteMappingAndMovedRectAreRejected() throws Exception {
    for(final String field:List.of("modelSha256","bankSha256")) {
      final var f=fixture();f.manifest.addProperty(field,"wrong");assertThrows(IOException.class,f::read);
    }
    final var f=fixture();f.manifest.getAsJsonArray("materials").get(0).getAsJsonObject().add("atlasRect",numbers(36,32,32,4));assertThrows(IOException.class,f::read);
    final var missing=fixture();missing.manifest.add("materials",new JsonArray());assertThrows(IOException.class,missing::read);
  }
  @Test void atlasCannotChangeDiscardVisibleBlackStpOrHideGapContent() throws Exception {
    for(final int[] change:new int[][]{{32,32,0xff112233},{36,32,0},{40,32,0},{40,32,0xffff0000},{4095,67,0x00112233},{0,0,0xff000000}}) {
      final var f=fixture();f.image.setRGB(change[0],change[1],change[2]);assertThrows(IOException.class,f::read);
    }
  }
  @Test void duplicateManifestKeysFailClosed() throws Exception {
    final var f=fixture();final var png=f.png();f.manifest.addProperty("atlasSha256",legend.definitive.textures.TexturePilot.sha256(png));
    final var text=f.manifest.toString().replaceFirst("\\{","{\"schema\":1,");
    assertThrows(IOException.class,()->WorldTerrainAtlas.read(text.getBytes(StandardCharsets.UTF_8),png,f.model,f.bank));
  }
  @Test void callbacksRequireFreshPairIgnoreOldGenerationsAndCopyBeforeMutation() {
    final var load=new WorldTerrainLoad();final long first=load.begin();final byte[] model={1,2};final byte[] bank={3,4};
    load.model(first,model,()->model[0]=9);assertNull(load.ready());
    load.bank(first,List.of(bank),()->bank[0]=8);final var snapshot=load.ready();
    assertArrayEquals(new byte[]{1,2},snapshot.model());assertArrayEquals(new byte[]{3,4},snapshot.bank().getFirst());
    snapshot.model()[0]=77;snapshot.bank().getFirst()[0]=77;assertEquals(1,load.ready().model()[0]);
    final long second=load.begin();assertNull(load.ready());
    load.bank(first,List.of(bank),()->fail("Stale native callback ran"));load.model(first,model,()->fail("Stale native model ran"));
    load.bank(second,List.of(new byte[]{5}),()->{});assertNull(load.ready());load.model(second,new byte[]{6},()->{});assertEquals(6,load.ready().model()[0]);
    load.invalidate();assertNull(load.ready());load.model(second,model,()->fail("Callback ran after teardown"));
  }
  @Test void eventSourceIsDefensivelyCopiedAndReplacementStartsEmpty() {
    final byte[] model={1},bank={2};final var event=new WorldTerrainTextureEvent(model,List.of(bank));model[0]=9;bank[0]=9;
    assertEquals(1,event.model()[0]);assertEquals(2,event.bank().getFirst()[0]);assertNull(event.replacement);
  }
}
