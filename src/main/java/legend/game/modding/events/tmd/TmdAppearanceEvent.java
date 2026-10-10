package legend.game.modding.events.tmd;

import legend.game.tmd.TmdObjTable1c;
import org.legendofdragoon.modloader.events.Event;

/** Experimental appearance composition after geometry; never transfers CPU table ownership. */
public final class TmdAppearanceEvent extends Event {
  public final TmdObjTable1c source;
  public final TmdObjTable1c geometry;
  public final int specialFlags;
  public TmdObjTable1c appearance;

  public TmdAppearanceEvent(final TmdObjTable1c source, final TmdObjTable1c geometry, final int specialFlags) {
    this.source = source;
    this.geometry = geometry;
    this.appearance = geometry;
    this.specialFlags = specialFlags;
  }
}
