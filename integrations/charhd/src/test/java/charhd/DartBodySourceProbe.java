package charhd;

import com.google.gson.JsonParser;
import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.Texture;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.UvAdjustmentMetrics14;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.events.EventManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual bounded reader/loader checks for an explicitly staged full Dart candidate. */
public final class DartBodySourceProbe {
  public static final class Listener {
    private final ReconstructedCharacters characters;
    Listener(final ReconstructedCharacters characters) { this.characters = characters; }
    @org.legendofdragoon.modloader.events.EventListener
    public void apply(final TmdAppearanceEvent event) throws java.io.IOException { this.characters.apply(event); }
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("files candidate-resources report");
    final Path files = Path.of(args[0]), root = Path.of(args[1]).toRealPath();
    final var candidate = new ReconstructedCharacters((path, limit) -> {
      final Path resource = root.resolve(path).toRealPath();
      if(!resource.startsWith(root) || Files.size(resource) > limit) throw new java.io.IOException("Invalid development resource");
      return Files.readAllBytes(resource);
    });
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true); renderer.set(GameEngine.RENDERER, new NoopApi());
    final var field = GameEngine.class.getDeclaredField("EVENT_ACCESS"); field.setAccessible(true);
    final var access = (EventManager.Access)field.get(null);
    final var audit = JsonParser.parseString(Files.readString(root.resolve("source-audit.json"))).getAsJsonObject();
    final var checks = new ArrayList<Map<String, Object>>();
    try {
      for(final var actorValue : audit.getAsJsonArray("characters")) {
        final var actor = actorValue.getAsJsonObject();
        if(!actor.get("character").getAsString().equals("dart")) continue;
        for(final var value : actor.getAsJsonArray("models")) {
          final var record = value.getAsJsonObject(); final String form = record.get("form").getAsString();
          for(final String route : List.of("indexed", "rgba", "mapped")) for(final boolean models : new boolean[]{false,true}) for(final boolean characters : new boolean[]{false,true}) {
            access.reset(); access.initialize(GameEngine.MODS);
            final var modelMod = models ? Class.forName("modelshd.ModelsHdMod").getConstructor().newInstance() : null;
            final var listener = characters ? new Listener(candidate) : null;
            if(listener != null) GameEngine.EVENTS.register(listener);
            final byte[] input = Files.readAllBytes(files.resolve(record.get("source").getAsString()));
            if(!legend.definitive.textures.TexturePilot.sha256(input).equals(record.get("sourceSha256").getAsString())) throw new AssertionError("Source drift");
            final var source = new CContainer("Dart body source", new FileData(input));
            final Set<Texture> uploads = Collections.newSetFromMap(new IdentityHashMap<>()); final var objects = new ArrayList<Obj>();
            int painted = 0, vertices = 0;
            for(final var table : source.tmdPtr_00.tmd.objTable) {
              if(route.equals("rgba")) for(final var primitive : table.primitives_10) if((primitive.header() & 0x04000000) != 0) UvAdjustmentMetrics14.PNG.apply(primitive);
              final var packets = Arrays.stream(table.primitives_10).flatMap(p -> Arrays.stream(p.data())).map(byte[]::clone).toArray(byte[][]::new);
              final var positions = Arrays.stream(table.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
              final var obj = route.equals("mapped") ? TmdObjLoader.fromObjTableMapped("Dart mapped body", table, (clut,u,v,header) -> new float[]{.2f,.3f}) :
                TmdObjLoader.fromObjTable("Dart body", table, 0, route.equals("rgba") ? 256 : 0, route.equals("rgba") ? 256 : 0);
              if(obj.faceDetailTexture() != null) { painted++; uploads.add(obj.faceDetailTexture()); if(obj.faceDetailTexture().width != 2048) throw new AssertionError("Wrong detail dimensions"); }
              for(final var mesh : obj.meshes) { for(final float number : mesh.vertices()) if(!Float.isFinite(number)) throw new AssertionError("Nonfinite body"); vertices += mesh.vertices().length / 16; }
              if(!Arrays.equals(positions,table.vert_top_00) || !Arrays.deepEquals(packets,Arrays.stream(table.primitives_10).flatMap(p -> Arrays.stream(p.data())).toArray(byte[][]::new))) throw new AssertionError("Source tables changed");
              objects.add(obj);
            }
            if(painted != (characters ? source.tmdPtr_00.tmd.objTable.length : 0) || uploads.size() != (characters ? 2 : 0)) throw new AssertionError("Expected all rigid parts with exactly two shared images");
            checks.add(Map.of("form",form,"route",route,"modelsHd",models,"charHdCandidate",characters,"paintedParts",painted,"uniqueGpuImages",uploads.size(),"vertices",vertices));
            objects.forEach(Obj::delete); Obj.deleteObjects(); Texture.deleteTextures();
            java.lang.ref.Reference.reachabilityFence(modelMod); java.lang.ref.Reference.reachabilityFence(listener);
          }
        }
      }
    } finally {access.reset();Obj.deleteObjects();Texture.deleteTextures();}
    if(checks.size()!=24) throw new AssertionError("Incomplete source matrix");
    Files.writeString(Path.of(args[2]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status","passed","scope","CPU construction and shared-image ownership; not native animation or Steam Deck acceptance","checks",checks))+"\n");
    System.out.println("Dart full-body construction passed: "+checks.size()+" combinations");
  }
}
