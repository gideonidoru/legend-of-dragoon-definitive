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
    final long timeoutNanos;
    try { timeoutNanos = timeout.toNanos(); } catch(final ArithmeticException tooLarge) { throw new IllegalArgumentException("Transfer timeout exceeds supported limits.", tooLarge); }
    if(Thread.currentThread().isInterrupted()) {
      final var interrupted = new InterruptedException("Download cancelled before transfer.");
      try { input.close(); } catch(final IOException cleanup) { interrupted.addSuppressed(cleanup); }
      throw interrupted;
    }
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
    final long deadline = System.nanoTime() + timeoutNanos;
    Throwable primary = null;
    try {
      while(true) {
        final long remaining = deadline - System.nanoTime();
        if(remaining <= 0 || System.nanoTime() - lastRead.get() > TimeUnit.SECONDS.toNanos(60)) throw new TimeoutException();
        try { return transfer.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250)), TimeUnit.NANOSECONDS); }
        catch(final TimeoutException waiting) { if(transfer.isDone()) continue; }
      }
    }
    catch(final InterruptedException interrupted) { primary = interrupted; Thread.currentThread().interrupt(); throw interrupted; }
    catch(final TimeoutException e) {
      final var failure = new IOException("Download stalled or took too long. Your current installation is unchanged; retry when connected.", e);
      primary = failure; throw failure;
    }
    catch(final ExecutionException e) {
      final IOException failure = e.getCause() instanceof IOException io ? io : new IOException("Download could not complete.", e.getCause());
      primary = failure; throw failure;
    }
    finally {
      cancelled.set(true);
      if(!transfer.isDone()) transfer.cancel(true);
      final IOException cleanup = stopReader(input, reader);
      if(cleanup != null) {
        if(cleanup instanceof WorkerCleanupException && !(primary instanceof InterruptedException)) { if(primary != null) cleanup.addSuppressed(primary); throw cleanup; }
        if(primary != null) primary.addSuppressed(cleanup); else throw cleanup;
      }
    }
  }
  private static IOException stopReader(final InputStream input, final Thread reader) {
    boolean interrupted = Thread.interrupted();
    IOException failure = null;
    try {
      try { input.close(); } catch(final IOException close) { failure = close; }
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      while(reader.isAlive()) {
        final long remaining = deadline - System.nanoTime();
        if(remaining <= 0) break;
        try { reader.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))); }
        catch(final InterruptedException stopped) { interrupted = true; reader.interrupt(); }
      }
      if(reader.isAlive()) {
        final var stuck = new WorkerCleanupException(); if(failure != null) stuck.addSuppressed(failure); failure = stuck;
      }
      return failure;
    } finally { if(interrupted) Thread.currentThread().interrupt(); }
  }
  static final class WorkerCleanupException extends IOException { WorkerCleanupException() { super("Download worker did not stop after cancellation; close setup before retrying."); } }
  static final class SizeLimitException extends IOException { SizeLimitException() { super("Download exceeds its size limit."); } }
  static final class WriteException extends IOException { WriteException(final IOException cause) { super("Could not write the download. Check installation storage.", cause); } }
}
