package legend.definitive.manager;

import java.nio.file.*;
import java.util.Arrays;

/** Isolated subprocess boundaries; never invokes the real game or a window. */
public final class StorageCrashMain {
  public static void main(final String[] args) throws Exception {
    final Path root = Path.of(args[1]);
    System.setProperty("definitive.installerLog", root.resolve("fixture-installer.log").toString());
    switch(args[0]) {
      case "bootstrap" -> new InstallStore(root, (source, target) -> {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        Runtime.getRuntime().halt(91);
      }).install(Path.of(args[2]));
      case "repair" -> new InstallStore(root).install(Path.of(args[2]), "", update -> { if(update.phase().equals("Repairing verified release")) Runtime.getRuntime().halt(95); });
      case "discs", "replace-discs" -> DiscImporter.importDiscs(new InstallStore(root), Arrays.stream(args).skip(2).map(Path::of).toList(), update -> {
        if(update.phase().equals("Activating verified discs")) Runtime.getRuntime().halt(92);
      }, args[0].equals("replace-discs"));
      case "play" -> System.exit(new InstallStore(root).play());
      case "pending" -> { try(final var operation = new InstallStore(root).lock()) { GameLease.begin(root); Runtime.getRuntime().halt(93); } }
      case "handoff" -> {
        final var store = new InstallStore(root);
        try(final var operation = store.lock()) {
          final String token = GameLease.begin(root);
          final var release = root.resolve("releases").resolve(store.state().getProperty("version"));
          final String cp = release.resolve("lod-game-test.jar") + java.io.File.pathSeparator + release.resolve("definitive-manager.jar") + java.io.File.pathSeparator + System.getProperty("java.class.path");
          final var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true", "-Ddefinitive.installRoot=" + root, "-Ddefinitive.launchToken=" + token, "-Dfixture.longCommand=" + "x".repeat(16384), "-cp", cp, StorageCrashMain.class.getName(), "delayed-wrapper", root.toString()).redirectErrorStream(true).redirectOutput(root.resolve("handoff-child.log").toFile()).start();
          Files.writeString(root.resolve("fixture-child.pid"), Long.toString(child.pid())); Runtime.getRuntime().halt(94);
        }
      }
      case "delayed-wrapper" -> {
        Files.writeString(root.resolve("fixture-spawn-gap.ready"), "Synthetic child is waiting before acquiring the game lease.");
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while(!Files.exists(root.resolve("fixture-spawn-gap.continue"))) { if(System.nanoTime() > deadline) throw new java.io.IOException("Synthetic spawn-gap gate timed out."); Thread.sleep(20); }
        try { ManagedGameMain.main(new String[0]); } catch(final Throwable failure) { throw new RuntimeException(failure); }
      }
      default -> throw new IllegalArgumentException("Unknown fixture mode.");
    }
  }
}
