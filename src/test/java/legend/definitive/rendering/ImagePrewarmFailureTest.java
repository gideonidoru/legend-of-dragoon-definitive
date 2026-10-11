package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.gpu.Rect4i;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.modding.coremod.CoreMod;
import legend.game.modding.events.submap.SubmapEnvironmentPreloadEvent;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.submap.CollisionGeometry;
import legend.game.submap.EnvironmentRenderingMetrics24;
import legend.game.submap.RetailSubmap;
import legend.game.submap.SMap;
import legend.game.tim.Tim;
import legend.game.types.NewRootStruct;
import legend.game.unpacker.FileData;
import org.joml.Vector2f;
import org.legendofdragoon.modloader.registries.RegistryId;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real preparation workers and scene fallback, without a window, graphics context or game assets. */
final class ImagePrewarmFailureTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"pngSupplierError", "pngReadError", "pngCloseError", "pngRuntimeFailure", "nativeDecodeError", "nativeRuntimeFailure", "retailFallback"})
  void actualPreparationContract(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      ImagePrewarmFailureTest.class.getName(), scenario).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Preparation fixture must finish within its bounded deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Preparation child must retire");
    }
  }

  public static void main(final String[] args) throws Exception {
    ImagePrewarmFailureTest.class.getDeclaredMethod(args[0]).invoke(new ImagePrewarmFailureTest());
    System.out.println("PASS " + args[0]);
  }

  private static void failed(final CompletableFuture<?> preparation, final Throwable expected) throws Exception {
    final ExecutionException failure = assertThrows(ExecutionException.class, () -> preparation.get(2, TimeUnit.SECONDS),
      "An accepted preparation error must settle its future, not wait indefinitely");
    assertSame(expected, failure.getCause(), "Retain the original worker failure");
  }

  private static void pngHealthy(final PngAssets cache) throws Exception {
    final byte[] png = PngAssetsTest.png(20, 40, 80, 0);
    assertTrue(cache.prewarm(() -> new ByteArrayInputStream(png)).get(2, TimeUnit.SECONDS));
    try(final var image = cache.acquire(ByteBuffer.wrap(png))) {
      assertEquals(20, image.pixels().get(0) & 255);
      assertEquals(40, image.pixels().get(1) & 255);
      assertEquals(80, image.pixels().get(2) & 255);
      assertEquals(0, image.pixels().get(3), "Transparent RGB/STP must remain exact");
    }
    cache.clear();
    assertEquals(0, cache.stats().liveDecodedBytes(), "Retire the subsequent healthy decode lease and cache");
  }

  private void pngSupplierError() throws Exception {
    try(final var cache = new PngAssets(4)) {
      final var expected = new AssertionError("controlled resource-provider error");
      final var preparation = cache.prewarm(() -> { throw expected; });
      final var event = new SubmapEnvironmentPreloadEvent(null, null, null, 1, 31);
      event.waitFor(preparation);
      final var ready = event.preparation();
      failed(preparation, expected);
      ready.get(2, TimeUnit.SECONDS);
      assertEquals(0, cache.stats().liveDecodedBytes());
      pngHealthy(cache);
    }
  }

  private void pngReadError() throws Exception {
    try(final var cache = new PngAssets(4)) {
      final var expected = new AssertionError("controlled compressed-read error");
      final var closeFailure = new AssertionError("controlled stream-close error");
      final var closed = new AtomicBoolean();
      final var preparation = cache.prewarm(() -> new InputStream() {
        @Override public int read() { throw expected; }
        @Override public int read(final byte[] data, final int offset, final int count) { throw expected; }
        @Override public void close() { closed.set(true); throw closeFailure; }
      });
      failed(preparation, expected);
      assertTrue(closed.get(), "Stream retirement must occur before failure completion");
      assertArrayEquals(new Throwable[]{closeFailure}, expected.getSuppressed());
      assertEquals(0, cache.stats().liveDecodedBytes());
      pngHealthy(cache);
    }
  }

  private void pngCloseError() throws Exception {
    try(final var cache = new PngAssets(4)) {
      final var expected = new AssertionError("controlled stream-close error after decode");
      final byte[] png = PngAssetsTest.png(1, 2, 3, 255);
      final var preparation = cache.prewarm(() -> new ByteArrayInputStream(png) {
        @Override public void close() { throw expected; }
      });
      failed(preparation, expected);
      assertEquals(4, cache.stats().cachedBytes());
      cache.clear();
      assertEquals(0, cache.stats().liveDecodedBytes(), "The successful decode lease must close before stream-close failure settles");
      pngHealthy(cache);
    }
  }

  private void pngRuntimeFailure() throws Exception {
    try(final var cache = new PngAssets(4)) {
      final var expected = new IllegalStateException("controlled ordinary resource failure");
      failed(cache.prewarm(() -> { throw expected; }), expected);
      pngHealthy(cache);
    }
  }

  private static NativeEnvironmentImages.Tile[] failingNative(final Throwable expected) {
    final Tim source = new Tim(new FileData(new byte[0])) {
      @Override public Rect4i getImageRect() {
        if(expected instanceof Error error) throw error;
        throw (RuntimeException)expected;
      }
    };
    return new NativeEnvironmentImages.Tile[]{new NativeEnvironmentImages.Tile(source, 0, 0, 1, 1, false)};
  }

  private void nativeDecodeError() throws Exception {
    final var expected = new AssertionError("controlled native-image preparation error");
    failed(NativeEnvironmentImages.prewarm(failingNative(expected)), expected);
    assertEquals(0, NativeEnvironmentImages.prewarm(new NativeEnvironmentImages.Tile[0]).get(2, TimeUnit.SECONDS).length);
  }

  private void nativeRuntimeFailure() throws Exception {
    final var expected = new IllegalArgumentException("controlled native-image input failure");
    failed(NativeEnvironmentImages.prewarm(failingNative(expected)), expected);
    assertEquals(0, NativeEnvironmentImages.prewarm(new NativeEnvironmentImages.Tile[0]).get(2, TimeUnit.SECONDS).length);
  }

  private static java.lang.reflect.Field field(final String name) throws Exception {
    final var field = RetailSubmap.class.getDeclaredField(name); field.setAccessible(true); return field;
  }

  private void retailFallback() throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    GameEngine.CONFIG.setConfig(CoreMod.IMAGE_CACHE_CONFIG.get(), true);
    ((EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes).register(new RegistryId("lod", "submap"), new EngineStateType<>(SMap.class, SMap::new));
    final SMap scene = new SMap();
    final var submap = new RetailSubmap(scene, 0, new NewRootStruct(new FileData(new byte[0x38f0])), new Vector2f(), new CollisionGeometry(scene));
    final var expected = new AssertionError("controlled worker-only environment error");
    final var calls = new AtomicInteger();
    final Tim source = new Tim(new FileData(new byte[0])) {
      @Override public Rect4i getImageRect() {
        if(calls.incrementAndGet() > 1) throw expected;
        return new Rect4i(0, 0, 4, 2);
      }
    };
    field("envTextures").set(submap, new Tim[]{source});
    field("envTextureCount_800cb584").setInt(submap, 1);
    final var metrics = (EnvironmentRenderingMetrics24[])field("envRenderMetrics_800cb710").get(submap);
    metrics[0].w_18 = metrics[0].h_1a = 1;
    final var prepare = RetailSubmap.class.getDeclaredMethod("prewarmEnvironment", int.class); prepare.setAccessible(true);
    final var ready = (CompletableFuture<?>)prepare.invoke(submap, 0);
    ready.get(2, TimeUnit.SECONDS);
    failed((CompletableFuture<?>)field("environmentPreparation").get(submap), expected);
    assertNull(field("preparedEnvironment").get(submap), "Actual RetailSubmap must retain ordinary preparation after optional worker failure");
    assertEquals(2, calls.get(), "Failure must occur on the actual worker after source selection");
  }
}
