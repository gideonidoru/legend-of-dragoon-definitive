package legend.game.modding.events.tmd;

import legend.game.tmd.TmdObjTable1c;
import org.legendofdragoon.modloader.events.Event;

/** Optional rendered geometry, prepared before allocation. CPU tables stay owned by the game. */
public final class TmdGeometryEvent extends Event {
  public final TmdObjTable1c source;
  public final int specialFlags;
  public final int textureWidth;
  public final int textureHeight;
  public TmdObjTable1c geometry;

  public TmdGeometryEvent(final TmdObjTable1c source, final int specialFlags, final int textureWidth, final int textureHeight) {
    this.source = source;
    this.geometry = source;
    this.specialFlags = specialFlags;
    this.textureWidth = textureWidth;
    this.textureHeight = textureHeight;
  }
}
