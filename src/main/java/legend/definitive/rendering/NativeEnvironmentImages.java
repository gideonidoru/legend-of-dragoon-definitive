package legend.definitive.rendering;

import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureLoader;
import legend.core.gpu.VramTextureSingle;
import legend.game.tim.Tim;
import java.util.IdentityHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** First-visit CPU palette expansion for native field assets; never touches the GPU or global VRAM. */
public final class NativeEnvironmentImages {
  public static final long MAX_BYTES = 32L*1024*1024;
  public record Tile(Tim source, int u, int v, int width, int height, boolean foreground) { }
  public record Segment(int width, int height, int[] rgba) { }
  private record Decoded(VramTextureSingle texture, VramTextureSingle palette) { }
  private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1), work -> {
    final Thread thread = new Thread(work,"Native environment preparation"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
  });
  private NativeEnvironmentImages() { }
  public static CompletableFuture<Segment[]> prewarm(final Tile[] source) {
    final Tile[] tiles=source.clone();
    final CompletableFuture<Segment[]> result=new CompletableFuture<>();
    try {
      WORKER.execute(() -> {
        if(result.isCancelled()) return;
        try { result.complete(decode(tiles)); }
        catch(final RuntimeException failure) { result.completeExceptionally(failure); }
      });
    } catch(final java.util.concurrent.RejectedExecutionException full) { result.complete(null); }
    return result;
  }
  public static Segment[] decode(final Tile[] tiles) {
    if(tiles.length > 32) throw new IllegalArgumentException("Native environment tile limit");
    final Segment[] segments=new Segment[tiles.length];
    final IdentityHashMap<Tim,Decoded> sources=new IdentityHashMap<>();
    long bytes=0;
    for(int i=0;i<tiles.length;i++) {
      final Tile tile=tiles[i];
      if(tile == null) continue;
      if(tile.width() <= 0 || tile.height() <= 0 || tile.width() > 16384 || tile.height() > 16384) throw new IllegalArgumentException("Native environment dimensions");
      Decoded decoded=sources.get(tile.source());
      if(decoded == null) {
        final Rect4i image=tile.source().getImageRect();
        final Rect4i clut=tile.source().getClutRect();
        final long sourceBytes=((long)image.w*image.h*tile.source().getBpp().widthDivisor+(long)clut.w*clut.h)*4;
        if(image.w <= 0 || image.h <= 0 || sourceBytes > MAX_BYTES-bytes) throw new IllegalArgumentException("Native environment source budget");
        bytes+=sourceBytes;
        decoded=new Decoded(VramTextureLoader.textureFromTim(tile.source()),VramTextureLoader.palettesFromTim(tile.source())[0]);
        sources.put(tile.source(),decoded);
      }
      final Rect4i region=new Rect4i(tile.u(),tile.v(),tile.width(),tile.height());
      // Keep the existing Neet/lumberjack oversized foreground correction exactly.
      if(tile.foreground()) {
        region.w=Math.min(region.w,decoded.texture().rect.w-region.x);
        region.h=Math.min(region.h,decoded.texture().rect.h-region.y);
      }
      final long segmentBytes=(long)region.w*region.h*4;
      if(region.x < 0 || region.y < 0 || region.w <= 0 || region.h <= 0 || region.x > decoded.texture().rect.w || region.y > decoded.texture().rect.h || region.w > decoded.texture().rect.w-region.x || region.h > decoded.texture().rect.h-region.y || segmentBytes > MAX_BYTES-bytes) throw new IllegalArgumentException("Native environment region budget");
      bytes+=segmentBytes;
      final int[] pixels=decoded.texture().applyPalette(decoded.palette(),region);
      for(int p=0;p<pixels.length;p++) if(pixels[p] != 0) pixels[p] |= 0xff000000;
      segments[i]=new Segment(region.w,region.h,pixels);
    }
    return segments;
  }
}
