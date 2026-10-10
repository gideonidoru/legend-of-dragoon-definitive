package charhd;

import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.TmdObjTable1c;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import modelshd.GeometryPass;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.EventManager;
import java.nio.file.*;
import java.util.*;

/** Owner-input opt-in, headless native geometry/paint construction, never a gameplay test. */
public final class DartExperimentProbe {
  public static final class Geometry {
    private final GeometryPass pass=new GeometryPass();
    private final Path overrides;
    private final IdentityHashMap<TmdObjTable1c,TmdObjTable1c> prepared=new IdentityHashMap<>();
    Geometry(final Path overrides)throws Exception {this.overrides=overrides;}
    @EventListener public void geometry(final TmdGeometryEvent event)throws java.io.IOException {
      this.pass.prepare(event,this.overrides);this.prepared.put(event.source,event.geometry);
    }
  }
  public static void main(final String[] args)throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("files study report");
    final var renderer=GameEngine.RENDERER.getClass().getDeclaredField("api");renderer.setAccessible(true);renderer.set(GameEngine.RENDERER,new NoopApi());
    final var field=GameEngine.class.getDeclaredField("EVENT_ACCESS");field.setAccessible(true);final var access=(EventManager.Access)field.get(null);
    final List<Map<String,Object>> rows=new ArrayList<>();
    try {
      for(final String form:List.of("field","combat"))for(final boolean rgba:new boolean[]{false,true})for(final boolean painted:new boolean[]{false,true}) {
        access.reset();access.initialize(GameEngine.MODS);
        final Path study=Path.of(args[1]);final var geometry=new Geometry(study.resolve("parts"));GameEngine.EVENTS.register(geometry);
        final var facePaint=painted?new DartFacePaint():null;
        final String model=form.equals("field")?"SECT/DRGN21.BIN/101/0":"characters/dart/models/combat/32";
        final byte[] input=Files.readAllBytes(Path.of(args[0]).resolve(model));
        final var tables=new CContainer("Dart experimental source",new FileData(input)).tmdPtr_00.tmd.objTable;
        if(rgba)for(final var table:tables)for(final var primitive:table.primitives_10)
          if((primitive.header()&0x04000000)!=0)legend.game.tmd.UvAdjustmentMetrics14.PNG.apply(primitive);
        long floats=0;
        int paintTextures=0;
        for(final var table:tables) {
          System.gc(); // Deliberate weak-listener lifetime regression stress, never gameplay code.
          final var positions=Arrays.stream(table.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
          final var bytes=Arrays.stream(table.primitives_10).flatMap(p->Arrays.stream(p.data())).map(byte[]::clone).toArray(byte[][]::new);
          final var result=TmdObjLoader.fromObjTable("Dart " + form,table,0,rgba?(form.equals("field")?64:256):0,rgba?(form.equals("field")?112:256):0);
          final var prepared=geometry.prepared.get(table);
          if(prepared==null)throw new AssertionError("Missing geometry preparation event");
          final var control=new TmdObjTable1c("private prepared geometry control",prepared.vert_top_00,prepared.normal_top_08,prepared.primitives_10);
          final var expected=TmdObjLoader.fromObjTable("control",control,0,rgba?(form.equals("field")?64:256):0,rgba?(form.equals("field")?112:256):0);
          if(!geometryRows(expected).equals(geometryRows(result)))
            throw new AssertionError("Appearance changed prepared geometry: " + form + " rgba=" + rgba + " part=" + java.util.Arrays.asList(tables).indexOf(table));
          expected.delete();
          if(result.faceDetailTexture()!=null)paintTextures++;
          for(final var mesh:result.meshes)for(final float value:mesh.vertices()){if(!Float.isFinite(value))throw new AssertionError("Nonfinite native data");floats++;}
          if(!Arrays.equals(positions,table.vert_top_00))throw new AssertionError("Source CPU geometry changed");
          final var after=Arrays.stream(table.primitives_10).flatMap(p->Arrays.stream(p.data())).toArray(byte[][]::new);
          if(!Arrays.deepEquals(bytes,after))throw new AssertionError("Source packets changed");
          result.delete();
        }
        if(paintTextures!=(painted?1:0))throw new AssertionError("Expected exactly one painted head when enabled");
        rows.add(Map.of("form",form,"facePaint",painted,"simulatedRgbaBody",rgba,"faceTextures",paintTextures,"parts",tables.length,"finiteNativeFloats",floats,"preparedGeometryUnchanged",true,"originalCpuTablesUnchanged",true));
        // The event bus holds weak references; retain test listeners for the entire sample.
        java.lang.ref.Reference.reachabilityFence(facePaint);
        java.lang.ref.Reference.reachabilityFence(geometry);
      }
    } finally {access.reset();Obj.deleteObjects();}
    Files.writeString(Path.of(args[2]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","passed","scope","Native CPU construction and source immutability; no GPU/gameplay/Deck acceptance","checks",rows))+"\n");
    System.out.println("Dart native experimental probe passed: " + rows.size() + " combinations");
  }
  private static Map<List<Integer>,Integer> geometryRows(final legend.core.renderer.MeshObj object) {
    final Map<List<Integer>,Integer> result=new HashMap<>();
    for(final var mesh:object.meshes) {
      final var vertices=mesh.vertices();
      for(int vertex=0;vertex<vertices.length;vertex+=16) {
        final List<Integer> key=new ArrayList<>();
        for(int component=0;component<7;component++)key.add(Float.floatToIntBits(vertices[vertex+component]));
        result.merge(key,1,Integer::sum);
      }
    }
    return result;
  }
}
