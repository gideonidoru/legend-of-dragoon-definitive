package legend.definitive.rendering;

import java.util.concurrent.atomic.AtomicLong;

/** CPU-side loading measurements only. Never represented as GPU frame-time measurements. */
public final class AssetLoadMetrics {
  private static final AtomicLong UPLOADS = new AtomicLong(), UPLOAD_NANOS = new AtomicLong(), UPLOAD_BYTES = new AtomicLong();
  private AssetLoadMetrics() { }
  public record Uploads(long calls, long cpuNanos, long payloadBytes) { }
  public static void upload(final long nanos, final long bytes) {
    UPLOADS.incrementAndGet(); UPLOAD_NANOS.addAndGet(Math.max(0,nanos)); UPLOAD_BYTES.addAndGet(Math.max(0,bytes));
  }
  public static Uploads uploads() { return new Uploads(UPLOADS.get(),UPLOAD_NANOS.get(),UPLOAD_BYTES.get()); }

  public static long payloadBytes(final int width, final int height, final legend.core.renderer.TextureDataFormat format, final legend.core.renderer.TextureDataType type) {
    final int components = switch(format) { case RGB -> 3; case RGBA -> 4; default -> 1; };
    return (long)width * height * components * (type == legend.core.renderer.TextureDataType.UBYTE ? 1 : 4);
  }
}
