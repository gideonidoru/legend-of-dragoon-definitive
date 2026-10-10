package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Drive the real setup buttons and worker, rather than testing a preview alone. */
class InstallerFlowTest {
  @org.junit.jupiter.api.io.TempDir Path temporary;
  @org.junit.jupiter.api.BeforeEach void realPath() throws Exception { this.temporary = this.temporary.toRealPath(); }
  private Path pack(final String name, final String platform) throws Exception {
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary; return fixtures.pack(name, platform);
  }
  private static void collect(final Container panel, final java.util.List<Component> all) { InstallStoreTest.collect(panel, all); }
  private static void snapshot(final JPanel panel, final String name) {
    panel.setSize(1100, 700); InstallStoreTest.layout(panel);
    final var image = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
    try { final Path output = Path.of("build/reports/installer-" + name + ".png"); Files.createDirectories(output.getParent()); javax.imageio.ImageIO.write(image, "png", output.toFile()); }
    catch(final java.io.IOException error) { throw new RuntimeException(error); }
  }
  private static void awaitIdle(final ManagerView panel) throws Exception {
    final long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime() < end) {
      final var busy = new java.util.concurrent.atomic.AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> busy.set(panel.isBusy()));
      if(!busy.get()) return;
      Thread.sleep(20);
    }
    fail("Installer did not finish its worker within the test budget");
  }
  private static JButton button(final Container panel, final String text) {
    final var all = new ArrayList<Component>(); collect(panel, all);
    return all.stream().filter(c -> c instanceof JButton b && b.getText().startsWith(text)).map(c -> (JButton)c).findFirst().orElseThrow();
  }
  @Test void actualInstallButtonCreatesVerifiedStateAndLauncher() throws Exception {
    final Path source = this.pack("flow-package", PackageManifest.hostPlatform());
    final Path target = this.temporary.resolve("new-install");
    final var panel = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> {
      panel.set(new ManagerView(null, source, target));
      button(panel.get(), "Install Definitive").doClick();
    });
    awaitIdle(panel.get());
    assertTrue(Files.isRegularFile(target.resolve("state.properties")));
    assertTrue(Files.isRegularFile(target.resolve("Play Game.sh")));
    final var store = new InstallStore(target);
    final Path active = target.resolve("releases").resolve(store.state().getProperty("version"));
    PackageManifest.read(active).verify(active, PackageManifest.hostPlatform());
    SwingUtilities.invokeAndWait(() -> assertNotNull(button(panel.get(), "Choose disc files")));
  }
  @Test void progressDoesNotBounceWithoutShowingActualWork() throws Exception {
    final Path source = this.pack("progress-package", PackageManifest.hostPlatform());
    final var ref = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new ManagerView(null, source, this.temporary.resolve("progress-install")); ref.set(panel);
      button(panel, "Install Definitive").doClick();
      final var all = new ArrayList<Component>(); collect(panel, all);
      final var progress = (JProgressBar)all.stream().filter(c -> c instanceof JProgressBar).findFirst().orElseThrow();
      assertFalse(progress.isIndeterminate(), "Installer progress must show completed work, not an endless bouncing bar");
      snapshot(panel, "working");
    });
    awaitIdle(ref.get());
  }
  @Test void finishClosesOnlyAfterInstallationAndDiscVerification() throws Exception {
    final Path target = this.temporary.resolve("finish-install");
    final var store = new InstallStore(target); store.install(this.pack("finish-package", PackageManifest.hostPlatform()));
    for(final String id : DiscImporter.IDS) Files.write(target.resolve("isos/" + id + ".bin"), InstallStoreTest.disc(id));
    Files.writeString(store.prepareLaunch().resolve("files/version"), "prepared-fixture");
    final var closed = new java.util.concurrent.atomic.AtomicBoolean(); final var ref = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var panel = new ManagerView(null, this.temporary, target, () -> closed.set(true)); ref.set(panel);
        final var step = ManagerView.class.getDeclaredField("step"); step.setAccessible(true); step.setInt(panel, 2);
        final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(panel);
        button(panel, "Finish without adding to Steam").doClick();
      } catch(final ReflectiveOperationException e) { throw new RuntimeException(e); }
    });
    awaitIdle(ref.get()); assertTrue(closed.get(), "Finish must close setup after verification");
  }
  @Test void failedInstallCannotAdvanceAndShowsRetryWithPersistentError() throws Exception {
    final Path source = this.pack("bad-package", PackageManifest.hostPlatform());
    Files.writeString(source.resolve("lod-game-test.jar"), "damaged");
    final Path target = this.temporary.resolve("failed-install"); final var ref = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> { ref.set(new ManagerView(null, source, target)); button(ref.get(), "Install Definitive").doClick(); });
    awaitIdle(ref.get());
    assertFalse(Files.exists(target.resolve("state.properties")));
    assertTrue(Files.readString(InstallerLog.path()).contains("Package checksum mismatch"));
    SwingUtilities.invokeAndWait(() -> {
      assertNotNull(button(ref.get(), "Return and retry"));
      final var all = new ArrayList<Component>(); collect(ref.get(), all);
      assertFalse(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().startsWith("Choose disc files")));
      ref.get().setSize(1024, 700); InstallStoreTest.layout(ref.get());
      final var retry = button(ref.get(), "Return and retry");
      final Point position = SwingUtilities.convertPoint(retry.getParent(), retry.getLocation(), ref.get());
      assertTrue(position.y + retry.getHeight() <= 700, "Retry and failure must be visible on the Deck screen");
      snapshot(ref.get(), "failure");
    });
  }
  @Test void relativeDestinationCannotInstallInsideTemporaryBootstrap() throws Exception {
    assertThrows(java.io.IOException.class, () -> ManagerView.installationPath("Games/Definitive"));
    assertThrows(java.io.IOException.class, () -> ManagerView.installationPath(""));
    assertEquals(Path.of(System.getProperty("user.home"), "Games", "Definitive"), ManagerView.installationPath("~/Games/Definitive"));
  }
  @Test void everySetupActionFitsTheDeckScreen() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        for(final int width : new int[]{1024, 1100}) for(final int stage : new int[]{0, 1, 2}) {
          final var panel = new ManagerView(null, this.temporary, Path.of("/home/deck/Games/Legend-of-Dragoon-Definitive"));
          final var step = ManagerView.class.getDeclaredField("step"); step.setAccessible(true); step.setInt(panel, stage);
          final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(panel);
          panel.setSize(width, 700); InstallStoreTest.layout(panel);
          final var all = new ArrayList<Component>(); collect(panel, all);
          for(final Component item : all) if(item instanceof JButton action) {
            final Point at = SwingUtilities.convertPoint(item.getParent(), item.getLocation(), panel);
            assertTrue(at.y >= 0 && at.y + item.getHeight() <= 700, stage + ": " + action.getText() + " is outside the Deck screen");
            assertTrue(item.getHeight() >= 44, stage + ": " + action.getText() + " is too small to tap");
          }
          if(width == 1100) snapshot(panel, "stage-" + stage);
        }
      } catch(final ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    });
  }
}
