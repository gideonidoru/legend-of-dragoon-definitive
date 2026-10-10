package legend.definitive.artwork;

import legend.core.renderer.Obj;
import legend.core.renderer.Texture;
import legend.game.tmd.TmdObjLoader;
import legend.game.wmap.WMapTmdRenderingData18;
import java.io.IOException;

/** Render-thread owner of optional static meshes and their padded color texture. */
public final class WorldTerrainArtwork {
  public final Obj[] meshes;
  public final Texture texture;
  private WorldTerrainArtwork(final Obj[] meshes, final Texture texture) { this.meshes = meshes; this.texture = texture; }
  public static WorldTerrainArtwork create(final WMapTmdRenderingData18 scene, final WorldTerrainAtlas atlas, final WorldTerrainLoad.Snapshot source) throws IOException {
    if(scene.dobj2s_00.length != atlas.parts()) throw new IOException("World model part count changed");
    ArtworkResources.hash(source.model(), atlas.modelHash());
    if(!WorldTerrainSource.decode(source.model(), source.bank()).bankHash().equals(atlas.bankHash())) throw new IOException("World texture bank changed");
    final Obj[] meshes = new Obj[atlas.parts()];
    Texture texture = null;
    try {
      texture = Texture.create("EnvHD world terrain", builder -> {
        final var pixels = org.lwjgl.BufferUtils.createByteBuffer(atlas.width() * atlas.height() * 4);
        pixels.put(atlas.rgba()).flip(); builder.data(pixels, atlas.width(), atlas.height()); builder.wrapS(false); builder.wrapT(false);
      }).hdAtlasFiltering(32);
      // Part zero is always animated ocean. Held faces within other parts retain VRAM sampling.
      for(int i = 1; i < meshes.length; i++) if(atlas.replacesPart(i)) meshes[i] = TmdObjLoader.fromObjTable("EnvHD world part " + i, scene.dobj2s_00[i].tmd_08, 0, atlas.width(), atlas.height(), atlas);
      return new WorldTerrainArtwork(meshes, texture);
    } catch(final RuntimeException | Error failure) {
      for(final Obj mesh : meshes) if(mesh != null) mesh.delete();
      if(texture != null) texture.delete();
      throw failure;
    }
  }
  public void delete() { for(final Obj mesh : this.meshes) if(mesh != null) mesh.delete(); this.texture.delete(); }
}
