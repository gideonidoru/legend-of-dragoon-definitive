// TMD camera-clipping regression, AGPL v3; see LICENSE.
// clang -framework OpenGL scripts/headless-tmd-clipping-probe.c -o /tmp/tmd-clipping-probe
// /tmp/tmd-clipping-probe gfx/shaders [unchanged-reference-shader-directory]
// Uses actual shipping shaders in a windowless macOS CGL context.
#define main legacy_probe_main
#include "headless-rendering-probe.c"
#undef main

// Reuse six uniform buffers across cases; shader block layouts remain production-defined.
static GLuint uniformBuffers[6];
static void uploadBlock(GLuint p,const char *name,int binding,float *values,int count) {
  GLuint index=glGetUniformBlockIndex(p,name);if(index==GL_INVALID_INDEX)return;
  GLint bytes;glGetActiveUniformBlockiv(p,index,GL_UNIFORM_BLOCK_DATA_SIZE,&bytes);require(bytes<=count*4,"uniform block size");
  if(!uniformBuffers[binding])glGenBuffers(1,&uniformBuffers[binding]);
  glBindBuffer(GL_UNIFORM_BUFFER,uniformBuffers[binding]);glBufferData(GL_UNIFORM_BUFFER,count*4,values,GL_STREAM_DRAW);
  glBindBufferBase(GL_UNIFORM_BUFFER,binding,uniformBuffers[binding]);glUniformBlockBinding(p,index,binding);
}
static GLuint texture(int unit, int width, int height, GLint format, GLenum dataFormat, const void *data) {
  GLuint id;glGenTextures(1,&id);glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,id);
  glTexImage2D(GL_TEXTURE_2D,0,format,width,height,0,dataFormat,GL_UNSIGNED_BYTE,data);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;
}
static void readTarget(int target,unsigned char *pixels) {glReadBuffer(GL_COLOR_ATTACHMENT0+target);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);}
static void uniforms(GLuint p,float *lights) {
  float transforms[32]={0},models[128*20]={0},projection[]={0,.1f,10,0},scissor[]={0,0,W,H},cluts[4096]={-1};
  for(int i=0;i<4;i++)transforms[i*5]=transforms[16+i*5]=models[i*5]=1;
  uploadBlock(p,"transforms",0,transforms,32);uploadBlock(p,"transforms2",1,models,2560);uploadBlock(p,"lighting",2,lights,4096);
  uploadBlock(p,"projectionInfo",3,projection,4);uploadBlock(p,"scissor",4,scissor,4);uploadBlock(p,"clutAnimation",5,cluts,4096);
  glUseProgram(p);integer(p,"tex15",1);integer(p,"tex24",0);glUniform3f(glGetUniformLocation(p,"recolour"),1,1,1);glUniform3f(glGetUniformLocation(p,"battleColour"),1,1,1);
  uniform(p,"alpha",-1);uniform(p,"discardTranslucency",0);integer(p,"smoothLighting",1);
}
static void draw(GLuint p,int adjacency,const float *vertices) {
  glUseProgram(p);glBufferData(GL_ARRAY_BUFFER,6*16*4,vertices,GL_STREAM_DRAW);glDrawArrays(adjacency?GL_TRIANGLES_ADJACENCY:GL_TRIANGLES,0,6);
  require(glGetError()==GL_NO_ERROR,"modern draw has no GL errors");
}
static void quad(float *v,int flags,int adjacency) {
  float corners[6][2]={{-.9f,-.9f},{.9f,-.9f},{.9f,.9f},{-.9f,-.9f},{.9f,.9f},{-.9f,.9f}};
  if(adjacency){float q[6][2]={{-.9f,-.9f},{-.9f,-.9f},{.9f,-.9f},{.9f,-.9f},{0,.9f},{0,.9f}};memcpy(corners,q,sizeof q);}
  memset(v,0,6*16*4);
  for(int i=0;i<6;i++){v[i*16]=corners[i][0];v[i*16+1]=corners[i][1];v[i*16+2]=.5f;v[i*16+6]=-1;v[i*16+7]=(corners[i][0]+1)/2;v[i*16+8]=(corners[i][1]+1)/2;v[i*16+9]=384;for(int j=11;j<15;j++)v[i*16+j]=.5f;v[i*16+15]=flags;}
}
static void clearTargets(void) {
  float zero[]={0,0,0,0};glDisable(GL_BLEND);glColorMaski(2,GL_TRUE,GL_TRUE,GL_TRUE,GL_TRUE);
  for(int i=0;i<3;i++)glClearBufferfv(GL_COLOR,i,zero);
}
static int coverage(const unsigned char *pixels) {int n=0;for(int i=3;i<W*H*4;i+=4)if(pixels[i])n++;return n;}
int main(int argc,char **argv) {
 require(argc==2 || argc==3,"usage: tmd-clipping-probe shader-directory [reference-shader-directory]");
 CGLPixelFormatAttribute attributes[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAccelerated,(CGLPixelFormatAttribute)0};
 CGLPixelFormatObj format;GLint count;require(CGLChoosePixelFormat(attributes,&format,&count)==kCGLNoError&&count>0,"offscreen format");
 CGLContextObj context;require(CGLCreateContext(format,NULL,&context)==kCGLNoError,"offscreen context");CGLDestroyPixelFormat(format);CGLSetCurrentContext(context);
 printf("GPU %s / %s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
 GLuint fbo;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
 GLuint colour=texture(4,W,H,GL_RGBA8,GL_RGBA,NULL),emission=texture(2,W,H,GL_RGBA8,GL_RGBA,NULL),mask=texture(3,W,H,GL_R8,GL_RED,NULL);
 glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,colour,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,emission,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,mask,0);
 GLenum targets[]={GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2};glDrawBuffers(3,targets);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"framebuffer");
 GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);int sizes[]={4,3,2,1,1,4,1},offset=0;
 for(int i=0;i<7;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,GL_FALSE,64,(void*)(long)(offset*4));offset+=sizes[i];}
 glViewport(0,0,W,H);glDisable(GL_CULL_FACE);glDisable(GL_DEPTH_TEST);
 unsigned char opaque[]={255,80,40,0,255,80,40,0,255,80,40,0,255,80,40,0};texture(0,2,2,GL_RGBA8,GL_RGBA,opaque);
 // Native indexed data: red CLUT entry 1 and a 32x32 page of index 1.
 unsigned *words=calloc(1024*512,sizeof(unsigned));require(words!=NULL,"VRAM allocation");words[1]=0x1f;
 for(int y=256;y<288;y++)for(int x=256;x<264;x++)words[y*1024+x]=0x1111;
 GLuint vram;glGenTextures(1,&vram);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,vram);
 glTexImage2D(GL_TEXTURE_2D,0,GL_R32UI,1024,512,0,GL_RED_INTEGER,GL_UNSIGNED_INT,words);free(words);
 glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
 int cases=0,failed=0;
 const char *pipelines[]={"tmd","battle_tmd"};
 const char *states[]={"outside-old-threshold","one-close-corner","all-close-corners","adjacent-quad-close","cross-hardware-near","behind-hardware-near"};
 for(int pipe=0;pipe<2;pipe++) {
  char vertex[64],fragment[64];snprintf(vertex,sizeof vertex,"%s.vsh",pipelines[pipe]);snprintf(fragment,sizeof fragment,"%s.fsh",pipelines[pipe]);
  GLuint programs[2]={program(argv[1],vertex,"tmd.gsh",fragment),0};
  if(argc==3)programs[1]=program(argv[2],vertex,"tmd.gsh",fragment);
  for(int mode=0;mode<3;mode++)for(int indexed=0;indexed<2;indexed++)for(int state=0;state<6;state++) {
   float v[96];quad(v,state==3?22:6,1);
   for(int i=0;i<6;i++) {
    v[i*16+9]=indexed?20:384;
    if(indexed){v[i*16+7]*=31;v[i*16+8]*=31;}
    v[i*16+2]=state==0?1.5f:state==1?(i==0?.75f:1.5f):state==2?.75f:state==3?(i==1?.75f:1.5f):state==4?(i==0?.05f:1.5f):.05f;
   }
   for(int version=0;version<(argc==3?2:1);version++) {
    GLuint p=programs[version];float lights[4096]={0};uniforms(p,lights);
    integer(p,"effectDetailTex",1);integer(p,"usePs1Depth",0);integer(p,"uiLayer",0);integer(p,"modernLighting",0);
    float transforms[32]={0};for(int i=0;i<4;i++)transforms[i*5]=1;
    float near=.1f,far=1000;
    if(mode==2) {
     transforms[16]=1;transforms[21]=1;transforms[26]=(far+near)/(far-near);transforms[27]=1;transforms[30]=-2*far*near/(far-near);
    } else {
     transforms[16]=mode==1?.5f:1;transforms[21]=mode==1?.5f:1;transforms[26]=2/far;transforms[30]=-1;transforms[31]=1;
    }
    uploadBlock(p,"transforms",0,transforms,32);float projection[]={0,2,.001f,(float)mode};uploadBlock(p,"projectionInfo",3,projection,4);
    clearTargets();draw(p,1,v);readTarget(0,version?b:a);
   }
   int visible=coverage(a),expected=mode==1?state==0:!(mode==2&&state==5);
   int parityRequired=argc==3&&(state==0||mode==1||(mode==2&&state==5));
   int parity=!parityRequired||memcmp(a,b,sizeof a)==0;
   int pass=(visible>0)==expected&&parity;cases++;if(!pass)failed++;
   printf("RESULT pipeline=%s mode=%d source=%s state=%s coverage=%d expectedVisible=%d referenceParity=%s verdict=%s\n",pipelines[pipe],mode,indexed?"indexed4":"HD-RGB",states[state],visible,expected,parityRequired?(parity?"PASS":"FAIL"):"not-required",pass?"PASS":"FAIL");
  }
  glDeleteProgram(programs[0]);if(programs[1])glDeleteProgram(programs[1]);
 }
 printf("SUMMARY cases=%d failed=%d\n",cases,failed);
 glDeleteBuffers(1,&vbo);glDeleteVertexArrays(1,&vao);glDeleteTextures(1,&vram);glDeleteTextures(1,&colour);glDeleteTextures(1,&emission);glDeleteTextures(1,&mask);glDeleteFramebuffers(1,&fbo);
 glDeleteBuffers(6,uniformBuffers);
 CGLSetCurrentContext(NULL);CGLDestroyContext(context);return failed?1:0;
}
