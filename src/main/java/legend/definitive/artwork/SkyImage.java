package legend.definitive.artwork;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import legend.game.textures.Image;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Validates a reviewed source-bound PNG before any renderer allocation. */
public final class SkyImage {
  private record Manifest(String sourceHash, String pngHash, String decodedHash, int scale, String review, String imageFile) {
    String resourcePath(final String base) {
      return (this.decodedHash == null ? base : "/envhd/sky-images/" + this.decodedHash) + '/' + this.imageFile;
    }
  }
  private SkyImage() { }
  public static Image read(final byte[] metadata, final byte[] png, final byte[] source) throws IOException {
    return read(parse(metadata), png, source);
  }

  public static Image load(final Class<?> owner, final String base, final byte[] source) throws IOException {
    final Manifest manifest = parse(ArtworkResources.read(owner, base + "/manifest.json", 65536));
    ArtworkResources.hash(source, manifest.sourceHash);
    return read(manifest, ArtworkResources.read(owner, manifest.resourcePath(base), 32 * 1024 * 1024), source);
  }

  /** Shared resources live in a fixed namespace keyed only by validated pixel hashes. */
  public static String resourcePath(final byte[] metadata, final String base) throws IOException {
    return parse(metadata).resourcePath(base);
  }

  private static Manifest parse(final byte[] metadata) throws IOException {
    if(metadata.length > 65536) throw new IOException("Sky metadata exceeds bounds");
    String sourceHash = null, pngHash = null, decodedHash = null, review = null;
    String imageFile = "image-v1.png";
    int scale = 0;
    try(final var json = new JsonReader(new StringReader(new String(metadata, StandardCharsets.UTF_8)))) {
      json.setStrictness(Strictness.STRICT);
      final var keys = new HashSet<String>();
      json.beginObject();
      while(json.hasNext()) {
        final String key = json.nextName();
        if(!keys.add(key)) throw new IOException("Duplicate sky metadata key");
        switch(key) {
          case "sourceMcqSha256" -> sourceHash = json.nextString();
          case "outputSha256" -> pngHash = json.nextString();
          case "reviewStatus" -> review = json.nextString();
          case "decodedRgbaSha256" -> decodedHash = json.nextString();
          case "imageFile" -> imageFile = json.nextString();
          case "scale" -> scale = json.nextInt();
          default -> json.skipValue();
        }
      }
      json.endObject();
      if(json.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IOException("Trailing sky metadata");
    } catch(final IllegalStateException | NumberFormatException failure) { throw new IOException("Invalid sky metadata", failure); }
    if(scale < 1 || scale > 8 || !("visual-reviewed-native-pending".equals(review) || "native-accepted".equals(review))) throw new IOException("Sky artwork has not passed visual review");
    if(sourceHash == null || !sourceHash.matches("[0-9a-f]{64}") || pngHash == null || !pngHash.matches("[0-9a-f]{64}") || decodedHash != null && !decodedHash.matches("[0-9a-f]{64}")) throw new IOException("Invalid sky artwork hash");
    if(!imageFile.matches("image-v[1-9][0-9]{0,3}\\.png")) throw new IOException("Invalid sky image resource name");
    return new Manifest(sourceHash, pngHash, decodedHash, scale, review, imageFile);
  }

  private static Image read(final Manifest manifest, final byte[] png, final byte[] source) throws IOException {
    ArtworkResources.hash(source, manifest.sourceHash);
    ArtworkResources.hash(png, manifest.pngHash);
    final Image original = SkySource.decode(source);
    if(manifest.decodedHash != null && !manifest.decodedHash.equals(SkySource.fingerprint(original))) throw new IOException("Shared sky image differs from decoded source artwork");
    final Image image = ArtworkResources.image(png, original.width * manifest.scale, original.height * manifest.scale);
    return SkySource.applyCoverage(original, image);
  }
}
