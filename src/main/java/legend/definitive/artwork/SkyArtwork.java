package legend.definitive.artwork;

import legend.core.gpu.Bpp;
import legend.core.renderer.Obj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.Texture;
import legend.game.textures.Image;
import legend.game.types.McqHeader;
import java.io.IOException;

/** A scene-owned optional panorama. Create/delete only on the render thread. */
public final class SkyArtwork {
  public final Obj mesh;
  public final Texture texture;
  private SkyArtwork(final Obj mesh, final Texture texture) { this.mesh = mesh; this.texture = texture; }

  public static SkyArtwork create(final McqHeader mcq, final Image image) throws IOException {
    return create(mcq, image, null);
  }

  public static SkyArtwork create(final McqHeader mcq, final Image image, final legend.core.renderer.Translucency translucency) throws IOException {
    final Image covered = SkySource.applyCoverage(mcq.source(), image);
    final Texture texture = Texture.create("EnvHD battle panorama", builder -> {
      final var pixels = org.lwjgl.BufferUtils.createByteBuffer(covered.data.length);
      pixels.put(covered.data).flip();
      builder.data(pixels, covered.width, covered.height); builder.wrapS(false); builder.wrapT(false);
    });
    try {
      final boolean offset = mcq.magic_00 == McqHeader.MAGIC_2;
      final var builder = new QuadBuilder("EnvHD panorama")
        .pos(offset ? mcq.screenOffsetX_28 : 0, offset ? mcq.screenOffsetY_2a : 0, 0)
        .posSize(mcq.screenWidth_14, mcq.screenHeight_16)
        .uv(0, 0).uvSize(1, 1).bpp(Bpp.BITS_24).monochrome(1);
      if(translucency != null) builder.translucency(translucency);
      final Obj mesh = builder.build();
      return new SkyArtwork(mesh, texture);
    } catch(final RuntimeException failure) { texture.delete(); throw failure; }
  }
  public void delete() { this.mesh.delete(); this.texture.delete(); }
}
