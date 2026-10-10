package legend.definitive.artwork;

import legend.game.textures.Image;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import javax.imageio.ImageIO;

public final class ArtworkResources {
  private ArtworkResources() { }
  public static byte[] read(final Class<?> owner, final String path, final int limit) throws IOException {
    try(final var input = owner.getResourceAsStream(path)) {
      if(input == null) throw new IOException("Missing artwork resource: " + path);
      final byte[] bytes = input.readNBytes(limit + 1);
      if(bytes.length > limit) throw new IOException("Artwork exceeds resource limit");
      return bytes;
    }
  }
  public static Image image(final byte[] png, final int width, final int height) throws IOException {
    if(png.length > 32 * 1024 * 1024 || width < 1 || height < 1 || width > 4096 || height > 4096) throw new IOException("Artwork size exceeds bounds");
    final var header = java.nio.ByteBuffer.wrap(png).order(java.nio.ByteOrder.BIG_ENDIAN);
    if(png.length < 33 || header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452 || header.getInt(16) != width || header.getInt(20) != height || png[24] != 8 || (png[25] != 2 && png[25] != 6)) throw new IOException("Artwork PNG header differs");
    final var image = ImageIO.read(new ByteArrayInputStream(png));
    if(image == null || image.getWidth() != width || image.getHeight() != height) throw new IOException("Invalid artwork PNG");
    final byte[] rgba = new byte[width * height * 4];
    for(int y = 0; y < height; y++) for(int x = 0; x < width; x++) {
      final int pixel = image.getRGB(x, y), i = (y * width + x) * 4;
      rgba[i] = (byte)(pixel >>> 16); rgba[i + 1] = (byte)(pixel >>> 8); rgba[i + 2] = (byte)pixel; rgba[i + 3] = (byte)(pixel >>> 24);
    }
    return new Image(rgba, width, height);
  }
  public static Properties metadata(final Class<?> owner, final String path) throws IOException {
    final var result = new Properties(); result.load(new ByteArrayInputStream(read(owner, path, 65536))); return result;
  }
  public static void hash(final byte[] bytes, final String expected) throws IOException {
    if(!legend.definitive.textures.TexturePilot.sha256(bytes).equals(expected)) throw new IOException("Artwork source or resource hash differs");
  }
}
