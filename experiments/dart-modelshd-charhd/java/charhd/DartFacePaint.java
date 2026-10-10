package charhd;

import legend.game.modding.events.tmd.TmdAppearanceEvent;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.TmdGeometryIdentity;
import org.legendofdragoon.modloader.events.EventListener;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;

/** Opt-in CharHD extension prototype: paint source-colored face surfaces after ModelsHD. */
@org.legendofdragoon.modloader.Mod(id = "charhd_dart_experiment", version = "3.0.0")
public final class DartFacePaint {
  private final BufferedImage paint;
  private final DartHeadReconstruction reconstruction;
  private final ByteBuffer fieldRgba;
  private final int fieldWidth, fieldHeight;
  private static final String FIELD = "b84e13a11adbd3419e1e4c5b810c8bd797f9dc68af5d41fe3c4736ce5c4f584b";
  private static final String COMBAT = "0bfd5ffdc6da5b99d770e75f3cab89c86718a541f996250eb362cfb07642f6bb";
  private final DartFaceMapping mapping = DartFaceMapping.load();

  public DartFacePaint() throws IOException {
    final byte[] bytes;
    try(final var input = getClass().getResourceAsStream("/charhd-experiment/dart-head-detail-v2.png")) {
      if(input == null) throw new IOException("Missing experimental face paint");
      bytes = input.readNBytes(8 * 1024 * 1024 + 1);
    }
    final String expected;
    try(final var input = getClass().getResourceAsStream("/charhd-experiment/dart-head-detail-v2.sha256")) {
      if(input == null) throw new IOException("Missing experimental face paint checksum");
      expected = new String(input.readNBytes(65), java.nio.charset.StandardCharsets.UTF_8).strip();
    }
    this.paint = read(bytes, expected);
    this.reconstruction = new DartHeadReconstruction();
    final byte[] fieldBytes;
    final String fieldDigest;
    try(final var input = getClass().getResourceAsStream("/charhd-experiment/dart-field-charhd-v1.png");
        final var checksum = getClass().getResourceAsStream("/charhd-experiment/dart-field-charhd-v1.sha256")) {
      if(input == null || checksum == null) throw new IOException("Missing experimental field restoration");
      fieldBytes = input.readNBytes(8*1024*1024+1);
      fieldDigest = new String(checksum.readNBytes(65), java.nio.charset.StandardCharsets.UTF_8).strip();
    }
    final var field = read(fieldBytes, fieldDigest);
    this.fieldWidth = field.getWidth(); this.fieldHeight = field.getHeight();
    if(this.fieldWidth != 256 || this.fieldHeight != 448) throw new IOException("Field restoration dimensions differ from source");
    final var rgba = ByteBuffer.allocateDirect(this.fieldWidth * this.fieldHeight * 4);
    for(int y = 0; y < this.fieldHeight; y++) for(int x = 0; x < this.fieldWidth; x++) {
      final int pixel = field.getRGB(x,y);
      rgba.put((byte)(pixel>>>16)).put((byte)(pixel>>>8)).put((byte)pixel).put((byte)(pixel>>>24));
    }
    this.fieldRgba = rgba.flip().asReadOnlyBuffer();
    legend.core.GameEngine.EVENTS.register(this);
  }

  @EventListener public void fieldTextures(final legend.game.modding.events.submap.SubmapObjectTextureEvent event) {
    if(!(event.getSubmap() instanceof legend.game.submap.RetailSubmap retail)) return;
    for(int index = 0; index < retail.objects.size(); index++) {
      if(event.textures.containsKey(index)) continue;
      final var texture = retail.getObjectTexture(index);
      if(texture == null) continue;
      try {
        final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texture.getData().getBytes()));
        if(hash.equals("477b2c39ad9809524c8e8852af59a1beff219dc536f4b3e8dffd18c21d54c7c3"))
          event.textures.putIfAbsent(index, builder -> builder.data(this.fieldRgba.duplicate(), this.fieldWidth, this.fieldHeight));
      } catch(final java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
  }

  static BufferedImage read(final byte[] bytes, final String expected) throws IOException {
    try {
      if(bytes.length > 8 * 1024 * 1024 || !expected.matches("[a-f0-9]{64}") ||
        !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected))
        throw new IOException("Experimental face paint checksum differs");
      // Probe image dimensions before decoding an untrusted PNG into pixels.
      try(final var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
        final var readers = javax.imageio.ImageIO.getImageReaders(input);
        if(!readers.hasNext()) throw new IOException("Invalid experimental face image");
        final var reader = readers.next();
        try {
          reader.setInput(input);
          if(!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) < 1 || reader.getHeight(0) < 1 ||
            reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw new IOException("Face paint exceeds pixel budget");
          return reader.read(0);
        } finally { reader.dispose(); }
      }
    } catch(final java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }

