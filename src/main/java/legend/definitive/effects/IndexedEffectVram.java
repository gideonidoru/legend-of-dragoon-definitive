package legend.definitive.effects;

import legend.core.gpu.Rect4i;
import legend.game.textures.Image;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** CPU-only companion to native VRAM. All methods execute under the owning GPU's VRAM lock. */
public final class IndexedEffectVram {
  public static final int WIDTH = 4096, HEIGHT = 1024;
  public static final long DETAIL_BYTES = (long)WIDTH * HEIGHT * Integer.BYTES;
  private final int[] owners = new int[1024 * 512], offsets = new int[1024 * 512];
  private final Map<Integer, Source> sources = new HashMap<>();
  private final BitSet dirtyRows = new BitSet(HEIGHT);
  private int[] detail;
  private int nextId;
  private final List<Snapshot> snapshots = new ArrayList<>();

  /** Bounded companion to an existing CPU VRAM save; the owner must restore or release it. */
  public static final class Snapshot {
    private final Rect4i rect;
    private final int[] owners, offsets;
    private int[] detail;
    private boolean released;
    private Snapshot(final Rect4i rect, final int[] owners, final int[] offsets, final int[] detail) {
      this.rect = rect; this.owners = owners; this.offsets = offsets; this.detail = detail;
    }
  }

  public Snapshot capture(final Rect4i rect) { return this.capture(rect, false); }

