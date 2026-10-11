package legend.definitive.artwork;

import legend.core.GameEngine;
import legend.core.gpu.Gpu;
import legend.core.gpu.Rect4i;
import legend.core.renderer.*;
import legend.core.renderer.opengl.GlApi;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.textures.Image;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.json.JSONObject;
import org.json.JSONArray;
import org.lwjgl.opengl.GL;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL33C.*;

/** Synthetic 4x indexed UI detail; real Standard shader/queue, no game or window. */
public final class NativeUiPaletteDetailProbe {
  static long open() throws Exception {var m=legend.definitive.rendering.NativeDepthClearProbe.class.getDeclaredMethod("open");m.setAccessible(true);return (long)m.invoke(null); }
  static long close(long context, boolean injected) throws Exception {var m=legend.definitive.rendering.NativeDepthClearProbe.class.getDeclaredMethod("close",long.class,boolean.class);m.setAccessible(true);return (long)m.invoke(null,context,injected); }
  static final int W=16,H=4;
  static final List<Runnable> resources=new ArrayList<>();
  static final JSONArray cleanupErrors=new JSONArray(), cases=new JSONArray();
  static QueuedModelStandard reusableQueue;
  static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
  static void field(Object owner,String name,Object value)throws Exception {
    var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
  }
  static void config(legend.game.saves.ConfigEntry<?> entry,Object value)throws Exception{
    var m=legend.game.saves.ConfigCollection.class.getDeclaredMethod("setConfigQuietly",legend.game.saves.ConfigEntry.class,Object.class);
    m.setAccessible(true);m.invoke(GameEngine.CONFIG,entry,value);
  }
  static Tim tim(int nativeIndex,int first,int second) {
    var b=ByteBuffer.allocate(66).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(16).putInt(8).putInt(44).putShort((short)0).putShort((short)256).putShort((short)16).putShort((short)1);
    for(int i=0;i<16;i++)b.putShort((short)(i==2?first:i==3?second:0));
    b.putInt(14).putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)1).putShort((short)(nativeIndex*0x1111));
    return new Tim(new FileData(b.array()));
  }
  static void upload(Gpu gpu,Tim tim){gpu.uploadData15(tim.getImageRect(),tim.getImageData());gpu.uploadData15(tim.getClutRect(),tim.getClutData());gpu.updateVramTexture();}
  static float[] identity(int size){var a=new float[size];for(int i=0;i<16;i+=5)a[i]=1;return a;}
  static void ubo(GlApi api,int binding,float[] data){var u=api.makeUniformBuffer(data.length*4L,binding);resources.add(u::delete);var nativeBuffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();nativeBuffer.put(data).flip();u.set(nativeBuffer);}
  static float fixtureOffset;
  static byte[] draw(GlApi api,Shader<ShaderOptionsStandard> shader,RenderBatch batch,MeshObj obj,Texture privateVram,boolean detail,int mode,String name)throws Exception {
    if(reusableQueue==null) reusableQueue=new QueuedModelStandard(batch,shader,shader.makeOptions(),FloatBuffer.allocate(128*32));
    var q=reusableQueue;
    var acquire=QueuedModelStandard.class.getDeclaredMethod("acquire",Obj.class,int.class);acquire.setAccessible(true);acquire.invoke(q,obj,0);
    q.ui();q.translucency(Translucency.HALF_B_PLUS_HALF_F);
    q.uvOffset(fixtureOffset,0);
    if(privateVram!=null)q.texture(privateVram,1);
    if(detail) legend.game.textures.NativeUiTextures.apply(q,0,0,0,256,0,0,4,1);
    q.useShader(0,mode);
    var texture=QueuedModel.class.getDeclaredMethod("useTexture");texture.setAccessible(true);texture.invoke(q);
    glClearColor(0,0,0,0);api.clear(true,false,false);
    var render=QueuedModelStandard.class.getDeclaredMethod("render",Translucency.class,int.class);render.setAccessible(true);render.invoke(q,Translucency.HALF_B_PLUS_HALF_F,0);
    var data=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);
    var pixels=new byte[data.capacity()];data.get(pixels);int error=glGetError();require(error==GL_NO_ERROR,"GL draw error "+error);
    cases.put(new JSONObject().put("name",name).put("detail",detail).put("privateOwner",privateVram!=null).put("discardMode",mode).put("glError",error));return pixels;
  }
  static void oracle(byte[] actual,int nativeColour,boolean selected,int mode) {
    boolean stp=(nativeColour&0x8000)!=0,black=(nativeColour&0x7fff)==0;
    boolean visible=(!black||stp) && !(mode==1&&stp) && !(mode==2&&!stp);
    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
      int p=(y*W+x)*4;float coefficient=((x%4)+(H-1-y)*4+1)*8f/131f;
      for(int channel=0;channel<3;channel++) {
        int colour=(nativeColour>>(channel*5))&31;
        int expected=visible?Math.round(colour*255f/31*(selected&&!black?coefficient:1)):0;
        require(Math.abs((actual[p+channel]&255)-expected)<=1,"Palette/subtexel oracle x="+x+" y="+y+" channel="+channel+" got="+(actual[p+channel]&255)+" expected="+expected);
      }
      require((actual[p+3]&255)==(visible?128:0),"Native coverage/STP pass mismatch");
    }
  }
  static int differing(byte[] a,byte[] b){int count=0;for(int p=0;p<a.length;p+=4)if(a[p]!=b[p]||a[p+1]!=b[p+1]||a[p+2]!=b[p+2])count++;return count;}
  public static void main(String[] args)throws Exception {
    if(args.length<2)throw new IllegalArgumentException("Supply existing depth-context JNI library and report path");
    boolean injected=args.length>2 && args[2].equals("--inject-cleanup-failure");
    var report=new JSONObject().put("scope","Synthetic actual Standard queue/shader live-palette and ownership readback; no game/window/retail assets").put("cases",cases).put("cleanupErrors",cleanupErrors).put("steamDeckAccepted",false);
    long context=0;boolean capabilities=false;Throwable failure=null;
    try {
      System.load(args[0]);context=open();require(context!=0,"Windowless CGL context");GL.createCapabilities();capabilities=true;
      report.put("device",glGetString(GL_RENDERER)).put("version",glGetString(GL_VERSION));
      GlApi api=new GlApi();field(GameEngine.RENDERER,"api",api);api.init();api.resize(W,H);api.viewport(0,0,W,H);
      CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
      config(CoreMod.MATERIAL_LIGHTING_CONFIG.get(),false);config(CoreMod.DEFAULT_SURFACE_DETAIL_CONFIG.get(),false);
      Shader<ShaderOptionsStandard> shader=ShaderManager.addShader(RenderEngine.STANDARD_SHADER);resources.add(shader::delete);
      var transforms=identity(32);for(int i=0;i<16;i++)transforms[16+i]=transforms[i];ubo(api,ShaderUniformBuffer.TRANSFORM,transforms);
      ubo(api,ShaderUniformBuffer.TRANSFORM2,identity(128*20));ubo(api,ShaderUniformBuffer.LIGHTING,new float[128*32]);
      ubo(api,ShaderUniformBuffer.PROJECTION_INFO,new float[]{0,.1f,10,0});ubo(api,ShaderUniformBuffer.SCISSOR,new float[]{0,0,W,H});
      var cluts=new float[1024*4];cluts[0]=-1;ubo(api,ShaderUniformBuffer.CLUT_ANIMATION,cluts);
      var values=new ArrayList<Float>();float[][] corners={{-1,1,0,0},{-1,-1,0,1},{1,1,4,0},{1,-1,4,1}};
      for(int c:new int[]{0,1,2,1,3,2}) {
        float[] v=corners[c];for(float f:new float[]{v[0],v[1],.5f,0,0,0,1,v[2],v[3],0,256<<6,1,1,1,1,6})values.add(f);
      }
      float[] vertices=new float[values.size()];for(int i=0;i<vertices.length;i++)vertices[i]=values.get(i);
      Mesh mesh=api.makeMesh("UI palette fixture",VertexOrder.TRIANGLES,vertices,vertices.length/16,true,false,null,BufferUsage.STATIC);resources.add(mesh::delete);
      int[] sizes={4,3,2,1,1,4,1};int offset=0;for(int i=0;i<sizes.length;i++){mesh.attribute(i,offset,sizes[i],16);offset+=sizes[i];}
      var obj=new MeshObj("UI palette fixture",new Mesh[]{mesh},false);resources.add(()->{obj.delete();Obj.deleteObjects();});
      var colour=api.makeTexture(null,"UI palette output",W,H,TextureInternalFormat.RGBA_8,TextureDataFormat.RGBA,TextureDataType.UBYTE,false,false,false,false);resources.add(colour::delete);
      var target=api.makeFrameBuffer("UI palette output",new FrameBufferAttachment[]{new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR,colour)});resources.add(target::delete);target.bind();
      glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);
      var global=GameEngine.GPU;global.initVram();resources.add(global.vramTexture15::delete);
      var privateGpu=new Gpu();privateGpu.initVram();resources.add(privateGpu.vramTexture15::delete);upload(privateGpu,tim(2,31<<5,0));
      byte[] reference=new byte[4*4],detail=new byte[16*4*4];
      for(int i=0;i<4;i++){reference[i*4]=2;reference[i*4+1]=(byte)131;}
      for(int y=0;y<4;y++)for(int x=0;x<16;x++){int p=(y*16+x)*4;detail[p]=(byte)(((x%4)+y*4+1)*8);detail[p+3]=0;}
      byte[] originalBytes=new byte[4*4];for(int p=0;p<originalBytes.length;p+=4)originalBytes[p]=(byte)131;
      var original=new Image(originalBytes,4,1);var enhancedImage=new Image(detail,16,4);var control=new Image(reference,4,1);
      legend.game.textures.NativeUiTextures.registerDeferred(new legend.game.textures.NativeUiTextures.Binding(0,0,0,256,4,1),originalBytes.length+detail.length,()->{
        try {return legend.game.textures.NativeUiTextures.Images.class.getConstructor(Image.class,Image.class,Image.class).newInstance(original,enhancedImage,control);}
        catch(NoSuchMethodException oldEngine){return new legend.game.textures.NativeUiTextures.Images(original,enhancedImage);}
        catch(Exception providerFailure){throw new IllegalStateException(providerFailure);}
      });
      resources.add(()->{legend.game.textures.NativeUiTextures.clear();legend.game.textures.NativeUiTextures.beginFrame();});
      var batch=new RenderBatch(GameEngine.RENDERER,FloatBuffer.allocate(32));int changed=0;
      int[] colours={31,31<<5,31<<10,0x4210,0,0x8000,0x801f,0x83e0};
      for(int c:colours)for(int mode=1;mode<=2;mode++) {
        upload(global,tim(2,c,31<<5));
        byte[] nativePixels=draw(api,shader,batch,obj,null,false,mode,"native-"+c+"-pass"+mode);oracle(nativePixels,c,false,mode);
        byte[] enhanced=draw(api,shader,batch,obj,null,true,mode,"detail-"+c+"-pass"+mode);oracle(enhanced,c,true,mode);changed+=differing(nativePixels,enhanced);
      }
      report.put("enhancedChangedPixels",changed);require(changed>0,"Authored 4x shading changes visible pixels");
      upload(global,tim(3,31,31<<5));
      oracle(draw(api,shader,batch,obj,null,true,1,"overwritten-native-index"),31<<5,false,1);
      oracle(draw(api,shader,batch,obj,privateGpu.vramTexture15,true,1,"private-owner-override"),31<<5,false,1);
      upload(global,tim(2,31<<10,0));oracle(draw(api,shader,batch,obj,null,false,1,"pooled-disabled-after-detail"),31<<10,false,1);
      oracle(draw(api,shader,batch,obj,null,true,1,"pooled-reenabled-live-blue"),31<<10,true,1);
      // Native index zero is visible when its live palette colour is nonzero.
      global.uploadData15(new Rect4i(0,0,1,1),new int[]{0});global.uploadData15(new Rect4i(0,256,1,1),new int[]{31});global.updateVramTexture();
      oracle(draw(api,shader,batch,obj,null,true,1,"visible-index-zero-overwrite"),31,false,1);
      upload(global,tim(2,31<<10,31<<5));global.uploadData15(new Rect4i(1,0,1,1),new int[]{0x3333});global.updateVramTexture();fixtureOffset=.25f;
      byte[] fractional=draw(api,shader,batch,obj,null,true,1,"fractional-offset-and-native-neighbor");
      for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
        int p=(y*W+x)*4;
        int expectedBlue=x==W-1?0:Math.round(255*((x+1)%4+(H-1-y)*4+1)*8f/131);
        require(Math.abs((fractional[p+2]&255)-expectedBlue)<=1 && (fractional[p+1]&255)==(x==W-1?255:0) && fractional[p]==0 && (fractional[p+3]&255)==128,"Fractional detail/native neighbor oracle");
      }
      fixtureOffset=0;
      require(cases.length()==38,"All draw controls executed");
    }catch(Throwable e){failure=e;report.put("failure",e.toString());}
    finally {
      if(capabilities){for(int i=resources.size()-1;i>=0;i--)try{resources.get(i).run();}catch(Throwable e){cleanupErrors.put(e.toString());}
        try{Texture.deleteTextures();int error=glGetError();report.put("cleanupGlError",error);if(error!=0)cleanupErrors.put("GL "+error);}catch(Throwable e){cleanupErrors.put(e.toString());}}
      if(context!=0)try{long status=close(context,injected);report.put("nativeCloseStatus",status);if(status!=0)cleanupErrors.put("CGL "+status);}catch(Throwable e){cleanupErrors.put(e.toString());}
      if(capabilities)try{GL.setCapabilities(null);}catch(Throwable e){cleanupErrors.put(e.toString());}
    }
    report.put("passed",failure==null&&cleanupErrors.isEmpty());Files.writeString(Path.of(args[1]),report.toString(2)+"\n");System.out.println(report.toString(2));
    if(failure!=null||!cleanupErrors.isEmpty())throw new AssertionError("UI palette probe failed",failure);
  }
}
