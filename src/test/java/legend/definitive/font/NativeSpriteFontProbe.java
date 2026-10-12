package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.gte.MV;
import legend.core.platform.*;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.definitive.rendering.NativeRendererProbe;
import legend.game.combat.ui.*;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.textures.*;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.json.JSONObject;
import org.lwjgl.opengl.GL;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Private windowless actual HUD queue proof. Synthetic coverage is a test control, never shipped artwork. */
public final class NativeSpriteFontProbe {
  private static final int[] US = {88,16,24,32,40,48,56,64,72,80};
  private static Obj slash;
  private static boolean clipDigits;
  private static Texture emissionOutput;
  private static byte[] lastEmission;
  private static final class Api extends GlApi { int output; @Override public void unbindFramebuffer() { glBindFramebuffer(GL_FRAMEBUFFER,this.output); } }
  private static final class OffscreenWindow extends NoopWindow {
    final Api api; OffscreenWindow(final Api api) { super(new NoopPlatformManager(),320,240); this.api=api; }
    @Override public RenderApi getRenderApi() { return this.api; }
  }
  private static Object field(final Object owner, final Class<?> type, final String name) throws Exception { var f=type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
  private static void require(final boolean condition, final String message) { if(!condition) throw new AssertionError(message); }
  private static void reset() throws Exception { var reset=RenderEngine.class.getDeclaredMethod("resetBatches"); reset.setAccessible(true); reset.invoke(GameEngine.RENDERER); }
  private static String hash(final byte[] bytes) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
  private static Image reference(final Tim image, final int u) {
    byte[] source=image.getImageData().getBytes(), bytes=new byte[8*8*4]; int stride=image.getImageRect().w*2;
    for(int y=0;y<8;y++) for(int x=0;x<8;x++) { int p=(y*8+x)*4; bytes[p]=(byte)((source[(32+y)*stride+(u+x)/2]>>>((u+x&1)*4))&15); bytes[p+3]=(byte)255; }
    return new Image(bytes,8,8);
  }
  /** Actual source/upload layouts, synthetic nearest-index art: address proof, not redraw acceptance. */
  private static void paletteLayoutControls(final Path hudSource,final Path proof,final RenderBatch batch,final Api api,final FrameBuffer target,final Texture output,final JSONObject report) throws Exception {
    final Path files=hudSource.getParent().getParent().getParent();
    final byte[] basicPage=Files.readAllBytes(files.resolve("SECT/DRGN0.BIN/6669/0"));
    final String[] pins={"92a1a2b12a561c30575de1935783eedc1498190c12d2c142fc21673909185636","5e90b8370c4d09089f735238c4330f35f8a6bb14b485c68c4ba62c2db8e6ea26","332df3c771e88c120aa51f6dd7b5b30fb2a270199f69ef789f2d28ea22cf2415","612ac85cccad6659f752344a0ffde08456cfe9531f6b8fbd459642ec71dc8a84"};
    require(hash(basicPage).equals(pins[0]),"Actual shared basic source page pin");
    final var receipts=new org.json.JSONArray();
    for(int bank=0;bank<4;bank++) {
      final byte[] paletteSource=Files.readAllBytes(bank<3?files.resolve("SECT/DRGN0.BIN/6669/"+bank):files.resolve("font.tim"));
      require(hash(paletteSource).equals(pins[bank]),"Actual palette/system source pin "+bank);
      final Tim paletteTim=new Tim(new FileData(paletteSource));
      final Tim source=bank<3?NativeUiTextureEvent.withPalette(new Tim(new FileData(basicPage)),paletteTim):paletteTim;
      final int imageX=832,imageY=bank<3?256:424,clutX=bank<3?832+bank*64:832,clutY=bank<3?480:422,rows=bank<3?4:1;
      final var event=new NativeUiTextureEvent(bank<3?"basic_"+bank:"font_system",source,imageX,imageY,clutX,clutY,rows);
      final int[] memory=new int[1024*512];final var rect=source.getImageRect();final byte[] pixels=source.getImageData().getBytes(),clut=paletteTim.getClutData().getBytes();
      for(int y=0;y<rect.h;y++)for(int x=0;x<rect.w;x++)memory[(imageY+y)*1024+imageX+x]=(pixels[(y*rect.w+x)*2]&255)|(pixels[(y*rect.w+x)*2+1]&255)<<8;
      for(int p=0;p<clut.length/32;p++)for(int x=0;x<16;x++)memory[(clutY+p%rows)*1024+clutX+p/rows*16+x]=(clut[(p*16+x)*2]&255)|(clut[(p*16+x)*2+1]&255)<<8;
      GameEngine.GPU.vramTexture15.data(0,0,1024,512,TextureDataType.UINT,memory);
      for(int palette:bank<3?new int[]{4,5}:new int[]{1}) {
        // Binary basic glyphs use index1, which alias5 intentionally makes invisible.
        // A labeled live-CLUT edit gives this address control a decisive visible oracle.
        final int faceAddress=(clutY+palette%rows)*1024+clutX+palette/rows*16+1;
        final int originalFace=memory[faceAddress];
        if(bank<3&&palette==5){memory[faceAddress]=0x03e0;GameEngine.GPU.vramTexture15.data(0,0,1024,512,TextureDataType.UINT,memory);}
        int u=-1,v=-1;
        search:for(int y=0;y+8<=rect.h;y+=8)for(int x=0;x+8<=rect.w*4;x+=8)for(int yy=0;yy<8;yy++)for(int xx=0;xx<8;xx++) {
          int index=(pixels[(y+yy)*rect.w*2+(x+xx)/2]>>>((x+xx&1)*4))&15;
          if(memory[(clutY+palette%rows)*1024+clutX+palette/rows*16+index]!=0){u=x;v=y;break search;}
        }
        require(u>=0,"Actual palette has visible source cells");
        final byte[] art=new byte[16*16*4];for(int y=0;y<16;y++)for(int x=0;x<16;x++){int p=(y*16+x)*4;art[p]=(byte)((pixels[(v+y/2)*rect.w*2+(u+x/2)/2]>>>((u+x/2&1)*4))&15);art[p+3]=(byte)255;}
        final var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xff000000|(art[(y*16+x)*4]&255)<<16);
        final var png=new java.io.ByteArrayOutputStream();require(ImageIO.write(image,"png",png),"Synthetic layout PNG writer");
        final var entry=new JSONObject().put("id","layout").put("family",event.id).put("crop",new int[]{u,v,8,8}).put("palette",palette).put("resource","layout.png").put("sourceSha256",hash(event.source())).put("outputSha256",hash(png.toByteArray())).put("outputSize",new int[]{16,16});
        final var resources=new HashMap<String,byte[]>();resources.put("uihd/font/native-sprites.json",new JSONObject().put("format",1).put("sprites",new org.json.JSONArray().put(entry)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));resources.put("uihd/font/layout.png",png.toByteArray());
        final var assets=NativeSpriteFontAssets.load(NativeSpriteFontTest.resources(resources,new int[1]));
        int pageX=(imageX+u/4)/64*64,pageY=(imageY+v)/256*256;final var crop=new NativeUiQuad(pageX,pageY,clutX+palette/rows*16,clutY+palette%rows,imageX*4+u-pageX*4,imageY+v-pageY,8,8);
        final var obj=new QuadBuilder("Actual palette-layout control").size(8,8).uv(crop.u(),crop.v()).bpp(legend.core.gpu.Bpp.BITS_4).vramPos(pageX,pageY).clut(crop.clutX(),crop.clutY()).build();
        final byte[][] colour=new byte[2][],emission=new byte[2][];batch.setProjectionSize(160,120);
        try {for(int mode=0;mode<2;mode++) {
          reset();NativeSpriteFont.clear();if(mode==1){assets.select(NativeSpriteFontProbe.class,event);require(NativeSpriteFont.select(crop)!=null,"Actual source-event loader selects physical palette address");}
          target.bind();api.viewport(0,0,320,240);api.clearColour(.2f,.2f,.2f);api.clear(true,true,false);GameEngine.RENDERER.scissorStack.reset();
          final var transform=new MV();transform.transfer.set(40,40,124);final var queued=GameEngine.RENDERER.queueUiOrthoModel(obj,transform,QueuedModelStandard.class);
          final var render=RenderEngine.class.getDeclaredMethod("renderBatch",RenderBatch.class);render.setAccessible(true);render.invoke(GameEngine.RENDERER,batch);glFinish();require(glGetError()==GL_NO_ERROR,"Palette-layout GPU draw");
          for(int attachment=0;attachment<2;attachment++){(attachment==0?output:emissionOutput).use(0);final var bytes=ByteBuffer.allocateDirect(320*240*4);glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,bytes);final var read=new byte[bytes.capacity()];bytes.get(read);if(attachment==0)colour[mode]=read;else emission[mode]=read;}
          final String name="layout-"+bank+"-palette"+palette+(mode==0?"-native":"-coverage");Files.write(proof.resolve(name+".rgba"),colour[mode],StandardOpenOption.CREATE_NEW);Files.write(proof.resolve(name+"-emission.rgba"),emission[mode],StandardOpenOption.CREATE_NEW);
        }
        require(Arrays.equals(colour[0],colour[1])&&Arrays.equals(emission[0],emission[1]),"Actual physical CLUT address preserves both native attachments");
        boolean visible=false;for(int p=0;p<colour[0].length;p+=4)visible|=(colour[0][p]&255)!=51||(colour[0][p+1]&255)!=51||(colour[0][p+2]&255)!=51;require(visible,"Palette layout control changes visible pixels");
        receipts.put(new JSONObject().put("family",event.id).put("palette",palette).put("eventClutRows",rows).put("sourceSha256",hash(event.source())).put("sourcePaletteSha256",pins[bank]).put("sourceImageSha256",bank<3?pins[0]:pins[3]).put("clutX",crop.clutX()).put("clutY",crop.clutY()).put("syntheticOutputSha256",hash(png.toByteArray())).put("crop",entry.get("crop")).put("originalLiveFace",originalFace).put("controlledLiveFace",memory[faceAddress]).put("scope","source-bound address/live-palette control with synthetic nearest art; not redraw acceptance"));
        }finally{reset();obj.delete();NativeSpriteFont.clear();batch.setProjectionSize(320,240);}
      }
    }
    report.put("actualSourcePaletteLayoutCases",receipts);
  }
  private static Image syntheticCoverage(final Image reference, final int[] palette) {
    final int size=64; byte[] bytes=new byte[size*size*4];
    for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
      float sx=(x+.5f)/8-.5f, sy=(y+.5f)/8-.5f; int ix=(int)Math.floor(sx),iy=(int)Math.floor(sy); float coverage=0;
      for(int yy=0;yy<2;yy++) for(int xx=0;xx<2;xx++) { int px=ix+xx,py=iy+yy;
        if(px>=0&&px<8&&py>=0&&py<8&&palette[reference.data[(py*8+px)*4]&255]!=0) coverage+=(xx==0?1-(sx-ix):sx-ix)*(yy==0?1-(sy-iy):sy-iy);
      }
      int p=(y*size+x)*4, index=reference.data[(Math.min(7,y/8)*8+Math.min(7,x/8))*4]&255;
      bytes[p]=(byte)(palette[index]==0?1:index); bytes[p+3]=(byte)Math.round(coverage*255);
    }
    return new Image(bytes,size,size);
  }
  private static void register(final Tim image, final int[] palette, final boolean fullCoverage, final int... rows) {
    for(int u:US) for(int row:rows) {
      Image reference=reference(image,u);
      Image artwork=syntheticCoverage(reference,palette);
      if(fullCoverage) {
        final byte[] bytes=new byte[16*16*4];
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){int p=(y*16+x)*4;bytes[p]=reference.data[(y/2*8+x/2)*4];bytes[p+3]=(byte)255;}
        artwork=new Image(bytes,16,16);
      }
      require(NativeSpriteFont.register(NativeSpriteFontProbe.class,"fixture",new NativeUiQuad(704,256,896,304+row,u,32,8,8),reference,artwork),"Synthetic fixture registers exact source crop");
    }
  }
  private static byte[] draw(final BattleHud hud, final BattleDisplayStats144 stats, final RenderBatch batch, final Api api, final FrameBuffer target, final Texture output, final int row, final Translucency blend, final float alpha, final boolean textureAlpha) throws Exception {
    reset(); GameEngine.RENDERER.scissorStack.reset(); target.bind(); api.viewport(0,0,320,240); api.clearColour(.2f,.2f,.2f); api.clear(true,true,false);
    GameEngine.DEFAULT_FONT.init();
    legend.game.Text.renderText(GameEngine.DEFAULT_FONT,"HP",stats.x_00-12,stats.y_02-15,new legend.game.inventory.screens.FontOptions());
    if(slash==null) {
      var builder=new QuadBuilder("Original HUD slash");var build=BattleHud.class.getDeclaredMethod("buildUiTextureElement",QuadBuilder.class,int.class,int.class,int.class,int.class,int.class);build.setAccessible(true);build.invoke(hud,builder,96,32,8,8,0x2c);slash=builder.build();
    }
    var slashTransform=new MV();slashTransform.transfer.set(stats.x_00+21,stats.y_02-15,124);
    GameEngine.RENDERER.queueUiOrthoModel(slash,slashTransform,QueuedModelStandard.class);
    var queue=BattleHud.class.getDeclaredMethod("queueStatDigit",BattleDisplayStats144.class,BattleDisplayStatsDigit10.class,int.class); queue.setAccessible(true);
    for(int number:new int[]{0,1}) for(int digit=0;digit<2;digit++) {
      var model=(QueuedModelStandard)queue.invoke(hud,stats,stats.digits_04[number][digit],2);
      model.clutOverride(896,304+row); if(blend!=null) model.translucency(blend); if(alpha!=-1) model.alpha(alpha); if(textureAlpha) model.useTextureAlpha();
      if(clipDigits)model.scissor(stats.x_00+12,stats.y_02-16,8,8);
    }
    var render=RenderEngine.class.getDeclaredMethod("renderBatch",RenderBatch.class); render.setAccessible(true); render.invoke(GameEngine.RENDERER,batch); glFinish();
    require(glGetError()==GL_NO_ERROR,"Actual HUD draw has no GL error");
    if(emissionOutput!=null){emissionOutput.use(0);var emission=ByteBuffer.allocateDirect(output.width*output.height*4);glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,emission);lastEmission=new byte[emission.capacity()];emission.get(lastEmission);}
    output.use(0); var buffer=ByteBuffer.allocateDirect(output.width*output.height*4); glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,buffer); byte[] pixels=new byte[buffer.capacity()];buffer.get(pixels);return pixels;
  }
  private static void save(final byte[] rgba, final Path file) throws Exception {
    save(rgba,file,320,240);
  }
  private static void save(final byte[] rgba, final Path file, final int width, final int height) throws Exception {
    var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=((height-1-y)*width+x)*4;image.setRGB(x,y,(rgba[p+3]&255)<<24|(rgba[p]&255)<<16|(rgba[p+1]&255)<<8|(rgba[p+2]&255));}
    require(ImageIO.write(image,"png",file.toFile()),"PNG writer available");
  }
  private static byte[] sheet(final BattleHud hud, final BattleDisplayStats144 stats, final RenderBatch batch, final Api api, final FrameBuffer target, final Texture output, final int scale, final float background) throws Exception {
    reset();batch.setProjectionSize(384/scale,240/scale);GameEngine.RENDERER.scissorStack.reset();target.bind();api.viewport(0,0,384,240);api.clearColour(background,background,background);api.clear(true,true,false);
    final var queue=BattleHud.class.getDeclaredMethod("queueStatDigit",BattleDisplayStats144.class,BattleDisplayStatsDigit10.class,int.class);queue.setAccessible(true);
    for(int row=0;row<2;row++)for(int value=0;value<10;value++) {
      hud.renderNumber(0,0,value,1);final var digit=stats.digits_04[0][0];stats.x_00=4+value*10-digit.x_02;stats.y_02=12+row*24-digit.y_04;
      ((QueuedModelStandard)queue.invoke(hud,stats,digit,2)).clutOverride(896,304+row*8);
    }
    final var render=RenderEngine.class.getDeclaredMethod("renderBatch",RenderBatch.class);render.setAccessible(true);render.invoke(GameEngine.RENDERER,batch);glFinish();require(glGetError()==GL_NO_ERROR,"All10 actual HUD sheet draw has no GL error");
    output.use(0);final var buffer=ByteBuffer.allocateDirect(output.width*output.height*4);glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,buffer);final byte[] pixels=new byte[buffer.capacity()];buffer.get(pixels);return pixels;
  }
  private static int foreground(final byte[] rgba,final int scale,final int row,final int value,final int background) {
    int count=0;for(int y=(12+row*24)*scale;y<(20+row*24)*scale;y++)for(int x=(4+value*10)*scale;x<(12+value*10)*scale;x++) {
      final int p=((239-y)*384+x)*4;
      if((rgba[p]&255)!=background||(rgba[p+1]&255)!=background||(rgba[p+2]&255)!=background)count++;
    }
    return count;
  }
  public static void main(final String[] args) throws Throwable {
    if(args.length<4) throw new IllegalArgumentException("Supply windowless JNI, read-only original 4113 directory, fresh proof directory, actual UIHD JAR; optional --native-only negative");
    final var flags=new HashSet<>(Arrays.asList(args).subList(4,args.length));require(flags.stream().allMatch(flag->flag.equals("--native-only")||flag.equals("--packaged")),"Known probe flags");
    final boolean nativeOnly=flags.contains("--native-only"),packaged=flags.contains("--packaged");
    Path proof=Path.of(args[2]).toAbsolutePath(); for(Path p=proof;p!=null;p=p.getParent()) if(Files.isSymbolicLink(p)) throw new IllegalArgumentException("Linked proof destination"); Files.createDirectory(proof);
    byte[] imageBytes=Files.readAllBytes(Path.of(args[1],"0")), paletteBytes=Files.readAllBytes(Path.of(args[1],"4"));
    require(hash(imageBytes).equals("f2c1e95184598a2db058a078affd4f9e3c9cbea749f90843e1d665b682ca52e4")&&hash(paletteBytes).equals("578f8eecd5377a6be20091248500f4a2908d316ceb166b460dd14e598654c162"),"Private original family source pins");
    final var report=new JSONObject().put("verdict","FAIL").put("scope",packaged?"windowless actual packaged UiHdMod listener and BattleHud small10 queues; separate synthetic native blend controls; no whole-game acceptance":"windowless actual BattleHud production queues; synthetic coverage fixture, no artwork or whole-game acceptance").put("packagedSmall10",packaged).put("sourceImageSha256",hash(imageBytes)).put("sourcePaletteSha256",hash(paletteBytes));
    System.load(args[0]); var open=NativeRendererProbe.class.getDeclaredMethod("open");open.setAccessible(true); long context=(long)open.invoke(null); require(context!=0,"Windowless context"); Throwable failure=null; Api api=null; FrameBuffer target=null;
    try(final var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(args[3]).toUri().toURL()},NativeSpriteFontProbe.class.getClassLoader())) {
      GL.createCapabilities(); CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      var setter=legend.game.saves.ConfigCollection.class.getDeclaredMethod("setConfigQuietly",legend.game.saves.ConfigEntry.class,Object.class);setter.setAccessible(true);setter.invoke(GameEngine.CONFIG,CoreMod.RESOLUTION_CONFIG.get(),Resolution.ORIGINAL);
      setter.invoke(GameEngine.CONFIG,CoreMod.ALLOW_WIDESCREEN_CONFIG.get(),false);report.put("controlledOffscreenWidescreen",false);
      api=new Api();var init=RenderEngine.class.getDeclaredMethod("init",Window.class);init.setAccessible(true);init.invoke(GameEngine.RENDERER,new OffscreenWindow(api));GameEngine.RENDERER.updateResolution();
      Texture output=Texture.empty("HUD glyph proof",320,240),depth=Texture.create("HUD glyph depth",b->{b.size(320,240);b.internalFormat(TextureInternalFormat.DEPTH_COMPONENT);b.dataFormat(TextureDataFormat.DEPTH_COMPONENT);b.dataType(TextureDataType.FLOAT);});
      emissionOutput=Texture.empty("HUD glyph emission proof",320,240);
      target=FrameBuffer.create("HUD glyph output",b->{b.attachment(FrameBufferAttachmentType.COLOUR,output);b.attachment(FrameBufferAttachmentType.COLOUR,emissionOutput);b.attachment(FrameBufferAttachmentType.DEPTH,depth);});target.bind();api.output=glGetInteger(GL_FRAMEBUFFER_BINDING);
      final Tim image=new Tim(new FileData(imageBytes)),paletteTim=new Tim(new FileData(paletteBytes)); int[] vram=new int[1024*512],palette=new int[16];
      byte[] packed=image.getImageData().getBytes();for(int y=0;y<256;y++)for(int x=0;x<64;x++) vram[(256+y)*1024+704+x]=(packed[(y*64+x)*2]&255)|(packed[(y*64+x)*2+1]&255)<<8;
      byte[] clut=paletteTim.getClutData().getBytes();for(int row=0;row<16;row++)for(int x=0;x<16;x++) {int value=(clut[(row*16+x)*2]&255)|(clut[(row*16+x)*2+1]&255)<<8;vram[(304+row)*1024+896+x]=value;if(row==0)palette[x]=value;}
      byte[] labelPaletteBytes=Files.readAllBytes(Path.of(args[1],"2"));require(hash(labelPaletteBytes).equals("b2a7ea197b475dde056a444bc217f3ea102cf2c3243ed9da7b3cc112d3eeffef"),"Original HUD slash palette pin");
      byte[] labelClut=new Tim(new FileData(labelPaletteBytes)).getClutData().getBytes();for(int row=0;row<16;row++)for(int x=0;x<16;x++)vram[(496+row)*1024+736+x]=(labelClut[(row*16+x)*2]&255)|(labelClut[(row*16+x)*2+1]&255)<<8;
      final var vramBuffer=org.lwjgl.BufferUtils.createIntBuffer(vram.length);vramBuffer.put(0,vram);
      GameEngine.GPU.vramTexture15=api.makeTexture(vramBuffer,"Pinned HUD VRAM",1024,512,TextureInternalFormat.R_32_UINT,TextureDataFormat.RED_INT,TextureDataType.UINT,false,false,false,false);GameEngine.GPU.vramTexture15.persistent=true;
      final var batch=(RenderBatch)field(GameEngine.RENDERER,RenderEngine.class,"mainBatch");batch.setProjectionSize(320,240);batch.setProjectionDepth(256);
      final Class<?> provider=Class.forName("uihd.UiHdMod",false,loader);
      require(Path.of(provider.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[3]).toRealPath()),"Actual UIHD packaged provider source");
      final Object module=provider.getConstructor().newInstance();
      final var selection=new legend.core.font.FontSelectionEvent();provider.getMethod("font",legend.core.font.FontSelectionEvent.class).invoke(module,selection);
      require(selection.font() instanceof legend.core.font.PackagedFont,"139-glyph provider stays enabled during numeric-family diagnosis");
      final var effective=(legend.core.font.EffectiveFont)GameEngine.DEFAULT_FONT;effective.selectOwned(selection.font());
      report.put("uihdJarSha256",hash(Files.readAllBytes(Path.of(args[3])))).put("default139Enabled",true);
      final var event=new NativeUiTextureEvent("battle_hud_4",NativeUiTextureEvent.withPalette(image,paletteTim),704,256,896,304);
      final var nativeListener=provider.getMethod("nativeTexture",NativeUiTextureEvent.class);
      if(packaged){nativeListener.invoke(module,event);NativeSpriteFont.clear();report.put("sourceCompositeSha256",hash(event.source())).put("existingRgbSelectionCount",NativeUiTextures.selectionCount());require(NativeUiTextures.selectionCount()>0,"Existing full-page RGB provider stays enabled");}
      final var hud=new BattleHud(null);final var stats=new BattleDisplayStats144();stats.x_00=100;stats.y_02=100;
      @SuppressWarnings("unchecked")final var values=(List<BattleDisplayStats144>)field(hud,BattleHud.class,"displayStats_800c6c2c");values.add(stats);var fade=BattleHud.class.getDeclaredField("hudFade");fade.setAccessible(true);fade.set(hud,6);
      hud.renderNumber(0,0,30,1);hud.renderNumber(0,1,30,1);
      final byte[] nativePixels=draw(hud,stats,batch,api,target,output,0,null,-1,false);save(nativePixels,proof.resolve("hud-30-30-native.png"));
      if(!nativeOnly){if(packaged)nativeListener.invoke(module,event);else register(image,palette,false,0);}
      final byte[] enhanced=draw(hud,stats,batch,api,target,output,0,null,-1,false);save(enhanced,proof.resolve("hud-30-30-coverage.png"));
      final var cropSelection=NativeSpriteFont.select(new NativeUiQuad(704,256,896,304,32,32,8,8));
      report.put("nativePixelSha256",hash(nativePixels)).put("coveragePixelSha256",hash(enhanced)).put("exactCropResourceSelected",cropSelection!=null).put("oldNumericPixelsUnchanged",Arrays.equals(nativePixels,enhanced));
      require(cropSelection!=null&&!Arrays.equals(nativePixels,enhanced),"Missing native glyph family fails BOTH exact-crop resource identity and unchanged old-pixel controls");
      if(packaged) {
        final byte[] metadata;try(var input=provider.getResourceAsStream("/uihd/font/native-sprites.json")){metadata=input.readAllBytes();}
        final var catalog=new JSONObject(new String(metadata,java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("sprites");final var identities=new org.json.JSONArray();
        for(int i=0;i<catalog.length();i++) {
          final var entry=catalog.getJSONObject(i);final var crop=entry.getJSONArray("crop");if(!entry.getString("family").equals(event.id)||!entry.getString("id").startsWith("native-small-"))continue;
          final var exact=NativeSpriteFont.select(new NativeUiQuad(704,256,896,304+entry.getInt("palette"),crop.getInt(0),crop.getInt(1),crop.getInt(2),crop.getInt(3)));require(exact!=null,"Every packaged small10 source token selects");
          final byte[] png;try(var input=provider.getResourceAsStream("/uihd/font/"+entry.getString("resource"))){png=input.readAllBytes();}require(hash(png).equals(entry.getString("outputSha256")),"Actual packaged glyph output identity");
          identities.put(new JSONObject().put("id",entry.getString("id")).put("resource",entry.getString("resource")).put("outputSha256",hash(png)).put("sourceSha256",entry.getString("sourceSha256")).put("crop",crop).put("palette",entry.getInt("palette")));
        }
        require(identities.length()==20,"Exactly10 packaged small glyphs across2 live palettes");report.put("nativeSmallSourceTokens",identities).put("nativeSpriteMetadataSha256",hash(metadata));
        NativeSpriteFont.clear();require(Arrays.equals(nativePixels,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Coverage off restores exact old RGB/native mask with139 enabled");
        nativeListener.invoke(module,event);require(Arrays.equals(enhanced,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Actual packaged listener re-enable restores authored small10 pixels");
        final Texture sheetOutput=Texture.empty("Small10 contextual sheet",384,240),sheetDepth=Texture.create("Small10 contextual depth",b->{b.size(384,240);b.internalFormat(TextureInternalFormat.DEPTH_COMPONENT);b.dataFormat(TextureDataFormat.DEPTH_COMPONENT);b.dataType(TextureDataType.FLOAT);});
        final FrameBuffer sheetTarget=FrameBuffer.create("Small10 contextual output",b->{b.attachment(FrameBufferAttachmentType.COLOUR,sheetOutput);b.attachment(FrameBufferAttachmentType.DEPTH,sheetDepth);});final int previousOutput=api.output;sheetTarget.bind();api.output=glGetInteger(GL_FRAMEBUFFER_BINDING);
        try {
          final var sheets=new org.json.JSONArray();
          for(int scale:new int[]{1,2,3})for(int background:new int[]{51,184}) {
            NativeSpriteFont.clear();final var old=sheet(hud,stats,batch,api,sheetTarget,sheetOutput,scale,background/255f);nativeListener.invoke(module,event);final var authored=sheet(hud,stats,batch,api,sheetTarget,sheetOutput,scale,background/255f);
            require(!Arrays.equals(old,authored),"Actual all10 packaged glyphs replace original pixels at scale"+scale+" background"+background);
            final String label="small10-"+scale+"x-rgb"+background;save(old,proof.resolve(label+"-native.png"),384,240);save(authored,proof.resolve(label+"-authored.png"),384,240);
            Files.write(proof.resolve(label+"-native.rgba"),old,StandardOpenOption.CREATE_NEW);Files.write(proof.resolve(label+"-authored.rgba"),authored,StandardOpenOption.CREATE_NEW);
            final var cells=new org.json.JSONArray();for(int row=0;row<2;row++)for(int value=0;value<10;value++) {
              final int before=foreground(old,scale,row,value,background),after=foreground(authored,scale,row,value,background);
              require(before>0&&after>0,"Every actual native/authored digit remains visible at scale"+scale+" palette"+row*8+" value"+value);
              cells.put(new JSONObject().put("value",value).put("palette",row*8).put("nativeForegroundPixels",before).put("authoredForegroundPixels",after));
            }
            sheets.put(new JSONObject().put("scale",scale).put("backgroundRgb",new int[]{background,background,background}).put("context","approximate gray context; no game-background claim").put("cells",cells).put("nativeRgbaSha256",hash(old)).put("authoredRgbaSha256",hash(authored)));
          }
          report.put("all10NativeSheets",sheets);
        }finally{reset();sheetTarget.delete();sheetOutput.delete();sheetDepth.delete();api.output=previousOutput;batch.setProjectionSize(320,240);stats.x_00=100;stats.y_02=100;hud.renderNumber(0,0,30,1);hud.renderNumber(0,1,30,1);}
        NativeUiTextures.clear();
      }
      register(image,palette,false,8);
      final var blendModes=new ArrayList<Translucency>();blendModes.add(null);blendModes.addAll(Arrays.asList(Translucency.FOR_RENDERING));
      int cases=0;for(int row:new int[]{0,8})for(Translucency blend:blendModes)for(boolean textureAlpha:new boolean[]{false,true})for(float alpha:new float[]{-1,0,.5f}) {
        NativeSpriteFont.clear();final var nativeControl=draw(hud,stats,batch,api,target,output,row,blend,alpha,textureAlpha);
        final var nativeEmission=lastEmission.clone();
        register(image,palette,true,0,8);final var coverageControl=draw(hud,stats,batch,api,target,output,row,blend,alpha,textureAlpha);
        final String caseName="blend-"+cases+"-row"+row+"-mode"+(blend==null?"none":blend.ordinal())+"-texalpha"+textureAlpha+"-alpha"+alpha;
        Files.write(proof.resolve(caseName+"-native.rgba"),nativeControl,StandardOpenOption.CREATE_NEW);Files.write(proof.resolve(caseName+"-coverage.rgba"),coverageControl,StandardOpenOption.CREATE_NEW);
        Files.write(proof.resolve(caseName+"-native-emission.rgba"),nativeEmission,StandardOpenOption.CREATE_NEW);Files.write(proof.resolve(caseName+"-coverage-emission.rgba"),lastEmission,StandardOpenOption.CREATE_NEW);
        if(!Arrays.equals(nativeControl,coverageControl)) {
          save(nativeControl,proof.resolve("blend-native-failure.png"));save(coverageControl,proof.resolve("blend-coverage-failure.png"));
          int rgb=0,alphas=0;for(int p=0;p<nativeControl.length;p+=4){if(nativeControl[p]!=coverageControl[p]||nativeControl[p+1]!=coverageControl[p+1]||nativeControl[p+2]!=coverageControl[p+2])rgb++;if(nativeControl[p+3]!=coverageControl[p+3])alphas++;}
          report.put("mismatchRgbPixels",rgb).put("mismatchAlphaPixels",alphas);
        }
        require(Arrays.equals(nativeControl,coverageControl),"Full-coverage sprite preserves native palette/STP/blend/fade pixels row"+row+" mode"+blend+" alpha"+alpha+" textureAlpha"+textureAlpha);cases++;
        require(Arrays.equals(nativeEmission,lastEmission),"Native emission attachment retains identical RGBA for row"+row+" mode"+blend+" alpha"+alpha+" textureAlpha"+textureAlpha);
      }
      if(!packaged){NativeSpriteFont.clear();require(Arrays.equals(nativePixels,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Disable returns exact native pixels");register(image,palette,false,0);require(Arrays.equals(enhanced,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Re-enable restores same coverage");}
      int contextualCases=0;clipDigits=true;
      for(int fadeLevel:new int[]{3,6})for(int colour:new int[]{2,3}) {
        fade.set(hud,fadeLevel);hud.renderNumber(0,0,30,colour);hud.renderNumber(0,1,30,colour);
        NativeSpriteFont.clear();final var original=draw(hud,stats,batch,api,target,output,0,null,-1,false);register(image,palette,true,0);
        require(Arrays.equals(original,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Actual HUD fade/tint and caller scissor are preserved");
        require(!Arrays.equals(nativePixels,original),"Clipped/tinted controls change visible pixels");contextualCases++;
      }
      clipDigits=false;fade.set(hud,6);hud.renderNumber(0,0,30,1);hud.renderNumber(0,1,30,1);
      stats.x_00=40;stats.y_02=40;
      for(int scale:new int[]{1,2,4}) {
        batch.setProjectionSize(320/scale,240/scale);
        NativeSpriteFont.clear();final var original=draw(hud,stats,batch,api,target,output,0,null,-1,false);register(image,palette,true,0);
        require(Arrays.equals(original,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Indexed full-coverage controls preserve actual HUD pixels at scale "+scale);contextualCases++;
      }
      batch.setProjectionSize(320,240);stats.x_00=100;stats.y_02=100;
      final var retained=NativeSpriteFont.select(new NativeUiQuad(704,256,896,304,32,32,8,8));
      final int artworkId=(int)field(retained.artwork(),legend.core.renderer.opengl.GlTexture.class,"id"),referenceId=(int)field(retained.reference(),legend.core.renderer.opengl.GlTexture.class,"id");
      require(retained.artwork().hasAllocatedStorage()&&retained.reference().hasAllocatedStorage(),"Actual glyph GPU storage exists");
      NativeSpriteFont.clear();require(!retained.current()&&glIsTexture(artworkId)&&glIsTexture(referenceId),"Invalidated glyph storage survives retained draw batches");
      reset();require(!glIsTexture(artworkId)&&!glIsTexture(referenceId),"Both owned glyph textures retire after actual batch reset before ID reuse");
      final int originalFace=vram[304*1024+897];vram[304*1024+897]=0x03e0;
      GameEngine.GPU.vramTexture15.data(0,0,1024,512,TextureDataType.UINT,vram);
      final var liveNative=draw(hud,stats,batch,api,target,output,0,null,-1,false);register(image,palette,true,0);
      require(Arrays.equals(liveNative,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Authored palette indices fetch changed live CLUT colours");
      NativeSpriteFont.clear();vram[304*1024+897]=originalFace;
      boolean[] used=new boolean[16];for(int u:new int[]{32,88}){var ref=reference(image,u);for(int p=0;p<ref.data.length;p+=4)used[ref.data[p]&255]=true;}
      int replacement=1;while(replacement<16&&used[replacement])replacement++;require(replacement<16,"Source overwrite control has a distinct native index");
      final int word=replacement*0x1111;for(int u:new int[]{32,88})for(int y=32;y<40;y++)for(int x=u/4;x<u/4+2;x++)vram[(256+y)*1024+704+x]=word;
      GameEngine.GPU.vramTexture15.data(0,0,1024,512,TextureDataType.UINT,vram);
      final var changedNative=draw(hud,stats,batch,api,target,output,0,null,-1,false);register(image,palette,false,0);
      require(Arrays.equals(changedNative,draw(hud,stats,batch,api,target,output,0,null,-1,false)),"Every overwritten native index falls back per fragment without obsolete glyph coverage");
      require(!Arrays.equals(liveNative,changedNative),"Native source overwrite control changes visible HUD pixels");
      paletteLayoutControls(Path.of(args[1]),proof,batch,api,target,output,report);
      require(field(effective,legend.core.font.EffectiveFont.class,"owned")==selection.font(),"139-glyph default identity is unchanged by numeric-only selection");
      report.put("blendDrawCases",cases).put("contextualDrawCases",contextualCases).put("gpuRetirementVerified",true).put("liveClutVerified",true).put("sourceOverwriteFallbackVerified",true).put("verdict","PASS").put("gpu",glGetString(GL_RENDERER));
    } catch(Throwable problem) {failure=problem;report.put("failure",problem.toString());}
    finally {
      try {NativeSpriteFont.clear();reset();if(api!=null)api.output=0;if(target!=null)target.delete();GameEngine.RENDERER.delete();Obj.deleteObjects();Texture.deleteTextures();require(glGetError()==GL_NO_ERROR,"GPU cleanup");}catch(Throwable problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);report.put("verdict","FAIL").put("cleanupFailure",problem.toString());}
      try {var close=NativeRendererProbe.class.getDeclaredMethod("close",long.class);close.setAccessible(true);close.invoke(null,context);}catch(Throwable problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);report.put("verdict","FAIL");}
      legend.core.Async.shutdown();GL.setCapabilities(null);Files.writeString(proof.resolve("receipt.json"),report.toString(2),StandardOpenOption.CREATE_NEW);
    }
    if(failure!=null)throw failure;System.out.println(report.toString(2));
  }
}
