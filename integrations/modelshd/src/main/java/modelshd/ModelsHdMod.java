// ModelsHD optional geometry adapter (2026-10-10), AGPL v3; see LICENSE.
package modelshd;

import legend.game.modding.events.battle.CombatantModelLoadedEvent;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdMeshObj;
import legend.game.combat.types.CombatantStruct1a8;
import legend.game.types.Model124;
import org.apache.logging.log4j.LogManager;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(id = ModelsHdMod.MOD_ID, version = "^3.0.0")
public final class ModelsHdMod {
  public static final String MOD_ID = "modelshd";
  private GeometryPass geometryPass;
  public ModelsHdMod() {
    try {
      TmdObjTable1c.class.getConstructor(String.class, org.joml.Vector3f[].class, org.joml.Vector3f[].class, TmdObjTable1c.Primitive[].class);
      TmdObjTable1c.class.getMethod("buildObjLike", TmdObjTable1c.class);
      TmdObjTable1c.class.getMethod("isAuthoredGeometry");
      this.geometryPass = new GeometryPass();
      legend.core.GameEngine.EVENTS.register(this);
    } catch(final NoSuchMethodException | java.io.IOException e) {
      LogManager.getLogger().warn("ModelsHD unavailable; original models retained: {}", e.getMessage());
    }
  }

  @EventListener
  public void onGeometry(final TmdGeometryEvent event) {
    if(this.geometryPass == null) return;
    try {
      this.geometryPass.prepare(event, Path.of("model-packs", MOD_ID, "parts"));
    } catch(final Exception failure) {
      LogManager.getLogger().warn("ModelsHD kept original {}: {}", event.source.name, failure.getMessage());
    }
  }

  @EventListener
  public void onCombatantLoaded(final CombatantModelLoadedEvent event) {
    // The world pass is already prepared once in the shared native mesh route.
    if(this.geometryPass != null) return;
    try {
      if(replaceIfSupported(event.combatant, event.model, Path.of("model-packs", MOD_ID, "battle"))) {
        LogManager.getLogger().info("ModelsHD replaced {} animation parts (Dragoon: {})", event.model.modelParts_00.length, event.combatant.isDragoon());
      }
    } catch(final Exception e) {
      LogManager.getLogger().warn("ModelsHD kept original model: {}", e.getMessage());
    }
  }

  // Both normal and Dragoon forms are players. Their part counts and source identities differ.
  static boolean replaceIfSupported(final CombatantStruct1a8 combatant, final Model124 model, final Path packs) throws java.io.IOException {
    if(!combatant.isPlayer() || combatant.tmd_08 == null || model.modelParts_00 == null) return false;
    // Animated palettes and auxiliary container data need their own compatibility proof.
    if(combatant.tmd_08.clutAnimations_04 != null || combatant.tmd_08.ptr_08 != null) return false;
    final TmdObjTable1c[] originals = new TmdObjTable1c[model.modelParts_00.length];
    for(int i = 0; i < originals.length; i++) {
      if(model.modelParts_00[i] == null || model.modelParts_00[i].tmd_08 == null) return false;
      originals[i] = model.modelParts_00[i].tmd_08;
    }
    // Custom geometry/UV owners need a declared handoff, not a guessed reinterpretation.
    for(final var original : originals) {
      if(original.getClass() != TmdObjTable1c.class || original.getObj().getClass() != TmdMeshObj.class) return false;
    }
    final String identity = ModelPack.identity(originals);
    final Path pack = packs.resolve(identity + ".json");
    final TmdObjTable1c[] replacements;
    if(Files.isRegularFile(pack)) {
      replacements = ModelPack.read(pack, originals);
    } else {
      final var resource = ModelsHdMod.class.getResourceAsStream("/modelshd/models/battle/" + identity + ".json");
      if(resource == null) return false;
      replacements = ModelPack.read(resource, originals);
    }
    TextureCompatibility.requireNativeAddressing(originals);
    ModelReplacement.install(model, replacements);
    return true;
  }
}
