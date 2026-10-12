package legend.core.renderer;

import legend.core.renderer.noop.NoopApi;
import legend.game.tmd.TmdFaceDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SharedFaceTextureTest {
  @AfterEach void retire() { Obj.deleteObjects(); Texture.deleteTextures(); }
  private static TmdFaceDetail.Image image() { return new TmdFaceDetail.Image(ByteBuffer.allocateDirect(16), 2, 2); }
  private static Texture texture() { return new NoopApi().makeTexture(null, "shared fixture", 2, 2, TextureInternalFormat.RGBA_8, TextureDataFormat.RGBA, TextureDataType.UBYTE, true, true, false, false); }

  @Test void immutablePixelsAndIndependentCoordinatesShareAnImage() {
    final var source = ByteBuffer.allocateDirect(16); source.put(0, (byte)17);
    final var image = new TmdFaceDetail.Image(source, 2, 2);
    final var uv = new float[][]{{0,0,1,0,0,1}};
    final var first = new TmdFaceDetail(image, uv); final var second = new TmdFaceDetail(image, uv);
    source.put(0, (byte)99); uv[0][0] = 1;
    assertSame(first.image(), second.image()); assertEquals(17, first.pixels().get(0)); assertEquals(0, first.u(0,0));
    assertThrows(java.nio.ReadOnlyBufferException.class, () -> first.pixels().put(0, (byte)0));
    first.pixels().position(8); assertEquals(0, second.pixels().position());
  }

  @Test void oneUploadServesManyPartsUntilTheLastOwnerRetires() {
    final var count = new AtomicInteger();
    final var cache = new SharedFaceTexture((name, image) -> {count.incrementAndGet(); return texture();});
    final var image = image(); final var first = cache.retain("body", image); final var second = cache.retain("body", image);
    final var texture = first.texture(); assertSame(texture, second.texture()); assertEquals(1, count.get());
    first.close(); first.close(); assertFalse(texture.deleted);
    second.close(); assertTrue(texture.deleted); assertThrows(IllegalStateException.class, second::texture);
  }

  @Test void queuedMeshDeletionKeepsSharedTextureUntilRendererRetirement() {
    final var cache = new SharedFaceTexture((name, image) -> texture()); final var lease = cache.retain("body", image());
    final var obj = new FixtureObj(); obj.faceDetailTexture(lease); final var texture = lease.texture();
    obj.delete(); Texture.deleteTextures(); assertFalse(texture.deleted);
    Obj.deleteObjects(); assertTrue(texture.deleted); assertTrue(obj.retired);
  }

  @Test void twoMeshesRetireIndependentlyAndDeleteTheBackendExactlyOnce() {
    final var texture = new CountingTexture();
    final var cache = new SharedFaceTexture((name, image) -> texture);
    final var image = image();
    final var first = new FixtureObj(); first.faceDetailTexture(cache.retain("body", image));
    final var second = new FixtureObj(); second.faceDetailTexture(cache.retain("body", image));
    first.delete(); Obj.deleteObjects(); Texture.deleteTextures();
    assertEquals(0, texture.deletions); assertSame(texture, second.faceDetailTexture());
    second.delete(); Obj.deleteObjects(); Texture.deleteTextures();
    Obj.deleteObjects(); Texture.deleteTextures();
    assertEquals(1, texture.deletions);
  }

  @Test void failedUploadCanRetryAndDoesNotPoisonTheCache() {
    final var count = new AtomicInteger(); final var image = image();
    final var cache = new SharedFaceTexture((name, pixels) -> {if(count.getAndIncrement() == 0) throw new IllegalStateException("upload failed"); return texture();});
    assertThrows(IllegalStateException.class, () -> cache.retain("body", image));
    try(final var lease = cache.retain("body", image)) { assertNotNull(lease.texture()); assertEquals(2, count.get()); }
  }

  @Test void oldContextOwnersCannotDeleteAReplacementUpload() {
    final var cache = new SharedFaceTexture((name, pixels) -> texture()); final var image = image();
    final var old = cache.retain("body", image); old.texture().delete();
    final var fresh = cache.retain("body", image); final var another = cache.retain("body", image);
    assertNotSame(old.texture(), fresh.texture()); old.close(); assertFalse(fresh.texture().deleted);
    assertSame(fresh.texture(), another.texture()); fresh.close(); assertFalse(another.texture().deleted); another.close();
  }

  private static final class FixtureObj extends Obj {
    boolean retired;
    FixtureObj() { super("retirement fixture"); }
    @Override protected void performDelete() { this.retired = true; }
    @Override public boolean hasTexture() {return true;}
    @Override public boolean hasTexture(int index) {return true;}
    @Override public boolean hasTranslucency() {return false;}
    @Override public boolean hasTranslucency(int index) {return false;}
    @Override public boolean shouldRender(Translucency translucency) {return true;}
    @Override public boolean shouldRender(Translucency translucency, int layer) {return true;}
    @Override public int getLayers() {return 1;}
    @Override public void render(int layer, int start, int count) { }
    @Override public void render(Translucency translucency, int layer, int start, int count) { }
  }

  private static final class CountingTexture extends Texture {
    int deletions;
    CountingTexture() { super("backend retirement fixture", 2, 2); }
    @Override protected void performDelete() { this.deletions++; }
    @Override public void data(int x,int y,int w,int h,TextureDataType type,ByteBuffer data) { }
    @Override public void data(int x,int y,int w,int h,TextureDataType type,int[] data) { }
    @Override public void use(int unit) { }
    @Override public void use() { }
    @Override public TextureInternalFormat internalFormat() { return TextureInternalFormat.RGBA_8; }
    @Override public TextureDataFormat dataFormat() { return TextureDataFormat.RGBA; }
    @Override public TextureDataType dataType() { return TextureDataType.UBYTE; }
    @Override public boolean minFilter() { return true; }
    @Override public boolean magFilter() { return true; }
    @Override public boolean wrapS() { return false; }
    @Override public boolean wrapT() { return false; }
  }
}
