package legend.core.renderer;

import legend.game.tmd.TmdFaceDetail;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/** Renderer-thread ownership of one GPU upload per immutable face-detail image. */
public final class SharedFaceTexture {
  private static final SharedFaceTexture SHARED = new SharedFaceTexture((name, image) -> Texture.create(name, builder -> {
    builder.data(image.pixels(), image.width, image.height);
    builder.minFilter(true); builder.magFilter(true); builder.wrapS(false); builder.wrapT(false);
  }));

  private final Map<TmdFaceDetail.Image, Entry> images = new IdentityHashMap<>();
  private final BiFunction<String, TmdFaceDetail.Image, Texture> upload;

  // A backend-independent seam for ownership and upload-failure tests.
  SharedFaceTexture(final BiFunction<String, TmdFaceDetail.Image, Texture> upload) {
    this.upload = upload;
  }

  public static Lease acquire(final String name, final TmdFaceDetail.Image image) {
    return SHARED.retain(name, image);
  }

  Lease retain(final String name, final TmdFaceDetail.Image image) {
    Entry entry = this.images.get(image);
    // Forced renderer teardown retires textures even while old mesh owners exist.
    if(entry == null || entry.texture.deleted) {
      final Texture texture = java.util.Objects.requireNonNull(this.upload.apply(name, image));
      texture.persistent = true;
      entry = new Entry(texture);
      this.images.put(image, entry);
    }
    entry.owners++;
    return new Lease(this, image, entry);
  }

  private static final class Entry {
    private final Texture texture;
    private int owners;
    private Entry(final Texture texture) { this.texture = texture; }
  }

  /** A mesh holds this lease until renderer retirement, including retained draws. */
  public static final class Lease implements AutoCloseable {
    private final SharedFaceTexture cache;
    private final TmdFaceDetail.Image image;
    private final Entry entry;
    private boolean closed;

    private Lease(final SharedFaceTexture cache, final TmdFaceDetail.Image image, final Entry entry) {
      this.cache = cache; this.image = image; this.entry = entry;
    }

    public Texture texture() {
      if(this.closed) throw new IllegalStateException("Face texture lease is closed");
      return this.entry.texture;
    }

    @Override public void close() {
      if(this.closed) return;
      this.closed = true;
      if(--this.entry.owners == 0) {
        this.entry.texture.delete();
        if(this.cache.images.get(this.image) == this.entry) this.cache.images.remove(this.image);
      }
    }
  }
}
