// Definitive contact shadows (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import legend.core.renderer.Obj;
import legend.core.renderer.PolyBuilder;
import legend.core.renderer.Translucency;
import legend.core.renderer.VertexOrder;
import legend.game.tmd.TmdObjTable1c;

import static legend.core.GameEngine.CONFIG;
import static legend.game.modding.coremod.CoreMod.SOFT_CONTACT_SHADOWS_CONFIG;

/** One persistent shared mesh; actor transforms, projection, depth and blend remain with callers. */
public final class ActorShadow {
  private static TmdObjTable1c source;
  private static Obj softened;

  private ActorShadow() { }

  public static void reset() {
    if(softened != null) softened.delete();
    source = null;
    softened = null;
  }

  public static Obj get(final TmdObjTable1c original) {
    if(!CONFIG.getConfig(SOFT_CONTACT_SHADOWS_CONFIG.get())) return original.getObj();
    if(source != original) {
      final float[] data;
      try {
        data = ContactShadow.forFootprint(original.vert_top_00);
      } catch(final IllegalArgumentException unsupportedFootprint) {
        reset();
        source = original;
        return original.getObj();
      }
      final PolyBuilder builder = new PolyBuilder("Soft actor contact shadow", VertexOrder.TRIANGLES_ADJACENCY)
        .translucency(Translucency.B_MINUS_F);
      // TMD geometry shaders consume vertices 0,2,4 of each adjacency primitive.
      for(int i = 0; i < data.length; i += 4) {
        for(int adjacency = 0; adjacency < 2; adjacency++) {
          builder.addVertex(data[i], data[i + 1], data[i + 2]).monochrome(data[i + 3]);
        }
      }
      final Obj replacement = builder.build();
      replacement.persistent = true;
      reset();
      source = original;
      softened = replacement;
    }
    return softened != null ? softened : original.getObj();
  }

  public static boolean isSoft(final Obj obj) {
    return obj != null && obj == softened;
  }
}
