package legend.game.textures;

import legend.core.gpu.Bpp;
import legend.core.renderer.QueuedModelStandard;
import legend.core.renderer.Texture;
import legend.game.tim.Tim;
import java.util.ArrayList;
import java.util.List;

/** Small optional UI regions; live indexed VRAM continues to classify every fragment. */
public final class NativeUiTextures {
  public static final long BUDGET = 32L * 1024 * 1024;
  private static final List<Region> REGIONS = new ArrayList<>();
  private static final java.util.Map<Integer,List<Region>> PALETTES = new java.util.HashMap<>();
  private static int paletteKey(final Binding binding) { return binding.clutX | binding.clutY << 16; }
  public static synchronized int selectionCount() { return REGIONS.size(); }
  public static synchronized int residentCount() { return (int)REGIONS.stream().filter(r -> r.artworkTexture != null).count(); }
  private static final List<Texture> RETIRED = new ArrayList<>();
  private static final java.util.Map<Texture,Region> RESIDENT = new java.util.IdentityHashMap<>();
  private static long allocated;
  private static long frame;
  public record Images(Image original, Image enhanced) { }
  public static synchronized void beginFrame() { retireCleared(); frame++; }
  private static void retireCleared() {
    // Only called with a renderer context, before new uploads. Mod changes can
    // enqueue retirement from another thread without touching GPU state there.
    for(final Texture texture : RETIRED) texture.deleteOwnedCacheEntry();
    RETIRED.clear();
  }
  private NativeUiTextures() { }

  public record Binding(int x, int y, int clutX, int clutY, int width, int height) {
    public boolean contains(final int pageX, final int pageY, final int paletteX, final int paletteY, final float u, final float v, final float w, final float h) {
      final float px = pageX * 4.0f + u, py = pageY + v;
      return paletteX == this.clutX && paletteY == this.clutY && w > 0 && h > 0
        && px < this.x + this.width && py < this.y + this.height && px + w > this.x && py + h > this.y;
    }
  }
  private static final class Region {
    final Binding binding;
    final Image original, enhanced;
    final java.util.function.Supplier<Images> provider;
    final long bytes;
    Texture sourceTexture, artworkTexture;
    boolean uploadFailed;
    long lastUsed = -1;
    Region(final Binding binding, final Image original, final Image enhanced) {
      this.binding = binding; this.original = original; this.enhanced = enhanced; this.provider = null;
      this.bytes = (long)original.data.length + enhanced.data.length;
    }
    Region(final Binding binding, final long bytes, final java.util.function.Supplier<Images> provider) {
      this.binding = binding; this.original = this.enhanced = null; this.bytes = bytes; this.provider = provider;
    }
  }

  /** All default palettes can be selected without retaining every decoded page or GPU texture. */
  public static synchronized boolean registerDeferred(final Binding binding, final long bytes, final java.util.function.Supplier<Images> provider) {
    if(binding.width < 1 || binding.height < 1 || binding.width > 512 || binding.height > 512 || bytes < 1 || bytes > BUDGET || REGIONS.size() >= 384) return false;
    for(final Region region : REGIONS) if(region.binding.equals(binding)) return false;
    final Region region = new Region(binding, bytes, java.util.Objects.requireNonNull(provider));
    REGIONS.add(region);
    PALETTES.computeIfAbsent(paletteKey(binding), key -> new ArrayList<>()).add(region);
    return true;
  }

  /** Pooled/paused draws must not retain a selection that was cleared or evicted. */
  public static synchronized boolean touch(final Texture artwork) {
    final Region region = RESIDENT.get(artwork);
    if(region == null) return false;
    region.lastUsed = frame;
    return true;
  }

  public static Image decode(final Tim tim, final int palette, final int x, final int y, final int width, final int height) {
    if(tim.getBpp() != Bpp.BITS_4 || !tim.hasClut()) throw new IllegalArgumentException("Native UI requires 4-bit indexed artwork");
    final var rect = tim.getImageRect();
    final var colours = tim.getClutData();
    final var pixels = tim.getImageData();
    if(x < 0 || y < 0 || width < 1 || height < 1 || width > 512 || height > 512
      || x + width > rect.w * 4 || y + height > rect.h || palette < 0 || (palette + 1) * 32 > colours.size()) {
      throw new IllegalArgumentException("Native UI crop or palette exceeds source");
    }
    final byte[] rgba = new byte[width * height * 4];
    for(int row = 0; row < height; row++) for(int col = 0; col < width; col++) {
      final int sx = x + col, sy = y + row;
      final int index = pixels.readUByte(sy * rect.w * 2 + sx / 2) >>> ((sx & 1) * 4) & 15;
      final int colour = colours.readUShort(palette * 32 + index * 2), out = (row * width + col) * 4;
      rgba[out] = (byte)((colour & 31) * 255 / 31);
      rgba[out + 1] = (byte)((colour >>> 5 & 31) * 255 / 31);
      rgba[out + 2] = (byte)((colour >>> 10 & 31) * 255 / 31);
      rgba[out + 3] = (byte)((colour & 0x8000) == 0 ? 0 : 255);
    }
    return new Image(rgba, width, height);
  }

