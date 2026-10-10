package legend.definitive.artwork;

import legend.game.textures.Image;
import legend.game.textures.NativeUiTextures;
import legend.game.textures.UiTextureEvent;
import legend.game.textures.TexturePacker;
import org.junit.jupiter.api.Test;
import org.legendofdragoon.modloader.registries.RegistryId;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UiArtworkTest {
  private static Image original() { return new Image(new byte[] {40,60,80,(byte)255,0,0,0,0},2,1); }
  private static Image enlarged() {
    return new Image(new byte[] {50,70,90,(byte)255,50,70,90,(byte)255,0,0,0,0,0,0,0,0,
      50,70,90,(byte)255,50,70,90,(byte)255,0,0,0,0,0,0,0,0},4,2);
  }
  @Test void firstOwnerWinsAndStoredInputsAreIndependent() {
    final var source=original(); final var event=new UiTextureEvent(Path.of("gfx/ui/../ui/ui.png"),new byte[]{1,2},source);
    source.data[0]=0;assertEquals(40,event.original().data[0]);assertEquals(Path.of("gfx/ui/ui.png"),event.path);
    final var enhanced=enlarged();assertTrue(event.replace(original(),enhanced));enhanced.data[0]=0;
    assertEquals(50,event.image().data[0]);event.image().data[0]=0;assertEquals(50,event.image().data[0]);
    assertFalse(event.replace(original(),enlarged()));assertFalse(new UiTextureEvent(event.path,new byte[0],original()).replace(source,enlarged()));
  }
  @Test void rejectsChangedCoverageHiddenRgbAndMalformedDimensions() {
    final var coverage=enlarged();coverage.data[3]=0;assertThrows(IllegalArgumentException.class,()->UiTextureEvent.validate(original(),coverage));
    final var hidden=enlarged();hidden.data[8]=1;assertThrows(IllegalArgumentException.class,()->UiTextureEvent.validate(original(),hidden));
    assertThrows(IllegalArgumentException.class,()->UiTextureEvent.validate(new Image(new byte[0],0,1),enlarged()));
    assertThrows(IllegalArgumentException.class,()->UiTextureEvent.validate(new Image(new byte[1],2,1),enlarged()));
    assertThrows(IllegalArgumentException.class,()->UiTextureEvent.validate(original(),new Image(new byte[48],4,3)));
  }
  @Test void nativeVisibilityPreservesBothBlackClassesAndStp() {
    final var nativeSource=new Image(new byte[]{40,60,80,0,0,0,0,(byte)255},2,1);
    final var good=enlarged();for(int i=3;i<good.data.length;i+=4)good.data[i]=(byte)(i%16<8?0:255);
    assertDoesNotThrow(()->NativeUiTextures.validate(nativeSource,good));
    good.data[8]=1;assertThrows(IllegalArgumentException.class,()->NativeUiTextures.validate(nativeSource,good));
    good.data[8]=0;good.data[3]=(byte)255;assertThrows(IllegalArgumentException.class,()->NativeUiTextures.validate(nativeSource,good));
  }
  @Test void menuOverrideAndDialoguePartialTilesResolvePhysicalPixels() {
    // initGlyph uses 0x19, overriding metrics' original 0x200c page.
    final int pageX=(0x19&15)*64-512;
    final var panel=new NativeUiTextures.Binding(64*4+128,256+48,144,496,48,32);
    assertTrue(panel.contains(pageX,256,144,496,128,48,9,8));
    assertFalse(panel.contains(pageX,256,144,497,128,48,9,8));
    final var border=new NativeUiTextures.Binding(896*4,256,832,484,64,46);
    assertTrue(border.contains(896,256,832,484,48,32,16,16));
    assertFalse(border.contains(896,256,832,484,64,0,16,16));
  }
  @Test void atlasRetriesRemainReusableAndPayloadsStayInBounds() {
    final var packer=new TexturePacker("test");final var id=new RegistryId("test:icon");packer.add(id,enlarged());
    assertThrows(RuntimeException.class,()->packer.packToBytes(2,2));
    assertThrows(TexturePacker.AtlasCapacityException.class,()->packer.packGrowing(2,2,2));
    final var packed=packer.packToBytes(8,8);assertEquals(256,packed.length);
    final var rect=packer.getRect(id);assertEquals(4,rect.w);assertEquals(2,rect.h);
    assertEquals(50,packed[(rect.y*8+rect.x)*4]);
    assertThrows(IllegalArgumentException.class,()->packer.packToBytes(4096,4096));
  }
  @Test void sharedDecodeReusesPrewarmingAndSupportsIndexedPng() throws Exception {
    final byte[] png=java.nio.file.Files.readAllBytes(Path.of("gfx/ui/ui.png"));
    final var cache=legend.definitive.rendering.PngAssets.SHARED;
    assertTrue(cache.prewarm(() -> new java.io.ByteArrayInputStream(png)).get(5,java.util.concurrent.TimeUnit.SECONDS));
    final long before=cache.stats().hits();
    final Image decoded=legend.game.textures.UiTextures.decode(png);
    assertEquals(32,decoded.width);assertEquals(16,decoded.height);assertTrue(cache.stats().hits()>before);
    final var reference=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
    for(int y=0;y<16;y++)for(int x=0;x<32;x++) {
      final int rgba=reference.getRGB(x,y),offset=(y*32+x)*4;
      assertEquals((rgba>>>24)&255,decoded.data[offset+3]&255);
      assertEquals((rgba>>>16)&255,decoded.data[offset]&255);
    }
    assertThrows(java.io.IOException.class,()->legend.game.textures.UiTextures.decode(new byte[32]));
    final byte[] bad=png.clone();java.nio.ByteBuffer.wrap(bad).putInt(16,Integer.MAX_VALUE);
    assertThrows(java.io.IOException.class,()->legend.game.textures.UiTextures.decode(bad));
  }

  @Test void shippedCatalogMatchesHashesDimensionsAndExactCoverage() throws Exception {
    final Path base=Path.of("integrations/uihd/runtime-assets/uihd");
    final var catalog=new org.json.JSONObject(java.nio.file.Files.readString(base.resolve("catalog.json")));
    final var assets=catalog.getJSONArray("assets");assertEquals(738,assets.length());
    final var ids=new java.util.HashSet<String>();long enhancedBytes=0;final var counts=new java.util.HashMap<String,Integer>();
    for(int n=0;n<assets.length();n++) {
      final var entry=assets.getJSONObject(n);assertTrue(ids.add(entry.getString("id")));
      assertTrue(entry.getString("resource").matches("[a-z0-9_-]+\\.png"));
      final byte[] encoded=java.nio.file.Files.readAllBytes(base.resolve("assets").resolve(entry.getString("resource")));
      ArtworkResources.hash(encoded,entry.getString("outputSha256"));
      final var image=legend.game.textures.UiTextures.decode(encoded);final int scale=entry.getInt("scale");
      final var size=entry.getJSONArray("sourceSize");final int w=size.getInt(0),h=size.getInt(1);
      assertEquals(w*scale,image.width);assertEquals(h*scale,image.height);assertTrue(scale>=2&&scale<=4);
      assertEquals(entry.getLong("enhancedRgbaBytes"),image.data.length);enhancedBytes+=image.data.length;
      final String kind=entry.getString("kind");counts.merge(kind,1,Integer::sum);
      final boolean nativeImage=kind.equals("native")||kind.equals("raster");
      final byte[] classes=new byte[w*h*(nativeImage?2:1)];
      for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
        final int i=(y*scale*image.width+x*scale)*4,out=(y*w+x)*(nativeImage?2:1);
        classes[out]=image.data[i+3];final boolean black=image.data[i]==0&&image.data[i+1]==0&&image.data[i+2]==0;
        if(nativeImage)classes[out+1]=(byte)(black?1:0);
        for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++) {
          final int j=((y*scale+dy)*image.width+x*scale+dx)*4;
          assertEquals(image.data[i+3],image.data[j+3]);
          if(nativeImage)assertEquals(black,image.data[j]==0&&image.data[j+1]==0&&image.data[j+2]==0);
          else if(image.data[j+3]==0)assertTrue(image.data[j]==0&&image.data[j+1]==0&&image.data[j+2]==0);
        }
      }
      ArtworkResources.hash(classes,entry.getString("sourceCoverageSha256"));
      if(nativeImage)assertTrue(image.data.length+(long)w*h*4<=NativeUiTextures.BUDGET);
      else if(entry.getString("sourcePath").startsWith("gfx/")) {
        final byte[] source=java.nio.file.Files.readAllBytes(Path.of(entry.getString("sourcePath")));
        ArtworkResources.hash(source,entry.getString("sourceSha256"));
        final var original=legend.game.textures.UiTextures.decode(source);UiTextureEvent.validate(original,image);
        final var boxes=entry.optJSONArray("protectedSourceRegions");
        if(boxes!=null)for(int b=0;b<boxes.length();b++) {
          final var box=boxes.getJSONArray(b);
          for(int y=box.getInt(1);y<box.getInt(3);y++)for(int x=box.getInt(0);x<box.getInt(2);x++)
            for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++)for(int c=0;c<4;c++) {
              if(c==3 || original.data[(y*w+x)*4+3]!=0) assertEquals(original.data[(y*w+x)*4+c],image.data[((y*scale+dy)*image.width+x*scale+dx)*4+c]);
            }
        }
      }
    }
    assertEquals(catalog.getLong("enhancedRgbaBytes"),enhancedBytes);
    assertEquals(319_027_968L,enhancedBytes);
    assertEquals(java.util.Map.of("atlas",30,"png",13,"atlas-native",26,"native",665,"raster",4),counts);
    assertEquals(9,ids.stream().filter(id->id.startsWith("portrait-")).count());
    assertEquals(96,ids.stream().filter(id->id.startsWith("battle_hud_")).count());
    assertEquals(48,ids.stream().filter(id->id.startsWith("basic_")).count());
    final var expectedFamilies=new java.util.HashSet<String>(java.util.Set.of("menu","items","menu_characters","menu_character_extras","dialogue","dialogue_arrow","world_map","indicator_big_arrow","indicator_small_arrow","indicator_alert","the_end"));
    for(int i=0;i<6;i++)expectedFamilies.add("battle_hud_"+i);
    for(int i=0;i<3;i++)expectedFamilies.add("basic_"+i);
    for(int chapter=0;chapter<4;chapter++)for(int frame=0;frame<14;frame++)if(frame<6||frame>=8)expectedFamilies.add("chapter_"+chapter+"_"+frame);
    for(int credit=0;credit<=356;credit++)if(credit!=50)expectedFamilies.add("credit_"+credit);
    final var actualFamilies=new java.util.HashSet<String>();
    for(int i=0;i<assets.length();i++)if(assets.getJSONObject(i).getString("kind").equals("native"))actualFamilies.add(assets.getJSONObject(i).getString("family"));
    assertEquals(expectedFamilies,actualFamilies);
    assertTrue(ids.containsAll(java.util.Set.of("game_over","title_background","title_trademark","title_copyright","loading_eye")));
    assertEquals(96,ids.stream().filter(id->id.startsWith("chapter_")).count());
    assertEquals(356,ids.stream().filter(id->id.startsWith("credit_")).count());
    assertEquals(16,ids.stream().filter(id->id.startsWith("world_map-")).count());
  }

  @Test void privateNativeSourcesSurviveOwnershipChangesAndRemainBounded() throws Exception {
    final var sources=new legend.game.textures.NativeUiSources();
    final byte[] bytes=new byte[32];bytes[0]=16;
    final var first=new legend.game.textures.NativeUiTextureEvent("menu",new legend.game.tim.Tim(new legend.game.unpacker.FileData(bytes)),64,256,144,496);
    sources.remember(first);bytes[0]=0;first.stopPropagation();
    final var restored=new java.util.ArrayList<legend.game.textures.NativeUiTextureEvent>();
    sources.replay(restored::add);assertEquals(1,restored.size());assertEquals(16,restored.getFirst().source()[0]);
    final var propagation=org.legendofdragoon.modloader.events.Event.class.getDeclaredMethod("shouldPropagate");propagation.setAccessible(true);
    assertEquals(false,propagation.invoke(first));assertEquals(true,propagation.invoke(restored.getFirst()));
    restored.getFirst().stopPropagation();restored.clear();sources.replay(restored::add);
    assertEquals(1,restored.size());assertEquals(true,propagation.invoke(restored.getFirst()));
    restored.getFirst().source()[0]=0;assertEquals(16,first.source()[0]);
    for(int i=0;i<10;i++)sources.remember(new legend.game.textures.NativeUiTextureEvent("family"+i,new legend.game.tim.Tim(new legend.game.unpacker.FileData(new byte[300_000])),0,0,0,0));
    restored.clear();sources.replay(restored::add);assertTrue(restored.size()<=8);assertTrue(sources.retainedBytes()<=2L*1024*1024);
    sources.remember(new legend.game.textures.NativeUiTextureEvent("oversize",new legend.game.tim.Tim(new legend.game.unpacker.FileData(new byte[2*1024*1024+1])),0,0,0,0));
    final long before=sources.retainedBytes();sources.replay(event -> sources.remember(event));assertEquals(before,sources.retainedBytes());
  }

  @Test void deferredSelectionsStayLazyAndFirstOwnerWins() {
    NativeUiTextures.clear();
    final var calls=new java.util.concurrent.atomic.AtomicInteger();
    final var binding=new NativeUiTextures.Binding(0,0,16,32,2,1);
    assertTrue(NativeUiTextures.registerDeferred(binding,40,()->{calls.incrementAndGet();return new NativeUiTextures.Images(original(),enlarged());}));
    assertFalse(NativeUiTextures.registerDeferred(binding,40,()->{throw new AssertionError("duplicate");}));
    assertEquals(1,NativeUiTextures.selectionCount());assertEquals(0,NativeUiTextures.residentCount());
    assertEquals(0,NativeUiTextures.allocatedBytes());assertEquals(0,calls.get());
    assertFalse(NativeUiTextures.registerDeferred(binding,NativeUiTextures.BUDGET+1,()->null));
    NativeUiTextures.clear();
  }
  @Test void allDefaultFamiliesReplayWithTheirPhysicalPaletteRows() {
    final var sources=new legend.game.textures.NativeUiSources();
    final byte[] bytes=new byte[32];bytes[0]=16;
    for(int i=0;i<65;i++)sources.remember(new legend.game.textures.NativeUiTextureEvent("small"+i,new legend.game.tim.Tim(new legend.game.unpacker.FileData(bytes)),0,0,0,0,4));
    final var replay=new java.util.ArrayList<legend.game.textures.NativeUiTextureEvent>();sources.replay(replay::add);
    assertEquals(64,replay.size());assertEquals("small1",replay.getFirst().id);
    assertTrue(replay.stream().allMatch(e->e.clutRows==4));assertEquals(64*32,sources.retainedBytes());
  }
  @Test void savedPortraitPackingFallsBackAtomicallyOnOptionalFailures() {
    final var id=new RegistryId("test:portrait");
    final java.util.List<java.util.function.Consumer<legend.game.textures.ReplaceAtlasTexturesEvent>> failures=java.util.List.of(
      event->{event.get(id).data[0]=99;throw new IllegalStateException("optional artwork listener failed");},
      event->event.replace(id,original(),new Image(new byte[0],0,1)),
      event->event.replace(id,original(),new Image(new byte[2049*4],2049,1)));
    for(final var selection:failures) {
      final var packer=new TexturePacker("Save reliability");packer.add(id,original());
      final var packed=packer.packWithReplacements(8,8,8,selection);
      final var rect=packer.getRect(id);
      assertEquals(2,rect.w);assertEquals(1,rect.h);
      assertEquals(40,packed.data()[(rect.y*packed.width()+rect.x)*4]);
    }
    final var packer=new TexturePacker("Selected save portrait");packer.add(id,original());
    final var selected=packer.packWithReplacements(2,2,8,event->event.replace(id,original(),enlarged()));
    assertEquals(4,packer.getRect(id).w);assertEquals(50,selected.data()[(packer.getRect(id).y*selected.width()+packer.getRect(id).x)*4]);
  }

  @Test void directRastersKeepStpAndFirstOwnerAndSourcesReplayCurrentSlots() {
    final var source=new Image(new byte[]{40,60,80,0,0,0,0,(byte)255},2,1);
    final var target=enlarged();for(int i=3;i<target.data.length;i+=4)target.data[i]=(byte)(i%16<8?0:255);
    final var event=new legend.game.textures.UiRasterEvent("title",new byte[]{1},source);
    assertTrue(event.replace(source,target));assertFalse(event.replace(source,target));
    source.data[0]=0;target.data[0]=0;assertEquals(40,event.original().data[0]);assertEquals(50,event.image().data[0]);
    final var sources=new legend.game.textures.NativeUiSources();final byte[] tim=new byte[32];tim[0]=16;
    sources.remember(new legend.game.textures.NativeUiTextureEvent("chapter_0_0","chapter_name",new legend.game.tim.Tim(new legend.game.unpacker.FileData(tim)),0,0,0,0,2));
    sources.remember(new legend.game.textures.NativeUiTextureEvent("chapter_0_1","chapter_name",new legend.game.tim.Tim(new legend.game.unpacker.FileData(tim)),0,0,0,0,2));
    final var replay=new java.util.ArrayList<legend.game.textures.NativeUiTextureEvent>();sources.replay(replay::add);
    assertEquals(1,replay.size());assertEquals("chapter_0_1",replay.getFirst().id);assertEquals("chapter_name",replay.getFirst().slot);
    final var owner=new Object();final var binding=new NativeUiTextures.Binding(0,0,16,32,2,1);
    NativeUiTextures.clear();NativeUiTextures.registerDeferred(owner,"chapter_name",binding,40,()->new NativeUiTextures.Images(original(),enlarged()));
    NativeUiTextures.beginSelection(new Object(),"chapter_name");assertEquals(1,NativeUiTextures.selectionCount());
    NativeUiTextures.beginSelection(owner,"chapter_name");assertEquals(0,NativeUiTextures.selectionCount());assertEquals(0,NativeUiTextures.allocatedBytes());
  }

}
