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
  private SkyImage() { }
  public static Image read(final byte[] metadata, final byte[] png, final byte[] source) throws IOException {
    if(metadata.length > 65536) throw new IOException("Sky metadata exceeds bounds");
    String sourceHash = null, pngHash = null, review = null;
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
          case "scale" -> scale = json.nextInt();
          default -> json.skipValue();
        }
      }
      json.endObject();
      if(json.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IOException("Trailing sky metadata");
    } catch(final IllegalStateException | NumberFormatException failure) { throw new IOException("Invalid sky metadata", failure); }
    if(scale < 1 || scale > 8 || !("visual-reviewed-native-pending".equals(review) || "native-accepted".equals(review))) throw new IOException("Sky artwork has not passed visual review");
    ArtworkResources.hash(source, sourceHash);
    ArtworkResources.hash(png, pngHash);
    final Image original = SkySource.decode(source);
    final Image image = ArtworkResources.image(png, original.width * scale, original.height * scale);
    return SkySource.applyCoverage(source, image);
  }
}
