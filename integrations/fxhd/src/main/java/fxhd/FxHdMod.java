// Definitive effects (2026-10-10), AGPL v3; see LICENSE.
package fxhd;

import legend.definitive.effects.EffectArtwork;
import legend.definitive.textures.TexturePilot;
import legend.definitive.textures.TimImage;
import legend.definitive.rendering.PngAssets;
import legend.game.modding.events.submap.EffectTextureEvent;
import legend.game.textures.Image;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "fxhd", version = "3.0.0")
public final class FxHdMod {
  private static final Set<String> SUPPORTED = Set.of("dust", "smoke_1", "smoke_2_dust_palette", "left_foot", "right_foot", "savepoint_big_circle");
  public FxHdMod() {
    EVENTS.register(this);
    // Immutable resource hints only: the shared worker never uploads or owns scene state.
    java.util.concurrent.CompletableFuture<Boolean> hint = java.util.concurrent.CompletableFuture.completedFuture(false);
    for(final String name : new String[]{"dust", "savepoint_big_circle", "smoke_1", "smoke_2_dust_palette", "left_foot", "right_foot"}) {
      hint = hint.handle((ready, failure) -> false).thenCompose(ignored -> legend.core.renderer.Texture.prewarmPng(FxHdMod.class, "/fxhd/" + name + ".png"));
    }
    hint.exceptionally(failure -> false);
  }

  @EventListener
  public void replace(final EffectTextureEvent event) {
    if(event.replacement != null || !SUPPORTED.contains(event.effect)) return;
    try {
      final byte[] source = event.source();
      event.replacement = read(event.effect, source);
    } catch(final Exception failure) {
      org.apache.logging.log4j.LogManager.getLogger().warn("FxHD retained original {}: {}", event.effect, failure.getMessage());
    }
  }

  public static Image read(final String name, final byte[] source) throws IOException {
    if(!SUPPORTED.contains(name)) throw new IOException("Unknown effects resource");
    final Properties metadata = new Properties();
    metadata.load(new ByteArrayInputStream(resource(name + ".properties", 16384)));
    if(!TexturePilot.sha256(source).equals(metadata.getProperty("sourceSha256"))) throw new IOException("Effect source changed");
    final TimImage original = TimImage.read(source, 0);
    final int scale = Integer.parseInt(metadata.getProperty("scale"));
    if(scale != 2 && scale != 4) throw new IOException("Unsupported effect scale");
    final byte[] png = resource(name + ".png", 1024 * 1024);
    if(!TexturePilot.sha256(png).equals(metadata.getProperty("outputSha256"))) throw new IOException("Effect resource changed");
    final var header = java.nio.ByteBuffer.wrap(png).order(java.nio.ByteOrder.BIG_ENDIAN);
    final int width = original.width() * scale, height = original.height() * scale;
    if(png.length < 33 || header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452
      || header.getInt(16) != width || header.getInt(20) != height || png[24] != 8 || png[25] != 6) throw new IOException("Effect PNG header differs");
    final byte[] rgba = new byte[width * height * 4];
    final var setting = legend.game.modding.coremod.CoreMod.IMAGE_CACHE_CONFIG;
    final PngAssets cache = !setting.isValid() || legend.core.GameEngine.CONFIG.getConfig(setting.get()) ? PngAssets.SHARED : PngAssets.UNCACHED;
    try(final var decoded = cache.acquire(java.nio.ByteBuffer.wrap(png))) {
      if(decoded.width() != width || decoded.height() != height) throw new IOException("Effect PNG differs");
      decoded.pixels().get(rgba);
    }
    final Image image = new Image(rgba, width, height);
    EffectArtwork.validate(original, image);
    return image;
  }

  private static byte[] resource(final String name, final int limit) throws IOException {
    try(final var input = FxHdMod.class.getResourceAsStream("/fxhd/" + name)) {
      if(input == null) throw new IOException("Missing effect resource");
      final byte[] bytes = input.readNBytes(limit + 1);
      if(bytes.length > limit) throw new IOException("Effect resource exceeds bounds");
      return bytes;
    }
  }
}
