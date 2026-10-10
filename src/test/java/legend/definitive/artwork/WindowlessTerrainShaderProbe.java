package legend.definitive.artwork;

import legend.definitive.rendering.NativeRendererProbe;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL33C.*;

/** Actual shipping TMD shader and RGBA8 mips on a macOS windowless GL context. */
public final class WindowlessTerrainShaderProbe {
  private static int shader(final int kind,final String source) {
    final int id=glCreateShader(kind);glShaderSource(id,source);glCompileShader(id);
    if(glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE) {final String error=glGetShaderInfoLog(id);glDeleteShader(id);throw new IllegalStateException(error);}return id;
  }
  private static void require(final boolean condition,final String message) {if(!condition) throw new AssertionError(message);}
  private static void options(final int program) {
    glUseProgram(program);glUniform1i(glGetUniformLocation(program,"tex24"),0);glUniform1i(glGetUniformLocation(program,"tex15"),1);glUniform1i(glGetUniformLocation(program,"hdTexture"),1);glUniform1i(glGetUniformLocation(program,"uiLayer"),1);glUniform3f(glGetUniformLocation(program,"recolour"),1,1,1);
    final int block=glGetUniformBlockIndex(program,"scissor");require(block!=GL_INVALID_INDEX,"Scissor block exists");glUniformBlockBinding(program,block,0);
  }
  private static byte[] sample(final int program,final int texture,final int red,final int stp,final boolean hole,final int discardMode) {
    final var image=ByteBuffer.allocateDirect(64*64*4);
    for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
      final boolean patch=x>=32 && x<36 && y>=32 && y<36;
      image.put((byte)(hole?0:patch?red:stp==255?255:0)).put((byte)0).put((byte)0).put((byte)(hole?0:patch?stp:0));
    }
    image.flip();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,64,64,0,GL_RGBA,GL_UNSIGNED_BYTE,image);glGenerateMipmap(GL_TEXTURE_2D);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,3);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST_MIPMAP_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glUniform1i(glGetUniformLocation(program,"fixtureFlags"),discardMode==0?2:10);
    glUniform1f(glGetUniformLocation(program,"discardTranslucency"),discardMode);
    glUniform2f(glGetUniformLocation(program,"fixtureUv"),34.5f/64,34.5f/64);
    glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
    final var pixel=ByteBuffer.allocateDirect(4);glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
    final byte[] result=new byte[4];pixel.get(result);
    if(red==1 && stp==0 && !hole) {
      final var mip=ByteBuffer.allocateDirect(8*8*4);glGetTexImage(GL_TEXTURE_2D,3,GL_RGBA,GL_UNSIGNED_BYTE,mip);
      require(mip.get((4*8+4)*4)==0,"Dark source must actually quantize to zero in the RGBA8 mip fixture");
    }
    return result;
  }
  public static void main(final String[] args) throws Exception {
    if(args.length!=2) throw new IllegalArgumentException("Requires existing windowless JNI library and source root");
    System.load(args[0]);final var open=NativeRendererProbe.class.getDeclaredMethod("open");final var close=NativeRendererProbe.class.getDeclaredMethod("close",long.class);
    open.setAccessible(true);close.setAccessible(true);final long context=(long)open.invoke(null);require(context!=0,"Windowless context is required");
    int program=0,vertex=0,fragment=0,texture=0,target=0,framebuffer=0,vao=0,scissor=0;
    try {
      GL.createCapabilities();final String fs=Files.readString(Path.of(args[1]).resolve("gfx/shaders/tmd.fsh"));
      final int start=fs.indexOf("in GS_OUT {"), end=fs.indexOf("};",start)+2;
      require(start>=0 && end>start,"Shipping shader interface exists");
      final String vs="#version 330 core\n"+fs.substring(start,end).replaceFirst("in GS_OUT","out GS_OUT")+"\n"
        +"uniform vec2 fixtureUv; uniform int fixtureFlags; void main(){ vec2 p=vec2(gl_VertexID==1?3:-1, gl_VertexID==2?3:-1); gl_Position=vec4(p,0,1); vertUv=fixtureUv+p*.0625; vertBpp=3; vertColour=vec4(1); lightingColour=vec3(1); lightingNormal=vec3(0,0,1); worldNormal=vec3(0,0,1); localPosition=vec3(0); worldPosition=vec3(0); localViewDirection=vec3(0,0,1); worldViewDirection=vec3(0,0,1); lightingIndex=0; vertFlags=fixtureFlags; translucency=0; depth=0;depthOffset=0; vertTpage=vec2(0);vertClut=vec2(0);widthMultiplier=1;widthMask=0;indexShift=0;indexMask=0;}";
      vertex=shader(GL_VERTEX_SHADER,vs);fragment=shader(GL_FRAGMENT_SHADER,fs);program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);
      require(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));glUseProgram(program);
      options(program);
      scissor=glGenBuffers();glBindBuffer(GL_UNIFORM_BUFFER,scissor);final var bounds=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());bounds.putFloat(-2).putFloat(-2).putFloat(4).putFloat(4).flip();glBufferData(GL_UNIFORM_BUFFER,bounds,GL_STATIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,0,scissor);
      texture=glGenTextures();target=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
      framebuffer=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Readback framebuffer complete");
      vao=glGenVertexArrays();glBindVertexArray(vao);glViewport(0,0,1,1);glDisable(GL_BLEND);glDisable(GL_DITHER);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_FRAMEBUFFER_SRGB);
      // Samplers of different types must use different texture units, even on an unused branch.
      final int integerTexture=glGenTextures();glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,integerTexture);glTexImage2D(GL_TEXTURE_2D,0,GL_R32UI,1,1,0,GL_RED_INTEGER,GL_UNSIGNED_INT,(ByteBuffer)null);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
      try {
        // Run the same shipping shader with just this repair removed: the oracle
        // must reproduce both the lost dark pixel and the brightened original black.
        final String repair="if(all(equal(source.rgb, vec3(0.0))) || all(equal(texColour.rgb, vec3(0.0)))) texColour.rgb = source.rgb;";
        require(fs.contains(repair),"Source-class repair exists");
        final int controlFragment=shader(GL_FRAGMENT_SHADER,fs.replace(repair,"")), control=glCreateProgram();
        try {
          glAttachShader(control,vertex);glAttachShader(control,controlFragment);glLinkProgram(control);require(glGetProgrami(control,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(control));options(control);
          require(sample(control,texture,1,0,false,0)[3]==0,"Negative control reproduces dark visible pixel disappearing");
          require((sample(control,texture,0,255,false,0)[0]&255)>0,"Negative control reproduces black acquiring neighboring color");
        } finally {glDeleteProgram(control);glDeleteShader(controlFragment);options(program);}
        final byte[] dark=sample(program,texture,1,0,false,0);require((dark[0]&255)==1 && (dark[3]&255)==255,"Dark visible STP=0 must survive mip quantization");
        final byte[] black=sample(program,texture,0,255,false,0);require((black[0]|black[1]|black[2])==0 && (black[3]&255)==255,"Visible black must stay black despite bright neighbors");
        require(sample(program,texture,1,0,true,0)[3]==0,"Nearest hole must remain discarded");
        require((sample(program,texture,1,0,false,1)[3]&255)==128,"Nonblack STP=0 belongs to opaque part of translucent primitive");
        require(sample(program,texture,1,0,false,2)[3]==0,"Nonblack STP=0 is absent from STP pass");
        require(sample(program,texture,0,255,false,1)[3]==0,"STP black is absent from opaque pass");
        require((sample(program,texture,0,255,false,2)[3]&255)==128,"Visible STP black survives STP pass");
        require(glGetError()==GL_NO_ERROR,"Windowless source-class shader check has no GL errors");
        System.out.println("PASS: shipping TMD shader on windowless OpenGL preserves dark nonblack, visible black, nearest holes and both STP passes at actual RGBA8 mip quantization. Removing only the repair reproduces both original defects. No game/UI/native scene acceptance.");
      } finally {glDeleteTextures(integerTexture);}
    } finally {
      if(vao!=0)glDeleteVertexArrays(vao);if(framebuffer!=0)glDeleteFramebuffers(framebuffer);if(scissor!=0)glDeleteBuffers(scissor);if(texture!=0)glDeleteTextures(texture);if(target!=0)glDeleteTextures(target);if(program!=0)glDeleteProgram(program);if(vertex!=0)glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);GL.setCapabilities(null);close.invoke(null,context);
    }
  }
}
