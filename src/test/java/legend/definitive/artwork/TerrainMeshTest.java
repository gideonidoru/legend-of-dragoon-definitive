package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdWithId;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.*;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.EventManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real mesh loader against a recording renderer, without a window. */
class TerrainMeshTest {
  RecordingApi api;
  EventManager.Access access;
  RenderApi previousApi;
  @BeforeEach void setup() throws Exception {
    final var field=GameEngine.RENDERER.getClass().getDeclaredField("api");field.setAccessible(true);this.previousApi=(RenderApi)field.get(GameEngine.RENDERER);
    this.api=new RecordingApi();field.set(GameEngine.RENDERER,this.api);
    final var events=GameEngine.class.getDeclaredField("EVENT_ACCESS");events.setAccessible(true);this.access=(EventManager.Access)events.get(null);this.access.initialize(GameEngine.MODS);
  }
  @AfterEach void cleanup() throws Exception {
    this.access.reset();Obj.deleteObjects();final var field=GameEngine.RENDERER.getClass().getDeclaredField("api");field.setAccessible(true);field.set(GameEngine.RENDERER,this.previousApi);
  }
  static Object cachedRefinement(final TmdObjTable1c source) throws Exception {
    final var field=TmdObjTable1c.class.getDeclaredField("refinedObj");field.setAccessible(true);return field.get(source);
  }
  static TmdObjTable1c source() {
    final var bytes=ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(65).putInt(0).putInt(1).putInt(28).putInt(3).putInt(52).putInt(3).putInt(76).putInt(2).putInt(0);
    for(final short[] p:new short[][]{{0,0,0},{10,0,0},{0,10,0}}) bytes.putShort(p[0]).putShort(p[1]).putShort(p[2]).putShort((short)0);
    for(int i=0;i<3;i++) bytes.putShort((short)0).putShort((short)0).putShort((short)4096).putShort((short)0);
    for(int face=0;face<2;face++) {
      bytes.putInt(face==0?0x34000601:0x36000601);
      for(int i=0;i<3;i++) bytes.put((byte)(i==1?7:1)).put((byte)0).putShort((short)(i==0?480<<6:i==1?face+1:0));
      for(int i=0;i<3;i++) bytes.putShort((short)i).putShort((short)i);
    }
    return new TmdWithId("terrain fixture",new FileData(bytes.array())).tmd.objTable[0];
  }
  public static final class GeometryListener {
    int calls;
    @EventListener public void refine(final TmdGeometryEvent event) {
      this.calls++;
      final var geometry=new TmdObjTable1c("owned geometry",event.source.vert_top_00,event.source.normal_top_08,event.source.primitives_10);
      geometry.vert_top_00[1].x=10.5f;
      geometry.faceSurfaces(new SurfaceResponse[]{new SurfaceResponse(SurfaceMaterial.METAL),new SurfaceResponse(SurfaceMaterial.SKIN)});
      event.geometry=geometry;
    }
  }
  @Test void mixedFaceUvsRefinementSurfaceFlagsAndNativeCacheRemainIndependent() throws Exception {
    final var source=source();source.surfaceMaterial(SurfaceMaterial.CLOTH);final var nativeMesh=source.getObj();final var nativeRefinement=cachedRefinement(source);
    final var listener=new GeometryListener();GameEngine.EVENTS.register(listener);
    final var atlas=WorldTerrainTest.fixture().read();final var custom=TmdObjLoader.fromObjTable("terrain optional",source,0,atlas.width(),atlas.height(),atlas);
    assertEquals(1,listener.calls);assertSame(nativeMesh,source.getObj());assertSame(nativeRefinement,cachedRefinement(source));assertEquals(SurfaceMaterial.CLOTH,custom.surfaceMaterial);
    final float[] hd=custom.meshes[1].vertices(),nativeFace=custom.meshes[0].vertices();
    assertEquals(10.5f,hd[16]);assertEquals((32+7f*4)/4096,hd[23]);assertEquals(1|0x180,hd[9]);
    assertEquals(7f,nativeFace[23]);assertEquals(2f,nativeFace[9]);
    assertEquals(new SurfaceResponse(SurfaceMaterial.METAL).flags(),(int)hd[15]&~0x1f);assertEquals(new SurfaceResponse(SurfaceMaterial.SKIN).flags(),(int)nativeFace[15]&~0x1f);
    custom.delete();assertSame(nativeMesh,source.getObj());assertFalse(((RecordingMesh)((MeshObj)nativeMesh).meshes[0]).deleted);
  }
  @Test void allocationFailureDeletesOptionalStagingAndRetainsNativeGeometryWithSameUvMap() throws Exception {
    final var source=source();final var listener=new GeometryListener();GameEngine.EVENTS.register(listener);this.api.failAt=2;
    final var atlas=WorldTerrainTest.fixture().read();final var mesh=TmdObjLoader.fromObjTable("fallback",source,0,atlas.width(),atlas.height(),atlas);
    assertEquals(1,listener.calls);assertTrue(this.api.created.getFirst().deleted);assertEquals(10f,mesh.meshes[1].vertices()[16]);
    assertEquals((32+7f*4)/4096,mesh.meshes[1].vertices()[23]);assertEquals(7f,mesh.meshes[0].vertices()[23]);assertNull(cachedRefinement(source));mesh.delete();
  }
  static final class RecordingApi extends NoopApi {
    final List<RecordingMesh> created=new ArrayList<>();int calls,failAt=-1;
    @Override public Mesh makeMesh(final String name,final VertexOrder order,final float[] vertices,final int[] indices,final boolean textured,final boolean translucent,final Translucency mode,final BufferUsage usage) {
      if(++this.calls==this.failAt) throw new IllegalStateException("Injected optional allocation failure");
      final var mesh=new RecordingMesh(vertices.clone(),textured,translucent,mode);this.created.add(mesh);return mesh;
    }
  }
  static final class RecordingMesh implements Mesh {
    final float[] vertices;final boolean textured,translucent;final Translucency mode;boolean deleted;
    RecordingMesh(final float[] vertices,final boolean textured,final boolean translucent,final Translucency mode) {this.vertices=vertices;this.textured=textured;this.translucent=translucent;this.mode=mode;}
    public void update() { } public void delete() {this.deleted=true;} public void attribute(final int i,final long o,final int n,final int s) { } public void draw() { } public void draw(final int s,final int n) { }
    public float[] vertices() {return this.vertices;} public boolean textured() {return this.textured;} public boolean translucent() {return this.translucent;} public Translucency translucencyMode() {return this.mode;}
  }
}