  public static void validate(final Image original, final Image enhanced) {
    UiTextureEvent.validateSource(original);
    UiTextureEvent.validateSource(enhanced);
    final int scale = enhanced.width / original.width;
    if(scale < 2 || scale > 4 || enhanced.width != original.width * scale || enhanced.height != original.height * scale
      || enhanced.data.length != (long)enhanced.width * enhanced.height * 4) throw new IllegalArgumentException("Native UI scale differs");
    for(int y = 0; y < enhanced.height; y++) for(int x = 0; x < enhanced.width; x++) {
      final int a = ((y / scale) * original.width + x / scale) * 4, b = (y * enhanced.width + x) * 4;
      final boolean originalBlack = original.data[a] == 0 && original.data[a + 1] == 0 && original.data[a + 2] == 0;
      final boolean targetBlack = enhanced.data[b] == 0 && enhanced.data[b + 1] == 0 && enhanced.data[b + 2] == 0;
      if(original.data[a + 3] != enhanced.data[b + 3] || originalBlack != targetBlack) throw new IllegalArgumentException("Native UI visibility or STP changed");
    }
  }

  /** Earlier owners win. Validating everything precedes any change to the installed selection. */
  public static boolean register(final Binding binding, final Image original, final Image enhanced) {
    validate(original, enhanced);
    if(binding.width != original.width || binding.height != original.height) throw new IllegalArgumentException("UI binding differs from source crop");
    final Region region;
    synchronized(NativeUiTextures.class) {
      for(final Region current : REGIONS) if(current.binding.equals(binding)) return false;
      final long bytes = (long)original.data.length + enhanced.data.length;
      if(bytes > BUDGET - allocated) return false;
      region = new Region(binding, new Image(original.data.clone(), original.width, original.height), new Image(enhanced.data.clone(), enhanced.width, enhanced.height));
      REGIONS.add(region);
      PALETTES.computeIfAbsent(paletteKey(binding), key -> new ArrayList<>()).add(region);
      allocated += bytes;
    }
    // Never acquire the renderer task monitor while holding the region lock:
    // the renderer executes preparation tasks while holding that monitor.
    // Removed regions cannot resurrect textures after a mod reboot.
    legend.core.GameEngine.RENDERER.addTask(() -> prepare(region));
    return true;
  }

  /** Called only while queuing on the renderer thread. Upcoming regions prepare at the start of the frame; first use also handles late arrivals. */
  public static synchronized void apply(final QueuedModelStandard model, final int pageX, final int pageY, final int clutX, final int clutY, final float u, final float v, final float width, final float height) {
    final List<Region> candidates = PALETTES.get(clutX | clutY << 16);
    if(candidates == null) return;
    for(final Region region : candidates) {
      if(!region.binding.contains(pageX, pageY, clutX, clutY, u, v, width, height)) continue;
      prepare(region);
      region.lastUsed = frame;
      if(region.artworkTexture == null) return;
      model.nativeUiArtwork(region.artworkTexture, region.sourceTexture, region.binding.x - pageX * 4, region.binding.y - pageY, region.binding.width, region.binding.height);
      return;
    }
  }

  private static synchronized void prepare(final Region region) {
    retireCleared();
    if(!REGIONS.contains(region) || region.artworkTexture != null || region.uploadFailed) return;
    if(region.provider != null) {
      while(allocated + region.bytes > BUDGET) {
        final Region oldest = REGIONS.stream().filter(r -> r.provider != null && r.artworkTexture != null && r.lastUsed != frame)
          .min(java.util.Comparator.comparingLong(r -> r.lastUsed)).orElse(null);
        if(oldest == null) return; // Keep every selection already used by this frame alive.
        RESIDENT.remove(oldest.artworkTexture);
        oldest.sourceTexture.deleteOwnedCacheEntry(); oldest.artworkTexture.deleteOwnedCacheEntry();
        oldest.sourceTexture = oldest.artworkTexture = null; allocated -= oldest.bytes;
      }
    }
    Texture original = null;
    try {
      final Images images = region.provider == null ? new Images(region.original, region.enhanced) : region.provider.get();
      validate(images.original, images.enhanced);
      if(images.original.width != region.binding.width || images.original.height != region.binding.height
        || (long)images.original.data.length + images.enhanced.data.length != region.bytes) throw new IllegalArgumentException("Deferred UI payload differs");
      original = UiTextures.upload("UI source reference", images.original);
      final Texture enhanced = UiTextures.upload("UI restored region", images.enhanced);
      original.persistent = enhanced.persistent = true;
      region.sourceTexture = original;
      region.artworkTexture = enhanced;
      RESIDENT.put(enhanced, region);
      if(region.provider != null) allocated += region.bytes;
    } catch(final RuntimeException failure) {
      if(original != null) original.deleteOwnedCacheEntry();
      region.uploadFailed = true;
      org.apache.logging.log4j.LogManager.getLogger(NativeUiTextures.class).warn("UI region upload failed; retaining original interface", failure);
    }
  }

  /** Rebind private sources against the current event bus after any mod/registry reboot. */
  public static void reselect(final java.util.function.Consumer<NativeUiTextureEvent> listener) {
    clear();
    NativeUiSources.CURRENT.replay(listener);
  }

  public static synchronized long allocatedBytes() { return allocated; }
  /** Mod reboots happen outside queued rendering; native fallback remains available throughout. */
  public static synchronized void clear() {
    for(final Region region : REGIONS) {
      if(region.sourceTexture != null) { region.sourceTexture.delete(); RETIRED.add(region.sourceTexture); }
      if(region.artworkTexture != null) { region.artworkTexture.delete(); RETIRED.add(region.artworkTexture); }
    }
    REGIONS.clear();
    PALETTES.clear();
    RESIDENT.clear();
    allocated = 0;
  }
}
