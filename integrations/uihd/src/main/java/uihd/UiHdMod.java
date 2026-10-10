package uihd;

import legend.definitive.artwork.ArtworkResources;
import legend.game.textures.Image;
import legend.game.textures.NativeUiTextureEvent;
import legend.game.textures.NativeUiTextures;
import legend.game.textures.ReplaceAtlasTexturesEvent;
import legend.game.textures.UiTextureEvent;
import legend.game.textures.UiTextures;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.EVENTS;

/** Independent source-bound interface restoration; absent or invalid artwork fails open. */
@Mod(id = "uihd", version = "3.0.0")
public final class UiHdMod {
  private static final Logger LOGGER = LogManager.getLogger(UiHdMod.class);
  private final List<JSONObject> assets = new ArrayList<>();

  public UiHdMod() {
    try {
      final var catalog = new JSONObject(new String(ArtworkResources.read(UiHdMod.class, "/uihd/catalog.json", 1024 * 1024), StandardCharsets.UTF_8));
      final JSONArray entries = catalog.getJSONArray("assets");
      if(entries.length() > 256) throw new IOException("UI catalog exceeds bounds");
      for(int i = 0; i < entries.length(); i++) {
        final JSONObject entry = entries.getJSONObject(i);
        if(!entry.getString("reviewStatus").equals("selected-source-layout-review-native-pending")) continue;
        if(!entry.getString("resource").matches("[a-z0-9_-]+\\.png")) throw new IOException("UI resource path differs");
        validateEntry(entry);
        this.assets.add(entry);
      }
    } catch(final Exception failure) {
      this.assets.clear();
      LOGGER.warn("UIHD retained original interface: {}", failure.getMessage());
    }
    EVENTS.register(this);
    // One sequential low-priority chain reuses the bounded engine worker instead
    // of flooding its queue. This never creates graphics objects or delays boot.
    java.util.concurrent.CompletableFuture<Boolean> upcoming = java.util.concurrent.CompletableFuture.completedFuture(true);
    for(final JSONObject entry : this.assets) {
      upcoming = upcoming.handle((ready, failure) -> true).thenCompose(ready ->
        legend.core.renderer.Texture.prewarmPng(UiHdMod.class, "/uihd/assets/" + entry.getString("resource")));
    }
  }

  private static void validateEntry(final JSONObject entry) throws IOException {
    entry.getString("id");
    if(!entry.getString("sourceSha256").matches("[a-f0-9]{64}") || !entry.getString("outputSha256").matches("[a-f0-9]{64}")) throw new IOException("UI identity differs");
    final String kind = entry.getString("kind");
    final Path source = Path.of(entry.getString("sourcePath")).normalize();
    if(source.isAbsolute() || source.startsWith("..")) throw new IOException("UI source escapes installation");
    final JSONArray size = entry.getJSONArray("outputSize");
    if(size.length() != 2 || size.getInt(0) < 1 || size.getInt(1) < 1 || size.getInt(0) > 4096 || size.getInt(1) > 4096) throw new IOException("UI output exceeds bounds");
    switch(kind) {
      case "atlas" -> {
        if(!source.startsWith(Path.of("gfx/goods"))) throw new IOException("UI atlas source differs");
        new RegistryId(entry.getString("registryId"));
      }
      case "png" -> { if(!source.startsWith(Path.of("gfx/ui"))) throw new IOException("UI PNG source differs"); }
      case "native" -> {
        entry.getString("family");
        final JSONArray crop = entry.getJSONArray("crop");
        if(crop.length() != 4 || crop.getInt(0) < 0 || crop.getInt(1) < 0 || crop.getInt(2) < 1 || crop.getInt(3) < 1
          || crop.getInt(2) > 512 || crop.getInt(3) > 512 || entry.getInt("palette") < 0 || entry.getInt("palette") >= 16) throw new IOException("Native UI crop differs");
      }
      default -> throw new IOException("Unknown UI artwork kind");
    }
  }

  private static Image candidate(final JSONObject entry) throws IOException {
    final byte[] bytes = ArtworkResources.read(UiHdMod.class, "/uihd/assets/" + entry.getString("resource"), 1024 * 1024);
    ArtworkResources.hash(bytes, entry.getString("outputSha256"));
    final JSONArray size = entry.getJSONArray("outputSize");
    final Image image = UiTextures.decode(bytes);
    if(image.width != size.getInt(0) || image.height != size.getInt(1)) throw new IOException("UI artwork dimensions differ");
    return image;
  }

  @EventListener
  public void replace(final ReplaceAtlasTexturesEvent event) {
    for(final JSONObject entry : this.assets) {
      if(!entry.getString("kind").equals("atlas")) continue;
      try {
        final Path source = Path.of(entry.getString("sourcePath")).normalize();
        if(!source.startsWith(Path.of("gfx/goods")) || Files.size(source) > 1024 * 1024) continue;
        final byte[] originalBytes = Files.readAllBytes(source);
        ArtworkResources.hash(originalBytes, entry.getString("sourceSha256"));
        final Image original = UiTextures.decode(originalBytes), replacement = candidate(entry);
        UiTextureEvent.validate(original, replacement);
        event.replace(new RegistryId(entry.getString("registryId")), original, replacement);
      } catch(final Exception failure) {
        LOGGER.warn("UIHD retained original {}: {}", entry.getString("id"), failure.getMessage());
      }
    }
  }

  @EventListener
  public void texture(final UiTextureEvent event) {
    for(final JSONObject entry : this.assets) {
      if(!entry.getString("kind").equals("png") || !event.path.equals(Path.of(entry.getString("sourcePath")).normalize())) continue;
      try {
        ArtworkResources.hash(event.source(), entry.getString("sourceSha256"));
        event.replace(event.original(), candidate(entry));
      } catch(final Exception failure) {
        LOGGER.warn("UIHD retained original {}: {}", entry.getString("id"), failure.getMessage());
      }
    }
  }

  @EventListener
  public void nativeTexture(final NativeUiTextureEvent event) {
    for(final JSONObject entry : this.assets) {
      if(!entry.getString("kind").equals("native") || !entry.getString("family").equals(event.id)) continue;
      try {
        ArtworkResources.hash(event.source(), entry.getString("sourceSha256"));
        final JSONArray crop = entry.getJSONArray("crop");
        final int x = crop.getInt(0), y = crop.getInt(1), w = crop.getInt(2), h = crop.getInt(3), palette = entry.getInt("palette");
        final Image original = NativeUiTextures.decode(event.tim(), palette, x, y, w, h);
        final var binding = new NativeUiTextures.Binding(event.imageX * 4 + x, event.imageY + y,
          event.clutX, event.clutY + palette, w, h);
        NativeUiTextures.register(binding, original, candidate(entry));
      } catch(final Exception failure) {
        LOGGER.warn("UIHD retained original {}: {}", entry.getString("id"), failure.getMessage());
      }
    }
  }
}
