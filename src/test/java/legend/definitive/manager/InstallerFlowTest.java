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
  @Test void reopeningDownloadedInstallerAfterUninstallOffersInstallInsteadOfRepair() throws Exception {
    final Path source = this.pack("return-package", PackageManifest.hostPlatform());
    final Path target = this.temporary.resolve("return-install");
    final var store = new InstallStore(target); store.install(source);
    final Path save = store.data(store.state()).resolve("saves/retained.dsav");
    Files.writeString(save, "player save");
    InstallLocation.record(store);
    store.uninstall(false, InstallProgress.NONE);
    assertEquals(target, InstallLocation.discover().orElseThrow(), "Retained location should still help find saves");
    final var panel = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> panel.set(new ManagerView(null, source, target)));
    awaitIdle(panel.get());
    SwingUtilities.invokeAndWait(() -> {
      final var all = new ArrayList<Component>(); collect(panel.get(), all);
      assertTrue(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Install Definitive")),
        "A downloaded installer must offer Install after uninstall, not claim an existing installation");
      assertFalse(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Repair")));
    });
    assertEquals("player save", Files.readString(save));
    SwingUtilities.invokeAndWait(() -> button(panel.get(), "Install Definitive").doClick());
    awaitIdle(panel.get()); store.verifyInstalled();
    assertFalse(Boolean.parseBoolean(store.state().getProperty("uninstalled", "false")));
    assertEquals("player save", Files.readString(store.data(store.state()).resolve("saves/retained.dsav")));
  }
  @Test void reopeningInterruptedFirstInstallDoesNotClaimGameIsInstalled() throws Exception {
    final Path target = this.temporary.resolve("interrupted-first-install"); new InstallStore(target);
    final var panel = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> {
      panel.set(new ManagerView(null, this.temporary, target));
      assertNotNull(button(panel.get(), "Install Definitive"));
      final var all = new ArrayList<Component>(); collect(panel.get(), all);
      assertFalse(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Repair")));
    });
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
    SwingUtilities.invokeAndWait(() -> ref.set(new ManagerView(null, this.temporary, target, () -> closed.set(true))));
    awaitIdle(ref.get());
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var panel = ref.get();
        final var screen = ManagerView.class.getDeclaredField("screen"); screen.setAccessible(true); screen.set(panel, ManagerView.Screen.STEAM);
        final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(panel);
        button(panel, "Finish without Steam").doClick();
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
      assertNotNull(button(ref.get(), "Back to setup"));
      final var all = new ArrayList<Component>(); collect(ref.get(), all);
      assertFalse(all.stream().anyMatch(c -> c instanceof JButton b && b.getText().startsWith("Choose disc files")));
      ref.get().setSize(1024, 700); InstallStoreTest.layout(ref.get());
      final var retry = button(ref.get(), "Back to setup");
      final Point position = SwingUtilities.convertPoint(retry.getParent(), retry.getLocation(), ref.get());
      assertTrue(position.y + retry.getHeight() <= 700, "Retry and failure must be visible on the Deck screen");
      snapshot(ref.get(), "failure");
    });
  }
  @Test void transferDetailsFitDeckAndNeverLabelLocalWorkAsNetworkSpeed() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var panel = new ManagerView(null, this.temporary, this.temporary.resolve("transfer-install"));
        final var body = ManagerView.class.getDeclaredField("body"); body.setAccessible(true); ((JPanel)body.get(panel)).removeAll();
        final var heading = ManagerView.class.getDeclaredMethod("heading", String.class, String.class); heading.setAccessible(true); heading.invoke(panel, "Installing Definitive", "Keep this window open while this step finishes.");
        final var started = ManagerView.class.getDeclaredField("started"); started.setAccessible(true); started.setLong(panel, System.nanoTime());
        final var busy = ManagerView.class.getDeclaredField("busy"); busy.setAccessible(true); busy.setBoolean(panel, true);
        final var visible = ManagerView.class.getDeclaredField("progressPanel"); visible.setAccessible(true); ((JPanel)visible.get(panel)).setVisible(true);
        panel.applyProgress(new InstallProgress.Update("Downloading FMVHD", "Opening cinematic", 10, 75, 128_000_000, 240_000_000, "fmv/Opening-Cinematic.mp4", 128_000_000, 240_000_000, 128_000_000, 8_200_000));
        final var all = new ArrayList<Component>(); collect(panel, all);
        final JLabel size = all.stream().filter(c -> c instanceof JLabel l && "Current file size".equals(l.getAccessibleContext().getAccessibleName())).map(c -> (JLabel)c).findFirst().orElseThrow();
        final JLabel speed = all.stream().filter(c -> c instanceof JLabel l && "Download speed".equals(l.getAccessibleContext().getAccessibleName())).map(c -> (JLabel)c).findFirst().orElseThrow();
        assertEquals("128.0 MB of 240.0 MB", size.getText()); assertEquals("8.2 MB/s", speed.getText());
        panel.setSize(1024, 700); InstallStoreTest.layout(panel);
        for(final JLabel item : new JLabel[]{size, speed}) {
          final Point position = SwingUtilities.convertPoint(item.getParent(), item.getLocation(), panel);
          assertTrue(position.y + item.getHeight() <= 700); assertTrue(item.getWidth() >= item.getPreferredSize().width, "Transfer details must not clip on Deck");
        }
        snapshot(panel, "transfer-details");
        panel.applyProgress(new InstallProgress.Update("Reusing FMVHD", "Verified local file", 10, 75, 240_000_000, 240_000_000, "fmv/Opening-Cinematic.mp4", 240_000_000, 240_000_000));
        assertEquals("", speed.getText(), "Copies and checksum reuse must not appear as downloads");
        assertEquals("240.0 MB of 240.0 MB", size.getText());
        panel.applyProgress(new InstallProgress.Update("Downloading release", "Downloading", 0, 80, 1_000_000, 20_000_000).withNetwork(new InstallProgress.Network(1_000_000, 500_000)));
        assertEquals("1.0 MB of 20.0 MB", size.getText()); assertEquals("500 KB/s", speed.getText());
        panel.applyProgress(new InstallProgress.Update("Downloading release", "Downloading", 0, 80, 1_000_000, -1).withNetwork(new InstallProgress.Network(1_000_000, 500_000)));
        assertEquals("1.0 MB downloaded", size.getText());
        panel.applyProgress(new InstallProgress.Update("Downloading EnvHD", "Env3HD.zip", 0, 80, 1_000_000, -1, "Env3HD.zip", 1_000_000, -1, 1_000_000, 500_000));
        assertEquals("1.0 MB downloaded", size.getText());
        final var reported = ManagerView.class.getDeclaredField("networkReportedAt"); reported.setAccessible(true); reported.setLong(panel, System.nanoTime() - 4_000_000_000L);
        final var tick = ManagerView.class.getDeclaredMethod("progressStatus"); tick.setAccessible(true); tick.invoke(panel);
        assertEquals("Waiting for data", speed.getText(), "Idle transfers must not keep displaying a positive speed");
      } catch(final ReflectiveOperationException error) { throw new RuntimeException(error); }
    });
  }
  @Test void pausedDownloadButtonRetriesTheActualWorker() throws Exception {
    final var panel = new AtomicReference<ManagerView>();
    final var attempts = new java.util.concurrent.atomic.AtomicInteger();
    final var finished = new java.util.concurrent.atomic.AtomicBoolean();
    final Class<?> actionType = Class.forName("legend.definitive.manager.ManagerView$Action");
    final Object action = java.lang.reflect.Proxy.newProxyInstance(actionType.getClassLoader(), new Class<?>[]{actionType}, (proxy, method, arguments) -> {
      if(attempts.incrementAndGet() == 1) throw new FileDelivery.DownloadPaused(new java.io.IOException("Fixture connection timeout"));
      return "Download complete";
    });
    SwingUtilities.invokeAndWait(() -> {
      try {
        panel.set(new ManagerView(null, this.temporary, this.temporary.resolve("paused-download")));
        final var run = ManagerView.class.getDeclaredMethod("run", String.class, actionType, Runnable.class); run.setAccessible(true);
        run.invoke(panel.get(), "Installing Definitive", action, (Runnable)() -> finished.set(true));
      } catch(final ReflectiveOperationException error) { throw new RuntimeException(error); }
    });
    awaitIdle(panel.get()); assertEquals(1, attempts.get()); assertFalse(finished.get());
    SwingUtilities.invokeAndWait(() -> { snapshot(panel.get(), "download-paused"); button(panel.get(), "Retry download").doClick(); });
    awaitIdle(panel.get()); assertEquals(2, attempts.get()); assertTrue(finished.get());
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
          final var screen = ManagerView.class.getDeclaredField("screen"); screen.setAccessible(true); screen.set(panel, new ManagerView.Screen[]{ManagerView.Screen.INSTALL, ManagerView.Screen.DISCS, ManagerView.Screen.STEAM}[stage]);
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
