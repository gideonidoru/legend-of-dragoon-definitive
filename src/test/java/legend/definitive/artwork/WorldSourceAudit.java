package legend.definitive.artwork;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Compare private Python decodes with bounded Java decoders; never starts rendering. */
public final class WorldSourceAudit {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("Requires extraction root, private batch folder and runtime assets");
    final Path files = Path.of(args[0]), batch = Path.of(args[1]), resources = Path.of(args[2]);
    final var entries = JsonParser.parseString(Files.readString(batch.resolve("candidates.json"))).getAsJsonArray();
    int checked = 0, selected = 0;
    for(final var entry : entries) {
      final var item = entry.getAsJsonObject(); final String key = item.get("sourceSha256").getAsString();
      final byte[] source = Files.readAllBytes(files.resolve(item.getAsJsonArray("sourceFiles").get(0).getAsString()));
      ArtworkResources.hash(source, key);
      final boolean backdrop = "world-backdrop".equals(item.get("kind").getAsString());
      final var actual = backdrop ? SkySource.decode(source) : LocationArtwork.decode(source);
      if(!SkySource.fingerprint(actual).equals(item.get("decodedRgbaSha256").getAsString())) throw new IllegalStateException("World pixel fingerprint differs: " + key);
      final var expected = ImageIO.read(batch.resolve("private-work/" + key + "-source.png").toFile());
      if(actual.width != expected.getWidth() || actual.height != expected.getHeight()) throw new IllegalStateException("World source dimensions differ");
      for(int p = 0; p < actual.width * actual.height; p++) {
        final int rgba = (actual.data[p * 4 + 3] & 255) << 24 | (actual.data[p * 4] & 255) << 16 | (actual.data[p * 4 + 1] & 255) << 8 | actual.data[p * 4 + 2] & 255;
        if(expected.getRGB(p % actual.width, p / actual.width) != rgba) throw new IllegalStateException("World source pixels differ: " + key);
      }
      checked++;
      if(backdrop) {
        final Path manifest = resources.resolve("envhd/world/backdrops/" + key + "/manifest.json");
        if(Files.isRegularFile(manifest)) {
          final byte[] metadata = Files.readAllBytes(manifest);
          SkyImage.read(metadata, Files.readAllBytes(resources.resolve(SkyImage.resourcePath(metadata, "unused").substring(1))), source); selected++;
        }
      } else {
        final Path base = resources.resolve("envhd/world/locations/" + key);
        if(Files.isRegularFile(base.resolve("manifest.properties"))) {
          final var metadata = new java.util.Properties(); metadata.load(new ByteArrayInputStream(Files.readAllBytes(base.resolve("manifest.properties"))));
          LocationArtwork.read(metadata, Files.readAllBytes(base.resolve("image-v1.png")), source); selected++;
        }
      }
    }
    System.out.println("World source pixel agreement: " + checked + '/' + entries.size() + "; reviewed runtime bindings: " + selected);
  }
}
