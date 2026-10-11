package legend.definitive.pacing;

import legend.core.GameEngine;
import legend.core.platform.SdlPlatformManager;
import legend.core.platform.SdlWindow;
import legend.core.platform.Window;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLError.*;
import static org.lwjgl.sdl.SDLInit.SDL_WasInit;

/** Real SDL tick and complete renderer callback, without creating a native window/context. */
final class WindowPresentationTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"cinematicFrame", "retainedSimulation", "callbackHandoffs", "drawFailure"})
  void currentDrawPrecedesNativePresentation(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED",
      // Child only: permit SDL's safe zero-handle validation, without native window creation.
      "-Dorg.lwjgl.util.NoChecks=true", "-cp", System.getProperty("java.class.path"),
      WindowPresentationTest.class.getName(), scenario)
      .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Presentation fixture must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Presentation fixture process must be retired");
    }
  }

  private static final class Platform extends SdlPlatformManager {
    int clears;
    @Override public void clearPressed() {
      assertFalse(SDL_GetError().isEmpty(), "Input cleanup follows the actual native swap attempt");
      this.clears++;
    }
  }

  private static final class TraceApi extends NoopApi {
    int finalDraws;
    boolean defaultFramebuffer;
    @Override public void unbindFramebuffer() { this.defaultFramebuffer = true; super.unbindFramebuffer(); }
    @Override public FrameBuffer makeFrameBuffer(final String name, final FrameBufferAttachment[] attachments) {
      final FrameBuffer delegate = super.makeFrameBuffer(name, attachments);
      return new FrameBuffer() {
        public void delete() { delegate.delete(); }
        public void bind() { TraceApi.this.defaultFramebuffer = false; delegate.bind(); }
      };
    }
    @Override public Mesh makeMesh(final String name, final VertexOrder order, final float[] vertices, final int[] indices) {
      final Mesh delegate = super.makeMesh(name, order, vertices, indices);
      if(!name.equals("Screen quad")) return delegate;
      return new Mesh() {
        public void update() { delegate.update(); }
        public void delete() { delegate.delete(); }
        public void attribute(final int index, final long offset, final int size, final int stride) { delegate.attribute(index, offset, size, stride); }
        public void draw() {
          // SMAA reuses this quad for offscreen work. Observe only its final backbuffer draw.
          if(!TraceApi.this.defaultFramebuffer) { delegate.draw(); return; }
          assertTrue(SDL_GetError().isEmpty(), "The current frame must complete its final screen draw before native presentation");
          TraceApi.this.finalDraws++; delegate.draw();
        }
        public void draw(final int start, final int count) { delegate.draw(start, count); }
        public float[] vertices() { return delegate.vertices(); }
        public boolean textured() { return delegate.textured(); }
        public boolean translucent() { return delegate.translucent(); }
        public Translucency translucencyMode() { return delegate.translucencyMode(); }
      };
    }
  }

  private static final class HeadlessWindow extends SdlWindow {
    TraceApi api;
    int fps;
    boolean closed;
    private static SdlPlatformManager forbiddenConstruction() { throw new AssertionError("Native SDL construction is forbidden in this fixture"); }
    private HeadlessWindow() { super(forbiddenConstruction(), "unused", 640, 480); }
    @Override public RenderApi getRenderApi() { return this.api; }
    @Override public int getWidth() { return 640; }
    @Override public int getHeight() { return 480; }
    @Override public void setFpsLimit(final int fps) { this.fps = fps; }
    @Override public int getFpsLimit() { return this.fps; }
    @Override public void close() { this.closed = true; }
  }

  private static void set(final Class<?> owner, final Object target, final String name, final Object value) throws Exception {
    final var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }

  private static final class Fixture {
    final Platform platform = new Platform();
    final HeadlessWindow window;
    final TraceApi api = new TraceApi();
    final AtomicLong now = new AtomicLong();
    final RenderEngine renderer = GameEngine.RENDERER;
    final java.lang.reflect.Method tick;
    Fixture() throws Exception {
      assertEquals(0, SDL_WasInit(0), "No SDL subsystem may be initialized");
      // Run only Window's Java constructor, including its real event dispatcher. Bypass
      // every SDL constructor and field initializer. This private fixture requires Java25.
      final Class<?> factoryType = Class.forName("sun.reflect.ReflectionFactory");
      final Object factory = factoryType.getMethod("getReflectionFactory").invoke(null);
      final Constructor<?> constructor = (Constructor<?>)factoryType.getMethod("newConstructorForSerialization", Class.class, Constructor.class)
        .invoke(factory, HeadlessWindow.class, Window.class.getDeclaredConstructor());
      constructor.setAccessible(true); this.window = (HeadlessWindow)constructor.newInstance();
      this.window.api = this.api; set(SdlWindow.class, this.window, "manager", this.platform);
      CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
      ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
      final var clockField = RenderEngine.class.getDeclaredField("simulationClock"); clockField.setAccessible(true);
      final SimulationClock clock = (SimulationClock)clockField.get(this.renderer);
      set(SimulationClock.class, clock, "clock", (LongSupplier)this.now::get); clock.reset(false);
      final var init = RenderEngine.class.getDeclaredMethod("init", Window.class); init.setAccessible(true); init.invoke(this.renderer, this.window);
      GameEngine.GPU.init();
      final var resize = RenderEngine.class.getDeclaredMethod("onResize", Window.class, int.class, int.class);
      resize.setAccessible(true); resize.invoke(this.renderer, this.window, 640, 480);
      this.tick = SdlWindow.class.getDeclaredMethod("tick"); this.tick.setAccessible(true);
    }
    void draw() throws Exception {
      SDL_ClearError(); this.api.defaultFramebuffer = false;
      this.tick.invoke(this.window);
      // SDL rejects the absent handle/subsystem and sets an error. This is our native
      // call-order marker, not a successful graphics swap or a physical latency test.
      assertFalse(SDL_GetError().isEmpty(), "The fixture must reach the actual native SDL presentation call");
      assertEquals(0, SDL_WasInit(0));
      assertFalse(this.window.closed);
    }
  }

  public static void main(final String[] args) throws Exception {
    final Fixture f = new Fixture();
    switch(args[0]) {
      case "cinematicFrame" -> {
        final int[] frames = {0}; f.renderer.setRenderCallback(() -> frames[0]++);
        for(int i = 0; i < 8; i++) f.draw();
        assertEquals(8, frames[0]); assertEquals(8, f.api.finalDraws); assertEquals(8, f.platform.clears);
      }
      case "retainedSimulation" -> {
        final int[] steps = {0}; f.renderer.setSimulationCallback(() -> steps[0]++); f.renderer.setSimulationRate(30);
        f.now.set(33_333_334); // A rate change arms the first future simulation deadline.
        f.draw();
        for(int i = 0; i < 8; i++) { f.now.addAndGet(100_000); f.draw(); }
        assertEquals(1, steps[0]); assertEquals(9, f.api.finalDraws); assertEquals(0, f.platform.clears);
        set(RenderEngine.class, f.renderer, "paused", true);
        for(int i = 0; i < 8; i++) { f.now.addAndGet(100_000_000); f.draw(); }
        assertEquals(1, steps[0]); assertEquals(17, f.api.finalDraws); assertEquals(0, f.platform.clears);
      }
      case "callbackHandoffs" -> {
        final int[] game = {0}, movie = {0};
        f.renderer.setSimulationCallback(() -> game[0]++); f.draw();
        f.renderer.addTask(() -> f.renderer.setRenderCallback(() -> movie[0]++)); f.draw();
        assertEquals(1, game[0]); assertEquals(1, movie[0]); assertEquals(1, f.platform.clears);
        f.renderer.addTask(() -> f.renderer.setSimulationCallback(() -> game[0]++)); f.draw();
        assertEquals(2, game[0]); assertEquals(1, movie[0]); assertEquals(1, f.platform.clears); assertEquals(3, f.api.finalDraws);
      }
      case "drawFailure" -> {
        f.renderer.setRenderCallback(() -> { throw new IllegalStateException("Controlled draw failure"); });
        SDL_ClearError();
        final var failure = assertThrows(InvocationTargetException.class, () -> f.tick.invoke(f.window));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(SDL_GetError().isEmpty(), "A failed draw must not present an incomplete frame");
        assertEquals(0, f.api.finalDraws); assertEquals(0, f.platform.clears);
      }
      default -> throw new IllegalArgumentException("Unknown presentation scenario");
    }
    assertEquals(0, SDL_WasInit(0), "Fixture must never initialize SDL video/audio or create a native window");
    System.out.println("PASS " + args[0]);
  }
}
