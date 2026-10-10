package charhd;

import com.google.gson.*;
import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.definitive.models.TmdGeometryPack;
import legend.definitive.textures.TexturePilot;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.tmd.*;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.legendofdragoon.modloader.events.EventManager;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ReconstructedCharactersTest {
  EventManager.Access access;
  @BeforeEach void setup() throws Exception {
    final var renderer=GameEngine.RENDERER.getClass().getDeclaredField("api");renderer.setAccessible(true);renderer.set(GameEngine.RENDERER,new NoopApi());
    final var field=GameEngine.class.getDeclaredField("EVENT_ACCESS");field.setAccessible(true);access=(EventManager.Access)field.get(null);access.initialize(GameEngine.MODS);
  }
  @AfterEach void cleanup() {access.reset();Obj.deleteObjects();}
  static TmdObjTable1c source() {
    final var data=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(12).putInt(0).putInt(0).putInt(0x41).putInt(0).putInt(1);
    data.putInt(28).putInt(3).putInt(52).putInt(3).putInt(76).putInt(1).putInt(0);
    for(final var point:new short[][]{{0,0,-5},{3,0,-5},{0,3,-5}})data.putShort(point[0]).putShort(point[1]).putShort(point[2]).putShort((short)0);
    for(int i=0;i<3;i++)data.putShort((short)0).putShort((short)0).putShort((short)4096).putShort((short)0);
    final var packet=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);packet.putShort(2,(short)320);
    for(int i=0;i<3;i++)packet.putShort(12+i*4,(short)i).putShort(14+i*4,(short)i);
    data.putInt(0x34000601).put(packet.array());
    return new CContainer("original synthetic actor",new FileData(data.array())).tmdPtr_00.tmd.objTable[0];
  }
  private record Fixture(TmdObjTable1c source,JsonObject manifest,Map<String,byte[]> resources) {
    ReconstructedCharacters load() throws IOException {
      resources.put("characters.json",manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return new ReconstructedCharacters((path,limit)->{final var bytes=resources.get(path);if(bytes==null)throw new IOException("Missing fixture");return bytes;});
    }
  }
  static Fixture fixture() throws Exception {
    final var source=source();final String id=TmdGeometryPack.identity(new TmdObjTable1c[]{source});
    final var pack=JsonParser.parseString("{\"version\":1,\"parts\":[{\"vertices\":[[0,0,-5],[4,0,-5],[0,4,-5]],\"normals\":[[0,0,1],[0,0,1],[0,0,1]],\"faces\":[{\"sourceFace\":0,\"vertices\":[0,1,2],\"normals\":[0,1,2],\"sourceWeights\":[[1,0,0],[0,1,0],[0,0,1]]}]}]}").getAsJsonObject();pack.addProperty("sourceGeometrySha256",id);
    final var bytes=pack.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final var geometry=TmdGeometryPack.read(new ByteArrayInputStream(bytes),new TmdObjTable1c[]{source})[0];
    final var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);for(int y=0;y<2;y++)for(int x=0;x<2;x++)image.setRGB(x,y,0xff204080);
    final var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);final byte[] png=out.toByteArray();
    final var uv=JsonParser.parseString("{\"version\":1,\"uvs\":[[0,0],[1,0],[0,1]]}").getAsJsonObject();
    uv.addProperty("sourceGeometrySha256",id);uv.addProperty("geometrySha256",TmdGeometryPack.identity(new TmdObjTable1c[]{geometry}));uv.addProperty("textureSha256",TexturePilot.sha256(png));
    final byte[] uvBytes=uv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final var entry=new JsonObject();entry.addProperty("sourceGeometrySha256",id);entry.addProperty("baselineGeometrySha256",id);entry.addProperty("geometrySha256",uv.get("geometrySha256").getAsString());
    for(final var pair:Map.of("pack","fixture/part.json","uv","fixture/uv.json","texture","fixture/paint.png").entrySet())entry.addProperty(pair.getKey(),pair.getValue());
    entry.addProperty("packSha256",TexturePilot.sha256(bytes));entry.addProperty("uvSha256",TexturePilot.sha256(uvBytes));entry.addProperty("textureSha256",TexturePilot.sha256(png));entry.addProperty("width",2);entry.addProperty("height",2);
    final var entries=new JsonArray();entries.add(entry);final var manifest=new JsonObject();manifest.addProperty("format",1);manifest.add("parts",entries);
    final var resources=new HashMap<String,byte[]>();resources.put("fixture/part.json",bytes);resources.put("fixture/uv.json",uvBytes);resources.put("fixture/paint.png",png);
    return new Fixture(source,manifest,resources);
  }
  @Test void bundledCandidateLoadsWithoutExperimentJarOrExternalPacks() throws Exception {assertNotNull(new ReconstructedCharacters());}
  @Test void geometryAndMaterialReplaceTogetherWithoutMutatingSource() throws Exception {
    final var fixture=fixture();final var source=fixture.source;final var before=source.primitives_10[0].data()[0].clone();
    final var event=new TmdAppearanceEvent(source,source,0);assertTrue(fixture.load().apply(event));
    assertEquals(4,event.appearance.vert_top_00[1].x);assertEquals(3,source.vert_top_00[1].x);assertArrayEquals(before,source.primitives_10[0].data()[0]);
    assertNotNull(event.appearance.faceDetail());assertEquals(2,event.appearance.faceDetail().width);assertTrue(event.appearance.faceDetail().pixels().isDirect());
  }
  @Test void otherGeometryAndAppearanceOwnersKeepPriority() throws Exception {
    final var fixture=fixture();final var upgrade=fixture.load();final var source=fixture.source;
    final var own=new TmdObjTable1c("another mod",new Vector3f[]{new Vector3f(),new Vector3f(8,0,0),new Vector3f(0,8,0)},source.normal_top_08,source.primitives_10);
    final var event=new TmdAppearanceEvent(source,own,0);assertFalse(upgrade.apply(event));assertSame(own,event.appearance);
    final var claimed=new TmdAppearanceEvent(source,source,0);claimed.appearance=own;assertFalse(upgrade.apply(claimed));assertSame(own,claimed.appearance);
  }
  @Test void declaredBaselineGeometryCanHandOff() throws Exception {
    final var fixture=fixture();final var source=fixture.source;
    final var baseline=new TmdObjTable1c("recorded baseline",source.vert_top_00,source.normal_top_08,source.primitives_10);
    final var event=new TmdAppearanceEvent(source,baseline,0);assertTrue(fixture.load().apply(event));assertNotNull(event.appearance.faceDetail());
  }
  @Test void nativeIndexConsumerRetainsOriginal() throws Exception {
    final var fixture=fixture();fixture.source.retainNativeVertexIndices();final var event=new TmdAppearanceEvent(fixture.source,fixture.source,0);assertFalse(fixture.load().apply(event));assertSame(fixture.source,event.appearance);
  }
  @Test void corruptedMaterialOrBindingCannotPartiallyInstallGeometry() throws Exception {
    for(final var name:List.of("fixture/part.json","fixture/uv.json","fixture/paint.png")) {
      final var fixture=fixture();fixture.resources.put(name,new byte[]{1,2,3});assertThrows(IOException.class,fixture::load);assertEquals(3,fixture.source.vert_top_00[1].x);
    }
  }
  @Test void manifestRejectsDuplicatesTraversalDimensionsAndTrailingData() throws Exception {
    final var duplicate=fixture();duplicate.manifest.getAsJsonArray("parts").add(duplicate.manifest.getAsJsonArray("parts").get(0).deepCopy());assertThrows(IOException.class,duplicate::load);
    final var path=fixture();path.manifest.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("pack","../part.json");assertThrows(IOException.class,path::load);
    final var size=fixture();size.manifest.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("width",2049);assertThrows(IOException.class,size::load);
    final var trailing=fixture();assertThrows(IOException.class,()->new ReconstructedCharacters((p,l)->p.equals("characters.json")?(trailing.manifest+" {}").getBytes():trailing.resources.get(p)));
  }
  @Test void wrongGeometryIdentityRetainsExistingAppearance() throws Exception {
    final var fixture=fixture();final var entry=fixture.manifest.getAsJsonArray("parts").get(0).getAsJsonObject();entry.remove("uv");entry.addProperty("geometrySha256","0".repeat(64));
    final var event=new TmdAppearanceEvent(fixture.source,fixture.source,0);assertThrows(IOException.class,()->fixture.load().apply(event));assertSame(fixture.source,event.appearance);
  }
  public static final class Listener {
    final ReconstructedCharacters characters;Listener(final ReconstructedCharacters characters){this.characters=characters;}
    @EventListener public void paint(final TmdAppearanceEvent event) throws IOException {this.characters.apply(event);}
  }
  @Test void mappedBodyAtlasAndNativeTextureRoutesKeepSupplementalMaterial() throws Exception {
    final var fixture=fixture();final var listener=new Listener(fixture.load());GameEngine.EVENTS.register(listener);
    final var mapped=TmdObjLoader.fromObjTableMapped("body atlas plus head",fixture.source,(clut,u,v,header)->new float[]{.125f,.25f});
    final var nativeMesh=TmdObjLoader.fromObjTable("native texture plus head",fixture.source);
    assertEquals(2,mapped.faceDetailTexture().width);assertEquals(2,nativeMesh.faceDetailTexture().width);
    mapped.delete();nativeMesh.delete();java.lang.ref.Reference.reachabilityFence(listener);
  }
}
