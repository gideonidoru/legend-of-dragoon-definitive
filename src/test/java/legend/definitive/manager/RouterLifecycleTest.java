package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RouterLifecycleTest {
  @TempDir Path temporary;
  @Test void immediateRouterExitPreservesCodeAndDiagnostics() throws Exception {
    final Path root = this.temporary.toRealPath();
    assertEquals(7, ManagerMain.runRouter(new ProcessBuilder("/bin/bash", "-c", "echo router-startup-failure >&2; exit 7"), root));
    assertTrue(Files.readString(root.resolve("manager-router.log")).contains("router-startup-failure"));
    assertEquals(0, ManagerMain.runRouter(new ProcessBuilder("/bin/bash", "-c", "echo ready; exit 0"), root));
    assertEquals("ready\n", Files.readString(root.resolve("manager-router.log")));
  }
  @Test void linkedRouterLogStopsBeforeStartingInnerManager() throws Exception {
    final Path root = this.temporary.toRealPath(), outside = root.resolve("outside"); Files.writeString(outside, "preserved");
    Files.createSymbolicLink(root.resolve("manager-router.log"), outside);
    assertThrows(java.io.IOException.class, () -> ManagerMain.runRouter(new ProcessBuilder("/bin/bash", "-c", "touch started").directory(root.toFile()), root));
    assertEquals("preserved", Files.readString(outside)); assertFalse(Files.exists(root.resolve("started")));
  }
}
