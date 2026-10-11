package legend.definitive.input;

import it.unimi.dsi.fastutil.longs.LongSet;
import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.SdlPlatformManager;
import legend.core.platform.Window;
import legend.core.platform.input.ButtonInputActivation;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputBinding;
import legend.core.platform.input.InputButton;
import legend.core.platform.input.InputKey;
import legend.core.platform.input.KeyInputActivation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production input bookkeeping with synthetic sources; no SDL initialization or physical input. */
final class ControllerDisconnectTest {
  @BeforeAll static void events() throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS");
    access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
  }

  private static Method method(final String name, final Class<?>... types) throws Exception {
    final var method = SdlPlatformManager.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method;
  }

  private static final class Fixture {
    final SdlPlatformManager platform = new SdlPlatformManager();
    final Window window = new NoopWindow(new NoopPlatformManager(), 640, 480);
    final List<InputAction> released = new ArrayList<>();

    Fixture() { this.window.events().onInputActionReleased((window, action) -> this.released.add(action)); }

    void press(final InputBinding<?> binding, final Integer device) throws Exception {
      method("pressBinding", InputBinding.class, Integer.class, Window.class).invoke(this.platform, binding, device, this.window);
      if(device != null) buttons().add((long)device << 32);
    }

    void release(final InputBinding<?> binding, final Integer device) throws Exception {
      method("releaseBinding", InputBinding.class, Integer.class, Window.class).invoke(this.platform, binding, device, this.window);
    }

    void disconnect(final int device) throws Exception {
      method("removeGamepadInputs", int.class, Window.class).invoke(this.platform, device, this.window);
    }

    void axis(final InputAction action, final int device, final float value) throws Exception {
      method("updateAxisAction", InputAction.class, long.class, float.class, int.class, int.class, int.class)
        .invoke(this.platform, action, (long)device << 32, value, device, 0, 20000);
    }

    LongSet buttons() throws Exception {
      final var field = SdlPlatformManager.class.getDeclaredField("buttonsHeld");
      field.setAccessible(true);
      return (LongSet)field.get(this.platform);
    }
  }

  private static InputBinding<?> binding(final InputAction action) {
    return new InputBinding<>(action, new ButtonInputActivation(InputButton.A));
  }

  @Test void disconnectCancelsPendingPressAndRepeatBeforeEveryReleaseCallback() throws Exception {
    final Fixture f = new Fixture();
    final InputAction first = InputAction.fixed(), second = InputAction.fixed();
    f.press(binding(first), 7);
    f.press(binding(second), 7);
    f.window.events().onInputActionReleased((window, action) -> {
      for(final InputAction check : List.of(first, second)) {
        assertFalse(f.platform.isActionPressed(check));
        assertFalse(f.platform.isActionRepeat(check));
        assertFalse(f.platform.isActionHeld(check));
      }
    });
    f.disconnect(7);
    assertEquals(2, f.released.size());
    assertTrue(f.buttons().isEmpty(), "Keyboard suppression has no stale held device");
    f.disconnect(7);
    assertEquals(2, f.released.size(), "Duplicate removal emits no extra release");
  }

  @Test void disconnectPreservesAnotherControllersSameActionAndReconnectionWorks() throws Exception {
    final Fixture f = new Fixture();
    final InputAction action = InputAction.fixed();
    final InputBinding<?> binding = binding(action);
    f.press(binding, 7);
    f.press(binding, 8);
    f.platform.clearPressed();
    f.disconnect(7);
    assertTrue(f.platform.isActionHeld(action));
    assertEquals(1, f.buttons().size());
    assertTrue(f.released.isEmpty());
    f.disconnect(8);
    assertFalse(f.platform.isActionHeld(action));
    assertFalse(f.platform.isActionRepeat(action));
    assertEquals(List.of(action), f.released);
    f.press(binding, 9);
    assertTrue(f.platform.isActionPressed(action));
    assertTrue(f.platform.isActionHeld(action));
  }

  @Test void disconnectPreservesKeyboardBindingForTheSameAction() throws Exception {
    final Fixture f = new Fixture();
    final InputAction action = InputAction.fixed();
    final InputBinding<?> keyboard = new InputBinding<>(action, new KeyInputActivation(InputKey.A));
    final InputBinding<?> controller = binding(action);
    f.press(keyboard, null);
    f.press(controller, 7);
    f.disconnect(7);
    assertTrue(f.platform.isActionHeld(action));
    assertTrue(f.released.isEmpty());
    assertTrue(f.buttons().isEmpty());
    f.release(keyboard, null);
    assertFalse(f.platform.isActionHeld(action));
    assertEquals(List.of(action), f.released);
  }

  @Test void disconnectPreservesAnotherDevicesAxisAndButtonSources() throws Exception {
    final Fixture f = new Fixture();
    final InputAction action = InputAction.fixed();
    final InputBinding<?> binding = binding(action);
    f.axis(action, 7, .75f);
    f.press(binding, 8);
    f.disconnect(7);
    assertTrue(f.platform.isActionHeld(action));
    assertEquals(0.0f, f.platform.getAxis(action));
    assertTrue(f.released.isEmpty());
    f.axis(action, 9, .5f);
    f.disconnect(8);
    assertTrue(f.platform.isActionHeld(action));
    assertEquals(.5f, f.platform.getAxis(action));
    f.disconnect(9);
    assertFalse(f.platform.isActionHeld(action));
    assertFalse(f.platform.isActionRepeat(action));
    assertFalse(f.platform.isActionPressed(action));
    assertEquals(List.of(action), f.released);
  }

  @Test void ordinaryShortTapRemainsLatchedAndUnmatchedReleaseDoesNotCancelOtherDevice() throws Exception {
    final Fixture f = new Fixture();
    final InputAction action = InputAction.fixed();
    final InputBinding<?> binding = binding(action);
    f.press(binding, 7);
    f.release(binding, 8);
    assertTrue(f.platform.isActionHeld(action));
    f.release(binding, 7);
    assertFalse(f.platform.isActionHeld(action));
    assertTrue(f.platform.isActionPressed(action));
    assertTrue(f.platform.isActionRepeat(action));
    f.platform.clearPressed();
    assertFalse(f.platform.isActionRepeat(action));
  }
}
