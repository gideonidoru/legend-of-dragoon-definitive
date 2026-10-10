// Definitive installation tooling (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** Guided Desktop Mode setup. The same operations are available headlessly for tests. */
public final class ManagerMain {
  private ManagerMain() { }

  public static void main(final String[] args) throws Exception {
    if(Runtime.version().feature() != 25) throw new IOException("Use Java 25 for this alpha.");
    if(args.length == 3 && args[0].equals("--verify")) { PackageManifest.read(Path.of(args[1])).verify(Path.of(args[1]), args[2]); System.out.println("Verified complete package."); return; }
    if(args.length == 4 && args[0].equals("--package")) { makeManifest(Path.of(args[1]), args[2], args[3]); return; }
    if(args.length == 3 && args[0].equals("--install")) { System.out.println(new InstallStore(Path.of(args[2])).install(Path.of(args[1]))); return; }
    if(args.length == 2 && args[0].equals("--prepare")) { System.out.println(new InstallStore(Path.of(args[1])).prepareDiscs()); return; }
    if(args.length == 2 && args[0].equals("--rollback")) { System.out.println(new InstallStore(Path.of(args[1])).rollback()); return; }
    if(args.length >= 3 && args[0].equals("--import")) { System.out.println(DiscSources.importSelected(new InstallStore(Path.of(args[1])), Arrays.stream(args).skip(2).map(Path::of).toList())); return; }
    final Path packageRoot = args.length == 2 && args[0].equals("--setup") ? Path.of(args[1]).toAbsolutePath() : null;
    final Path installedRoot = args.length == 2 && (args[0].equals("--manage") || args[0].equals("--play")) ? Path.of(args[1]).toAbsolutePath() : InstallLocation.discover().orElse(Path.of(System.getProperty("user.home"), "Games", "Legend-of-Dragoon-Definitive"));
    // The managed root bootstrap routes to the currently active manager, including after rollback.
    if(args.length == 2 && (args[0].equals("--manage") || args[0].equals("--play"))) {
      final Path self = Path.of(ManagerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
      if(self.equals(installedRoot.resolve("definitive-manager.jar"))) {
        final var state = new InstallStore(installedRoot).state();
        final Path active = InstallStore.child(installedRoot.resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
        PackageManifest.read(active).verify(active, PackageManifest.hostPlatform());
        System.exit(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "--enable-native-access=ALL-UNNAMED", "-cp", active.resolve("definitive-manager.jar") + java.io.File.pathSeparator + active.resolve("libs/*"), "legend.definitive.manager.ManagerMain", args[0], installedRoot.toString()).inheritIO().start().waitFor());
      }
    }
    if(args.length == 2 && args[0].equals("--play")) {
      final var store = new InstallStore(installedRoot);
      final int code = store.play();
      if(code != 0 && code != 130 && code != 143) {
        System.err.println("Game exited with code " + code + ". Details: " + store.gameLog());
        try(final var lines = Files.lines(store.gameLog())) { final var tail = lines.toList(); tail.subList(Math.max(0, tail.size() - 24), tail.size()).forEach(System.err::println); }
      }
      System.exit(code); return;
    }
    final var window = new java.util.concurrent.atomic.AtomicReference<JFrame>();
    InstallerLog.write("Starting installer/launcher: platform=" + PackageManifest.hostPlatform() + ", Java=" + Runtime.version() + ", target=" + installedRoot);
    Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
      InstallerLog.failure(failure);
      final JFrame frame = window.get();
      if(frame != null && frame.isDisplayable()) SwingUtilities.invokeLater(() -> ((ManagerView)frame.getContentPane()).showFailure(failure));
      else failure.printStackTrace(System.err);
    });
    SwingUtilities.invokeAndWait(() -> window.set(show(packageRoot, installedRoot)));
    DeckControls.loop(window);
  }

  private static JFrame show(final Path packageRoot, final Path initialRoot) {
    final JFrame frame = new JFrame("The Legend of Dragoon · Definitive");
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.setContentPane(buildPanel(frame, packageRoot, initialRoot));
    final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    frame.setSize(Math.min(1100, screen.width), Math.min(740, screen.height)); frame.setMinimumSize(new Dimension(Math.min(1024, screen.width), Math.min(700, screen.height)));
    if(PackageManifest.hostPlatform().equals("linux-x64")) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    DeckControls.installKeyboardNavigation(frame);
    frame.setLocationRelativeTo(null); frame.setVisible(true);
    return frame;
  }

  static JPanel buildPanel(final JFrame frame, final Path packageRoot, final Path initialRoot) {
    return new ManagerView(frame, packageRoot, initialRoot);
  }

  static void makeManifest(final Path root, final String platform, final String revision) throws IOException {
    final Properties metadata = new Properties();
    metadata.setProperty("format", "1"); metadata.setProperty("java", "25"); metadata.setProperty("platform", platform); metadata.setProperty("sourceRevision", revision);
    metadata.setProperty("upstream", "fba1543543865e29ee572f479003d9b47158eeb3");
    metadata.setProperty("skurfa", "3c9e4b3ecefc31cb32fd1281a7857f3a08f56081");
    metadata.setProperty("releaseRepository", "gideonidoru/legend-of-dragoon-definitive");
    final Properties hashes = new Properties();
    try(final var files = Files.walk(root)) {
      for(final Path file : files.filter(Files::isRegularFile).toList()) {
        final String name = root.relativize(file).toString().replace('\\', '/');
        if(!PackageManifest.allowed(name)) throw new IOException("Not permitted in package: " + name);
        hashes.setProperty(name, PackageManifest.sha256(file));
        if(name.matches("lod-game-[A-Za-z0-9._-]+\\.jar")) {
          if(metadata.containsKey("gameJar")) throw new IOException("Package must contain one engine JAR.");
          metadata.setProperty("gameJar", name);
        }
      }
    }
    metadata.setProperty("releaseTag", System.getProperty("definitive.releaseTag", "local-" + revision));
    metadata.setProperty("id", PackageManifest.identity(metadata, hashes));
    InstallStore.atomicProperties(root.resolve(PackageManifest.METADATA), metadata);
    InstallStore.atomicProperties(root.resolve(PackageManifest.HASHES), hashes);
    new PackageManifest(metadata, hashes).verify(root, platform);
    System.out.println("Verified package " + metadata.getProperty("id") + " for " + platform);
  }
}
