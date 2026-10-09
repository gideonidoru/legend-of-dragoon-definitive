// Definitive reproducible private texture pilot (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.textures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** CLI: TIM new-output-directory explicit-palette [disk cut object]. Never imports results into Git. */
public final class TexturePilot {
  private TexturePilot() { }
  public static void main(final String[] args) throws Exception {
    if(args.length != 3 && args.length != 6) throw new IllegalArgumentException("Usage: TIM NEW_OUTPUT_DIRECTORY PALETTE [DISK CUT OBJECT]");
    final Path source = Path.of(args[0]), output = Path.of(args[1]);
    if(Files.size(source) > 16 * 1024 * 1024) throw new IOException("TIM exceeds pilot limit.");
    final byte[] bytes = Files.readAllBytes(source);
    final int palette = Integer.parseInt(args[2]);
    final TimImage image = TimImage.read(bytes, palette);
    Files.createDirectory(output);
    write(output.resolve("original-preview.png"), image.width(), image.height(), image.pixels(), true);
    write(output.resolve("nearest-preview.png"), image.width() * 2, image.height() * 2, TextureScaler.twice(image, true), true);
    final int[] enhanced = TextureScaler.twice(image, false);
    write(output.resolve("scale2x-preview.png"), image.width() * 2, image.height() * 2, enhanced, true);
    write(output.resolve("scale2x-engine.png"), image.width() * 2, image.height() * 2, enhanced, false);
    final SortedMap<String, String> metadata = new TreeMap<>();
    metadata.put("format", "1"); metadata.put("pipeline", "scale2x-java-1"); metadata.put("alpha", "psx-stp");
    metadata.put("sourceSha256", sha256(bytes)); metadata.put("width", "" + image.width()); metadata.put("height", "" + image.height());
    metadata.put("palette", "" + palette); metadata.put("paletteCount", "" + image.paletteCount());
    for(final String name : List.of("original-preview", "nearest-preview", "scale2x-preview", "scale2x-engine")) metadata.put(name + "Sha256", sha256(Files.readAllBytes(output.resolve(name + ".png"))));
    for(int i = 0; i < 3; i++) metadata.put(List.of("disk", "cut", "object").get(i), args.length == 6 ? "" + Integer.parseInt(args[i + 3]) : "-1");
    final StringBuilder manifest = new StringBuilder(); metadata.forEach((key, value) -> manifest.append(key).append('=').append(value).append('\n'));
    Files.writeString(output.resolve("manifest.properties"), manifest);
    Files.writeString(output.resolve("REPORT.md"), "# Private texture pilot\n\nPipeline scale2x-java-1, Java " + System.getProperty("java.version") + ". Input " + image.width() + "x" + image.height() + ", output " + image.width() * 2 + "x" + image.height() * 2 + ". RGBA texture storage rises from " + image.width() * image.height() * 4 + " to " + image.width() * image.height() * 16 + " bytes (4x), excluding driver overhead.\n\nPreviews use conventional opacity. Engine PNG alpha encodes PSX STP: zero-alpha coloured pixels remain visible in the engine; black/zero is discarded. A normal viewer may show the engine PNG as transparent. Exact mapping and single-palette validation are required for field loading. Battle replacement, animation seams, visual benefit and Deck performance are unverified. No neural reconstruction or added geometry. See manifest for input/output SHA256.\n");
    System.out.println("Private comparison written to " + output.toAbsolutePath());
  }
  static void write(final Path path, final int width, final int height, final int[] pixels, final boolean preview) throws IOException {
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final int[] colours = pixels.clone();
    if(preview) for(int i = 0; i < colours.length; i++) if(colours[i] != 0) colours[i] |= 0xff000000;
    image.setRGB(0, 0, width, height, colours, 0, width);
    if(!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG encoder unavailable.");
  }
  public static String sha256(final byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
  }
}
