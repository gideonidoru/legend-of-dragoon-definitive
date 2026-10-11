package legend.definitive.artwork;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipFile;
/** Resource-only parts belong to EnvHD; they add no mod IDs or installer choices. */
public final class FieldArtworkBundle {
  private FieldArtworkBundle() { }
  public static String name(final String key) {
    if(!key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid field source key");
    return "EnvHD-Fields-v0.1.0-part-" + Character.digit(key.charAt(0),16)/4 + ".zip";
  }
  public static byte[] read(final Path mods, final String key) throws IOException {
    try(final ZipFile archive = new ZipFile(mods.resolve(name(key)).toFile())) {
      final var entry=archive.getEntry("envhd/fields/"+key+"/image-v1.png");
      if(entry==null || entry.isDirectory() || entry.getSize()<0 || entry.getSize()>32*1024*1024) throw new IOException("Missing or oversized EnvHD field resource");
      try(final var stream=archive.getInputStream(entry)) {
        final byte[] png=stream.readNBytes(32*1024*1024+1);
        if(png.length!=entry.getSize()) throw new IOException("Field bundle size differs");
        return png;
      }
    }
  }
}
