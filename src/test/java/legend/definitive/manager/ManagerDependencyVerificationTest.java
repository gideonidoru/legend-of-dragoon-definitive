package legend.definitive.manager;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ManagerDependencyVerificationTest {
  @TempDir Path temporary;
  private Path pack() throws Exception {
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    return fixtures.pack("dependency-fixture", PackageManifest.hostPlatform());
  }
  @Test void alteredDependencyIsRejectedBeforeManagerLaunch() throws Exception {
    final Path packageRoot = this.pack(); final var manifest = PackageManifest.read(packageRoot);
    manifest.verifyManagerDependencies(packageRoot);
    final Path library;
    try(final var files = Files.list(packageRoot.resolve("libs"))) { library = files.filter(p -> p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
    Files.writeString(library, "modified executable dependency");
    assertThrows(IOException.class, () -> manifest.verifyManagerDependencies(packageRoot));
  }
  @Test void unexpectedOrMissingDependenciesAreRejected() throws Exception {
    final Path packageRoot = this.pack(); final var manifest = PackageManifest.read(packageRoot);
    final Path extra = packageRoot.resolve("libs/unexpected.jar"); Files.writeString(extra, "unexpected code");
    assertThrows(IOException.class, () -> manifest.verifyManagerDependencies(packageRoot)); Files.delete(extra);
    try(final var files = Files.list(packageRoot.resolve("libs"))) { Files.delete(files.findFirst().orElseThrow()); }
    assertThrows(IOException.class, () -> manifest.verifyManagerDependencies(packageRoot));
  }
}
