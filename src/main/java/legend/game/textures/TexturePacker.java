package legend.game.textures;

import legend.core.gpu.Bpp;
import legend.core.gpu.Rect4i;
import legend.core.renderer.Obj;
import legend.core.renderer.QuadBuilder;
import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import org.legendofdragoon.modloader.registries.RegistryId;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBRPContext;
import org.lwjgl.stb.STBRPNode;
import org.lwjgl.stb.STBRPRect;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.stb.STBRectPack.stbrp_init_target;
import static org.lwjgl.stb.STBRectPack.stbrp_pack_rects;

public class TexturePacker {
  public final String name;

  private final Map<RegistryId, Rect4i> entryToRect = new HashMap<>();
  private final Map<RegistryId, Image> entryToImage = new HashMap<>();

  public TexturePacker(final String name) {
    this.name = name;
  }

  public void add(final RegistryId id, final Image image) {
    UiTextureEvent.validateSource(image);
    final Rect4i rect = new Rect4i();
    rect.w = image.width;
    rect.h = image.height;
    this.entryToRect.put(id, rect);
    this.entryToImage.put(id, image);
  }

  public Rect4i getRect(final RegistryId id) {
    return this.entryToRect.get(id);
  }

  /** Shares the live atlas selection with CPU-only save-card production. */
  public boolean applyReplacements() {
    return this.applyReplacements(legend.core.GameEngine.EVENTS::postEvent);
  }
  public boolean applyReplacements(final java.util.function.Consumer<ReplaceAtlasTexturesEvent> selection) {
    final Map<RegistryId, Image> images = new HashMap<>();
    this.entryToImage.forEach((id, image) -> images.put(id, new Image(image.data.clone(), image.width, image.height)));
    selection.accept(new ReplaceAtlasTexturesEvent(images));
    // Validate the complete selection before changing authoritative originals.
    if(!images.keySet().equals(this.entryToImage.keySet())) throw new IllegalArgumentException("Portrait selection changed identities");
    images.values().forEach(UiTextureEvent::validateSource);
    boolean changed = false;
    for(final var entry : images.entrySet()) {
      final Image before = this.entryToImage.get(entry.getKey());
      final Image after = entry.getValue();
      if(before.width != after.width || before.height != after.height || !java.util.Arrays.equals(before.data, after.data)) {
        this.add(entry.getKey(), after);
        changed = true;
      }
    }
    return changed;
  }

  public record Packed(byte[] data, int width, int height) { }
  /** Saving is independent of optional artwork selection failures and atlas capacity. */
  public Packed packWithReplacements(final int width, final int height, final int maximum) {
    return this.packWithReplacements(width, height, maximum, legend.core.GameEngine.EVENTS::postEvent);
  }
  public Packed packWithReplacements(final int width, final int height, final int maximum, final java.util.function.Consumer<ReplaceAtlasTexturesEvent> selection) {
    final var originals = new HashMap<>(this.entryToImage);
    try {
      this.applyReplacements(selection);
      return this.packGrowingToBytes(width, height, maximum);
    } catch(final RuntimeException optionalFailure) {
      org.apache.logging.log4j.LogManager.getLogger(TexturePacker.class).warn("Retaining original portraits for {}", this.name, optionalFailure);
      this.entryToImage.clear(); this.entryToRect.clear();
      originals.forEach(this::add);
      return this.packGrowingToBytes(width, height, maximum);
    }
  }
  public Packed packGrowingToBytes(int width, int height, final int maximum) {
    if(width < 1 || height < 1 || maximum > 2048 || maximum < width || maximum < height) throw new IllegalArgumentException("Atlas dimensions exceed budget");
    while(true) {
      try { return new Packed(this.packToBytes(width, height), width, height); }
      catch(final AtlasFull full) {
        if(width == maximum && height == maximum) throw new AtlasCapacityException();
        if(width <= height && width < maximum) width = Math.min(maximum, width * 2);
        else height = Math.min(maximum, height * 2);
      }
    }
  }

  private static final class AtlasFull extends RuntimeException { }
  public static final class AtlasCapacityException extends IllegalStateException {
    public AtlasCapacityException() { super("UI atlas exceeds bounded size"); }
  }

