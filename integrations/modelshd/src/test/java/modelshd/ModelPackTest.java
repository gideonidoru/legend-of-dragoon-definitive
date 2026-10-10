package modelshd;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import legend.core.GameEngine;
import legend.core.gte.ModelPart10;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.tmd.TmdObjTable1c;
import legend.game.types.Model124;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelPackTest {
  @TempDir Path temp;
  RecordingApi api;
  @BeforeEach void renderer() throws Exception {
    api = new RecordingApi();
    var field = GameEngine.RENDERER.getClass().getDeclaredField("api"); field.setAccessible(true); field.set(GameEngine.RENDERER, api);
  }
  @AfterEach void cleanup() { Obj.deleteObjects(); }
  static TmdObjTable1c source(int page) {
    var packet = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    for(int i=0;i<3;i++) packet.put((byte)(20+i*20)).put((byte)(10+i*15)).putShort((short)(i==0?17347:i==1?page:0));
    for(int i=0;i<3;i++) packet.putShort((short)i).putShort((short)i);
    return new TmdObjTable1c("source", new Vector3f[]{new Vector3f(0,0,0),new Vector3f(10,0,0),new Vector3f(0,10,0)},new Vector3f[]{new Vector3f(0,0,1),new Vector3f(0,0,1),new Vector3f(0,0,1)},new TmdObjTable1c.Primitive[]{new TmdObjTable1c.Primitive(0,24,0x34000601,new byte[][]{packet.array()})});
  }
  static JsonArray rows(float[][] numbers) {
    var result=new JsonArray();for(var row:numbers){var a=new JsonArray();for(float v:row)a.add(v);result.add(a);}return result;
  }
  static JsonObject pack(TmdObjTable1c... sources) {
    var root=new JsonObject();root.addProperty("version",1);root.addProperty("sourceGeometrySha256",ModelPack.identity(sources));var parts=new JsonArray();root.add("parts",parts);
    for(var src:sources){var p=new JsonObject();p.add("vertices",rows(new float[][]{{0,0,0},{10,0,0},{0,10,0}}));p.add("normals",rows(new float[][]{{0,0,1},{0,0,1},{0,0,1}}));var faces=new JsonArray();var f=new JsonObject();f.addProperty("sourceFace",0);var refs=new JsonArray();for(int i=0;i<3;i++)refs.add(i);f.add("vertices",refs);f.add("normals",refs.deepCopy());f.add("sourceWeights",rows(new float[][]{{1,0,0},{0,1,0},{0,0,1}}));faces.add(f);p.add("faces",faces);parts.add(p);}return root;
  }
  TmdObjTable1c[] read(JsonObject p,TmdObjTable1c... src) throws Exception {var path=temp.resolve("pack.json");Files.writeString(path,p.toString());return ModelPack.read(path,src);}
  static Model124 model(TmdObjTable1c... sources){var m=new Model124("test");m.modelParts_00=new ModelPart10[sources.length];for(int i=0;i<sources.length;i++){m.modelParts_00[i]=new ModelPart10();m.modelParts_00[i].tmd_08=sources[i];}return m;}
  @Test void activeIndexedMaterialsAndFloatGeometryReachNativeLoader() throws Exception {
    var src=source(0x20);var p=pack(src);p.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("vertices").get(1).getAsJsonArray().set(0,new com.google.gson.JsonPrimitive(10.375));var replacements=read(p,src);src.getObj();var old=api.created.get(0);var m=model(src);var animation=m.anim_08;ModelReplacement.install(m,replacements);assertSame(animation,m.anim_08);var mesh=api.created.get(1);assertEquals(10.375f,mesh.data[16]);assertEquals(20f,mesh.data[7]);assertEquals(10f,mesh.data[8]);assertEquals(0x20,mesh.data[9]);assertEquals(17347,mesh.data[10]);assertEquals(3,mesh.data[15]);assertTrue(mesh.textured);Obj.deleteObjects();assertTrue(old.deleted);assertFalse(mesh.deleted);m.deleteModelParts();Obj.deleteObjects();assertTrue(mesh.deleted);
  }
  @Test void rgbaTextureDimensionsAndAddressingAreInherited() throws Exception {
    var src=source(0x180);src.rebuildObj(512,256);var replacements=read(pack(src),src);ModelReplacement.install(model(src),replacements);var data=api.created.get(1).data;assertEquals(20f/512,data[7]);assertEquals(10f/256,data[8]);assertEquals(0x180,data[9]);assertEquals(17347,data[10]);
  }
  @Test void relocatedPaletteAndPageAreCopiedFromActiveSource() throws Exception {
    var original=source(0x20);var p=pack(original);var runtime=source(0x60);runtime.primitives_10[0].data()[0][2]=42;var replacement=read(p,runtime)[0];var packet=replacement.primitives_10[0].data()[0];assertEquals(42,packet[2]);assertEquals(0x60,ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(6));assertArrayEquals(runtime.primitives_10[0].data()[0],packet);
  }
  @Test void gpuPreparationFailurePreservesAllOriginalsAndDeletesStagedMeshes() throws Exception {
    var a=source(0x20);var b=source(0x20);a.getObj();b.getObj();var m=model(a,b);var replacements=read(pack(a,b),a,b);api.failAt=4;assertThrows(IllegalStateException.class,()->ModelReplacement.install(m,replacements));assertSame(a,m.modelParts_00[0].tmd_08);assertSame(b,m.modelParts_00[1].tmd_08);Obj.deleteObjects();assertFalse(api.created.get(0).deleted);assertFalse(api.created.get(1).deleted);assertTrue(api.created.get(2).deleted);
  }
  @Test void failedSecondLayerReleasesFirstLayerWithoutReplacingSource() throws Exception {
    var base=source(0x20);var opaque=base.primitives_10[0];var translucent=new TmdObjTable1c.Primitive(0,24,0x36000601,opaque.data());var src=new TmdObjTable1c("mixed",base.vert_top_00,base.normal_top_08,new TmdObjTable1c.Primitive[]{opaque,translucent});src.getObj();var candidate=new TmdObjTable1c("candidate",src.vert_top_00,src.normal_top_08,src.primitives_10);var m=model(src);api.failAt=4;assertThrows(IllegalStateException.class,()->ModelReplacement.install(m,new TmdObjTable1c[]{candidate}));assertSame(src,m.modelParts_00[0].tmd_08);assertFalse(api.created.get(0).deleted);assertFalse(api.created.get(1).deleted);assertTrue(api.created.get(2).deleted);
  }
  @Test void invalidLastPartNeverChangesOriginals() throws Exception {
    var a=source(0x20);var b=source(0x20);var p=pack(a,b);p.getAsJsonArray("parts").get(1).getAsJsonObject().getAsJsonArray("faces").get(0).getAsJsonObject().getAsJsonArray("vertices").set(0,new com.google.gson.JsonPrimitive(999));assertThrows(java.io.IOException.class,()->read(p,a,b));assertEquals(0,api.created.size());
  }
  @Test void strictJsonRejectsTrailingDataAndExcessiveDepth() throws Exception {
    var src=source(0x20);var path=temp.resolve("malformed.json");Files.writeString(path,pack(src)+" {}");assertThrows(java.io.IOException.class,()->ModelPack.read(path,new TmdObjTable1c[]{src}));Files.writeString(path,"[".repeat(40)+"0"+"]".repeat(40));assertThrows(java.io.IOException.class,()->ModelPack.read(path,new TmdObjTable1c[]{src}));
  }
  @Test void sourceColourAlphaByteSurvivesNativeConstruction() throws Exception {
    var bytes=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);bytes.put(new byte[]{(byte)216,(byte)148,107,56});for(int i=0;i<3;i++)bytes.putShort((short)i).putShort((short)i);var base=source(0x20);var src=new TmdObjTable1c("coloured",base.vert_top_00,base.normal_top_08,new TmdObjTable1c.Primitive[]{new TmdObjTable1c.Primitive(0,16,0x30000401,new byte[][]{bytes.array()})});var replacements=read(pack(src),src);var original=((MeshObj)src.getObj()).meshes[0].vertices().clone();var after=((MeshObj)replacements[0].buildObjLike(src)).meshes[0].vertices();assertArrayEquals(original,after);
  }
  @Test void nativeIndexedAndRgbaHandoffsPass() {
    var indexed=source(0x20);indexed.getObj();var rgba=source(0x180);rgba.rebuildObj(256,112);assertDoesNotThrow(()->TextureCompatibility.requireNativeAddressing(new TmdObjTable1c[]{indexed,rgba}));
  }
  @Test void customActiveUvOverrideKeepsTextureOwnerPriority() {
    var src=source(0x20);var obj=(MeshObj)src.getObj();obj.meshes[0].vertices()[7]=123f;assertThrows(IllegalArgumentException.class,()->TextureCompatibility.requireNativeAddressing(new TmdObjTable1c[]{src}));assertEquals(123f,obj.meshes[0].vertices()[7]);assertFalse(api.created.get(0).deleted);
  }
  @Test void unknownSourceRejected(){var src=source(0x20);var p=pack(src);p.addProperty("sourceGeometrySha256","0".repeat(64));assertThrows(java.io.IOException.class,()->read(p,src));}
  @Test void missingAnimationPartRejected(){var a=source(0x20);var b=source(0x20);assertThrows(java.io.IOException.class,()->read(pack(a),a,b));}
  @Test void malformedCornerWeightsRejected(){var src=source(0x20);var p=pack(src);p.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("faces").get(0).getAsJsonObject().getAsJsonArray("sourceWeights").get(0).getAsJsonArray().set(0,new com.google.gson.JsonPrimitive(.5));assertThrows(java.io.IOException.class,()->read(p,src));}
  @Test void invalidNormalRejected(){var src=source(0x20);var p=pack(src);p.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("normals").get(0).getAsJsonArray().set(2,new com.google.gson.JsonPrimitive(0));assertThrows(java.io.IOException.class,()->read(p,src));}
  @Test void sharedOrSourceReplacementTablesRejected(){var src=source(0x20);var m=model(src,source(0x20));assertThrows(IllegalArgumentException.class,()->ModelReplacement.install(m,new TmdObjTable1c[]{src,source(0x20)}));var own=source(0x20);assertThrows(IllegalArgumentException.class,()->ModelReplacement.install(m,new TmdObjTable1c[]{own,own}));}
  @Test void ownedFactoryCopiesArraysAndRejectsNonfinite(){var src=source(0x20);var copy=new TmdObjTable1c("copy",src.vert_top_00,src.normal_top_08,src.primitives_10);src.vert_top_00[0].x=5;src.primitives_10[0].data()[0][0]=99;assertEquals(0,copy.vert_top_00[0].x);assertEquals(20,copy.primitives_10[0].data()[0][0]);assertThrows(IllegalArgumentException.class,()->new TmdObjTable1c("bad",new Vector3f[]{new Vector3f(Float.NaN,0,0)},src.normal_top_08,src.primitives_10));}
  static class RecordingApi extends NoopApi {
    final List<RecordingMesh> created=new ArrayList<>();int calls,failAt=-1;
    @Override public Mesh makeMesh(String name,VertexOrder order,float[] data,int[] indices,boolean textured,boolean translucent,Translucency mode,BufferUsage usage){if(++calls==failAt)throw new IllegalStateException("injected allocation failure");var mesh=new RecordingMesh(data.clone(),textured,translucent,mode);created.add(mesh);return mesh;}
  }
  static class RecordingMesh implements Mesh {
    final float[] data;final boolean textured,translucent;final Translucency mode;boolean deleted;
    RecordingMesh(float[] d,boolean t,boolean x,Translucency m){data=d;textured=t;translucent=x;mode=m;}
    public void update(){}public void delete(){deleted=true;}public void attribute(int i,long o,int n,int s){}public void draw(){}public void draw(int s,int n){}public float[] vertices(){return data;}public boolean textured(){return textured;}public boolean translucent(){return translucent;}public Translucency translucencyMode(){return mode;}
  }
}
