package legend.definitive.input;

import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.SdlPlatformManager;
import legend.core.platform.Window;
import legend.core.platform.input.*;
import legend.game.EngineState;
import legend.game.EngineStates;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.combat.Battle;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.scripting.GameVarParam;
import legend.game.submap.SMap;
import legend.lodmod.LodMod;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Real platform bookkeeping and Battle/SMap script masks; no SDL window or physical input. */
final class LegacyInputCancellationTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {
    "focusLossCancelsEveryLegacyActionBeforeReleaseListeners",
    "focusLossCancelsCompletedTapsAndVmEdgesAfterPlatformConsumption",
    "disconnectCancelsFinalSourceButPreservesCompletedTaps",
    "disconnectPreservesAnotherControllerOrKeyboardOwner",
    "bindingReplacementCancelsVmMasksAndPreservesUnrelatedAction",
    "ordinaryShortTapsRemainLatchedForEveryLegacyAction",
    "deferredGlobalResetClearsEveryPlatformAndVmMask",
    "disconnectPreservesRemainingAxisUntilItsDeviceIsRemoved",
    "cancellationDispatchAllowsListenerRemovalAndReentrantBindingReset",
    "quickSavePressReachesEligibilityWhileRepeatsAndLoadingDoNot"
  })
  void cancellationReachesScriptConsumers(final String scenario) throws Exception {
    // The game registries are process singletons. Isolate actual-state fixtures from
    // other delivery tests rather than resetting their entries or changing suite order.
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      LegacyInputCancellationTest.class.getName(), scenario
    ).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Input fixture must finish within its bounded deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Input fixture process must be retired");
    }
  }

  public static void main(final String[] args) throws Exception {
    registries();
    final Method scenario = LegacyInputCancellationTest.class.getDeclaredMethod(args[0]);
    scenario.invoke(new LegacyInputCancellationTest());
    System.out.println("PASS " + args[0]);
  }

  private static void registries() throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    if(!CoreMod.RUN_BY_DEFAULT.isValid()) CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    if(!LodMod.INPUT_ACTION_SMAP_INTERACT.isValid()) {
      CoreMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
      LodMod.registerInputActions(new InputActionRegistryEvent((InputActionRegistry)GameEngine.REGISTRIES.inputActions));
    }
    final EngineStateTypeRegistry states = (EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes;
    final RegistryId battle = new RegistryId("lod", "battle"), submap = new RegistryId("lod", "submap");
    if(!states.hasEntry(battle)) states.register(battle, new EngineStateType<>(Battle.class, Battle::new));
    if(!states.hasEntry(submap)) states.register(submap, new EngineStateType<>(SMap.class, SMap::new));
  }

  private static Method method(final String name, final Class<?>... types) throws Exception {
    final Method method = SdlPlatformManager.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method;
  }

  private record ActionMask(InputAction action, int mask) { }

  private static List<ActionMask> actions(final boolean battle) {
    if(battle) return List.of(new ActionMask(LodMod.INPUT_ACTION_BTTL_ATTACK.get(), 0x20), new ActionMask(LodMod.INPUT_ACTION_BTTL_COUNTER.get(), 0x40));
    return List.of(
      new ActionMask(LodMod.INPUT_ACTION_GENERAL_MOVE_UP.get(), 0x1000),
      new ActionMask(LodMod.INPUT_ACTION_GENERAL_MOVE_RIGHT.get(), 0x2000),
      new ActionMask(LodMod.INPUT_ACTION_GENERAL_MOVE_DOWN.get(), 0x4000),
      new ActionMask(LodMod.INPUT_ACTION_GENERAL_MOVE_LEFT.get(), 0x8000),
      new ActionMask(LodMod.INPUT_ACTION_GENERAL_RUN.get(), 0x40),
      new ActionMask(LodMod.INPUT_ACTION_SMAP_INTERACT.get(), 0x20),
      new ActionMask(LodMod.INPUT_ACTION_SMAP_SNOWFIELD_WARP.get(), 0x800));
  }

  private static final class Fixture implements AutoCloseable {
    private final EngineState<?> previous = EngineStates.currentEngineState_8004dd04;
    private final boolean previousRun = GameEngine.CONFIG.getConfig(CoreMod.RUN_BY_DEFAULT.get());
    private final SdlPlatformManager platform;
    private final Window window = new NoopWindow(new NoopPlatformManager(), 640, 480);
    private final Consumer<InputAction> forwarding;
    private final List<InputAction> cancelled = new ArrayList<>();

    Fixture(final boolean battle) { this(battle, new SdlPlatformManager()); }

    Fixture(final boolean battle, final SdlPlatformManager platform) {
      this.platform = platform;
      GameEngine.CONFIG.setConfig(CoreMod.RUN_BY_DEFAULT.get(), false);
      EngineStates.currentEngineState_8004dd04 = battle ? new Battle() : new SMap();
      // Same current-state forwarding as bindRendererEvents, including cancellation without a window.
      this.window.events().onInputActionPressed((w, a, r) -> EngineStates.currentEngineState_8004dd04.inputActionPressed(a, r));
      this.window.events().onInputActionReleased((w, a) -> EngineStates.currentEngineState_8004dd04.inputActionReleased(a));
      this.forwarding = this.platform.onInputActionCancelled(action -> {
        EngineStates.currentEngineState_8004dd04.inputActionCancelled(action);
        this.cancelled.add(action);
      });
    }

    void press(final InputBinding<?> binding, final Integer device) throws Exception {
      method("pressBinding", InputBinding.class, Integer.class, Window.class).invoke(this.platform, binding, device, this.window);
    }

    void release(final InputBinding<?> binding, final Integer device) throws Exception {
      method("releaseBinding", InputBinding.class, Integer.class, Window.class).invoke(this.platform, binding, device, this.window);
    }

    void focusLoss() throws Exception { method("cancelInactiveWindowInput", Window.class).invoke(this.platform, this.window); }
    void disconnect(final int device) throws Exception { method("removeGamepadInputs", int.class, Window.class).invoke(this.platform, device, this.window); }

    void assertMask(final ActionMask action, final boolean edge, final boolean held) {
      assertEquals(edge, this.platform.isActionPressed(action.action), "platform pressed");
      assertEquals(edge, this.platform.isActionRepeat(action.action), "platform repeat");
      assertEquals(held, this.platform.isActionHeld(action.action), "platform held");
      assertEquals(edge, (new GameVarParam(4).get() & action.mask) != 0, "VM pressed");
      assertEquals(edge, (new GameVarParam(26).get() & action.mask) != 0, "VM repeat");
      assertEquals(held, (new GameVarParam(3).get() & action.mask) != 0, "VM held");
    }

    @Override public void close() {
      this.platform.removeInputActionCancelled(this.forwarding);
      EngineStates.currentEngineState_8004dd04 = this.previous;
      GameEngine.CONFIG.setConfig(CoreMod.RUN_BY_DEFAULT.get(), this.previousRun);
    }
  }

  private static InputBinding<?> button(final InputAction action) {
    return new InputBinding<>(action, new ButtonInputActivation(InputButton.A));
  }

  private void focusLossCancelsEveryLegacyActionBeforeReleaseListeners() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) {
      try(final Fixture f = new Fixture(battle)) {
        final List<ActionMask> actions = actions(battle);
        for(final ActionMask action : actions) f.press(button(action.action), 7);
        f.window.events().onInputActionReleased((w, a) -> {
          for(final ActionMask check : actions) f.assertMask(check, false, false);
        });
        f.focusLoss();
        for(final ActionMask action : actions) f.assertMask(action, false, false);
        assertEquals(actions.size(), f.cancelled.size());
      }
    }
  }

  private void focusLossCancelsCompletedTapsAndVmEdgesAfterPlatformConsumption() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) for(final boolean consumed : new boolean[]{false, true}) {
      try(final Fixture f = new Fixture(battle)) {
        final ActionMask action = actions(battle).getFirst();
        final var binding = button(action.action);
        f.press(binding, 7);
        f.release(binding, 7);
        if(consumed) f.platform.clearPressed();
        assertNotEquals(0, new GameVarParam(4).get() & action.mask);
        f.focusLoss();
        f.assertMask(action, false, false);
      }
    }
  }

  private void disconnectCancelsFinalSourceButPreservesCompletedTaps() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) for(final boolean tap : new boolean[]{false, true}) {
      try(final Fixture f = new Fixture(battle)) {
        final ActionMask action = actions(battle).getFirst();
        final var binding = button(action.action);
        f.press(binding, 7);
        if(tap) f.release(binding, 7);
        f.disconnect(7);
        f.assertMask(action, tap, false);
        assertEquals(tap ? 0 : 1, f.cancelled.size());
        f.disconnect(7);
        assertEquals(tap ? 0 : 1, f.cancelled.size());
      }
    }
  }

  private void disconnectPreservesAnotherControllerOrKeyboardOwner() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) for(final boolean keyboard : new boolean[]{false, true}) {
      try(final Fixture f = new Fixture(battle)) {
        final ActionMask action = actions(battle).getFirst();
        final var controller = button(action.action);
        final var other = keyboard ? new InputBinding<>(action.action, new KeyInputActivation(InputKey.A)) : controller;
        f.press(controller, 7);
        f.press(other, keyboard ? null : 8);
        f.disconnect(7);
        f.assertMask(action, true, true);
        assertTrue(f.cancelled.isEmpty());
        f.release(other, keyboard ? null : 8);
        f.assertMask(action, true, false);
      }
    }
  }

  private void bindingReplacementCancelsVmMasksAndPreservesUnrelatedAction() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) {
      final List<ActionMask> actions = actions(battle);
      final ActionMask action = actions.getFirst(), unrelated = actions.get(1);
      final var previous = InputBindings.getActivationsForAction(action.action);
      try(final Fixture f = new Fixture(battle, (SdlPlatformManager)GameEngine.PLATFORM)) {
        InputBindings.overwriteBindings(action.action, List.of(new ButtonInputActivation(InputButton.A)));
        final var binding = InputBindings.getBindings(ButtonInputActivation.class).stream().filter(b -> b.action == action.action).findFirst().orElseThrow();
        f.press(binding, 7);
        f.press(button(unrelated.action), 8);
        InputBindings.overwriteBindings(action.action, List.of(new ButtonInputActivation(InputButton.B)));
        f.assertMask(action, false, false);
        f.assertMask(unrelated, true, true);
      } finally {
        InputBindings.overwriteBindings(action.action, previous);
        GameEngine.PLATFORM.resetActionState(unrelated.action);
      }
    }
  }

  private void ordinaryShortTapsRemainLatchedForEveryLegacyAction() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) for(final ActionMask action : actions(battle)) {
      try(final Fixture f = new Fixture(battle)) {
        final var binding = button(action.action);
        f.press(binding, 7);
        f.release(binding, 7);
        f.assertMask(action, true, false);
        assertTrue(f.cancelled.isEmpty());
      }
    }
    try(final Fixture f = new Fixture(false)) {
      final InputAction run = LodMod.INPUT_ACTION_GENERAL_RUN.get();
      GameEngine.CONFIG.setConfig(CoreMod.RUN_BY_DEFAULT.get(), true);
      f.press(button(run), 7);
      f.focusLoss();
      assertFalse(f.platform.isActionHeld(run));
      for(final int parameter : new int[]{3, 4, 26}) {
        assertEquals(0x40, new GameVarParam(parameter).get() & 0x40, "Cancellation must preserve configured run-by-default interpretation");
      }
    }
  }

  private void deferredGlobalResetClearsEveryPlatformAndVmMask() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) {
      try(final Fixture f = new Fixture(battle)) {
        for(final ActionMask action : actions(battle)) f.press(button(action.action), 7);
        f.platform.resetActionStates();
        assertTrue(f.platform.isActionHeld(actions(battle).getFirst().action), "Reset remains deferred until the input pump applies it");
        method("applyPendingActionReset").invoke(f.platform);
        for(final ActionMask action : actions(battle)) f.assertMask(action, false, false);
        assertEquals(actions(battle).size(), f.cancelled.size());
        method("applyPendingActionReset").invoke(f.platform);
        assertEquals(actions(battle).size(), f.cancelled.size(), "Applying an already consumed reset emits no duplicate cancellations");
      }
    }
  }

  private void disconnectPreservesRemainingAxisUntilItsDeviceIsRemoved() throws Exception {
    for(final boolean battle : new boolean[]{true, false}) {
      try(final Fixture f = new Fixture(battle)) {
        final ActionMask action = actions(battle).getFirst();
        f.press(button(action.action), 7);
        method("updateAxisAction", InputAction.class, long.class, float.class, int.class, int.class, int.class)
          .invoke(f.platform, action.action, (long)9 << 32, .5f, 9, 0, 20000);
        f.disconnect(7);
        f.assertMask(action, true, true);
        assertEquals(.5f, f.platform.getAxis(action.action));
        assertTrue(f.cancelled.isEmpty());
        f.disconnect(9);
        f.assertMask(action, false, false);
      }
    }
  }

  private void cancellationDispatchAllowsListenerRemovalAndReentrantBindingReset() throws Exception {
    try(final Fixture f = new Fixture(true)) {
      final List<ActionMask> actions = actions(true);
      final ActionMask first = actions.getFirst(), second = actions.get(1);
      final InputAction unrelated = InputAction.fixed();
      final var listener = new java.util.concurrent.atomic.AtomicReference<Consumer<InputAction>>();
      listener.set(f.platform.onInputActionCancelled(action -> {
        f.platform.removeInputActionCancelled(listener.get());
        for(final ActionMask check : actions) {
          assertFalse(f.platform.isActionHeld(check.action));
          assertFalse(f.platform.isActionPressed(check.action));
        }
        f.platform.resetActionState(unrelated);
      }));
      f.press(button(first.action), 7);
      f.press(button(second.action), 7);
      f.focusLoss();
      f.assertMask(first, false, false);
      f.assertMask(second, false, false);
      assertTrue(f.cancelled.contains(unrelated));
    }
  }

  private void quickSavePressReachesEligibilityWhileRepeatsAndLoadingDoNot() throws Exception {
    try(final Fixture f = new Fixture(false)) {
      final int[] eligibilityChecks = {0};
      final SMap map = new SMap() {
        @Override public boolean canSave() {
          eligibilityChecks[0]++;
          return false; // Exercise the real shortcut guard without writing a player save.
        }
      };
      EngineStates.currentEngineState_8004dd04 = map;
      legend.game.Scus94491BpeSegment_800b.gameState_800babc8 = new legend.game.types.GameState52c();
      legend.game.Menus.whichMenu_800bdc38 = legend.game.inventory.WhichMenu.NONE_0;
      legend.game.FullScreenEffects.fullScreenEffect_800bb140.currentColour_28 = 0;
      map.smapLoadingStage_800cb430 = legend.game.submap.SubmapState.RENDER_SUBMAP_12;
      final InputAction save = LodMod.INPUT_ACTION_SMAP_SAVE.get();
      map.inputActionPressed(save, false);
      assertEquals(1, eligibilityChecks[0], "Ordinary quick-save must reach the existing eligibility guard");
      map.inputActionPressed(save, true);
      map.inputActionReleased(save);
      map.inputActionCancelled(save);
      assertEquals(1, eligibilityChecks[0], "Repeat/release/cancellation must not attempt a new save");
      map.smapLoadingStage_800cb430 = legend.game.submap.SubmapState.INIT_0;
      map.inputActionPressed(save, false);
      assertEquals(1, eligibilityChecks[0], "Loading scenes retain their existing save gate");
    }
  }
}
