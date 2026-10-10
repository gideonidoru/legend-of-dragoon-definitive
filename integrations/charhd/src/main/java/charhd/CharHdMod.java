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
  public CharHdMod() { legend.core.GameEngine.EVENTS.register(this); }
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
