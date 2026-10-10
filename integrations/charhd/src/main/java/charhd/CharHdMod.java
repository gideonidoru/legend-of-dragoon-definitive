// Definitive opt-in field texture adapter (2026-10-09), AGPL v3; see LICENSE.
package charhd;

import legend.definitive.textures.TexturePack;
import legend.game.modding.events.submap.SubmapObjectTextureEvent;
import legend.game.submap.RetailSubmap;
import java.nio.file.*;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;

@org.legendofdragoon.modloader.Mod(id = "charhd", version = "3.0.0")
public final class CharHdMod {
  private ReconstructedCharacters characters;

  @org.legendofdragoon.modloader.events.EventListener
  public void applyCharacter(final legend.game.modding.events.tmd.TmdAppearanceEvent event) {
    if(this.characters == null) return;
    try {
      this.characters.apply(event);
    } catch(final Exception failure) {
      LogManager.getLogger().warn("CharHD retained existing character geometry/material: {}", failure.getMessage());
    }
  }
  @org.legendofdragoon.modloader.events.EventListener
  public void applyCombat(final legend.game.modding.events.battle.CombatantMaterialEvent event) {
    if(event.replacement != null) return;
    try {
      final String identity = legend.definitive.textures.TexturePilot.sha256(event.modelSource);
      final String base = "/charhd/packs/" + identity + "/";
      final byte[] manifest = resource(base + "manifest.json", 65536);
      if(manifest == null) return;
      final byte[] png = resource(base + "atlas-engine-stp.png", 32 * 1024 * 1024);
      if(png == null) throw new java.io.IOException("Missing CharHD atlas");
      final var atlas = legend.definitive.materials.MaterialAtlas.read(manifest, png, event.modelSource, event.timSource);
      final byte[] definitions = resource(base + "surfaces.json", 65536);
      final var surfaces = definitions == null ? java.util.Map.<Integer, legend.core.renderer.SurfaceResponse>of() : CharacterSurfaces.read(definitions, atlas);
      event.surfaces = surfaces;
      event.replacement = atlas;
    } catch(final Exception failure) {
      LogManager.getLogger().warn("CharHD retained original character: {}", failure.getMessage());
    }
  }

  private static byte[] resource(final String path, final int limit) throws java.io.IOException {
    try(final var input = CharHdMod.class.getResourceAsStream(path)) {
      if(input == null) return null;
      final byte[] bytes = input.readNBytes(limit + 1);
      if(bytes.length > limit) throw new java.io.IOException("Oversized CharHD resource");
      return bytes;
    }
  }

  public CharHdMod() {
    try { this.characters = new ReconstructedCharacters(); }
    catch(final java.io.IOException failure) { LogManager.getLogger().warn("CharHD reconstructions unavailable; existing models retained: {}", failure.getMessage()); }
    legend.core.GameEngine.EVENTS.register(this);
  }
  @org.legendofdragoon.modloader.events.EventListener
  public void apply(final SubmapObjectTextureEvent event) {
    if(!(event.getSubmap() instanceof RetailSubmap retail)) return;
    final Path folder = Path.of("texture-packs/pilot");
    if(!Files.isDirectory(folder)) return;
    try {
      final var replacements = new HashMap<Integer, TexturePack.Replacement>();
      for(int index = 0; index < retail.objects.size(); index++) {
        final var original = retail.getObjectTexture(index);
        if(original == null || event.textures.containsKey(index)) continue;
        final var texture = TexturePack.read(folder, event.disk, event.submapCut, index, original.getData().getBytes());
        if(texture != null) replacements.put(index, texture);
      }
      replacements.forEach((index, texture) -> event.textures.putIfAbsent(index, builder -> builder.data(texture.rgba().duplicate(), texture.width(), texture.height())));
    } catch(final Exception e) { LogManager.getLogger().warn("CharHD pack ignored; original textures retained: {}", e.getMessage()); }
  }
}
