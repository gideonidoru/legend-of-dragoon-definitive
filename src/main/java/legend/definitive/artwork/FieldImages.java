package legend.definitive.artwork;

import legend.core.gpu.Rect4i;
import legend.definitive.rendering.NativeEnvironmentImages.Segment;
import legend.game.textures.Image;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Independent source-bound field images. Native placement, depth and script slots remain unchanged. */
public final class FieldImages {
  public record Layer(Image image, Rect4i placement) { }
  public record Source(Image background, Layer[] foregrounds) { }
  private FieldImages() { }

  public static Source compose(final Rect4i canvas, final int backgrounds, final Segment[] segments, final int[][] placements) {
    if(canvas.w < 1 || canvas.h < 1 || (long)canvas.w * canvas.h > 2097152 || backgrounds < 0 || backgrounds > segments.length || placements.length != segments.length) throw new IllegalArgumentException("Field canvas bounds");
    final byte[] bg = new byte[canvas.w * canvas.h * 4];
    final Layer[] fg = new Layer[segments.length - backgrounds];
    for(int i = 0; i < segments.length; i++) {
      final Segment tile = segments[i];
      if(tile == null) continue;
      final int x = placements[i][0] - canvas.x, y = placements[i][1] - canvas.y;
      if(x < 0 || y < 0 || tile.width() < 1 || tile.height() < 1 || x + tile.width() > canvas.w || y + tile.height() > canvas.h || tile.rgba().length != tile.width() * tile.height()) throw new IllegalArgumentException("Field tile placement");
      final ByteBuffer rgba = ByteBuffer.allocate(tile.rgba().length * 4).order(ByteOrder.LITTLE_ENDIAN);
      for(final int pixel : tile.rgba()) rgba.putInt(pixel);
      final Image image = new Image(rgba.array(), tile.width(), tile.height());
      if(i < backgrounds) {
        for(int row = 0; row < tile.height(); row++) System.arraycopy(image.data, row * tile.width() * 4, bg, ((y + row) * canvas.w + x) * 4, tile.width() * 4);
      } else fg[i - backgrounds] = new Layer(image, new Rect4i(placements[i][0], placements[i][1], tile.width(), tile.height()));
    }
    return new Source(new Image(bg, canvas.w, canvas.h), fg);
  }

  public static String fingerprint(final Image image) {
    final ByteBuffer pixels = ByteBuffer.allocate(8 + image.data.length).order(ByteOrder.LITTLE_ENDIAN);
    pixels.putInt(image.width).putInt(image.height).put(image.data);
    return legend.definitive.textures.TexturePilot.sha256(pixels.array());
  }

  /** Missing selections fall back normally. A corrupt selection cannot change native coverage. */
  public static Image loadOptional(final Class<?> owner, final Image source) throws IOException {
    final String key = fingerprint(source), base = "/envhd/fields/" + key;
    try(final var exists = owner.getResourceAsStream(base + "/manifest.properties")) { if(exists == null) return null; }
    final var metadata = ArtworkResources.metadata(owner, base + "/manifest.properties");
    if(!metadata.stringPropertyNames().equals(java.util.Set.of("schema", "sourceRgbaSha256", "sourceWidth", "sourceHeight", "scale", "outputSha256")) || !"1".equals(metadata.getProperty("schema")) || !key.equals(metadata.getProperty("sourceRgbaSha256")) || !"4".equals(metadata.getProperty("scale")) || !Integer.toString(source.width).equals(metadata.getProperty("sourceWidth")) || !Integer.toString(source.height).equals(metadata.getProperty("sourceHeight"))) throw new IOException("Field source differs from selected artwork");
    final byte[] png;
    try(final var embedded = owner.getResourceAsStream(base + "/image-v1.png")) {
      if(embedded != null) png = ArtworkResources.read(owner, base + "/image-v1.png", 32 * 1024 * 1024);
      else png = FieldArtworkBundle.read(java.nio.file.Path.of("mods"), key);
    }
    return read(metadata, png, source);
  }
  public static Image read(final java.util.Properties metadata, final byte[] png, final Image source) throws IOException {
    final String key = fingerprint(source);
    if(!key.equals(metadata.getProperty("sourceRgbaSha256")) || !"4".equals(metadata.getProperty("scale")) || !Integer.toString(source.width).equals(metadata.getProperty("sourceWidth")) || !Integer.toString(source.height).equals(metadata.getProperty("sourceHeight"))) throw new IOException("Field source differs");
    ArtworkResources.hash(png, metadata.getProperty("outputSha256"));
    final Image image = ArtworkResources.image(png, source.width * 4, source.height * 4);
    validate(source, image);
    return image;
  }

  public static void validate(final Image source, final Image image) throws IOException {
    if(image.width != source.width * 4 || image.height != source.height * 4) throw new IOException("Field scale differs");
    for(int y = 0; y < image.height; y++) for(int x = 0; x < image.width; x++) {
      final int p = (y * image.width + x) * 4, q = (y / 4 * source.width + x / 4) * 4;
      final boolean a = (source.data[q] | source.data[q + 1] | source.data[q + 2]) == 0;
      final boolean b = (image.data[p] | image.data[p + 1] | image.data[p + 2]) == 0;
      if(source.data[q + 3] != image.data[p + 3] || a != b) throw new IOException("Field visibility or original black changed");
    }
  }
}
