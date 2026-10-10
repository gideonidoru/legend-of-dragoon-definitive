package legend.definitive.artwork;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StageLoadTest {
  @Test void bothCompletionOrdersWaitForMatchingFilesAndSceneThread() {
    for(final boolean modelFirst : new boolean[]{true, false}) {
      final var model = new CompletableFuture<String>();
      final var texture = new CompletableFuture<String>();
      final var scene = new ArrayDeque<Runnable>();
      final var calls = new AtomicInteger();
      final var result = StageLoad.apply(model, texture, scene::add, () -> true, (m, t) -> {
        assertEquals("model", m); assertEquals("texture", t); calls.incrementAndGet();
      });
      if(modelFirst) model.complete("model"); else texture.complete("texture");
      assertTrue(scene.isEmpty());
      if(modelFirst) texture.complete("texture"); else model.complete("model");
      assertEquals(0, calls.get()); assertFalse(result.isDone());
      scene.remove().run(); assertEquals(1, calls.get()); assertTrue(result.isDone());
    }
  }

  @Test void sceneChangedAfterLoadingDoesNotInstallObsoleteResources() {
    final var scene = new ArrayDeque<Runnable>(); final var current = new AtomicBoolean(true);
    final var calls = new AtomicInteger();
    final var result = StageLoad.apply(CompletableFuture.completedFuture(1), CompletableFuture.completedFuture(2), scene::add, current::get, (m, t) -> calls.incrementAndGet());
    current.set(false); scene.remove().run();
    assertTrue(result.isDone()); assertEquals(0, calls.get());
  }

  @Test void failedInputDoesNotAllocateOrQueueSceneWork() {
    final var scene = new ArrayDeque<Runnable>();
    final var result = StageLoad.apply(CompletableFuture.failedFuture(new IllegalStateException("missing model")), CompletableFuture.completedFuture(2), scene::add, () -> true, (m, t) -> fail());
    assertTrue(result.isCompletedExceptionally()); assertTrue(scene.isEmpty());
  }
}
