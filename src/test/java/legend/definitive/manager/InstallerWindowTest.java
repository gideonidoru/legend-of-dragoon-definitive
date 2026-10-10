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
      final var list = (JList<?>)components.stream().filter(c -> c instanceof JList<?>).findFirst().orElseThrow(); list.setSelectedIndex(0);
      list.getActionMap().get("activate").actionPerformed(new java.awt.event.ActionEvent(list, 0, "activate"));
      components.stream().filter(c -> c instanceof JButton b && b.getText().startsWith("Use 1 selected")).map(c -> (JButton)c).findFirst().orElseThrow().doClick();
    });
    SwingUtilities.invokeAndWait(() -> { }); assertTrue(completed.get()); if(callbackProblem.get() != null) throw new AssertionError(callbackProblem.get());
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = (ManagerView)this.frame.getContentPane();
        final var step = ManagerView.class.getDeclaredField("step"); step.setAccessible(true); step.setInt(view, 2);
        final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(view);
        final var components = new ArrayList<Component>(); InstallStoreTest.collect(view, components);
        components.stream().filter(c -> c instanceof JButton b && b.getText().equals("Finish without adding to Steam")).map(c -> (JButton)c).findFirst().orElseThrow().doClick();
      } catch(final ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    });
    final long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime() < end) { final var displayable = new AtomicBoolean(); SwingUtilities.invokeAndWait(() -> displayable.set(this.frame.isDisplayable())); if(!displayable.get()) return; Thread.sleep(20); }
    fail("Finish did not dispose the actual installer window");
  }
}
