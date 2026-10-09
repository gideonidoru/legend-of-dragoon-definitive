// Definitive bounded response transfer (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;

/** A body deadline independent of the HTTP response-header timeout. */
final class DownloadBody {
  private DownloadBody() { }
  static long copy(final InputStream input, final OutputStream output, final long limit, final Duration timeout) throws IOException, InterruptedException {
    final FutureTask<Long> transfer = new FutureTask<>(() -> {
      final byte[] buffer = new byte[65536]; long total = 0;
      for(int count; (count = input.read(buffer)) != -1;) {
        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled.");
        if((total += count) > limit) throw new IOException("Download exceeds its size limit.");
        output.write(buffer, 0, count);
      }
      return total;
    });
    final Thread reader = Thread.ofVirtual().name("definitive-download").start(transfer);
    try { return transfer.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
    catch(final TimeoutException e) { throw new IOException("Download stalled or took too long. Your current installation is unchanged; retry when connected.", e); }
    catch(final ExecutionException e) { if(e.getCause() instanceof IOException io) throw io; throw new IOException("Download could not complete.", e.getCause()); }
    finally {
      if(!transfer.isDone()) transfer.cancel(true);
      input.close();
      reader.join(1000);
    }
  }
}
