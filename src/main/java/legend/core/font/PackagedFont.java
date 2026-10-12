package legend.core.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import legend.core.lang.I18nText;
import legend.core.renderer.Texture;
import legend.definitive.artwork.ArtworkResources;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Source-bound atlas owned only by its optional selection, outside configured font discovery. */
public final class PackagedFont extends Font {
  public static final String SOURCE_IMAGE_SHA256 = "dcf84d68a67f0f76aaaf4233d0c2d1ec3c588e8f98a2ca3fb4d873b0fee246d8";
  public static final String SOURCE_METADATA_SHA256 = "b4faacbef8f23e5fff5374484c1356e9f92134b1abaf928c491e8587e91b1c12";
  private final byte[] png;

  private PackagedFont(final Char2ObjectMap<Glyph> glyphs, final byte[] png) {
    super(null, new I18nText("uihd.font.original_hd.name"), glyphs);
    this.png = png.clone();
  }

  public static PackagedFont load(final Class<?> owner) throws IOException {
    final byte[] metadata = ArtworkResources.read(owner, "/uihd/font/original-hd.json", 256 * 1024);
    final byte[] png = ArtworkResources.read(owner, "/uihd/font/original-hd.png", 32 * 1024 * 1024);
    return validate(metadata, png, boundedSource(Path.of("gfx/fonts/default.json"), 256 * 1024), boundedSource(Path.of("gfx/fonts/default.png"), 2 * 1024 * 1024));
  }

  private static byte[] boundedSource(final Path path, final int limit) throws IOException {
    try(final var input = Files.newInputStream(path)) {
      final byte[] bytes = input.readNBytes(limit + 1);
      if(bytes.length > limit) throw new IOException("Original font exceeds bounds");
      return bytes;
    }
  }

