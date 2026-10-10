package legend.game.textures;

import org.legendofdragoon.modloader.events.Event;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;

/** Native indexed UI remains authoritative for palette, visibility and blend classification. */
public final class NativeUiTextureEvent extends Event {
  public final String id;
  /** Stable upload slot; animated sources replace only their own previous selection. */
  public final String slot;
  public final int imageX, imageY, clutX, clutY;
  public final int clutRows;
  private final byte[] source;
  public NativeUiTextureEvent(final String id, final Tim tim, final int imageX, final int imageY, final int clutX, final int clutY) {
    this(id, tim, imageX, imageY, clutX, clutY, tim.hasClut() ? Math.max(1, tim.getClutRect().h) : 1);
  }
  public NativeUiTextureEvent(final String id, final Tim tim, final int imageX, final int imageY, final int clutX, final int clutY, final int clutRows) {
    this(id, id, tim, imageX, imageY, clutX, clutY, clutRows);
  }
  public NativeUiTextureEvent(final String id, final String slot, final Tim tim, final int imageX, final int imageY, final int clutX, final int clutY, final int clutRows) {
    if(clutRows < 1 || clutRows > 16) throw new IllegalArgumentException("UI palette bank rows exceed bounds");
    this.id = id;
    this.slot = slot;
    this.source = tim.getData().getBytes().clone();
    this.imageX = imageX; this.imageY = imageY;
    this.clutX = clutX; this.clutY = clutY;
    this.clutRows = clutRows;
  }
  /** Combines the actual uploaded indexed page with its separately loaded palette. */
  public static Tim withPalette(final Tim image, final Tim palette) {
    return withPaletteData(image, palette.getClutData().getBytes());
  }
  public static Tim withPaletteData(final Tim image, final byte[] palette) {
    if(image.getBpp() != legend.core.gpu.Bpp.BITS_4 || palette.length < 32 || palette.length % 32 != 0 || palette.length > 16 * 32) throw new IllegalArgumentException("Invalid UI page/palette");
    final var rect = image.getImageRect();
    final byte[] pixels = image.getImageData().getBytes();
    final var bytes = java.nio.ByteBuffer.allocate(8 + 12 + palette.length + 12 + pixels.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8).putInt(12 + palette.length).putShort((short)0).putShort((short)0).putShort((short)16).putShort((short)(palette.length / 32)).put(palette);
    bytes.putInt(12 + pixels.length).putShort((short)rect.x).putShort((short)rect.y).putShort((short)rect.w).putShort((short)rect.h).put(pixels);
    return new Tim(new FileData(bytes.array()));
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
  public static void uploaded(final String id, final Tim tim) { uploaded(id, id, tim); }
  public static void uploaded(final String id, final String slot, final Tim tim) {
    final var image = tim.getImageRect();
    final var clut = tim.hasClut() ? tim.getClutRect() : new legend.core.gpu.Rect4i(0, 0, 16, 1);
    post(new NativeUiTextureEvent(id, slot, tim, image.x, image.y, clut.x, clut.y, Math.min(16, Math.max(1, clut.h))));
  }
  NativeUiTextureEvent fresh() { return new NativeUiTextureEvent(this.id, this.slot, new Tim(new FileData(this.source)), this.imageX, this.imageY, this.clutX, this.clutY, this.clutRows); }
  int encodedBytes() { return this.source.length; }
  public byte[] source() { return this.source.clone(); }
  public Tim tim() { return new Tim(new FileData(this.source.clone())); }
}
