package legend.game.textures;

import org.legendofdragoon.modloader.events.Event;
import java.util.Arrays;

/** Source-bound direct-color UI; alpha retains the renderer's native STP convention. */
public final class UiRasterEvent extends Event {
  public final String id;
  private final byte[] source;
  private final Image original;
  private Image replacement;
  public UiRasterEvent(final String id, final byte[] source, final Image original) {
    UiTextureEvent.validateSource(original);
    this.id = id; this.source = source.clone();
    this.original = new Image(original.data.clone(), original.width, original.height);
  }
  public byte[] source() { return this.source.clone(); }
  public Image original() { return new Image(this.original.data.clone(), this.original.width, this.original.height); }
  public boolean replaced() { return this.replacement != null; }
  public Image image() {
    final Image image = this.replaced() ? this.replacement : this.original;
    return new Image(image.data.clone(), image.width, image.height);
  }
  public boolean replace(final Image expected, final Image candidate) {
    if(this.replaced() || expected.width != this.original.width || expected.height != this.original.height || !Arrays.equals(expected.data, this.original.data)) return false;
    NativeUiTextures.validate(this.original, candidate);
    this.replacement = new Image(candidate.data.clone(), candidate.width, candidate.height);
    return true;
  }
}
