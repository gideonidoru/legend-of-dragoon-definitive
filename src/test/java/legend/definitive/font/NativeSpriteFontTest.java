package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.combat.ui.*;
import legend.game.textures.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeSpriteFontTest {
  public static final class ResourceOwner { }
  static Class<?> resources(final java.util.Map<String,byte[]> files, final int[] pngReads) throws Exception {
    final String name=ResourceOwner.class.getName();final byte[] code;
    try(var input=ResourceOwner.class.getResourceAsStream("NativeSpriteFontTest$ResourceOwner.class")){code=input.readAllBytes();}
    final class Loader extends ClassLoader {
      Loader(){super(NativeSpriteFontTest.class.getClassLoader());}
      Class<?> owner(){return defineClass(name,code,0,code.length);}
      @Override public java.io.InputStream getResourceAsStream(final String path){
        if(path.endsWith(".png"))pngReads[0]++;
        final byte[] bytes=files.get(path);return bytes==null?null:new java.io.ByteArrayInputStream(bytes);
      }
    }
    return new Loader().owner();
  }
  private Object previousApi;
  private java.lang.reflect.Field api;
  @BeforeEach void setup() throws Exception {
    if(!legend.game.modding.coremod.CoreMod.SHADER_ENABLE_CRT_CONFIG.isValid()) legend.game.modding.coremod.CoreMod.registerConfig(new legend.game.saves.ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    api = RenderEngine.class.getDeclaredField("api"); api.setAccessible(true); previousApi = api.get(GameEngine.RENDERER); api.set(GameEngine.RENDERER, new NoopApi());
    NativeSpriteFont.clear(); reset();
  }
  @AfterEach void cleanup() throws Exception { NativeSpriteFont.clear(); reset(); api.set(GameEngine.RENDERER, previousApi); }
  private static void reset() throws Exception { final var method = RenderEngine.class.getDeclaredMethod("resetBatches"); method.setAccessible(true); method.invoke(GameEngine.RENDERER); }
  private static Image image(final int w, final int h, final int index, final int alpha) {
    final byte[] bytes = new byte[w * h * 4];
    for(int i = 0; i < bytes.length; i += 4) { bytes[i] = (byte)index; bytes[i + 3] = (byte)alpha; }
    return new Image(bytes, w, h);
  }
  private static NativeUiQuad source(final int u) { return new NativeUiQuad(704, 256, 896, 304, u, 32, 8, 8); }
  @Test void retirementCallbackCompletesWithoutTakingRegistryMonitor() throws Exception {
    NativeSpriteFont.register(this,"retirement",source(0),image(8,8,1,255),image(16,16,1,255));
    assertNotNull(NativeSpriteFont.select(source(0)));
    NativeSpriteFont.clear();
    final var completed=new java.util.concurrent.CountDownLatch(1);
    final var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
    final var worker=new Thread(()->{try{reset();}catch(Throwable problem){failure.set(problem);}finally{completed.countDown();}},"font-retirement-control");
    boolean independent;
    synchronized(NativeSpriteFont.class) {
      worker.start();
      independent=completed.await(1,java.util.concurrent.TimeUnit.SECONDS);
    }
    worker.join(2000);
    assertFalse(worker.isAlive(),"Control must release its monitor even when the baseline callback blocks");
    assertNull(failure.get());
    assertTrue(independent,"Renderer retirement must finish while another thread owns the sprite registry monitor");
    assertEquals(0,NativeSpriteFont.allocatedBytes());
  }
  private static byte[] timSource(final int paletteWidth) {
    return timSource(paletteWidth,1);
  }
  private static byte[] timSource(final int paletteWidth,final int paletteHeight) {
    final var encoded=java.nio.ByteBuffer.allocate(8+12+paletteWidth*paletteHeight*2+12+32).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    encoded.putInt(16).putInt(8).putInt(12+paletteWidth*paletteHeight*2).putShort((short)0).putShort((short)0).putShort((short)paletteWidth).putShort((short)paletteHeight);
    for(int i=0;i<paletteWidth*paletteHeight;i++)encoded.putShort((short)(i%16==1?0x7fff:0));
    encoded.putInt(44).putShort((short)0).putShort((short)0).putShort((short)2).putShort((short)8);
    while(encoded.hasRemaining())encoded.put((byte)0x11);
    return encoded.array();
  }
  @Test void exactSourceAndPaletteAreRequiredAndDuplicateOwnerDoesNotStealSelection() {
    final Object owner = new Object();
    assertTrue(NativeSpriteFont.register(owner, "hud", source(32), image(8,8,1,255), image(64,64,1,200)));
    assertFalse(NativeSpriteFont.register(new Object(), "other", source(32), image(8,8,1,255), image(64,64,1,200)));
    assertNull(NativeSpriteFont.select(source(33)));
    assertNull(NativeSpriteFont.select(new NativeUiQuad(704,256,896,312,32,32,8,8)));
    final var selected = NativeSpriteFont.select(source(32)); assertNotNull(selected);
    NativeSpriteFont.beginSelection(new Object(), "hud"); assertTrue(selected.current());
    NativeSpriteFont.beginSelection(owner, "hud"); assertFalse(selected.current()); assertEquals(0,NativeSpriteFont.retainedBytes());
  }
  @Test void malformedEncodingBlankCoverageAndNonuniformScaleFailBeforeSelection() {
    final Object owner = new Object();
    assertThrows(IllegalArgumentException.class, () -> NativeSpriteFont.register(owner,"hud",source(32),image(8,8,16,255),image(64,64,1,200)));
    assertThrows(IllegalArgumentException.class, () -> NativeSpriteFont.register(owner,"hud",source(32),image(8,8,1,255),image(64,64,1,0)));
    assertThrows(IllegalArgumentException.class, () -> NativeSpriteFont.register(owner,"hud",source(32),image(8,8,1,255),image(64,32,1,200)));
    assertEquals(0,NativeSpriteFont.selectionCount());
  }
  @Test void deferredDecodeAndGpuRetirementAreBoundedAndOldTokensCannotResurrect() throws Exception {
    final int[] decoded={0};
    assertTrue(NativeSpriteFont.registerDeferred(this,"hud",source(32),image(8,8,1,255),64,64,()->{decoded[0]++;return image(64,64,1,200);}));
    assertEquals(0,decoded[0]);
    final var selected=NativeSpriteFont.select(source(32)); assertNotNull(selected); assertEquals(1,decoded[0]);
    assertNotNull(NativeSpriteFont.select(source(32)));assertEquals(1,decoded[0]);
    final long allocated=NativeSpriteFont.allocatedBytes();assertTrue(allocated>0);
    NativeSpriteFont.clear();assertFalse(selected.current());assertEquals(allocated,NativeSpriteFont.allocatedBytes(),"Retained queues still own GPU budget");
    reset();assertEquals(0,NativeSpriteFont.allocatedBytes());
    assertTrue(NativeSpriteFont.registerDeferred(this,"broken",source(32),image(8,8,1,255),64,64,()->image(64,64,16,255)));
    assertNull(NativeSpriteFont.select(source(32)));assertEquals(0,NativeSpriteFont.allocatedBytes());
  }
  @Test void engineNativeRgbCanBeSupersededButIndependentTextureOverridesCannot() throws Exception {
    assertTrue(NativeSpriteFont.register(this,"hud",source(32),image(8,8,1,255),image(64,64,1,200)));
    final var obj=new QuadBuilder("Digit source priority").size(8,8).uv(32,32).bpp(legend.core.gpu.Bpp.BITS_4).vramPos(704,256).clut(896,304).build();
    final var art=UiTextures.upload("Existing artwork owner",image(16,16,1,255)); final var reference=UiTextures.upload("Existing source owner",image(8,8,1,255));
    final var owned=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class).nativeUiArtwork(art,reference,32,32,8,8);
    assertTrue(owned.hasTranslucency(),"Engine-owned full-page RGB may yield to exact glyph coverage");
    final var custom=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class).uiArtwork(art,reference,32,32,8,8);
    assertFalse(custom.hasTranslucency(),"Independent artwork selection retains priority");
    final var raw=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class).texture(art,2);
    assertFalse(raw.hasTranslucency(),"Independent texture-slot override retains priority");
    final var later=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class);
    assertTrue(later.hasTranslucency());later.texture(art,2);
    assertFalse(later.hasTranslucency(),"Independent override after an earlier pass query also retains priority");
    final var textures=QueuedModel.class.getDeclaredField("textures");textures.setAccessible(true);
    assertSame(art,((Texture[])textures.get(later))[2]);
    final var mainOverride=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class);
    assertTrue(mainOverride.hasTranslucency());mainOverride.texture(art,0);
    assertFalse(mainOverride.hasTranslucency());assertNull(((Texture[])textures.get(mainOverride))[2]);
    final var offset=GameEngine.RENDERER.queueUiOrthoModel(obj,new legend.core.gte.MV(),QueuedModelStandard.class).uvOffset(1,0);
    assertFalse(offset.hasTranslucency(),"Different native glyph geometry cannot borrow coverage");
    obj.delete();art.delete();reference.delete();
  }
  @Test void gpuLruBudgetIncludesPendingRetirementAndProtectsThisFrame() throws Exception {
    final NativeUiQuad[] crops=new NativeUiQuad[4];
    for(int i=0;i<crops.length;i++) {
      crops[i]=new NativeUiQuad(704,256,896,304,i*64,0,64,64);
      assertTrue(NativeSpriteFont.registerDeferred(this,"large",crops[i],image(64,64,1,255),1024,1024,()->image(1024,1024,1,255)));
    }
    final var first=NativeSpriteFont.select(crops[0]);
    final var oldest=NativeSpriteFont.select(crops[1]);
    assertNotNull(first);assertNotNull(oldest);assertNotNull(NativeSpriteFont.select(crops[2]));
    assertNull(NativeSpriteFont.select(crops[3]),"Current-frame textures cannot be evicted");
    final long before=NativeSpriteFont.allocatedBytes();assertTrue(before<=16L*1024*1024);
    NativeSpriteFont.beginFrame();assertTrue(first.current());
    assertNull(NativeSpriteFont.select(crops[3]),"Evicted retained texture still owns its budget");
    assertTrue(first.current());assertFalse(oldest.current());assertEquals(before,NativeSpriteFont.allocatedBytes());
    reset();assertNotNull(NativeSpriteFont.select(crops[3]));
    assertTrue(NativeSpriteFont.allocatedBytes()<=16L*1024*1024);
    assertFalse(oldest.current(),"Uploading another glyph cannot resurrect the old draw token");
  }
  @Test void widestActualCreditCardGeometryFitsAndOutsideVramOrImpossibleAllocationDoesNot() {
    final var widest=new NativeUiQuad(512,0,896,0,0,0,360,17);
    assertTrue(NativeSpriteFont.registerDeferred(this,"credit_slot",widest,image(360,17,1,255),1440,68,()->image(1440,68,1,255)));
    assertNotNull(NativeSpriteFont.select(widest),"Actual maximum credit width360 supports4x1440 artwork without a UV256 wrap");
    assertThrows(IllegalArgumentException.class,()->NativeSpriteFont.registerDeferred(this,"outside-x",new NativeUiQuad(960,0,896,0,0,0,360,17),image(360,17,1,255),1440,68,()->image(1440,68,1,255)));
    assertThrows(IllegalArgumentException.class,()->NativeSpriteFont.registerDeferred(this,"outside-y",new NativeUiQuad(512,256,896,0,0,500,360,17),image(360,17,1,255),1440,68,()->image(1440,68,1,255)));
    assertFalse(NativeSpriteFont.registerDeferred(this,"impossible",new NativeUiQuad(512,0,896,0,0,32,128,128),image(128,128,1,255),2048,2048,()->{fail("Oversized GPU allocation must not decode");return null;}));
  }
  @Test void packagedResourcesAreLazySourceBoundAndMalformedArtworkFallsBack() throws Exception {
    final byte[] source=timSource(16);final var tim=new legend.game.tim.Tim(new legend.game.unpacker.FileData(source));
    final var raster=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<16;y++)for(int x=0;x<16;x++)raster.setRGB(x,y,0xc8010000);
    final var output=new java.io.ByteArrayOutputStream();assertTrue(javax.imageio.ImageIO.write(raster,"png",output));final byte[] png=output.toByteArray();
    final var entry=new org.json.JSONObject().put("id","digit").put("family","fixture").put("crop",new int[]{0,0,8,8}).put("palette",0).put("resource","digit.png")
      .put("sourceSha256",legend.definitive.textures.TexturePilot.sha256(source)).put("outputSha256",legend.definitive.textures.TexturePilot.sha256(png)).put("outputSize",new int[]{16,16});
    final var catalog=new org.json.JSONObject().put("format",1).put("sprites",new org.json.JSONArray().put(entry));
    final var files=new java.util.HashMap<String,byte[]>();files.put("uihd/font/native-sprites.json",catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));files.put("uihd/font/digit.png",png);
    final int[] reads={0};final var assets=NativeSpriteFontAssets.load(resources(files,reads));
    final var event=new NativeUiTextureEvent("fixture",tim,704,256,896,304);assets.select(this,event);
    assertEquals(0,reads[0]);final var crop=new NativeUiQuad(704,256,896,304,0,0,8,8);
    assertNotNull(NativeSpriteFont.select(crop));assertEquals(1,reads[0]);
    final byte[] changed=source.clone();changed[changed.length-1]=0x22;
    assets.select(this,new NativeUiTextureEvent("fixture",new legend.game.tim.Tim(new legend.game.unpacker.FileData(changed)),704,256,896,304));
    assertNull(NativeSpriteFont.select(crop),"Re-uploading another source clears obsolete coverage");reset();
    files.put("uihd/font/digit.png",new byte[33]);assets.select(this,event);assertNull(NativeSpriteFont.select(crop),"Wrong resource hash falls back");
    entry.put("outputSha256",legend.definitive.textures.TexturePilot.sha256(png)).put("outputSize",new int[]{32,32});
    files.put("uihd/font/native-sprites.json",catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));files.put("uihd/font/digit.png",png);
    NativeSpriteFontAssets.load(resources(files,reads)).select(this,event);assertNull(NativeSpriteFont.select(crop),"Matching hash with larger declared size fails before decoding");
    entry.put("crop",new double[]{0.5,0,8,8});files.put("uihd/font/native-sprites.json",catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThrows(java.io.IOException.class,()->NativeSpriteFontAssets.load(resources(files,reads)));
    NativeSpriteFont.clear();reset();
    final byte[] horizontal=timSource(32);
    entry.put("crop",new int[]{0,0,8,8}).put("outputSize",new int[]{16,16}).put("palette",1).put("sourceSha256",legend.definitive.textures.TexturePilot.sha256(horizontal));
    files.put("uihd/font/native-sprites.json",catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    final var horizontalAssets=NativeSpriteFontAssets.load(resources(files,reads));
    horizontalAssets.select(this,new NativeUiTextureEvent("fixture",new legend.game.tim.Tim(new legend.game.unpacker.FileData(horizontal)),832,424,832,422));
    assertNotNull(NativeSpriteFont.select(new NativeUiQuad(832,256,848,422,0,168,8,8)),"32x1 bank1 uses the horizontal CLUT and forced system-font upload offset");
    assertNull(NativeSpriteFont.select(new NativeUiQuad(832,256,832,423,0,168,8,8)),"Horizontal bank cannot select the following row");
    NativeSpriteFont.clear();reset();
    final byte[] basic=timSource(16,16);
    entry.put("palette",4).put("sourceSha256",legend.definitive.textures.TexturePilot.sha256(basic));
    final var alias=new org.json.JSONObject(entry.toString()).put("id","digit-basic-5").put("palette",5);
    catalog.put("sprites",new org.json.JSONArray().put(entry).put(alias));files.put("uihd/font/native-sprites.json",catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    NativeSpriteFontAssets.load(resources(files,reads)).select(this,new NativeUiTextureEvent("fixture","basic",new legend.game.tim.Tim(new legend.game.unpacker.FileData(basic)),832,256,832,480,4));
    assertNotNull(NativeSpriteFont.select(new NativeUiQuad(832,256,848,480,0,0,8,8)),"Basic palette4 is column1/row0 of the actual four-row upload layout");
    assertNotNull(NativeSpriteFont.select(new NativeUiQuad(832,256,848,481,0,0,8,8)),"Basic palette5 is column1/row1");
    assertNull(NativeSpriteFont.select(new NativeUiQuad(832,256,832,484,0,0,8,8)),"Normalized TIM header cannot replace physical upload rows");
  }
  @Test void actualHud30NumbersUseProductionQueueAndResolveBeforeOpaqueDecision() throws Exception {
    final var hud = new BattleHud(null); final var stats = new BattleDisplayStats144();
    final var values = BattleHud.class.getDeclaredField("displayStats_800c6c2c"); values.setAccessible(true);
    @SuppressWarnings("unchecked") final var list = (List<BattleDisplayStats144>)values.get(hud); list.add(stats);
    final var fade = BattleHud.class.getDeclaredField("hudFade"); fade.setAccessible(true); fade.set(hud,6);
    hud.renderNumber(0,0,30,1); hud.renderNumber(0,1,30,1);
    assertEquals(3,stats.digits_04[0][0].digitValue_00); assertEquals(0,stats.digits_04[0][1].digitValue_00);
    assertEquals(10,stats.digits_04[0][0].x_02); assertEquals(15,stats.digits_04[0][1].x_02);
    assertEquals(27,stats.digits_04[1][0].x_02); assertEquals(32,stats.digits_04[1][1].x_02);
    for(final int u : new int[]{32,88}) assertTrue(NativeSpriteFont.register(this,"hud",source(u),image(8,8,1,255),image(64,64,1,200)));
    final var queue = BattleHud.class.getDeclaredMethod("queueStatDigit",BattleDisplayStats144.class,BattleDisplayStatsDigit10.class,int.class); queue.setAccessible(true);
    for(final int number : new int[]{0,1}) for(int digit = 0; digit < 2; digit++) {
      final var model = (QueuedModelStandard)queue.invoke(hud,stats,stats.digits_04[number][digit],2);
      assertTrue(model.shouldRender(null,0),"Authored fully covered opaque interiors retain native depth ordering; shader defers only coverage edges");
      assertTrue(model.hasTranslucency()); assertTrue(model.shouldRender(Translucency.HALF_B_PLUS_HALF_F,0));
      model.translucency(Translucency.B_PLUS_F);
      assertTrue(model.shouldRender(Translucency.HALF_B_PLUS_HALF_F,0)); assertTrue(model.shouldRender(Translucency.B_PLUS_F,0));
      NativeSpriteFont.clear(); assertFalse(model.shouldRender(null,0));
      model.translucency(null); assertTrue(model.shouldRender(null,0),"Disable restores native opaque path");
      for(final int u : new int[]{32,88}) assertTrue(NativeSpriteFont.register(this,"hud",source(u),image(8,8,1,255),image(64,64,1,200)));
    }
    final var obj = BattleHud.class.getDeclaredField("floatingTextType1Digits"); obj.setAccessible(true); ((Obj)obj.get(hud)).delete();
  }
}
