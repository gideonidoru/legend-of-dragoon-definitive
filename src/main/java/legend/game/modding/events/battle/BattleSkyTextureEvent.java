package legend.game.modding.events.battle;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Source-bound optional panorama selection. Owns no renderer resources. */
public final class BattleSkyTextureEvent extends Event {
  private final byte[] source;
  public Image replacement;
  public BattleSkyTextureEvent(final byte[] source) { this.source = source.clone(); }
  public byte[] source() { return this.source.clone(); }
}
