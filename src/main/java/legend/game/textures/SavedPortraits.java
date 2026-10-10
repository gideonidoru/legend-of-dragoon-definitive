package legend.game.textures;

import legend.core.gpu.Rect4i;
import legend.game.saves.SeveredSavedGame;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/** Display-only restoration of matching old portraits; custom snapshots and save bytes stay authoritative. */
public final class SavedPortraits {
  private SavedPortraits() { }
  public record Selected(Image image, List<Rect4i> rectangles) { }
  private static org.legendofdragoon.modloader.registries.RegistryId id(final legend.game.saves.SavedCharacter character, final int index) {
    final var id = character.portraitId();
    return id == null ? new org.legendofdragoon.modloader.registries.RegistryId("saved_snapshot", "portrait_" + index) : id;
  }
  public static Selected select(final SeveredSavedGame savedGame) throws IOException {
    final Image source = UiTextures.decode(savedGame.atlas.getBytes());
    final Selected unchanged = new Selected(source, savedGame.charPortraits);
    final TexturePacker packer = new TexturePacker("Save card portraits");
    final var identities = new java.util.HashMap<org.legendofdragoon.modloader.registries.RegistryId, Image>();
    try {
      for(int i = 0; i < savedGame.characters.size(); i++) {
        final Rect4i rect = savedGame.charPortraits.get(i);
        if(rect.x < 0 || rect.y < 0 || rect.w < 1 || rect.h < 1 || rect.x + rect.w > source.width || rect.y + rect.h > source.height) return unchanged;
        final byte[] pixels = new byte[rect.w * rect.h * 4];
        for(int y = 0; y < rect.h; y++) System.arraycopy(source.data, ((rect.y + y) * source.width + rect.x) * 4, pixels, y * rect.w * 4, rect.w * 4);
        final var id = id(savedGame.characters.get(i), i);
        final Image image = new Image(pixels, rect.w, rect.h), previous = identities.put(id, image);
        if(previous != null && (previous.width != image.width || previous.height != image.height || !java.util.Arrays.equals(previous.data, image.data))) return unchanged;
        packer.add(id, image);
      }
      if(!packer.applyReplacements()) return unchanged;
      final var packed = packer.packGrowingToBytes(512, 512, 2048);
      final List<Rect4i> rectangles = new ArrayList<>();
      for(int i = 0; i < savedGame.characters.size(); i++) {
        final var rect = packer.getRect(id(savedGame.characters.get(i), i));
        rectangles.add(new Rect4i(rect.x, rect.y, rect.w, rect.h));
      }
      return new Selected(new Image(packed.data(), packed.width(), packed.height()), rectangles);
    } catch(final RuntimeException failure) {
      org.apache.logging.log4j.LogManager.getLogger(SavedPortraits.class).warn("Retaining original saved-card portraits", failure);
      return unchanged;
    }
  }
}
