package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/** A real private Git repository advances while the installer must retain its reviewed revision. */
class PinnedSourceFallbackTest {
  @TempDir Path temporary;
  private static String git(final Path cwd, final String... args) throws Exception {
    final var command = new java.util.ArrayList<String>(); command.add("git"); command.addAll(java.util.List.of(args));
    final var builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1"); builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
    final var process = builder.start();
    final String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), output); return output.trim();
  }
  @Test void advancingDefaultBranchCannotChangePinnedCheckout() throws Exception {
    final Path root = this.temporary.toRealPath(), repository = Files.createDirectory(root.resolve("origin"));
    git(repository, "init", "--quiet"); Files.writeString(repository.resolve("content"), "reviewed"); git(repository, "add", "content");
    git(repository, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "--quiet", "-m", "reviewed");
    final String reviewed = git(repository, "rev-parse", "HEAD");
    Files.writeString(repository.resolve("content"), "unreviewed main"); git(repository, "add", "content");
    git(repository, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "--quiet", "-m", "later");
    final Path checkout = root.resolve("checkout");
    PortableSetup.checkoutPinned(repository.toString(), reviewed, checkout, root.resolve("build.log"), InstallProgress.NONE);
    assertEquals(reviewed, git(checkout, "rev-parse", "HEAD")); assertEquals("reviewed", Files.readString(checkout.resolve("content")));
    assertTrue(git(checkout, "status", "--porcelain").isEmpty());
  }
}
