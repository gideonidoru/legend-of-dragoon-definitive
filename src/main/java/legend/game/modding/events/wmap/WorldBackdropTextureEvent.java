package legend.game.modding.events.wmap;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Optional source-bound world-map parchment replacement. No renderer ownership. */
public final class WorldBackdropTextureEvent extends Event {
  private final byte[] source;
  public Image replacement;
  public WorldBackdropTextureEvent(final byte[] source) { this.source = source.clone(); }
  public byte[] source() { return this.source.clone(); }
}
