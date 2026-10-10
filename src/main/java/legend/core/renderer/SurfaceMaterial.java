package legend.core.renderer;

/** Authored surface response; never inferred from texture colors or actor names. */
public enum SurfaceMaterial {
  MATTE(12.0f, 0.018f),
  CLOTH(6.0f, 0.008f),
  SKIN(24.0f, 0.035f),
  METAL(64.0f, 0.16f);

  public final float exponent;
  public final float strength;

  SurfaceMaterial(final float exponent, final float strength) {
    this.exponent = exponent;
    this.strength = strength;
  }
}
