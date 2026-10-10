package charhd;

import legend.core.GameEngine;
import legend.core.renderer.MeshObj;
import legend.core.renderer.Obj;
import legend.core.renderer.SurfaceMaterial;
import legend.core.renderer.SurfaceResponse;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdFaceDetail;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.legendofdragoon.modloader.events.EventManager;
import org.legendofdragoon.modloader.events.EventListener;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DartFacePaintTest {
  public static final class FailingAppearance {
    final TmdObjTable1c replacement;
    FailingAppearance(final TmdObjTable1c replacement) {this.replacement=replacement;}
    @EventListener public void geometry(final TmdGeometryEvent event) {event.geometry=this.replacement;}
    @EventListener public void appearance(final TmdAppearanceEvent event) {
      event.appearance=new TmdObjTable1c("invalid optional appearance",new Vector3f[]{new Vector3f()},this.replacement.normal_top_08,this.replacement.primitives_10);
    }
  }
  EventManager.Access access;
  @BeforeEach void setup() throws Exception {
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true); renderer.set(GameEngine.RENDERER,new NoopApi());
    final var field=GameEngine.class.getDeclaredField("EVENT_ACCESS"); field.setAccessible(true);
    access=(EventManager.Access)field.get(null); access.initialize(GameEngine.MODS);
  }
  @AfterEach void cleanup() { access.reset(); Obj.deleteObjects(); }

  static TmdObjTable1c source() {
    final byte[] packet=new byte[24]; final var buffer=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(2,(short)320).putShort(6,(short)0);
    for(int i=0;i<3;i++) buffer.putShort(12+i*4,(short)0).putShort(14+i*4,(short)i);
    return new TmdObjTable1c("synthetic texture",new Vector3f[]{new Vector3f(0,0,-5),new Vector3f(3,0,-5),new Vector3f(0,3,-5)},
      new Vector3f[]{new Vector3f(0,0,1)},new TmdObjTable1c.Primitive[]{new TmdObjTable1c.Primitive(0,24,0x34000600,new byte[][]{packet})});
  }
  static BufferedImage paint() {
    final var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
    for(int x=0;x<2;x++)for(int y=0;y<2;y++)image.setRGB(x,y,0x204080);
    return image;
  }
  static TmdObjTable1c parsed() {
    final var data=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(12).putInt(0).putInt(0).putInt(0x41).putInt(0).putInt(1);
    data.putInt(28).putInt(3).putInt(52).putInt(3).putInt(76).putInt(1).putInt(0);
    for(final var point:new short[][]{{0,0,-5},{3,0,-5},{0,3,-5}})data.putShort(point[0]).putShort(point[1]).putShort(point[2]).putShort((short)0);
    for(int i=0;i<3;i++)data.putShort((short)0).putShort((short)0).putShort((short)4096).putShort((short)0);
    data.putInt(0x34000601).put(source().primitives_10[0].data()[0]);
    return new legend.game.types.CContainer("synthetic native",new legend.game.unpacker.FileData(data.array())).tmdPtr_00.tmd.objTable[0];
  }
  @Test void sampledFacePaintKeepsOriginalGeometryPacketsAndSurface() {
    final var original=source(); final byte[] packet=original.primitives_10[0].data()[0].clone();
    original.faceSurfaces(new SurfaceResponse[]{new SurfaceResponse(SurfaceMaterial.SKIN)});
    original.sourceFaces(new int[]{7},8);
    final var colored=DartFacePaint.paint(original,Set.of(7),paint(),true);
    assertArrayEquals(packet,original.primitives_10[0].data()[0]);
    assertEquals(original.vert_top_00[1],colored.vert_top_00[1]);
    assertNotSame(original.vert_top_00[1],colored.vert_top_00[1]);
    assertEquals(7,colored.sourceFace(0));assertEquals(original.faceSurface(0),colored.faceSurface(0));
    assertEquals(0x34000600,colored.primitives_10[0].header());
    assertTrue(colored.faceDetail().applies(0));
    final var mesh=TmdObjLoader.fromObjTable("painted",colored,0);
    assertInstanceOf(MeshObj.class,mesh);
    assertNotNull(mesh.faceDetailTexture());
    assertEquals(2,mesh.faceDetailTexture().width);
    for(final var part:mesh.meshes)for(final float value:part.vertices())assertTrue(Float.isFinite(value));
  }
  @Test void reconstructedUvBindingRejectsUnrelatedOrChangedGeometry() throws Exception {
    final var replacement=new DartHeadReconstruction();
    assertNull(replacement.paint("b84e13a11adbd3419e1e4c5b810c8bd797f9dc68af5d41fe3c4736ce5c4f584b",source()));
    assertNull(replacement.paint("0bfd5ffdc6da5b99d770e75f3cab89c86718a541f996250eb362cfb07642f6bb",source()));
    assertNull(replacement.paint("0".repeat(64),source()));
  }
  @Test void completeHeadLayerPreservesBandanaAndSeparatesHairRegion() {
    for(final int face:new int[]{20,21,23,110,121,122}) {
      final var original=source();original.sourceFaces(new int[]{face},148);
      assertSame(original,DartFacePaint.paintHead(original,paint(),false));
    }
    final var original=source();original.sourceFaces(new int[]{75},151);
    final var painted=DartFacePaint.paintHead(original,paint(),false);
    assertNotSame(original,painted);
    for(int i=0;i<3;i++) {
      assertTrue(painted.faceDetail().v(0,i)>=DartFaceMapping.load().faceRegionHeight());
      assertTrue(painted.faceDetail().u(0,i)<=DartFaceMapping.load().hairRegionWidth());
    }
  }
  @Test void battleBandanaAndHairRetainTheirMaterials() {
    final var mapping=DartFaceMapping.load();
    for(final int face:new int[]{20,21,23,75,110,121,122}) {
      final var original=source(); original.sourceFaces(new int[]{face},148);
      assertSame(original,DartFacePaint.paint(original,mapping.combatFaces(),paint(),false),"Protected face "+face);
    }
  }
  @Test void battleEyeAndMouthLandmarksAlignWithPaint() {
    final var original=source();
    original.vert_top_00[0].set(-70,38.5f,-35);
    original.vert_top_00[1].set(-70,38.5f,35);
    original.vert_top_00[2].set(-70,-11,0);
    final var colored=DartFacePaint.paint(original,DartFaceMapping.load().combatFaces(),paint(),false);
    final float eye=(float)(colored.faceDetail().v(0,0)/DartFaceMapping.load().faceRegionHeight()), mouth=(float)(colored.faceDetail().v(0,2)/DartFaceMapping.load().faceRegionHeight());
    assertTrue(eye>=.29f && eye<=.36f,"Eye landmark "+eye);
    assertTrue(mouth>=.60f && mouth<=.68f,"Mouth landmark "+mouth);
  }
  @Test void unrelatedFaceRetainsItsTextureAndPacketBytes() {
    final var original=source();
    final var colored=DartFacePaint.paint(original,Set.of(9),paint(),false);
    assertSame(original,colored);
    assertEquals(original.primitives_10[0].header(),colored.primitives_10[0].header());
    assertArrayEquals(original.primitives_10[0].data()[0],colored.primitives_10[0].data()[0]);
  }
  @Test void mappingOwnsItsCopyAndRejectsUnknownFaces() {
    final var original=source(); final int[] mapping={7}; original.sourceFaces(mapping,8); mapping[0]=3;
    assertEquals(7,original.sourceFace(0));
    assertThrows(IllegalArgumentException.class,()->original.sourceFaces(new int[]{8},8));
    assertThrows(IllegalArgumentException.class,()->original.sourceFaces(new int[]{},8));
    assertThrows(IndexOutOfBoundsException.class,()->original.sourceFace(1));
  }
  @Test void corruptResourceRejection() {
    assertThrows(java.io.IOException.class,()->DartFacePaint.read(new byte[]{1,2},"0".repeat(64)));
  }
  @Test void paintedTextureIsOwnedByItsRenderedObject() throws Exception {
    final var colored=DartFacePaint.paint(source(),Set.of(0),paint(),true);
    final var mesh=TmdObjLoader.fromObjTable("owned detail",colored,0);
    final var texture=mesh.faceDetailTexture();
    assertTrue(texture.persistent);
    final var deleted=legend.core.renderer.Texture.class.getDeclaredField("deleted");deleted.setAccessible(true);
    assertFalse(deleted.getBoolean(texture));
    mesh.delete();assertTrue(deleted.getBoolean(texture));
    mesh.delete();assertTrue(deleted.getBoolean(texture));
    assertThrows(IllegalStateException.class,()->mesh.faceDetailTexture(texture));
  }
  @Test void unsupportedDetailPacketsAreRejectedBeforeUpload() {
    final var original=source();
    final var translucent=new TmdObjTable1c("translucent",original.vert_top_00,original.normal_top_08,
      new TmdObjTable1c.Primitive[]{new TmdObjTable1c.Primitive(0,24,0x36000600,original.primitives_10[0].data())});
    final var detail=new TmdFaceDetail(ByteBuffer.allocate(4),1,1,new float[][]{{0,0,1,0,0,1}});
    assertThrows(IllegalArgumentException.class,()->translucent.faceDetail(detail));
    final var quad=new TmdFaceDetail(ByteBuffer.allocate(4),1,1,new float[][]{{0,0,1,0,0,1,1,1}});
    assertThrows(IllegalArgumentException.class,()->original.faceDetail(quad));
  }
  @Test void detailOwnsItsPixelsAndCoordinatesAndRejectsInvalidData() {
    final var pixels=ByteBuffer.allocateDirect(4).putInt(0x204080ff).flip();
    final float[][] uv={{0,0,1,0,0,1}};
    final var detail=new TmdFaceDetail(pixels,1,1,uv);
    pixels.putInt(0,0);uv[0][0]=.75f;
    assertEquals(0,detail.u(0,0));assertEquals(0x204080ff,detail.pixels().getInt());
    assertThrows(java.nio.ReadOnlyBufferException.class,()->detail.pixels().put(0,(byte)0));
    assertThrows(IllegalArgumentException.class,()->new TmdFaceDetail(ByteBuffer.allocate(4),2049,1,uv));
    assertThrows(IllegalArgumentException.class,()->new TmdFaceDetail(ByteBuffer.allocate(4),1,1,new float[][]{{Float.NaN,0,1,0,0,1}}));
    assertThrows(IllegalArgumentException.class,()->new TmdFaceDetail(ByteBuffer.allocate(4),1,1,new float[][]{{0,0,1,0,-.1f,1}}));
    assertThrows(IllegalArgumentException.class,()->source().faceDetail(new TmdFaceDetail(ByteBuffer.allocate(4),1,1,new float[][]{})));
  }
  @Test void appearanceFailurePreservesAlreadyPreparedGeometry() {
    final var source=parsed();
    final var replacement=new TmdObjTable1c("prepared geometry",new Vector3f[]{new Vector3f(9,0,-5),new Vector3f(3,0,-5),new Vector3f(0,3,-5)},source.normal_top_08,source.primitives_10);
    GameEngine.EVENTS.register(new FailingAppearance(replacement));
    final var mesh=TmdObjLoader.fromObjTable("fallback",source,0);
    assertTrue(java.util.Arrays.stream(mesh.meshes).anyMatch(part->part.vertices()[0]==9));
    assertEquals(0,source.vert_top_00[0].x);
  }
  @Test void appearanceLoadsWhenOtherModClassesAreIsolated() throws Exception {
    final var code=DartFacePaint.class.getProtectionDomain().getCodeSource().getLocation();
    final String resource=DartFacePaint.class.getResource("/charhd-experiment/dart-face-mapping-v1.json").toString();
    final var resources=new java.net.URI(resource.substring(0,resource.indexOf("charhd-experiment/"))).toURL();
    try(final var loader=new java.net.URLClassLoader(new java.net.URL[]{code,resources},getClass().getClassLoader()) {
      @Override protected Class<?> loadClass(final String name,final boolean resolve) throws ClassNotFoundException {
        if(name.startsWith("modelshd."))throw new ClassNotFoundException("Other mod isolated: "+name);
        if(name.startsWith("charhd.")) {
          synchronized(getClassLoadingLock(name)) {
            Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);
            if(resolve)resolveClass(type);return type;
          }
        }
        return super.loadClass(name,resolve);
      }
    }) {
      final var type=loader.loadClass("charhd.DartFacePaint");
      final var listener=type.getConstructor().newInstance();final var original=source();
      final var event=new TmdAppearanceEvent(original,original,0);
      type.getMethod("apply",TmdAppearanceEvent.class).invoke(listener,event);
      assertSame(original,event.appearance);
      java.lang.ref.Reference.reachabilityFence(listener);
    }
  }
  @Test void shippedOptInResourceLoadsAndUnknownSourceRetainsGeometry() throws Exception {
    final var listener=new DartFacePaint(); final var source=source();
    final var event=new TmdAppearanceEvent(source,source,0);listener.apply(event);
    assertSame(source,event.appearance);
  }
}
