import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Rasterizes the project's explicit M/L/Q/Z glyph vectors without host-font substitution. */
public final class RenderUiHdFont {
  private static Path2D shape(final Element glyph) {
    final String data = glyph.getAttribute("d");
    final var tokens = new ArrayList<String>();
    final var matcher = Pattern.compile("[MLQZ]|-?[0-9]+(?:\\.[0-9]+)?").matcher(data);
    int previous = 0;
    while(matcher.find()) {
      if(!data.substring(previous, matcher.start()).matches("[\\s,]*")) throw new IllegalArgumentException("Unsupported vector command");
      tokens.add(matcher.group());
      previous = matcher.end();
    }
    if(!data.substring(previous).matches("[\\s,]*")) throw new IllegalArgumentException("Unsupported vector suffix");
    final var path = new Path2D.Double(glyph.getAttribute("fill-rule").equals("nonzero") ? Path2D.WIND_NON_ZERO : Path2D.WIND_EVEN_ODD);
    for(int i = 0; i < tokens.size();) {
      final String command = tokens.get(i++);
      switch(command) {
        case "M" -> path.moveTo(Double.parseDouble(tokens.get(i++)), Double.parseDouble(tokens.get(i++)));
        case "L" -> path.lineTo(Double.parseDouble(tokens.get(i++)), Double.parseDouble(tokens.get(i++)));
        case "Q" -> path.quadTo(Double.parseDouble(tokens.get(i++)), Double.parseDouble(tokens.get(i++)), Double.parseDouble(tokens.get(i++)), Double.parseDouble(tokens.get(i++)));
        case "Z" -> path.closePath();
        default -> throw new IllegalArgumentException("Unknown vector command");
      }
    }
    return path;
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("Expected atlas SVG and fresh output PNG");
    final Path source = Path.of(args[0]), output = Path.of(args[1]);
    final var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    final var document = factory.newDocumentBuilder().parse(source.toFile());
    final Element root = document.getDocumentElement();
    final int width = Integer.parseInt(root.getAttribute("width")), height = Integer.parseInt(root.getAttribute("height"));
    if(width < 1 || height < 1 || width > 2048 || height > 2048) throw new IllegalArgumentException("Atlas dimensions exceed bounds");
    final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final var groups = root.getElementsByTagName("g");
      if(groups.getLength() != 139) throw new IllegalArgumentException("Glyph count differs");
      for(int i = 0; i < groups.getLength(); i++) {
        final Element group = (Element)groups.item(i);
        final var transform = Pattern.compile("translate\\((\\d+) (\\d+)\\) scale\\(8\\)").matcher(group.getAttribute("transform"));
        if(!transform.matches()) throw new IllegalArgumentException("Invalid glyph transform");
        final int x = Integer.parseInt(transform.group(1)), y = Integer.parseInt(transform.group(2));
        final var elements = group.getElementsByTagName("path");
        if(elements.getLength() != 1) throw new IllegalArgumentException("Invalid glyph path count");
        final Path2D path = shape((Element)elements.item(0));
        final var bounds = path.getBounds2D();
        final int cellWidth = Integer.parseInt(group.getAttribute("data-width")), cellHeight = Integer.parseInt(group.getAttribute("data-height"));
        if(!path.getPathIterator(null).isDone() && (bounds.getMinX() < 0 || bounds.getMinY() < 0 || bounds.getMaxX() > cellWidth || bounds.getMaxY() > cellHeight)) throw new IllegalArgumentException("Glyph escapes source rectangle");
        if(x < 16 || y < 16 || x + cellWidth * 8 + 16 > width || y + cellHeight * 8 + 16 > height) throw new IllegalArgumentException("Glyph escapes padded atlas");
        final var copy = (Graphics2D)graphics.create();
        try {
          copy.translate(x, y);
          copy.scale(8, 8);
          copy.fill(path);
        } finally {
          copy.dispose();
        }
      }
    } finally {
      graphics.dispose();
    }
    try(final var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      if(!ImageIO.write(image, "PNG", stream)) throw new IllegalStateException("PNG writer unavailable");
    }
    System.out.println("Rasterized 139 explicit vector glyphs with transparent gutters; artwork still requires review");
  }
}
