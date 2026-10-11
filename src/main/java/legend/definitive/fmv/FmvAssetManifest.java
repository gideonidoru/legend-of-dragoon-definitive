package legend.definitive.fmv;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** The complete approved cinematic data contract, shared by packaging, mod and manager. */
public record FmvAssetManifest(Map<String, Film> films, String legacyBundleSha256, String provenanceSha256, String noticeSha256) {
  public static final String ROOT = "gfx/fmvhd/";
  public static final String FILE = ROOT + "manifest.properties";
  public static final List<String> NAMES = List.of("OPENH", "DEMOH", "DEMO2", "WAR1H", "TVRH", "GOAST", "ROZEH", "TREEH", "WAR2H", "BLACKH", "DRAGON1", "DENIN", "DENIN2", "DRAGON2", "DEIASH", "MOONH", "ENDING1H", "ENDING2H");
  private static final List<String> FIELDS = List.of("sha256", "sourceSha256", "bytes", "frames", "duration", "width", "height", "fps");
  public record Source(String sha256, String sourceSha256, long bytes) { }
  public record Film(String name, Source source, long frames, BigDecimal duration) {
    public String path() { return ROOT + "videos/" + this.source.sha256 + ".mp4"; }
  }
  public FmvAssetManifest { films = Map.copyOf(films); }

  public static Properties properties(final InputStream input) throws IOException {
    if(input == null) throw new IOException("FMVHD manifest is missing");
    final byte[] bytes = input.readNBytes(65537);
    if(bytes.length > 65536) throw new IOException("FMVHD manifest exceeds its bound");
    final Properties properties = new Properties() {
      @Override public synchronized Object put(final Object key, final Object value) {
        if(this.containsKey(key)) throw new IllegalArgumentException("Duplicate FMVHD property");
        return super.put(key, value);
      }
    };
    try { properties.load(new java.io.ByteArrayInputStream(bytes)); }
    catch(final IllegalArgumentException failure) { throw new IOException("Invalid FMVHD manifest", failure); }
    return properties;
  }
  private static String digest(final Properties p, final String key) throws IOException {
    final String value = p.getProperty(key, "");
    if(!value.matches("[a-f0-9]{64}")) throw new IOException("Invalid FMVHD digest: " + key);
    return value;
  }
  private static long number(final Properties p, final String key) throws IOException {
    final String value = p.getProperty(key, "");
    if(!value.matches("[1-9][0-9]{0,9}")) throw new IOException("Invalid FMVHD number: " + key);
    return Long.parseLong(value);
  }
  private static Source source(final Properties p, final String name) throws IOException {
    final long bytes = number(p, name + ".bytes");
    if(bytes > 512L * 1024 * 1024) throw new IOException("Oversized FMVHD movie");
    return new Source(digest(p, name + ".sha256"), digest(p, name + ".sourceSha256"), bytes);
  }
  public static FmvAssetManifest read(final InputStream input) throws IOException {
    final Properties p = properties(input);
    final Set<String> expected = new HashSet<>(Set.of("format", "legacyBundleSha256", "provenanceSha256", "noticeSha256"));
    final Map<String, Film> films = new HashMap<>();
    final Map<String, Long> sizes = new HashMap<>();
    for(final String name : NAMES) {
      for(final String field : FIELDS) expected.add(name + '.' + field);
      final Source source = source(p, name);
      final Long priorSize = sizes.putIfAbsent(source.sha256, source.bytes);
      if(priorSize != null && priorSize != source.bytes) throw new IOException("Shared movie identity has conflicting sizes");
      final long frames = number(p, name + ".frames");
      if(frames > 1_000_000 || number(p, name + ".width") != 1280 || number(p, name + ".height") != 768 || number(p, name + ".fps") != 15) throw new IOException("Unsupported FMVHD timing or dimensions");
      final String value = p.getProperty(name + ".duration", "");
      if(!value.matches("[0-9]{1,6}(\\.[0-9]{1,16})?")) throw new IOException("Invalid FMVHD duration");
      final BigDecimal duration = new BigDecimal(value);
      if(BigDecimal.valueOf(frames).divide(BigDecimal.valueOf(15), 16, RoundingMode.HALF_UP).subtract(duration).abs().compareTo(new BigDecimal("0.001")) > 0) throw new IOException("FMVHD duration disagrees with native cadence");
      films.put(name, new Film(name, source, frames, duration));
    }
    if(!"1".equals(p.getProperty("format")) || !p.stringPropertyNames().equals(expected)) throw new IOException("FMVHD film inventory is incomplete or unexpected");
    return new FmvAssetManifest(films, digest(p, "legacyBundleSha256"), digest(p, "provenanceSha256"), digest(p, "noticeSha256"));
  }
  public static Map<String, Source> readLegacy(final InputStream input) throws IOException {
    final Properties p = properties(input);
    final Set<String> expected = new HashSet<>();
    final Map<String, Source> result = new HashMap<>();
    for(final String name : NAMES) {
      for(final String field : List.of("sha256", "sourceSha256", "bytes")) expected.add(name + '.' + field);
      result.put(name, source(p, name));
    }
    if(!p.stringPropertyNames().equals(expected)) throw new IOException("Legacy FMVHD inventory is incomplete or unexpected");
    return Map.copyOf(result);
  }
  public Film replacement(final String nativeFile, final String nativeSha256) {
    if(!nativeFile.matches("STR/[A-Z0-9]+\\.IKI")) return null;
    final Film film = this.films.get(nativeFile.substring(4, nativeFile.length() - 4));
    return film != null && film.source.sourceSha256.equals(nativeSha256) ? film : null;
  }
  public Set<String> paths() {
    final Set<String> paths = new HashSet<>(Set.of(FILE, ROOT + "NOTICE.txt", ROOT + "provenance.json"));
    for(final Film film : this.films.values()) paths.add(film.path());
    return Set.copyOf(paths);
  }
}
