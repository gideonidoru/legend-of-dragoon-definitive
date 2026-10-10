package legend.definitive.artwork;

import legend.game.textures.Image;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded CPU validation of the fixed 120x90, single-palette location TIM. */
public final class LocationArtwork {
  private LocationArtwork() { }
  public static Image decode(final byte[] source) throws IOException {
    if(source.length != 11344) throw new IOException("Location TIM bounds differ");
    final var bytes = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
    if(bytes.getInt(0) != 16 || bytes.getInt(4) != 9 || bytes.getInt(8) != 524 || Short.toUnsignedInt(bytes.getShort(16)) != 256 || bytes.getShort(18) != 1 || bytes.getInt(532) != 10812 || bytes.getShort(540) != 60 || bytes.getShort(542) != 90) throw new IOException("Location TIM layout differs");
    final byte[] rgba = new byte[120 * 90 * 4];
    for(int p = 0; p < 120 * 90; p++) {
      final int colour = Short.toUnsignedInt(bytes.getShort(20 + Byte.toUnsignedInt(source[544 + p]) * 2));
      rgba[p * 4] = (byte)((colour & 31) << 3);
      rgba[p * 4 + 1] = (byte)((colour >>> 5 & 31) << 3);
      rgba[p * 4 + 2] = (byte)((colour >>> 10 & 31) << 3);
      rgba[p * 4 + 3] = colour == 0 ? 0 : (byte)255;
    }
    return new Image(rgba, 120, 90);
  }
  public static Image load(final Class<?> owner, final String base, final byte[] source) throws IOException {
    final var metadata = ArtworkResources.metadata(owner, base + "/manifest.properties");
    return read(metadata, ArtworkResources.read(owner, base + "/image-v1.png", 32 * 1024 * 1024), source);
  }

  public static Image read(final java.util.Properties metadata, final byte[] png, final byte[] source) throws IOException {
    if(!"visual-reviewed-native-pending".equals(metadata.getProperty("reviewStatus")) && !"native-accepted".equals(metadata.getProperty("reviewStatus"))) throw new IOException("Location artwork has not passed review");
    if(!"4".equals(metadata.getProperty("scale"))) throw new IOException("Location artwork scale differs");
    ArtworkResources.hash(source, metadata.getProperty("sourceSha256"));
    final Image original = decode(source);
    if(!SkySource.fingerprint(original).equals(metadata.getProperty("decodedRgbaSha256"))) throw new IOException("Location decoded source differs");
    ArtworkResources.hash(png, metadata.getProperty("outputSha256"));
    return SkySource.applyCoverage(original, ArtworkResources.image(png, 480, 360));
  }

  public static Image coverage(final byte[] source, final Image replacement) throws IOException {
    return SkySource.applyCoverage(decode(source), replacement);
  }
}
