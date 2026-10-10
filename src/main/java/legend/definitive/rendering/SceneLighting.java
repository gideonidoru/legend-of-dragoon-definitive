// Definitive scene-light tuning (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

/** Bounded scene-light response applied to every opaque lit model, using its authored lights. */
public record SceneLighting(float keyR, float keyG, float keyB, float ambientR, float ambientG, float ambientB) {
  public static final SceneLighting ORIGINAL = new SceneLighting(1, 1, 1, 1, 1, 1);
  // Neutral gains retain scene hue; softened light terminators are evaluated in both shader paths.
  public static final SceneLighting ENHANCED = new SceneLighting(1.03f, 1.03f, 1.03f, 0.98f, 0.98f, 0.98f);

  public SceneLighting {
    for(final float value : new float[] {keyR, keyG, keyB, ambientR, ambientG, ambientB}) {
      if(!Float.isFinite(value) || value < 0.5f || value > 1.5f) {
        throw new IllegalArgumentException("Scene lighting gains must be finite and bounded");
      }
    }
  }

}
