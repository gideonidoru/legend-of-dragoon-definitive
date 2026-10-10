// Definitive bounded response transfer (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;

/** A body deadline independent of the HTTP response-header timeout. */
final class DownloadBody {
  private DownloadBody() { }
  static long copy(final InputStream input, final OutputStream output, final long limit, final Duration timeout) throws IOException, InterruptedException {
    return copy(input, output, limit, timeout, bytes -> { });
  }
  static long copy(final InputStream input, final OutputStream output, final long limit, final Duration timeout, final java.util.function.LongConsumer progress) throws IOException, InterruptedException {
    if(limit < 0 || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Choose positive transfer limits.");
    final var lastRead = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
    final var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    final FutureTask<Long> transfer = new FutureTask<>(() -> {
      final byte[] buffer = new byte[65536]; long total = 0;
      for(int count; (count = input.read(buffer)) != -1;) {
        if(cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled.");
        if((total += count) > limit) throw new SizeLimitException();
        try { output.write(buffer, 0, count); }
        catch(final IOException failure) { throw new WriteException(failure); }
        lastRead.set(System.nanoTime()); progress.accept(total);
      }
      return total;
    });
    final Thread reader = Thread.ofVirtual().name("definitive-download").start(transfer);
    final long deadline = System.nanoTime() + timeout.toNanos();
    try {
      while(true) {
        final long remaining = deadline - System.nanoTime();
        if(remaining <= 0 || System.nanoTime() - lastRead.get() > TimeUnit.SECONDS.toNanos(60)) throw new TimeoutException();
        try { return transfer.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250)), TimeUnit.NANOSECONDS); }
        catch(final TimeoutException waiting) { if(transfer.isDone()) continue; }
      }
    }
    catch(final TimeoutException e) { throw new IOException("Download stalled or took too long. Your current installation is unchanged; retry when connected.", e); }
    catch(final ExecutionException e) { if(e.getCause() instanceof IOException io) throw io; throw new IOException("Download could not complete.", e.getCause()); }
    finally {
      cancelled.set(true);
      if(!transfer.isDone()) transfer.cancel(true);
      try { input.close(); }
      finally { reader.join(1000); }
      if(reader.isAlive()) throw new IOException("Download worker did not stop after cancellation; retry after closing setup.");
    }
  }
  static final class SizeLimitException extends IOException { SizeLimitException() { super("Download exceeds its size limit."); } }
  static final class WriteException extends IOException { WriteException(final IOException cause) { super("Could not write the download. Check installation storage.", cause); } }
}