  private Snapshot capture(final Rect4i rect, final boolean rotation) {
    if(rect.x < 0 || rect.y < 0 || rect.w < 1 || rect.h < 1 || rect.x + rect.w > 1024 || rect.y + rect.h > 512 || (long)rect.w * rect.h > 32768 || this.snapshots.size() >= (rotation ? 3 : 2)) throw new IllegalArgumentException("FX CPU snapshot exceeds the two-region budget");
    final int[] owners = new int[rect.w * rect.h], offsets = new int[owners.length];
    final int[] detail = this.detail == null ? null : new int[owners.length * 8];
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      final int src = (rect.y + y) * 1024 + rect.x + x, dst = y * rect.w + x;
      owners[dst] = this.owners[src]; offsets[dst] = this.offsets[src];
      final Source source = this.sources.get(owners[dst]);
      if(source != null) source.references++;
      if(detail != null) {
        final int p = (rect.y + y) * 2 * WIDTH + (rect.x + x) * 4;
        System.arraycopy(this.detail, p, detail, dst * 8, 4);
        System.arraycopy(this.detail, p + WIDTH, detail, dst * 8 + 4, 4);
      }
    }
    final Snapshot snapshot = new Snapshot(new Rect4i(rect.x, rect.y, rect.w, rect.h), owners, offsets, detail);
    this.snapshots.add(snapshot);
    return snapshot;
  }

  public void restore(final Snapshot snapshot) {
    if(snapshot == null || snapshot.released || !this.snapshots.contains(snapshot)) return;
    final Rect4i rect = snapshot.rect;
    if(snapshot.detail != null && this.detail == null) { this.detail = new int[WIDTH * HEIGHT]; this.markAllDirty(); }
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      final int src = y * rect.w + x, dst = (rect.y + y) * 1024 + rect.x + x;
      final Source source = this.sources.get(snapshot.owners[src]);
      if(source != null) source.references++;
      this.invalidateWord(dst);
      this.owners[dst] = snapshot.owners[src]; this.offsets[dst] = snapshot.offsets[src];
      if(snapshot.detail != null) {
        final int p = (rect.y + y) * 2 * WIDTH + (rect.x + x) * 4;
        System.arraycopy(snapshot.detail, src * 8, this.detail, p, 4);
        System.arraycopy(snapshot.detail, src * 8 + 4, this.detail, p + WIDTH, 4);
      }
    }
    if(this.detail != null) this.dirtyRows.set(rect.y * 2, (rect.y + rect.h) * 2);
    this.release(snapshot);
  }

  public void release(final Snapshot snapshot) {
    if(snapshot == null || snapshot.released || !this.snapshots.remove(snapshot)) return;
    snapshot.released = true;
    for(final int owner : snapshot.owners) {
      final Source source = this.sources.get(owner);
      if(source != null && --source.references == 0) this.sources.remove(owner);
    }
    snapshot.detail = null;
  }

  public void rotateRows(final Rect4i rect, final int rows) {
    final Snapshot snapshot = this.capture(rect, true);
    final int[] owners = snapshot.owners.clone(), offsets = snapshot.offsets.clone();
    final int[] detail = snapshot.detail == null ? null : snapshot.detail.clone();
    for(int y = 0; y < rect.h; y++) {
      final int src = Math.floorMod(y - rows, rect.h) * rect.w, dst = y * rect.w;
      System.arraycopy(owners, src, snapshot.owners, dst, rect.w);
      System.arraycopy(offsets, src, snapshot.offsets, dst, rect.w);
      if(detail != null) System.arraycopy(detail, src * 8, snapshot.detail, dst * 8, rect.w * 8);
    }
    this.restore(snapshot);
  }

  public static final class Source {
    public final int id, width, height;
    public final String hash;
    private int references;
    private Source(final int id, final String hash, final int width, final int height) {
      this.id = id; this.hash = hash; this.width = width; this.height = height;
    }
    public IndexedEffectTextureEvent event() { return new IndexedEffectTextureEvent(this.hash, this.width, this.height); }
  }

  public List<Source> sources() { return List.copyOf(this.sources.values()); }
  public boolean allocated() { return this.detail != null; }
  public int[] pixels() { return this.detail; }
  public BitSet takeDirtyRows() {
    final BitSet result = (BitSet)this.dirtyRows.clone();
    this.dirtyRows.clear();
    return result;
  }
  public void markAllDirty() { if(this.detail != null) this.dirtyRows.set(0, HEIGHT); }

  public static void validate(final Tim tim, final Image image) {
    final Rect4i rect = tim.getImageRect();
    validateDimensions(rect.w * 4, rect.h, image);
    final FileData words = tim.getImageData();
    for(int y = 0; y < image.height; y++) for(int x = 0; x < image.width; x++) {
      final int nativeX = x / 2;
      final int index = words.readUShort((y / 2 * rect.w + nativeX / 4) * 2) >>> ((nativeX & 3) * 4) & 15;
      if(Byte.toUnsignedInt(image.data[(y * image.width + x) * 4]) != index) throw new IllegalArgumentException("FX detail source index differs");
    }
  }

  private static void validateDimensions(final int width, final int height, final Image image) {
    if(width < 1 || width > 1024 || height < 1 || height > 512 || image.width != width * 2 || image.height != height * 2
      || image.data.length != (long)image.width * image.height * 4) throw new IllegalArgumentException("FX detail dimensions differ");
    for(int i = 0; i < image.data.length; i += 4) {
      if(Byte.toUnsignedInt(image.data[i]) > 15 || Byte.toUnsignedInt(image.data[i + 1]) > 15
        || Byte.toUnsignedInt(image.data[i + 2]) > 127 || Byte.toUnsignedInt(image.data[i + 3]) != 255) throw new IllegalArgumentException("FX palette interpolation differs");
    }
  }

  /** Remember only identity and current word ownership, never original game files or palettes. */
  public Source register(final Rect4i rect, final String hash, final Image image) {
    if(rect.x < 0 || rect.y < 0 || rect.w < 1 || rect.h < 1 || rect.x + rect.w > 1024 || rect.y + rect.h > 512 || rect.w * 4 > 1024) throw new IllegalArgumentException("FX image exceeds native VRAM");
    if(image != null) validateDimensions(rect.w * 4, rect.h, image);
    final Source source = new Source(++this.nextId, hash, rect.w * 4, rect.h);
    this.sources.put(source.id, source);
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      final int target = (rect.y + y) * 1024 + rect.x + x;
      this.invalidateWord(target);
      this.owners[target] = source.id;
      this.offsets[target] = y * rect.w + x;
      source.references++;
      if(image != null) this.writeWord(target, this.offsets[target], source.width, image);
    }
    return source;
  }

  public void reselect(final int sourceId, final Image image) {
    final Source source = this.sources.get(sourceId);
    if(source == null) return; // An obsolete load/reselection cannot resurrect an overwritten region.
    if(image != null) validateDimensions(source.width, source.height, image);
    for(int i = 0; i < this.owners.length; i++) if(this.owners[i] == sourceId) {
      this.clearDetail(i);
      if(image != null) this.writeWord(i, this.offsets[i], source.width, image);
    }
    for(final Snapshot snapshot : this.snapshots) {
      for(int i = 0; i < snapshot.owners.length; i++) if(snapshot.owners[i] == sourceId) {
        if(snapshot.detail == null && image != null) snapshot.detail = new int[snapshot.owners.length * 8];
        if(snapshot.detail != null) {
          if(image == null) java.util.Arrays.fill(snapshot.detail, i * 8, i * 8 + 8, 0);
          else writePackedWord(snapshot.detail, i * 8, 4, snapshot.offsets[i], source.width, image);
        }
      }
    }
  }

  public void invalidateWord(final int word) {
    if(this.owners[word] == 0) return;
    final Source source = this.sources.get(this.owners[word]);
    if(source != null && --source.references == 0) this.sources.remove(source.id);
    this.owners[word] = 0;
    this.clearDetail(word);
  }

  /** Mirror the GPU's actual traversal and masked-write decisions, including overlapping copies. */
  public void copyWord(final int sourceWord, final int targetWord) {
    if(sourceWord == targetWord) return;
    final int owner = this.owners[sourceWord], offset = this.offsets[sourceWord];
    final Source source = this.sources.get(owner);
    if(source != null) source.references++; // Retain before releasing the overwritten destination.
    this.invalidateWord(targetWord);
    this.owners[targetWord] = owner;
    this.offsets[targetWord] = offset;
    if(this.detail != null) {
      final int src = sourceWord / 1024 * 2 * WIDTH + sourceWord % 1024 * 4;
      final int dst = targetWord / 1024 * 2 * WIDTH + targetWord % 1024 * 4;
      System.arraycopy(this.detail, src, this.detail, dst, 4);
      System.arraycopy(this.detail, src + WIDTH, this.detail, dst + WIDTH, 4);
      this.dirtyRows.set(targetWord / 1024 * 2, targetWord / 1024 * 2 + 2);
    }
  }

  private void clearDetail(final int word) {
    if(this.detail == null) return;
    final int start = word / 1024 * 2 * WIDTH + word % 1024 * 4;
    // Avoid dirtying rows for unrelated original writes into already empty detail slots.
    boolean changed = false;
    for(int row = 0; row < 2; row++) for(int x = 0; x < 4; x++) {
      final int i = start + row * WIDTH + x;
      changed |= this.detail[i] != 0;
      this.detail[i] = 0;
    }
    if(changed) this.dirtyRows.set(word / 1024 * 2, word / 1024 * 2 + 2);
  }

  private void writeWord(final int target, final int sourceWord, final int width, final Image image) {
    if(this.detail == null) { this.detail = new int[WIDTH * HEIGHT]; this.markAllDirty(); }
    final int dst = target / 1024 * 2 * WIDTH + target % 1024 * 4;
    writePackedWord(this.detail, dst, WIDTH, sourceWord, width, image);
    this.dirtyRows.set(target / 1024 * 2, target / 1024 * 2 + 2);
  }

  private static void writePackedWord(final int[] destination, final int dst, final int stride, final int sourceWord, final int width, final Image image) {
    final int sx = sourceWord % (width / 4) * 8, sy = sourceWord / (width / 4) * 2;
    for(int row = 0; row < 2; row++) for(int x = 0; x < 4; x++) {
      final int p = ((sy + row) * image.width + sx + x * 2) * 4;
      destination[dst + row * stride + x] = code(image.data, p) | code(image.data, p + 4) << 16;
    }
  }

  private static int code(final byte[] rgba, final int index) {
    return 0x8000 | Byte.toUnsignedInt(rgba[index]) | Byte.toUnsignedInt(rgba[index + 1]) << 4 | Byte.toUnsignedInt(rgba[index + 2]) << 8;
  }
}
