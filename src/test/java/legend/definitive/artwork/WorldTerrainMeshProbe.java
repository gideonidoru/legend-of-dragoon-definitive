package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.core.gte.ModelPart10;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdWithId;
import legend.game.unpacker.FileData;
import legend.game.wmap.WMapTmdRenderingData18;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.events.EventManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual custom ModelsHD world geometry plus EnvHD UVs on a recording renderer. */
public final class WorldTerrainMeshProbe {
  public static final class GeometryListener {
    final Object pass;
    final java.lang.reflect.Method prepare;
    final Path overrides;
    int refined, calls;
    GeometryListener(final Path overrides) throws Exception {
      final Class<?> type=Class.forName("modelshd.GeometryPass");this.pass=type.getConstructor().newInstance();
      this.prepare=type.getMethod("prepare",TmdGeometryEvent.class,Path.class);this.overrides=overrides;
    }
    @EventListener public void refine(final TmdGeometryEvent event) throws Exception {
      this.calls++;if((boolean)this.prepare.invoke(this.pass,event,this.overrides)) this.refined++;
    }
  }
  public static void main(final String[] args) throws Exception {
    if(args.length!=2) throw new IllegalArgumentException("Requires private extraction and atlas folder");
    final var renderer=GameEngine.RENDERER.getClass().getDeclaredField("api");renderer.setAccessible(true);final Object previous=renderer.get(GameEngine.RENDERER);
    final var api=new TerrainMeshTest.RecordingApi();renderer.set(GameEngine.RENDERER,api);
    final var events=GameEngine.class.getDeclaredField("EVENT_ACCESS");events.setAccessible(true);final var access=(EventManager.Access)events.get(null);access.initialize(GameEngine.MODS);
    final Path overrides=Files.createTempDirectory("envhd-empty-model-overrides-");
    int parts=0,nativeVertices=0,hdVertices=0;
    try {
      final var listener=new GeometryListener(overrides);GameEngine.EVENTS.register(listener);
      final Path base=Path.of(args[0]).resolve("SECT/DRGN0.BIN"), atlases=Path.of(args[1]);
      final var refinedCache=legend.game.tmd.TmdObjTable1c.class.getDeclaredField("refinedObj");refinedCache.setAccessible(true);
      for(int bankIndex=0;bankIndex<8;bankIndex++) {
        final List<byte[]> bank=new ArrayList<>();
        try(final var paths=Files.list(base.resolve(Integer.toString(5697+bankIndex)))) {
          for(final var path:paths.filter(p->p.getFileName().toString().matches("[0-9]+")).sorted(java.util.Comparator.comparingInt(p->Integer.parseInt(p.getFileName().toString()))).toList()) bank.add(Files.readAllBytes(path));
        }
        final byte[] raw=Files.readAllBytes(base.resolve(Integer.toString(5705+bankIndex)));final String hash=legend.definitive.textures.TexturePilot.sha256(raw);
        final Path folder=atlases.resolve(hash);final var atlas=WorldTerrainAtlas.read(Files.readAllBytes(folder.resolve("manifest.json")),Files.readAllBytes(folder.resolve("atlas-v1.png")),raw,bank);
        final var source=new WorldTerrainLoad.Snapshot(raw,bank);final var tmd=new TmdWithId("private world source",new FileData(raw.clone()));
        final var scene=new WMapTmdRenderingData18();scene.dobj2s_00=new ModelPart10[tmd.tmd.objTable.length];
        for(int i=0;i<scene.dobj2s_00.length;i++) {scene.dobj2s_00[i]=new ModelPart10();scene.dobj2s_00[i].tmd_08=tmd.tmd.objTable[i];}
        final var artwork=WorldTerrainArtwork.create(scene,atlas,source);
        if(artwork.meshes[0]!=null || !artwork.texture.isHdFiltered()) throw new IllegalStateException("Water retention or atlas filtering differs");
        for(int i=1;i<artwork.meshes.length;i++) if(artwork.meshes[i]!=null) {
          if(refinedCache.get(scene.dobj2s_00[i].tmd_08)!=null) throw new IllegalStateException("Artwork contaminated native geometry cache");
          parts++;
          for(final Mesh mesh:((MeshObj)artwork.meshes[i]).meshes) for(int v=0;v<mesh.vertices().length;v+=16) {
            final float[] vertices=mesh.vertices();if(((int)vertices[v+15]&4)==0) continue;
            final int bpp=(int)vertices[v+9]>>>7&3;
            if(bpp==3) {if(vertices[v+7]<0 || vertices[v+7]>1 || vertices[v+8]<0 || vertices[v+8]>1) throw new IllegalStateException("Invalid packed UV");hdVertices++;}
            else if(bpp==0 || bpp==1) nativeVertices++;
            else throw new IllegalStateException("Unsupported terrain texture type");
          }
        }
        artwork.delete();
        // Rebuilding the native scene after optional deletion must remain valid.
        for(final var part:scene.dobj2s_00) {part.tmd_08.getObj();part.tmd_08.delete();}
        Obj.deleteObjects();
      }
      System.out.println("Headless terrain mesh compatibility: "+parts+" optional parts; "+hdVertices+" HD / "+nativeVertices+" retained indexed vertices; ModelsHD refined "+listener.refined+" of "+listener.calls+" requests. Native visual acceptance remains pending.");
    } finally {access.reset();Obj.deleteObjects();Texture.deleteTextures();renderer.set(GameEngine.RENDERER,previous);Files.delete(overrides);}
  }
}
