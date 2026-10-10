// Definitive launcher input (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.sdl.SDL_Event;
import static org.lwjgl.sdl.SDLGamepad.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLHints.*;
import static org.lwjgl.sdl.SDLStdinc.SDL_free;

/** SDL runs on the Linux main thread. Input is routed only to this app's active window, never injected. */
final class DeckControls {
  private static final NavigationInput INPUT = new NavigationInput();
  private DeckControls() { }
  static void loop(final AtomicReference<JFrame> window) {
    if(!PackageManifest.hostPlatform().equals("linux-x64")) return;
    final Map<Integer, Long> pads = new HashMap<>();
    boolean initialized = false;
    try {
      SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
      if(!SDL_Init(SDL_INIT_GAMEPAD)) return; initialized = true;
      final var ids = SDL_GetGamepads();
      if(ids != null) { for(int i = 0; i < ids.limit(); i++) { final int id = ids.get(i); final long pad = SDL_OpenGamepad(id); if(pad != 0) pads.put(id, pad); } SDL_free(ids); }
      int horizontal = 0, vertical = 0, axisKey = 0;
      final Map<Integer, Set<Integer>> buttons = new HashMap<>();
      try(final SDL_Event event = SDL_Event.malloc()) {
        while(window.get() == null || window.get().isDisplayable()) {
          while(SDL_PollEvent(event)) {
            switch(event.type()) {
              case SDL_EVENT_GAMEPAD_ADDED -> { final int id = event.gdevice().which(); if(!pads.containsKey(id)) { final long pad = SDL_OpenGamepad(id); if(pad != 0) pads.put(id, pad); } }
              case SDL_EVENT_GAMEPAD_REMOVED -> { final Long pad = pads.remove(event.gdevice().which()); if(pad != null) SDL_CloseGamepad(pad); horizontal = 0; vertical = 0; axisKey = 0; buttons.clear(); INPUT.clear(); }
              case SDL_EVENT_GAMEPAD_BUTTON_DOWN -> {
                final int key = buttonKey(event.gbutton().button());
                if(key != 0) { buttons.computeIfAbsent(event.gbutton().which(), unused -> new HashSet<>()).add(key); dispatch(window.get(), NavigationInput.Source.BUTTON, key); }
              }
              case SDL_EVENT_GAMEPAD_BUTTON_UP -> {
                final int key = buttonKey(event.gbutton().button()); final Set<Integer> held = buttons.get(event.gbutton().which()); if(held != null) held.remove(key); SwingUtilities.invokeLater(() -> INPUT.release(NavigationInput.Source.BUTTON, key));
              }
              case SDL_EVENT_GAMEPAD_AXIS_MOTION -> {
                final int axis = event.gaxis().axis(); final int value = event.gaxis().value();
                if(axis == SDL_GAMEPAD_AXIS_LEFTX) horizontal = direction(value);
                if(axis == SDL_GAMEPAD_AXIS_LEFTY) vertical = direction(value);
              }
              default -> { }
            }
          }
          final int nextAxis = vertical != 0 ? (vertical > 0 ? KeyEvent.VK_DOWN : KeyEvent.VK_UP) : horizontal != 0 ? (horizontal > 0 ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT) : 0;
          if(nextAxis != axisKey) { final int previousAxis = axisKey; SwingUtilities.invokeLater(() -> INPUT.release(NavigationInput.Source.AXIS, previousAxis)); axisKey = nextAxis; }
          if(axisKey != 0) dispatch(window.get(), NavigationInput.Source.AXIS, axisKey);
          for(final Set<Integer> held : buttons.values()) for(final int key : held) if(NavigationInput.directional(key)) dispatch(window.get(), NavigationInput.Source.BUTTON, key);
          Thread.sleep(16);
        }
      }
    } catch(final LinkageError | Exception failure) { System.err.println("Controller support unavailable: " + failure.getMessage() + ". Touch and keyboard navigation remain available."); }
    finally { if(initialized) { for(final long pad : pads.values()) SDL_CloseGamepad(pad); SDL_QuitSubSystem(SDL_INIT_GAMEPAD); } }
  }
  static int direction(final int value) { return value > 18000 ? 1 : value < -18000 ? -1 : 0; }
  static int buttonKey(final int button) {
    return switch(button) {
      case SDL_GAMEPAD_BUTTON_SOUTH -> KeyEvent.VK_ENTER;
      case SDL_GAMEPAD_BUTTON_EAST -> KeyEvent.VK_ESCAPE;
      case SDL_GAMEPAD_BUTTON_DPAD_UP -> KeyEvent.VK_UP;
      case SDL_GAMEPAD_BUTTON_DPAD_DOWN -> KeyEvent.VK_DOWN;
      case SDL_GAMEPAD_BUTTON_DPAD_LEFT -> KeyEvent.VK_LEFT;
      case SDL_GAMEPAD_BUTTON_DPAD_RIGHT -> KeyEvent.VK_RIGHT;
      case SDL_GAMEPAD_BUTTON_LEFT_SHOULDER -> KeyEvent.VK_PAGE_UP;
      case SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER -> KeyEvent.VK_PAGE_DOWN;
      default -> 0;
    };
  }
  static void installKeyboardNavigation(final JFrame frame) {
    final KeyEventDispatcher dispatcher = event -> {
      if(event.isAltDown() || event.isControlDown() || event.isMetaDown()) return false;
      final Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      while(owner != null && owner != frame) owner = owner.getOwner();
      final int key = event.getKeyCode();
      if(event.getID() == KeyEvent.KEY_RELEASED) INPUT.release(NavigationInput.Source.KEYBOARD, key == KeyEvent.VK_SPACE ? KeyEvent.VK_ENTER : key);
      if(owner == frame && (focus instanceof AbstractButton || focus instanceof JList<?> || key == KeyEvent.VK_ESCAPE) && (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE && focus instanceof JList<?> || key == KeyEvent.VK_ESCAPE || NavigationInput.directional(key))) {
        final int routed = key == KeyEvent.VK_SPACE ? KeyEvent.VK_ENTER : key;
        if(event.getID() == KeyEvent.KEY_RELEASED) INPUT.release(NavigationInput.Source.KEYBOARD, routed);
        else if(event.getID() == KeyEvent.KEY_PRESSED && INPUT.press(NavigationInput.Source.KEYBOARD, routed, System.nanoTime())) route(frame, routed);
        return true;
      }
      return false;
    };
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowDeactivated(final java.awt.event.WindowEvent event) {
        SwingUtilities.invokeLater(() -> { Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(); while(active != null && active != frame) active = active.getOwner(); if(active == null) INPUT.clear(); });
      }
      @Override public void windowClosed(final java.awt.event.WindowEvent event) { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher); INPUT.clear(); }
    });
  }
  private static void dispatch(final JFrame frame, final NavigationInput.Source source, final int key) {
    SwingUtilities.invokeLater(() -> { if(INPUT.press(source, key, System.nanoTime())) route(frame, key); });
  }
  static void route(final JFrame frame, final int key) {
    if(frame == null) return;
    final Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    Window owner = active; while(owner != null && owner != frame) owner = owner.getOwner();
    if(owner == null) return;
    if(frame.getContentPane() instanceof ManagerView view && view.isBusy()) return;
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if(key == KeyEvent.VK_ENTER && focused instanceof AbstractButton button && button.isEnabled()) { button.doClick(); return; }
    if(focused instanceof JList<?> list && key != KeyEvent.VK_ESCAPE) {
      if(key == KeyEvent.VK_ENTER) { final Action action = list.getActionMap().get("activate"); if(action != null) action.actionPerformed(new java.awt.event.ActionEvent(list, 0, "activate")); }
      else moveSelection(list, key);
      return;
    }
    if(key == KeyEvent.VK_ESCAPE && active instanceof RootPaneContainer container) {
      final var pane = container.getRootPane(); final Object binding = pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
      final Action action = pane.getActionMap().get(binding); if(action != null) action.actionPerformed(new java.awt.event.ActionEvent(pane, 0, "cancel")); return;
    }
    if(key == KeyEvent.VK_ENTER && active instanceof RootPaneContainer container) { final JButton b = container.getRootPane().getDefaultButton(); if(b != null && b.isEnabled()) b.doClick(); return; }
    if(focused != null) {
      if(key == KeyEvent.VK_UP || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_PAGE_UP) focused.transferFocusBackward();
      else if(key == KeyEvent.VK_DOWN || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_PAGE_DOWN) focused.transferFocus();
    }
  }
  static void moveSelection(final JList<?> list, final int key) {
    if(list.getModel().getSize() == 0) return;
    final int amount = key == KeyEvent.VK_PAGE_DOWN ? 6 : key == KeyEvent.VK_PAGE_UP ? -6 : key == KeyEvent.VK_DOWN ? 1 : key == KeyEvent.VK_UP ? -1 : 0;
    if(amount == 0) { if(key == KeyEvent.VK_RIGHT) list.transferFocus(); else if(key == KeyEvent.VK_LEFT) list.transferFocusBackward(); return; }
    final int index = Math.max(0, Math.min(list.getModel().getSize() - 1, list.getSelectedIndex() + amount)); list.setSelectedIndex(index); list.ensureIndexIsVisible(index);
  }
}
