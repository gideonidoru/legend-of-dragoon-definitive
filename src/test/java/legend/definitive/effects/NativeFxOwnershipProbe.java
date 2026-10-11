package legend.definitive.effects;

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

/** Private original synthetic ownership fixture; no game/window or retail inputs. */
public final class NativeFxOwnershipProbe {
  static native long open();
  static native long close(long context);
  static final int W=32,H=16;
  static final List<Runnable> resources=new ArrayList<>();
  static final JSONArray cleanupErrors=new JSONArray(), cases=new JSONArray();
  static QueuedModelBattleTmd reusableQueue;
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
  static Image detail() {
    byte[] b=new byte[8*2*4];
    for(int p=0;p<b.length;p+=4){b[p]=2;b[p+1]=3;b[p+2]=127;b[p+3]=(byte)255;}
    return new Image(b,8,2);
  }
  static void upload(Gpu gpu,Tim tim){gpu.uploadData15(tim.getImageRect(),tim.getImageData());gpu.uploadData15(tim.getClutRect(),tim.getClutData());gpu.updateVramTexture();}
  static float[] identity(int size){var a=new float[size];for(int i=0;i<16;i+=5)a[i]=1;return a;}
  static void ubo(GlApi api,int binding,float[] data){var u=api.makeUniformBuffer(data.length*4L,binding);resources.add(u::delete);var nativeBuffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();nativeBuffer.put(data).flip();u.set(nativeBuffer);}
  static int program(Shader<?> shader)throws Exception{var f=shader.getClass().getDeclaredField("shader");f.setAccessible(true);return f.getInt(shader);}
  static byte[] draw(GlApi api,Shader<ShaderOptionsBattleTmd> shader,RenderBatch batch,MeshObj obj,Texture privateVram,boolean ui,String name)throws Exception {
    if(reusableQueue==null) reusableQueue=new QueuedModelBattleTmd(batch,shader,shader.makeOptions(),FloatBuffer.allocate(128*32));
    var q=reusableQueue;
    var acquire=QueuedModelBattleTmd.class.getDeclaredMethod("acquire",Obj.class,int.class);acquire.setAccessible(true);acquire.invoke(q,obj,0);
    if(privateVram!=null)q.texture(privateVram,1);
    if(ui)q.ui();
    q.useShader(0,0);
    var texture=QueuedModel.class.getDeclaredMethod("useTexture");texture.setAccessible(true);texture.invoke(q);
    int enabled=glGetUniformi(program(shader),glGetUniformLocation(program(shader),"effectArtworkEnabled"));
    glActiveTexture(GL_TEXTURE1);int owner=glGetInteger(GL_TEXTURE_BINDING_2D);glActiveTexture(GL_TEXTURE6);int fx=glGetInteger(GL_TEXTURE_BINDING_2D);glActiveTexture(GL_TEXTURE0);
    glClearColor(0,0,0,0);api.clear(true,false,false);
    var render=QueuedModelBattleTmd.class.getDeclaredMethod("render",Translucency.class,int.class);render.setAccessible(true);render.invoke(q,null,0);
    var data=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);
    var pixels=new byte[data.capacity()];data.get(pixels);
    int index=(H/2*W+W/2)*4;
    int[] center={pixels[index]&255,pixels[index+1]&255,pixels[index+2]&255,pixels[index+3]&255};
    int error=glGetError();require(error==GL_NO_ERROR,"GL draw error "+error);
    Texture selected=privateVram==null?GameEngine.GPU.vramTexture15:privateVram;
    var id=selected.getClass().getDeclaredField("id");id.setAccessible(true);require(owner==id.getInt(selected),"VRAM owner binding mismatch in "+name);
    cases.put(new JSONObject().put("name",name).put("privateVram",privateVram!=null && privateVram!=GameEngine.GPU.vramTexture15).put("ui",ui).put("effectUniform",enabled).put("vramBinding",owner).put("globalDetailBinding",fx).put("center",center).put("glError",error));
    return pixels;
  }
  static int differing(byte[] a,byte[] b){int count=0;for(int p=0;p<a.length;p+=4)if(a[p]!=b[p]||a[p+1]!=b[p+1]||a[p+2]!=b[p+2])count++;return count;}
  public static void main(String[] args)throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("Supply windowless JNI library and JSON report path");
    var report=new JSONObject().put("publishedSource","0af4f997e27f49783180cb2ff1c5a04012467289").put("cases",cases).put("cleanupErrors",cleanupErrors).put("deckCauseConfirmed",false).put("scope","Actual published GPU upload, QueuedModelBattleTmd selection/draw, original battle shaders, synthetic fixed quad/private GPU; no full entity/camera/gameplay replay");
    long context=0;boolean capabilities=false;Throwable failure=null;
    try {
      System.load(args[0]);context=open();require(context!=0,"Windowless CGL context");GL.createCapabilities();capabilities=true;
      report.put("device",glGetString(GL_RENDERER)).put("version",glGetString(GL_VERSION));
      GlApi api=new GlApi();field(GameEngine.RENDERER,"api",api);api.init();api.resize(W,H);api.viewport(0,0,W,H);
      CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
      config(CoreMod.MATERIAL_LIGHTING_CONFIG.get(),false);config(CoreMod.DEFAULT_SURFACE_DETAIL_CONFIG.get(),false);
      Shader<ShaderOptionsBattleTmd> shader=ShaderManager.addShader(RenderEngine.BATTLE_TMD_SHADER);resources.add(shader::delete);
      var transforms=identity(32);for(int i=0;i<16;i++)transforms[16+i]=transforms[i];ubo(api,ShaderUniformBuffer.TRANSFORM,transforms);
      ubo(api,ShaderUniformBuffer.TRANSFORM2,identity(128*20));ubo(api,ShaderUniformBuffer.LIGHTING,new float[128*32]);
      ubo(api,ShaderUniformBuffer.PROJECTION_INFO,new float[]{0,.1f,10,0});ubo(api,ShaderUniformBuffer.SCISSOR,new float[]{0,0,W,H});
      var cluts=new float[1024*4];cluts[0]=-1;ubo(api,ShaderUniformBuffer.CLUT_ANIMATION,cluts);
      var values=new ArrayList<Float>();float[][] corners={{-1,1,0,0},{-1,-1,0,1},{1,1,4,0},{1,-1,4,1}};
      for(int[] t:new int[][]{{0,1,2},{1,3,2}})for(int c:new int[]{t[0],t[0],t[1],t[1],t[2],t[2]}) {
        float[] v=corners[c];for(float f:new float[]{v[0],v[1],.5f,0,0,0,1,v[2],v[3],0,256<<6,1,1,1,1,6})values.add(f);
      }
      float[] vertices=new float[values.size()];for(int i=0;i<vertices.length;i++)vertices[i]=values.get(i);
      Mesh mesh=api.makeMesh("FX owner fixture",VertexOrder.TRIANGLES_ADJACENCY,vertices,vertices.length/16,true,false,null,BufferUsage.STATIC);resources.add(mesh::delete);
      int[] sizes={4,3,2,1,1,4,1};int offset=0;for(int i=0;i<sizes.length;i++){mesh.attribute(i,offset,sizes[i],16);offset+=sizes[i];}
      var obj=new MeshObj("FX owner fixture",new Mesh[]{mesh},false);resources.add(()->{obj.delete();Obj.deleteObjects();});
      var colour=api.makeTexture(null,"FX owner output",W,H,TextureInternalFormat.RGBA_8,TextureDataFormat.RGBA,TextureDataType.UBYTE,false,false,false,false);resources.add(colour::delete);
      var target=api.makeFrameBuffer("FX owner output",new FrameBufferAttachment[]{new FrameBufferAttachment(FrameBufferAttachmentType.COLOUR,colour)});resources.add(target::delete);target.bind();
      glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);
      var global=GameEngine.GPU;global.initVram();resources.add(global.vramTexture15::delete);
      var privateGpu=new Gpu();privateGpu.initVram();resources.add(privateGpu.vramTexture15::delete);
      Tim globalTim=tim(2,31,31<<10),privateTim=tim(2,31<<5,31<<10);
      global.uploadEffectTim(globalTim,event->event.detail=detail());global.updateVramTexture();upload(privateGpu,privateTim);
      var fxField=Gpu.class.getDeclaredField("effectDetailTexture");fxField.setAccessible(true);var fx=(Texture)fxField.get(global);resources.add(fx::delete);
      require(global.hasEffectArtwork(),"Global artwork selected");
      var batch=new RenderBatch(GameEngine.RENDERER,FloatBuffer.allocate(32));
      byte[] globalEnhanced=draw(api,shader,batch,obj,null,false,"global-default-enhanced");
      byte[] privateEnhanced=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-matching-enhanced");
      byte[] explicitGlobal=draw(api,shader,batch,obj,global.vramTexture15,false,"explicit-global-after-private");
      byte[] repeatedPrivate=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-after-explicit-global");
      byte[] repeatedGlobal=draw(api,shader,batch,obj,null,false,"default-global-after-private");
      byte[] privateUi=draw(api,shader,batch,obj,privateGpu.vramTexture15,true,"private-ui");
      byte[] globalUi=draw(api,shader,batch,obj,null,true,"global-ui");
      byte[] afterUi=draw(api,shader,batch,obj,null,false,"global-after-ui-reuse");
      upload(privateGpu,tim(3,31<<5,31<<10));
      byte[] privateMismatchEnhanced=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-mismatching-enhanced");
      global.reselectEffectArtwork(event->event.detail=null);global.updateVramTexture();
      byte[] privateMismatchNative=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-mismatching-disabled-fx");
      upload(privateGpu,privateTim);
      byte[] privateNative=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-matching-disabled-fx");
      byte[] globalNative=draw(api,shader,batch,obj,null,false,"global-disabled-fx");
      global.reselectEffectArtwork(event->event.detail=detail());global.updateVramTexture();
      byte[] reenabledGlobal=draw(api,shader,batch,obj,null,false,"global-reenabled-fx");
      byte[] reenabledPrivate=draw(api,shader,batch,obj,privateGpu.vramTexture15,false,"private-reenabled-fx");
      int globalChange=differing(globalEnhanced,globalNative),privateChange=differing(privateEnhanced,privateNative);
      int staleChange=differing(privateMismatchEnhanced,privateMismatchNative),uiChange=differing(privateUi,privateNative);
      report.put("globalEnhancedChangedPixels",globalChange).put("privateUnrelatedFxChangedPixels",privateChange).put("mismatchingIndexChangedPixels",staleChange).put("uiProtectedChangedPixels",uiChange).put("reproduced",privateChange>0).put("privateOwnerIsolationPassed",privateChange==0);
      require(globalChange==W*H,"Positive global FX control changes all pixels");
      require(privateChange==0,"Private owner must not inherit unrelated global detail");
      require(Arrays.equals(privateEnhanced,privateNative),"Private RGBA coverage and colour remain native");
      require(staleChange==0&&uiChange==0,"Mismatching-index/UI negative controls remain native");
      require(Arrays.equals(explicitGlobal,globalEnhanced) && Arrays.equals(repeatedGlobal,globalEnhanced)
        && Arrays.equals(afterUi,globalEnhanced) && Arrays.equals(reenabledGlobal,globalEnhanced),"Global default/explicit/cached/re-enabled paths retain intended detail");
      require(Arrays.equals(repeatedPrivate,privateNative) && Arrays.equals(reenabledPrivate,privateNative),"Cached private/re-enabled paths stay native");
      require(Arrays.equals(globalUi,globalNative),"Global UI remains native");
      require(privateGpu.getPixel15(0,0)==0x2222 && privateGpu.getPixel15(2,256)==31<<5
        && privateGpu.getPixel15(3,256)==31<<10,"Private native pixels and CLUT remain unchanged");
      require(cases.length()==14,"All ownership and binding transitions executed");

    }catch(Throwable e){failure=e;report.put("failure",e.toString());}
    finally {
      if(capabilities){for(int i=resources.size()-1;i>=0;i--)try{resources.get(i).run();}catch(Throwable e){cleanupErrors.put(e.toString());}
        try{Texture.deleteTextures();int error=glGetError();report.put("cleanupGlError",error);if(error!=0)cleanupErrors.put("GL "+error);}catch(Throwable e){cleanupErrors.put(e.toString());}}
      if(context!=0)try{long status=close(context);report.put("nativeCloseStatus",status);if(status!=0)cleanupErrors.put("CGL "+status);}catch(Throwable e){cleanupErrors.put(e.toString());}
      if(capabilities)try{GL.setCapabilities(null);}catch(Throwable e){cleanupErrors.put(e.toString());}
    }
    report.put("passed",failure==null&&cleanupErrors.isEmpty());Files.writeString(Path.of(args[1]),report.toString(2)+"\n");System.out.println(report.toString(2));
    if(failure!=null||!cleanupErrors.isEmpty())throw new AssertionError("FX owner probe failed",failure);
  }
}
