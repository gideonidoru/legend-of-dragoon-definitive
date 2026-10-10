package legend.game.tmd;

import legend.core.MathHelper;
import legend.core.renderer.Obj;
import legend.core.renderer.SurfaceMaterial;
import legend.game.unpacker.CtmdTransformer;
import legend.game.unpacker.FileData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static legend.game.Models.updateTmdPacketIlen;

/** 0x1c bytes long */
public class TmdObjTable1c {
  /** Three signed 16-bit source coordinates, including large scenery and stages. */
  public static final float MAX_GEOMETRY_VECTOR_LENGTH_SQUARED = 3_221_225_472.0f;
  public final String name;

  public final Vector3f[] vert_top_00;
  public final int n_vert_04;
  public final Vector3f[] normal_top_08;
  public final int n_normal_0c;
  public final Primitive[] primitives_10;
  public final int n_primitive_14;
  public final int scale_18;
  private final boolean authoredGeometry;
  private boolean nativeVertexIndices;
  Obj refinedObj;
  private boolean refinedCache;

  public boolean requiresNativeVertexIndices() {
    return this.nativeVertexIndices;
  }

  /** Deforming effects address the original CPU vertices, so retire only our refined cache. */
  public void retainNativeVertexIndices() {
    this.nativeVertexIndices = true;
    if(this.refinedCache) {
      this.delete();
    }
  }

  /** Explicit geometry owners retain priority over optional automatic refinement. */
  public boolean isAuthoredGeometry() {
    return this.authoredGeometry;
  }

  Obj obj;
  private int textureWidth;
  private int textureHeight;
  private String meshName;

  /** Owned floating-point geometry for model mods. No parsed source table is mutated. */
  public TmdObjTable1c(final String name, final Vector3f[] vertices, final Vector3f[] normals, final Primitive[] primitives) {
    this.authoredGeometry = true;
    this.name = name;
    if(vertices.length == 0 || vertices.length > 65535 || normals.length > 65535 || primitives.length > 50000) {
      throw new IllegalArgumentException("Authored geometry exceeds part limits");
    }
    this.vert_top_00 = copyVectors(vertices);
    this.normal_top_08 = copyVectors(normals);
    this.primitives_10 = new Primitive[primitives.length];
    int packets = 0;
    for(int i = 0; i < primitives.length; i++) {
      final Primitive primitive = primitives[i];
      if(primitive.width() <= 0 || primitive.width() > 64 || primitive.data().length == 0) {
        throw new IllegalArgumentException("Invalid authored primitive");
      }
      final byte[][] data = new byte[primitive.data().length][];
      for(int j = 0; j < data.length; j++) {
        if(primitive.data()[j].length != primitive.width()) {
          throw new IllegalArgumentException("Authored packet width differs from data");
        }
        data[j] = primitive.data()[j].clone();
      }
      packets = Math.addExact(packets, data.length);
      if(packets > 50000) {
        throw new IllegalArgumentException("Too many authored packets");
      }
      this.primitives_10[i] = new Primitive(primitive.offset(), primitive.width(), primitive.header(), data);
    }
    this.n_vert_04 = vertices.length;
    this.n_normal_0c = normals.length;
    this.n_primitive_14 = packets;
    this.scale_18 = 0;
  }

  private static Vector3f[] copyVectors(final Vector3f[] input) {
    final Vector3f[] output = new Vector3f[input.length];
    for(int i = 0; i < input.length; i++) {
      if(input[i] == null || !input[i].isFinite() || input[i].lengthSquared() > MAX_GEOMETRY_VECTOR_LENGTH_SQUARED) {
        throw new IllegalArgumentException("Invalid authored vector");
      }
      output[i] = new Vector3f(input[i]);
    }
    return output;
  }

