package legend.definitive.mods;

import org.apache.logging.log4j.LogManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Managed visual defaults are an effective load selection, independent of campaign/save bytes. */
public final class ManagedModProfile {
  public static final String PROFILE_PROPERTY = "definitive.artworkProfile";
  public static final String PREFERENCES_PROPERTY = "definitive.hdModPreferences";
  public static final Set<String> MOD_IDS = Set.of("scbackgroundhd", "envhd", "charhd", "uihd", "fxhd", "fmvhd", "modelshd");
  private static final Set<String> OPTIONAL_BACKGROUNDS_AND_MODELS = Set.of("scbackgroundhd", "modelshd");
  private static final int MAX_BYTES = 8192;
  private record Configuration(boolean backgroundsAndModels, Path preferences) { }
  private ManagedModProfile() { }

  /** No explicit managed properties means unchanged upstream selection behavior. */
  private static Configuration configuration() {
    final String profile = System.getProperty(PROFILE_PROPERTY, "");
    final String file = System.getProperty(PREFERENCES_PROPERTY, "");
    if((!profile.equals("hd") && !profile.equals("original")) || file.isBlank()) return null;
    try {
      final Path path = Path.of(file).normalize();
      if(!path.isAbsolute() || path.getFileName() == null || !path.getFileName().toString().equals("definitive-hd-mods.properties")) return null;
      return new Configuration(profile.equals("hd"), path);
    } catch(final java.nio.file.InvalidPathException invalid) { return null; }
  }

  public static boolean isManaged() { return configuration() != null; }

  /** Launcher-owned optional modules cannot be selected while its original profile is active. */
  public static boolean isSelectable(final String id) {
    final Configuration configuration = configuration();
    return configuration == null || configuration.backgroundsAndModels || !OPTIONAL_BACKGROUNDS_AND_MODELS.contains(id);
  }

  public static synchronized Set<String> effective(final Set<String> requested, final Set<String> installed) {
    return effective(requested, installed, Map.of());
  }

  /** Staged choices affect previews without persisting a cancelled menu or new campaign. */
  public static synchronized Set<String> effective(final Set<String> requested, final Set<String> installed, final Map<String, Boolean> staged) {
    final Configuration configuration = configuration();
    if(configuration == null) return Set.copyOf(requested);
    final Set<String> result = new HashSet<>(requested);
    result.removeAll(MOD_IDS);
    final Properties preferences;
    try { preferences = read(configuration.preferences); }
    catch(final IOException failure) {
      LogManager.getLogger(ManagedModProfile.class).warn("Could not read HD module choices; retaining the campaign's available choices: {}", failure.getMessage());
      // Never turn an unreadable opt-out into an assumed opt-in. The narrow profile still applies.
      for(final String id : requested) if(allowed(id, installed, configuration)) result.add(id);
      applyStaged(result, installed, configuration, staged);
      return Set.copyOf(result);
    }
    for(final String id : MOD_IDS) {
      if(allowed(id, installed, configuration) && !preferences.getProperty(id, "true").equals("false")) result.add(id);
    }
    applyStaged(result, installed, configuration, staged);
    return Set.copyOf(result);
  }

  private static void applyStaged(final Set<String> result, final Set<String> installed, final Configuration configuration, final Map<String, Boolean> staged) {
    for(final var choice : staged.entrySet()) {
      if(allowed(choice.getKey(), installed, configuration)) {
        if(choice.getValue()) result.add(choice.getKey());
        else result.remove(choice.getKey());
      }
    }
  }

  private static boolean allowed(final String id, final Set<String> installed, final Configuration configuration) {
    return MOD_IDS.contains(id) && installed.contains(id) && (configuration.backgroundsAndModels || !OPTIONAL_BACKGROUNDS_AND_MODELS.contains(id));
  }

  /** Called only after explicit choices are accepted. */
  public static synchronized void recordSelection(final String id, final boolean enabled) throws IOException {
    recordSelections(Map.of(id, enabled));
  }

  /** All accepted visual choices replace the preferences in one atomic write. */
  public static synchronized void recordSelections(final Map<String, Boolean> choices) throws IOException {
    final Configuration configuration = configuration();
    if(configuration == null) return;
    final Map<String, Boolean> managed = new java.util.HashMap<>();
    for(final var choice : choices.entrySet()) {
      if(MOD_IDS.contains(choice.getKey())) {
        if(choice.getValue() == null) throw new IOException("HD module choice is missing");
        managed.put(choice.getKey(), choice.getValue());
      }
    }
    if(managed.isEmpty()) return;
    final Properties preferences = read(configuration.preferences);
    managed.forEach((id, enabled) -> preferences.setProperty(id, Boolean.toString(enabled)));
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    preferences.store(bytes, "Definitive HD module choices");
    if(bytes.size() > MAX_BYTES) throw new IOException("HD module choices exceed their size limit");
    checked(configuration.preferences);
    Files.createDirectories(configuration.preferences.getParent());
    checked(configuration.preferences);
    final Path temporary = Files.createTempFile(configuration.preferences.getParent(), ".definitive-hd-mods-", ".tmp");
    try {
      try(final FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
        final ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray());
        while(data.hasRemaining()) file.write(data);
        file.force(true);
      }
      checked(configuration.preferences);
      Files.move(temporary, configuration.preferences, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally { Files.deleteIfExists(temporary); }
  }

  private static Properties read(final Path path) throws IOException {
    checked(path);
    final Properties preferences = new Properties();
    final ByteBuffer data = ByteBuffer.allocate(MAX_BYTES + 1);
    try(final FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      while(data.hasRemaining() && file.read(data) > 0) { }
    } catch(final NoSuchFileException absent) { return preferences; }
    if(data.position() > MAX_BYTES) throw new IOException("HD module choices exceed their size limit");
    try { preferences.load(new ByteArrayInputStream(data.array(), 0, data.position())); }
    catch(final IllegalArgumentException malformed) { throw new IOException("HD module choices are malformed", malformed); }
    for(final String id : MOD_IDS) {
      final String value = preferences.getProperty(id);
      if(value != null && !value.equals("true") && !value.equals("false")) throw new IOException("HD module choice is invalid: " + id);
    }
    return preferences;
  }

  private static void checked(final Path path) throws IOException {
    for(Path current = path; current != null; current = current.getParent()) {
      if(Files.isSymbolicLink(current)) throw new IOException("Linked HD module preferences are not supported");
    }
    if(Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("HD module preferences are not a regular file");
  }
}
