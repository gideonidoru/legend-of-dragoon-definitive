// Definitive effects (2026-10-10), AGPL v3; see LICENSE.
package legend.game.modding.events.submap;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Actual source image/palette; particle geometry, timing and blending stay with its owner. */
public final class EffectTextureEvent extends Event {
  public final String effect;
  private final byte[] source;
  public Image replacement;

  public EffectTextureEvent(final String effect, final byte[] source) {
    this.effect = effect;
    this.source = source.clone();
  }

  public byte[] source() { return this.source.clone(); }
}
