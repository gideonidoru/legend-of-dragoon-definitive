package legend.game.textures;

import legend.core.GameEngine;
import legend.core.renderer.NativeUiQuad;
import legend.core.renderer.Texture;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact native glyph crops, with local indexed references and exclusively owned GPU resources. */
public final class NativeSpriteFont {
  private static final int MAX_GLYPHS = 2048;
  private static final long MAX_BYTES = 16L * 1024 * 1024;
  private static final Map<NativeUiQuad, Region> REGIONS = new LinkedHashMap<>();
  private static long bytes;
  private static final java.util.concurrent.atomic.AtomicLong GPU_BYTES = new java.util.concurrent.atomic.AtomicLong();
  private static long frame;
  private NativeSpriteFont() { }

  private static final class Region {
    final Object owner;
    final String slot;
    final NativeUiQuad source;
    final Image indices;
    final java.util.function.Supplier<Image> artwork;
    final int artworkWidth, artworkHeight;
    final long retainedBytes, gpuBytes;
    Texture referenceTexture, artworkTexture;
    boolean failed;
    long lastUsed = -1;
    Region(final Object owner, final String slot, final NativeUiQuad source, final Image indices, final int artworkWidth, final int artworkHeight, final long retainedBytes, final java.util.function.Supplier<Image> artwork) {
      this.owner = owner; this.slot = slot; this.source = source;
      this.indices = new Image(indices.data.clone(), indices.width, indices.height);
      this.artwork = artwork; this.artworkWidth = artworkWidth; this.artworkHeight = artworkHeight;
      this.retainedBytes = retainedBytes; this.gpuBytes = (long)indices.data.length + (long)artworkWidth * artworkHeight * 4;
    }
  }

  /** A draw keeps this token only while its exact source remains selected. */
  public static final class Selection {
    private final Region region;
    private final Texture artwork, reference;
    private Selection(final Region region) { this.region = region; this.artwork = region.artworkTexture; this.reference = region.referenceTexture; }
    public Texture artwork() { return this.artwork; }
    public Texture reference() { return this.reference; }
    public NativeUiQuad source() { return this.region.source; }
    public boolean current() { synchronized(NativeSpriteFont.class) {
      if(REGIONS.get(this.region.source) != this.region || this.region.artworkTexture != this.artwork) return false;
      this.region.lastUsed = frame; return true;
    } }
  }

  private static void validate(final Image image, final boolean reference) {
    final int max = reference ? 512 : 2048;
    if(image.width < 1 || image.height < 1 || image.width > max || image.height > max || image.data.length != (long)image.width * image.height * 4) throw new IllegalArgumentException("Sprite font image exceeds bounds");
    boolean visible = false;
    for(int i = 0; i < image.data.length; i += 4) {
      if((image.data[i] & 255) > 15 || image.data[i + 1] != 0 || image.data[i + 2] != 0 || reference && (image.data[i + 3] & 255) != 255) throw new IllegalArgumentException("Sprite font palette-index encoding differs");
      visible |= image.data[i + 3] != 0;
    }
    if(!visible) throw new IllegalArgumentException("Sprite font image is blank");
  }

  /** Register only after source/resource hash validation. Duplicate crops never steal another owner's selection. */
  public static synchronized boolean register(final Object owner, final String slot, final NativeUiQuad source, final Image indices, final Image artwork) {
    validate(artwork, false);
    final Image immutable = new Image(artwork.data.clone(), artwork.width, artwork.height);
    return register(owner, slot, source, indices, artwork.width, artwork.height, (long)indices.data.length + artwork.data.length, () -> immutable);
  }

  /** Packaged glyph pixels decode only when drawn, rather than at mod boot. */
  public static synchronized boolean registerDeferred(final Object owner, final String slot, final NativeUiQuad source, final Image indices, final int width, final int height, final java.util.function.Supplier<Image> provider) {
    return register(owner, slot, source, indices, width, height, indices.data.length, Objects.requireNonNull(provider));
  }

