package legend.game.tmd;

import java.nio.ByteBuffer;

/** Immutable, bounded supplemental color texture and per-face normalized UVs. */
public final class TmdFaceDetail {
  /** One immutable image may be shared by every rigid part of a character. */
  public static final class Image {
    private final ByteBuffer rgba;
    public final int width, height;

    public Image(final ByteBuffer rgba, final int width, final int height) {
      if(width < 1 || height < 1 || width > 2048 || height > 2048 || rgba.remaining() != (long)width * height * 4)
        throw new IllegalArgumentException("Face detail exceeds pixel budget");
      this.width = width; this.height = height;
      final var data = ByteBuffer.allocateDirect(rgba.remaining());
      data.put(rgba.duplicate()); this.rgba = data.flip().asReadOnlyBuffer();
    }

    public ByteBuffer pixels() { return this.rgba.duplicate(); }
  }

  private final Image image;
  private final float[][] coordinates;
  public final int width, height;

  public TmdFaceDetail(final ByteBuffer rgba, final int width, final int height, final float[][] coordinates) {
    this(new Image(rgba, width, height), coordinates);
  }

  public TmdFaceDetail(final Image image, final float[][] coordinates) {
    this.image = java.util.Objects.requireNonNull(image);
    if(coordinates.length > 50000) throw new IllegalArgumentException("Face detail exceeds face budget");
    this.width = image.width; this.height = image.height;
    this.coordinates = new float[coordinates.length][];
    for(int face = 0; face < coordinates.length; face++) {
      final var uv = coordinates[face];
      if(uv == null) continue;
      if(uv.length != 6 && uv.length != 8) throw new IllegalArgumentException("Face detail requires triangle/quad UVs");
      for(final float value : uv) if(!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid detail UV");
      this.coordinates[face] = uv.clone();
    }
  }
  public int faces() { return this.coordinates.length; }
  public boolean applies(final int face) { return this.coordinates[face] != null; }
  public int corners(final int face) { return this.coordinates[face].length / 2; }
  public float u(final int face, final int corner) { return this.coordinates[face][corner*2]; }
  public float v(final int face, final int corner) { return this.coordinates[face][corner*2+1]; }
  public Image image() { return this.image; }
  public ByteBuffer pixels() { return this.image.pixels(); }
}
