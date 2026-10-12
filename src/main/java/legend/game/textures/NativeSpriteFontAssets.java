package legend.game.textures;

import legend.core.renderer.NativeUiQuad;
import legend.definitive.artwork.ArtworkResources;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Optional packaged authored palette-index glyphs; native sources remain local and authoritative. */
public final class NativeSpriteFontAssets {
  private final Class<?> resourceOwner;
  private final List<JSONObject> sprites;
  private NativeSpriteFontAssets(final Class<?> resourceOwner, final List<JSONObject> sprites) { this.resourceOwner = resourceOwner; this.sprites = sprites; }
  public static NativeSpriteFontAssets load(final Class<?> owner) throws IOException {
    try {
      final var root = new JSONObject(new String(ArtworkResources.read(owner, "/uihd/font/native-sprites.json", 2 * 1024 * 1024), StandardCharsets.UTF_8));
      if(!(root.get("format") instanceof Integer) || root.getInt("format") != 1) throw new IOException("Sprite font format differs");
      final var array = root.getJSONArray("sprites");
      if(array.length() > 2048) throw new IOException("Sprite font catalog exceeds bounds");
      final List<JSONObject> entries = new ArrayList<>();
      final var identities = new java.util.HashSet<String>();
      for(int i = 0; i < array.length(); i++) {
        final var entry = array.getJSONObject(i);
        if(!entry.getString("id").matches("[a-z0-9_-]{1,96}") || !entry.getString("family").matches("[a-z0-9_-]{1,96}")
          || !entry.getString("resource").matches("[a-z0-9_-]{1,96}\\.png") || !entry.getString("sourceSha256").matches("[a-f0-9]{64}")
          || !entry.getString("outputSha256").matches("[a-f0-9]{64}")) throw new IOException("Sprite font identity differs");
        for(final String pin : new String[]{"sourceImageSha256", "sourcePaletteSha256"}) if(entry.has(pin) && !entry.getString(pin).matches("[a-f0-9]{64}")) throw new IOException("Sprite font audit source pin differs");
        final var crop = entry.getJSONArray("crop"); final var size = entry.getJSONArray("outputSize");
        if(crop.length() != 4 || size.length() != 2) throw new IOException("Sprite font geometry differs");
        for(int n = 0; n < 4; n++) if(!(crop.get(n) instanceof Integer)) throw new IOException("Sprite font crop is not integral");
        for(int n = 0; n < 2; n++) if(!(size.get(n) instanceof Integer)) throw new IOException("Sprite font size is not integral");
        if(!(entry.get("palette") instanceof Integer) || entry.getInt("palette") < 0 || entry.getInt("palette") > 15
          || crop.getInt(0) < 0 || crop.getInt(1) < 0 || crop.getInt(2) < 1 || crop.getInt(3) < 1
          || crop.getInt(0) > 4095 || crop.getInt(1) > 511 || crop.getInt(2) > 512 || crop.getInt(3) > 512
          || size.getInt(0) < 1 || size.getInt(1) < 1 || size.getInt(0) > 2048 || size.getInt(1) > 2048) throw new IOException("Sprite font bounds differ");
        final String identity = entry.getString("family") + ":" + entry.getInt("palette") + ":" + crop;
        if(!identities.add(identity)) throw new IOException("Duplicate sprite font crop");
        entries.add(entry);
      }
      return new NativeSpriteFontAssets(owner, List.copyOf(entries));
    } catch(final RuntimeException failure) { throw new IOException("Invalid sprite font catalog", failure); }
  }

  public void select(final Object owner, final NativeUiTextureEvent event) {
    final String slot = "sprite-font:" + event.slot;
    NativeSpriteFont.beginSelection(owner, slot);
    if(this.sprites.stream().noneMatch(entry -> entry.getString("family").equals(event.id))) return;
    final byte[] sourceBytes = event.source();
    final String sourceHash = legend.definitive.textures.TexturePilot.sha256(sourceBytes);
    final legend.game.tim.Tim tim;
    final legend.core.gpu.Rect4i rect;
    final byte[] original;
    try {
      tim = new legend.game.tim.Tim(new legend.game.unpacker.FileData(sourceBytes));
      rect = tim.getImageRect();
      original = tim.getImageData().getBytes();
    } catch(final RuntimeException failure) {
      org.apache.logging.log4j.LogManager.getLogger(NativeSpriteFontAssets.class).warn("Retaining native sprite source {}: {}", event.id, failure.getMessage());
      return;
    }
    for(final JSONObject entry : this.sprites) {
      if(!entry.getString("family").equals(event.id)) continue;
      try {
        if(!sourceHash.equals(entry.getString("sourceSha256"))) throw new IOException("Sprite font source hash differs");
        final var crop = entry.getJSONArray("crop");
        final int u = crop.getInt(0), v = crop.getInt(1), w = crop.getInt(2), h = crop.getInt(3), palette = entry.getInt("palette");
        if(tim.getBpp() != legend.core.gpu.Bpp.BITS_4 || u + w > rect.w * 4 || v + h > rect.h || !tim.hasClut()) throw new IOException("Sprite font source page differs");
        final var paletteRect = tim.getClutRect();
        final int columns = paletteRect.w / 16;
        if(columns < 1 || paletteRect.w % 16 != 0 || palette >= (long)columns * paletteRect.h) throw new IOException("Sprite font source palette differs");
        if(event.imageX < 0 || event.imageX > 1023 || event.imageY < 0 || event.imageY > 511) throw new IOException("Sprite font upload location differs");
        final byte[] indices = new byte[w * h * 4];
        for(int y = 0; y < h; y++) for(int x = 0; x < w; x++) {
          final int packed = original[(v + y) * rect.w * 2 + (u + x) / 2] & 255;
          indices[(y * w + x) * 4] = (byte)(packed >> ((u + x & 1) * 4) & 15);
          indices[(y * w + x) * 4 + 3] = (byte)255;
        }
        final var size = entry.getJSONArray("outputSize");
        final int pageX = (event.imageX + u / 4) / 64 * 64, pageY = (event.imageY + v) / 256 * 256;
        // The uploaded layout may rearrange the source TIM banks (basic fonts use four-row columns).
        final var source = new NativeUiQuad(pageX, pageY, event.clutX + palette / event.clutRows * 16, event.clutY + palette % event.clutRows,
          event.imageX * 4 + u - pageX * 4, event.imageY + v - pageY, w, h);
        if(!NativeSpriteFont.registerDeferred(owner, slot, source, new Image(indices, w, h), size.getInt(0), size.getInt(1), () -> {
          try {
            final byte[] png = ArtworkResources.read(this.resourceOwner, "/uihd/font/" + entry.getString("resource"), 4 * 1024 * 1024);
            ArtworkResources.hash(png, entry.getString("outputSha256"));
            if(png.length < 33 || (png[24] & 255) != 8 || (png[25] & 255) != 6) throw new IOException("Sprite font PNG is not RGBA8");
            final var header = java.nio.ByteBuffer.wrap(png);
            if(header.getInt(16) != size.getInt(0) || header.getInt(20) != size.getInt(1)) throw new IOException("Sprite font PNG dimensions differ from catalog");
            return UiTextures.decode(png);
          } catch(final IOException failure) { throw new java.io.UncheckedIOException(failure); }
        })) throw new IOException("Sprite font crop already selected or exceeds budget");
      } catch(final IOException | RuntimeException failure) {
        org.apache.logging.log4j.LogManager.getLogger(NativeSpriteFontAssets.class).warn("Retaining native sprite {}: {}", entry.getString("id"), failure.getMessage());
      }
    }
  }
}
