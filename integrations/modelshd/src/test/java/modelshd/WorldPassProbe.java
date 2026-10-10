package modelshd;

import com.google.gson.JsonParser;
import legend.core.GameEngine;
import legend.core.renderer.MeshObj;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdWithId;
import legend.game.tmd.UvAdjustmentMetrics14;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.events.EventManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** Headless native CPU construction for private source data, no source bytes in reports. */
public final class WorldPassProbe {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("Usage: WorldPassProbe FILES CATALOG OUTPUT_REPORT");
    final Path files = Path.of(args[0]);
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api"); renderer.setAccessible(true); renderer.set(GameEngine.RENDERER,new NoopApi());
    final var manager = GameEngine.class.getDeclaredField("EVENT_ACCESS"); manager.setAccessible(true);
    final var access = (EventManager.Access)manager.get(null); access.initialize(GameEngine.MODS);
    final var mod = new ModelsHdMod(); // Actual event-driven loading; keep listener alive.
    final var catalog = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
    final var jobs = new java.util.ArrayList<String>();
    for(final var row : catalog.getAsJsonArray("models")) jobs.add(row.getAsJsonObject().getAsJsonArray("appearances").get(0).getAsJsonObject().get("source").getAsString());
    try(var input = ModelsHdMod.class.getResourceAsStream("/modelshd/models/roster.json")) {
      for(final var row : JsonParser.parseString(new String(input.readAllBytes())).getAsJsonObject().getAsJsonArray("models")) {
        final var value = row.getAsJsonObject(); jobs.add("characters/"+value.get("character").getAsString()+"/models/"+value.get("form").getAsString()+"/32");
      }
    }
    final var world = JsonParser.parseString(Files.readString(Path.of("integrations/modelshd/src/main/resources/modelshd/models/world-pass.json"))).getAsJsonObject();
    final var expected = new HashSet<String>();
    for(final var row : world.getAsJsonArray("partPacks")) expected.add(row.getAsJsonObject().get("sourceGeometrySha256").getAsString());
    final var verified = new HashSet<String>(); long floats = 0; int changedInstances = 0;
    for(int index = 0; index < jobs.size(); index++) {
      final byte[] bytes = Files.readAllBytes(files.resolve(jobs.get(index)));
      final var data = new FileData(bytes.clone());
      // This probe isolates TMD construction, not original auxiliary stream decoding.
      final var parts = new TmdWithId("private probe",data.slice(data.readInt(0))).tmd.objTable;
      for(final var part : parts) {
        final String identity = ModelPack.identity(new TmdObjTable1c[]{part});
        final var sourcePoints = java.util.Arrays.stream(part.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
        final var mesh = (MeshObj)part.getObj();
        for(final var layer : mesh.meshes) for(final float number : layer.vertices()) {
          if(!Float.isFinite(number)) throw new AssertionError("Nonfinite native data"); floats++;
        }
        for(int i=0;i<sourcePoints.length;i++) if(!sourcePoints[i].equals(part.vert_top_00[i])) throw new AssertionError("CPU source table changed");
        if(expected.contains(identity)) {
          // Rendering data must actually grow; resource existence alone is insufficient.
          final int sourceVertices = java.util.Arrays.stream(part.primitives_10).mapToInt(p->p.data().length*((p.header()>>>24&8)==0?3:4)).sum();
          final int actualVertices = java.util.Arrays.stream(mesh.meshes).mapToInt(m->m.vertices().length/16).sum();
          if(actualVertices <= sourceVertices) {
            new GeometryPass().prepare(new legend.game.modding.events.tmd.TmdGeometryEvent(part,0,0,0),Path.of("model-packs/modelshd/parts"));
            throw new AssertionError("Expected smoothing did not activate: container=" + index + " part=" + identity + " sourceCorners=" + sourceVertices + " renderedCorners=" + actualVertices);
          }
          verified.add(identity);changedInstances++;
        }
        part.delete();
        for(final var primitive : part.primitives_10) if((primitive.header()&0x04000000)!=0) UvAdjustmentMetrics14.PNG.apply(primitive);
        part.rebuildObj(256,128);
        for(final var layer : ((MeshObj)part.getObj()).meshes) for(final float number : layer.vertices()) if(!Float.isFinite(number)) throw new AssertionError("Nonfinite typed PNG data");
        part.delete();
      }
      Obj.deleteObjects();
      if((index+1)%100==0) System.out.println("Verified " + (index+1) + " source containers");
    }
    if(jobs.size()!=1343 || !verified.equals(expected)) throw new AssertionError("Incomplete native part coverage");
    final var report = new com.google.gson.JsonObject();
    report.addProperty("result","passed");report.addProperty("containers",jobs.size());report.addProperty("uniqueCustomParts",verified.size());
    report.addProperty("customPartInstances",changedInstances);report.addProperty("finiteNativeFloats",floats);
    report.addProperty("scope","Actual event-driven native CPU mesh construction with no-op allocation and simulated typed PNG addressing. Source CPU tables unchanged. No GL draw, auxiliary playback, real CharHD gameplay or physical Deck proof.");
    Files.writeString(Path.of(args[2]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    System.out.println(report);access.reset();java.lang.ref.Reference.reachabilityFence(mod);
  }
}
