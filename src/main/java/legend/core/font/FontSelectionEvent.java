package legend.core.font;

import org.legendofdragoon.modloader.events.Event;
import java.io.IOException;

/** Fresh on mod boot: first valid provider owns the optional selection; absent providers restore configuration. */
public final class FontSelectionEvent extends Event {
  private Font font;
  public Font font() { return this.font; }
  /** A provider can offer only a validated packaged resource, never a shared configured font. */
  public boolean select(final Class<?> resourceOwner) throws IOException {
    if(this.font != null) return false;
    this.font = PackagedFont.load(resourceOwner);
    return true;
  }
}
