package legend.game.modding.events.battle;

import legend.definitive.materials.MaterialAtlas;
import org.legendofdragoon.modloader.events.Event;

/** Optional source-bound artwork; selection owns no GPU resources. */
public final class BattleStageTextureEvent extends Event {
  private final byte[] model, tim;
  public MaterialAtlas replacement;
  public BattleStageTextureEvent(final byte[] model, final byte[] tim) {
    this.model = model.clone(); this.tim = tim.clone();
  }
  public byte[] model() { return this.model.clone(); }
  public byte[] tim() { return this.tim.clone(); }
}
