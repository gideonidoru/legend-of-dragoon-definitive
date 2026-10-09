// Definitive test tooling (2026-10-09), AGPL v3; see LICENSE.
package legend.game;

import legend.core.GameEngine;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Native Mac engine stays on the OS first thread; JUnit runs separately. */
public final class FirstThreadGameplayRunner {
  private FirstThreadGameplayRunner() { }

  public static void main(final String[] args) throws Exception {
    if(!Files.isRegularFile(Path.of("files/version"))) {
      throw new IllegalStateException("Complete private game extraction is required");
    }
    // Preserve normal locale/log initialization before initializing engine services.
    Class.forName(Main.class.getName());
    GameEngine.isLoading();
    Bootstrapper.useExternallyStartedEngine(Thread.currentThread());
    final Thread tests = new Thread(() -> {
      try {
        final var listener = new SummaryGeneratingListener();
        final var request = LauncherDiscoveryRequestBuilder.request()
          .selectors(selectClass(EngineBootTest.class)).build();
        LauncherFactory.create().execute(request, listener);
        final var summary = listener.getSummary();
        final var output = new PrintWriter(System.out, true);
        summary.printTo(output);
        summary.printFailuresTo(output);
        final boolean passed = summary.getTestsFoundCount() > 0
          && summary.getTestsSucceededCount() == summary.getTestsFoundCount()
          && summary.getTotalFailureCount() == 0;
        System.exit(passed ? 0 : 1);
      } catch(final Throwable error) {
        error.printStackTrace();
        System.exit(1);
      }
    }, "Gameplay-tests");
    tests.setDaemon(true);
    tests.start();
    Main.main(new String[0]);
    // Closing the window prematurely cannot count as a passing test run.
    System.exit(1);
  }
}
