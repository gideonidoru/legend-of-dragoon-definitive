package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.net.URI;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Render the shipped components; no desktop window, game or network is opened. */
class PresentationTest {
  @TempDir Path temporary;
  private static void field(final ManagerView view, final String name, final Object value) throws Exception {
    final var field = ManagerView.class.getDeclaredField(name); field.setAccessible(true); field.set(view, value);
  }
  private static void invoke(final ManagerView view, final String method) throws Exception {
    final var action = ManagerView.class.getDeclaredMethod(method); action.setAccessible(true); action.invoke(view);
  }
  private static JButton button(final JPanel view, final String text) {
    final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
    return components.stream().filter(c -> c instanceof JButton b && b.getText().equals(text)).map(c -> (JButton)c).findFirst().orElseThrow();
  }
  private static void invalidateTree(final Container root) {
    for(final Component child : root.getComponents()) if(child instanceof Container container) invalidateTree(container);
    root.invalidate();
  }
  private static void inspect(final JPanel view, final int width, final int height, final String name) throws Exception {
    view.setSize(width, height);
    // Detached headless panels do not receive Swing's window validation pass.
    invalidateTree(view); InstallStoreTest.layout(view);
    final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
    for(final Component component : components) {
      boolean visible = true;
      for(Component parent = component; parent != view && parent != null; parent = parent.getParent()) visible &= parent.isVisible();
      if(!visible) continue;
      if(component instanceof JButton || component instanceof JLabel || component instanceof JTextField) assertTrue(component.getWidth() > 0 && component.getHeight() > 0, name + ": visible content collapsed: " + component);
      if(component.getWidth() <= 0 || component.getHeight() <= 0) continue;
      final Point point = SwingUtilities.convertPoint(component.getParent(), component.getLocation(), view);
      if(component instanceof JButton || component instanceof JLabel || component instanceof JTextField) {
        for(Container parent = component.getParent(); parent != null && parent != view; parent = parent.getParent()) {
          final Point local = SwingUtilities.convertPoint(component.getParent(), component.getLocation(), parent);
          assertTrue(local.x >= 0 && local.y >= 0 && local.x + component.getWidth() <= parent.getWidth() && local.y + component.getHeight() <= parent.getHeight(), name + ": ancestor clips content: " + component);
        }
        assertTrue(point.x >= 0 && point.y >= 0 && point.x + component.getWidth() <= width && point.y + component.getHeight() <= height, name + ": content outside screen: " + component);
        assertTrue(component.getWidth() <= component.getParent().getWidth(), name + ": content outside card: " + component + " parent width=" + component.getParent().getWidth());
        assertTrue(component.getX() >= 0 && component.getY() >= 0 && component.getX() + component.getWidth() <= component.getParent().getWidth() && component.getY() + component.getHeight() <= component.getParent().getHeight(), name + ": card clips its content: " + component);
        if(component instanceof JLabel label && label.getText().startsWith("<html>")) assertTrue(label.getHeight() >= label.getPreferredSize().height, name + ": wrapped copy is clipped");
        if(component instanceof JButton) assertTrue(component.getHeight() >= 44, name + ": action too small");
      }
    }
    final var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics(); view.paint(graphics); graphics.dispose();
    final Path file = Path.of("build/reports/installer-" + name + "-" + width + ".png"); Files.createDirectories(file.getParent()); javax.imageio.ImageIO.write(image, "png", file.toFile());
  }
  @Test void sharedSurfaceRendersSmoothTextWithoutDesktopFontSettings() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var panel = ManagerView.surface(new BorderLayout()); panel.setBackground(Color.WHITE);
      final var text = new JLabel("Definitive"); text.setFont(ManagerView.font(26, false)); text.setForeground(Color.BLACK); panel.add(text);
      panel.setSize(240, 60); InstallStoreTest.layout(panel);
      final var image = new java.awt.image.BufferedImage(240, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
      final var graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
      final var colours = new java.util.HashSet<Integer>();
      for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) colours.add(image.getRGB(x, y));
      assertTrue(colours.size() > 2, "Text must have smooth intermediate coverage, not just black and white pixels");
    });
  }
  @Test void setupStepsFitMinimumClientArea() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final int width : new int[]{1024, 1100, 1280}) {
          for(final int height : new int[]{660, 700, 760}) {
            final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
            for(int step = 0; step < 3; step++) {
              field(view, "step", step); invoke(view, "render");
              inspect(view, width, height, "setup-" + step + "-" + height);
            }
          }
        }
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void customPathDoesNotLeaveAnOrphanLetter() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final String directory = "a".repeat(45);
        final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/" + directory));
        field(view, "step", 2); invoke(view, "render"); inspect(view, 1024, 660, "custom-path");
        final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
        final String path = components.stream().filter(c -> c instanceof JLabel l && l.getText().contains("/home/deck/Games/")).map(c -> ((JLabel)c).getText()).findFirst().orElseThrow();
        assertFalse(path.matches("(?s).*<br>[A-Za-z0-9]</div>.*"));
        assertTrue(path.contains("a".repeat(8) + "</div>"));
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void updateReviewCompletionAndReturnFitHandheldLayouts() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final int width : new int[]{1024, 1100, 1280}) {
          final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
          field(view, "launcher", true);
          field(view, "candidate", new ReleaseUpdates.Candidate("definitive-alpha-2026-10-09-presentation-2", "fixture", URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/fixture/package.zip"), "a".repeat(64)));
          invoke(view, "render"); inspect(view, width, 700, "launcher-update");
          button(view, "Review update").doClick(); inspect(view, width, 700, "update-review");
          assertNotNull(button(view, "Install update"));
          invoke(view, "goBack"); assertNotNull(button(view, "Play"));
          field(view, "updateComplete", true); invoke(view, "render"); inspect(view, width, 700, "update-complete");
          button(view, "Back to launcher").doClick(); assertNotNull(button(view, "Play"));
        }
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void longFailureKeepsRetryAndFullLogAvailable() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
        final String full = "Storage failure at /home/deck/" + "long-path/".repeat(100);
        view.showFailure(new java.io.IOException(full)); inspect(view, 1024, 700, "long-error");
        assertNotNull(button(view, "Back to setup")); assertNotNull(button(view, "Show error details"));
        assertTrue(Files.readString(InstallerLog.path()).contains(full));
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void sharedDialogLayoutHasOneCloseActionAndReadableRestoreCopy() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var text = ManagerView.copy("Restore the previous version with its pre-update saves and settings. Your newer data is kept separately.", 18, ManagerView.MUTED);
        final var panel = ManagerDialogs.panel("Restore previous version", text, "Restore version", () -> { }, () -> { });
        inspect(panel, 700, 420, "restore-sheet");
        final var close = new java.util.concurrent.atomic.AtomicBoolean();
        final var log = ManagerDialogs.panel("Installer log", new JScrollPane(new JTextArea("Fixture log")), "Close", () -> { }, () -> close.set(true));
        assertThrows(java.util.NoSuchElementException.class, () -> button(log, "Cancel"));
        button(log, "Close").doClick(); assertTrue(close.get());
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
}
