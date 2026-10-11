package legend.definitive.fmv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Synthetic public test bytes; no original or enhanced copyrighted footage. */
public final class FmvSplitFixture {
  public final Map<String, byte[]> movies = new LinkedHashMap<>();
  public final byte[] provenance = "synthetic public provenance".getBytes(StandardCharsets.UTF_8);
  public final byte[] notice = "synthetic asset notice".getBytes(StandardCharsets.UTF_8);
  public FmvSplitFixture() {
    for(final String name : FmvAssetManifest.NAMES) this.movies.put(name, ("synthetic movie " + name).getBytes(StandardCharsets.UTF_8));
  }
  public static String sha(final byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
  public Properties manifest(final boolean legacy) throws Exception {
    final Properties p = new Properties();
    if(!legacy) {
      p.setProperty("format", "1"); p.setProperty("legacyBundleSha256", "a".repeat(64));
      p.setProperty("provenanceSha256", sha(this.provenance)); p.setProperty("noticeSha256", sha(this.notice));
    }
    for(final var entry : this.movies.entrySet()) {
      final String name = entry.getKey();
      p.setProperty(name + ".sha256", sha(entry.getValue()));
      p.setProperty(name + ".sourceSha256", sha(("synthetic native source " + name).getBytes(StandardCharsets.UTF_8)));
      p.setProperty(name + ".bytes", Integer.toString(entry.getValue().length));
      if(!legacy) {
        p.setProperty(name + ".frames", "15"); p.setProperty(name + ".duration", "1");
        p.setProperty(name + ".width", "1280"); p.setProperty(name + ".height", "768"); p.setProperty(name + ".fps", "15");
      }
    }
    return p;
  }
  public static byte[] bytes(final Properties properties) {
    final StringBuilder text = new StringBuilder();
    for(final String key : new TreeSet<>(properties.stringPropertyNames())) text.append(key).append('=').append(properties.getProperty(key)).append('\n');
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }
  public Path jar(final Path packageRoot, final boolean legacy, final String code) throws Exception {
    final Path jar = packageRoot.resolve("bundled-mods/FMVHD-v0.1.0.jar"); Files.createDirectories(jar.getParent());
    try(final var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
      zip.putNextEntry(new ZipEntry("fmvhd/assets.properties")); zip.write(bytes(manifest(legacy))); zip.closeEntry();
      zip.putNextEntry(new ZipEntry("synthetic-code.txt")); zip.write(code.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
      if(legacy) for(final var entry : this.movies.entrySet()) {
        zip.putNextEntry(new ZipEntry("fmvhd/videos/" + entry.getKey() + ".mp4")); zip.write(entry.getValue()); zip.closeEntry();
      }
    }
    return jar;
  }
  public void split(final Path packageRoot, final String code) throws Exception {
    final Path data = packageRoot.resolve("gfx/fmvhd"); Files.createDirectories(data.resolve("videos"));
    Files.write(data.resolve("manifest.properties"), bytes(manifest(false)));
    Files.write(data.resolve("provenance.json"), this.provenance); Files.write(data.resolve("NOTICE.txt"), this.notice);
    for(final byte[] video : this.movies.values()) Files.write(data.resolve("videos/" + sha(video) + ".mp4"), video);
    jar(packageRoot, false, code);
  }
}
