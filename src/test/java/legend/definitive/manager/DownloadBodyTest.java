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
  @Test void cancellationRemainsInterruptedExceptionWhenInputCloseAlsoFails() throws Exception {
    final var reading = new java.util.concurrent.CountDownLatch(1); final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    final var interrupted = new AtomicBoolean();
    final InputStream input = new InputStream() {
      @Override public int read() throws IOException {
        reading.countDown();
        try { Thread.sleep(60000); return -1; } catch(final InterruptedException stopped) { throw new InterruptedIOException(); }
      }
      @Override public void close() throws IOException { throw new IOException("fixture close failed"); }
    };
    final Thread worker = Thread.ofPlatform().start(() -> {
      try { DownloadBody.copy(input,new ByteArrayOutputStream(),100,Duration.ofSeconds(10)); }
      catch(final Throwable thrown) { failure.set(thrown); interrupted.set(Thread.currentThread().isInterrupted()); }
    });
    assertTrue(reading.await(2,java.util.concurrent.TimeUnit.SECONDS)); worker.interrupt(); worker.join(3000);
    assertFalse(worker.isAlive()); assertInstanceOf(InterruptedException.class,failure.get()); assertTrue(interrupted.get());
    assertTrue(java.util.Arrays.stream(failure.get().getSuppressed()).anyMatch(thrown -> thrown.getMessage().contains("fixture close failed")));
  }
  @Test void alreadyCancelledTransferDoesNotStartReadingOrWriting() throws Exception {
    final var reads = new java.util.concurrent.atomic.AtomicInteger(); final var closed = new AtomicBoolean();
    final var output = new ByteArrayOutputStream();
    final InputStream input = new InputStream() { @Override public int read() { reads.incrementAndGet(); return -1; } @Override public void close() { closed.set(true); } };
    Thread.currentThread().interrupt();
    try { assertThrows(InterruptedException.class,() -> DownloadBody.copy(input,output,10,Duration.ofSeconds(1))); assertTrue(Thread.currentThread().isInterrupted()); }
    finally { Thread.interrupted(); }
    assertEquals(0,reads.get()); assertEquals(0,output.size()); assertTrue(closed.get());
  }

  @Test void uncooperativeReaderProducesFatalCleanupFailureThenFixtureStopsIt() throws Exception {
    final var release = new java.util.concurrent.CountDownLatch(1); final var stopped = new java.util.concurrent.CountDownLatch(1);
    final InputStream input = new InputStream() {
      @Override public int read() {
        try { while(true) { try { release.await(); return -1; } catch(final InterruptedException ignored) { } } }
        finally { stopped.countDown(); }
      }
    };
    try { assertThrows(DownloadBody.WorkerCleanupException.class,() -> DownloadBody.copy(input,new ByteArrayOutputStream(),100,Duration.ofMillis(50))); }
    finally { release.countDown(); assertTrue(stopped.await(2,java.util.concurrent.TimeUnit.SECONDS)); }
  }

}
