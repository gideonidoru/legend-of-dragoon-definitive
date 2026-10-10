package legend.game.modding.events.submap;

import legend.core.renderer.Texture;
import legend.game.modding.events.engine.InGameEvent;
import legend.game.submap.SMap;
import legend.game.submap.Submap;
import legend.game.types.GameState52c;
import java.util.concurrent.CompletableFuture;

/** Early resource hint. Listeners submit compressed resources; no graphics objects are created here. */
public final class SubmapEnvironmentPreloadEvent extends InGameEvent<SMap> implements LoadedSubmapEvent {
  private final Submap submap;
  public final int disk, submapCut;
  public SubmapEnvironmentPreloadEvent(final SMap state, final GameState52c gameState, final Submap submap, final int disk, final int cut) {
    super(state,gameState); this.submap=submap; this.disk=disk; this.submapCut=cut;
  }
  @Override public Submap getSubmap() { return this.submap; }
  public CompletableFuture<Boolean> prewarm(final Class<?> owner, final String resource) {
    return Texture.prewarmPng(owner,resource);
  }
}
