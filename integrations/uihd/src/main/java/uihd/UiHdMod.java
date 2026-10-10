package uihd;

import legend.definitive.artwork.ArtworkResources;
import legend.game.textures.ReplaceAtlasTexturesEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.registries.RegistryId;
import java.nio.file.Files;
import java.nio.file.Path;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "uihd", version = "3.0.0")
public final class UiHdMod {
  private static final String[] ICONS = {"red_stone", "blue_stone", "moon_gem", "vanishing_stone", "magic_oil"};
  public UiHdMod() { EVENTS.register(this); }
  @EventListener public void replace(final ReplaceAtlasTexturesEvent event) {
    for(final String name : ICONS) {
      try {
        final String base = "/uihd/goods/" + name;
        final var meta = ArtworkResources.metadata(UiHdMod.class, base + ".properties");
        final Path source = Path.of("gfx/goods/" + name + ".png");
        if(Files.size(source) > 1024 * 1024) continue;
        final byte[] original = Files.readAllBytes(source), png = ArtworkResources.read(UiHdMod.class, base + ".png", 1024 * 1024);
        ArtworkResources.hash(original, meta.getProperty("sourceSha256")); ArtworkResources.hash(png, meta.getProperty("outputSha256"));
        event.replace(new RegistryId("lod", name), ArtworkResources.image(original, 32, 32), ArtworkResources.image(png, 64, 64));
      } catch(final Exception failure) { org.apache.logging.log4j.LogManager.getLogger().warn("UIHD retained original icon {}: {}", name, failure.getMessage()); }
    }
  }
}
