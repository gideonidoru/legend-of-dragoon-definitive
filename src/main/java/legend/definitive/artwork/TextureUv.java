package legend.definitive.artwork;

/** Optional per-material remapping. Null retains that face's native indexed UVs. */
@FunctionalInterface
public interface TextureUv {
  float[] map(int page, int clut, float u, float v);
}
