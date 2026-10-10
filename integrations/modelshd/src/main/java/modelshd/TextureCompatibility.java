// ModelsHD active texture handoff (2026-10-10), AGPL v3; see LICENSE.
package modelshd;

import legend.core.renderer.MeshObj;
import legend.game.tmd.TmdObjTable1c;

/** Advanced UV/mesh overrides retain priority unless their active addressing matches the typed native route. */
public final class TextureCompatibility {
  private TextureCompatibility() { }
  public static void requireNativeAddressing(final TmdObjTable1c[] sources) {
    for(final var source : sources) {
      final var control = new TmdObjTable1c("ModelsHD material handoff control", source.vert_top_00, source.normal_top_08, source.primitives_10);
      try {
        final var active = (MeshObj)source.getObj();
        final var expected = (MeshObj)control.buildObjLike(source);
        if(active.meshes.length != expected.meshes.length || active.useBackfaceCulling() != expected.useBackfaceCulling()) throw new IllegalArgumentException("Active texture mesh route differs");
        for(int i = 0; i < active.meshes.length; i++) {
          final float[] actual = active.meshes[i].vertices(), original = expected.meshes[i].vertices();
          if(actual.length != original.length) throw new IllegalArgumentException("Active texture vertex mapping differs");
          for(int row = 0; row < actual.length; row += 16) for(int field = 7; field < 16; field++) {
            if(Float.floatToIntBits(actual[row + field]) != Float.floatToIntBits(original[row + field])) throw new IllegalArgumentException("Active UV/material override requires a declared ModelsHD handoff");
          }
        }
      } finally { control.delete(); }
    }
  }
}
