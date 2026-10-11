package legend.definitive.loading;

import legend.core.Async;
import legend.game.unpacker.Loader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Actual executor and loader settlement, with a bounded two-worker pool in isolated JVMs. */
final class AsyncShutdownTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"forcedQueueSettlement", "forcedLoaderSettlement", "gracefulDrain", "taskOutcomes", "consumerCancellation", "rejectionAndNullArguments"})
  void actualExecutorContract(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-XX:ActiveProcessorCount=2", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      AsyncShutdownTest.class.getName(), scenario, this.temporary.toString()
    ).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Executor scenario must complete within its bounded deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Executor child must retire");
    }
  }

  public static void main(final String[] args) throws Exception {
    final var fixture = new AsyncShutdownTest();
    fixture.temporary = Path.of(args[1]);
    try {
      AsyncShutdownTest.class.getDeclaredMethod(args[0]).invoke(fixture);
      System.out.println("PASS " + args[0]);
    } finally {
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown(), "Actual executor must retire before fixture exit");
    }
  }

  private static final class OccupiedPool implements AutoCloseable {
    private final CountDownLatch started = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CompletableFuture<?>[] active = new CompletableFuture<?>[2];

    OccupiedPool() throws Exception {
      assertEquals(2, Runtime.getRuntime().availableProcessors());
      for(int i = 0; i < this.active.length; i++) this.active[i] = Async.run(() -> {
        this.started.countDown();
        try { this.release.await(); }
        catch(final InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      });
      assertTrue(this.started.await(2, TimeUnit.SECONDS), "Both real workers must occupy the pool before queueing");
    }

    @Override public void close() { this.release.countDown(); }
  }

  private void forcedQueueSettlement() throws Exception {
    try(final var pool = new OccupiedPool()) {
      final var ran = new AtomicInteger();
      final CompletableFuture<Void> runnable = Async.run(() -> { ran.incrementAndGet(); });
      final CompletableFuture<Integer> supplier = Async.run(() -> { ran.incrementAndGet(); return 7; });
      final var callbacks = new AtomicInteger();
      runnable.whenComplete((value, failure) -> callbacks.incrementAndGet());
      supplier.whenComplete((value, failure) -> callbacks.incrementAndGet());
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown());
      assertEquals(0, ran.get(), "Discarded operations must not execute");
      assertTrue(runnable.isCancelled(), "Discarded runnable must settle rather than stay pending");
      assertTrue(supplier.isCancelled(), "Discarded supplier must settle rather than stay pending");
      assertThrows(CancellationException.class, () -> runnable.get(1, TimeUnit.SECONDS));
      assertThrows(CancellationException.class, () -> supplier.get(1, TimeUnit.SECONDS));
      assertEquals(2, callbacks.get(), "Each discarded operation must notify dependents once");
      for(final var active : pool.active) assertTrue(active.isDone(), "Started operations retain their actual completion");
      Async.shutdownNow();
      assertEquals(2, callbacks.get(), "Repeated shutdown must not repeat settlement");
    }
  }

  private void forcedLoaderSettlement() throws Exception {
    try(final var pool = new OccupiedPool()) {
      final Path absent = this.temporary.resolve("absent");
      final var reads = List.of(Loader.loadFile(absent), Loader.loadFile("absent"),
        Loader.loadFiles(absent, absent), Loader.loadFiles("absent", "absent"),
        Loader.loadDirectory(absent), Loader.loadDirectory("absent"));
      assertEquals(8, Loader.getLoadingFileCount());
      assertTrue(reads.getFirst().cancel(false));
      assertEquals(8, Loader.getLoadingFileCount(), "Consumer cancellation must not retire actual queued work");
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown());
      for(final var read : reads) assertTrue(read.isCompletedExceptionally(), "Discarded loader work must reach a terminal failure");
      assertEquals(0, Loader.getLoadingFileCount(), "All six loader APIs must retire their actual queue counts");
      Async.shutdownNow();
      assertEquals(0, Loader.getLoadingFileCount(), "Repeated shutdown must not decrement counts twice");
    }
  }

  private void gracefulDrain() throws Exception {
    try(final var pool = new OccupiedPool()) {
      final var count = new AtomicInteger();
      final CompletableFuture<Void> runnable = Async.run(() -> { count.incrementAndGet(); });
      final CompletableFuture<Integer> supplier = Async.run(() -> { count.incrementAndGet(); return 7; });
      Async.shutdown();
      assertFalse(runnable.isDone());
      assertFalse(supplier.isDone());
      pool.release.countDown();
      runnable.get(2, TimeUnit.SECONDS);
      assertEquals(7, supplier.get(2, TimeUnit.SECONDS));
      assertTrue(Async.awaitShutdown());
      assertEquals(2, count.get(), "Graceful shutdown must run accepted queue entries exactly once");
    }
  }

  private void taskOutcomes() throws Exception {
    final var count = new AtomicInteger();
    Async.run(() -> { count.incrementAndGet(); }).get(2, TimeUnit.SECONDS);
    assertEquals(7, Async.run(() -> 7).get(2, TimeUnit.SECONDS));
    final var expected = new IllegalStateException("controlled task failure");
    final CompletableFuture<Void> failed = Async.run(() -> { throw expected; });
    final var observed = new AtomicReference<Throwable>();
    failed.whenComplete((value, failure) -> observed.set(failure)).handle((value, failure) -> null).get(2, TimeUnit.SECONDS);
    final CompletionException wrapped = assertInstanceOf(CompletionException.class, observed.get());
    assertSame(expected, wrapped.getCause(), "Preserve CompletableFuture async failure wrapping");
    assertSame(expected, assertThrows(CompletionException.class, failed::join).getCause());
    final CompletableFuture<Integer> failedSupplier = Async.run(() -> { throw expected; });
    assertSame(expected, assertThrows(CompletionException.class, failedSupplier::join).getCause());
    final var existingWrapper = new CompletionException(expected);
    final CompletableFuture<Integer> wrappedSupplier = Async.run(() -> { throw existingWrapper; });
    assertSame(existingWrapper, assertThrows(CompletionException.class, wrappedSupplier::join), "Do not wrap an existing CompletionException again");
    assertEquals(1, count.get());
  }

  private void consumerCancellation() throws Exception {
    try(final var pool = new OccupiedPool()) {
      final var count = new AtomicInteger();
      final CompletableFuture<Void> cancelled = Async.run(() -> { count.incrementAndGet(); });
      assertTrue(cancelled.cancel(false));
      assertTrue(pool.active[0].cancel(false), "An already started operation may be cancelled by its consumer");
      assertEquals(1, pool.release.getCount(), "Consumer cancellation must not release running work");
      pool.release.countDown();
      Async.shutdown();
      assertTrue(Async.awaitShutdown());
      assertTrue(cancelled.isCancelled());
      assertEquals(0, count.get(), "Already cancelled queued work must not start");
    }
  }

  private void rejectionAndNullArguments() {
    assertThrows(NullPointerException.class, () -> Async.run((Runnable)null));
    assertThrows(NullPointerException.class, () -> Async.run((java.util.function.Supplier<Object>)null));
    Async.shutdownNow();
    assertThrows(RejectedExecutionException.class, () -> Async.run(() -> { }));
    assertThrows(RejectedExecutionException.class, () -> Async.run(() -> 7));
  }
}
