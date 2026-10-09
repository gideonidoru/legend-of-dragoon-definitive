package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class DownloadBodyTest {
  @Test void boundedTransferAndStalledReaderCleanup() throws Exception {
    final var output = new ByteArrayOutputStream();
    assertEquals(3, DownloadBody.copy(new ByteArrayInputStream(new byte[]{1,2,3}), output, 3, Duration.ofSeconds(1)));
    assertArrayEquals(new byte[]{1,2,3}, output.toByteArray());
    assertThrows(IOException.class, () -> DownloadBody.copy(new ByteArrayInputStream(new byte[4]), new ByteArrayOutputStream(), 3, Duration.ofSeconds(1)));
    final AtomicBoolean closed = new AtomicBoolean(), interrupted = new AtomicBoolean();
    final InputStream stalled = new InputStream() {
      @Override public int read() throws IOException {
        try { Thread.sleep(60000); return -1; }
        catch(final InterruptedException e) { interrupted.set(true); throw new InterruptedIOException(); }
      }
      @Override public void close() { closed.set(true); }
    };
    assertThrows(IOException.class, () -> DownloadBody.copy(stalled, new ByteArrayOutputStream(), 1024, Duration.ofMillis(200)));
    assertTrue(closed.get()); assertTrue(interrupted.get());
  }
}
