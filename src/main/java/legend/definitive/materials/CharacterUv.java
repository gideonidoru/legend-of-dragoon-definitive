package legend.definitive.materials;

import legend.game.tmd.UvAdjustmentMetrics14;
import java.util.Map;

/** Inverts native battle UV relocation, then resolves the original CLUT's packed region. */
public final class CharacterUv implements MaterialUvMap {
  private final MaterialAtlas atlas;
  private final int columns, uOffset, vOffset;
  private final Map<Integer, MaterialAtlas.Region> regions;
  private final Map<Integer, legend.core.renderer.SurfaceResponse> surfaces;

  public CharacterUv(final MaterialAtlas atlas, final int columns, final UvAdjustmentMetrics14 relocation) {
    this(atlas, columns, relocation, Map.of());
  }

  public CharacterUv(final MaterialAtlas atlas, final int columns, final UvAdjustmentMetrics14 relocation,
                     final Map<Integer, legend.core.renderer.SurfaceResponse> surfaces) {
    if(columns < 1 || columns > 4) throw new IllegalArgumentException("Invalid palette columns");
    if(relocation.clutX % 64 != 0 || relocation.clutY % 16 != 0)
      throw new IllegalArgumentException("CLUT relocation overlaps original palette bits");
    this.surfaces = Map.copyOf(surfaces);
    this.atlas = atlas;
    this.columns = columns;
    this.uOffset = relocation.tpageX % 64 * 4;
    this.vOffset = relocation.tpageY % 256;
    this.regions = atlas.regions().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(MaterialAtlas.Region::palette, region -> region));
  }

  @Override public legend.core.renderer.SurfaceResponse surface(final int clut) {
    return this.surfaces.get((clut >>> 6 & 15) * this.columns + (clut & 3));
  }

  public float[] map(final int clut, final float u, final float v) {
    return this.map(clut, u, v, 0x34000000);
  }

  @Override public float[] map(final int clut, final float u, final float v, final int primitiveHeader) {
    final int palette = (clut >>> 6 & 15) * this.columns + (clut & 3);
    final var region = this.regions.get(palette);
    if(region == null) throw new IllegalArgumentException("Missing character palette");
    final int command = primitiveHeader & 0xff040000;
    final boolean relocated = command >= 0x34000000 && command <= 0x37000000 || command >= 0x3c000000 && command <= 0x3f000000;
    final float sourceU = originalCoordinate(u, relocated ? this.uOffset : 0), sourceV = originalCoordinate(v, relocated ? this.vOffset : 0);
    if(sourceU < region.left() + 8 || sourceU >= region.right() - 8 || sourceV < region.top() + 8 || sourceV >= region.bottom() - 8)
      throw new IllegalArgumentException("Character UV outside verified material");
    return new float[]{(region.x() + (sourceU - region.left()) * this.atlas.scale()) / this.atlas.width(),
      (region.y() + (sourceV - region.top()) * this.atlas.scale()) / this.atlas.height()};
  }

  public static float originalCoordinate(final float value, final int offset) {
    if(!Float.isFinite(value) || value < 0 || value > 255) throw new IllegalArgumentException("Invalid native UV");
    final float result = value - offset;
    return result < 0 ? result + 256 : result;
  }
}
