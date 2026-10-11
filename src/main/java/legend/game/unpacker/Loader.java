package legend.game.unpacker;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import legend.core.Async;
import legend.core.DebugHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public final class Loader {
  private Loader() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);

  private static final Pattern MRG_ENTRY = Pattern.compile("[=;]");

  private static final AtomicInteger LOADING_COUNT = new AtomicInteger();

  public static Path resolve(final String name) {
    return Unpacker.ROOT.resolve(fixPath(name));
  }

  public static Path resolve(final Path name) {
    return Unpacker.ROOT.resolve(name);
  }

  public static FileData loadFileSync(final Path path) {
    LOGGER.info("Loading file %s", path);

    try {
      return new FileData(Files.readAllBytes(path));
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load file " + path, e);
    }
  }

  public static FileData loadFileSync(final String name) {
    return loadFileSync(Unpacker.ROOT.resolve(fixPath(name)));
  }

  public static CompletableFuture<FileData> loadFile(final Path path) {
    final int total = LOADING_COUNT.incrementAndGet();
    final StackWalker.StackFrame frame = DebugHelper.getCallerFrame();
    LOGGER.info("Queueing file %s (total queued: %d) from %s.%s(%s:%d)", path, total, frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber());

    return trackLoad(schedule(() -> loadFileSync(path)), 1, "File", path.toString());
  }

  public static CompletableFuture<FileData> loadFile(final String name) {
    final int total = LOADING_COUNT.incrementAndGet();
    LOGGER.info("Queueing file %s (total queued: %d)", name, total);

    return trackLoad(schedule(() -> loadFileSync(name)), 1, "File", name);
  }

  public static CompletableFuture<List<FileData>> loadFiles(final String... files) {
    final int total = LOADING_COUNT.updateAndGet(i -> i + files.length);
    LOGGER.info("Queueing files %s (total queued: %d)", Arrays.toString(files), total);

    final FileData[] data = new FileData[files.length];
    final CompletableFuture<FileData>[] futures = new CompletableFuture[files.length];
    for(int i = 0; i < files.length; i++) {
      final int finalI = i;
      futures[i] = schedule(() -> data[finalI] = loadFileSync(files[finalI]));
    }

    return trackLoad(CompletableFuture.allOf(futures).thenApply(v -> List.of(data)), files.length, "Files", Arrays.toString(files));
  }

  public static CompletableFuture<List<FileData>> loadFiles(final Path... files) {
    final int total = LOADING_COUNT.updateAndGet(i -> i + files.length);
    LOGGER.info("Queueing files %s (total queued: %d)", Arrays.toString(files), total);

    final FileData[] data = new FileData[files.length];
    final CompletableFuture<FileData>[] futures = new CompletableFuture[files.length];
    for(int i = 0; i < files.length; i++) {
      final int finalI = i;
      futures[i] = schedule(() -> data[finalI] = loadFileSync(files[finalI]));
    }

    return trackLoad(CompletableFuture.allOf(futures).thenApply(v -> List.of(data)), files.length, "Files", Arrays.toString(files));
  }

  public static CompletableFuture<List<FileData>> loadDirectory(final String name) {
    final int total = LOADING_COUNT.incrementAndGet();
    LOGGER.info("Queueing directory %s (total queued: %d)", name, total);

    return trackLoad(schedule(() -> loadDirectorySync(name)), 1, "Directory", name);
  }

  public static CompletableFuture<List<FileData>> loadDirectory(final Path dir) {
    final int total = LOADING_COUNT.incrementAndGet();

    final StackWalker.StackFrame frame = DebugHelper.getCallerFrame();
    LOGGER.info("Queueing directory %s (total queued: %d) from %s.%s(%s:%d)", dir, total, frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber());

    return trackLoad(schedule(() -> loadDirectorySync(dir)), 1, "Directory", dir.toString());
  }

  private static <T> CompletableFuture<T> schedule(final Supplier<T> operation) {
    try {
      return Async.run(operation);
    } catch(final RejectedExecutionException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private static <T> CompletableFuture<T> trackLoad(final CompletableFuture<T> work, final int count, final String kind, final String path) {
    final var completion = new CompletableFuture<T>();
    // Keep bookkeeping on the actual work, independent of consumer cancellation.
    work.whenComplete((result, failure) -> {
      final int remaining = LOADING_COUNT.addAndGet(-count);
      if(failure == null) {
        LOGGER.info("%s %s loaded (remaining queued: %d)", kind, path, remaining);
        completion.complete(result);
      } else {
        LOGGER.error(kind + " " + path + " failed (remaining queued: " + remaining + ')', failure);
        completion.completeExceptionally(failure);
      }
    });
    return completion;
  }

  public static List<FileData> loadDirectorySync(final String name) {
    return loadDirectorySync(Unpacker.ROOT.resolve(fixPath(name)));
  }

  public static List<FileData> loadDirectorySync(final Path dir) {
    LOGGER.info("Loading directory %s", dir);

    final Path mrg = dir.resolve("mrg");

    if(Files.exists(mrg)) {
      try(final BufferedReader reader = Files.newBufferedReader(mrg)) {
        final Int2IntMap fileMap = new Int2IntOpenHashMap();
        final Int2IntMap virtualSizeMap = new Int2IntOpenHashMap();

        reader.lines().forEach(line -> {
          final String[] parts = MRG_ENTRY.split(line);

          if(parts.length != 3) {
            throw new RuntimeException("Invalid MRG entry in " + dir + ": " + line);
          }

          final int virtual = Integer.parseInt(parts[0]);
          final int size = Integer.parseInt(parts[2]);
          if(virtual < 0 || size < 0 || fileMap.containsKey(virtual)) {
            throw new RuntimeException("Invalid or duplicate MRG entry in " + dir + ": " + line);
          }

          // Indicates no file
          if(parts[1].isBlank()) {
            fileMap.put(virtual, -1);
            virtualSizeMap.put(virtual, 0);
            return;
          }

          final int real = Integer.parseInt(parts[1]);
          if(real < -1) throw new RuntimeException("Invalid MRG target in " + dir + ": " + line);
          fileMap.put(virtual, real);
          virtualSizeMap.put(virtual, size);
        });

        final List<FileData> files = new ArrayList<>(Arrays.asList(new FileData[fileMap.size()]));
        final Int2IntMap resolved = new Int2IntOpenHashMap();
        final var directories = new IntOpenHashSet();
        for(int index = 0; index < fileMap.size(); index++) {
          if(!fileMap.containsKey(index)) throw new RuntimeException("Non-contiguous MRG indices in " + dir);
        }

        // Add real files
        for(int index = 0; index < fileMap.size(); index++) {
          if(fileMap.get(index) != index) continue;

          try {
            final Path file = dir.resolve(String.valueOf(index));
            if(Files.isRegularFile(file)) {
              files.set(index, new FileData(Files.readAllBytes(file)));
            } else if(Files.isDirectory(file)) {
              files.set(index, new FileData(new byte[0]));
              directories.add(index);
            } else {
              throw new IOException("Missing MRG file " + file);
            }
            resolved.put(index, index);
          } catch(final IOException e) {
            throw new RuntimeException("Failed to load directory " + dir, e);
          }
        }

        // Add virtual files
        for(int virtual = 0; virtual < fileMap.size(); virtual++) {
          if(fileMap.get(virtual) == virtual || fileMap.get(virtual) == -1) continue;
          final int real = resolveMrgAlias(virtual, fileMap, resolved, dir);
          if(!directories.contains(real)) {
            files.set(virtual, FileData.virtual(files.get(real), virtualSizeMap.get(virtual), real));
          } else {
            // Directory entries and their aliases retain the legacy empty placeholder.
            files.set(virtual, new FileData(new byte[0]));
          }
        }

        return files;
      } catch(final IOException | NumberFormatException e) {
        throw new RuntimeException("Failed to load directory " + dir, e);
      }
    }

    try(final DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
      final List<FileData> files = new ArrayList<>();

      StreamSupport.stream(ds.spliterator(), false)
        .filter(Files::isRegularFile)
        .sorted((path1, path2) -> {
          final String filename1 = path1.getFileName().toString();
          final String filename2 = path2.getFileName().toString();

          try {
            return Integer.compare(Integer.parseInt(filename1), Integer.parseInt(filename2));
          } catch(final NumberFormatException ignored) {
          }

          return String.CASE_INSENSITIVE_ORDER.compare(filename1, filename2);
        })
        .forEach(child -> {
          try {
            files.add(new FileData(Files.readAllBytes(child)));
          } catch(final IOException e) {
            throw new RuntimeException("Failed to load directory " + dir, e);
          }
        });

      return files;
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load directory " + dir, e);
    }
  }

  private static int resolveMrgAlias(final int virtual, final Int2IntMap fileMap, final Int2IntMap resolved, final Path dir) {
    if(resolved.containsKey(virtual)) return resolved.get(virtual);
    final var path = new IntArrayList();
    final var visited = new IntOpenHashSet();
    int real = virtual;
    while(!resolved.containsKey(real)) {
      if(!fileMap.containsKey(real) || fileMap.get(real) == -1) {
        throw new RuntimeException("Missing MRG alias target " + real + " for entry " + virtual + " in " + dir);
      }
      if(!visited.add(real)) throw new RuntimeException("Cyclic MRG alias for entry " + virtual + " in " + dir);
      path.add(real);
      real = fileMap.get(real);
    }
    final int root = resolved.get(real);
    for(final int index : path) resolved.put(index, root);
    return root;
  }

  public static int getLoadingFileCount() {
    return LOADING_COUNT.get();
  }

  public static boolean exists(final String name) {
    return Files.exists(Unpacker.ROOT.resolve(fixPath(name)));
  }

  public static boolean isDirectory(final String name) {
    return Files.isDirectory(Unpacker.ROOT.resolve(fixPath(name)));
  }

  private static String fixPath(String name) {
    if(name.contains(";")) {
      name = name.substring(0, name.lastIndexOf(';'));
    }

    if(name.startsWith("\\") || name.startsWith("/")) {
      name = name.substring(1);
    }

    return name.replace('\\', '/');
  }
}
