package legend.core.platform;

import legend.core.platform.input.InputClass;
import legend.core.renderer.RenderApi;

import java.nio.file.Path;

public abstract class Window {
  private boolean simulationConsumesInput;

  public final void setSimulationConsumesInput(final boolean active) { this.simulationConsumesInput = active; }
  public final boolean simulationConsumesInput() { return this.simulationConsumesInput; }
  private final WindowEvents events = new WindowEvents(this);

  public abstract RenderApi getRenderApi();

  protected abstract void destroy();
  protected abstract boolean shouldClose();

  public WindowEvents events() {
    return this.events;
  }

  public abstract void show();
  public abstract void close();

  public abstract void updateMonitor();
  public abstract void makeFullscreen();
  public abstract void makeWindowed();
  public abstract void centerWindow();

  public abstract void setTitle(final String title);

  public abstract int getWidth();
  public abstract int getHeight();
  public abstract boolean hasFocus();

  public abstract InputClass getInputClass();
  public abstract void startTextInput();
  public abstract void stopTextInput();

  public abstract void disableCursor();
  public abstract void showCursor();
  public abstract void hideCursor();
  public abstract void useNormalCursor();
  public abstract void usePointerCursor();
  public abstract void setWindowIcon(final Path path);

  public abstract void setFpsLimit(final int limit);
  public abstract int getFpsLimit();
}
