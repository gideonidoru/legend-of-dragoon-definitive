package legend.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class Async {
  private Async() { }

  private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(AVAILABLE_PROCESSORS);

  public static CompletableFuture<Void> run(final Runnable task) {
    Objects.requireNonNull(task);
    return run(() -> {
      task.run();
      return null;
    });
  }

  public static <T> CompletableFuture<T> run(final Supplier<T> task) {
    final var pending = new PendingTask<>(task);
    EXECUTOR.execute(pending);
    return pending.future;
  }

  /** Keeps a discarded queue entry connected to the completion observed by its callers. */
  private static final class PendingTask<T> implements Runnable {
    private final Supplier<T> task;
    private final CompletableFuture<T> future = new CompletableFuture<>();

    private PendingTask(final Supplier<T> task) {
      this.task = Objects.requireNonNull(task);
    }

    @Override public void run() {
      if(this.future.isDone()) return;
      try {
        this.future.complete(this.task.get());
      } catch(final Throwable failure) {
        // Match CompletableFuture's async failure contract, including dependent callbacks.
        this.future.completeExceptionally(failure instanceof CompletionException ? failure : new CompletionException(failure));
      }
    }
  }

  public static void shutdown() {
    EXECUTOR.shutdown();
  }

  public static void shutdownNow() {
    // This private executor accepts only PendingTask entries. Started work owns its completion.
    for(final Runnable discarded : EXECUTOR.shutdownNow()) {
      ((PendingTask<?>)discarded).future.cancel(false);
    }
  }

  public static boolean awaitShutdown() throws InterruptedException {
    return EXECUTOR.awaitTermination(5, TimeUnit.SECONDS);
  }
}
