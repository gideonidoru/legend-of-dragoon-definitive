package legend.definitive.artwork;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Explicit private extraction audit; never initializes the game or renderer. */
public final class SkySourceAudit {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("Requires extraction root, decoded source folder, and EnvHD sky resources");
    final Path files = Path.of(args[0]), sources = Path.of(args[1]), resources = Path.of(args[2]);
    final var entries = JsonParser.parseString(Files.readString(sources.resolve("inventory.json"))).getAsJsonArray();
    int checked = 0, reviewed = 0;
    for(final var entry : entries) {
      final var item = entry.getAsJsonObject();
      final String key = item.get("sourceMcqSha256").getAsString();
      final byte[] source = Files.readAllBytes(files.resolve(item.getAsJsonArray("sourceFiles").get(0).getAsString()));
      ArtworkResources.hash(source, key);
      final var actual = SkySource.decode(source);
      final var expected = ImageIO.read(sources.resolve(key + ".png").toFile());
      if(expected.getWidth() != actual.width || expected.getHeight() != actual.height) throw new IllegalStateException("Source dimensions differ: " + key);
      for(int y = 0; y < actual.height; y++) for(int x = 0; x < actual.width; x++) {
        final int p = (y * actual.width + x) * 4;
        final int rgba = (actual.data[p + 3] & 255) << 24 | (actual.data[p] & 255) << 16 | (actual.data[p + 1] & 255) << 8 | actual.data[p + 2] & 255;
        if(expected.getRGB(x, y) != rgba) throw new IllegalStateException("Decoder pixels differ: " + key + " at " + x + ',' + y);
      }
      checked++;
      final Path candidate = resources.resolve(key);
      if(Files.isRegularFile(candidate.resolve("manifest.json"))) {
        final byte[] metadata = Files.readAllBytes(candidate.resolve("manifest.json"));
        final String imagePath = SkyImage.resourcePath(metadata, "/envhd/skies/" + key);
        final Path png = resources.getParent().getParent().resolve(imagePath.substring(1));
        final var image = SkyImage.read(metadata, Files.readAllBytes(png), source);
        System.out.println("Reviewed source-bound artwork " + key + ' ' + image.width + 'x' + image.height);
        reviewed++;
      }
    }
    System.out.println("MCQ pixel agreement: " + checked + " / " + entries.size() + "; reviewed runtime resources: " + reviewed);
  }
}
