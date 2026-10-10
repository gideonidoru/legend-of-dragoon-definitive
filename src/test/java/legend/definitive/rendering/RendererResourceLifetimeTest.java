package legend.definitive.rendering;

import legend.core.renderer.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererResourceLifetimeTest {
  static final class CountedTexture extends Texture {
    int releases;
    CountedTexture() { super("lifetime fixture", 1, 1); }
    protected void performDelete() { releases++; }
    public void data(int x,int y,int w,int h,TextureDataType t,ByteBuffer b) { }
    public void data(int x,int y,int w,int h,TextureDataType t,int[] b) { }
    public void use(int i) { }
    public void use() { }
    public TextureInternalFormat internalFormat() { return TextureInternalFormat.RGBA_8; }
    public TextureDataFormat dataFormat() { return TextureDataFormat.RGBA; }
    public TextureDataType dataType() { return TextureDataType.UBYTE; }
    public boolean minFilter() { return false; }
    public boolean magFilter() { return false; }
    public boolean wrapS() { return false; }
    public boolean wrapT() { return false; }
  }
  static final class CountedObj extends Obj {
    int releases;
    CountedObj() { super("lifetime fixture"); }
    protected void performDelete() { releases++; }
    public boolean hasTexture() { return false; }
    public boolean hasTexture(int i) { return false; }
    public boolean hasTranslucency() { return false; }
    public boolean hasTranslucency(int i) { return false; }
    public boolean shouldRender(Translucency t) { return true; }
    public boolean shouldRender(Translucency t,int layer) { return true; }
    public int getLayers() { return 1; }
    public void render(int layer,int start,int count) { }
    public void render(Translucency t,int layer,int start,int count) { }
  }
  @Test void sceneCleanupMustReleaseTextureBackendExactlyOnce() {
    Texture.setShouldLog(false);
    final var texture = new CountedTexture();
    try {
      Texture.clearTextureList(false);
      Texture.deleteTextures();
      assertEquals(1,texture.releases,"Scene cleanup lost the texture before backend retirement");
    } finally { Texture.setShouldLog(true); }
  }
  @Test void sceneCleanupMustReleaseMeshBackendExactlyOnce() {
    Obj.setShouldLog(false);
    final var mesh = new CountedObj();
    try {
      Obj.clearObjList(false);
      Obj.deleteObjects();
      assertEquals(1,mesh.releases,"Scene cleanup lost the mesh before backend retirement");
    } finally { Obj.setShouldLog(true); }
  }
  @Test void persistentAndAlreadyRetiredResourcesReleaseExactlyOnce() {
    Texture.setShouldLog(false); Obj.setShouldLog(false);
    final var texture=new CountedTexture(); final var mesh=new CountedObj();
    texture.persistent=mesh.persistent=true;
    try {
      Texture.clearTextureList(false); Obj.clearObjList(false);
      Texture.deleteTextures(); Obj.deleteObjects();
      assertEquals(0,texture.releases); assertEquals(0,mesh.releases);
      texture.delete(); mesh.delete();
      Texture.clearTextureList(true); Obj.clearObjList(true);
      Texture.deleteTextures(); Obj.deleteObjects();
      Texture.deleteTextures(); Obj.deleteObjects();
      assertEquals(1,texture.releases); assertEquals(1,mesh.releases);
    } finally { Texture.setShouldLog(true); Obj.setShouldLog(true); }
  }
  @Test void shutdownDrainsLivePersistentAndPendingResources() {
    final var liveTexture=new CountedTexture(); final var liveMesh=new CountedObj();
    final var pendingTexture=new CountedTexture(); final var pendingMesh=new CountedObj();
    liveTexture.persistent=liveMesh.persistent=true;
    pendingTexture.delete(); pendingMesh.delete();
    new RenderEngine().delete();
    assertEquals(1,liveTexture.releases); assertEquals(1,liveMesh.releases);
    assertEquals(1,pendingTexture.releases); assertEquals(1,pendingMesh.releases);
    Texture.deleteTextures(); Obj.deleteObjects();
    assertEquals(1,liveTexture.releases); assertEquals(1,liveMesh.releases);
  }
}

