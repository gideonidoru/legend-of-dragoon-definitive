package charhd;

import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.game.tmd.*;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.events.EventManager;
import java.nio.file.*;
import java.util.*;

/** Explicit owner-input CPU probe of bundled resources. No external model overrides or GPU draw. */
public final class ReconstructionSourceProbe {
  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("files report");
    final var renderer=GameEngine.RENDERER.getClass().getDeclaredField("api");renderer.setAccessible(true);renderer.set(GameEngine.RENDERER,new NoopApi());
    final var field=GameEngine.class.getDeclaredField("EVENT_ACCESS");field.setAccessible(true);final var access=(EventManager.Access)field.get(null);
    final Path files=Path.of(args[0]);final var rows=new ArrayList<Map<String,Object>>();
    try {
      for(final String form:List.of("field","combat"))for(final String route:List.of("indexed","rgba","mapped"))for(final boolean models:new boolean[]{false,true})for(final boolean chars:new boolean[]{false,true}) {
        access.reset();access.initialize(GameEngine.MODS);
        final var modelMod=models?Class.forName("modelshd.ModelsHdMod").getConstructor().newInstance():null;
        final var charMod=chars?new CharHdMod():null;
        final String path=form.equals("field")?"SECT/DRGN21.BIN/101/0":"characters/dart/models/combat/32";
        final var source=new CContainer("bundled CharHD source",new FileData(Files.readAllBytes(files.resolve(path))));
        int textures=0,vertices=0;
        for(int index=0;index<source.tmdPtr_00.tmd.objTable.length;index++) {
          final var table=source.tmdPtr_00.tmd.objTable[index];
          if(route.equals("rgba"))for(final var primitive:table.primitives_10)if((primitive.header()&0x04000000)!=0)UvAdjustmentMetrics14.PNG.apply(primitive);
          final var originalPackets=Arrays.stream(table.primitives_10).flatMap(p->Arrays.stream(p.data())).map(byte[]::clone).toArray(byte[][]::new);
          final var positions=Arrays.stream(table.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
          final var object=route.equals("mapped")?TmdObjLoader.fromObjTableMapped("CharHD body mapping",table,(clut,u,v,header)->new float[]{.2f,.3f}):
            TmdObjLoader.fromObjTable("CharHD body",table,0,route.equals("rgba")?256:0,route.equals("rgba")?256:0);
          if(object.faceDetailTexture()!=null) {textures++;if(index!=7 || object.faceDetailTexture().width!=2048)throw new AssertionError("Wrong supplemental texture");}
          if(index==7 && chars && object.faceDetailTexture()==null)throw new AssertionError("Bundled CharHD head did not activate without manual overrides");
          for(final var mesh:object.meshes) {for(final var value:mesh.vertices())if(!Float.isFinite(value))throw new AssertionError("Nonfinite mesh");vertices+=mesh.vertices().length/16;}
          if(!Arrays.equals(positions,table.vert_top_00) || !Arrays.deepEquals(originalPackets,Arrays.stream(table.primitives_10).flatMap(p->Arrays.stream(p.data())).toArray(byte[][]::new)))throw new AssertionError("Original CPU tables changed");
          object.delete();
        }
        if(textures!=(chars?1:0))throw new AssertionError("Wrong painted head count");
        rows.add(Map.of("form",form,"route",route,"modelsHd",models,"charHd",chars,"supplementalTextures",textures,"vertices",vertices,"sourceUnchanged",true));
        java.lang.ref.Reference.reachabilityFence(modelMod);java.lang.ref.Reference.reachabilityFence(charMod);
      }
    } finally {access.reset();Obj.deleteObjects();}
    Files.writeString(Path.of(args[1]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","passed","scope","Bundled CPU geometry/material construction, not gameplay or Steam Deck validation","checks",rows))+"\n");
    System.out.println("CharHD bundled reconstruction probe passed: "+rows.size()+" combinations");
  }
}
