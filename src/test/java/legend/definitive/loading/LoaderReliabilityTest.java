package legend.definitive.loading;

import legend.core.Async;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Actual loader contracts in isolated processes, without game data, windows or singleton resets. */
final class LoaderReliabilityTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {
    "cyclesAreRejectedPromptly", "missingRootsAndTargetsAreRejected", "malformedMapsAreRejected",
    "aliasesKeepIndicesBytesAndMetadata", "longAliasChainsResolveWithoutRecursion",
    "emptyEntriesAndDirectoriesRetainTheirMeaning", "unmappedDirectoriesKeepNormalOrdering",
    "pathFileFailuresRemainExceptional", "stringFileFailuresRemainExceptional",
    "pathBatchFailuresRemainExceptional", "stringBatchFailuresRemainExceptional",
    "directoryFailuresRemainExceptional", "successfulAsyncReadsRetainOrderAndPathConventions",
    "submissionFailureDoesNotLeakCounters", "cancelledConsumerStillSettlesActualWork"
  })
  void actualLoaderContract(final String scenario) throws Exception {
    final Path root = Files.createDirectory(this.temporary.resolve(scenario));
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      LoaderReliabilityTest.class.getName(), scenario, root.toString()
    ).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Loader fixture must complete within its bounded deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Loader fixture process must be retired");
    }
  }

  public static void main(final String[] args) throws Exception {
    final var fixture = new LoaderReliabilityTest();
    fixture.temporary = Path.of(args[1]);
    try {
      LoaderReliabilityTest.class.getDeclaredMethod(args[0]).invoke(fixture);
      System.out.println("PASS " + args[0]);
    } finally {
      Async.shutdownNow();
      assertTrue(Async.awaitShutdown(), "Fixture must retire the actual asynchronous executor");
    }
  }

  private Path map(final String name, final String entries) throws Exception {
    final Path folder = Files.createDirectory(this.temporary.resolve(name));
    Files.writeString(folder.resolve("mrg"), entries);
    return folder;
  }

  private static void rejectedPromptly(final Path folder) throws Exception {
    final var failure = new AtomicReference<Throwable>();
    final Thread worker = new Thread(() -> {
      try { Loader.loadDirectorySync(folder); }
      catch(final Throwable problem) { failure.set(problem); }
    }, "malformed-mrg-fixture");
    worker.setDaemon(true);
    worker.start();
    worker.join(1_000);
    assertFalse(worker.isAlive(), "Malformed MRG must not trap a worker in alias traversal");
    assertInstanceOf(RuntimeException.class, failure.get(), "Malformed MRG must fail instead of returning incomplete assets");
    assertTrue(failure.get().getMessage().contains(folder.toString()), "Failure must identify the affected directory");
  }

  private void cyclesAreRejectedPromptly() throws Exception {
    final Path folder = this.map("cycle", "0=1;4\n1=0;4\n");
    Files.write(folder.resolve("0"), new byte[4]);
    Files.write(folder.resolve("1"), new byte[4]);
    rejectedPromptly(folder);
  }

  private void missingRootsAndTargetsAreRejected() throws Exception {
    rejectedPromptly(this.map("missing-root", "0=0;4\n"));
    rejectedPromptly(this.map("unknown-target", "0=1;4\n"));
    rejectedPromptly(this.map("absent-target", "0=1;4\n1=;0\n"));
  }

  private void malformedMapsAreRejected() throws Exception {
    final String[] invalid = {"bad", "0=0;-1\n", "-1=-1;0\n", "0=0;0\n0=0;0\n", "1=1;0\n", "0=-2;0\n"};
    for(int i = 0; i < invalid.length; i++) {
      final Path folder = this.map("invalid-" + i, invalid[i]);
      Files.write(folder.resolve("0"), new byte[0]);
      Files.write(folder.resolve("1"), new byte[0]);
      rejectedPromptly(folder);
    }
  }

  private void aliasesKeepIndicesBytesAndMetadata() throws Exception {
    final Path folder = this.map("aliases", "2=2;4\n0=1;0\n1=2;2\n3=;0\n");
    final byte[] bytes = {11, 22, 33, 44};
    Files.write(folder.resolve("2"), bytes);
    final List<FileData> files = Loader.loadDirectorySync(folder);
    assertEquals(4, files.size());
    for(int i = 0; i < 3; i++) assertArrayEquals(bytes, files.get(i).getBytes());
    assertEquals(2, files.get(0).realFileIndex());
    assertFalse(files.get(0).hasVirtualSize());
    assertEquals(2, files.get(1).realFileIndex());
    assertTrue(files.get(1).hasVirtualSize());
    assertEquals(-1, files.get(2).realFileIndex());
    assertNull(files.get(3));
  }

  private void longAliasChainsResolveWithoutRecursion() throws Exception {
    final int count = 10_000;
    final var entries = new StringBuilder();
    for(int i = 0; i < count; i++) entries.append(i).append('=').append(i + 1).append(";4\n");
    entries.append(count).append('=').append(count).append(";4\n");
    final Path folder = this.map("long-chain", entries.toString());
    final byte[] bytes = {1, 2, 3, 4};
    Files.write(folder.resolve(String.valueOf(count)), bytes);
    final List<FileData> files = Loader.loadDirectorySync(folder);
    assertEquals(count + 1, files.size());
    for(int i = 0; i < count; i++) {
      assertEquals(count, files.get(i).realFileIndex());
      assertArrayEquals(bytes, files.get(i).getBytes());
    }
    assertEquals(-1, files.get(count).realFileIndex());
  }

  private void emptyEntriesAndDirectoriesRetainTheirMeaning() throws Exception {
    final Path folder = this.map("empty-types", "0=;0\n1=1;0\n2=2;0\n3=2;0\n");
    Files.write(folder.resolve("1"), new byte[0]);
    Files.createDirectory(folder.resolve("2"));
    final List<FileData> files = Loader.loadDirectorySync(folder);
    assertEquals(4, files.size());
    assertNull(files.get(0));
    for(int i = 1; i < 4; i++) {
      assertNotNull(files.get(i));
      assertEquals(0, files.get(i).size());
      assertEquals(-1, files.get(i).realFileIndex());
    }
    assertTrue(Loader.loadDirectorySync(this.map("empty-map", "")).isEmpty());
  }

  private void unmappedDirectoriesKeepNormalOrdering() throws Exception {
    final Path folder = Files.createDirectory(this.temporary.resolve("ordinary"));
    Files.write(folder.resolve("10"), new byte[]{10});
    Files.write(folder.resolve("2"), new byte[]{2});
    Files.createDirectory(folder.resolve("ignored-directory"));
    final List<FileData> files = Loader.loadDirectorySync(folder);
    assertEquals(2, files.size());
    assertArrayEquals(new byte[]{2}, files.get(0).getBytes());
    assertArrayEquals(new byte[]{10}, files.get(1).getBytes());
    assertTrue(Loader.loadDirectorySync(Files.createDirectory(this.temporary.resolve("empty-directory"))).isEmpty());
  }

  private static void failedFuture(final CompletableFuture<?> future) throws Exception {
    final boolean[] success = {false};
    final CompletableFuture<?> consumer = future.thenRun(() -> success[0] = true);
    final ExecutionException failure = assertThrows(ExecutionException.class, () -> consumer.get(2, TimeUnit.SECONDS));
    assertNotNull(failure.getCause());
    assertFalse(success[0], "Failure must not invoke a success-only consumer");
    assertTrue(future.isCompletedExceptionally());
    assertEquals(0, Loader.getLoadingFileCount(), "Every completed or failed read must settle its counter");
    assertEquals("fallback", future.handle((value, error) -> error == null ? "success" : "fallback").get(2, TimeUnit.SECONDS));
  }

  private void pathFileFailuresRemainExceptional() throws Exception {
    failedFuture(Loader.loadFile(this.temporary.resolve("missing-file")));
  }

  private void stringFileFailuresRemainExceptional() throws Exception {
    failedFuture(Loader.loadFile("missing-file"));
  }

  private void pathBatchFailuresRemainExceptional() throws Exception {
    final Path good = Files.write(this.temporary.resolve("good"), new byte[]{7});
    failedFuture(Loader.loadFiles(good, this.temporary.resolve("missing-file")));
  }

  private void stringBatchFailuresRemainExceptional() throws Exception {
    Files.createDirectory(this.temporary.resolve("files"));
    Files.write(this.temporary.resolve("files/good"), new byte[]{7});
    failedFuture(Loader.loadFiles("good", "missing-file"));
  }

  private void directoryFailuresRemainExceptional() throws Exception {
    failedFuture(Loader.loadDirectory(this.temporary.resolve("missing-directory")));
    failedFuture(Loader.loadDirectory("missing-directory"));
    failedFuture(Loader.loadDirectory(this.map("async-cycle", "0=1;4\n1=0;4\n")));
  }

  private void successfulAsyncReadsRetainOrderAndPathConventions() throws Exception {
    final Path folder = Files.createDirectories(this.temporary.resolve("files/TEST"));
    final byte[] first = {1, 2}, second = {3, 4};
    final Path a = Files.write(folder.resolve("one.bin"), first);
    final Path b = Files.write(folder.resolve("two.bin"), second);
    assertArrayEquals(first, Loader.loadFile(a).get(2, TimeUnit.SECONDS).getBytes());
    assertArrayEquals(first, Loader.loadFile("\\TEST\\one.bin;1").get(2, TimeUnit.SECONDS).getBytes());
    for(final List<FileData> files : List.of(Loader.loadFiles(b, a).get(2, TimeUnit.SECONDS), Loader.loadFiles("TEST/two.bin", "TEST/one.bin").get(2, TimeUnit.SECONDS))) {
      assertArrayEquals(second, files.get(0).getBytes());
      assertArrayEquals(first, files.get(1).getBytes());
    }
    assertTrue(Loader.loadFiles(new Path[0]).get(2, TimeUnit.SECONDS).isEmpty());
    assertTrue(Loader.loadFiles(new String[0]).get(2, TimeUnit.SECONDS).isEmpty());
    assertEquals(2, Loader.loadDirectory(folder).get(2, TimeUnit.SECONDS).size());
    assertEquals(2, Loader.loadDirectory("TEST").get(2, TimeUnit.SECONDS).size());
    assertEquals(0, Loader.getLoadingFileCount());
  }

  private void submissionFailureDoesNotLeakCounters() throws Exception {
    Async.shutdownNow();
    final Path missing = this.temporary.resolve("missing");
    failedFuture(Loader.loadFile(missing));
    failedFuture(Loader.loadFile("missing"));
    failedFuture(Loader.loadFiles(missing, missing));
    failedFuture(Loader.loadFiles("missing", "missing"));
    failedFuture(Loader.loadDirectory(missing));
    failedFuture(Loader.loadDirectory("missing"));
  }

  private void cancelledConsumerStillSettlesActualWork() throws Exception {
    final int workers = Runtime.getRuntime().availableProcessors();
    final var ready = new java.util.concurrent.CountDownLatch(workers);
    final var release = new java.util.concurrent.CountDownLatch(1);
    final var blockers = new CompletableFuture<?>[workers];
    try {
      for(int i = 0; i < workers; i++) blockers[i] = Async.run(() -> {
        ready.countDown();
        try { release.await(); }
        catch(final InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      });
      assertTrue(ready.await(3, TimeUnit.SECONDS), "All executor workers must reach the controlled queue barrier");
      final Path file = Files.write(this.temporary.resolve("queued"), new byte[]{7});
      final CompletableFuture<FileData> consumer = Loader.loadFile(file);
      assertEquals(1, Loader.getLoadingFileCount());
      assertTrue(consumer.cancel(false));
      assertTrue(consumer.isCancelled());
      assertEquals(1, Loader.getLoadingFileCount(), "Cancelling the consumer must not claim unfinished I/O is done");
      release.countDown();
      CompletableFuture.allOf(blockers).get(2, TimeUnit.SECONDS);
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while(Loader.getLoadingFileCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
      assertEquals(0, Loader.getLoadingFileCount(), "Actual queued read must settle its counter even after consumer cancellation");
      assertTrue(consumer.isCancelled());
    } finally { release.countDown(); }
  }
}
