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
import legend.game.combat.encounters.Encounter;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.unpacker.Loader;
import legend.lodmod.LodMod;
import org.joml.Vector3f;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

import static legend.game.Scus94491BpeSegment.battlePreloadedEntities_1f8003f4;
import static legend.game.Scus94491BpeSegment.simpleRand;
import static legend.game.Scus94491BpeSegment_8004.simpleRandSeed_8004dd44;
import static legend.game.Graphics.vsyncMode_8007a3b8;
import static legend.game.Scus94491BpeSegment_800b.battleFlags_800bc960;
import static legend.game.Scus94491BpeSegment_800b.encounter;
import static org.junit.jupiter.api.Assertions.*;

/** Actual battle/loader/parser/render callbacks; synthetic files, no native window or audio. */
final class BattleStageLoadingReliabilityTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"missingStageFailsOnRenderer", "missingModelFailsOnRenderer",
    "malformedTimFailsOnRenderer", "malformedModelFailsOnRenderer", "emptyStageWaitsForRenderer",
    "healthyTimWaitsForRenderer", "staleOwnerFailureIsIgnored", "supersededFailureIsIgnored",
    "destroyedStageFailureIsIgnored", "staleEngineSuccessIsIgnored", "staleEngineFailureIsIgnored",
    "missingControllerFailsOnRenderer", "malformedControllerFailsOnRenderer", "controllerSuccessWaitsForRenderer",
    "staleControllerEngineSuccessIsIgnored", "staleControllerOwnerSuccessIsIgnored", "destroyedControllerSuccessIsIgnored",
    "supersededControllerFailureIsIgnored", "supersededControllerSuccessCannotMaskFailure",
    "missingPlayerScriptFailsSynchronously", "controllerReadinessAdvancesExistingViewportGate",
    "staleControllerEngineFailureIsIgnored", "staleControllerOwnerFailureIsIgnored", "destroyedControllerFailureIsIgnored"})
  void actualBattleStageContract(final String scenario) throws Exception {
    final Path root = Files.createDirectory(this.temporary.resolve(scenario));
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-XX:ActiveProcessorCount=2", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
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
    this.rootFile("SECT/DRGN0.BIN/" + name, bytes);
  }

  private void rootFile(final String name, final byte[] bytes) throws Exception {
    final Path path = this.temporary.resolve("files").resolve(name);
    Files.createDirectories(path.getParent()); Files.write(path, bytes);
  }

  private void emptyStage(final int stage) throws Exception {
    final String path = Integer.toString(2497 + stage);
    for(int i = 0; i < 3; i++) this.file(path + "/0/" + i, new byte[0]);
    this.file(path + "/1", new byte[0]); this.file(path + "/2", new byte[0]);
    this.file(path + "/mrg", "0=0;0\n1=1;0\n2=2;0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** Start deferred stage reads, but release their workers only after the initial draw has returned. */
  private static void startStageLoadsOnRenderer() throws Exception {
    final int workers = loaderExecutor().getCorePoolSize();
    final CountDownLatch waiting = new CountDownLatch(workers);
    final CountDownLatch release = new CountDownLatch(1);
    final var blockers = new ArrayList<CompletableFuture<Void>>();
    try {
      for(int i = 0; i < workers; i++) {
        blockers.add(Async.run(() -> {
          waiting.countDown();
          try {
            assertTrue(release.await(5, TimeUnit.SECONDS), "Stage fixture must release its worker barrier");
          } catch(final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stage fixture worker was interrupted", failure);
          }
        }));
      }
      assertTrue(waiting.await(5, TimeUnit.SECONDS), "Every actual loader worker must reach the fixture barrier");
      rendererTasks();
    } finally {
      release.countDown();
    }
    CompletableFuture.allOf(blockers.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
  }

  private static ThreadPoolExecutor loaderExecutor() throws Exception {
    final var field = Async.class.getDeclaredField("EXECUTOR"); field.setAccessible(true);
    return (ThreadPoolExecutor)field.get(null);
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
    battle().loadStage(0); startStageLoadsOnRenderer(); settleLoader(); failsOnRenderer();
  }

  private void missingModelFailsOnRenderer() throws Exception {
    this.file("2497/0", new byte[0]); this.file("2497/1", new byte[0]); this.file("2497/2", new byte[0]);
    battle().loadStage(0); startStageLoadsOnRenderer(); settleLoader(); failsOnRenderer();
  }

  private void malformedTimFailsOnRenderer() throws Exception {
    this.emptyStage(0); this.file("2497/2", new byte[4]);
    battle().loadStage(0); startStageLoadsOnRenderer(); settleLoader(); failsOnRenderer();
  }

  private void malformedModelFailsOnRenderer() throws Exception {
    this.emptyStage(0);
    for(int i = 0; i < 3; i++) this.file("2497/0/" + i, new byte[4]);
    battle().loadStage(0); startStageLoadsOnRenderer(); settleLoader(); failsOnRenderer();
  }

  private void emptyStageWaitsForRenderer() throws Exception {
    this.emptyStage(0); final Battle battle = battle(); battle.loadStage(0);
    assertEquals(true, get(battle, "stageHasModel_800c66b8"));
    startStageLoadsOnRenderer(); settleLoader();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"), "Worker must not commit scene state");
    rendererTasks();
    assertEquals(false, get(battle, "stageHasModel_800c66b8"), "Valid native empty-stage records remain supported");
  }

  private void healthyTimWaitsForRenderer() throws Exception {
    this.emptyStage(0); this.file("2497/2", stageTim());
    final Battle battle = battle(); battle.loadStage(0);
    startStageLoadsOnRenderer(); settleLoader();
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
    final Battle battle = battle(); battle.loadStage(0); startStageLoadsOnRenderer(); settleLoader();
    battle.FUN_800c8624(); rendererTasks();
    assertNotNull(battlePreloadedEntities_1f8003f4);
  }

  private void supersededFailureIsIgnored() throws Exception {
    this.emptyStage(1); final Battle battle = battle(); battle.loadStage(0); battle.loadStage(1);
    startStageLoadsOnRenderer(); settleLoader(); rendererTasks();
    assertEquals(1, get(battle, "currentStage_800c66a4"));
    assertEquals(false, get(battle, "stageHasModel_800c66b8"), "New healthy stage must survive obsolete failure");
  }

  private void destroyedStageFailureIsIgnored() throws Exception {
    final Battle battle = battle(); battle.loadStage(0); startStageLoadsOnRenderer(); settleLoader();
    battle.FUN_800c8748(); rendererTasks(); assertNull(battlePreloadedEntities_1f8003f4);
  }

  private void staleEngineSuccessIsIgnored() throws Exception {
    this.emptyStage(0); this.file("2497/2", stageTim());
    final Battle battle = battle(); battle.loadStage(0); startStageLoadsOnRenderer(); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"), "A replaced engine must not receive old scenery state");
    assertEquals(0, Battle.stageDarkening_800c6958.original_000[0][0]);
    assertEquals(0, GameEngine.GPU.getPixel15(448, 240), "A replaced engine must not receive an old palette upload");
  }

  private void staleEngineFailureIsIgnored() throws Exception {
    final Battle battle = battle(); battle.loadStage(0); startStageLoadsOnRenderer(); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks();
    assertEquals(true, get(battle, "stageHasModel_800c66b8"));
  }

  private static void invoke(final Battle battle, final String method) throws Exception {
    final var call = Battle.class.getDeclaredMethod(method); call.setAccessible(true);
    try { call.invoke(battle); }
    catch(final InvocationTargetException failure) {
      if(failure.getCause() instanceof RuntimeException problem) throw problem;
      if(failure.getCause() instanceof Error problem) throw problem;
      throw failure;
    }
  }

  private Battle controllerBattle(final boolean healthy) throws Exception {
    this.rootFile("player_combat_script", new byte[4]);
    if(healthy) this.rootFile("SECT/DRGN1.BIN/401", new byte[4]);
    encounter = new Encounter(255, 0, 11, 29, 3, 5, 7, 9, 65535, 255,
      new Encounter.Monster(0, new Vector3f()));
    battleFlags_800bc960 = 0; simpleRandSeed_8004dd44 = 0x345;
    return battle();
  }

  private static void startController(final Battle battle) throws Exception {
    invoke(battle, "loadStageDataAndControllerScripts");
  }

  /** Observe actual worker retirement without closing the executor before a superseding load. */
  private static void settleWorkers() throws Exception {
    final ThreadPoolExecutor executor = loaderExecutor();
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while(executor.getActiveCount() != 0 || !executor.getQueue().isEmpty() || Loader.getLoadingFileCount() != 0) {
      assertTrue(System.nanoTime() < deadline, "Actual controller loader/callbacks must retire within five seconds");
      Thread.sleep(1);
    }
  }

  private static void controllerNotPublished() {
    assertEquals(0, battleFlags_800bc960 & 0x2, "Worker or obsolete completion must not publish battle readiness");
    assertNull(GameEngine.SCRIPTS.getState(5), "Worker or obsolete completion must not allocate a controller script");
    assertEquals(0x345, simpleRandSeed_8004dd44, "Worker or obsolete completion must not consume gameplay RNG");
  }

  private static void controllerFailsOnRenderer() {
    final IllegalStateException failure = assertThrows(IllegalStateException.class,
      BattleStageLoadingReliabilityTest::rendererTasks, "Required controller failure must reach the engine instead of hanging entry");
    assertTrue(failure.getMessage().contains("battle controller"));
    assertTrue(failure.getMessage().contains("DRGN1/401"));
    assertNotNull(failure.getCause(), "Original controller read or script failure must be retained");
    assertEquals(0, battleFlags_800bc960 & 0x2, "Failed preparation must not report the controller ready");
  }

  private void missingControllerFailsOnRenderer() throws Exception {
    final Battle battle = this.controllerBattle(false); set(battle, "loadingStage", 4);
    startController(battle); settleLoader();
    battleFlags_800bc960 |= 1; invoke(battle, "initializeViewportAndCamera");
    assertEquals(4, get(battle, "loadingStage"), "Missing controller leaves the real viewport gate waiting");
    controllerFailsOnRenderer();
  }

  private void malformedControllerFailsOnRenderer() throws Exception {
    final Battle battle = this.controllerBattle(false); this.rootFile("SECT/DRGN1.BIN/401", new byte[0]);
    startController(battle); settleLoader(); controllerFailsOnRenderer();
  }

  private void controllerSuccessWaitsForRenderer() throws Exception {
    final Battle battle = this.controllerBattle(true);
    final int opening = (simpleRand() & 0x8000) == 0 ? encounter.monsterOpeningCamera : encounter.playerOpeningCamera;
    final int position = simpleRand() & 3, expectedSeed = simpleRandSeed_8004dd44;
    simpleRandSeed_8004dd44 = 0x345;
    startController(battle); settleLoader(); controllerNotPublished(); rendererTasks();
    assertEquals(2, battleFlags_800bc960); assertNotNull(GameEngine.SCRIPTS.getState(5));
    assertSame(GameEngine.SCRIPTS.getState(5), get(battle, "scriptState_800c674c"));
    assertEquals(opening + 1, battle.cameraScriptMainTableJumpIndex_800c6748);
    assertEquals(position, battle.hud.currentCameraPositionIndicesIndex_800c66b0);
    assertEquals(encounter.cameraPosIndices[position], battle.currentCameraIndex_800c6780);
    assertEquals(expectedSeed, simpleRandSeed_8004dd44, "Healthy camera selection retains the existing two RNG calls");
    rendererTasks(); assertEquals(expectedSeed, simpleRandSeed_8004dd44, "Completion must publish only once");
  }

  private void staleControllerEngineSuccessIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(true); startController(battle); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks(); controllerNotPublished();
  }

  private void staleControllerOwnerSuccessIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(true); startController(battle); settleLoader();
    battle.FUN_800c8624(); rendererTasks(); controllerNotPublished();
  }

  private void destroyedControllerSuccessIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(true); startController(battle); settleLoader();
    battle.FUN_800c8748(); rendererTasks(); controllerNotPublished(); assertNull(battlePreloadedEntities_1f8003f4);
  }

  private void supersededControllerFailureIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(false); startController(battle); settleWorkers();
    this.rootFile("SECT/DRGN1.BIN/401", new byte[4]); startController(battle); settleLoader();
    controllerNotPublished(); rendererTasks();
    assertEquals(2, battleFlags_800bc960); assertNotNull(GameEngine.SCRIPTS.getState(5));
  }

  private void supersededControllerSuccessCannotMaskFailure() throws Exception {
    final Battle battle = this.controllerBattle(true); startController(battle); settleWorkers();
    Files.delete(this.temporary.resolve("files/SECT/DRGN1.BIN/401")); startController(battle); settleLoader();
    controllerFailsOnRenderer(); assertEquals(0x345, simpleRandSeed_8004dd44); assertNull(GameEngine.SCRIPTS.getState(5));
  }

  private void missingPlayerScriptFailsSynchronously() throws Exception {
    final Battle battle = this.controllerBattle(true); Files.delete(this.temporary.resolve("files/player_combat_script"));
    final RuntimeException failure = assertThrows(RuntimeException.class, () -> startController(battle));
    assertTrue(failure.getMessage().contains("player_combat_script")); controllerNotPublished();
  }

  private void controllerReadinessAdvancesExistingViewportGate() throws Exception {
    final Battle battle = this.controllerBattle(true); set(battle, "loadingStage", 4);
    battleFlags_800bc960 = 1; startController(battle); settleLoader(); rendererTasks();
    invoke(battle, "initializeViewportAndCamera");
    assertEquals(5, get(battle, "loadingStage")); assertEquals(3, vsyncMode_8007a3b8);
    assertEquals(0x43, battleFlags_800bc960, "Existing readiness, transition and viewport flags are retained");
  }

  private void staleControllerEngineFailureIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(false); startController(battle); settleLoader();
    EngineStates.currentEngineState_8004dd04 = new Battle(); rendererTasks(); controllerNotPublished();
  }

  private void staleControllerOwnerFailureIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(false); startController(battle); settleLoader();
    battle.FUN_800c8624(); rendererTasks(); controllerNotPublished();
  }

  private void destroyedControllerFailureIsIgnored() throws Exception {
    final Battle battle = this.controllerBattle(false); startController(battle); settleLoader();
    battle.FUN_800c8748(); rendererTasks(); controllerNotPublished();
  }
}