  /** No graphics allocation occurs until all identities, metrics, rectangles and PNG decode validate. */
  public static PackagedFont validate(final byte[] metadata, final byte[] png, final byte[] sourceMetadata, final byte[] sourceImage) throws IOException {
    if(metadata.length > 256 * 1024 || sourceMetadata.length > 256 * 1024 || sourceImage.length > 2 * 1024 * 1024 || png.length > 32 * 1024 * 1024) throw new IOException("Font resources exceed bounds");
    ArtworkResources.hash(sourceMetadata, SOURCE_METADATA_SHA256);
    ArtworkResources.hash(sourceImage, SOURCE_IMAGE_SHA256);
    try {
      final JSONObject document = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
      if(integer(document,"format") != 1 || integer(document,"lineHeight") != 12 || integer(document,"glyphCount") != 139
        || !document.getString("name").equals("uihd.font.original_hd.name")
        || !document.getString("sourceImageSha256").equals(SOURCE_IMAGE_SHA256)
        || !document.getString("sourceMetadataSha256").equals(SOURCE_METADATA_SHA256)) throw new IOException("Font source or format differs");
      final String outputHash = document.getString("outputSha256");
      if(!outputHash.matches("[a-f0-9]{64}")) throw new IOException("Font output identity differs");
      ArtworkResources.hash(png, outputHash);
      final var size = document.getJSONArray("outputSize");
      if(size.length() != 2 || !(size.get(0) instanceof Integer) || !(size.get(1) instanceof Integer)) throw new IOException("Font size differs");
      final int width = size.getInt(0), height = size.getInt(1);
      if(width < 1 || height < 1 || width > 2048 || height > 2048 || png.length < 33) throw new IOException("Font atlas exceeds bounds");
      final ByteBuffer header = ByteBuffer.wrap(png);
      if(header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452
        || header.getInt(16) != width || header.getInt(20) != height || png[24] != 8 || png[25] != 6) throw new IOException("Font PNG header differs");
      final var decoded = ImageIO.read(new ByteArrayInputStream(png));
      if(decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) throw new IOException("Font PNG cannot be decoded");
      final JSONObject original = new JSONObject(new String(sourceMetadata,StandardCharsets.UTF_8)).getJSONObject("glyphs");
      final JSONObject candidate = document.getJSONObject("glyphs");
      if(original.length() != 139 || !candidate.keySet().equals(original.keySet())) throw new IOException("Font glyph set differs");
      final Char2ObjectMap<Glyph> glyphs = new Char2ObjectOpenHashMap<>();
      final var rectangles = new ArrayList<int[]>();
      for(final String key : original.keySet()) {
        final char codepoint = key.length() == 1 ? key.charAt(0) : parseCodepoint(key);
        if(glyphs.containsKey(codepoint)) throw new IOException("Duplicate font codepoint");
        final JSONObject baseline = original.getJSONObject(key), glyph = candidate.getJSONObject(key);
        for(final String metric : new String[]{"x", "y", "w", "h"}) if(integer(glyph,metric) != integer(baseline,metric)) throw new IOException("Font logical metrics differ");
        if(!(glyph.get("colour") instanceof Boolean) || glyph.getBoolean("colour") != baseline.getBoolean("colour")) throw new IOException("Font colour metric differs");
        final int u=integer(glyph,"texU"), v=integer(glyph,"texV"), w=integer(glyph,"texW"), h=integer(glyph,"texH");
        if(u < 16 || v < 16 || w < 0 || h < 0 || (long)u+w+16 > width || (long)v+h+16 > height
          || (integer(baseline,"texW") == 0) != (w == 0) || (integer(baseline,"texH") == 0) != (h == 0)) throw new IOException("Font glyph rectangle differs");
        boolean visible = false;
        for(int row=v-16;row<v+h+16;row++) for(int col=u-16;col<u+w+16;col++) {
          final int pixel = decoded.getRGB(col,row);
          if(col>=u && col<u+w && row>=v && row<v+h) visible |= (pixel>>>24)!=0;
          else if(pixel!=0) throw new IOException("Font glyph gutter is not transparent black");
        }
        if(w!=0 && h!=0 && !visible) throw new IOException("Font glyph has no visible coverage");
        if(w != 0 && h != 0) {
          for(final int[] rect : rectangles) if((long)u < (long)rect[0]+rect[2]+16 && (long)rect[0] < (long)u+w+16
            && (long)v < (long)rect[1]+rect[3]+16 && (long)rect[1] < (long)v+h+16) throw new IOException("Font glyph gutter differs");
          rectangles.add(new int[]{u,v,w,h});
        }
        glyphs.put(codepoint,new Glyph(codepoint,u,v,w,h,integer(glyph,"x"),integer(glyph,"y"),integer(glyph,"w"),integer(glyph,"h"),glyph.getBoolean("colour")));
      }
      return new PackagedFont(glyphs,png);
    } catch(final RuntimeException failure) { throw new IOException("Malformed optional font metadata",failure); }
  }

  private static int integer(final JSONObject object, final String key) throws IOException {
    if(!(object.get(key) instanceof Integer value)) throw new IOException("Font integer differs: " + key);
    return value;
  }
  private static char parseCodepoint(final String key) throws IOException {
    if(!key.matches("0x[0-9a-fA-F]{1,4}")) throw new IOException("Font codepoint differs");
    return (char)Integer.parseInt(key.substring(2),16);
  }
  @Override protected Texture createTexture() {
    return Texture.create("UIHD Original HD font", builder -> {
      builder.internalFormat(legend.core.renderer.TextureInternalFormat.RGBA_8);
      builder.dataFormat(legend.core.renderer.TextureDataFormat.RGBA);
      builder.dataType(legend.core.renderer.TextureDataType.UBYTE);
      builder.minFilter(true); builder.magFilter(true); builder.png(ByteBuffer.wrap(this.png));
    });
  }
  @Override public legend.core.renderer.QueuedModelStandard queueChar(final char chr, final legend.core.gte.MV transforms) {
    return super.queueChar(chr,transforms).useTextureAlpha().alpha(1.0f)
      .translucency(legend.core.renderer.Translucency.HALF_B_PLUS_HALF_F);
  }
}
