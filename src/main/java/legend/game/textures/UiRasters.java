package legend.game.textures;

import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureSingle;
import legend.core.renderer.Texture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static legend.core.GameEngine.EVENTS;

/** Direct texture consumers keep their logical dimensions and original animation/placement. */
public final class UiRasters {
  private UiRasters() { }
  public static Image decode(final VramTextureSingle texture, final VramTextureSingle palette) {
    final int width = texture.rect.w, height = texture.rect.h;
    final int[] rgba = texture.applyPalette(palette, new Rect4i(0, 0, width, height));
    final byte[] bytes = new byte[rgba.length * 4];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(rgba);
    return new Image(bytes, width, height);
  }
  public static UiRasterEvent select(final String id, final byte[] source, final Image original) {
    final var event = new UiRasterEvent(id, source, original);
    EVENTS.postEvent(event);
    return event;
  }
  public static Texture upload(final String id, final byte[] source, final VramTextureSingle texture, final VramTextureSingle palette) {
    final var event = select(id, source, decode(texture, palette));
    try { return UiTextures.upload(id, event.image()); }
    catch(final RuntimeException failure) {
      if(!event.replaced()) throw failure;
      org.apache.logging.log4j.LogManager.getLogger(UiRasters.class).warn("Retaining original {} after artwork upload failure", id, failure);
      return UiTextures.upload(id, event.original());
    }
  }
}
