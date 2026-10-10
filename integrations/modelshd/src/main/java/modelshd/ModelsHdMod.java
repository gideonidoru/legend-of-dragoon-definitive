// ModelsHD optional geometry adapter (2026-10-10), AGPL v3; see LICENSE.
package modelshd;

import legend.game.modding.events.battle.CombatantModelLoadedEvent;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdMeshObj;
import org.apache.logging.log4j.LogManager;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(id = ModelsHdMod.MOD_ID, version = "^3.0.0")
public final class ModelsHdMod {
  public static final String MOD_ID = "modelshd";
  public ModelsHdMod() {
    try {
      TmdObjTable1c.class.getConstructor(String.class, org.joml.Vector3f[].class, org.joml.Vector3f[].class, TmdObjTable1c.Primitive[].class);
      TmdObjTable1c.class.getMethod("buildObjLike", TmdObjTable1c.class);
      legend.core.GameEngine.EVENTS.register(this);
    } catch(final NoSuchMethodException e) {
      LogManager.getLogger().warn("ModelsHD requires the Definitive authored-model API; original models retained");
    }
  }

  @EventListener
  public void onCombatantLoaded(final CombatantModelLoadedEvent event) {
    if((event.combatant.flags_19e & 4) == 0) return;
    // Animated palettes and auxiliary container data need their own compatibility proof.
    if(event.combatant.tmd_08.clutAnimations_04 != null || event.combatant.tmd_08.ptr_08 != null) return;
    final TmdObjTable1c[] originals = new TmdObjTable1c[event.model.modelParts_00.length];
    for(int i = 0; i < originals.length; i++) originals[i] = event.model.modelParts_00[i].tmd_08;
    try {
      // Custom geometry/UV owners need a declared handoff, not a guessed reinterpretation.
      for(final var original : originals) {
        if(original.getClass() != TmdObjTable1c.class || original.getObj().getClass() != TmdMeshObj.class) return;
      }
      final String identity = ModelPack.identity(originals);
      final Path pack = Path.of("model-packs", MOD_ID, "battle", identity + ".json");
      if(!Files.isRegularFile(pack)) return;
      final var replacements = ModelPack.read(pack, originals);
      TextureCompatibility.requireNativeAddressing(originals);
      ModelReplacement.install(event.model, replacements);
      LogManager.getLogger().info("ModelsHD replaced {} animation parts for {}", replacements.length, identity);
    } catch(final Exception e) {
      LogManager.getLogger().warn("ModelsHD kept original model: {}", e.getMessage());
    }
  }
}
