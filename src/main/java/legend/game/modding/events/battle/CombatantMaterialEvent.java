package legend.game.modding.events.battle;

import legend.game.combat.Battle;
import legend.game.combat.bent.BattleEvent;
import legend.game.types.Model124;
import legend.definitive.materials.MaterialAtlas;

/** Source-bound optional presentation, resolved before allocation; first replacement keeps priority. */
public final class CombatantMaterialEvent extends BattleEvent {
  public final byte[] modelSource, timSource;
  public final Model124 model;
  public MaterialAtlas replacement;
  public java.util.Map<Integer, legend.core.renderer.SurfaceResponse> surfaces = java.util.Map.of();
  public CombatantMaterialEvent(final Battle battle, final Model124 model, final byte[] modelSource, final byte[] timSource) {
    super(battle);
    this.model = model;
    this.modelSource = modelSource.clone();
    this.timSource = timSource.clone();
  }
}
