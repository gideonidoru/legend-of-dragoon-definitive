package legend.definitive.artwork;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Independently compare all private decodes and packed atlases without rendering. */
public final class WorldTerrainSourceAudit {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IllegalArgumentException("Requires extraction, private batch and private atlas folder");
    final Path base = Path.of(args[0]).resolve("SECT/DRGN0.BIN"), batch = Path.of(args[1]), atlases = Path.of(args[2]);
    final var plan = JsonParser.parseString(Files.readString(batch.resolve("source-plan.json"))).getAsJsonObject();
    int materials = 0, mapped = 0, scenes = 0;
    final var unique = new java.util.HashSet<String>();
    for(final var value : plan.getAsJsonArray("scenes")) {
      final var expected = value.getAsJsonObject(); final int bankIndex = expected.get("bank").getAsInt();
      final var bank = new ArrayList<byte[]>();
      for(final var file : expected.getAsJsonArray("bankFiles")) bank.add(Files.readAllBytes(base.resolve(Integer.toString(5697 + bankIndex)).resolve(file.getAsJsonObject().get("file").getAsString())));
      final byte[] model = Files.readAllBytes(base.resolve(Integer.toString(5705 + bankIndex)));
      final var actual = WorldTerrainSource.decode(model, bank);
      if(!actual.modelHash().equals(expected.get("modelSha256").getAsString()) || !actual.bankHash().equals(expected.get("bankSha256").getAsString()) || actual.materials().size() != expected.getAsJsonArray("materials").size()) throw new IllegalStateException("World source identities differ");
      for(int i = 0; i < actual.materials().size(); i++) {
        final var a = actual.materials().get(i); final var e = expected.getAsJsonArray("materials").get(i).getAsJsonObject();
        if(a.page() != e.get("page").getAsInt() || a.clut() != e.get("clut").getAsInt() || a.held() != "held-native-uv-mapping".equals(e.get("status").getAsString())) throw new IllegalStateException("World reference mapping differs");
        if(!a.held()) {
          if(!a.fingerprint().equals(e.get("decodedRgbaSha256").getAsString()) || a.u() != e.getAsJsonArray("sourceUvOrigin").get(0).getAsInt() || a.v() != e.getAsJsonArray("sourceUvOrigin").get(1).getAsInt()) throw new IllegalStateException("World source pixel identity differs");
          final var original = javax.imageio.ImageIO.read(batch.resolve("private-work/" + a.fingerprint() + "-source-stp.png").toFile());
          if(original.getWidth() != a.image().width || original.getHeight() != a.image().height) throw new IllegalStateException("World source dimensions differ");
          for(int p = 0; p < a.image().data.length; p += 4) {
            final var rgba = a.image().data;
            final int colour = (rgba[p + 3] & 255) << 24 | (rgba[p] & 255) << 16 | (rgba[p + 1] & 255) << 8 | rgba[p + 2] & 255;
            if(original.getRGB(p / 4 % original.getWidth(), p / 4 / original.getWidth()) != colour) throw new IllegalStateException("World source pixels differ");
          }
          unique.add(a.fingerprint());
        }
        materials++;
      }
      final Path folder = atlases.resolve(actual.modelHash());
      final var atlas = WorldTerrainAtlas.read(Files.readAllBytes(folder.resolve("manifest.json")), Files.readAllBytes(folder.resolve("atlas-v1.png")), model, bank);
      mapped += atlas.regions().size(); scenes++;
    }
    System.out.println("Independent terrain agreement: " + scenes + " scenes, " + materials + " static bindings, " + unique.size() + " unique decodes; " + mapped + " validated HD atlas bindings");
  }
}
