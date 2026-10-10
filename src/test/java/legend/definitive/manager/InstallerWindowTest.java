package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Swing windows in a CI virtual display; never launch on the user's desktop. */
@EnabledIfSystemProperty(named = "java.awt.headless", matches = "false")
class InstallerWindowTest {
  @TempDir Path temporary;
  private JFrame frame;
  @AfterEach void closeWindows() throws Exception { SwingUtilities.invokeAndWait(() -> { for(final Window window : Window.getWindows()) window.dispose(); }); }
  private static void awaitFocus(final JButton action) throws Exception {
    final long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime() < end) {
      final var focused = new AtomicBoolean(); SwingUtilities.invokeAndWait(() -> focused.set(action.isFocusOwner()));
      if(focused.get()) return;
      Thread.sleep(20);
    }
    fail("Focus did not reach " + action.getText());
  }
  private static void awaitEntries(final JList<?> list) {
    final var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
    final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    final Timer timer = new Timer(20, event -> { if(list.getModel().getSize() > 0 || System.nanoTime() > deadline) loop.exit(); });
    timer.start(); loop.enter(); timer.stop(); assertTrue(list.getModel().getSize() > 0, "Asynchronous picker did not load fixture files");
  }
  @Test void repeatedPickerActivationOpensOneWindowWithRealCheckboxSelection() throws Exception {
    Files.writeString(this.temporary.resolve("disc.bin"), "fixture");
    SwingUtilities.invokeAndWait(() -> {
      this.frame = new JFrame("Picker repeat fixture"); this.frame.setSize(1100, 740); this.frame.setVisible(true);
      for(int i = 0; i < 4; i++) TouchFilePicker.choose(this.frame, this.temporary, false, paths -> { });
      final var visible = java.util.Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog && w.isVisible()).toList();
      assertEquals(1, visible.size(), "Repeated A / keyboard activation must never duplicate the picker");
      final var picker = (JDialog)visible.getFirst(); final var all = new ArrayList<Component>(); InstallStoreTest.collect(picker.getContentPane(), all);
      @SuppressWarnings("unchecked") final var list = (JList<PickerFiles.Entry>)all.stream().filter(c -> c instanceof JList<?>).findFirst().orElseThrow();
      awaitEntries(list); list.setSelectedIndex(0); list.getActionMap().get("activate").actionPerformed(new java.awt.event.ActionEvent(list, 0, "activate"));
      final var row = (Container)list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, true, true);
      final var cells = new ArrayList<Component>(); InstallStoreTest.collect(row, cells);
      assertTrue(cells.stream().anyMatch(c -> c instanceof JCheckBox box && box.isSelected()), "Selection must have a visible checkbox, independent of font glyphs");
      final var image = new java.awt.image.BufferedImage(picker.getWidth(), picker.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB); final var g = image.createGraphics(); picker.paint(g); g.dispose();
      try { javax.imageio.ImageIO.write(image, "png", Path.of("build/reports/installer-picker-recovery-linux.png").toFile()); } catch(final java.io.IOException failure) { throw new RuntimeException(failure); }
    });
  }
  @Test void errorRecoveryReturnsControllerFocusToVisibleActions() throws Exception {
    final var recovery = new AtomicReference<JButton>(); final var details = new AtomicReference<JButton>();
    SwingUtilities.invokeAndWait(() -> {
      this.frame = new JFrame("Recovery focus fixture");
      final var view = new ManagerView(this.frame, this.temporary, this.temporary.resolve("installation"));
      this.frame.setContentPane(view); this.frame.setSize(1100, 740); this.frame.setVisible(true);
    });
    awaitFocus(this.frame.getRootPane().getDefaultButton());
    SwingUtilities.invokeAndWait(() -> {
      final var view = (ManagerView)this.frame.getContentPane(); view.showFailure(new java.io.IOException("Fixture storage failure"));
      recovery.set(this.frame.getRootPane().getDefaultButton());
      final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
      details.set(components.stream().filter(c -> c instanceof JButton b && b.getText().equals("Show error details")).map(c -> (JButton)c).findFirst().orElseThrow());
    });
    assertEquals("Back to setup", recovery.get().getText()); awaitFocus(recovery.get());
    SwingUtilities.invokeAndWait(() -> DeckControls.route(this.frame, java.awt.event.KeyEvent.VK_DOWN)); awaitFocus(details.get());
    SwingUtilities.invokeAndWait(() -> DeckControls.route(this.frame, java.awt.event.KeyEvent.VK_ESCAPE));
    awaitFocus(this.frame.getRootPane().getDefaultButton());
    assertEquals("Install Definitive", this.frame.getRootPane().getDefaultButton().getText());
  }
  @Test void dirtyChildRepaintKeepsSharedSurfaceTextSmoothing() throws Exception {
    for(final boolean manager : new boolean[]{true, false}) {
      final var painted = new AtomicInteger(); final var smoothed = new AtomicBoolean();
      final var probe = new JComponent() {
        @Override protected void paintComponent(final Graphics graphics) {
          painted.incrementAndGet(); smoothed.set(((Graphics2D)graphics).getFontRenderContext().isAntiAliased());
          graphics.setColor(Color.BLACK); graphics.drawString("Fixture text", 12, 28);
        }
      };
      probe.setOpaque(true); probe.setPreferredSize(new Dimension(200, 50));
      SwingUtilities.invokeAndWait(() -> {
        this.frame = new JFrame("Repaint fixture");
        final JPanel root = manager ? new ManagerView(null, this.temporary, this.temporary.resolve("installation")) : ManagerView.surface(new BorderLayout());
        root.add(probe, BorderLayout.SOUTH); this.frame.setContentPane(root); this.frame.setSize(1100, 740); this.frame.setVisible(true);
      });
      SwingUtilities.invokeAndWait(() -> {
        final int before = painted.get(); probe.paintImmediately(0, 0, probe.getWidth(), probe.getHeight());
        assertTrue(painted.get() > before, "Dirty child must actually repaint"); assertTrue(smoothed.get(), "Dirty child repaint must pass through its smooth painting origin"); this.frame.dispose();
      });
    }
  }
  @Test void selectedFilesClosePickerBeforeCallbackAndFinishDisposesWindow() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path target = this.temporary.resolve("installation"); final var store = new InstallStore(target);
    store.install(fixture.pack("package", PackageManifest.hostPlatform()));
    for(final String id : DiscImporter.IDS) Files.write(target.resolve("isos/" + id + ".bin"), InstallStoreTest.disc(id));
    Files.writeString(store.prepareLaunch().resolve("files/version"), "prepared-fixture");
    final Path selections = Files.createDirectory(this.temporary.resolve("selections")); Files.writeString(selections.resolve("disc.bin"), "selection fixture");
    final var completed = new AtomicBoolean(); final var callbackProblem = new AtomicReference<Throwable>();
    SwingUtilities.invokeAndWait(() -> {
      this.frame = new JFrame("Installer fixture");
      final var view = new ManagerView(this.frame, this.temporary, target); this.frame.setContentPane(view); this.frame.setSize(1100, 740); this.frame.setVisible(true);
      TouchFilePicker.choose(this.frame, selections, false, paths -> {
        try {
          assertEquals(java.util.List.of(selections.resolve("disc.bin")), paths);
          assertTrue(this.frame.isEnabled());
          assertFalse(java.util.Arrays.stream(Window.getWindows()).anyMatch(w -> w instanceof JDialog && w.isDisplayable()), "Picker must be disposed before work begins");
        } catch(final Throwable failure) { callbackProblem.set(failure); }
        completed.set(true);
      });
      final var picker = (JDialog)java.util.Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog && w.isVisible()).findFirst().orElseThrow();
      final var components = new ArrayList<Component>(); InstallStoreTest.collect(picker.getContentPane(), components);
      final var list = (JList<?>)components.stream().filter(c -> c instanceof JList<?>).findFirst().orElseThrow(); awaitEntries(list); list.setSelectedIndex(0);
      list.getActionMap().get("activate").actionPerformed(new java.awt.event.ActionEvent(list, 0, "activate"));
      final var image = new java.awt.image.BufferedImage(picker.getWidth(), picker.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
      final var graphics = image.createGraphics(); picker.paint(graphics); graphics.dispose();
      try { javax.imageio.ImageIO.write(image, "png", Path.of("build/reports/installer-disc-picker-linux.png").toFile()); }
      catch(final java.io.IOException error) { throw new RuntimeException(error); }
      components.stream().filter(c -> c instanceof JButton b && b.getText().equals("Use 1 file")).map(c -> (JButton)c).findFirst().orElseThrow().doClick();
    });
    SwingUtilities.invokeAndWait(() -> { }); assertTrue(completed.get()); if(callbackProblem.get() != null) throw new AssertionError(callbackProblem.get());
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = (ManagerView)this.frame.getContentPane();
        final var screen = ManagerView.class.getDeclaredField("screen"); screen.setAccessible(true); screen.set(view, ManagerView.Screen.STEAM);
        final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(view);
        final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
        components.stream().filter(c -> c instanceof JButton b && b.getText().equals("Finish without Steam")).map(c -> (JButton)c).findFirst().orElseThrow().doClick();
      } catch(final ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    });
    final long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime() < end) { final var displayable = new AtomicBoolean(); SwingUtilities.invokeAndWait(() -> displayable.set(this.frame.isDisplayable())); if(!displayable.get()) return; Thread.sleep(20); }
    fail("Finish did not dispose the actual installer window");
  }
}
