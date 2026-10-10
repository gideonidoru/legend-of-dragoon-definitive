// ModelsHD optional geometry adapter, AGPL v3; see LICENSE.
package modelshd;
import legend.game.tmd.TmdObjTable1c;
import legend.definitive.models.TmdGeometryPack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Backward-compatible mod facade over the engine's bounded source-bound reader. */
public final class ModelPack {
  private ModelPack() { }
  public static String identity(final TmdObjTable1c[] parts) { return TmdGeometryPack.identity(parts); }
  public static TmdObjTable1c[] read(final Path path, final TmdObjTable1c[] originals) throws IOException { return TmdGeometryPack.read(path, originals); }
  public static TmdObjTable1c[] read(final InputStream input, final TmdObjTable1c[] originals) throws IOException { return TmdGeometryPack.read(input, originals); }
  static int integer(final com.google.gson.JsonElement value) throws IOException { return TmdGeometryPack.integer(value); }
}
