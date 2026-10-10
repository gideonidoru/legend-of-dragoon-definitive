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
      int pngs=0, rasters=0, nativeVariants=0;
      final Set<String> families=new HashSet<>();
      for(int i=0;i<catalog.length();i++) {
        final var e=catalog.getJSONObject(i);final String kind=e.getString("kind");
        if(kind.equals("png")) {
          final byte[] source=Files.readAllBytes(Path.of(e.getString("sourcePath")));
          final var event=new UiTextureEvent(Path.of(e.getString("sourcePath")),source,UiTextures.decode(source));
          type.getMethod("texture",UiTextureEvent.class).invoke(mod,event);
          require(event.image().width==e.getJSONArray("outputSize").getInt(0),"actual PNG replacement");pngs++;
        }
        if(kind.equals("raster")) {
          final String family=e.getString("family");
          final byte[] raw;
          final Image original;
          if(family.equals("game_over")) {
            raw=Files.readAllBytes(Loader.resolve(e.getString("sourcePath")));
            original=SkySource.decodeUi(raw);
          } else {
            final var paths=e.getJSONArray("sourcePaths");
            final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            final legend.core.gpu.VramTexture[] textures=new legend.core.gpu.VramTexture[paths.length()];
            for(int j=0;j<paths.length();j++) {
              final String path=paths.getString(j);bytes.write(Files.readAllBytes(Loader.resolve(path)));
              textures[j]=legend.core.gpu.VramTextureLoader.textureFromTim(tim(path,0));
            }
            raw=bytes.toByteArray();
            final var texture=family.equals("title_background")?legend.core.gpu.VramTextureLoader.stitchVertical(textures)
              :family.equals("title_copyright")?legend.core.gpu.VramTextureLoader.stitchHorizontal(textures):textures[0];
            final var palette=legend.core.gpu.VramTextureLoader.palettesFromTim(tim(paths.getString(0),0))[0];
            original=UiRasters.decode((legend.core.gpu.VramTextureSingle)texture,(legend.core.gpu.VramTextureSingle)palette);
          }
          final var event=new UiRasterEvent(family,raw,original);
          type.getMethod("raster",UiRasterEvent.class).invoke(mod,event);
          require(event.replaced()&&event.image().width==e.getJSONArray("outputSize").getInt(0),"actual raster "+family);
          NativeUiTextures.validate(original,event.image());rasters++;
          continue;
        }
        if(!kind.equals("native")||!families.add(e.getString("family")))continue;
        final String family=e.getString("family");Tim source=tim(e.getString("sourcePath"),e.getInt("sourceOffset"));
        int x,y,cx,cy,rows;String slot=family;
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
        } else if(family.equals("menu")||family.equals("items")||family.equals("menu_characters")) {
          final var image=source.getImageRect();final var clut=source.getClutRect();x=image.x-512;y=image.y;cx=clut.x-512;cy=clut.y;rows=clut.h;
        } else {
          final var image=source.getImageRect();final var clut=source.getClutRect();x=image.x;y=image.y;cx=clut.x;cy=clut.y;rows=clut.h;
          if(family.startsWith("chapter_")) slot=Integer.parseInt(family.substring(family.lastIndexOf('_')+1))<8?"chapter_name":"chapter_number";
          if(family.startsWith("credit_")) {
            final int index=Integer.parseInt(family.substring(7)),creditSlot=index%16;
            slot="credit_slot_"+creditSlot;x=512+creditSlot/8*128;y=creditSlot%8*64;cx=896;cy=creditSlot;rows=1;
          }
        }
        NativeUiTextureEvent.post(new NativeUiTextureEvent(family,slot,source,x,y,cx,cy,rows));
        final var field=NativeUiTextures.class.getDeclaredField("REGIONS");field.setAccessible(true);
        int decodedCount=0;
        for(Object region:(List<?>)field.get(null)) {
          final var owned=region.getClass().getDeclaredField("slot");owned.setAccessible(true);
          if(!slot.equals(owned.get(region)))continue;
          final var provider=region.getClass().getDeclaredField("provider");provider.setAccessible(true);
          final var decoded=(NativeUiTextures.Images)((java.util.function.Supplier<?>)provider.get(region)).get();
          NativeUiTextures.validate(decoded.original(),decoded.enhanced());decodedCount++;
        }
        final int expected=(int)java.util.stream.IntStream.range(0,catalog.length()).mapToObj(catalog::getJSONObject)
          .filter(a->a.getString("kind").equals("native")&&a.getString("family").equals(family)).count();
        require(decodedCount==expected,"all native source variants "+family);nativeVariants+=decodedCount;
      }
      require(pngs==13&&rasters==4&&families.size()==424&&nativeVariants==665,"complete full-source denominator exercised");
      require(NativeUiTextures.selectionCount()<=384&&NativeUiTextures.allocatedBytes()==0,"all current slots stay metadata-only");
      final int active=NativeUiTextures.selectionCount();
      NativeUiTextures.reselect(GameEngine.EVENTS::postEvent);
      require(NativeUiTextures.selectionCount()==active&&NativeUiTextures.allocatedBytes()==0,"all current source slots replay after mod reboot");
      verifySavedPortraits(type,mod,originals);
      final byte[] noClut=new byte[22];final var raw=java.nio.ByteBuffer.wrap(noClut).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      raw.putInt(0,16).putInt(4,2).putInt(8,14).putShort(16,(short)1).putShort(18,(short)1);
      NativeUiTextureEvent.uploaded("the_end",new Tim(new FileData(noClut)));
      require(NativeUiTextures.allocatedBytes()==0,"unsupported no-CLUT TIM retains native rendering");
      NativeUiTextures.clear();
      System.out.println("PASS: actual UIHD JAR replaces56 atlas entries,13 PNGs,4 stitched/MCQ rasters and validates665 native variants across424 actual source families; reused chapter/credit slots replay lazily with zero GPU allocation; new/old save-card portraits share the source-bound selection.");
    }
  }
  private static void verifySavedPortraits(Class<?> type,Object mod,Map<RegistryId,Image> originals) throws Exception {
    final var packer=new TexturePacker("Private source saved portraits");
    final var ids=new ArrayList<RegistryId>();
    for(final String name:"dart lavitz shana rose haschel albert meru kongol miranda".split(" ")) {
      final var id=new RegistryId("lod:"+name);ids.add(id);packer.add(id,originals.get(id));
    }
    final byte[] bytes=packer.packToBytes(512,512);
    final var saved=new legend.game.saves.SeveredSavedGame(null,"test","private","private",new RegistryId("lod:campaign"),new legend.game.saves.ConfigCollection(),
      new FileData(PngWriter.compress(org.lwjgl.BufferUtils.createByteBuffer(bytes.length).put(0,bytes),512,512)),512,512);
    for(final var id:ids) {
      saved.characters.add(new legend.game.saves.SeveredSavedCharacterV2(id));
      final var rect=packer.getRect(id);saved.charPortraits.add(new legend.core.gpu.Rect4i(rect.x,rect.y,rect.w,rect.h));
    }
    final byte[] before=saved.atlas.getBytes().clone();
    final var selected=SavedPortraits.select(saved);
    require(selected.rectangles().stream().allMatch(r->r.w==192&&r.h==192),"every matching saved portrait restored");
    require(Arrays.equals(before,saved.atlas.getBytes())&&saved.charPortraits.stream().allMatch(r->r.w==48),"save data remains unchanged");
    require(packer.applyReplacements(),"new save packer uses same replacements");
    final var packed=packer.packGrowingToBytes(512,512,2048);
    require(packed.width()*packed.height()<=1024*1024&&ids.stream().allMatch(id->packer.getRect(id).w==192),"all nine new save portraits pack in bounded HD atlas");
  }

}
