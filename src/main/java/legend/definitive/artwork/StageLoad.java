package legend.definitive.artwork;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Joins matching model/texture loads and applies them only on the scene executor. */
public final class StageLoad {
  private StageLoad() { }

  public static <M, T> CompletableFuture<Void> apply(final CompletableFuture<M> model, final CompletableFuture<T> texture, final Executor scene, final BooleanSupplier current, final BiConsumer<M, T> install) {
    return model.thenCombine(texture, Pair::new).thenAcceptAsync(pair -> {
      if(current.getAsBoolean()) {
        install.accept(pair.model(), pair.texture());
      }
    }, scene);
  }

  private record Pair<M, T>(M model, T texture) { }
}
