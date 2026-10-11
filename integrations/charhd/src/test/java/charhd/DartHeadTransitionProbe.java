// Source-bound field/battle head regression, AGPL v3; see LICENSE.
package charhd;

import com.google.gson.GsonBuilder;
import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.Texture;
import legend.core.renderer.noop.NoopApi;
import legend.definitive.models.TmdGeometryPack;
import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjTable1c;
import legend.game.types.CContainer;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.events.EventManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Owner-input CPU/cache lifecycle check. Does not launch a game or draw on a GPU. */
public final class DartHeadTransitionProbe {
  private static final String FIELD_HEAD = "8814dc36066fced448de5dd2975efb67fa2de06daf88a63eb00cfac9ed648818";
  private static final String BATTLE_HEAD = "c7dcb52fe0bc114dceba14f7737a3acd29e478834b0d25538af94af23346253c";
  private static final List<String> FIELD_MODELS = List.of(
    "SECT/DRGN21.BIN/101/0", "SECT/DRGN21.BIN/32/0", "SECT/DRGN21.BIN/41/0", "SECT/DRGN21.BIN/116/0"
  );

  private static TmdObjTable1c head(final Path files, final String model) throws Exception {
    return new CContainer(model, new FileData(Files.readAllBytes(files.resolve(model)))).tmdPtr_00.tmd.objTable[7];
  }

  private static Obj checkHead(final TmdObjTable1c source, final String expected, final String phase) throws Exception {
    final var originals = new TmdObjTable1c[]{source};
    final String originalIdentity = TmdGeometryPack.identity(originals);
    final var geometry = GameEngine.EVENTS.postEvent(new TmdGeometryEvent(source, 0, 0, 0)).geometry;
    final var appearance = GameEngine.EVENTS.postEvent(new TmdAppearanceEvent(source, geometry, 0)).appearance;
    final String actual = TmdGeometryPack.identity(new TmdObjTable1c[]{appearance});
    if(!expected.equals(actual) || appearance.faceDetail() == null) {
      throw new AssertionError(phase + ": expected " + expected + ", received " + actual + " from " + originalIdentity);
    }
    final Obj rendered = source.getObj();
    if(rendered.faceDetailTexture() == null || rendered.faceDetailTexture().width != 2048) {
      throw new AssertionError(phase + ": cached mesh lost the accepted head material");
    }
    if(!originalIdentity.equals(TmdGeometryPack.identity(originals))) throw new AssertionError("Native source changed");
    return rendered;
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("files report");
    final var renderer = GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true);
    renderer.set(GameEngine.RENDERER, new NoopApi());
    final var events = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    events.setAccessible(true);
    final var access = (EventManager.Access)events.get(null);
    final Path files = Path.of(args[0]);
    final var checks = new ArrayList<Map<String, Object>>();
    try {
      for(final boolean models : new boolean[]{false, true}) {
        access.reset();
        access.initialize(GameEngine.MODS);
        final var modelMod = models ? Class.forName("modelshd.ModelsHdMod").getConstructor().newInstance() : null;
        final var charMod = new CharHdMod();
        for(final String path : FIELD_MODELS) {
          for(int cycle = 0; cycle < 8; cycle++) {
            final var field = head(files, path);
            final Obj before = checkHead(field, FIELD_HEAD, "field before battle");
            final var battle = head(files, "characters/dart/models/combat/32");
            checkHead(battle, BATTLE_HEAD, "battle preparation");
            if(before != checkHead(field, FIELD_HEAD, "retained field during preparation")) {
              throw new AssertionError("Battle preparation changed the field cache");
            }
            // Retire both state-owned meshes, then check reused CPU tables and fresh reloads.
            field.delete();
            battle.delete();
            Obj.deleteObjects();
            Texture.deleteTextures();
            checkHead(field, FIELD_HEAD, "field rebuilt after battle");
            field.delete();
            final var returned = head(files, path);
            checkHead(returned, FIELD_HEAD, "fresh field after battle");
            returned.delete();
            Obj.deleteObjects();
            Texture.deleteTextures();
          }
          checks.add(Map.of("fieldModel", path, "modelsHd", models, "cycles", 8, "fieldHead", FIELD_HEAD, "battleHead", BATTLE_HEAD));
        }
        java.lang.ref.Reference.reachabilityFence(modelMod);
        java.lang.ref.Reference.reachabilityFence(charMod);
      }
    } finally {
      access.reset();
      Obj.deleteObjects();
      Texture.deleteTextures();
    }
    Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
      "status", "passed", "scope", "Actual-source appearance and mesh-cache reconstruction; no game transition, GPU or Steam Deck visual proof", "checks", checks
    )) + "\n");
    System.out.println("Dart field/battle head lifecycle passed: 64 cycles, four source identities");
  }
}
