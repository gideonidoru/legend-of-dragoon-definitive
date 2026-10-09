package legend.game;

import legend.core.GameEngine;

import static legend.core.GameEngine.RENDERER;
import static legend.core.GameEngine.PLATFORM;

public final class Bootstrapper {
  private static Thread externallyStartedEngine;

  private Bootstrapper() { }

  // Definitive (2026-10-09): Mac runner owns the native first thread.
  static void useExternallyStartedEngine(final Thread engine) {
    externallyStartedEngine = engine;
  }

  static boolean isExternallyStarted() {
    return externallyStartedEngine != null;
  }

  /**
   * Start the engine on a background thread
   */
  public static Thread loadEngine() {
    final Thread engine;
    if(externallyStartedEngine != null) {
      engine = externallyStartedEngine;
    } else {
      engine = new Thread(() -> Main.main(new String[0]));
      engine.start();
    }
    Wait.waitFor(() -> !GameEngine.isLoading(), 60_000, "engine loading");
    Wait.waitFor(() -> RENDERER.window() != null, 30_000, "engine window created");
    if(externallyStartedEngine != null) {
      Input.focusEngineWindow();
      Wait.waitFor(() -> PLATFORM.getLastWindow() == RENDERER.window(), 30_000, "test input routed to engine window");
    }
    return engine;
  }
}
