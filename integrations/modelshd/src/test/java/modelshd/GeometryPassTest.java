package modelshd;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import legend.core.GameEngine;
import legend.core.renderer.MeshObj;
import legend.core.renderer.Obj;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.TmdObjTable1c;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.EventManager;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class GeometryPassTest {
  @TempDir Path overrides;
  ModelPackTest.RecordingApi api;
  EventManager.Access access;

  @BeforeEach void prepare() throws Exception {
    api = new ModelPackTest.RecordingApi();
    var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api"); renderer.setAccessible(true); renderer.set(GameEngine.RENDERER, api);
    var field = GameEngine.class.getDeclaredField("EVENT_ACCESS"); field.setAccessible(true); access = (EventManager.Access)field.get(null);
    access.initialize(GameEngine.MODS);
  }
  @AfterEach void cleanup() { access.reset(); Obj.deleteObjects(); }

  static TmdObjTable1c parsed() { return parsed(false); }
  static TmdObjTable1c parsed(boolean mixed) {
    var packet = ModelPackTest.source(0x20).primitives_10[0].data()[0];
    var data = ByteBuffer.allocate(mixed ? 156 : 128).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(12).putInt(0).putInt(0).putInt(0x41).putInt(0).putInt(1);
    data.putInt(28).putInt(3).putInt(52).putInt(3).putInt(76).putInt(mixed ? 2 : 1).putInt(0);
    for(var p:new short[][]{{0,0,0},{10,0,0},{0,10,0}}) data.putShort(p[0]).putShort(p[1]).putShort(p[2]).putShort((short)0);
    for(int i=0;i<3;i++) data.putShort((short)0).putShort((short)0).putShort((short)4096).putShort((short)0);
    data.putInt(0x34000601).put(packet);
    if(mixed) data.putInt(0x36000601).put(packet);
    return new CContainer("parsed",new FileData(data.array())).tmdPtr_00.tmd.objTable[0];
  }

  GeometryPass pass(TmdObjTable1c source, byte[] payload, boolean corruptHash) throws Exception {
    var root = new JsonObject(); root.addProperty("format",1); var entries = new JsonArray(); root.add("partPacks",entries);
    var row=new JsonObject();row.addProperty("sourceGeometrySha256",ModelPack.identity(new TmdObjTable1c[]{source}));
    row.addProperty("packBytes",payload.length);row.addProperty("packSha256",corruptHash?"0".repeat(64):HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));entries.add(row);
    return new GeometryPass(new ByteArrayInputStream(root.toString().getBytes()),id->new ByteArrayInputStream(payload));
  }
  static byte[] payload(TmdObjTable1c source) {
    var pack=ModelPackTest.pack(source);
    var faces=pack.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("faces");
    for(int i=1;i<source.n_primitive_14;i++) {
      var face=faces.get(0).deepCopy().getAsJsonObject();face.addProperty("sourceFace",i);faces.add(face);
    }
    pack.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("vertices").get(1).getAsJsonArray().set(0,new com.google.gson.JsonPrimitive(10.5f));
    return pack.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test void sharedPartPassKeepsCpuGeometryMaterialWordsAndEventDimensions() throws Exception {
    var source=parsed(); var originalPacket=source.primitives_10[0].data()[0].clone(); var event=new TmdGeometryEvent(source,0,256,112);
    assertTrue(pass(source,payload(source),false).prepare(event,overrides));
    assertNotSame(source,event.geometry);assertEquals(10.5f,event.geometry.vert_top_00[1].x);
    assertEquals(10,source.vert_top_00[1].x);assertArrayEquals(originalPacket,source.primitives_10[0].data()[0]);
    assertArrayEquals(originalPacket,event.geometry.primitives_10[0].data()[0]);assertEquals(256,event.textureWidth);assertEquals(112,event.textureHeight);
  }
  @Test void corruptPartAndOtherGeometryOwnerRetainOriginal() throws Exception {
    var source=parsed();var event=new TmdGeometryEvent(source,0,0,0);
    assertThrows(java.io.IOException.class,()->pass(source,payload(source),true).prepare(event,overrides));assertSame(source,event.geometry);
    event.geometry=ModelPackTest.source(0x20);assertFalse(pass(source,payload(source),false).prepare(event,overrides));
    var authored=ModelPackTest.source(0x20);assertFalse(pass(authored,payload(authored),false).prepare(new TmdGeometryEvent(authored,0,0,0),overrides));
  }
  @Test void nativeLoaderAppliesPassOnceAndRetainsIndexedAndRgbaNormalization() throws Exception {
    var source=parsed();
    ByteBuffer.wrap(source.primitives_10[0].data()[0]).order(ByteOrder.LITTLE_ENDIAN).putShort(6,(short)0x180);
    var geometry=pass(source,payload(source),false);var listener=new Listener(geometry,overrides);
    GameEngine.EVENTS.register(listener);
    var before=source.vert_top_00[1].x;
    var obj=TmdObjLoader.fromObjTable("field RGBA",source,0,256,112);
    assertEquals(1,listener.calls);assertEquals(before,source.vert_top_00[1].x);
    assertEquals(10.5f,obj.meshes[0].vertices()[16]);
    assertEquals(20f/256f,obj.meshes[0].vertices()[7]);assertEquals(10f/112f,obj.meshes[0].vertices()[8]);
    obj.delete();ByteBuffer.wrap(source.primitives_10[0].data()[0]).order(ByteOrder.LITTLE_ENDIAN).putShort(6,(short)0x20);
    var indexed=TmdObjLoader.fromObjTable("world indexed",source);
    assertEquals(2,listener.calls);assertEquals(20f,indexed.meshes[0].vertices()[7]);indexed.delete();
  }
  @Test void failedCandidateAllocationFreesStagedMeshThenRebuildsOriginal() throws Exception {
    var source=parsed(true);var listener=new Listener(pass(source,payload(source),false),overrides);GameEngine.EVENTS.register(listener);
    api.failAt=2;var obj=TmdObjLoader.fromObjTable("fallback",source);
    assertEquals(10f,obj.meshes[0].vertices()[16]);assertEquals(1,listener.calls);
    assertEquals(3,api.created.size());assertTrue(api.created.get(0).deleted);
    assertFalse(api.created.get(1).deleted);assertFalse(api.created.get(2).deleted);obj.delete();
  }
  @Test void largeNativeSceneryFitsCoordinateBudgetButExcessiveAuthoredGeometryDoesNot() throws Exception {
    var source=parsed();var pack=ModelPackTest.pack(source);
    var corner=pack.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("vertices").get(1).getAsJsonArray();
    for(int i=0;i<3;i++) corner.set(i,new com.google.gson.JsonPrimitive(32767));
    assertEquals(32767f,ModelPack.read(new ByteArrayInputStream(pack.toString().getBytes()),new TmdObjTable1c[]{source})[0].vert_top_00[1].z);
    corner.set(0,new com.google.gson.JsonPrimitive(100000));
    assertThrows(java.io.IOException.class,()->ModelPack.read(new ByteArrayInputStream(pack.toString().getBytes()),new TmdObjTable1c[]{source}));
  }
  @Test void absentModLeavesNativeMeshUnchangedAndAuthoredTablesBypassEvent() {
    var source=parsed();var baseline=TmdObjLoader.fromObjTable("native",source);
    assertEquals(10f,baseline.meshes[0].vertices()[16]);baseline.delete();
    assertFalse(source.isAuthoredGeometry());assertTrue(ModelPackTest.source(0x20).isAuthoredGeometry());
  }

  @Test void vertexDifferenceAnimationRetiresCachedRefinementAndKeepsOriginalIndices() throws Exception {
    var source=parsed();var pack=ModelPackTest.pack(source);
    var part=pack.getAsJsonArray("parts").get(0).getAsJsonObject();
    part.getAsJsonArray("vertices").add(ModelPackTest.rows(new float[][]{{5,0,0}}).get(0));
    part.getAsJsonArray("faces").get(0).getAsJsonObject().getAsJsonArray("vertices").set(0,new com.google.gson.JsonPrimitive(3));
    var listener=new Listener(pass(source,pack.toString().getBytes(),false),overrides);GameEngine.EVENTS.register(listener);
    var cached=(MeshObj)source.getObj();assertEquals(3f,cached.meshes[0].vertices()[3]);
    var direct=TmdObjLoader.fromObjTable("separate direct consumer",source);direct.delete();
    var animation=new legend.game.combat.types.VertexDifferenceAnimation18();
    animation.tmd=source;animation.ticksRemaining_00=2;animation.vertexCount_08=3;animation.sourceVertices_0c=source.vert_top_00;
    animation.current_14=java.util.Arrays.stream(source.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
    animation.step_10=new org.joml.Vector3f[]{new org.joml.Vector3f(1,0,0),new org.joml.Vector3f(1,0,0),new org.joml.Vector3f(1,0,0)};
    legend.game.combat.types.VertexDifferenceAnimation18.applyVertexDifferenceAnimation(null,animation);
    var nativeObj=(MeshObj)source.getObj();assertNotSame(cached,nativeObj);assertTrue(source.requiresNativeVertexIndices());
    assertEquals(11f,nativeObj.meshes[0].vertices()[16]);assertEquals(2,listener.calls);
    for(int offset=0;offset<nativeObj.meshes[0].vertices().length;offset+=16) assertTrue(nativeObj.meshes[0].vertices()[offset+3]<source.n_vert_04);
    Obj.deleteObjects();assertTrue(api.created.get(0).deleted);assertTrue(api.created.get(1).deleted);assertFalse(api.created.get(2).deleted);
    legend.game.combat.types.VertexDifferenceAnimation18.applyVertexDifferenceAnimation(null,animation);
    assertSame(nativeObj,source.getObj());assertEquals(12f,nativeObj.meshes[0].vertices()[16]);assertEquals(2,listener.calls);
  }

  public static class Listener {
    final GeometryPass pass; final Path folder; int calls;
    Listener(GeometryPass pass,Path folder){this.pass=pass;this.folder=folder;}
    @EventListener public void loaded(TmdGeometryEvent event) throws Exception {calls++;pass.prepare(event,folder);}
  }
}
