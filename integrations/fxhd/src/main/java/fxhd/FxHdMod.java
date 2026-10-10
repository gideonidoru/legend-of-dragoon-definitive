package fxhd;

import legend.definitive.artwork.ArtworkResources;
import legend.definitive.textures.TimImage;
import legend.game.modding.events.submap.EffectTextureEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "fxhd", version = "3.0.0")
public final class FxHdMod {
  public FxHdMod() { EVENTS.register(this); }
  @EventListener public void replace(final EffectTextureEvent event) {
    if(!"dust".equals(event.effect) || event.replacement != null) return;
    try {
      final byte[] tim = event.source();
      final var meta = ArtworkResources.metadata(FxHdMod.class, "/fxhd/dust.properties");
      ArtworkResources.hash(tim, meta.getProperty("sourceSha256"));
      final byte[] png = ArtworkResources.read(FxHdMod.class, "/fxhd/dust.png", 1024 * 1024);
      ArtworkResources.hash(png, meta.getProperty("outputSha256"));
      final var source = TimImage.read(tim, 0);
      final var image = ArtworkResources.image(png, source.width() * 4, source.height() * 4);
      validate(source, image);
      event.replacement = image;
    } catch(final Exception failure) { org.apache.logging.log4j.LogManager.getLogger().warn("FxHD retained original dust: {}", failure.getMessage()); }
  }
  public static void validate(final TimImage source, final legend.game.textures.Image image) throws java.io.IOException {
    if(image.width != source.width() * 4 || image.height != source.height() * 4) throw new java.io.IOException("Effect scale differs");
    for(int y = 0; y < image.height; y++) for(int x = 0; x < image.width; x++) {
      final int expected = source.pixels()[(y / 4) * source.width() + x / 4], index = (y * image.width + x) * 4;
      final int rgb = Byte.toUnsignedInt(image.data[index]) << 16 | Byte.toUnsignedInt(image.data[index + 1]) << 8 | Byte.toUnsignedInt(image.data[index + 2]);
      final int alpha = Byte.toUnsignedInt(image.data[index + 3]);
      if(alpha != expected >>> 24 || (rgb == 0 && alpha == 0) != (expected == 0) || (expected & 0xffffff) == 0 && rgb != 0) throw new java.io.IOException("Effect STP/discard coverage changed");
    }
  }
}
