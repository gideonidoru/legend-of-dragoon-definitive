package legend.definitive.artwork;

import legend.core.renderer.*;
import legend.game.submap.AttachedSobjEffect;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class EffectLifecycleTest {
  private static void set(final AttachedSobjEffect effects, final String name, final Object value) throws Exception {
    final var field = AttachedSobjEffect.class.getDeclaredField(name); field.setAccessible(true); field.set(effects, value);
  }
  private static Object get(final AttachedSobjEffect effects, final String name) throws Exception {
    final var field = AttachedSobjEffect.class.getDeclaredField(name); field.setAccessible(true); return field.get(effects);
  }
  @Test void teardownReleasesReplacementAndDisabledCycleKeepsItAbsent() throws Exception {
    final var effects = new AttachedSobjEffect();
    for(int cycle = 0; cycle < 2; cycle++) {
      final var obj = new OwnedObj(); final var texture = new OwnedTexture();
      set(effects, "quadDustHd", obj); set(effects, "dustHdTexture", texture);
      effects.deallocateAttachedSobjEffects();
      assertNull(get(effects, "quadDustHd")); assertNull(get(effects, "dustHdTexture"));
      Obj.deleteObjects(); Texture.deleteTextures();
      assertEquals(1, obj.deletes); assertEquals(1, texture.deletes);
      // No replacement loaded in the next cycle (FxHD disabled).
      effects.deallocateAttachedSobjEffects();
      assertNull(get(effects, "quadDustHd")); assertNull(get(effects, "dustHdTexture"));
    }
  }
  private static final class OwnedObj extends Obj {
    int deletes;
    OwnedObj() { super("test HD dust"); }
    protected void performDelete() { this.deletes++; }
    public boolean hasTexture() { return true; }
    public boolean hasTexture(final int index) { return true; }
    public boolean hasTranslucency() { return true; }
    public boolean hasTranslucency(final int index) { return true; }
    public boolean shouldRender(final Translucency t) { return true; }
    public boolean shouldRender(final Translucency t, final int layer) { return true; }
    public int getLayers() { return 1; }
    public void render(final int layer, final int start, final int count) { fail("No rendering during teardown"); }
    public void render(final Translucency t, final int layer, final int start, final int count) { fail("No rendering during teardown"); }
  }
  private static final class OwnedTexture extends Texture {
    int deletes;
    OwnedTexture() { super("test HD dust", 1, 1); }
    protected void performDelete() { this.deletes++; }
    public void data(final int x, final int y, final int w, final int h, final TextureDataType t, final ByteBuffer b) { fail(); }
    public void data(final int x, final int y, final int w, final int h, final TextureDataType t, final int[] b) { fail(); }
    public void use(final int active) { fail(); }
    public void use() { fail(); }
    public TextureInternalFormat internalFormat() { return TextureInternalFormat.RGBA_8; }
    public TextureDataFormat dataFormat() { return TextureDataFormat.RGBA; }
    public TextureDataType dataType() { return TextureDataType.UBYTE; }
    public boolean minFilter() { return false; }
    public boolean magFilter() { return false; }
    public boolean wrapS() { return false; }
    public boolean wrapT() { return false; }
  }
}
