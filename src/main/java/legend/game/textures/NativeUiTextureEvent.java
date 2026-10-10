package legend.game.textures;

import org.legendofdragoon.modloader.events.Event;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;

/** Native indexed UI remains authoritative for palette, visibility and blend classification. */
public final class NativeUiTextureEvent extends Event {
  public final String id;
  public final int imageX, imageY, clutX, clutY;
  private final byte[] source;
  public NativeUiTextureEvent(final String id, final Tim tim, final int imageX, final int imageY, final int clutX, final int clutY) {
    this.id = id;
    this.source = tim.getData().getBytes().clone();
    this.imageX = imageX; this.imageY = imageY;
    this.clutX = clutX; this.clutY = clutY;
  }
  /** Matches Menus.loadMenuTexture's framebuffer relocation without altering the TIM. */
  public static void menu(final String id, final FileData data) {
    final Tim tim = new Tim(data);
    final var image = tim.getImageRect();
    final var clut = tim.getClutRect();
    post(new NativeUiTextureEvent(id, tim, image.x - 512, image.y, clut.x - 512, clut.y));
  }
  public static void post(final NativeUiTextureEvent event) {
    NativeUiSources.CURRENT.remember(event);
    legend.core.GameEngine.EVENTS.postEvent(event);
  }
  NativeUiTextureEvent fresh() { return new NativeUiTextureEvent(this.id, new Tim(new FileData(this.source)), this.imageX, this.imageY, this.clutX, this.clutY); }
  int encodedBytes() { return this.source.length; }
  public byte[] source() { return this.source.clone(); }
  public Tim tim() { return new Tim(new FileData(this.source.clone())); }
}
