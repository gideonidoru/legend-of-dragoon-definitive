package legend.definitive.rendering;

import org.joml.Vector3fc;
import java.nio.FloatBuffer;

/** Bounded, allocation-free light collection. Nearby particles share a light. */
public final class EffectLights {
  public static final int CAPACITY = 4;
  private final float[] positions = new float[CAPACITY * 4];
  private final float[] colours = new float[CAPACITY * 4];
  private int count;

  public void clear() { this.count = 0; }
  public int count() { return this.count; }

  public void add(final Vector3fc position, final float r, final float g, final float b, final float radius) {
    if(!Float.isFinite(position.x()) || !Float.isFinite(position.y()) || !Float.isFinite(position.z()) || !Float.isFinite(radius) || radius <= 0 || !Float.isFinite(r + g + b)) return;
    if(Math.abs(position.x()) > 1_000_000 || Math.abs(position.y()) > 1_000_000 || Math.abs(position.z()) > 1_000_000) return;
    final float red = Math.max(0, Math.min(1, r));
    final float green = Math.max(0, Math.min(1, g));
    final float blue = Math.max(0, Math.min(1, b));
    final float score = red * 0.2126f + green * 0.7152f + blue * 0.0722f;
    if(score < 0.02f) return;
    final float reach = Math.min(512, radius);
    int slot = this.count;
    for(int i = 0; i < this.count; i++) {
      final int p = i * 4;
      final float dx = this.positions[p] - position.x(), dy = this.positions[p + 1] - position.y(), dz = this.positions[p + 2] - position.z();
      if(dx * dx + dy * dy + dz * dz < reach * reach * 0.0625f) {
        if(score <= this.colours[p + 3]) return;
        slot = i;
        break;
      }
    }
    if(slot == CAPACITY) {
      slot = 0;
      for(int i = 1; i < CAPACITY; i++) if(this.colours[i * 4 + 3] < this.colours[slot * 4 + 3]) slot = i;
      if(score <= this.colours[slot * 4 + 3]) return;
    } else if(slot == this.count) {
      this.count++;
    }
    final int p = slot * 4;
    this.positions[p] = position.x(); this.positions[p + 1] = position.y(); this.positions[p + 2] = position.z(); this.positions[p + 3] = reach;
    this.colours[p] = red * 0.22f; this.colours[p + 1] = green * 0.22f; this.colours[p + 2] = blue * 0.22f; this.colours[p + 3] = score;
  }

  public void store(final FloatBuffer positions, final FloatBuffer colours) {
    positions.clear(); positions.put(this.positions).flip();
    colours.clear(); colours.put(this.colours).flip();
  }
}
