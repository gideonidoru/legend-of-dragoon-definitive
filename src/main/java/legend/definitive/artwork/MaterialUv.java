package legend.definitive.artwork;

import legend.definitive.materials.MaterialAtlas;

/** Source CLUT identity to packed float UVs; original packets remain untouched. */
public final class MaterialUv {
  private final MaterialAtlas atlas;
  private final int paletteColumns;
  public MaterialUv(final MaterialAtlas atlas, final int paletteColumns) {
    this.atlas = atlas;
    this.paletteColumns = paletteColumns;
  }
  public float[] map(final int clut, final float u, final float v) {
    final int palette = (clut >>> 6 & 15) * this.paletteColumns + (clut & 3);
    final var region = this.atlas.regions().stream().filter(r -> r.palette() == palette).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing atlas palette"));
    return coordinates(region, this.atlas.scale(), this.atlas.width(), this.atlas.height(), u, v);
  }
  public static float[] coordinates(final MaterialAtlas.Region region, final int scale, final int width, final int height, final float u, final float v) {
    return new float[]{(region.x() + (u - region.left()) * scale) / width, (region.y() + (v - region.top()) * scale) / height};
  }
}
