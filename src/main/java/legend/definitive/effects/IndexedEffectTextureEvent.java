package legend.definitive.effects;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Immutable source identity; palette colors always remain in the owner's live VRAM. */
public final class IndexedEffectTextureEvent extends Event {
  public final String sourceSha256;
  public final int width, height;
  public Image detail;

  public IndexedEffectTextureEvent(final String sourceSha256, final int width, final int height) {
    this.sourceSha256 = sourceSha256;
    this.width = width;
    this.height = height;
  }
}
