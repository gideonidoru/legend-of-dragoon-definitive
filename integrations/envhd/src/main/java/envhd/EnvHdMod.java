package envhd;

import legend.definitive.artwork.ArtworkResources;
import legend.definitive.materials.MaterialAtlas;
import legend.game.modding.events.battle.BattleStageTextureEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.IOException;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "envhd", version = "3.0.0")
public final class EnvHdMod {
  public EnvHdMod() { EVENTS.register(this); }
  @EventListener public void replace(final BattleStageTextureEvent event) {
    if(event.replacement != null) return;
    try {
      final byte[] model = event.model(), tim = event.tim();
      final String key = legend.definitive.textures.TexturePilot.sha256(model);
      final String base = "/envhd/stages/" + key;
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.json")) { if(exists == null) return; }
      final var atlas = MaterialAtlas.read(ArtworkResources.read(EnvHdMod.class, base + "/manifest.json", 65536), ArtworkResources.read(EnvHdMod.class, base + "/atlas-engine-stp.png", 32 * 1024 * 1024), model, tim);
      legend.definitive.artwork.StageArtwork.validateReferences(model, tim);
      event.replacement = atlas;
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original stage: {}", failure.getMessage()); }
  }
}
