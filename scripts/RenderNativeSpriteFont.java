import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Rasterizes explicit indexed-font vectors; palette RGB comes from the pinned private source. */
public final class RenderNativeSpriteFont {
  private static Path safePath(final Path supplied) throws IOException {
    Path cursor=supplied.toAbsolutePath().getRoot();
    for(final Path part:supplied.toAbsolutePath()) {
      cursor=cursor.resolve(part);
      if(Files.isSymbolicLink(cursor)) throw new IOException("Linked authoring path");
    }
    return supplied.toAbsolutePath().normalize();
  }

  private static byte[] png(final BufferedImage image) throws IOException {
    final var bytes=new ByteArrayOutputStream();
    if(!ImageIO.write(image,"PNG",bytes)) throw new IOException("No PNG writer");
    return bytes.toByteArray();
  }

  private static void outputs(final Path encoded,final byte[] indices,final Path preview,final byte[] pixels) throws IOException {
    boolean madeEncoded=false,madePreview=false;
    try {
      try(final var out=Files.newOutputStream(encoded,StandardOpenOption.CREATE_NEW)){madeEncoded=true;out.write(indices);}
      try(final var out=Files.newOutputStream(preview,StandardOpenOption.CREATE_NEW)){madePreview=true;out.write(pixels);}
    } catch(final IOException failure) {
      // Remove only files this invocation created; a racing owner's existing file stays intact.
      for(final Path created:new Path[]{madeEncoded?encoded:null,madePreview?preview:null}) {
        if(created!=null) try {Files.delete(created);} catch(final IOException cleanup){failure.addSuppressed(cleanup);}
      }
      throw failure;
    }
  }
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
    if(args.length != 5) throw new IllegalArgumentException("SVG, encoded PNG, preview PNG, scale, sixteen palette colours required");
    final Path source=safePath(Path.of(args[0])), encoded=safePath(Path.of(args[1])), preview=safePath(Path.of(args[2]));
    if(source.equals(encoded)||source.equals(preview)||encoded.equals(preview)||Files.exists(encoded)||Files.exists(preview)) throw new IOException("Authoring outputs must be distinct new files");
    if(!Files.isDirectory(encoded.getParent())||!Files.isDirectory(preview.getParent())) throw new IOException("Output parent must exist");
    final int scale=Integer.parseInt(args[3]); if(scale<2||scale>8) throw new IllegalArgumentException("Invalid scale");
    final String[] words=args[4].split(","); if(words.length!=16) throw new IllegalArgumentException("Palette count differs");
    final int[] colours=new int[16]; for(int i=0;i<16;i++){if(!words[i].matches("[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Invalid palette colour");colours[i]=Integer.parseInt(words[i],16);}
    if(Files.size(source)>256*1024) throw new IllegalArgumentException("Vector exceeds bounds");
    final var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    final Element root=f.newDocumentBuilder().parse(source.toFile()).getDocumentElement();
    if(!root.getTagName().equals("svg")) throw new IllegalArgumentException("Expected SVG");
    for(int i=0;i<root.getAttributes().getLength();i++) if(!java.util.Set.of("xmlns","viewBox").contains(root.getAttributes().item(i).getNodeName())) throw new IllegalArgumentException("Unsupported SVG attribute");
    for(int i=0;i<root.getChildNodes().getLength();i++) {
      final var child=root.getChildNodes().item(i);
      if(child instanceof Element && !child.getNodeName().equals("path")) throw new IllegalArgumentException("Only direct palette paths supported");
      if(child.getNodeType()==org.w3c.dom.Node.TEXT_NODE&&!child.getTextContent().isBlank()) throw new IllegalArgumentException("Unexpected SVG text");
    }
    final String[] box=root.getAttribute("viewBox").trim().split(" +");
    if(box.length!=4||!box[0].equals("0")||!box[1].equals("0")) throw new IllegalArgumentException("Invalid viewBox");
    final int width=Integer.parseInt(box[2]),height=Integer.parseInt(box[3]);
    if(width<1||height<1||(long)width*scale>2048||(long)height*scale>2048)throw new IllegalArgumentException("Cell exceeds bounds");
    final BufferedImage image=new BufferedImage(width*scale,height*scale,BufferedImage.TYPE_INT_ARGB);final var g=image.createGraphics();
    final BufferedImage mask=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
    final byte[] roles=new byte[image.getWidth()*image.getHeight()];
    final float[] contributions=new float[roles.length];
    try {
      g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      final var layers=root.getElementsByTagName("path");if(layers.getLength()<1||layers.getLength()>128)throw new IllegalArgumentException("Layer count differs");
      for(int i=0;i<layers.getLength();i++) {
        final Element e=(Element)layers.item(i);final int index=Integer.parseInt(e.getAttribute("data-palette-index"));if(index<1||index>15)throw new IllegalArgumentException("Palette role differs");
        for(int n=0;n<e.getAttributes().getLength();n++) if(!java.util.Set.of("data-palette-index","fill","fill-rule","stroke","stroke-width","transform","d").contains(e.getAttributes().item(n).getNodeName())) throw new IllegalArgumentException("Unsupported path attribute");
        if(e.hasChildNodes()||!java.util.Set.of("","evenodd","nonzero").contains(e.getAttribute("fill-rule"))) throw new IllegalArgumentException("Unsupported path content");
        if(e.hasAttribute("stroke-width")) {
          if(!e.getAttribute("fill").equals("none")||!java.util.Set.of("#fff","#ffffff").contains(e.getAttribute("stroke"))) throw new IllegalArgumentException("Expected exclusive white palette stroke");
        } else if(!java.util.Set.of("","#fff","#ffffff").contains(e.getAttribute("fill"))||!java.util.Set.of("","none").contains(e.getAttribute("stroke"))) {
          throw new IllegalArgumentException("Expected exclusive white palette fill");
        }
        final var path=shape(e);Shape painted=path;
        final var tx=Pattern.compile("translate\\((-?[0-9]+(?:\\.[0-9]+)?) (-?[0-9]+(?:\\.[0-9]+)?)\\)").matcher(e.getAttribute("transform"));
        if(!e.getAttribute("transform").isEmpty()){if(!tx.matches())throw new IllegalArgumentException("Unsupported transform");painted=AffineTransform.getTranslateInstance(Double.parseDouble(tx.group(1)),Double.parseDouble(tx.group(2))).createTransformedShape(path);}
        if(e.hasAttribute("stroke-width")) {
          final float thickness=Float.parseFloat(e.getAttribute("stroke-width"));if(!Float.isFinite(thickness)||thickness<=0||thickness>2)throw new IllegalArgumentException("Stroke exceeds bounds");
          painted=new BasicStroke(thickness,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(painted);
        }
        final var bounds=painted.getBounds2D();if(!Double.isFinite(bounds.getMaxX())||bounds.getMinX()<0||bounds.getMinY()<0||bounds.getMaxX()>width||bounds.getMaxY()>height)throw new IllegalArgumentException("Layer escapes cell");
        g.setColor(new Color(colours[index]));g.fill(painted);
        final var mg=mask.createGraphics();
        try {
          mg.setComposite(AlphaComposite.Clear);mg.fillRect(0,0,mask.getWidth(),mask.getHeight());
          mg.setComposite(AlphaComposite.SrcOver);mg.scale(scale,scale);
          mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);mg.setColor(Color.WHITE);mg.fill(painted);
        } finally {mg.dispose();}
        for(int y=0;y<mask.getHeight();y++) for(int x=0;x<mask.getWidth();x++) {
          final int offset=y*mask.getWidth()+x;final float coverage=(mask.getRGB(x,y)>>>24)/255.0f;
          final float retained=contributions[offset]*(1-coverage);
          if(coverage>retained){roles[offset]=(byte)index;contributions[offset]=coverage;}else contributions[offset]=retained;
        }
      }
    } finally {g.dispose();}
    final BufferedImage packed=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
    final BufferedImage previewPixels=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
      final int alpha=image.getRGB(x,y)>>>24;
      final int index=alpha==0?0:roles[y*image.getWidth()+x]&255;
      packed.setRGB(x,y,alpha<<24|index<<16);
      previewPixels.setRGB(x,y,alpha<<24|colours[index]);
    }
    outputs(encoded,png(packed),preview,png(previewPixels));
  }
}
