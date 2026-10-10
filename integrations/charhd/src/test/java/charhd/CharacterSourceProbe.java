package charhd;

import legend.core.GameEngine;
import legend.core.gte.ModelPart10;
import legend.core.renderer.MeshObj;
import legend.core.renderer.noop.NoopApi;
import legend.game.types.CContainer;
import legend.game.types.Model124;
import legend.game.unpacker.FileData;
import legend.game.tmd.UvAdjustmentMetrics14;
import legend.definitive.materials.CharacterAppearance;
import legend.definitive.materials.MaterialAtlas;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.legendofdragoon.modloader.events.EventManager;

/** Explicit local source opt-in: native CPU mesh construction only, no GL/game window. */
public final class CharacterSourceProbe {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("files candidates report");
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true);
    renderer.set(GameEngine.RENDERER, new NoopApi());
    final var accessField = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    accessField.setAccessible(true);
    final var access = (EventManager.Access)accessField.get(null);
    final Path files = Path.of(args[0]), candidates = Path.of(args[1]);
    final var records = JsonParser.parseString(Files.readString(candidates.resolve("census.json"))).getAsJsonObject().getAsJsonArray("records");
    final List<Map<String, Object>> results = new ArrayList<>();
    try {
      for(final var record : records) {
        final var entry = record.getAsJsonObject();
        final String character = entry.get("character").getAsString(), form = entry.get("form").getAsString();
        final byte[] rawModel = Files.readAllBytes(files.resolve(entry.get("model").getAsString()));
        final byte[] rawTim = Files.readAllBytes(files.resolve(entry.get("texture").getAsString()));
        final Path formFolder = candidates.resolve(character).resolve(form);
        final Path packFolder = Files.isDirectory(formFolder.resolve("authored-armor-v1")) ? formFolder.resolve("authored-armor-v1") : formFolder.resolve("restored4");
        final var atlas = MaterialAtlas.read(packFolder, rawModel, rawTim);
        final Path metadata = Path.of("integrations/charhd/production/candidates").resolve(entry.get("modelSha256").getAsString()).resolve("surfaces.json");
        final var surfaces = Files.isRegularFile(metadata) ? CharacterSurfaces.read(Files.readAllBytes(metadata), atlas) : Map.<Integer, legend.core.renderer.SurfaceResponse>of();
        for(final boolean geometry : new boolean[]{false, true}) {
          access.reset();
          access.initialize(GameEngine.MODS);
          if(geometry) {
            final Class<?> mod = Class.forName("modelshd.ModelsHdMod");
            mod.getConstructor().newInstance();
          }
          final var source = new CContainer("CharHD source probe", new FileData(rawModel.clone()));
          final var model = new Model124("CharHD source probe");
          model.uvAdjustments_9d = new UvAdjustmentMetrics14(1, 320, 256);
          model.modelParts_00 = new ModelPart10[source.tmdPtr_00.tmd.objTable.length];
          for(int part = 0; part < model.modelParts_00.length; part++) {
            model.modelParts_00[part] = new ModelPart10();
            model.modelParts_00[part].tmd_08 = source.tmdPtr_00.tmd.objTable[part];
            legend.game.Models.adjustPartUvs(model.modelParts_00[part], model.uvAdjustments_9d);
          }
          final var appearance = CharacterAppearance.create(model, atlas,
            new legend.game.tim.Tim(new FileData(rawTim)).getClutRect().w / 16, surfaces, rawModel);
          model.materialAppearance = appearance;
          if(!appearance.applies(model)) throw new AssertionError("Native actor rejected");
          int vertices = 0;
          for(int part = 0; part < model.modelParts_00.length; part++) for(final var mesh : ((MeshObj)appearance.mesh(part)).meshes) {
            final float[] data = mesh.vertices();
            for(int row = 0; row < data.length; row += 16) {
              vertices++;
              for(int field = 0; field < 16; field++) if(!Float.isFinite(data[row + field])) throw new AssertionError("Nonfinite character vertex");
              if(((int)data[row + 15] & 2) != 0 && (data[row + 7] < 0 || data[row + 7] > 1 || data[row + 8] < 0 || data[row + 8] > 1))
                throw new AssertionError("Character atlas UV outside image");
            }
          }
          model.animateTextures_ec[0] = true;
          if(appearance.applies(model)) throw new AssertionError("Texture animation failed to retain original");
          model.animateTextures_ec[0] = false;
          model.modelParts_00[0].tmd_08.retainNativeVertexIndices();
          if(appearance.applies(model)) throw new AssertionError("Native deformation failed to retain original");
          model.deleteModelParts();
          if(model.materialAppearance != null || appearance.applies(model)) throw new AssertionError("Optional material did not release");
          results.add(Map.of("character", character, "form", form, "modelsHd", geometry, "nativeVertices", vertices,
            "parts", model.modelParts_00.length, "result", "passed", "rgbaBytes", (long)atlas.width() * atlas.height() * 4));
        }
      }
    } finally { access.reset(); }
    final var report = Map.of("scope", "Source-bound atlas validation, native CPU mesh construction, ModelsHD on/off and lifetime/fallback; no GPU draw/gameplay/Deck proof", "checks", results);
    Files.writeString(Path.of(args[2]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    System.out.println("CharHD source probe passed " + results.size() + " actor combinations");
  }
}
