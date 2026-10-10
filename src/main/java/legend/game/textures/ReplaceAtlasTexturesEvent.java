package legend.game.textures;

import org.legendofdragoon.modloader.events.Event;
import org.legendofdragoon.modloader.registries.RegistryId;
import java.util.Map;

/** Optional replacements after ordinary registration. Existing artwork can be checked first. */
public final class ReplaceAtlasTexturesEvent extends Event {
  private final Map<RegistryId, Image> images;
  public ReplaceAtlasTexturesEvent(final Map<RegistryId, Image> images) { this.images = images; }
  public Image get(final RegistryId id) { return this.images.get(id); }
  public boolean replace(final RegistryId id, final Image expected, final Image replacement) {
    final Image current = this.images.get(id);
    if(current == null || current.width != expected.width || current.height != expected.height || !java.util.Arrays.equals(current.data, expected.data)) return false;
    this.images.put(id, replacement); return true;
  }
}