  public TextureAtlas packGrowing(int width, int height, final int maximum) {
    if(width < 1 || height < 1 || maximum > 2048 || maximum < width || maximum < height) throw new IllegalArgumentException("Atlas dimensions exceed budget");
    while(true) {
      try { return this.pack(width, height); }
      catch(final AtlasFull full) {
        if(width == maximum && height == maximum) throw new AtlasCapacityException();
        if(width <= height && width < maximum) width = Math.min(maximum, width * 2);
        else height = Math.min(maximum, height * 2);
      }
    }
  }

  public byte[] packToBytes(final int width, final int height) {
    if(width < 1 || height < 1 || width > 2048 || height > 2048) throw new IllegalArgumentException("Atlas dimensions exceed budget");
    final STBRPContext ctx = STBRPContext.malloc();
    final STBRPNode.Buffer nodes = STBRPNode.malloc(width); // documentation says that nodes should be >= width
    final STBRPRect.Buffer rectBuffer = STBRPRect.malloc(this.entryToRect.size());

    try {
      int i = 0;
      for(final Rect4i icon : this.entryToRect.values()) {
        final STBRPRect rect = rectBuffer.get(i++);
        rect.x(icon.x);
        rect.y(icon.y);
        rect.w(icon.w);
        rect.h(icon.h);
      }

      stbrp_init_target(ctx, width, height, nodes);

      if(stbrp_pack_rects(ctx, rectBuffer) == 0) {
        throw new AtlasFull();
      }

      i = 0;
      for(final Rect4i icon : this.entryToRect.values()) {
        final STBRPRect rect = rectBuffer.get(i++);
        icon.x = rect.x();
        icon.y = rect.y();
        icon.w = rect.w();
        icon.h = rect.h();
      }

    } finally {
      rectBuffer.free();
      nodes.free();
      ctx.free();
    }

    return this.buildTexture(width, height);
  }

  public TextureAtlas pack(final int width, final int height) {
    final byte[] packedData = this.packToBytes(width, height);
    final ByteBuffer buffer = BufferUtils.createByteBuffer(packedData.length);
    buffer.put(0, packedData);

    final Texture texture = Texture.create("Atlas " + this.name, builder -> {
      builder.internalFormat(TextureInternalFormat.RGBA_8);
      builder.dataFormat(TextureDataFormat.RGBA);
      builder.dataType(TextureDataType.UBYTE);
      builder.data(buffer, width, height);
    });

    final Map<RegistryId, TextureAtlasIcon> icons = new HashMap<>();
    final QuadBuilder builder = new QuadBuilder("Atlas " + this.name);

    for(final var entry : this.entryToRect.entrySet()) {
      final Rect4i rect = entry.getValue();

      builder.add();
      builder.bpp(Bpp.BITS_24);
      builder.posSize(1.0f, 1.0f);
      builder.uv(rect.x / (float)width, rect.y / (float)height);
      builder.uvSize(rect.w / (float)width, rect.h / (float)height);
    }

    final Obj obj = builder.build();
    final TextureAtlas atlas = new TextureAtlas(texture, obj, icons);

    int i = 0;
    for(final var entry : this.entryToRect.entrySet()) {
      final RegistryId id = entry.getKey();
      final Rect4i rect = entry.getValue();

      icons.put(id, new TextureAtlasIcon(atlas, rect, i * 4));
      i++;
    }

    return atlas;
  }

  private byte[] buildTexture(final int width, final int height) {
    final byte[] out = new byte[width * height * 4];

    for(final RegistryId entry : this.entryToRect.keySet()) {
      this.insertImage(out, width, entry);
    }

    return out;
  }

  private void insertImage(final byte[] data, final int stride, final RegistryId entry) {
    final Rect4i icon = this.entryToRect.get(entry);
    final Image image = this.entryToImage.get(entry);

    for(int y = 0; y < icon.h; y++) {
      System.arraycopy(image.data, y * icon.w * 4, data, ((icon.y + y) * stride + icon.x) * 4, icon.w * 4);
    }
  }
}
