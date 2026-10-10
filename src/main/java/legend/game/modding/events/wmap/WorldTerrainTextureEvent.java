package legend.game.modding.events.wmap;

import legend.definitive.artwork.WorldTerrainAtlas;
import legend.definitive.artwork.WorldTerrainLoad;
import org.legendofdragoon.modloader.events.Event;
import java.util.List;

/** Optional source-bound static terrain. No GPU resource ownership. */
public final class WorldTerrainTextureEvent extends Event {
  private final WorldTerrainLoad.Snapshot source;
  public WorldTerrainAtlas replacement;
  public WorldTerrainTextureEvent(final byte[] model, final List<byte[]> bank) { this.source = new WorldTerrainLoad.Snapshot(model, bank); }
  public byte[] model() { return this.source.model(); }
  public List<byte[]> bank() { return this.source.bank(); }
}