  public TmdObjTable1c(final String name, final FileData data, final FileData baseOffset) {
    this.authoredGeometry = false;
    this.name = name;

    final FileData verts = baseOffset.slice(data.readInt(0x0));
    final FileData normals = baseOffset.slice(data.readInt(0x8));
    final FileData primitives = baseOffset.slice(data.readInt(0x10));
    int primitivesOffset = 0;

    this.n_vert_04 = data.readInt(0x4);
    this.n_normal_0c = data.readInt(0xc);
    this.n_primitive_14 = data.readInt(0x14);

    this.vert_top_00 = new Vector3f[this.n_vert_04];
    Arrays.setAll(this.vert_top_00, i -> verts.readSvec3_0(i * 0x8, new Vector3f()));

    for(int i = 0; i < this.vert_top_00.length; i++) {
      this.vert_top_00[i] = verts.readSvec3_0(i * 0x8, new Vector3f());
    }

    this.normal_top_08 = new Vector3f[this.n_normal_0c];
    Arrays.setAll(this.normal_top_08, i -> normals.readSvec3_12(i * 0x8, new Vector3f()));

    final List<Primitive> primitivesList = new ArrayList<>();
    updateTmdPacketIlen(primitives, this.n_primitive_14);

    for(int primitiveIndex = 0; primitiveIndex < this.n_primitive_14; ) {
      final int startOffset = primitivesOffset;
      final int header = primitives.readInt(primitivesOffset);
      final int count = header & 0xffff;

      final int packetSize = CtmdTransformer.primitivePacketSize(header);
      final byte[][] packetData = new byte[count][];

      for(int i = 0; i < count; i++) {
        primitivesOffset += 4;
        packetData[i] = new byte[packetSize];
        primitives.read(primitivesOffset, packetData[i], 0, packetSize);
        primitivesOffset += packetSize;
      }

      primitivesOffset = MathHelper.roundUp(primitivesOffset, 4);

      primitivesList.add(new Primitive(startOffset, packetSize, header, packetData));
      primitiveIndex += count;
    }

    this.primitives_10 = primitivesList.toArray(Primitive[]::new);

    this.scale_18 = data.readInt(0x18);
  }

  public Obj getObj() {
    if(this.obj == null) {
      this.obj = TmdObjLoader.fromObjTable(this.meshName == null ? this.name : this.meshName, this, 0, this.textureWidth, this.textureHeight);
      this.refinedCache = this.obj == this.refinedObj;
      this.obj.surfaceMaterial = this.surfaceMaterial;
    }

    return this.obj;
  }

  private SurfaceMaterial surfaceMaterial = SurfaceMaterial.MATTE;
  private boolean surfaceAuthored;
  private legend.core.renderer.SurfaceResponse[] faceSurfaces;

  /** Flattened primitive/packet order. Null entries retain the part's surface fallback. */
  public void faceSurfaces(final legend.core.renderer.SurfaceResponse[] surfaces) {
    if(surfaces.length != this.n_primitive_14) throw new IllegalArgumentException("Face surface count differs from polygon count");
    this.faceSurfaces = surfaces.clone();
    this.delete(); // Existing geometry must be rebuilt with the new per-face flags.
  }

  public legend.core.renderer.SurfaceResponse faceSurface(final int face) {
    return this.faceSurfaces == null ? null : this.faceSurfaces[face];
  }

  public legend.core.renderer.SurfaceResponse[] faceSurfaces() {
    return this.faceSurfaces == null ? new legend.core.renderer.SurfaceResponse[this.n_primitive_14] : this.faceSurfaces.clone();
  }

  public void surfaceMaterial(final SurfaceMaterial material) {
    this.surfaceMaterial = java.util.Objects.requireNonNull(material);
    this.surfaceAuthored = true;
    if(this.obj != null) this.obj.surfaceMaterial = material;
  }

  /** Optional meshes retain an authored geometry response or the source fallback. */
  public SurfaceMaterial surfaceMaterialLike(final TmdObjTable1c source) {
    return this.surfaceAuthored ? this.surfaceMaterial : source.surfaceMaterial;
  }

  public void rebuildObj(final int textureWidth, final int textureHeight) {
    this.rebuildObj(this.name, textureWidth, textureHeight);
  }

  public void rebuildObj(final String meshName, final int textureWidth, final int textureHeight) {
    if(textureWidth < 0 || textureHeight < 0 || (textureWidth == 0) != (textureHeight == 0)) {
      throw new IllegalArgumentException("Texture dimensions must both be zero or positive");
    }
    this.delete();
    this.meshName = meshName;
    this.textureWidth = textureWidth;
    this.textureHeight = textureHeight;
    this.getObj();
  }

  /** Retain the source's indexed or RGBA texture normalization when replacing geometry. */
  public Obj buildObjLike(final TmdObjTable1c source) {
    if(!this.surfaceAuthored) this.surfaceMaterial = source.surfaceMaterial;
    this.textureWidth = source.textureWidth;
    this.textureHeight = source.textureHeight;
    final Obj result = this.getObj();
    result.surfaceMaterial = this.surfaceMaterial;
    return result;
  }

  public void delete() {
    if(this.obj != null) {
      this.obj.delete();
    }

    this.obj = null;
    this.refinedObj = null;
    this.refinedCache = false;
  }

  @Override
  public String toString() {
    return this.name + ' ' + super.toString();
  }

  public record Primitive(int offset, int width, int header, byte[][] data) { }
}
