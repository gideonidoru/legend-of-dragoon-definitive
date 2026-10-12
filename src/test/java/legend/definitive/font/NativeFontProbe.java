package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.font.*;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.definitive.rendering.NativeRendererProbe;
import legend.game.Text;
import legend.game.inventory.screens.FontOptions;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import org.lwjgl.opengl.GL;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.Arrays;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL30C.*;

/** Optional windowless real-backend proof; ordinary tests never create a GPU context. */
public final class NativeFontProbe {
  private static final int WIDTH=320, HEIGHT=240;
  private static final class OffscreenApi extends GlApi {
    int output;
    @Override public void unbindFramebuffer() { glBindFramebuffer(GL_FRAMEBUFFER,this.output); }
  }
  private static final class HeadlessWindow extends NoopWindow {
    final OffscreenApi api;
    HeadlessWindow(OffscreenApi api) { super(new NoopPlatformManager(),WIDTH,HEIGHT); this.api=api; }
    @Override public RenderApi getRenderApi() { return this.api; }
  }
  private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
  private static Object field(Object object,Class<?> type,String name) throws Exception {
    var field=type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static int handle(Object object,String name) throws Exception { return (int)field(object,object.getClass(),name); }
  private static byte[] pixels(Texture texture) {
    texture.use(0); glActiveTexture(GL_TEXTURE0); var pixels=ByteBuffer.allocateDirect(texture.width*texture.height*4);
    glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels); var result=new byte[pixels.capacity()]; pixels.get(result); return result;
  }
  private static void save(byte[] pixels,Path path) throws Exception {
    save(pixels,WIDTH,HEIGHT,path);
  }
  private static void save(byte[] pixels,int width,int height,Path path) throws Exception {
    var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<height;y++) for(int x=0;x<width;x++) { int p=((height-1-y)*width+x)*4;
      image.setRGB(x,y,0xff000000|(pixels[p]&255)<<16|(pixels[p+1]&255)<<8|(pixels[p+2]&255)); }
    require(ImageIO.write(image,"png",path.toFile()),"PNG proof writer available");
  }
  private static byte[] draw(Font font,FontOptions options,Texture output,FrameBuffer buffer,RenderBatch batch,OffscreenApi api) throws Exception {
    var reset=RenderEngine.class.getDeclaredMethod("resetBatches"); reset.setAccessible(true); reset.invoke(GameEngine.RENDERER);
    GameEngine.RENDERER.scissorStack.reset(); buffer.bind(); api.viewport(0,0,WIDTH,HEIGHT); api.clear(true,true,false);
    final int[] count={0}, shadows={0};
    Text.renderText(font,"AVWXYZ aemnrw 0123456789",40,40,options,(model,shadow)-> { count[0]++; if(shadow) shadows[0]++; });
    require(count[0]>0,"actual Font.queueChar executed"); require(options.hasShadow()==(shadows[0]>0),"actual text shadow queues agree");
    var render=RenderEngine.class.getDeclaredMethod("renderBatch",RenderBatch.class); render.setAccessible(true); render.invoke(GameEngine.RENDERER,batch);
    glFinish(); require(glGetError()==GL_NO_ERROR,"shipping font render has no GL errors"); return pixels(output);
  }
  private static char codepoint(String key) { return key.length()==1?key.charAt(0):(char)Integer.parseInt(key.substring(2),16); }
  private static int glyphSheets(Font font,JSONObject metadata,RenderBatch batch,OffscreenApi api,Path proof) throws Exception {
    final var keys=new java.util.ArrayList<>(metadata.getJSONObject("glyphs").keySet()); keys.sort(String::compareTo);
    final var groups=new java.util.LinkedHashMap<String,java.util.List<String>>();
    groups.put("letters",keys.stream().filter(k->codepoint(k)<0xe000).toList()); groups.put("controllers",keys.stream().filter(k->codepoint(k)>=0xe000).toList());
    require(groups.get("letters").size()==98&&groups.get("controllers").size()==41,"all actual glyph raster groups covered");
    final var manifest=new JSONObject(); int verified=0;
    for(final var group:groups.entrySet()) {
      var legend=new org.json.JSONArray();
      for(int i=0;i<group.getValue().size();i++) legend.put(new JSONObject().put("index",i).put("key",group.getValue().get(i)).put("column",i%12).put("row",i/12));
      manifest.put(group.getKey(),legend);
      for(int scale=1;scale<=3;scale++) for(boolean light:new boolean[]{false,true}) {
        final int width=WIDTH*scale,height=HEIGHT*scale,bg=light?204:0;
        var output=Texture.empty("Font glyph sheet",width,height); var depth=Texture.create("Font sheet depth",b->{b.size(width,height);b.internalFormat(TextureInternalFormat.DEPTH_COMPONENT);b.dataFormat(TextureDataFormat.DEPTH_COMPONENT);b.dataType(TextureDataType.FLOAT);});
        var buffer=FrameBuffer.create("Font glyph sheet",b->{b.attachment(FrameBufferAttachmentType.COLOUR,output);b.attachment(FrameBufferAttachmentType.DEPTH,depth);});
        final int previousOutput=api.output;
        try {
          var reset=RenderEngine.class.getDeclaredMethod("resetBatches");reset.setAccessible(true);reset.invoke(GameEngine.RENDERER);
          buffer.bind();api.output=glGetInteger(GL_FRAMEBUFFER_BINDING);api.resize(width,height);api.viewport(0,0,width,height);api.clearColour(bg/255.0f,bg/255.0f,bg/255.0f);api.clear(true,true,false);
          GameEngine.RENDERER.scissorStack.reset();font.init();
          for(int i=0;i<group.getValue().size();i++) {
            final String key=group.getValue().get(i); final char c=codepoint(key);
            final int x=4+i%12*26,y=1+i/12*26;
            final float ink=light?0:1;
            Text.renderText(GameEngine.FONTS.get(Path.of("gfx/fonts/default.json")),String.format("%03d",i),x,y,new FontOptions().size(.35f).colour(ink,ink,ink),(model,shadow)->model.worldScissor().set(0,0,width,height));
            Text.renderText(font,String.valueOf(c),x+6,y+9,new FontOptions().colour(ink,ink,ink),(model,shadow)->model.worldScissor().set(0,0,width,height));
          }
          var render=RenderEngine.class.getDeclaredMethod("renderBatch",RenderBatch.class);render.setAccessible(true);render.invoke(GameEngine.RENDERER,batch);glFinish();
          byte[] raster=pixels(output);require(glGetError()==GL_NO_ERROR,"shipping glyph sheet GL draw");
          for(int i=0;i<group.getValue().size();i++) {
            if(group.getValue().get(i).equals(" ")) continue;
            final int x=(4+i%12*26+5)*scale,y=(1+i/12*26+9)*scale; int visible=0;
            for(int row=y;row<Math.min(height,y+16*scale);row++)for(int col=x;col<Math.min(width,x+14*scale);col++){
              int p=((height-1-row)*width+col)*4;if((raster[p]&255)!=bg||(raster[p+1]&255)!=bg||(raster[p+2]&255)!=bg)visible++;
            }
            require(visible>0,"glyph vanished at "+scale+"x "+(light?"light":"dark")+" "+group.getValue().get(i)); verified++;
          }
          save(raster,width,height,proof.resolve(group.getKey()+"-"+scale+"x-"+(light?"light":"dark")+".png"));
        } finally { api.output=previousOutput;buffer.delete(); output.delete();depth.delete();Obj.deleteObjects();Texture.deleteTextures(); }
      }
    }
    Files.writeString(proof.resolve("glyph-sheet-legend.json"),manifest.toString(2),StandardOpenOption.CREATE_NEW);
    api.resize(WIDTH,HEIGHT);api.clearColour(0,0,0);return verified;
  }

  private static long nonBlack(byte[] pixels) { long count=0; for(int p=0;p<pixels.length;p+=4) if(pixels[p]!=0||pixels[p+1]!=0||pixels[p+2]!=0) count++; return count; }
  public static void main(String[] args) throws Throwable {
    if(args.length!=3) throw new IllegalArgumentException("Supply windowless JNI library, actual UIHD JAR and fresh private proof directory");
    Path proof=Path.of(args[2]).toAbsolutePath();
    for(Path prefix=proof;prefix!=null;prefix=prefix.getParent()) if(Files.isSymbolicLink(prefix)) throw new IllegalArgumentException("Linked proof destination");
    Files.createDirectory(proof);
    System.load(Path.of(args[0]).toAbsolutePath().toString()); var open=NativeRendererProbe.class.getDeclaredMethod("open"); open.setAccessible(true);
    long context=(long)open.invoke(null); require(context!=0,"Windowless CGL context available");
    boolean capabilities=false, initialized=false; Throwable failure=null; FrameBuffer buffer=null; OffscreenApi api=null;
    var report=new JSONObject().put("verdict","FAIL").put("scope","windowless actual shipping font GPU; no game, UI window or device acceptance");
    try(var loader=new URLClassLoader(new java.net.URL[]{Path.of(args[1]).toUri().toURL()},NativeFontProbe.class.getClassLoader())) {
      GL.createCapabilities(); capabilities=true; report.put("gpu",glGetString(GL_RENDERER)).put("glVersion",glGetString(GL_VERSION));
      CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      var setter=legend.game.saves.ConfigCollection.class.getDeclaredMethod("setConfigQuietly",legend.game.saves.ConfigEntry.class,Object.class); setter.setAccessible(true);
      setter.invoke(GameEngine.CONFIG,CoreMod.RESOLUTION_CONFIG.get(),Resolution.ORIGINAL);
      final String savedChoice=GameEngine.CONFIG.getConfig(CoreMod.RETAIL_FONT_CONFIG.get());
      api=new OffscreenApi(); var init=RenderEngine.class.getDeclaredMethod("init",Window.class); init.setAccessible(true); initialized=true; init.invoke(GameEngine.RENDERER,new HeadlessWindow(api)); GameEngine.RENDERER.updateResolution();
      var output=Texture.empty("Font proof output",WIDTH,HEIGHT); output.persistent=true;
      var depth=Texture.create("Font proof depth",b-> { b.size(WIDTH,HEIGHT); b.internalFormat(TextureInternalFormat.DEPTH_COMPONENT); b.dataFormat(TextureDataFormat.DEPTH_COMPONENT); b.dataType(TextureDataType.FLOAT); }); depth.persistent=true;
      buffer=FrameBuffer.create("Font proof frame",b-> { b.attachment(FrameBufferAttachmentType.COLOUR,output); b.attachment(FrameBufferAttachmentType.DEPTH,depth); }); buffer.bind(); api.output=glGetInteger(GL_FRAMEBUFFER_BINDING);
      GameEngine.GPU.vramTexture15=api.makeTexture(org.lwjgl.BufferUtils.createIntBuffer(1024*512),"Font proof synthetic VRAM",1024,512,TextureInternalFormat.R_32_UINT,TextureDataFormat.RED_INT,TextureDataType.UINT,false,false,false,false); GameEngine.GPU.vramTexture15.persistent=true;
      var batch=(RenderBatch)field(GameEngine.RENDERER,RenderEngine.class,"mainBatch"); batch.setProjectionSize(WIDTH,HEIGHT); batch.setProjectionDepth(256);
      legend.game.Graphics.displayWidth_1f8003e0=WIDTH; Text.textZ_800bdf00=10;
      Class<?> owner=Class.forName("uihd.UiHdMod",false,loader);
      require(Path.of(owner.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[1]).toRealPath()),"resource provider comes from actual requested UIHD JAR");
      final org.legendofdragoon.modloader.events.EventManager.Access[] eventAccess=new org.legendofdragoon.modloader.events.EventManager.Access[1];
      final var eventBus=new org.legendofdragoon.modloader.events.EventManager(access->eventAccess[0]=access,(mod,error)->{throw new AssertionError("Actual font provider listener failed",error);});
      final var mods=new org.legendofdragoon.modloader.ModManager(access->{});
      eventAccess[0].initialize(mods);
      final Object provider=owner.getConstructor().newInstance();
      eventBus.register(provider);
      var selection=eventBus.postEvent(new FontSelectionEvent()); require(selection.font()!=null,"actual packaged UiHdMod listener selected font through event bus"); require(!selection.select(owner),"duplicate provider cannot create or replace owned selection");
      eventAccess[0].reset(); require(eventBus.postEvent(new FontSelectionEvent()).font()==null,"unloaded provider leaves fresh event empty");
      eventAccess[0].initialize(mods); eventBus.register(provider);
      require(eventBus.postEvent(new FontSelectionEvent()).font()!=null,"reloaded actual provider reselects packaged resources");
      report.put("packagedProviderEventBusUnloadReload","PASS");
      var enhanced=(PackagedFont)selection.font(); var effective=(EffectiveFont)GameEngine.DEFAULT_FONT;
      var configured=GameEngine.FONTS.get(Path.of("gfx/fonts/smooth.json")); GameEngine.configureDefaultFont(configured);
      effective.selectOwned(enhanced); effective.init();
      var texture=(Texture)field(enhanced,Font.class,"texture"); var obj=(MeshObj)field(enhanced,Font.class,"obj");
      texture.use(0); int textureId=glGetInteger(GL_TEXTURE_BINDING_2D), vao=handle(obj.meshes[0],"vao"), vbo=handle(obj.meshes[0],"vbo");
      final byte[] atlasBytes=legend.definitive.artwork.ArtworkResources.read(owner,"/uihd/font/original-hd.png",32*1024*1024);
      final byte[] metadataBytes=legend.definitive.artwork.ArtworkResources.read(owner,"/uihd/font/original-hd.json",256*1024);
      report.put("atlasSha256",legend.definitive.textures.TexturePilot.sha256(atlasBytes)).put("metadataSha256",legend.definitive.textures.TexturePilot.sha256(metadataBytes));
      report.put("engineCodeSource",Font.class.getProtectionDomain().getCodeSource().getLocation().toString());
      final var digest=java.security.MessageDigest.getInstance("SHA-256");
      try(var input=Files.newInputStream(Path.of(args[1]))) { byte[] block=new byte[65536]; int read; while((read=input.read(block))!=-1) digest.update(block,0,read); }
      report.put("uihdJarSha256",java.util.HexFormat.of().formatHex(digest.digest()));
      var rawPng=ImageIO.read(new java.io.ByteArrayInputStream(atlasBytes)); byte[] uploaded=pixels(texture);
      for(int y=0;y<texture.height;y++) for(int x=0;x<texture.width;x++) { int value=rawPng.getRGB(x,y), p=(y*texture.width+x)*4;
        if((uploaded[p]&255)!=(value>>>16&255)||(uploaded[p+1]&255)!=(value>>>8&255)||(uploaded[p+2]&255)!=(value&255)||(uploaded[p+3]&255)!=(value>>>24)) throw new AssertionError("Actual atlas upload differs at "+x+","+y); }
      report.put("atlasUpload","PASS");
      var plain=draw(effective,new FontOptions().colour(1,1,1),output,buffer,batch,api); require(nonBlack(plain)>100,"actual shipping font produces visible text"); save(plain,proof.resolve("font-plain.png"));
      long partial=0; for(int p=0;p<plain.length;p+=4) if((plain[p]&255)>0&&(plain[p]&255)<255) partial++;
      require(partial>10,"RGBA atlas antialiasing survives shipping true-alpha font draw");
      var shadow=draw(effective,new FontOptions().colour(1,1,1).shadowColour(.3f,.3f,.3f),output,buffer,batch,api); require(!Arrays.equals(plain,shadow)&&nonBlack(shadow)>nonBlack(plain),"actual shipping shadows add visible pixels"); save(shadow,proof.resolve("font-shadow.png"));
      var clipped=draw(effective,new FontOptions().colour(1,1,1).trim(-6),output,buffer,batch,api); require(nonBlack(clipped)>0&&nonBlack(clipped)<nonBlack(plain),"actual shipping clipping retains bounded partial text"); save(clipped,proof.resolve("font-clipped.png"));
      byte[] retained=pixels(output); effective.selectOwned(null); require(effective.configured()==configured,"disable restores saved configured font");
      Obj.deleteObjects(); Texture.deleteTextures(); require(glIsTexture(textureId)&&glIsVertexArray(vao)&&glIsBuffer(vbo),"retained real draw resources survive disable until queue reset");
      require(Arrays.equals(retained,pixels(output)),"disable does not alter retained presented frame");
      var restored=draw(effective,new FontOptions().colour(1,1,1),output,buffer,batch,api); Obj.deleteObjects(); Texture.deleteTextures();
      require(!glIsTexture(textureId)&&!glIsVertexArray(vao)&&!glIsBuffer(vbo),"real font texture and mesh retire after retained queues clear"); require(nonBlack(restored)>0,"configured font renders after disable");
      effective.selectOwned(PackagedFont.load(owner)); var reenabled=draw(effective,new FontOptions().colour(1,1,1),output,buffer,batch,api); require(Arrays.equals(plain,reenabled),"re-enable creates fresh identical actual font rendering");
      CoreMod.RETAIL_FONT_CONFIG.get().onChange(GameEngine.CONFIG,savedChoice,"default.json"); require(effective.configured()==GameEngine.FONTS.get(Path.of("gfx/fonts/default.json")),"configured font reload remains distinct during override");
      require(Arrays.equals(plain,draw(effective,new FontOptions().colour(1,1,1),output,buffer,batch,api)),"configuration reload preserves enabled HD selection");
      effective.selectOwned(null); require(GameEngine.CONFIG.getConfig(CoreMod.RETAIL_FONT_CONFIG.get()).equals(savedChoice),"UIHD enable/disable never overwrites configured saved choice");
      report.put("trueAlphaShadowClippingLifecycleConfig","PASS");
      effective.selectOwned(PackagedFont.load(owner));
      int rasterCases=glyphSheets(effective,new JSONObject(new String(metadataBytes,java.nio.charset.StandardCharsets.UTF_8)),batch,api,proof);
      effective.selectOwned(null);
      require(glGetError()==GL_NO_ERROR,"native lifecycle leaves no GL errors"); report.put("lightBackgroundRgb",new int[]{204,204,204}).put("darkBackgroundRgb",new int[]{0,0,0}).put("contrastLimit","Source-white uncoloured symbols are not guaranteed readable against pure white; retained fourth-run negative proves this boundary").put("glyphRasterCases",rasterCases).put("glyphCount",139).put("uploadPixels",texture.width*texture.height).put("antialiasedPixels",partial).put("verdict","PASS");
    } catch(Throwable problem) { failure=problem; report.put("failure",problem.toString()); }
    finally {
      if(capabilities) {
        try { if(api!=null) api.output=0; if(buffer!=null) buffer.delete(); if(initialized) GameEngine.RENDERER.delete(); Obj.deleteObjects(); Texture.deleteTextures(); require(glGetError()==GL_NO_ERROR,"native cleanup has no GL errors"); }
        catch(Throwable cleanup) { if(failure==null) failure=cleanup; else failure.addSuppressed(cleanup); report.put("verdict","FAIL").put("cleanupFailure",cleanup.toString()); }
      }
      try { var close=NativeRendererProbe.class.getDeclaredMethod("close",long.class); close.setAccessible(true); close.invoke(null,context); }
      catch(Throwable cleanup) { if(failure==null) failure=cleanup; else failure.addSuppressed(cleanup); report.put("verdict","FAIL").put("contextCleanupFailure",cleanup.toString()); }
      legend.core.Async.shutdown(); GL.setCapabilities(null); Files.writeString(proof.resolve("receipt.json"),report.toString(2),StandardOpenOption.CREATE_NEW);
    }
    if(failure!=null) throw failure; System.out.println(report.toString(2));
  }
}
