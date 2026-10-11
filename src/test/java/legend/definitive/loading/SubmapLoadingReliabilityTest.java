package legend.definitive.loading;

import legend.core.Async;
import legend.core.GameEngine;
import legend.core.platform.input.InputActionRegistry;
import legend.core.platform.input.InputActionRegistryEvent;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.EngineStates;
import legend.game.DrgnFiles;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.submap.*;
import legend.game.types.NewRootStruct;
import legend.game.unpacker.FileData;
import legend.lodmod.LodMod;
import org.joml.Vector2f;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Actual scene callers in bounded isolated JVMs; synthetic parsed data and no native window. */
final class SubmapLoadingReliabilityTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"missingRetailAssetsNeverInvokeSuccess", "healthyRetailAssetsInvokeSuccess",
    "missingEnvironmentFailsOnRenderer", "malformedEnvironmentFailsOnRenderer",
    "missingStartupFailsOnRenderer", "malformedStartupFailsOnRenderer", "startupCommitWaitsForRenderer",
    "assetSuccessWaitsForRenderer", "assetFailureReachesRenderer", "staleSceneCompletionIsIgnored",
    "staleEngineCompletionIsIgnored", "supersededLoadInSameSceneIsIgnored",
    "malformedRetailAssetsNeverInvokeSuccess", "retailFutureRetainsReadAndOverlayFailures",
    "legacySubmapBridgeRetainsCallbacksAndSynchronousFailures", "supersededSuccessIsIgnored",
    "staleFailureIsIgnored", "destroyedSceneCompletionIsIgnored", "synchronousPreparationFailureReachesRenderer", "legacyRetailOverrideStillDispatches", "legacyRetailDelegationDoesNotRecurse"})
  void actualSceneContract(final String scenario) throws Exception {
    final Path root = Files.createDirectory(this.temporary.resolve(scenario));
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      SubmapLoadingReliabilityTest.class.getName(), scenario, root.toString())
      .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Scene fixture must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Scene fixture process must be retired");
    }
  }

  public static void main(final String[] args) throws Exception {
    registries();
    final var fixture = new SubmapLoadingReliabilityTest();
    fixture.temporary = Path.of(args[1]);
    try {
      SubmapLoadingReliabilityTest.class.getDeclaredMethod(args[0]).invoke(fixture);
      System.out.println("PASS " + args[0]);
    } finally {
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown(), "Fixture must retire actual loader work before deleting files");
    }
  }

  private static void registries() throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    CoreMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    LodMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    ((EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes).register(new RegistryId("lod", "submap"), new EngineStateType<>(SMap.class, SMap::new));
    DrgnFiles.drgnBinIndex_800bc058 = 1;
  }

  private static void set(final Object owner, final String name, final Object value) throws Exception {
    final var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
  }

  private static Object get(final Object owner, final String name) throws Exception {
    final var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
  }

  private static NewRootStruct root() { return new NewRootStruct(new FileData(new byte[0x38f0])); }

  private static SMap scene() {
    final SMap scene = new SMap(); EngineStates.currentEngineState_8004dd04 = scene; return scene;
  }

  private static RetailSubmap retail(final SMap scene, final int cut) {
    return new RetailSubmap(scene, cut, root(), new Vector2f(), new CollisionGeometry(scene));
  }

  private void file(final String name, final byte[] bytes) throws Exception {
    final Path path = this.temporary.resolve("files").resolve(name);
    Files.createDirectories(path.getParent()); Files.write(path, bytes);
  }

  private void folder(final String name) throws Exception { Files.createDirectories(this.temporary.resolve("files").resolve(name)); }

  private static void settleLoader() throws Exception {
    Async.shutdown();
    assertTrue(Async.awaitShutdown(), "Actual loader and parsing callbacks must settle within five seconds");
  }

  private static legend.core.platform.NoopWindow rendererWindow;

  private static void rendererTasks() throws Exception {
    if(rendererWindow == null) {
      rendererWindow = new legend.core.platform.NoopWindow(new legend.core.platform.NoopPlatformManager(), 640, 480);
      final var init = legend.core.renderer.RenderEngine.class.getDeclaredMethod("init", legend.core.platform.Window.class);
      init.setAccessible(true); init.invoke(GameEngine.RENDERER, rendererWindow);
      GameEngine.GPU.init();
      final var resize = legend.core.renderer.RenderEngine.class.getDeclaredMethod("onResize", legend.core.platform.Window.class, int.class, int.class);
      resize.setAccessible(true); resize.invoke(GameEngine.RENDERER, rendererWindow, 640, 480);
    }
    final var draw = legend.core.platform.NoopWindow.class.getDeclaredMethod("tick"); draw.setAccessible(true);
    try {
      draw.invoke(rendererWindow);
    } catch(final InvocationTargetException failure) {
      if(failure.getCause() instanceof RuntimeException problem) throw problem;
      if(failure.getCause() instanceof Error problem) throw problem;
      throw failure;
    }
  }

  private static void failsOnRenderer(final String resource) {
    final RuntimeException failure = assertThrows(RuntimeException.class, SubmapLoadingReliabilityTest::rendererTasks,
      "Required scene failure must surface instead of hanging or advancing");
    assertTrue(failure.getMessage().contains(resource), failure.toString());
    assertNotNull(failure.getCause(), "Renderer failure must retain the original load/parse problem");
  }

  private void missingRetailAssetsNeverInvokeSuccess() throws Exception {
    final AtomicBoolean success = new AtomicBoolean();
    retail(scene(), 0).loadAssets(() -> success.set(true)); settleLoader();
    assertFalse(success.get(), "Missing required assets must never invoke the legacy success callback");
  }

  private void healthyRetailAssetsInvokeSuccess() throws Exception {
    this.folder("SECT/DRGN21.BIN/5/textures"); this.folder("SECT/DRGN21.BIN/6");
    this.file("SECT/DRGN21.BIN/6/0", new byte[4]); this.file("SECT/DRGN21.BIN/6/1", new byte[4]);
    final RetailSubmap submap = retail(scene(), 0); final AtomicBoolean success = new AtomicBoolean();
    submap.loadAssets(() -> success.set(true)); settleLoader();
    assertTrue(success.get(), "Healthy actual resource parsing must still complete");
    assertNotNull(submap.script); assertTrue(submap.objects.isEmpty());
  }

  private void environment(final boolean malformed) throws Exception {
    if(malformed) this.folder("SECT/DRGN21.BIN/4");
    final SMap scene = scene(); set(scene, "newrootPtr_800cab04", root());
    scene.smapLoadingStage_800cb430 = SubmapState.LOAD_ENVIRONMENT_3; scene.tick(); settleLoader();
    assertEquals(SubmapState.WAIT_FOR_ENVIRONMENT, scene.smapLoadingStage_800cb430);
    failsOnRenderer("environment");
    assertEquals(SubmapState.WAIT_FOR_ENVIRONMENT, scene.smapLoadingStage_800cb430);
  }

  private void missingEnvironmentFailsOnRenderer() throws Exception { this.environment(false); }
  private void malformedEnvironmentFailsOnRenderer() throws Exception { this.environment(true); }

  private void startupFiles() throws Exception {
    this.file("SUBMAP/NEWROOT.RDT", new byte[0x38f0]);
    for(int i = 0; i < 6; i++) this.file("SUBMAP/savepoint/" + i, new byte[16]);
  }

  private void startupFailure(final boolean malformed) throws Exception {
    if(malformed) { this.startupFiles(); this.file("SUBMAP/NEWROOT.RDT", new byte[1]); }
    final SMap scene = scene(); scene.smapLoadingStage_800cb430 = SubmapState.LOAD_NEWROOT_1;
    scene.tick(); settleLoader(); scene.tick();
    assertEquals(SubmapState.WAIT_FOR_NEWROOT_2, scene.smapLoadingStage_800cb430,
      "I/O counter reaching zero must not advance a failed parsing chain");
    failsOnRenderer("startup");
  }

  private void missingStartupFailsOnRenderer() throws Exception { this.startupFailure(false); }
  private void malformedStartupFailsOnRenderer() throws Exception { this.startupFailure(true); }

  private void startupCommitWaitsForRenderer() throws Exception {
    this.startupFiles(); final SMap scene = scene(); scene.smapLoadingStage_800cb430 = SubmapState.LOAD_NEWROOT_1;
    scene.tick(); settleLoader(); scene.tick();
    assertEquals(SubmapState.WAIT_FOR_NEWROOT_2, scene.smapLoadingStage_800cb430);
    assertNull(get(scene, "newrootPtr_800cab04"), "Parsed startup data remains unpublished until renderer commit");
    rendererTasks();
    assertEquals(SubmapState.LOAD_ENVIRONMENT_3, scene.smapLoadingStage_800cb430);
    for(final String field : new String[]{"newrootPtr_800cab04", "savepointAnm1", "savepointAnm2", "savepointTmd", "savepointAnimation"}) assertNotNull(get(scene, field));
  }

  private static final class ControlledSubmap extends RetailSubmap {
    CompletableFuture<Void> preparation = new CompletableFuture<>();
    ControlledSubmap(final SMap scene) { super(scene, 0, root(), new Vector2f(), new CollisionGeometry(scene)); }
    RuntimeException submissionFailure;
    @Override public CompletableFuture<Void> loadAssets() {
      if(this.submissionFailure != null) throw this.submissionFailure;
      return this.preparation;
    }
    @Override public void loadAssets(final Runnable onLoaded) { this.preparation.thenRun(onLoaded); }
  }

  private static void startAssets(final SMap scene, final RetailSubmap submap) throws Exception {
    set(scene, "submap", submap); set(scene, "mediaLoadingStage_800c68e4", SubmapMediaState.LOAD_SOBJ_ASSETS_AND_SCRIPTS_5);
    final var execute = SMap.class.getDeclaredMethod("executeSubmapMediaLoadingStage", int.class);
    execute.setAccessible(true); execute.invoke(scene, 0);
  }

  private static Object media(final SMap scene) throws Exception { return get(scene, "mediaLoadingStage_800c68e4"); }

  private static void completeFromWorker(final CompletableFuture<Void> future, final Throwable failure) throws Exception {
    final Thread worker = new Thread(() -> {
      if(failure == null) future.complete(null); else future.completeExceptionally(failure);
    }, "scene-preparation-fixture");
    worker.start(); worker.join(1_000); assertFalse(worker.isAlive(), "Controlled preparation callback must settle");
  }

  private void assetSuccessWaitsForRenderer() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    completeFromWorker(submap.preparation, null);
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene), "Worker completion must not mutate simulation stages");
    rendererTasks(); assertEquals(SubmapMediaState.FINALIZE_SUBMAP_LOADING_7, media(scene));
  }

  private void assetFailureReachesRenderer() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    completeFromWorker(submap.preparation, new IllegalArgumentException("bad script"));
    failsOnRenderer("assets"); assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private void staleSceneCompletionIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap old = new ControlledSubmap(scene); startAssets(scene, old);
    completeFromWorker(old.preparation, null); set(scene, "submap", retail(scene, 1)); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private void staleEngineCompletionIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap old = new ControlledSubmap(scene); startAssets(scene, old);
    completeFromWorker(old.preparation, null); EngineStates.currentEngineState_8004dd04 = new SMap(); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private void supersededLoadInSameSceneIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    final CompletableFuture<Void> old = submap.preparation; submap.preparation = new CompletableFuture<>(); startAssets(scene, submap);
    completeFromWorker(old, new IllegalArgumentException("abandoned scene")); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
    completeFromWorker(submap.preparation, null); rendererTasks();
    assertEquals(SubmapMediaState.FINALIZE_SUBMAP_LOADING_7, media(scene));
  }
  private void malformedRetailAssetsNeverInvokeSuccess() throws Exception {
    this.folder("SECT/DRGN21.BIN/5/textures"); this.folder("SECT/DRGN21.BIN/6");
    final AtomicBoolean success = new AtomicBoolean();
    retail(scene(), 0).loadAssets(() -> success.set(true)); settleLoader();
    assertFalse(success.get(), "Failed script parsing must not invoke the legacy success callback");
  }

  private void retailFutureRetainsReadAndOverlayFailures() throws Exception {
    final RetailSubmap missing = retail(scene(), 0);
    assertThrows(ExecutionException.class, () -> missing.loadAssets().get(5, TimeUnit.SECONDS));
    this.folder("SECT/DRGN21.BIN/5/textures"); this.folder("SECT/DRGN21.BIN/6");
    this.file("SECT/DRGN21.BIN/6/0", new byte[4]); this.file("SECT/DRGN21.BIN/6/1", new byte[4]);
    final RetailSubmap overlay = retail(scene(), 5);
    assertThrows(ExecutionException.class, () -> overlay.loadAssets().get(5, TimeUnit.SECONDS),
      "Successful object preparation does not hide required overlay read failures");
    settleLoader(); assertNotNull(overlay.script, "Healthy object preparation reached the actual parser");
  }

  /** Represents an existing mod implementing the callback API, without a future override. */
  private static final class LegacySubmap extends Submap {
    Runnable completion;
    RuntimeException submissionFailure;
    LegacySubmap(final SMap scene) { super(scene); }
    @Override public CompletableFuture<Void> loadEnv() { return CompletableFuture.completedFuture(null); }
    @Override public void loadAssets(final Runnable completion) {
      if(this.submissionFailure != null) throw this.submissionFailure;
      this.completion = completion;
    }
    @Override public void loadMusicAndSounds() { }
    @Override public void startMusic() { }
    @Override public void loadMapTransitionData(final MapTransitionData4c data) { }
    @Override public void prepareEnv() { }
    @Override public void prepareSobjModel(final SubmapObject210 sobj) { }
    @Override public void finishLoading() { }
    @Override public void preDraw() { }
    @Override public void draw() { }
    @Override public void drawEnv(final legend.core.gte.MV[] matrices) { }
    @Override public void unload() { }
    @Override public void calcGoodScreenOffset(final float x, final float y, final Vector2f out) { }
    @Override public int getEncounterRate() { return 0; }
    @Override public void prepareEncounter(final boolean stage) { }
    @Override public void prepareEncounter(final legend.game.combat.encounters.Encounter encounter, final boolean stage) { }
    @Override public void storeStateBeforeBattle() { }
    @Override public boolean isReturningToSameMapAfterBattle() { return false; }
    @Override public SubmapSavable canSave() { return null; }
    @Override public legend.game.types.GsRVIEW2 getCamera() { return null; }
  }

  private void legacySubmapBridgeRetainsCallbacksAndSynchronousFailures() throws Exception {
    final LegacySubmap mod = new LegacySubmap(scene()); final CompletableFuture<Void> loaded = mod.loadAssets();
    assertFalse(loaded.isDone(), "Existing mod callbacks may prepare asynchronously");
    mod.completion.run(); assertNull(loaded.get(1, TimeUnit.SECONDS));
    final IllegalArgumentException failure = new IllegalArgumentException("mod rejected preparation");
    mod.submissionFailure = failure;
    assertSame(failure, assertThrows(ExecutionException.class, () -> mod.loadAssets().get(1, TimeUnit.SECONDS)).getCause());
  }

  private void supersededSuccessIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    final CompletableFuture<Void> old = submap.preparation; submap.preparation = new CompletableFuture<>(); startAssets(scene, submap);
    completeFromWorker(old, null); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene), "Old success must not commit into a newer load with the same scene and stage");
    completeFromWorker(submap.preparation, null); rendererTasks();
    assertEquals(SubmapMediaState.FINALIZE_SUBMAP_LOADING_7, media(scene));
  }

  private void staleFailureIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    completeFromWorker(submap.preparation, new IllegalArgumentException("abandoned engine"));
    EngineStates.currentEngineState_8004dd04 = new SMap(); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private void destroyedSceneCompletionIsIgnored() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene); startAssets(scene, submap);
    // Production has constructed its Java sequencer before a state is destroyed; native output is unnecessary here.
    set(GameEngine.AUDIO_THREAD, "sequencer", new legend.core.audio.sequencer.Sequencer(true, 24,
      legend.core.audio.InterpolationPrecision.Double, legend.core.audio.PitchResolution.Quadruple,
      legend.core.audio.EffectsOverTimeGranularity.Double));
    completeFromWorker(submap.preparation, null); scene.destroy(); rendererTasks();
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private void synchronousPreparationFailureReachesRenderer() throws Exception {
    final SMap scene = scene(); final ControlledSubmap submap = new ControlledSubmap(scene);
    submap.submissionFailure = new IllegalArgumentException("preparation submission rejected"); startAssets(scene, submap);
    failsOnRenderer("assets"); assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
  }

  private static final class LegacyRetailSubmap extends RetailSubmap {
    Runnable completion;
    LegacyRetailSubmap(final SMap scene) { super(scene, 0, root(), new Vector2f(), new CollisionGeometry(scene)); }
    @Override public void loadAssets(final Runnable completion) { this.completion = completion; }
  }

  private void legacyRetailOverrideStillDispatches() throws Exception {
    final SMap scene = scene(); final LegacyRetailSubmap mod = new LegacyRetailSubmap(scene); startAssets(scene, mod);
    assertNotNull(mod.completion, "The new future entrypoint must retain existing RetailSubmap callback overrides");
    mod.completion.run(); rendererTasks();
    assertEquals(SubmapMediaState.FINALIZE_SUBMAP_LOADING_7, media(scene));
  }

  private static final class DelegatingRetailSubmap extends RetailSubmap {
    boolean legacyCalled;
    DelegatingRetailSubmap(final SMap scene) { super(scene, 0, root(), new Vector2f(), new CollisionGeometry(scene)); }
    @Override public void loadAssets(final Runnable completion) {
      this.legacyCalled = true; super.loadAssets(completion);
    }
  }

  private void legacyRetailDelegationDoesNotRecurse() throws Exception {
    this.folder("SECT/DRGN21.BIN/5/textures"); this.folder("SECT/DRGN21.BIN/6");
    this.file("SECT/DRGN21.BIN/6/0", new byte[4]); this.file("SECT/DRGN21.BIN/6/1", new byte[4]);
    final SMap scene = scene(); final DelegatingRetailSubmap mod = new DelegatingRetailSubmap(scene); startAssets(scene, mod);
    settleLoader(); assertTrue(mod.legacyCalled); assertNotNull(mod.script);
    assertEquals(SubmapMediaState.WAIT_FOR_SOBJ_ASSETS_AND_SCRIPTS_6, media(scene));
    rendererTasks(); assertEquals(SubmapMediaState.FINALIZE_SUBMAP_LOADING_7, media(scene));
  }

}