  @EventListener public void apply(final TmdAppearanceEvent event) {
    if(event.appearance != event.geometry || event.source.requiresNativeVertexIndices() ||
      event.geometry != event.source && !event.geometry.hasSourceFaces()) return;
    final String identity = TmdGeometryIdentity.identity(new TmdObjTable1c[] {event.source});
    final var reconstructed=this.reconstruction.paint(identity,event.geometry);
    if(reconstructed!=null)event.appearance=reconstructed;
    else if(FIELD.equals(identity)) event.appearance = paint(event.geometry, this.mapping.fieldFaces(), Set.of(), this.paint, true, this.mapping);
    else if(COMBAT.equals(identity)) event.appearance = paint(event.geometry, this.mapping.combatFaces(), Set.of(), this.paint, false, this.mapping);
  }

  /** Full-resolution supplemental albedo; original texture pages and CPU tables stay intact. */
  public static TmdObjTable1c paint(final TmdObjTable1c geometry, final Set<Integer> selected, final BufferedImage image, final boolean field) {
    return paint(geometry,selected,Set.of(),image,field,DartFaceMapping.load());
  }

  public static TmdObjTable1c paintHead(final TmdObjTable1c geometry,final BufferedImage image,final boolean field) {
    final var mapping=DartFaceMapping.load();
    return paint(geometry,field?mapping.fieldFaces():mapping.combatFaces(),field?mapping.fieldHairFaces():mapping.combatHairFaces(),image,field,mapping);
  }

  private static TmdObjTable1c paint(final TmdObjTable1c geometry, final Set<Integer> selected, final Set<Integer> hair, final BufferedImage image,
                                    final boolean field, final DartFaceMapping mapping) {
    if(geometry.faceDetail() != null) throw new IllegalArgumentException("Existing face detail retains priority");
    final var primitives = new ArrayList<TmdObjTable1c.Primitive>();
    final var lineage = new ArrayList<Integer>();
    final var surfaces = new ArrayList<legend.core.renderer.SurfaceResponse>();
    final float[][] detailUvs = new float[geometry.n_primitive_14][];
    int rendered = 0;
    boolean anyPaint = false;
    for(final var primitive : geometry.primitives_10) for(final byte[] packet : primitive.data()) {
      final int source = geometry.sourceFace(rendered);
      int header = primitive.header();
      if(selected.contains(source) || hair.contains(source)) {
        anyPaint = true;
        final int mode = header >>> 24, count = (mode & 8) == 0 ? 3 : 4;
        final boolean lit = (mode & 1) == 0;
        int cursor = (mode & 4) != 0 ? count * 4 : 0;
        if((header & 0x40000) != 0 || !lit) cursor += count * 4;
        else if((mode & 4) == 0) cursor += 4;
        final float[] uv = new float[count*2];
        for(int corner = 0; corner < count; corner++) {
          if(lit && ((mode & 16) != 0 || corner == 0)) cursor += 2;
          final int vertex = u16(packet,cursor); cursor += 2;
          final var point = geometry.vert_top_00[vertex];
          final double x = field ? point.x : point.z / mapping.combatScale(), y = field ? point.y : (mapping.combatOriginY()-point.y) / mapping.combatScale();
          uv[corner*2] = (float)Math.clamp(.5 + x * mapping.projection(), 0, 1);
          final double originY=field ? mapping.originY() : mapping.combatFaceOriginY();
          final double verticalScale=field ? 1.0 : mapping.combatFaceVerticalScale();
          uv[corner*2+1] = (float)(Math.clamp(.5 + (y-originY) * mapping.projection() * verticalScale, 0, 1)*mapping.faceRegionHeight());
          if(hair.contains(source)) {
            final double z=field ? point.z : -point.x/mapping.combatScale();
            final var hairProjection=(field?mapping.fieldHairProjection():mapping.combatHairProjection()).get(source);
            uv[corner*2]=(float)(hairProjection.u(x,y,z)*mapping.hairRegionWidth());
            uv[corner*2+1]=(float)(mapping.faceRegionHeight()+(1-mapping.faceRegionHeight())*hairProjection.v(x,y,z));
          }
        }
        detailUvs[rendered] = uv;
        header &= ~0x02000000; // Selected authored face paint is opaque, not a PSX STP material.
      }
      primitives.add(new TmdObjTable1c.Primitive(0,primitive.width(),header,new byte[][] {packet}));
      lineage.add(source); surfaces.add(geometry.faceSurface(rendered++));
    }
    if(!anyPaint) return geometry;
    final var result = new TmdObjTable1c("CharHD experimental face detail", geometry.vert_top_00, geometry.normal_top_08,
      primitives.toArray(TmdObjTable1c.Primitive[]::new));
    result.sourceFaces(lineage.stream().mapToInt(Integer::intValue).toArray(), lineage.stream().mapToInt(Integer::intValue).max().orElseThrow()+1);
    result.faceSurfaces(surfaces.toArray(legend.core.renderer.SurfaceResponse[]::new));
    final var rgba=ByteBuffer.allocateDirect(image.getWidth()*image.getHeight()*4);
    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
      final int rgb=image.getRGB(x,y);rgba.put((byte)(rgb>>>16)).put((byte)(rgb>>>8)).put((byte)rgb).put((byte)255);
    }
    result.faceDetail(new legend.game.tmd.TmdFaceDetail(rgba.flip(),image.getWidth(),image.getHeight(),detailUvs));
    return result;
  }

  private static int u16(final byte[] data, final int offset) { return (data[offset] & 255) | (data[offset+1] & 255) << 8; }

}