  private static boolean register(final Object owner, final String slot, final NativeUiQuad source, final Image indices, final int width, final int height, final long retained, final java.util.function.Supplier<Image> provider) {
    Objects.requireNonNull(owner); Objects.requireNonNull(slot); Objects.requireNonNull(source);
    validate(indices, true);
    if(source.pageX() < 0 || source.pageX() > 960 || source.pageY() < 0 || source.pageY() > 256 || source.clutX() < 0 || source.clutX() > 1008 || source.clutY() < 0 || source.clutY() > 511
      || !Float.isFinite(source.u()) || !Float.isFinite(source.v()) || source.u() != Math.floor(source.u()) || source.v() != Math.floor(source.v())
      || source.u() < 0 || source.v() < 0 || source.width() != indices.width || source.height() != indices.height
      || source.pageX() * 4.0f + source.u() + source.width() > 4096 || source.pageY() + source.v() + source.height() > 512 || width < 1 || height < 1 || width > 2048 || height > 2048
      || width % indices.width != 0 || height % indices.height != 0 || width / indices.width != height / indices.height || width / indices.width < 2 || width / indices.width > 16) throw new IllegalArgumentException("Sprite font source geometry differs");
    if(REGIONS.containsKey(source) || REGIONS.size() >= MAX_GLYPHS || bytes + retained > MAX_BYTES || (long)indices.data.length + (long)width * height * 4 > MAX_BYTES) return false;
    REGIONS.put(source, new Region(owner, slot, source, indices, width, height, retained, provider)); bytes += retained; return true;
  }

  /** Renderer-thread selection happens before shader/pass classification. */
  public static synchronized Selection select(final NativeUiQuad source) {
    final Region region = REGIONS.get(source);
    if(region == null || region.failed) return null;
    region.lastUsed = frame;
    if(region.artworkTexture == null) {
      if(GPU_BYTES.get() + region.gpuBytes > MAX_BYTES) {
        final Region oldest = REGIONS.values().stream().filter(candidate -> candidate.artworkTexture != null && candidate.lastUsed != frame).min(java.util.Comparator.comparingLong(candidate -> candidate.lastUsed)).orElse(null);
        if(oldest != null) retireTextures(oldest);
        return null; // Pending retirement still owns its budget until every retained batch is reset.
      }
      Texture reference = null;
      try {
        final Image decoded = region.artwork.get(); validate(decoded, false);
        if(decoded.width != region.artworkWidth || decoded.height != region.artworkHeight) throw new IllegalArgumentException("Deferred sprite dimensions differ");
        reference = UiTextures.upload("Native glyph indexed reference", region.indices);
        final java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(decoded.data.length);
        pixels.put(0, decoded.data);
        final Texture artwork = Texture.create("Native glyph coverage", builder -> {
          builder.internalFormat(legend.core.renderer.TextureInternalFormat.RGBA_8);
          builder.dataFormat(legend.core.renderer.TextureDataFormat.RGBA);
          builder.dataType(legend.core.renderer.TextureDataType.UBYTE);
          builder.minFilter(true); builder.magFilter(true); builder.wrapS(false); builder.wrapT(false);
          builder.data(pixels, decoded.width, decoded.height);
        });
        reference.persistent = artwork.persistent = true;
        region.referenceTexture = reference; region.artworkTexture = artwork;
        GPU_BYTES.addAndGet(region.gpuBytes);
      } catch(final RuntimeException failure) {
        if(reference != null) reference.deleteOwnedCacheEntry();
        region.failed = true;
        org.apache.logging.log4j.LogManager.getLogger(NativeSpriteFont.class).warn("Sprite font upload failed; retaining native glyph", failure);
        return null;
      }
    }
    return new Selection(region);
  }

  private static void retire(final Region region) {
    bytes -= region.retainedBytes;
    retireTextures(region);
  }
  private static void retireTextures(final Region region) {
    if(region.artworkTexture != null) {
      final Texture artwork = region.artworkTexture, reference = region.referenceTexture;
      region.artworkTexture = region.referenceTexture = null;
      GameEngine.RENDERER.retireAfterBatchReset(() -> {
        artwork.deleteOwnedCacheEntry(); reference.deleteOwnedCacheEntry();
        // Renderer holds its retirement lock here; never acquire the registry monitor.
        GPU_BYTES.addAndGet(-region.gpuBytes);
      });
    }
  }
  public static synchronized void beginSelection(final Object owner, final String slot) {
    REGIONS.values().removeIf(region -> { if(region.owner != owner || !region.slot.equals(slot)) return false; retire(region); return true; });
  }
  public static synchronized void clear() { REGIONS.values().forEach(NativeSpriteFont::retire); REGIONS.clear(); }
  public static synchronized int selectionCount() { return REGIONS.size(); }
  public static synchronized long retainedBytes() { return bytes; }
  /** Includes resources awaiting safe renderer retirement. */
  public static long allocatedBytes() { return GPU_BYTES.get(); }
  public static synchronized void beginFrame() { frame++; }
}
