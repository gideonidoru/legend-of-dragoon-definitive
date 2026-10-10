package legend.core.renderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Small, bounded local include reader shared by desktop GL and GLES transpilation. */
public final class ShaderSources {
  private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
  private ShaderSources() { }

  public static String read(final Path file) throws IOException {
    final Path path = file.toRealPath();
    return read(path, path.getParent(), new HashSet<>(), new int[] {0});
  }

  private static String read(final Path file, final Path root, final Set<Path> active, final int[] bytes) throws IOException {
    if(!file.startsWith(root) || active.size() >= 8 || !active.add(file)) throw new IOException("Invalid shader include: " + file);
    try {
      final long length = Files.size(file);
      if(length > 1_048_576 - bytes[0]) throw new IOException("Shader sources exceed 1 MiB");
      bytes[0] += (int)length;
      final StringBuilder result = new StringBuilder();
      for(final String line : Files.readString(file).split("\\R", -1)) {
        final var match = INCLUDE.matcher(line);
        if(match.matches()) {
          result.append(read(file.getParent().resolve(match.group(1)).toRealPath(), root, active, bytes));
        } else {
          result.append(line).append('\n');
        }
      }
      return result.toString();
    } finally { active.remove(file); }
  }
}
