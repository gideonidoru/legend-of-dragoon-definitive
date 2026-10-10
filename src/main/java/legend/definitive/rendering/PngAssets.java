package legend.definitive.rendering;

import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.lwjgl.stb.STBImage.*;

/** Bounded decoded-image cache. Worker prewarming never creates or uploads a graphics object. */
public final class PngAssets implements AutoCloseable {
  public static final PngAssets SHARED = new PngAssets(64L * 1024 * 1024);
  public static final PngAssets UNCACHED = new PngAssets(0);
  private static final int MAX_ENCODED_BYTES = 64 * 1024 * 1024;
  private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2), work -> {
    final Thread thread = new Thread(work,"HD image prewarm"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
  });
  private final long budget;
  private final LinkedHashMap<String,Entry> cache = new LinkedHashMap<>(16,.75f,true);
  private long cachedBytes, liveBytes, hits, misses, evictions, decodeNanos, decodedBytes;
  private boolean closed;
  private boolean retentionEnabled = true;
  private static final class Entry {
    final ByteBuffer pixels;
    final int width, height;
    int users;
    boolean retired;
    Entry(final ByteBuffer pixels, final int width, final int height) { this.pixels=pixels; this.width=width; this.height=height; }
  }
  public record Stats(long budgetBytes, long cachedBytes, long liveDecodedBytes, long hits, long misses, long evictions, long decodeCpuNanos, long decodedBytes) { }
  public final class Image implements AutoCloseable {
    private Entry entry;
    private Image(final Entry entry) { this.entry=entry; }
    public int width() { return this.entry.width; }
    public int height() { return this.entry.height; }
    public ByteBuffer pixels() { return this.entry.pixels.asReadOnlyBuffer(); }
    @Override public void close() {
      synchronized(PngAssets.this) {
        if(this.entry == null) return;
        if(--this.entry.users == 0 && this.entry.retired) release(this.entry);
        this.entry=null;
      }
    }
  }
  public PngAssets(final long budget) {
    if(budget < 0 || budget > 256L*1024*1024) throw new IllegalArgumentException("Decoded cache budget out of range");
    this.budget=budget;
  }
  private static String identity(final ByteBuffer encoded) {
    try {
      final var digest=MessageDigest.getInstance("SHA-256"); digest.update(encoded.duplicate());
      return HexFormat.of().formatHex(digest.digest());
    } catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
  }
  public Image acquire(final ByteBuffer encoded) {
    if(encoded.remaining() == 0 || encoded.remaining() > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Encoded image exceeds loading budget");
    final String identity=identity(encoded);
    synchronized(this) {
      if(this.closed) throw new IllegalStateException("Image cache closed");
      final Entry cached=this.cache.get(identity);
      if(cached != null) { this.hits++; cached.users++; return new Image(cached); }
      this.misses++;
    }
    final long start=System.nanoTime();
    final Entry loaded=decode(encoded);
    synchronized(this) {
      this.decodeNanos+=System.nanoTime()-start;
      this.decodedBytes+=loaded.pixels.remaining();
      this.liveBytes+=loaded.pixels.remaining();
      if(this.closed) { release(loaded); throw new IllegalStateException("Image cache closed while decoding"); }
      final Entry raced=this.cache.get(identity);
      if(raced != null) { release(loaded); raced.users++; return new Image(raced); }
      loaded.users=1;
      if(!this.retentionEnabled || loaded.pixels.remaining() > this.budget) loaded.retired=true;
      else {
        while(this.cachedBytes + loaded.pixels.remaining() > this.budget) {
          final var first=this.cache.entrySet().iterator(); final Entry oldest=first.next().getValue(); first.remove();
          this.cachedBytes-=oldest.pixels.remaining(); this.evictions++; oldest.retired=true;
          if(oldest.users == 0) release(oldest);
        }
        this.cache.put(identity,loaded); this.cachedBytes+=loaded.pixels.remaining();
      }
      return new Image(loaded);
    }
  }
  private static Entry decode(final ByteBuffer encoded) {
    try(final MemoryStack stack=MemoryStack.stackPush()) {
      final var w=stack.mallocInt(1); final var h=stack.mallocInt(1); final var channels=stack.mallocInt(1);
      final ByteBuffer input;
      if(encoded.isDirect()) input=encoded.duplicate();
      else { input=org.lwjgl.system.MemoryUtil.memAlloc(encoded.remaining()); input.put(encoded.duplicate()).flip(); }
      try {
        if(!stbi_info_from_memory(input,w,h,channels) || w.get(0) <= 0 || h.get(0) <= 0 || w.get(0) > 16384 || h.get(0) > 16384 || (long)w.get(0)*h.get(0)*4 > 256L*1024*1024) throw new IllegalArgumentException("Invalid or oversized image dimensions");
        final ByteBuffer pixels=stbi_load_from_memory(input,w,h,channels,4);
        if(pixels == null) throw new IllegalArgumentException("Image decode failed: " + stbi_failure_reason());
        return new Entry(pixels,w.get(0),h.get(0));
      } finally {
        if(!encoded.isDirect()) org.lwjgl.system.MemoryUtil.memFree(input);
      }
    }
  }
  private void release(final Entry entry) { this.liveBytes-=entry.pixels.remaining(); stbi_image_free(entry.pixels); }
  public synchronized Stats stats() { return new Stats(this.budget,this.cachedBytes,this.liveBytes,this.hits,this.misses,this.evictions,this.decodeNanos,this.decodedBytes); }
  public synchronized void clear() {
    for(final Entry entry : this.cache.values()) { entry.retired=true; if(entry.users == 0) release(entry); }
    this.cache.clear(); this.cachedBytes=0;
  }
  /** Disabling retention also covers decodes that finish after the configuration change. */
  public synchronized void retention(final boolean enabled) { this.retentionEnabled=enabled; if(!enabled) this.clear(); }
  @Override public synchronized void close() { this.closed=true; this.clear(); }

  /** Bounded queue; reads compressed bytes on the worker and takes ownership of its stream. */
  public CompletableFuture<Boolean> prewarm(final java.util.function.Supplier<InputStream> source) {
    final CompletableFuture<Boolean> result=new CompletableFuture<>();
    try {
      WORKER.execute(() -> {
        if(result.isCancelled()) return;
        try(final InputStream stream=source.get()) {
          if(stream == null) throw new IOException("Missing image resource");
          final byte[] data=stream.readNBytes(MAX_ENCODED_BYTES+1);
          try(final Image image=this.acquire(ByteBuffer.wrap(data))) { result.complete(true); }
        } catch(final Exception failure) { result.completeExceptionally(failure); }
      });
    } catch(final java.util.concurrent.RejectedExecutionException full) { result.complete(false); }
    return result;
  }
  public CompletableFuture<Boolean> prewarm(final Path path) {
    return this.prewarm(() -> { try { return Files.newInputStream(path); } catch(final IOException e) { throw new java.io.UncheckedIOException(e); } });
  }
}
