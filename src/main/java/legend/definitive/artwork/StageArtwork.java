package legend.definitive.artwork;

import legend.core.renderer.Obj;
import legend.core.renderer.Texture;
import legend.definitive.materials.MaterialAtlas;
import legend.game.combat.environment.BattleStage;
import legend.game.tmd.TmdObjLoader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Owns only the optional stage meshes/texture, leaving retail resources available. */
public final class StageArtwork {
  public final Obj[] meshes;
  public final Texture texture;
  private StageArtwork(final Obj[] meshes, final Texture texture) { this.meshes = meshes; this.texture = texture; }

  public static StageArtwork create(final BattleStage stage, final MaterialAtlas atlas, final byte[] originalModel, final byte[] tim) throws IOException {
    if(!atlas.modelHash().equals(legend.definitive.textures.TexturePilot.sha256(originalModel)) || !atlas.timHash().equals(legend.definitive.textures.TexturePilot.sha256(tim))) throw new IOException("Stage source changed");
    validateReferences(originalModel, tim);
    final Obj[] meshes = new Obj[stage.dobj2s_00.length];
    Texture texture = null;
    try {
      texture = Texture.create("EnvHD stage", builder -> {
        final ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(atlas.width() * atlas.height() * 4);
        pixels.put(atlas.rgba()).flip();
        builder.data(pixels, atlas.width(), atlas.height()); builder.wrapS(false); builder.wrapT(false);
      });
      final MaterialUv uv = new MaterialUv(atlas, 4);
      for(int i = 0; i < meshes.length; i++) meshes[i] = TmdObjLoader.fromObjTable("EnvHD " + stage.name + " part " + i, stage.dobj2s_00[i].tmd_08, 0, atlas.width(), atlas.height(), uv);
      return new StageArtwork(meshes, texture);
    } catch(final RuntimeException failure) {
      for(final Obj mesh : meshes) if(mesh != null) mesh.delete();
      if(texture != null) texture.delete();
      throw failure;
    }
  }
  /** First slice accepts one complete 4-bit page with a canonical 64x16 palette grid. */
  public static void validateReferences(final byte[] model, final byte[] tim) throws IOException {
    try {
      final ByteBuffer t = ByteBuffer.wrap(tim).order(ByteOrder.LITTLE_ENDIAN), m = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN);
      if(t.getInt(4) != 8 || t.getShort(16) != 64 || t.getShort(18) != 16 || m.getInt(4) != 0 || m.getInt(8) != 0) throw new IOException("Animated or unsupported stage material layout");
      final int cx = Short.toUnsignedInt(t.getShort(12)), cy = Short.toUnsignedInt(t.getShort(14)), image = 8 + t.getInt(8);
      final int ix = Short.toUnsignedInt(t.getShort(image + 4)), iy = Short.toUnsignedInt(t.getShort(image + 6));
      if(cx % 64 != 0 || cy % 16 != 0 || t.getShort(image + 8) != 64 || t.getShort(image + 10) != 256) throw new IOException("Unsupported stage page origin");
      final int base = m.getInt(0) + 12, parts = m.getInt(base - 4);
      if(parts < 1 || parts > 256) throw new IOException("Invalid stage part count");
      for(int i = 0; i < parts; i++) {
        final int entry = base + i * 28, count = m.getInt(entry + 20);
        int offset = base + m.getInt(entry + 16);
        if(count < 0 || count > 100000) throw new IOException("Invalid stage face count");
        for(int j = 0; j < count; j++) {
          final int header = m.getInt(offset), bytes = (header >>> 8 & 255) * 4;
          if(bytes < 4 || bytes > 64) throw new IOException("Invalid stage packet");
          if((header >>> 24 & 4) != 0) {
            final int clut = Short.toUnsignedInt(m.getShort(offset + 6)), page = Short.toUnsignedInt(m.getShort(offset + 10));
            final int px = (clut & 63) * 16, py = clut >>> 6;
            if((page >>> 7 & 3) != 0 || (page & 15) * 64 != ix || (page & 16) * 16 != iy || px < cx || px >= cx + 64 || py < cy || py >= cy + 16) throw new IOException("Stage face references another texture page");
          }
          offset += 4 + bytes;
        }
      }
    } catch(final IndexOutOfBoundsException | java.nio.BufferUnderflowException failure) { throw new IOException("Truncated stage references", failure); }
  }
  public void delete() { for(final Obj mesh : this.meshes) mesh.delete(); this.texture.delete(); }
}
