package fmvhd;

import legend.game.modding.events.fmv.FmvPlaybackEvent;
import org.apache.logging.log4j.LogManager;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "fmvhd", version = "3.0.0")
public final class FmvHdMod {
  private final Properties assets = new Properties();

  public FmvHdMod() {
    try(final var in = FmvHdMod.class.getResourceAsStream("/fmvhd/assets.properties")) {
      if(in != null) this.assets.load(in);
    } catch(final IOException e) { LogManager.getLogger().warn("FMVHD manifest unavailable; original videos retained", e); }
    EVENTS.register(this);
  }

  @EventListener
  public void replace(final FmvPlaybackEvent event) {
    if(event.replacement != null || !event.file.matches("STR/[A-Z0-9]+\\.IKI")) return;
    final String name = event.file.substring(4, event.file.length() - 4);
    final String digest = this.assets.getProperty(name + ".sha256");
    if(digest == null || !event.sourceSha256().equals(this.assets.getProperty(name + ".sourceSha256"))) return;
    try(final var resource = FmvHdMod.class.getResourceAsStream("/fmvhd/videos/" + name + ".mp4")) {
      if(resource == null) return;
      final Path root = Path.of("cache", "fmvhd").toAbsolutePath();
      event.replacement = VideoCache.materialize(root, digest, Long.parseLong(this.assets.getProperty(name + ".bytes")), resource);
    } catch(final IOException | RuntimeException e) {
      LogManager.getLogger().warn("FMVHD retained original {}: {}", name, e.getMessage());
    }
  }
}
