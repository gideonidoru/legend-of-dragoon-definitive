package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.core.Version;
import legend.core.platform.*;
import legend.core.renderer.*;
import legend.definitive.mods.ManagedModProfile;
import legend.game.submap.MapIndicator;
import legend.game.textures.*;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.legendofdragoon.modloader.ModManager;
import java.nio.file.*;
import java.util.*;

/** Private real queued-marker selection replay; creates no native window or game. */
public class UiHdFieldMarkerProbe {
  static Object field(Object o, Class<?> type, String n) throws Exception { var f=type.getDeclaredField(n);f.setAccessible(true);return f.get(o); }
  static void checkNative() throws Exception {
    for(String name:new String[]{"big_arrow","small_arrow"}) {
      final Tim tim=new Tim(new FileData(Files.readAllBytes(legend.game.unpacker.Loader.resolve("SUBMAP/"+name+".tim"))));
      final var rects=new legend.core.gpu.Rect4i[]{tim.getImageRect(),tim.getClutRect()};
      final var blocks=new FileData[]{tim.getImageData(),tim.getClutData()};
      for(int block=0;block<2;block++)for(int y=0;y<rects[block].h;y++)for(int x=0;x<rects[block].w;x++) {
        if(GameEngine.GPU.getPixel15(rects[block].x+x,rects[block].y+y)!=blocks[block].readUShort((y*rects[block].w+x)*2))throw new AssertionError("Marker native image/CLUT was modified");
      }
    }
  }
  static void checkAll(final MapIndicator marker, final RenderBatch batch, final java.lang.reflect.Method use, final int expected, final String name) throws Exception {
    int count=0;
    for(var palette:new int[][]{{992,272},{992,273},{976,472}})for(int frame=0;frame<12;frame++) {
      batch.orthoPool.reset();marker.renderPlayerIndicator(20,20,38,1,1,1,palette[0],palette[1],frame*16,96);
      var queued=batch.orthoPool.get(0);use.invoke(queued);
      if((boolean)field(queued,QueuedModelStandard.class,"uiArtwork"))count++;
    }
    if(count!=expected)throw new AssertionError(name+" expected "+expected+" enhanced frames, got "+count);
    System.out.println(name+": "+count+"/36 enhanced");
  }
  public static void main(String[] args) throws Exception {
    if(!Boolean.getBoolean("java.awt.headless"))throw new AssertionError("headless required");
    boolean enabled = args.length < 4 || Boolean.parseBoolean(args[3]);
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY,"hd");
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY,Path.of(args[1],"definitive-hd-mods.properties").toString());
    ((ModManager.Access)field(null,GameEngine.class,"MOD_ACCESS")).findMods(Path.of(args[0]),Version.VERSION);
    var missing = GameEngine.bootMods(enabled ? Set.of("lod", "uihd") : Set.of("lod"));
    if(!missing.isEmpty() || !GameEngine.MODS.getFailedToLoad().isEmpty() || GameEngine.MODS.isReady("uihd") != enabled) throw new AssertionError("Mod activation failed");
    var init=RenderEngine.class.getDeclaredMethod("init",Window.class);init.setAccessible(true);
    init.invoke(GameEngine.RENDERER,new NoopWindow(new NoopPlatformManager(),1280,800));GameEngine.GPU.init();
    var map = new legend.game.submap.SMap();
    var load = legend.game.submap.SMap.class.getDeclaredMethod("loadMiscTextures", int.class);
    load.setAccessible(true); load.invoke(map, 2);
    System.out.println("REGIONS "+NativeUiTextures.selectionCount());
    var batch=(RenderBatch)field(GameEngine.RENDERER,RenderEngine.class,"mainBatch");
    var use=QueuedModelStandard.class.getDeclaredMethod("useTexture");use.setAccessible(true);
    var marker=new MapIndicator();int hd=0,total=0;
    int[][] palettes={{992,272},{992,273},{976,472}};
    for(var p:palettes){int selected=0;for(int frame=0;frame<12;frame++){
      batch.orthoPool.reset(); marker.renderPlayerIndicator(20,20,38,1,1,1,p[0],p[1],frame*16,96);
      var q=batch.orthoPool.get(0);use.invoke(q);total++;
      if((boolean)field(q,QueuedModelStandard.class,"uiArtwork")){hd++;selected++;}
    }System.out.println("CLUT "+p[0]+","+p[1]+": "+selected+"/12 HD frames");}
    System.out.println("TOTAL "+hd+"/"+total+" enhanced; source selection only, no GPU visual acceptance");
    checkNative();
    if(hd!=(enabled ? total : 0))throw new AssertionError("Player marker retains native low-resolution frames");
    if(enabled) {
      final long residentBytes=NativeUiTextures.allocatedBytes();
      GameEngine.bootMods(Set.of("lod"));checkAll(marker,batch,use,0,"disabled-after-selection");
      GameEngine.bootMods(Set.of("lod","uihd"));checkAll(marker,batch,use,36,"reenabled-from-remembered-sources");
      if(NativeUiTextures.allocatedBytes()!=residentBytes || NativeUiTextures.selectionCount()!=6)throw new AssertionError("Reselection grows marker residency");
      final byte[] changed=Files.readAllBytes(legend.game.unpacker.Loader.resolve("SUBMAP/big_arrow.tim"));changed[changed.length-1]^=1;
      final Tim invalid=new Tim(new FileData(changed));GameEngine.GPU.uploadEffectTim(invalid);NativeUiTextureEvent.uploaded("indicator_big_arrow",invalid);
      checkAll(marker,batch,use,0,"changed-source-retires-all-player-palettes");
      load.invoke(map,2);checkAll(marker,batch,use,36,"valid-source-restored");
      checkNative();
      if(NativeUiTextures.allocatedBytes()!=residentBytes || NativeUiTextures.selectionCount()!=6)throw new AssertionError("Reload grows marker residency");
      System.out.println("PLAYER_RESIDENT_BYTES "+residentBytes);
    }
    marker.destroy(); NativeUiTextures.clear();
  }
}
