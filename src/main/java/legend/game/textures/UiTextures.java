package legend.game.textures;

import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import org.lwjgl.BufferUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static legend.core.GameEngine.EVENTS;

/** Optional UI images retain their source coordinate system without writing to the installed gfx files. */
public final class UiTextures {
  private UiTextures() { }
  public record Loaded(Texture texture, int width, int height) { }

  public static Loaded load(final String name, final Path path) {
    try {
      if(Files.size(path) > 32 * 1024 * 1024) throw new IOException("UI source exceeds bounds");
      final byte[] source = Files.readAllBytes(path);
      if(source.length < 33) throw new IOException("Truncated UI PNG");
      final Image original = decode(source);
      final UiTextureEvent event = new UiTextureEvent(path, source, original);
      EVENTS.postEvent(event);
      return new Loaded(upload(name, event.image()), original.width, original.height);
    } catch(final IOException failure) {
      throw new IllegalStateException("Could not load UI artwork " + path, failure);
    }
  }

  public static Image decode(final byte[] png) throws IOException {
    if(png.length < 33 || png.length > 32 * 1024 * 1024) throw new IOException("UI source bounds differ");
    final ByteBuffer header = ByteBuffer.wrap(png);
    final int width = header.getInt(16), height = header.getInt(20);
    if(header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452 || width < 1 || height < 1 || width > 4096 || height > 4096
      || (long)width * height > 4 * 1024 * 1024) throw new IOException("UI source dimensions exceed bounds");
    final byte[] rgba = new byte[width * height * 4];
    final var cache = Texture.imageCachingEnabled() ? legend.definitive.rendering.PngAssets.SHARED : legend.definitive.rendering.PngAssets.UNCACHED;
    try(final var decoded = cache.acquire(header)) {
      if(decoded.width() != width || decoded.height() != height) throw new IOException("Invalid UI source");
      decoded.pixels().get(0, rgba);
    } catch(final IllegalArgumentException failure) {
      throw new IOException("Invalid UI source", failure);
    }
    return new Image(rgba, width, height);
  }

  public static Texture upload(final String name, final Image image) {
    final ByteBuffer pixels = BufferUtils.createByteBuffer(image.data.length);
    pixels.put(0, image.data);
    return Texture.create(name, builder -> {
      builder.internalFormat(TextureInternalFormat.RGBA_8);
      builder.dataFormat(TextureDataFormat.RGBA);
      builder.dataType(TextureDataType.UBYTE);
      builder.wrapS(false);
      builder.wrapT(false);
      builder.data(pixels, image.width, image.height);
    });
  }
}
