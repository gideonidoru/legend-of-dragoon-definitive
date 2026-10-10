package legend.definitive.rendering;

import legend.core.renderer.Texture;
import java.nio.ByteBuffer;

/** Shared neutral micro-surface finish. Never interprets painted color as height or metal. */
public final class DefaultMaterialMaps {
  public static final int SIZE = 64;
  public static final float STRENGTH = 0.35f;
  private static Texture normal, roughness;
  private static boolean failed;
  private DefaultMaterialMaps() { }
  private static double height(final int x, final int y) {
    final double u = x * (Math.PI * 2 / SIZE), v = y * (Math.PI * 2 / SIZE);
    return Math.sin(u*5+v*3)*0.012 + Math.cos(u*3-v*7)*0.009 + Math.sin(u*11+v*9)*0.004;
  }
  /** Original periodic data, generated once, independent of game artwork and palette animation. */
  public static ByteBuffer pixels(final boolean normals) {
    final ByteBuffer data = ByteBuffer.allocateDirect(SIZE*SIZE*4);
    for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
      if(normals) {
        final double nx = -(height(x+1,y)-height(x-1,y))*2, ny = -(height(x,y+1)-height(x,y-1))*2;
        final double length = Math.sqrt(nx*nx+ny*ny+1);
        data.put((byte)Math.round((nx/length*.5+.5)*255)).put((byte)Math.round((ny/length*.5+.5)*255)).put((byte)Math.round((1/length*.5+.5)*255));
      } else {
        final int value = (int)Math.round(127.5 + height(x,y)*1800);
        data.put((byte)value).put((byte)value).put((byte)value);
      }
      data.put((byte)255);
    }
    return data.flip();
  }
  private static Texture create(final boolean normals) {
    final Texture texture = Texture.create(normals ? "Default surface normals" : "Default surface roughness", builder -> {
      builder.data(pixels(normals),SIZE,SIZE); builder.minFilter(true); builder.magFilter(true);
    }).hdFiltering();
    texture.persistent = true;
    return texture;
  }
  public static boolean bind() {
    if(failed) return false;
    try {
      if(normal == null) normal = create(true);
      if(roughness == null) roughness = create(false);
      normal.use(4); roughness.use(5);
      return true;
    } catch(final RuntimeException failure) {
      delete(); failed=true;
      org.apache.logging.log4j.LogManager.getLogger().warn("Default surface maps unavailable; retaining authored model shading",failure);
      return false;
    }
  }
  public static void delete() {
    if(normal != null) normal.delete();
    if(roughness != null) roughness.delete();
    normal = roughness = null;
    failed=false;
  }
}
