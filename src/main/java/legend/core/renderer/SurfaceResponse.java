package legend.core.renderer;

/** Optional face material and roughness, packed into unused TMD vertex flag bits. */
public record SurfaceResponse(SurfaceMaterial material, float roughness) {
  public SurfaceResponse {
    java.util.Objects.requireNonNull(material);
    if(!Float.isFinite(roughness) || roughness < 0.05f || roughness > 1) throw new IllegalArgumentException("Roughness must be in [0.05, 1]");
  }
  public SurfaceResponse(final SurfaceMaterial material) {
    this(material, (float)Math.sqrt((128 - material.exponent) / 124));
  }
  public int flags() {
    return 0x20 | this.material.ordinal() << 6 | Math.round(this.roughness * 255) << 9;
  }
}
