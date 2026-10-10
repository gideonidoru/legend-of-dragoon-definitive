package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.game.textures.*;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import legend.game.unpacker.Loader;
import org.json.JSONObject;
import org.legendofdragoon.modloader.registries.RegistryId;
import java.nio.file.*;
import java.util.*;

/** Opt-in private-source audit of the actual packaged mod, without a window or GPU. */
public final class UiHdSourceProbe {
  private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
  private static Tim tim(String path,int offset) throws Exception {return new Tim(new FileData(Files.readAllBytes(Loader.resolve(path))).slice(offset));}
  public static void main(String[] args) throws Exception {
    final var access=GameEngine.class.getDeclaredField("EVENT_ACCESS");access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    legend.game.modding.coremod.CoreMod.registerConfig(new legend.game.saves.ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    try(final var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},UiHdSourceProbe.class.getClassLoader())) {
      final var type=loader.loadClass("uihd.UiHdMod");final Object mod=type.getConstructor().newInstance();
      final var catalog=new JSONObject(new String(type.getResourceAsStream("/uihd/catalog.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("assets");
      final Map<RegistryId,Image> originals=new HashMap<>();
      for(int i=0;i<catalog.length();i++) {
        final var e=catalog.getJSONObject(i);final String kind=e.getString("kind");
        if(!kind.equals("atlas")&&!kind.equals("atlas-native"))continue;
        final Image original;
        if(kind.equals("atlas-native")) {
          final var texture=legend.core.gpu.VramTextureLoader.textureFromTim(tim(e.getString("sourcePath"),0));
          final var palette=legend.core.gpu.VramTextureLoader.palettesFromTim(tim(e.getString("paletteSourcePath"),0))[e.getInt("palette")];
          final var c=e.getJSONArray("crop");final int[] rgba=texture.applyPalette(palette,new legend.core.gpu.Rect4i(c.getInt(0),c.getInt(1),c.getInt(2),c.getInt(3)));
          final byte[] bytes=new byte[rgba.length*4];java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(rgba);
          original=new Image(bytes,c.getInt(2),c.getInt(3));
        } else {final String path=e.getString("sourcePath");original=UiTextures.decode(Files.readAllBytes(path.startsWith("gfx/")?Path.of(path):Loader.resolve(path)));}
        originals.put(new RegistryId(e.getString("registryId")),original);
      }
      final var images=new HashMap<>(originals);type.getMethod("replace",ReplaceAtlasTexturesEvent.class).invoke(mod,new ReplaceAtlasTexturesEvent(images));
      require(images.size()==56,"all atlas entries present");
      for(var e:originals.entrySet())require(images.get(e.getKey()).width==e.getValue().width*4,"actual mod replaces "+e.getKey());
      final var changed=images.get(new RegistryId("lod:dart"));type.getMethod("replace",ReplaceAtlasTexturesEvent.class).invoke(mod,new ReplaceAtlasTexturesEvent(images));
      require(images.get(new RegistryId("lod:dart"))==changed,"earlier atlas owner remains authoritative");
      int pngs=0;
      final Set<String> families=new HashSet<>();
      for(int i=0;i<catalog.length();i++) {
        final var e=catalog.getJSONObject(i);final String kind=e.getString("kind");
        if(kind.equals("png")) {
          final byte[] source=Files.readAllBytes(Path.of(e.getString("sourcePath")));
          final var event=new UiTextureEvent(Path.of(e.getString("sourcePath")),source,UiTextures.decode(source));
          type.getMethod("texture",UiTextureEvent.class).invoke(mod,event);
          require(event.image().width==e.getJSONArray("outputSize").getInt(0),"actual PNG replacement");pngs++;
        }
        if(!kind.equals("native")||!families.add(e.getString("family")))continue;
        final String family=e.getString("family");Tim source=tim(e.getString("sourcePath"),e.getInt("sourceOffset"));
        int x,y,cx,cy,rows;
        if(family.startsWith("battle_hud_")) {
          final int bank=Integer.parseInt(family.substring(11));source=NativeUiTextureEvent.withPalette(source,tim(e.getString("paletteSourcePath"),0));
          x=704;y=256;cx=bank<4?704+bank*16:896+(bank-4)*16;cy=bank<4?496:304;rows=16;
        } else if(family.startsWith("basic_")) {
          final int bank=Integer.parseInt(family.substring(6));source=NativeUiTextureEvent.withPalette(source,tim(e.getString("paletteSourcePath"),0));
          x=832;y=256;cx=832+bank*64;cy=480;rows=4;
        } else if(family.equals("menu_character_extras")) {
          final byte[] a=tim(e.getString("sourcePath"),0x10460).getClutData().getBytes(),b=tim(e.getString("sourcePath"),0x10580).getClutData().getBytes();
          final byte[] palettes=new byte[13*32];System.arraycopy(a,0,palettes,0,4*32);System.arraycopy(b,0,palettes,4*32,b.length);
          source=NativeUiTextureEvent.withPaletteData(source,palettes);x=128;y=256;cx=176;cy=496;rows=13;
        } else if(family.startsWith("dialogue")) {
          final var image=source.getImageRect();final var clut=source.getClutRect();x=image.x;y=image.y;cx=clut.x;cy=clut.y;rows=clut.h;
        } else {
          final var image=source.getImageRect();final var clut=source.getClutRect();x=image.x-512;y=image.y;cx=clut.x-512;cy=clut.y;rows=clut.h;
        }
        NativeUiTextureEvent.post(new NativeUiTextureEvent(family,source,x,y,cx,cy,rows));
      }
      require(pngs==12&&families.size()==15,"every default source family exercised");
      require(NativeUiTextures.selectionCount()==191&&NativeUiTextures.allocatedBytes()==0,"all native variants select lazily");
      final var field=NativeUiTextures.class.getDeclaredField("REGIONS");field.setAccessible(true);
      for(Object region:(List<?>)field.get(null)) {
        final var provider=region.getClass().getDeclaredField("provider");provider.setAccessible(true);
        final var decoded=(NativeUiTextures.Images)((java.util.function.Supplier<?>)provider.get(region)).get();
        NativeUiTextures.validate(decoded.original(),decoded.enhanced());
      }
      NativeUiTextures.reselect(GameEngine.EVENTS::postEvent);
      require(NativeUiTextures.selectionCount()==191&&NativeUiTextures.allocatedBytes()==0,"all fifteen private families replay on mod reboot");
      NativeUiTextures.clear();
      System.out.println("PASS: actual UIHD JAR replaces all56 atlas entries and12 PNGs, selects/validates all191 native variants from15 real private source families, preserves earlier atlas ownership, replays lazily with zero GPU allocation.");
    }
  }
}
