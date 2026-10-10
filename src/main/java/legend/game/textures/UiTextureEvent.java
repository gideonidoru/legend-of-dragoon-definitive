package legend.game.textures;

import org.legendofdragoon.modloader.events.Event;
import java.nio.file.Path;
import java.util.Arrays;

/** Source-bound optional UI artwork. Logical coordinates always belong to the original image. */
public final class UiTextureEvent extends Event {
  public final Path path;
  private final byte[] source;
  private final Image original;
  private Image replacement;

  public UiTextureEvent(final Path path, final byte[] source, final Image original) {
    validateSource(original);
    this.path = path.normalize();
    this.source = source.clone();
    this.original = new Image(original.data.clone(), original.width, original.height);
  }

  public byte[] source() { return this.source.clone(); }
  public Image original() { return new Image(this.original.data.clone(), this.original.width, this.original.height); }
  public Image image() { final Image image = this.replacement == null ? this.original : this.replacement; return new Image(image.data.clone(), image.width, image.height); }

  public boolean replace(final Image expected, final Image candidate) {
    if(this.replacement != null || expected.width != this.original.width || expected.height != this.original.height
      || !Arrays.equals(expected.data, this.original.data)) return false;
    validate(this.original, candidate);
    this.replacement = new Image(candidate.data.clone(), candidate.width, candidate.height);
    return true;
  }

  static void validateSource(final Image image) {
    if(image.width < 1 || image.height < 1 || image.width > 4096 || image.height > 4096
      || image.data.length != (long)image.width * image.height * 4) throw new IllegalArgumentException("UI image dimensions or pixels exceed bounds");
  }

  public static void validate(final Image source, final Image target) {
    validateSource(source);
    validateSource(target);
    final int scale = target.width / source.width;
    if(scale < 2 || scale > 4 || target.width != source.width * scale || target.height != source.height * scale
      || target.width > 4096 || target.height > 4096 || target.data.length != (long)target.width * target.height * 4) {
      throw new IllegalArgumentException("UI artwork dimensions differ from source");
    }
    for(int y = 0; y < target.height; y++) for(int x = 0; x < target.width; x++) {
      final int a = ((y / scale) * source.width + x / scale) * 4, b = (y * target.width + x) * 4;
      if(source.data[a + 3] != target.data[b + 3]) throw new IllegalArgumentException("UI alpha coverage changed");
      if(source.data[a + 3] == 0 && (target.data[b] != 0 || target.data[b + 1] != 0 || target.data[b + 2] != 0)) {
        throw new IllegalArgumentException("UI transparent texels contain colour");
      }
    }
  }
}
