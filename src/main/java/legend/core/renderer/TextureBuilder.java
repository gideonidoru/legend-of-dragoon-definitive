package legend.core.renderer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static legend.core.GameEngine.RENDERER;
import static legend.core.IoHelper.pathToByteBuffer;

public class TextureBuilder {
  private final String name;

  @Nullable
  private Buffer buffer;
  private int w;
  private int h;

  private TextureInternalFormat internalFormat = TextureInternalFormat.RGBA_8;
  private TextureDataFormat dataFormat = TextureDataFormat.RGBA;
  private TextureDataType dataType = TextureDataType.UBYTE;

  private boolean minFilter;
  private boolean magFilter;

  private boolean wrapS = true;
  private boolean wrapT = true;

  private final List<Runnable> cleanup = new ArrayList<>();

  public TextureBuilder(final String name) {
    this.name = name;
  }

  public void free() {
    for(final Runnable runnable : this.cleanup) {
      runnable.run();
    }
  }

  public void png(final Path path) {
    final ByteBuffer imageBuffer;
    try {
      imageBuffer = pathToByteBuffer(path);
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }

    this.png(imageBuffer);
  }

  public void png(final ByteBuffer imageBuffer) {
    final legend.definitive.rendering.PngAssets.Image image = (Texture.imageCachingEnabled() ? legend.definitive.rendering.PngAssets.SHARED : legend.definitive.rendering.PngAssets.UNCACHED).acquire(imageBuffer);
    this.cleanup.add(image::close);
    this.data(image.pixels(), image.width(), image.height());
  }

  public void size(final int w, final int h) {
    this.w = w;
    this.h = h;
  }

  public void data(final Buffer data, final int w, final int h) {
    if(data != null && !data.isDirect()) {
      throw new IllegalArgumentException("Texture pixel data requires direct native storage");
    }
    this.buffer = data;
    this.size(w, h);
  }

  public void internalFormat(final TextureInternalFormat format) {
    this.internalFormat = format;
  }

  public void dataFormat(final TextureDataFormat format) {
    this.dataFormat = format;
  }

  public void dataType(final TextureDataType dataType) {
    this.dataType = dataType;
  }

  public void minFilter(final boolean minFilter) {
    this.minFilter = minFilter;
  }

  public void magFilter(final boolean magFilter) {
    this.magFilter = magFilter;
  }

  public void wrapS(final boolean wrapS) {
    this.wrapS = wrapS;
  }

  public void wrapT(final boolean wrapT) {
    this.wrapT = wrapT;
  }

  Texture build() {
    return RENDERER.api().makeTexture(this.buffer, this.name, this.w, this.h, this.internalFormat, this.dataFormat, this.dataType, this.minFilter, this.magFilter, this.wrapS, this.wrapT);
  }
}
