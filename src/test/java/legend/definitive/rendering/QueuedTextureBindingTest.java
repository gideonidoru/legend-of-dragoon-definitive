package legend.definitive.rendering;

import legend.core.renderer.*;

import legend.core.GameEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;

class QueuedTextureBindingTest {
  private record Scenario(String type, String setup) { }

  static Stream<Scenario> cases() {
    return Stream.of("standard", "tmd", "battle").flatMap(type -> Stream.of(
      "native", "albedo", "otherUnit", "private", "albedoPrivate", "explicitGlobal",
      "reusedNative", "reusedAlbedo", "uiAlbedo").map(setup -> new Scenario(type, setup)));
  }

  @ParameterizedTest @MethodSource("cases")
  void eachDrawSelectsItsNativeOwner(final Scenario scenario) throws Exception {
    final Texture previous = GameEngine.GPU.vramTexture15;
    final Texture[] bound = new Texture[32];
    final TrackingTexture global = new TrackingTexture("global", bound);
    final TrackingTexture privateVram = new TrackingTexture("private", bound);
    final TrackingTexture albedo = new TrackingTexture("albedo", bound);
    final MeshObj obj = new MeshObj("binding fixture", new Mesh[0], false);
    try {
      GameEngine.GPU.vramTexture15 = global;
      final RenderBatch batch = new RenderBatch(GameEngine.RENDERER, FloatBuffer.allocate(4096));
      final QueuedModel<?, ?> queue = switch(scenario.type) {
        case "standard" -> new QueuedModelStandard(batch, null, null, FloatBuffer.allocate(4096));
        case "tmd" -> new QueuedModelTmd(batch, null, null, FloatBuffer.allocate(4096));
        case "battle" -> new QueuedModelBattleTmd(batch, null, null, FloatBuffer.allocate(4096));
        default -> throw new AssertionError(scenario.type);
      };
      acquire(queue, obj, 0);
      privateVram.use(1);
      if(scenario.setup.startsWith("reused")) {
        queue.texture(privateVram, 1);
        useTextures(queue);
        acquire(queue, obj, 1);
      }
      switch(scenario.setup) {
        case "albedo", "reusedAlbedo", "uiAlbedo" -> queue.texture(albedo);
        case "otherUnit" -> queue.texture(albedo, 4);
        case "private" -> queue.texture(privateVram, 1);
        case "albedoPrivate" -> { queue.texture(albedo); queue.texture(privateVram, 1); }
        case "explicitGlobal" -> queue.texture(global, 1);
      }
      if(scenario.setup.equals("uiAlbedo")) queue.ui();
      useTextures(queue);
      final boolean explicitPrivate = scenario.setup.equals("private") || scenario.setup.equals("albedoPrivate");
      assertSame(explicitPrivate ? privateVram : global, bound[1], "Native image and CLUT must belong to this draw");
      if(scenario.setup.contains("Albedo") || scenario.setup.equals("albedo") || scenario.setup.equals("albedoPrivate")) assertSame(albedo, bound[0]);
      if(scenario.setup.equals("otherUnit")) assertSame(albedo, bound[4]);
    } finally {
      GameEngine.GPU.vramTexture15 = previous;
      obj.delete(); Obj.deleteObjects();
      global.delete(); privateVram.delete(); albedo.delete(); Texture.deleteTextures();
    }
  }

  private static void acquire(final QueuedModel<?, ?> queue, final Obj obj, final int sequence) throws Exception {
    final var method = QueuedModel.class.getDeclaredMethod("acquire", Obj.class, int.class);
    method.setAccessible(true); method.invoke(queue, obj, sequence);
  }

  private static void useTextures(final QueuedModel<?, ?> queue) throws Exception {
    final var method = QueuedModel.class.getDeclaredMethod("useTexture");
    method.setAccessible(true); method.invoke(queue);
  }

  private static final class TrackingTexture extends Texture {
    private final Texture[] bound;
    TrackingTexture(final String name, final Texture[] bound) { super(name, 1, 1); this.bound = bound; }
    @Override public void use(final int unit) { this.bound[unit] = this; }
    @Override public void use() { this.use(0); }
    @Override public void data(final int x, final int y, final int w, final int h, final TextureDataType type, final ByteBuffer data) { }
    @Override public void data(final int x, final int y, final int w, final int h, final TextureDataType type, final int[] data) { }
    @Override public TextureInternalFormat internalFormat() { return TextureInternalFormat.R_32_UINT; }
    @Override public TextureDataFormat dataFormat() { return TextureDataFormat.RED_INT; }
    @Override public TextureDataType dataType() { return TextureDataType.UINT; }
    @Override public boolean minFilter() { return false; }
    @Override public boolean magFilter() { return false; }
    @Override public boolean wrapS() { return false; }
    @Override public boolean wrapT() { return false; }
    @Override protected void performDelete() { }
  }
}
