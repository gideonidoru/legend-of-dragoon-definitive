package legend.definitive.materials;

import legend.core.renderer.SurfaceResponse;

/** Atomic HD addressing at vertex construction; source packets are never changed. */
public interface MaterialUvMap {
  float[] map(int clut, float u, float v, int primitiveHeader);
  default SurfaceResponse surface(final int clut) { return null; }
}
