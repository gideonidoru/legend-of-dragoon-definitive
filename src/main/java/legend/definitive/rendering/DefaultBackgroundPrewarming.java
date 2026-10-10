package legend.definitive.rendering;

import legend.core.renderer.Texture;
import legend.game.modding.events.submap.SubmapEnvironmentPreloadEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Source-pinned Skurfa aliases; original native assets use NativeEnvironmentImages for every cut. */
public final class DefaultBackgroundPrewarming {
  private record Scene(int resourceCut, int width, int height, int foregrounds) { }
  private static final Map<String,Scene> SCENES=read();
  private DefaultBackgroundPrewarming() { }
  private static Map<String,Scene> read() {
    try(final var input=DefaultBackgroundPrewarming.class.getResourceAsStream("/lod_core/rendering/default-background-resources.csv")) {
      if(input == null) throw new IOException("Missing bundled background resource mapping");
      final byte[] bytes=input.readNBytes(32769);
      if(bytes.length > 32768) throw new IOException("Background mapping exceeds budget");
      final Map<String,Scene> scenes=new HashMap<>();
      for(final String line : new String(bytes,StandardCharsets.UTF_8).lines().toList()) {
        if(line.startsWith("#") || line.isBlank()) continue;
        final String[] fields=line.split(",");
        if(fields.length != 6) throw new IOException("Invalid background mapping");
        final int disk=Integer.parseInt(fields[0]), cut=Integer.parseInt(fields[1]), resource=Integer.parseInt(fields[2]), width=Integer.parseInt(fields[3]), height=Integer.parseInt(fields[4]), foregrounds=Integer.parseInt(fields[5]);
        if(disk < 0 || disk > 4 || cut < 0 || resource < 0 || width <= 0 || height <= 0 || width > 8192 || height > 8192 || foregrounds < 0 || foregrounds > 32 || scenes.put(disk+":"+cut,new Scene(resource,width,height,foregrounds)) != null) throw new IOException("Invalid background mapping values");
      }
      return Map.copyOf(scenes);
    } catch(final IOException | RuntimeException failure) {
      org.apache.logging.log4j.LogManager.getLogger().warn("Background hints unavailable; retaining ordinary artwork loading",failure);
      return Map.of();
    }
  }
  /** Background first, then earliest foregrounds that fit the retained decode budget together. */
  public static List<String> resources(final int disk, final int cut) {
    final Scene scene=SCENES.get(disk+":"+cut);
    if(scene == null) return List.of();
    final long imageBytes=(long)scene.width()*scene.height()*4;
    final long count=Math.min(scene.foregrounds()+1, PngAssets.SHARED.stats().budgetBytes()/imageBytes);
    if(count == 0) return List.of();
    final String prefix="/scbackgroundhd/cut"+scene.resourceCut();
    final List<String> resources=new ArrayList<>();
    resources.add(prefix+"/background.png");
    for(int i=0;i<count-1;i++) resources.add(prefix+"/foregrounds/foreground_%02d.png".formatted(i));
    return List.copyOf(resources);
  }
  public static void preload(final SubmapEnvironmentPreloadEvent event) {
    for(final var mod : legend.core.GameEngine.MODS.getLoadedMods()) {
      if(!mod.modId.equals("scbackgroundhd")) continue;
      final Class<?> owner=mod.mod.getClass();
      CompletableFuture<?> batch=CompletableFuture.completedFuture(null);
      for(final String path : resources(event.disk,event.submapCut)) {
        batch=batch.handle((value,failure)->null).thenCompose(ignored -> Texture.prewarmPng(owner,path));
      }
      event.waitFor(batch);
    }
  }
}
