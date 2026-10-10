// Original synthetic material fixtures (2026-10-09), AGPL v3; no retail assets.
package legend.definitive.materials;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import legend.definitive.textures.TexturePilot;
import legend.definitive.textures.TimImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Original synthetic controls shared by CPU validation and the native upload probe. */
public final class MaterialAtlasFixture {
  private MaterialAtlasFixture() { }
  public record Fixture(Path folder, byte[] model, byte[] tim, JsonObject manifest) { }
  static byte[] model() {
    final ByteBuffer out = ByteBuffer.allocate(52 + 24 + 8 + 48).order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(12).putInt(0).putInt(0).putInt(0x41).putInt(0).putInt(1);
    out.putInt(28).putInt(3).putInt(52).putInt(1).putInt(60).putInt(2).putInt(0);
    out.position(84);
    for(int palette = 0; palette < 2; palette++) {
      out.putInt(0x24000507).put((byte)0).put((byte)0).putShort((short)palette);
      out.put((byte)3).put((byte)0).putShort((short)0).put((byte)0).put((byte)3).putShort((short)0);
      out.putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)2);
    }
    return out.array();
  }
  static byte[] tim() {
    final ByteBuffer out = ByteBuffer.allocate(8 + 76 + 20).order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(0x10).putInt(8).putInt(76).putShort((short)0).putShort((short)0).putShort((short)32).putShort((short)1);
    for(int palette = 0; palette < 2; palette++) for(int colour = 0; colour < 16; colour++) out.putShort((short)(colour == 0 ? 0 : colour == 1 ? (palette == 0 ? 31 : 0x7c00) : colour == 2 ? 0x8000 : colour == 3 ? 0x83e0 : 31));
    out.putInt(20).putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)4);
    for(int row = 0; row < 4; row++) out.put((byte)0x10).put((byte)0x32);
    return out.array();
  }
  public static Fixture create(final Path temporary, final int scale) throws Exception {
    final Path folder = Files.createTempDirectory(temporary, "atlas");
    final byte[] model = model(), tim = tim();
    final var layout = SourceMaterials.layout(model, tim, scale);
    final var image = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
    final List<Map<String, Object>> entries = new ArrayList<>();
    for(final var region : layout.regions()) {
      final TimImage source = TimImage.read(tim, region.palette());
      for(int y = 0; y < region.height(); y++) for(int x = 0; x < region.width(); x++) image.setRGB(region.x() + x, region.y() + y, source.pixels()[Math.clamp(region.top() + y / scale, 0, 3) * 4 + Math.clamp(region.left() + x / scale, 0, 3)]);
      entries.add(Map.of("palette", region.palette(), "sourceCrop", List.of(region.left(), region.top(), region.right(), region.bottom()), "atlasRect", List.of(region.x(), region.y(), region.width(), region.height())));
    }
    ImageIO.write(image, "png", folder.resolve("atlas-engine-stp.png").toFile());
    final var manifest = new Gson().toJsonTree(Map.of("pipeline", "definitive-private-material-pack-1", "modelSha256", TexturePilot.sha256(model), "timSha256", TexturePilot.sha256(tim), "scale", scale, "paddingSourceTexels", 8, "atlasSize", List.of(layout.width(), layout.height()), "materials", entries, "algorithm", "nearest", "preservedPalettes", List.of(), "atlasEngineSha256", TexturePilot.sha256(Files.readAllBytes(folder.resolve("atlas-engine-stp.png"))))).getAsJsonObject();
    final var fixture = new Fixture(folder, model, tim, manifest); save(fixture); return fixture;
  }
  public static void save(final Fixture fixture) throws IOException { Files.writeString(fixture.folder.resolve("manifest.json"), fixture.manifest.toString()); }
  public static MaterialAtlas read(final Fixture fixture) throws IOException { return MaterialAtlas.read(fixture.folder, fixture.model, fixture.tim); }
}
