package legend.definitive.loading;

import legend.core.Async;
import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.platform.input.InputActionRegistry;
import legend.core.platform.input.InputActionRegistryEvent;
import legend.core.renderer.RenderEngine;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.EngineStates;
import legend.game.combat.Battle;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.unpacker.Loader;
import legend.lodmod.LodMod;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static legend.game.Scus94491BpeSegment.battlePreloadedEntities_1f8003f4;
import static org.junit.jupiter.api.Assertions.*;

/** Actual battle/loader/parser/render callbacks; synthetic files, no native window or audio. */
final class BattleStageLoadingReliabilityTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"missingStageFailsOnRenderer", "missingModelFailsOnRenderer",
    "malformedTimFailsOnRenderer", "malformedModelFailsOnRenderer", "emptyStageWaitsForRenderer",
    "healthyTimWaitsForRenderer", "staleOwnerFailureIsIgnored", "supersededFailureIsIgnored",
    "destroyedStageFailureIsIgnored", "staleEngineSuccessIsIgnored", "staleEngineFailureIsIgnored"})
  void actualBattleStageContract(final String scenario) throws Exception {
    final Path root = Files.createDirectory(this.temporary.resolve(scenario));
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      BattleStageLoadingReliabilityTest.class.getName(), scenario, root.toString())
      .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Battle fixture must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Battle fixture process must retire");
    }
  }

  public static void main(final String[] args) throws Exception {
    registries();
    final var fixture = new BattleStageLoadingReliabilityTest();
    fixture.temporary = Path.of(args[1]);
    try {
      BattleStageLoadingReliabilityTest.class.getDeclaredMethod(args[0]).invoke(fixture);
      System.out.println("PASS " + args[0]);
    } finally {
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown(), "Fixture must retire actual loader work");
    }
  }

  private static void registries() throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    CoreMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    LodMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    ((EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes).register(new RegistryId("lod", "battle"), new EngineStateType<>(Battle.class, Battle::new));
  }

  private static void set(final Battle owner, final String name, final Object value) throws Exception {
    final var field = Battle.class.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
  }

  private static Object get(final Battle owner, final String name) throws Exception {
    final var field = Battle.class.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
  }

  private static Battle battle() throws Exception {
    final Battle battle = new Battle(); EngineStates.currentEngineState_8004dd04 = battle;
    battle.FUN_800c8624(); battle.allocateStageDarkeningStorage();
    set(battle, "stageHasModel_800c66b8", true);
    return battle;
  }

  private void file(final String name, final byte[] bytes) throws Exception {
    final Path path = this.temporary.resolve("files/SECT/DRGN0.BIN").resolve(name);
    Files.createDirectories(path.getParent()); Files.write(path, bytes);
  }

  private void emptyStage(final int stage) throws Exception {
    final String path = Integer.toString(2497 + stage);
    for(int i = 0; i < 3; i++) this.file(path + "/0/" + i, new byte[0]);
    this.file(path + "/1", new byte[0]); this.file(path + "/2", new byte[0]);
    this.file(path + "/mrg", "0=0;0\n1=1;0\n2=2;0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static void settleLoader() throws Exception {
    Async.shutdown();
    assertTrue(Async.awaitShutdown(), "Actual directory loading must settle within five seconds");
    assertEquals(0, Loader.getLoadingFileCount(), "I/O bookkeeping must retire on success and failure");
  }

  private static NoopWindow rendererWindow;

  private static void rendererTasks() throws Exception {
    if(rendererWindow == null) {
      rendererWindow = new NoopWindow(new NoopPlatformManager(), 640, 480);
      final var init = RenderEngine.class.getDeclaredMethod("init", Window.class);
      init.setAccessible(true); init.invoke(GameEngine.RENDERER, rendererWindow); GameEngine.GPU.init();
      final var resize = RenderEngine.class.getDeclaredMethod("onResize", Window.class, int.class, int.class);
      resize.setAccessible(true); resize.invoke(GameEngine.RENDERER, rendererWindow, 640, 480);
    }
    final var draw = NoopWindow.class.getDeclaredMethod("tick"); draw.setAccessible(true);
    try { draw.invoke(rendererWindow); }
    catch(final InvocationTargetException failure) {
      if(failure.getCause() instanceof RuntimeException problem) throw problem;
      if(failure.getCause() instanceof Error problem) throw problem;
      throw failure;
    }
  }

  private static void failsOnRenderer() {
    final IllegalStateException failure = assertThrows(IllegalStateException.class,
      BattleStageLoadingReliabilityTest::rendererTasks, "Failed required scenery must reach the engine error path");
    assertTrue(failure.getMessage().contains("battle stage"), failure.toString());
    assertTrue(failure.getMessage().contains("DRGN0/2497"), "Failure must identify the required resource");
    assertNotNull(failure.getCause(), "Original directory or parser failure must be retained");
  }

  private void missingStageFailsOnRenderer() throws Exception {
    battle().loadStage(0); rendererTasks(); settleLoader(); failsOnRenderer();
  }

  private void missingModelFailsOnRenderer() throws Exception {
    this.file("2497/0", new byte[0]); this.file("2497/1", new byte[0]); this.file("2497/2", new byte[0]);
    battle().loadStage(0); rendererTasks(); settleLoader(); failsOnRenderer();
  }

  private void malformedTimFailsOnRenderer() throws Exception {
    this.emptyStage(0); this.file("2497/2", new byte[4]);
    battle().loadStage(0); rendererTasks(); settleLoader(); failsOnRenderer();
  }

  private void malformedModelFailsOnRenderer() throws Exception {
    this.emptyStage(0);
    for(int i = 0; i < 3; i++) this.file("2497/0/" + i, new byte[4]);
    battle().loadStage(0); rendererTasks(); settleLoader(); failsOnRenderer();
  }

  private void emptyStageWaitsForRenderer() throws Exception {
    this.emptyStage(0); final Battle battle = battle(); battle.loadStage(0);
    assertEquals(true, get(battle, "stageHasModel_800c66b8"));
    rendererTasks(); settleLoader();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"), "Worker must not commit scene state");
    rendererTasks();
    assertEquals(false, get(battle, "stageHasModel_800c66b8"), "Valid native empty-stage records remain supported");
  }

  private void healthyTimWaitsForRenderer() throws Exception {
    this.emptyStage(0); this.file("2497/2", stageTim());
    final Battle battle = battle(); battle.loadStage(0);
    rendererTasks(); settleLoader();
    assertEquals(0, Battle.stageDarkening_800c6958.original_000[0][0]);
    assertEquals(0, GameEngine.GPU.getPixel15(448, 240), "Worker must not upload the palette");
    rendererTasks();
    assertEquals(0x1234, Battle.stageDarkening_800c6958.original_000[0][0]);
    assertEquals(0x0456, Battle.stageDarkening_800c6958.original_000[15][63]);
    assertEquals(0x1234, GameEngine.GPU.getPixel15(448, 240));
    assertEquals(0x0456, GameEngine.GPU.getPixel15(511, 255));
    assertEquals(false, get(battle, "stageHasModel_800c66b8"));
  }

  private static byte[] stageTim() {
    final ByteBuffer tim = ByteBuffer.allocate(0x8820).order(ByteOrder.LITTLE_ENDIAN);
    tim.putInt(16).putInt(8).putInt(2060).putShort((short)448).putShort((short)240).putShort((short)64).putShort((short)16);
    tim.putShort((short)0x1234); tim.putShort(0x14 + 2046, (short)0x0456);
    tim.position(2068); tim.putInt(32780).putShort((short)576).putShort((short)256).putShort((short)64).putShort((short)256);
    return tim.array();
  }

  private void staleOwnerFailureIsIgnored() throws Exception {
    final Battle battle = battle(); battle.loadStage(0); rendererTasks(); settleLoader();
    battle.FUN_800c8624(); rendererTasks();
    assertNotNull(battlePreloadedEntities_1f8003f4);
  }

  private void supersededFailureIsIgnored() throws Exception {
    this.emptyStage(1); final Battle battle = battle(); battle.loadStage(0); battle.loadStage(1);
    rendererTasks(); settleLoader(); rendererTasks();
    assertEquals(1, get(battle, "currentStage_800c66a4"));
    assertEquals(false, get(battle, "stageHasModel_800c66b8"), "New healthy stage must survive obsolete failure");
  }

  private void destroyedStageFailureIsIgnored() throws Exception {
    final Battle battle = battle(); battle.loadStage(0); rendererTasks(); settleLoader();
    battle.FUN_800c8748(); rendererTasks(); assertNull(battlePreloadedEntities_1f8003f4);
  }

  private void staleEngineSuccessIsIgnored() throws Exception {
    this.emptyStage(0); this.file("2497/2", stageTim());
    final Battle battle = battle(); battle.loadStage(0); rendererTasks(); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"), "A replaced engine must not receive old scenery state");
    assertEquals(0, Battle.stageDarkening_800c6958.original_000[0][0]);
    assertEquals(0, GameEngine.GPU.getPixel15(448, 240), "A replaced engine must not receive an old palette upload");
  }

  private void staleEngineFailureIsIgnored() throws Exception {
    final Battle battle = battle(); battle.loadStage(0); rendererTasks(); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"));
  }
}
