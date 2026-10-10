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
  @Test void focusedActionsStayDistinctAcrossPointerStates() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      try {
        for(final boolean primary : new boolean[]{false, true}) {
          final JButton action = ManagerView.button("Continue", primary);
          KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
            @Override public Component getFocusOwner() { return action; }
          });
          action.setSize(320, 60);
          for(int state = 0; state < 3; state++) {
            action.getModel().setRollover(state == 1);
            action.getModel().setArmed(state == 2);
            action.getModel().setPressed(state == 2);
            final var image = new java.awt.image.BufferedImage(320, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
            final var graphics = image.createGraphics(); action.paint(graphics); graphics.dispose();
            final double ring = luminance(image.getRGB(160, 2));
            final double fill = luminance(image.getRGB(160, 10));
            final double contrast = (Math.max(ring, fill) + .05) / (Math.min(ring, fill) + .05);
            assertTrue(contrast >= 3, "Focused " + (primary ? "primary" : "secondary") + " action state " + state + " has only " + contrast + ":1 contrast");
            final Path output = Path.of("build/reports/focused-action-" + primary + "-" + state + ".png");
            Files.createDirectories(output.getParent()); javax.imageio.ImageIO.write(image, "png", output.toFile());
          }
        }
      } catch(final java.io.IOException error) { throw new RuntimeException(error); }
      finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); }
    });
  }
  private static double luminance(final int rgb) {
    double value = 0;
    final double[] weights = {.2126, .7152, .0722};
    for(int channel = 0; channel < 3; channel++) {
      final double c = ((rgb >> (16 - 8 * channel)) & 255) / 255.0;
      value += weights[channel] * (c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4));
    }
    return value;
  }
  @Test void installationFieldShowsFocusWithoutChangingItsLayout() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      try {
        final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
        final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
        final JTextField field = components.stream().filter(c -> c instanceof JTextField).map(c -> (JTextField)c).findFirst().orElseThrow();
        final Insets before = field.getInsets(); field.setSize(520, 48);
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
          @Override public Component getFocusOwner() { return field; }
        });
        final var image = new java.awt.image.BufferedImage(520, 48, java.awt.image.BufferedImage.TYPE_INT_RGB);
        final var graphics = image.createGraphics(); field.paint(graphics); graphics.dispose();
        final double contrast = (luminance(image.getRGB(260, 5)) + .05) / (luminance(image.getRGB(260, 1)) + .05);
        assertTrue(contrast >= 3, "The installation field focus has only " + contrast + ":1 contrast");
        assertEquals(before, field.getInsets(), "Focus must not shift the path or aligned actions");
        final Path output = Path.of("build/reports/focused-installation-field.png"); Files.createDirectories(output.getParent()); javax.imageio.ImageIO.write(image, "png", output.toFile());
      } catch(final java.io.IOException error) { throw new RuntimeException(error); }
      finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); }
    });
  }
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
        if(component instanceof JLabel label && !label.getText().startsWith("<html>")) {
          final Insets insets = label.getInsets();
          assertTrue(label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth() - insets.left - insets.right, name + ": text is truncated: " + label.getText());
        }
        if(component instanceof JButton) assertTrue(component.getHeight() >= 44, name + ": action " + ((JButton)component).getText() + " too small: " + component.getHeight());
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
              field(view, "screen", new ManagerView.Screen[]{ManagerView.Screen.INSTALL, ManagerView.Screen.DISCS, ManagerView.Screen.STEAM}[step]); invoke(view, "render");
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
        field(view, "screen", ManagerView.Screen.STEAM); invoke(view, "render"); inspect(view, 1024, 660, "custom-path");
        final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
        final String path = components.stream().filter(c -> c instanceof JLabel l && l.getText().contains("/home/deck/Games/")).map(c -> ((JLabel)c).getText()).findFirst().orElseThrow();
        assertFalse(path.matches("(?s).*<br>[A-Za-z0-9]</div>.*"));
        assertTrue(path.contains("a".repeat(8) + "</div>"));
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void longUnicodePathsKeepEveryCharacterWhenWrapped() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final String path = "/home/deck/Games/a" + "🗡".repeat(60);
      final String html = ManagerView.copy(path, 15, ManagerView.MUTED).getText();
      assertEquals("<html><div style='width:360px'>" + path + "</div></html>", html.replace("<br>", ""));
      for(int i = 0; i < html.length(); i++) {
        if(Character.isHighSurrogate(html.charAt(i))) assertTrue(i + 1 < html.length() && Character.isLowSurrogate(html.charAt(++i)), "Wrapping split a Unicode character");
        else assertFalse(Character.isLowSurrogate(html.charAt(i)), "Wrapping left an unmatched Unicode character");
      }
    });
  }
  @Test void wideFolderNamesFitTheVerifiedInstallationCard() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final String name : new String[]{"W".repeat(90), "a" + "🗡".repeat(60)}) {
          final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/" + name));
          field(view, "screen", ManagerView.Screen.STEAM); invoke(view, "render"); inspect(view, 1024, 660, "wide-path-" + (name.startsWith("W") ? "latin" : "unicode"));
          final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
          assertTrue(components.stream().anyMatch(c -> c instanceof JPanel p && ("/home/deck/Games/" + name).equals(p.getToolTipText()) && ("/home/deck/Games/" + name).equals(p.getAccessibleContext().getAccessibleDescription())), "The complete installation location must remain available");
        }
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void updateLabelsUseReadableDatesAndRetainUnfamiliarIdentifiers() {
    assertEquals("October 10, 2026 · Refinement 3", ManagerView.releaseLabel("definitive-alpha-2026-10-10-refinement-3"));
    assertEquals("October 10, 2026", ManagerView.releaseLabel("definitive-alpha-2026-10-10"));
    assertEquals("definitive-alpha-2026-02-30-refinement-3", ManagerView.releaseLabel("definitive-alpha-2026-02-30-refinement-3"));
    assertEquals("community-preview", ManagerView.releaseLabel("community-preview"));
    final String identifier = "🗡".repeat(121);
    assertEquals("🗡".repeat(117) + "…", ManagerView.releaseLabel(identifier));
  }
  @Test void longProgressDetailsRemainReadableAtMinimumSize() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = new ManagerView(null, this.temporary, this.temporary.resolve("installation"));
        field(view, "busy", true); field(view, "started", System.nanoTime());
        final var bodyField = ManagerView.class.getDeclaredField("body"); bodyField.setAccessible(true);
        ((JPanel)bodyField.get(view)).removeAll();
        final var heading = ManagerView.class.getDeclaredMethod("heading", String.class, String.class); heading.setAccessible(true);
        heading.invoke(view, "Installing Definitive", "Keep this window open while this step finishes.");
        final var panelField = ManagerView.class.getDeclaredField("progressPanel"); panelField.setAccessible(true);
        ((JPanel)panelField.get(view)).setVisible(true);
        final var phaseField = ManagerView.class.getDeclaredField("progressPhase"); phaseField.setAccessible(true);
        ((JLabel)phaseField.get(view)).setText("Installing game and HD artwork");
        final var percentField = ManagerView.class.getDeclaredField("progressPercent"); percentField.setAccessible(true);
        ((JLabel)percentField.get(view)).setText("70%");
        final var barField = ManagerView.class.getDeclaredField("progress"); barField.setAccessible(true);
        ((JProgressBar)barField.get(view)).setValue(70);
        final var filePanel = ManagerView.class.getDeclaredField("fileProgressPanel"); filePanel.setAccessible(true); ((JPanel)filePanel.get(view)).setVisible(true);
        final var fileStatus = ManagerView.class.getDeclaredField("fileStatus"); fileStatus.setAccessible(true); ((JLabel)fileStatus.get(view)).setText("25% · bundled-mods/FMVHD-v0.1.0.jar");
        final var fileBar = ManagerView.class.getDeclaredField("fileProgress"); fileBar.setAccessible(true); ((JProgressBar)fileBar.get(view)).setValue(25);
        assertEquals("Current file progress", ((JProgressBar)fileBar.get(view)).getAccessibleContext().getAccessibleName());
        final String detail = "Unpacking the verified package into /home/deck/Games/" + "a-long-installation-folder/".repeat(16);
        field(view, "progressDetail", detail); invoke(view, "progressStatus");
        for(final int width : new int[]{1024, 1100, 1280}) inspect(view, width, 660, "long-progress");
        final var statusField = ManagerView.class.getDeclaredField("status"); statusField.setAccessible(true);
        assertEquals(detail, ((JLabel)statusField.get(view)).getToolTipText(), "The full work detail must remain available");
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void updateReviewCompletionAndReturnFitHandheldLayouts() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final int width : new int[]{1024, 1100, 1280}) {
          final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
          field(view, "screen", ManagerView.Screen.LAUNCHER);
          field(view, "installedIdentity", "October 10, 2026 · Delivery hardening");
          field(view, "candidate", new ReleaseUpdates.Candidate("definitive-alpha-2026-10-10-refinement-3", "fixture", URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/fixture/package.zip"), "a".repeat(64)));
          invoke(view, "render"); inspect(view, width, 660, "launcher-update");
          button(view, "Review update").doClick(); inspect(view, width, 660, "update-review");
          assertNotNull(button(view, "Install update"));
          invoke(view, "goBack"); assertNotNull(button(view, "Play"));
          field(view, "screen", ManagerView.Screen.UPDATED); invoke(view, "render"); inspect(view, width, 660, "update-complete");
          button(view, "Back to launcher").doClick(); assertNotNull(button(view, "Play"));
        }
      } catch(final Exception error) { throw new RuntimeException(error); }
    });
  }
  @Test void launcherFooterKeepsSpaceFromActionsAtMinimumSize() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
        field(view, "screen", ManagerView.Screen.LAUNCHER);
        field(view, "installedIdentity", "October 10, 2026 · Delivery hardening");
          field(view, "candidate", new ReleaseUpdates.Candidate("definitive-alpha-2026-10-10-refinement-3", "fixture", URI.create("https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/fixture/package.zip"), "a".repeat(64)));
        invoke(view, "render"); inspect(view, 1024, 660, "launcher-footer");
        final JButton action = button(view, "Review update");
        final var status = ManagerView.class.getDeclaredField("updates"); status.setAccessible(true); final JLabel footer = (JLabel)status.get(view);
        final Point actionPoint = SwingUtilities.convertPoint(action.getParent(), action.getLocation(), view);
        final Point footerPoint = SwingUtilities.convertPoint(footer.getParent(), footer.getLocation(), view);
        assertTrue(footerPoint.y - actionPoint.y - action.getHeight() >= 12, "Footer copy must not crowd the last action");
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
  @Test void recoveryNoticeAndAcknowledgmentFitMinimumClientArea() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final int width : new int[]{1024, 1100, 1280}) {
          final var view = new ManagerView(null, this.temporary, this.temporary.resolve("not-installed"));
          field(view, "screen", ManagerView.Screen.RECOVERY); field(view, "recoveryPending", true); field(view, "recoveryNotice", "Earlier verified package and its private data were recovered. Newer private files remain protected at /home/deck/Games/" + "preserved-data/".repeat(100));
          field(view, "recoveryRestoredRelease", "alpha-0123456789abcdef"); field(view, "recoveryRestoredData", "/home/deck/Games/data-previous"); field(view, "recoveryPreservedLocations", "/home/deck/Games/data-newer\n/home/deck/Games/snapshots");
          invoke(view, "render"); inspect(view, width, 660, "recovery-review");
          assertNotNull(button(view, "Use verified recovered state")); assertNotNull(button(view, "Review preserved data")); assertNotNull(button(view, "Recovery details"));
          assertThrows(java.util.NoSuchElementException.class, () -> button(view, "Play"));
        }
      } catch(final Exception failure) { throw new RuntimeException(failure); }
    });
  }
  @Test void storageRowsPaintPathsBytesProtectionReasonsAndCheckboxes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new StorageReviewPanel(new InstallStore.RetentionInventory(java.util.List.of(
        new InstallStore.RetainedItem("releases/alpha-0123456789abcdef", 1048576, true, "Inactive verified engine"),
        new InstallStore.RetainedItem("data/data-01234567", 2048, false, "Private saves are retained")), true, "Verified"), selected -> false, selected -> fail());
      final var list = panel.entries(); list.setSize(484, 164);
      final var image = new java.awt.image.BufferedImage(484, 164, java.awt.image.BufferedImage.TYPE_INT_RGB);
      final var graphics = image.createGraphics(); list.paint(graphics); graphics.dispose();
      for(int row = 0; row < 2; row++) {
        for(final int[] area : new int[][]{{50, 9, 430, 30}, {50, 36, 430, 70}, {10, 20, 38, 58}}) {
          int dark = 0;
          for(int y = row * 82 + area[1]; y < row * 82 + area[3]; y++) for(int x = area[0]; x < area[2]; x++) if(luminance(image.getRGB(x, y)) < .45) dark++;
          assertTrue(dark > 12, "Storage row " + row + " must visibly paint its " + (area[0] == 10 ? "checkbox" : area[1] == 9 ? "path" : "bytes and protection reason"));
        }
      }
    });
  }
  @Test void storageInventoryScreenFitsMinimumClientAreaAndLongPaths() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final boolean complete : new boolean[]{true, false}) for(final int width : new int[]{1024, 1100, 1280}) {
          final var view = new ManagerView(null, this.temporary, this.temporary.resolve("not-installed"));
          field(view, "screen", ManagerView.Screen.STORAGE);
          field(view, "storageInventory", new InstallStore.RetentionInventory(java.util.List.of(new InstallStore.RetainedItem("releases/" + "a".repeat(100), 1024L * 1024 * 1024, true, "Inactive verified engine; no state or router references it."), new InstallStore.RetainedItem("snapshots/snapshot-" + "b".repeat(200), 20 * 1024, false, "Private saves, settings, mods and snapshots are always retained.")), complete, complete ? "Ownership verified." : "Pruning stopped: ownership discovery is incomplete."));
          invoke(view, "render"); inspect(view, width, 660, "storage-" + complete);
          assertNotNull(button(view, "Back to launcher")); assertNotNull(button(view, "Refresh inventory"));
          final var all = new ArrayList<Component>(); InstallStoreTest.collect(view, all);
          assertTrue(all.stream().anyMatch(component -> component instanceof StorageReviewPanel));
          assertFalse(button(view, "Remove selected files").isEnabled());
        }
      } catch(final Exception failure) { throw new RuntimeException(failure); }
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
