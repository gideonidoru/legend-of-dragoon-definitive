package legend.definitive.font;

import legend.core.font.PackagedFont;
import legend.core.font.FontManager;
import legend.core.font.FontSelectionEvent;
import legend.definitive.textures.TexturePilot;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PackagedFontTest {
  private record Fixture(JSONObject metadata, byte[] png, byte[] source, byte[] sourceImage) {
    byte[] json() { return metadata.toString().getBytes(StandardCharsets.UTF_8); }
    PackagedFont validate() throws IOException { return PackagedFont.validate(json(),png,source,sourceImage); }
  }
  private Fixture fixture() throws IOException {
    byte[] source=Files.readAllBytes(Path.of("gfx/fonts/default.json")), image=Files.readAllBytes(Path.of("gfx/fonts/default.png"));
    var metadata=new JSONObject(new String(source,StandardCharsets.UTF_8));
    metadata.put("format",1).put("name","uihd.font.original_hd.name").put("glyphCount",139).put("lineHeight",12)
      .put("sourceImageSha256",PackagedFont.SOURCE_IMAGE_SHA256).put("sourceMetadataSha256",PackagedFont.SOURCE_METADATA_SHA256)
      .put("outputSize",new int[]{1024,1024});
    var glyphs=metadata.getJSONObject("glyphs"); int i=0;
    for(String key:glyphs.keySet()) { var glyph=glyphs.getJSONObject(key); glyph.put("texU",32+i%12*64).put("texV",32+i/12*64); i++; }
    var raster=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
    for(String key:glyphs.keySet()) { var glyph=glyphs.getJSONObject(key); for(int y=glyph.getInt("texV");y<glyph.getInt("texV")+glyph.getInt("texH");y++) for(int x=glyph.getInt("texU");x<glyph.getInt("texU")+glyph.getInt("texW");x++) raster.setRGB(x,y,0xffffffff); }
    var out=new ByteArrayOutputStream(); ImageIO.write(raster,"png",out);
    byte[] png=out.toByteArray(); metadata.put("outputSha256",TexturePilot.sha256(png));
    return new Fixture(metadata,png,source,image);
  }
  @Test void allOriginalMetricsAndControllerCodepointsRemainExactWithoutGraphicsInitialization() throws Exception {
    var f=fixture(); var font=f.validate(); var original=new FontManager().get(Path.of("gfx/fonts/default.json"));
    var glyphs=new JSONObject(new String(f.source,StandardCharsets.UTF_8)).getJSONObject("glyphs"); assertEquals(139,glyphs.length());
    for(String key:glyphs.keySet()) { char c=key.length()==1?key.charAt(0):(char)Integer.parseInt(key.substring(2),16);
      assertEquals(original.charWidth(c),font.charWidth(c),key); assertEquals(original.usesColour(c),font.usesColour(c),key); }
    assertEquals(24,font.textHeight("A\nA")); assertNull(font.path,"Packaged font is outside configured font discovery");
  }
  @Test void sourcePinsOutputPinsAndMissingResourceFailBeforeSelection() throws Exception {
    var f=fixture(); byte[] altered=f.source.clone(); altered[0]=' ';
    assertThrows(IOException.class,()->PackagedFont.validate(f.json(),f.png,altered,f.sourceImage));
    f.metadata.put("outputSha256","0".repeat(64)); assertThrows(IOException.class,f::validate);
    var event=new FontSelectionEvent(); assertThrows(IOException.class,()->event.select(PackagedFontTest.class)); assertNull(event.font());
  }
  @Test void changedMissingDuplicateAndFractionalMetricsFailClosed() throws Exception {
    var f=fixture(); var glyphs=f.metadata.getJSONObject("glyphs"); glyphs.getJSONObject("A").put("w",99); assertThrows(IOException.class,f::validate);
    f=fixture(); f.metadata.getJSONObject("glyphs").remove("A"); assertThrows(IOException.class,f::validate);
    f=fixture(); f.metadata.getJSONObject("glyphs").put("0x0041",f.metadata.getJSONObject("glyphs").getJSONObject("A")); assertThrows(IOException.class,f::validate);
    f=fixture(); f.metadata.getJSONObject("glyphs").getJSONObject("A").put("texU",32.5); assertThrows(IOException.class,f::validate);
  }
  @Test void croppedOverlappingOrUnpaddedRectanglesFailBeforeGraphics() throws Exception {
    var f=fixture(); var glyph=f.metadata.getJSONObject("glyphs").getJSONObject("A"); glyph.put("texU",1023); assertThrows(IOException.class,f::validate);
    f=fixture(); glyph=f.metadata.getJSONObject("glyphs").getJSONObject("A"); glyph.put("texU",0); assertThrows(IOException.class,f::validate);
    f=fixture(); var glyphs=f.metadata.getJSONObject("glyphs"); var a=glyphs.getJSONObject("A"); var b=glyphs.getJSONObject("B"); b.put("texU",a.getInt("texU")).put("texV",a.getInt("texV")); assertThrows(IOException.class,f::validate);
    f=fixture(); f.metadata.getJSONObject("glyphs").getJSONObject("A").put("texH",0); assertThrows(IOException.class,f::validate);
  }
  @Test void invalidPngAndOversizedAtlasFailClosedEvenWithMatchingHash() throws Exception {
    var f=fixture(); byte[] truncated=java.util.Arrays.copyOf(f.png,33); f.metadata.put("outputSha256",TexturePilot.sha256(truncated));
    assertThrows(IOException.class,()->PackagedFont.validate(f.json(),truncated,f.source,f.sourceImage));
    var large=fixture(); large.metadata.put("outputSize",new int[]{4096,4096}); assertThrows(IOException.class,large::validate);
  }
  @Test void emptyGlyphAndOpaqueGutterFailEvenWithMatchingOutputIdentity() throws Exception {
    for(boolean gutter:new boolean[]{false,true}) {
      var f=fixture(); var raster=ImageIO.read(new java.io.ByteArrayInputStream(f.png));var glyph=f.metadata.getJSONObject("glyphs").getJSONObject("A");
      if(gutter) raster.setRGB(glyph.getInt("texU")-1,glyph.getInt("texV"),0xffffffff);
      else for(int y=glyph.getInt("texV");y<glyph.getInt("texV")+glyph.getInt("texH");y++) for(int x=glyph.getInt("texU");x<glyph.getInt("texU")+glyph.getInt("texW");x++) raster.setRGB(x,y,0);
      var out=new ByteArrayOutputStream(); ImageIO.write(raster,"png",out); byte[] altered=out.toByteArray(); f.metadata.put("outputSha256",TexturePilot.sha256(altered));
      assertThrows(IOException.class,()->PackagedFont.validate(f.json(),altered,f.source,f.sourceImage));
    }
  }

}
