// Definitive Steam presentation (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Map;

/** Bundled original key art: offline Steam setup, complete composition, no external image service. */
final class SteamArtwork {
  private SteamArtwork() { }
  static final String SOURCE = "https://legendofdragoon.org/wp-content/uploads/LODbox-expanded_EZG.webp";
  static final String SHA256 = "15e076e65c41048055c27cb2c4ab404b4caeb8a1ca6825265bb84c26c92e2ce7";
  static Path folder(final Path install) throws IOException {
    final Path folder = install.resolve("steam-artwork");
    if(Files.isSymbolicLink(folder)) throw new IOException("Unexpected linked Steam artwork folder.");
    Files.createDirectories(folder); return folder;
  }
  static void prepare(final Path install, final InstallProgress progress) throws IOException {
    progress.phase("Preparing Steam artwork", "Original cover art · Dart, Shana and Rose", 5);
    try(final var input = SteamArtwork.class.getResourceAsStream("steam-cover.png")) {
      if(input == null) throw new IOException("Bundled Steam artwork is missing. Reinstall the current package.");
      final BufferedImage original = ImageIO.read(input);
      if(original == null) throw new IOException("Bundled Steam artwork could not be read.");
      render(install, original);
    }
    final Path source = folder(install).resolve("source.txt");
    if(Files.isSymbolicLink(source)) throw new IOException("Unexpected linked artwork credit.");
    Files.writeString(source, "Original North American cover artwork. Copyright Sony Interactive Entertainment.\nCommunity preservation: https://legendofdragoon.org/merchandise/\nSource: " + SOURCE + "\nOriginal WebP SHA256: " + SHA256 + "\n");
  }
  static Path icon(final Path install) throws IOException {
    final Path folder = folder(install), icon = folder.resolve("icon.png");
    if(Files.isSymbolicLink(icon)) throw new IOException("Unexpected linked Steam icon.");
    if(!Files.isRegularFile(icon)) prepare(install, InstallProgress.NONE);
    return icon;
  }
  static void render(final Path install, final BufferedImage original) throws IOException {
    final Path folder = folder(install);
    for(final var spec : Map.of("portrait.png", new Dimension(600, 900), "landscape.png", new Dimension(920, 430), "hero.png", new Dimension(1920, 620), "icon.png", new Dimension(512, 512), "logo.png", new Dimension(640, 160)).entrySet()) {
      final Dimension size = spec.getValue(); final var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      final var g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      if(!spec.getKey().equals("logo.png")) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x081c22), size.width, size.height, new Color(0x102832))); g.fillRect(0, 0, size.width, size.height);
        if(original != null) {
          // Fit the complete original composition. Never cut off the title, faces or wings.
          final double scale = Math.min((double)size.width / original.getWidth(), (double)(size.height - (spec.getKey().equals("icon.png") ? 0 : 70)) / original.getHeight());
          final int w = (int)(original.getWidth() * scale), h = (int)(original.getHeight() * scale);
          g.drawImage(original, (size.width - w) / 2, (size.height - h) / 2 - (spec.getKey().equals("icon.png") ? 0 : 18), w, h, null);
        } else {
          g.setColor(new Color(0xe9dca9)); g.setFont(new Font(Font.SERIF, Font.BOLD, Math.min(34, size.width / 17)));
          centre(g, "THE LEGEND OF DRAGOON", size.width, size.height / 2);
        }
      }
      if(!spec.getKey().equals("icon.png")) {
        g.setColor(new Color(0xe9dca9)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, spec.getKey().equals("logo.png") ? 28 : 20));
        if(spec.getKey().equals("logo.png")) { centre(g, "THE LEGEND OF DRAGOON", size.width, 58); centre(g, "DEFINITIVE", size.width, 110); }
        else centre(g, "D E F I N I T I V E", size.width, size.height - 26);
      }
      g.dispose(); write(image, folder.resolve(spec.getKey()));
    }
  }
  private static void centre(final Graphics2D g, final String text, final int width, final int y) { g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, y); }
  private static void write(final BufferedImage image, final Path target) throws IOException {
    if(Files.isSymbolicLink(target)) throw new IOException("Unexpected linked Steam artwork file.");
    final Path pending = Files.createTempFile(target.getParent(), ".artwork-", ".tmp");
    try { if(!ImageIO.write(image, "png", pending.toFile())) throw new IOException("Could not write Steam artwork."); Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
    finally { Files.deleteIfExists(pending); }
  }
  static void installGrid(final SteamLibrary.Account account, final int appid, final Path install) throws IOException {
    final Path grid = account.config().resolve("grid");
    if(Files.isSymbolicLink(grid)) throw new IOException("Unexpected linked Steam grid folder."); Files.createDirectories(grid);
    final String id = Integer.toUnsignedString(appid);
    for(final var entry : Map.of("p.png", "portrait.png", ".png", "landscape.png", "_hero.png", "hero.png", "_logo.png", "logo.png", "_icon.png", "icon.png").entrySet()) {
      final Path target = grid.resolve(id + entry.getKey());
      if(Files.isSymbolicLink(target)) throw new IOException("Unexpected linked Steam grid artwork.");
      if(Files.exists(target)) continue; // Preserve any player-chosen library artwork.
      final Path pending = Files.createTempFile(grid, ".definitive-art-", ".tmp");
      try { Files.copy(folder(install).resolve(entry.getValue()), pending, StandardCopyOption.REPLACE_EXISTING); Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE); }
      finally { Files.deleteIfExists(pending); }
    }
  }
}
