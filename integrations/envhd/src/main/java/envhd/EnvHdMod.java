package envhd;

import legend.definitive.artwork.ArtworkResources;
import legend.definitive.materials.MaterialAtlas;
import legend.game.modding.events.battle.BattleStageTextureEvent;
import legend.game.modding.events.battle.BattleSkyTextureEvent;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.IOException;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "envhd", version = "3.0.0")
public final class EnvHdMod {
  private final java.util.Set<Integer> protectedFieldCuts = new java.util.HashSet<>();
  private boolean fieldProtectionAvailable;
  public EnvHdMod() {
    try {
      final var ownership = ArtworkResources.metadata(EnvHdMod.class, "/envhd/fields/protected.properties");
      for(final String cut : ownership.getProperty("cuts").split(",")) this.protectedFieldCuts.add(Integer.parseInt(cut));
      this.fieldProtectionAvailable = true;
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained native fields: missing ownership protection"); }
    EVENTS.register(this);
  }
  @EventListener public void replaceFields(final legend.game.modding.events.submap.SubmapEnvironmentTextureEvent event) {
    if(!this.fieldProtectionAvailable || this.protectedFieldCuts.contains(event.submapCut) || event.nativeImages == null) return;
    final java.util.List<legend.core.renderer.Texture> allocated = new java.util.ArrayList<>();
    try {
      final var source = event.nativeImages.get();
      final var bg = event.background == null ? legend.definitive.artwork.FieldImages.loadOptional(EnvHdMod.class, source.background()) : null;
      final legend.game.textures.Image[] fg = new legend.game.textures.Image[event.foregrounds.length];
      if(source.foregrounds().length != fg.length) throw new IOException("Field slots differ");
      for(int i = 0; i < fg.length; i++) if(event.foregrounds[i] == null && source.foregrounds()[i] != null) fg[i] = legend.definitive.artwork.FieldImages.loadOptional(EnvHdMod.class, source.foregrounds()[i].image());
      final var background = bg == null ? null : fieldTexture(bg, allocated);
      final legend.core.renderer.Texture[] foregrounds = new legend.core.renderer.Texture[fg.length];
      for(int i = 0; i < fg.length; i++) if(fg[i] != null) foregrounds[i] = fieldTexture(fg[i], allocated);
      if(background != null) event.background = background;
      for(int i = 0; i < fg.length; i++) if(foregrounds[i] != null) {
        event.foregrounds[i] = foregrounds[i];
        event.foregroundRects[i] = source.foregrounds()[i].placement();
      }
    } catch(final IOException | RuntimeException failure) {
      for(final var texture : allocated) texture.delete();
      org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original field: {}", failure.getMessage());
    }
  }
  private static legend.core.renderer.Texture fieldTexture(final legend.game.textures.Image image, final java.util.List<legend.core.renderer.Texture> allocated) {
    final var texture = legend.core.renderer.Texture.create("EnvHD field", builder -> {
      final var bytes = org.lwjgl.BufferUtils.createByteBuffer(image.data.length); bytes.put(image.data).flip();
      builder.data(bytes, image.width, image.height); builder.wrapS(false); builder.wrapT(false);
    });
    allocated.add(texture); return texture;
  }
  @EventListener public void replaceTerrain(final legend.game.modding.events.wmap.WorldTerrainTextureEvent event) {
    if(event.replacement != null) return;
    try {
      final byte[] model = event.model();
      final String base = "/envhd/world/terrain/" + legend.definitive.textures.TexturePilot.sha256(model);
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.json")) { if(exists == null) return; }
      event.replacement = legend.definitive.artwork.WorldTerrainAtlas.load(EnvHdMod.class, base, model, event.bank());
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original terrain: {}", failure.getMessage()); }
  }
  @EventListener public void replaceBackdrop(final legend.game.modding.events.wmap.WorldBackdropTextureEvent event) {
    if(event.replacement != null) return;
    try {
      final byte[] source = event.source();
      final String base = "/envhd/world/backdrops/" + legend.definitive.textures.TexturePilot.sha256(source);
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.json")) { if(exists == null) return; }
      event.replacement = legend.definitive.artwork.SkyImage.load(EnvHdMod.class, base, source);
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original world backdrop: {}", failure.getMessage()); }
  }
  @EventListener public void replaceLocation(final legend.game.modding.events.wmap.LocationThumbnailTextureEvent event) {
    if(event.replacement != null) return;
    try {
      final byte[] source = event.source();
      final String base = "/envhd/world/locations/" + legend.definitive.textures.TexturePilot.sha256(source);
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.properties")) { if(exists == null) return; }
      event.replacement = legend.definitive.artwork.LocationArtwork.load(EnvHdMod.class, base, source);
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original location image: {}", failure.getMessage()); }
  }
  @EventListener public void replaceSky(final BattleSkyTextureEvent event) {
    if(event.replacement != null) return;
    try {
      final byte[] source = event.source();
      final String base = "/envhd/skies/" + legend.definitive.textures.TexturePilot.sha256(source);
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.json")) { if(exists == null) return; }
      event.replacement = legend.definitive.artwork.SkyImage.load(EnvHdMod.class, base, source);
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original sky: {}", failure.getMessage()); }
  }
  @EventListener public void replace(final BattleStageTextureEvent event) {
    if(event.replacement != null || event.staticReplacement != null) return;
    try {
      final byte[] model = event.model(), tim = event.tim();
      final String key = legend.definitive.textures.TexturePilot.sha256(model);
      final String base = "/envhd/stages/" + key;
      try(final var exists = EnvHdMod.class.getResourceAsStream(base + "/manifest.json")) {
        if(exists == null) {
          final String staticBase = "/envhd/battle-materials/" + key;
          try(final var available = EnvHdMod.class.getResourceAsStream(staticBase + "/manifest.json")) { if(available == null) return; }
          event.staticReplacement = legend.definitive.artwork.BattleMaterialAtlas.load(EnvHdMod.class, staticBase, model, tim);
          return;
        }
      }
      final var atlas = MaterialAtlas.read(ArtworkResources.read(EnvHdMod.class, base + "/manifest.json", 65536), ArtworkResources.read(EnvHdMod.class, base + "/atlas-engine-stp.png", 32 * 1024 * 1024), model, tim);
      legend.definitive.artwork.StageArtwork.validateReferences(model, tim);
      event.replacement = atlas;
    } catch(final IOException | RuntimeException failure) { org.apache.logging.log4j.LogManager.getLogger().warn("EnvHD retained original stage: {}", failure.getMessage()); }
  }
}
