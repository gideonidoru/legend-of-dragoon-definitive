// Definitive rendering acceptance (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.rendering;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class ContactShadowTest {
  @Test void softenedMeshTilesADiscWithoutFlippedOrOverlappingRings() {
    final float radius = 32;
    final float[] data = ContactShadow.vertices(true, radius);
    double area = 0;
    for(int i = 0; i < data.length; i += 12) {
      final double signed = (data[i + 4] - data[i]) * (data[i + 10] - data[i + 2]) - (data[i + 6] - data[i + 2]) * (data[i + 8] - data[i]);
      assertTrue(signed > 0, "Consistent nondegenerate triangle winding");
      area += signed / 2;
    }
    final double expected = 32 * Math.sin(2 * Math.PI / 32) * radius * radius / 2;
    assertEquals(expected, area, 0.001, "Bands tile the bounded polygon exactly once");
    for(int i = 0; i < data.length; i += 4) {
      final double distance = Math.hypot(data[i], data[i + 2]);
      assertTrue(distance <= radius + 0.00001);
      assertEquals(0, data[i + 1]);
      assertTrue(data[i + 3] >= 0 && data[i + 3] <= 0.5);
      if(Math.abs(distance - radius) < 0.00001) assertEquals(0, data[i + 3], "Fringe vanishes at outer boundary");
    }
  }

  @Test void legacyChoiceRetainsEightTrianglesAndRadialFalloff() {
    final float[] data = ContactShadow.vertices(false, 32);
    assertEquals(8 * 3 * 4, data.length);
    for(int i = 0; i < data.length; i += 12) {
      assertEquals(0.5f, data[i + 3]);
      assertEquals(0, data[i + 7]);
      assertEquals(0, data[i + 11]);
      assertEquals(32, Math.hypot(data[i + 4], data[i + 6]), 0.00001);
    }
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.vertices(true, Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.vertices(true, -1));
  }
  @Test void actorFootprintRetainsCenterPlaneAndExtentsWithoutMutatingSource() {
    final Vector3f[] source = {new Vector3f(-1900, -2, -1700), new Vector3f(2100, -2, -1700), new Vector3f(2100, -2, 1800), new Vector3f(-1900, -2, 1800)};
    final float[] data = ContactShadow.forFootprint(source);
    float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
    float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
    for(int i = 0; i < data.length; i += 4) {
      minX = Math.min(minX, data[i]); maxX = Math.max(maxX, data[i]);
      minZ = Math.min(minZ, data[i + 2]); maxZ = Math.max(maxZ, data[i + 2]);
      assertEquals(-2, data[i + 1]);
      assertTrue(data[i + 3] >= 0 && data[i + 3] <= 0.48f);
      final double radius = Math.hypot((data[i] - 100) / 2000, (data[i + 2] - 50) / 1750);
      assertTrue(radius <= 1.000001);
      if(radius > 0.99999) assertEquals(0, data[i + 3]);
    }
    assertEquals(-1900, minX); assertEquals(2100, maxX);
    assertEquals(-1700, minZ); assertEquals(1800, maxZ);
    assertEquals(new Vector3f(-1900, -2, -1700), source[0]);
    source[0].y = 5;
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.forFootprint(source));
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.forFootprint(new Vector3f[] {new Vector3f(), new Vector3f(), new Vector3f()}));
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.forFootprint(new Vector3f[] {null, new Vector3f(), new Vector3f()}));
    assertThrows(IllegalArgumentException.class, () -> ContactShadow.forFootprint(null));
  }

  @Test void scriptedShadowTintRetainsNativeChannelOverflow() {
    assertEquals(0, ContactShadow.effectTint(0));
    assertEquals(128.0f / 255.0f * 2, ContactShadow.effectTint(1));
    assertEquals(2, ContactShadow.effectTint(255.0f / 128.0f));
    final float overflow = 3 * (128.0f / 255.0f * 2);
    assertEquals(overflow - 2, ContactShadow.effectTint(3), 0.000001f);
    assertEquals(-0.5f * (128.0f / 255.0f * 2), ContactShadow.effectTint(-0.5f));
  }

}
