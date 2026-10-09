// Definitive exact-map private field pilot, AGPL v3; see LICENSE.
package legend.definitive.textures;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Properties;

/** Validates before exposing RGBA to the existing texture event. Invalid packs keep original textures. */
public final class TexturePack {
  private TexturePack() { }
  public record Replacement(int width, int height, ByteBuffer rgba) { }
  public static Replacement read(final Path folder, final int disk, final int cut, final int object, final byte[] original) throws IOException {
    final Path manifest = folder.resolve("manifest.properties"), png = folder.resolve("scale2x-engine.png");
    if(Files.isSymbolicLink(manifest) || Files.isSymbolicLink(png) || Files.size(manifest) > 16384 || Files.size(png) > 16 * 1024 * 1024) throw new IOException("Invalid pilot pack paths or sizes.");
    final Properties p = new Properties(); try(final var in = Files.newInputStream(manifest)) { p.load(in); }
    if(!"1".equals(p.getProperty("format")) || !"scale2x-java-1".equals(p.getProperty("pipeline")) || !"psx-stp".equals(p.getProperty("alpha"))) throw new IOException("Unsupported pilot pack.");
    if(!("" + disk).equals(p.getProperty("disk")) || !("" + cut).equals(p.getProperty("cut")) || !("" + object).equals(p.getProperty("object"))) return null;
    final TimImage source = TimImage.read(original, 0);
    if(source.paletteCount() > 1 || !TexturePilot.sha256(original).equals(p.getProperty("sourceSha256")) || !"0".equals(p.getProperty("palette")) || !("" + source.width()).equals(p.getProperty("width")) || !("" + source.height()).equals(p.getProperty("height"))) throw new IOException("Source texture or palette does not match the field pilot.");
    final byte[] bytes = Files.readAllBytes(png);
    final int width = source.width() * 2, height = source.height() * 2;
    final ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    if(bytes.length < 33 || header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(12) != 0x49484452 || header.getInt(16) != width || header.getInt(20) != height || !TexturePilot.sha256(bytes).equals(p.getProperty("scale2x-engineSha256"))) throw new IOException("Pilot PNG dimensions or checksum do not match.");
    final var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
    if(image == null || image.getWidth() != width || image.getHeight() != height) throw new IOException("Invalid pilot PNG.");
    final int[] expected = TextureScaler.twice(source, false);
    final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    if(!java.util.Arrays.equals(expected, pixels)) throw new IOException("Pilot pixels or STP encoding differ from the reproducible source.");
    final ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
    for(final int pixel : pixels) rgba.put((byte)(pixel >>> 16)).put((byte)(pixel >>> 8)).put((byte)pixel).put((byte)(pixel >>> 24));
    rgba.flip(); return new Replacement(width, height, rgba);
  }
}
