package legend.definitive.rendering;

import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import legend.core.renderer.noop.NoopApi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HdTextureContractTest {
  private Texture texture(final TextureInternalFormat format) {
    return new NoopApi().makeTexture(null, "contract", 64, 64, format, TextureDataFormat.RGBA, TextureDataType.UBYTE, false, false, false, false);
  }

  @Test void originalPalettesAndDepthCannotAccidentallyAcquireColorFiltering() {
    for(final TextureInternalFormat format : new TextureInternalFormat[] {TextureInternalFormat.R_32_UINT, TextureInternalFormat.DEPTH_COMPONENT, TextureInternalFormat.R_8}) {
      final Texture texture = this.texture(format);
      assertFalse(texture.isHdFiltered());
      assertThrows(IllegalArgumentException.class, texture::hdFiltering);
      assertFalse(texture.isHdFiltered());
      texture.delete();
    }
  }

  @Test void unpaddedAtlasesStayExactAndValidColorProfilesAreExplicit() {
    final Texture texture = this.texture(TextureInternalFormat.RGBA_8);
    assertFalse(texture.isHdFiltered());
    assertThrows(IllegalArgumentException.class, () -> texture.hdAtlasFiltering(3));
    assertFalse(texture.isHdFiltered());
    assertSame(texture, texture.hdAtlasFiltering(8));
    assertTrue(texture.isHdFiltered());
    assertThrows(IllegalArgumentException.class, () -> texture.hdFiltering(-1));
    texture.delete();
  }
}
