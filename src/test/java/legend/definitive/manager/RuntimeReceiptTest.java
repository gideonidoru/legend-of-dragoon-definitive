package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeReceiptTest {
  @TempDir Path temporary;
  @Test void verifiedUpdateRefreshesTheRuntimeReceiptToItsWorkingJvm() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("install"));
    store.install(fixtures.pack("first", PackageManifest.hostPlatform()));
    Files.writeString(store.root().resolve(".java-path"), "/old/cache/runtime-jdk25/bin/java\n");
    store.install(fixtures.pack("second", PackageManifest.hostPlatform()));
    assertEquals(Path.of(System.getProperty("java.home"), "bin", "java") + "\n", Files.readString(store.root().resolve(".java-path")));
    try(final var paths = Files.list(store.root())) { assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".java-path-"))); }
  }
  @Test void linkedRuntimeReceiptPreservesExternalDataAndActiveState() throws Exception {
    this.temporary = this.temporary.toRealPath();
    final var fixtures = new InstallStoreTest(); fixtures.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("install"));
    store.install(fixtures.pack("first", PackageManifest.hostPlatform()));
    final String before = Files.readString(store.root().resolve("state.properties"));
    final Path sentinel = this.temporary.resolve("outside"); Files.writeString(sentinel, "preserved");
    Files.delete(store.root().resolve(".java-path")); Files.createSymbolicLink(store.root().resolve(".java-path"), sentinel);
    assertThrows(java.io.IOException.class, () -> store.install(fixtures.pack("second", PackageManifest.hostPlatform())));
    assertEquals("preserved", Files.readString(sentinel));
    assertEquals(before, Files.readString(store.root().resolve("state.properties")));
  }
}
