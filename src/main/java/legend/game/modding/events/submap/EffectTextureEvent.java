package legend.game.modding.events.submap;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Static effect texture only; animation, timing and blend mode stay with the engine. */
public final class EffectTextureEvent extends Event {
  public final String effect;
  private final byte[] source;
  public Image replacement;
  public EffectTextureEvent(final String effect, final byte[] source) { this.effect = effect; this.source = source.clone(); }
  public byte[] source() { return this.source.clone(); }
}
