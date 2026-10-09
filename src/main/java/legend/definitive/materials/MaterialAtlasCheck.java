// Definitive private atlas preflight (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.materials;

import java.nio.file.Path;

/** Read-only CLI; no game boot, GPU upload, extraction or output assets. */
public final class MaterialAtlasCheck {
  private MaterialAtlasCheck() { }
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("Usage: ORIGINAL_MODEL ORIGINAL_TIM PRIVATE_PACK_FOLDER");
    final MaterialAtlas atlas = MaterialAtlas.read(Path.of(args[2]), MaterialAtlas.readBounded(Path.of(args[0]), 16 * 1024 * 1024), MaterialAtlas.readBounded(Path.of(args[1]), 16 * 1024 * 1024));
    System.out.printf("{\"validation\":\"definitive-java-material-preflight-1\",\"modelSha256\":\"%s\",\"timSha256\":\"%s\",\"atlasSha256\":\"%s\",\"scale\":%d,\"atlasSize\":[%d,%d],\"materials\":%d,\"checkedMaterialTexels\":%d,\"rgbaBytes\":%d,\"nativeRendering\":false}%n", atlas.modelHash(), atlas.timHash(), atlas.atlasHash(), atlas.scale(), atlas.width(), atlas.height(), atlas.regions().size(), atlas.checkedTexels(), atlas.rgba().remaining());
  }
}
