package legend.game.modding.events.wmap;

import legend.game.textures.Image;
import org.legendofdragoon.modloader.events.Event;

/** Location landscape only; popup text, controls and character art are separate. */
public final class LocationThumbnailTextureEvent extends Event {
  private final byte[] source;
  public Image replacement;
  public LocationThumbnailTextureEvent(final byte[] source) { this.source = source.clone(); }
  public byte[] source() { return this.source.clone(); }
}
