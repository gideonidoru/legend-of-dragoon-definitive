package legend.definitive.materials;

import legend.core.renderer.Obj;
import legend.core.renderer.Texture;
import legend.core.renderer.QueuedModelBattleTmd;
import legend.game.types.Model124;
import legend.game.tmd.TmdObjLoader;
import legend.game.tmd.TmdObjTable1c;

/** Owns optional rendered meshes; native CPU geometry, animations and indexed effects remain intact. */
public final class CharacterAppearance implements AutoCloseable {
  private final TmdObjTable1c[] owners;
  private final Obj[] meshes;
  private Texture colour;

  private CharacterAppearance(final TmdObjTable1c[] owners, final Obj[] meshes, final Texture colour) {
    this.owners = owners;
    this.meshes = meshes;
    this.colour = colour;
  }

  public static CharacterAppearance create(final Model124 model, final MaterialAtlas atlas, final int columns,
                                            final java.util.Map<Integer, legend.core.renderer.SurfaceResponse> surfaces, final byte[] originalModel) {
    requireNativeSource(model, originalModel);
    final TmdObjTable1c[] owners = new TmdObjTable1c[model.modelParts_00.length];
    final Obj[] meshes = new Obj[owners.length];
    Texture texture = null;
    try {
      final CharacterUv mapping = new CharacterUv(atlas, columns, model.uvAdjustments_9d, surfaces);
      for(int i = 0; i < owners.length; i++) {
        owners[i] = model.modelParts_00[i].tmd_08;
        if(owners[i].getClass() != TmdObjTable1c.class || owners[i].isAuthoredGeometry() || owners[i].requiresNativeVertexIndices())
          throw new IllegalArgumentException("Character override requires explicit material handoff");
        meshes[i] = TmdObjLoader.fromObjTableMapped("CharHD " + i, owners[i], mapping);
      }
      texture = Texture.create("CharHD colour", builder -> {
        builder.data(atlas.rgba().duplicate(), atlas.width(), atlas.height());
        builder.wrapS(false);
        builder.wrapT(false);
      });
      return new CharacterAppearance(owners, meshes, texture);
    } catch(final RuntimeException failure) {
      for(final Obj mesh : meshes) if(mesh != null) mesh.delete();
      if(texture != null) texture.delete();
      throw failure;
    }
  }

  /** Reject unknown UV/packet owners before allocating any optional resources. */
  public static void requireNativeSource(final Model124 model, final byte[] originalModel) {
    final var source = new legend.game.types.CContainer("CharHD source control", new legend.game.unpacker.FileData(originalModel.clone())).tmdPtr_00.tmd.objTable;
    if(model.modelParts_00 == null || model.modelParts_00.length != source.length) throw new IllegalArgumentException("Character parts differ");
    for(int i = 0; i < source.length; i++) {
      final var part = new legend.core.gte.ModelPart10();
      part.tmd_08 = source[i];
      legend.game.Models.adjustPartUvs(part, model.uvAdjustments_9d);
      final var active = model.modelParts_00[i].tmd_08;
      if(active.primitives_10.length != source[i].primitives_10.length) throw new IllegalArgumentException("Character packet groups differ");
      for(int group = 0; group < active.primitives_10.length; group++) {
        final var expected = source[i].primitives_10[group];
        final var actual = active.primitives_10[group];
        if(expected.header() != actual.header() || !java.util.Arrays.deepEquals(expected.data(), actual.data()))
          throw new IllegalArgumentException("Active character UV/packet override retains priority");
      }
    }
  }

  public boolean applies(final Model124 model) {
    if(this.colour == null || model.modelParts_00 == null || model.modelParts_00.length != this.owners.length) return false;
    for(final boolean animation : model.animateTextures_ec) if(animation) return false;
    for(int i = 0; i < this.owners.length; i++) if(model.modelParts_00[i].tmd_08 != this.owners[i] || this.owners[i].requiresNativeVertexIndices()) return false;
    return true;
  }

  public Obj mesh(final int part) { return this.meshes[part]; }
  public void bind(final QueuedModelBattleTmd queue) { queue.texture(this.colour, 0); }

  @Override public void close() {
    if(this.colour == null) return;
    for(final Obj mesh : this.meshes) mesh.delete();
    this.colour.delete();
    this.colour = null;
  }
}
