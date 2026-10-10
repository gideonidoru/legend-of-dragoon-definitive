package charhd;

import com.google.gson.JsonParser;
import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.UvAdjustmentMetrics14;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.events.EventManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Explicit owner-input construction probe. Development resources never enter normal packaging. */
public final class PartyCandidateSourceProbe {
  public static final class CandidateListener {
    private final ReconstructedCharacters characters;
    CandidateListener(final ReconstructedCharacters characters) { this.characters = characters; }
    @org.legendofdragoon.modloader.events.EventListener
    public void apply(final TmdAppearanceEvent event) throws java.io.IOException { this.characters.apply(event); }
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("files authoring-root report");
    final Path files = Path.of(args[0]), root = Path.of(args[1]).toRealPath();
    final var candidates = new ReconstructedCharacters((path, limit) -> {
      final Path resource = root.resolve(path.equals("characters.json") ? "candidate-manifest.json" : path).normalize();
      if(!resource.startsWith(root) || Files.size(resource) > limit) throw new java.io.IOException("Invalid development resource");
      return Files.readAllBytes(resource);
    });
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true); renderer.set(GameEngine.RENDERER, new NoopApi());
    final var field = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    field.setAccessible(true); final var access = (EventManager.Access)field.get(null);
    final var audit = JsonParser.parseString(Files.readString(root.resolve("source-audit.json"))).getAsJsonObject();
    final var rows = new ArrayList<Map<String, Object>>();
    try {
      for(final var actorValue : audit.getAsJsonArray("characters")) {
        final var actor = actorValue.getAsJsonObject();
        final String name = actor.get("character").getAsString();
        if(name.equals("dart")) continue; // Exact promoted Dart uses its separate bundled-source gate.
        final var fit = JsonParser.parseString(Files.readString(root.resolve(name + "/candidate-v1/fit-receipt.json"))).getAsJsonObject();
        for(final var modelValue : actor.getAsJsonArray("models")) {
          final var model = modelValue.getAsJsonObject();
          final String form = model.get("form").getAsString();
          int head = -1;
          for(final var fitValue : fit.getAsJsonArray("forms")) {
            final var record = fitValue.getAsJsonObject();
            if(form.equals(record.get("form").getAsString())) head = record.get("sourcePartIndex").getAsInt();
          }
          if(head < 0) throw new AssertionError("Missing candidate head");
          for(final String route : List.of("indexed", "rgba", "mapped")) for(final boolean models : new boolean[]{false, true}) for(final boolean chars : new boolean[]{false, true}) {
            access.reset(); access.initialize(GameEngine.MODS);
            final var modelMod = models ? Class.forName("modelshd.ModelsHdMod").getConstructor().newInstance() : null;
            final var candidateMod = chars ? new CandidateListener(candidates) : null;
            if(candidateMod != null) GameEngine.EVENTS.register(candidateMod);
            final byte[] input = Files.readAllBytes(files.resolve(model.get("source").getAsString()));
            if(!legend.definitive.textures.TexturePilot.sha256(input).equals(model.get("sourceSha256").getAsString())) throw new AssertionError("Source drift");
            final var source = new CContainer("party candidate source", new FileData(input));
            int textures = 0, vertices = 0;
            for(int index = 0; index < source.tmdPtr_00.tmd.objTable.length; index++) {
              final var table = source.tmdPtr_00.tmd.objTable[index];
              if(route.equals("rgba")) for(final var primitive : table.primitives_10) if((primitive.header() & 0x04000000) != 0) UvAdjustmentMetrics14.PNG.apply(primitive);
              final var packets = Arrays.stream(table.primitives_10).flatMap(p -> Arrays.stream(p.data())).map(byte[]::clone).toArray(byte[][]::new);
              final var positions = Arrays.stream(table.vert_top_00).map(org.joml.Vector3f::new).toArray(org.joml.Vector3f[]::new);
              final var object = route.equals("mapped") ? TmdObjLoader.fromObjTableMapped("candidate mapped", table, (clut, u, v, header) -> new float[]{.2f, .3f}) :
                TmdObjLoader.fromObjTable("candidate", table, 0, route.equals("rgba") ? 256 : 0, route.equals("rgba") ? 256 : 0);
              if(object.faceDetailTexture() != null) {
                textures++;
                if(index != head || object.faceDetailTexture().width != 2048) throw new AssertionError("Wrong painted part");
              }
              for(final var mesh : object.meshes) {
                for(final float value : mesh.vertices()) if(!Float.isFinite(value)) throw new AssertionError("Nonfinite candidate vertex");
                vertices += mesh.vertices().length / 16;
              }
              if(!Arrays.equals(positions, table.vert_top_00) || !Arrays.deepEquals(packets, Arrays.stream(table.primitives_10).flatMap(p -> Arrays.stream(p.data())).toArray(byte[][]::new))) throw new AssertionError("Source tables changed");
              object.delete();
            }
            if(textures != (chars ? 1 : 0)) throw new AssertionError(name + " " + form + " did not bind one head");
            rows.add(Map.of("character", name, "form", form, "route", route, "modelsHd", models, "charHd", chars, "headPart", head, "textures", textures, "vertices", vertices));
            java.lang.ref.Reference.reachabilityFence(modelMod); java.lang.ref.Reference.reachabilityFence(candidateMod);
            Obj.deleteObjects(); legend.core.renderer.Texture.deleteTextures();
          }
        }
      }
    } finally { access.reset(); Obj.deleteObjects(); legend.core.renderer.Texture.deleteTextures(); }
    Files.writeString(Path.of(args[2]), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status", "passed", "scope", "Development candidate CPU construction only, not native gameplay or Steam Deck", "checks", rows)) + "\n");
    System.out.println("Party candidate construction passed: " + rows.size() + " combinations");
  }
}
