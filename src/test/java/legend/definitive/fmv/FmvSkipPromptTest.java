package legend.definitive.fmv;

import legend.core.platform.input.InputClass;
import legend.game.fmv.Fmv;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production native-frame prompt clock shared by original and HD movies. */
class FmvSkipPromptTest {
  private static Method method(final String name, final Class<?>... parameters) throws Exception {
    final var method = Fmv.class.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
  }
  private static Object get(final String name) throws Exception {
    final var field = Fmv.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
  }
  private static void set(final String name, final Object value) throws Exception {
    final var field = Fmv.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
  }
  private static void reset() throws Exception {
    set("skipText", null); set("currentInputSource", null); set("skipTextFrame", 0); set("skipTextExpiresAtFrame", 0L);
    set("skipTextNeedsFrame", false);
    set("isKeyboardInput", false); set("isControllerInput", false);
  }

  @Test void promptExpiresAfterFourMediaSecondsAtEveryPresentationRate() throws Exception {
    final var show = method("setSkipText", String.class, InputClass.class);
    final var advance = method("handleSkipText", int.class);
    final var valid = method("isValidSkipInput", InputClass.class);
    try {
      for(final int rate : new int[]{5, 15, 60}) {
        reset(); show.invoke(null, "Skip movie", InputClass.MOUSE);
        for(int tick = 0; tick <= rate * 4; tick++) {
          final int nativeFrame = tick * 15 / rate;
          advance.invoke(null, nativeFrame);
          assertEquals(nativeFrame < 60, valid.invoke(null, InputClass.MOUSE), "Prompt lifetime at " + rate + " callbacks/s, native frame " + nativeFrame);
          if(nativeFrame < 60) assertEquals("Skip movie", get("skipText"));
        }
        assertNull(get("skipText")); assertNull(get("currentInputSource"));
      }
    } finally { reset(); }
  }

  @Test void skippedFramesBeforeNewInputAndPausedCallbacksDoNotAgeTheNewPrompt() throws Exception {
    final var show = method("setSkipText", String.class, InputClass.class);
    final var advance = method("handleSkipText", int.class);
    final var input = method("handleSkipText");
    final var valid = method("isValidSkipInput", InputClass.class);
    try {
      reset(); advance.invoke(null, 300);
      show.invoke(null, "Skip movie", InputClass.KEYBOARD);
      for(int callback = 0; callback < 600; callback++) {
        advance.invoke(null, 300); input.invoke(null);
      }
      assertEquals(true, valid.invoke(null, InputClass.KEYBOARD));
      assertEquals(false, valid.invoke(null, InputClass.MOUSE));
      advance.invoke(null, 359); assertNotNull(get("skipText"));
      advance.invoke(null, 360); assertNull(get("skipText"));
      show.invoke(null, "Fresh mouse prompt", InputClass.MOUSE);
      advance.invoke(null, 360);
      advance.invoke(null, 419); assertEquals(true, valid.invoke(null, InputClass.MOUSE));
      advance.invoke(null, 420); assertEquals(false, valid.invoke(null, InputClass.MOUSE));
    } finally { reset(); }
  }

  @Test void inputBeforeFirstRenderAfterAStallGetsItsFullDisplayedLifetime() throws Exception {
    final var show = method("setSkipText", String.class, InputClass.class);
    final var advance = method("handleSkipText", int.class);
    final var valid = method("isValidSkipInput", InputClass.class);
    try {
      for(final InputClass source : new InputClass[]{InputClass.MOUSE, InputClass.KEYBOARD, InputClass.GAMEPAD}) {
        reset(); advance.invoke(null, 0);
        // Audio advanced while rendering stalled; input reaches the engine before the next draw.
        show.invoke(null, "Fresh prompt after a stall", source);
        assertEquals(true, valid.invoke(null, source), "Second input may skip even before the next draw");
        advance.invoke(null, 300);
        assertEquals("Fresh prompt after a stall", get("skipText"));
        advance.invoke(null, 359); assertEquals(true, valid.invoke(null, source));
        advance.invoke(null, 360); assertEquals(false, valid.invoke(null, source));
      }
      reset(); show.invoke(null, "Previously displayed prompt", InputClass.MOUSE); advance.invoke(null, 0);
      advance.invoke(null, 300); assertNull(get("skipText"), "An old displayed prompt still expires across skipped frames");
    } finally { reset(); }
  }
}
