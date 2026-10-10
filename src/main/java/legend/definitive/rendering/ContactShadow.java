// Definitive contact shadows (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import org.joml.Vector3f;

/** Triangle positions and subtractive darkness; no texture, extra pass or frame allocation. */
public final class ContactShadow {
  private ContactShadow() { }

  public static float[] vertices(final boolean soft, final float radius) {
    if(!Float.isFinite(radius) || radius <= 0 || radius > 1024) {
      throw new IllegalArgumentException("Shadow radius must be finite and bounded");
    }
    final int segments = soft ? 32 : 8;
    final float[] rings = soft ? new float[] {0, 0.35f, 0.7f, 1} : new float[] {0, 1};
    final float[] darkness = soft ? new float[] {0.48f, 0.3f, 0.09f, 0} : new float[] {0.5f, 0};
    final float[] vertices = new float[segments * (1 + (rings.length - 2) * 2) * 3 * 4];
    int offset = 0;
    for(int segment = 0; segment < segments; segment++) {
      final double a = segment * Math.PI * 2 / segments;
      final double b = (segment + 1) * Math.PI * 2 / segments;
      offset = vertex(vertices, offset, 0, 0, darkness[0]);
      offset = vertex(vertices, offset, Math.cos(a) * rings[1] * radius, Math.sin(a) * rings[1] * radius, darkness[1]);
      offset = vertex(vertices, offset, Math.cos(b) * rings[1] * radius, Math.sin(b) * rings[1] * radius, darkness[1]);
      for(int ring = 1; ring < rings.length - 1; ring++) {
        offset = vertex(vertices, offset, Math.cos(a) * rings[ring] * radius, Math.sin(a) * rings[ring] * radius, darkness[ring]);
        offset = vertex(vertices, offset, Math.cos(a) * rings[ring + 1] * radius, Math.sin(a) * rings[ring + 1] * radius, darkness[ring + 1]);
        offset = vertex(vertices, offset, Math.cos(b) * rings[ring + 1] * radius, Math.sin(b) * rings[ring + 1] * radius, darkness[ring + 1]);
        offset = vertex(vertices, offset, Math.cos(a) * rings[ring] * radius, Math.sin(a) * rings[ring] * radius, darkness[ring]);
        offset = vertex(vertices, offset, Math.cos(b) * rings[ring + 1] * radius, Math.sin(b) * rings[ring + 1] * radius, darkness[ring + 1]);
        offset = vertex(vertices, offset, Math.cos(b) * rings[ring] * radius, Math.sin(b) * rings[ring] * radius, darkness[ring]);
      }
    }
    return vertices;
  }

  /** Fit a soft disc to the loaded native shadow, retaining its center, plane and extents. */
  public static float[] forFootprint(final Vector3f[] footprint) {
    if(footprint == null || footprint.length < 3 || footprint[0] == null) throw new IllegalArgumentException("Shadow footprint requires at least three vertices");
    float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
    float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
    final float y = footprint[0].y;
    for(final Vector3f vertex : footprint) {
      if(vertex == null || !vertex.isFinite() || Math.abs(vertex.y - y) > 0.001f) throw new IllegalArgumentException("Shadow footprint must be finite and horizontal");
      minX = Math.min(minX, vertex.x); maxX = Math.max(maxX, vertex.x);
      minZ = Math.min(minZ, vertex.z); maxZ = Math.max(maxZ, vertex.z);
    }
    final float radiusX = (maxX - minX) / 2, radiusZ = (maxZ - minZ) / 2;
    if(radiusX <= 0 || radiusZ <= 0 || radiusX > 32768 || radiusZ > 32768) throw new IllegalArgumentException("Shadow footprint is degenerate or excessive");
    final float centerX = (maxX + minX) / 2, centerZ = (maxZ + minZ) / 2;
    final float[] data = vertices(true, 1);
    for(int i = 0; i < data.length; i += 4) {
      data[i] = centerX + data[i] * radiusX;
      data[i + 1] = y;
      data[i + 2] = centerZ + data[i + 2] * radiusZ;
    }
    return data;
  }

  /** Native shadow packets use 0x80 RGB; retain the CTMD shader's per-channel overflow. */
  public static float effectTint(final float battleColour) {
    final float tinted = battleColour * (128.0f / 255.0f * 2.0f);
    return tinted > 2.0f ? tinted % 2.0f : tinted;
  }

  private static int vertex(final float[] data, final int offset, final double x, final double z, final float darkness) {
    data[offset] = (float)x;
    data[offset + 1] = 0;
    data[offset + 2] = (float)z;
    data[offset + 3] = darkness;
    return offset + 4;
  }
}
