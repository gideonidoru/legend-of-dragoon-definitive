// ModelsHD optional geometry adapter (2026-10-10), AGPL v3; see LICENSE.
package modelshd;

import legend.game.tmd.TmdObjTable1c;
import legend.game.types.Model124;
import java.util.IdentityHashMap;

public final class ModelReplacement {
  private ModelReplacement() { }

  /** Build every GPU part before changing any model reference. Animation state stays owned by the game. */
  public static void install(final Model124 model, final TmdObjTable1c[] replacements) {
    if(replacements.length != model.modelParts_00.length) throw new IllegalArgumentException("Animation part count differs");
    final var source = new IdentityHashMap<TmdObjTable1c, Boolean>();
    for(final var part : model.modelParts_00) source.put(part.tmd_08, Boolean.TRUE);
    final var owned = new IdentityHashMap<TmdObjTable1c, Boolean>();
    for(int i = 0; i < replacements.length; i++) {
      if(replacements[i] == null || owned.put(replacements[i], Boolean.TRUE) != null || source.containsKey(replacements[i])) throw new IllegalArgumentException("Replacement tables must be owned and distinct");
    }
    try {
      for(int i = 0; i < replacements.length; i++) replacements[i].buildObjLike(model.modelParts_00[i].tmd_08);
    } catch(final RuntimeException | Error failure) {
      for(final var replacement : replacements) replacement.delete();
      throw failure;
    }
    final TmdObjTable1c[] originals = new TmdObjTable1c[replacements.length];
    for(int i = 0; i < replacements.length; i++) {
      originals[i] = model.modelParts_00[i].tmd_08;
      model.modelParts_00[i].tmd_08 = replacements[i];
    }
    for(final var original : originals) original.delete();
  }
}
