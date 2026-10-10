// Modern presentation verification, AGPL v3; see LICENSE.
// Uses actual shipping shaders in a windowless macOS CGL context.
#define main legacy_probe_main
#include "headless-rendering-probe.c"
#undef main

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
  block(p,"transforms",0,transforms,32);block(p,"transforms2",1,models,2560);block(p,"lighting",2,lights,4096);
  block(p,"projectionInfo",3,projection,4);block(p,"scissor",4,scissor,4);block(p,"clutAnimation",5,cluts,4096);
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
static void assertCoverage(const unsigned char *reference,const unsigned char *candidate) {for(int i=3;i<W*H*4;i+=4)require(reference[i]==candidate[i],"coverage unchanged");}
static int differences(const unsigned char *x,const unsigned char *y) {int n=0;for(int i=0;i<W*H*4;i+=4)if(memcmp(x+i,y+i,3))n++;return n;}

int main(int argc,char **argv) {
  require(argc==3,"usage: modern-probe current-shader-dir committed-baseline-dir");
  CGLPixelFormatAttribute attributes[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAccelerated,(CGLPixelFormatAttribute)0};
  CGLPixelFormatObj format;GLint count;require(CGLChoosePixelFormat(attributes,&format,&count)==kCGLNoError&&count>0,"offscreen format");
  CGLContextObj context;require(CGLCreateContext(format,NULL,&context)==kCGLNoError,"offscreen context");CGLDestroyPixelFormat(format);CGLSetCurrentContext(context);
  printf("Offscreen GPU: %s / %s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
  GLuint standard=program(argv[1],"standard.vsh",NULL,"standard.fsh"),post=program(argv[1],"post.vsh",NULL,"screen.fsh");
  GLuint fbo;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
  GLuint colour=texture(4,W,H,GL_RGBA8,GL_RGBA,NULL),emission=texture(2,W,H,GL_RGBA8,GL_RGBA,NULL),mask=texture(3,W,H,GL_R8,GL_RED,NULL);
  glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,colour,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,emission,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,mask,0);
  GLenum targets[]={GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2};glDrawBuffers(3,targets);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"three-target framebuffer with R8 mask");
  GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);int sizes[]={4,3,2,1,1,4,1},offset=0;
  for(int i=0;i<7;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,GL_FALSE,64,(void*)(long)(offset*4));offset+=sizes[i];}
  glViewport(0,0,W,H);float lights[4096]={0},v[96];uniforms(standard,lights);quad(v,4,0);
  clearTargets();integer(standard,"uiLayer",0);uniform(standard,"emission",.65f);draw(standard,0,v);readTarget(0,a);readTarget(1,b);readTarget(2,c);
  int center=(64*W+64)*4;require(a[center]>0&&b[center]>0&&c[center]==0,"explicit scene emission and empty UI coverage");
  glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDisablei(GL_BLEND,2);integer(standard,"uiLayer",1);uniform(standard,"alpha",.5f);draw(standard,0,v);readTarget(2,c);require(c[center]==255,"translucent UI coverage is binary");readTarget(1,b);require(b[center]<a[center],"UI never emits");
  integer(standard,"uiLayer",0);glColorMaski(2,GL_FALSE,GL_FALSE,GL_FALSE,GL_FALSE);draw(standard,0,v);readTarget(2,c);require(c[center]==255,"translucent scene preserves underlying UI coverage");
  clearTargets();readTarget(1,b);readTarget(2,c);require(b[center]==0&&c[center]==0,"auxiliary clears are zero");
  printf("PASS: MRT emission, R8 interface mask, half-blended UI replacement, scene mask preservation and auxiliary clearing.\n");

  for(int pipeline=0;pipeline<2;pipeline++) {
    const char *vertex=pipeline?"battle_tmd.vsh":"tmd.vsh",*fragment=pipeline?"battle_tmd.fsh":"tmd.fsh";
    GLuint p=program(argv[1],vertex,"tmd.gsh",fragment),old=program(argv[2],vertex,"tmd.gsh",fragment);
    memset(lights,0,sizeof lights);lights[10]=-1;lights[24]=lights[25]=lights[26]=.25f;lights[28]=lights[29]=lights[30]=.1f;
    uniforms(old,lights);quad(v,5,1);clearTargets();draw(old,1,v);readTarget(0,a);
    uniforms(p,lights);clearTargets();draw(p,1,v);readTarget(0,b);require(memcmp(a,b,sizeof a)==0,"new lighting disabled matches committed shader");
    integer(p,"modernLighting",1);integer(p,"materialLighting",1);glUniform2f(glGetUniformLocation(p,"surfaceResponse"),64,.16f);clearTargets();draw(p,1,v);readTarget(0,b);assertCoverage(a,b);require(differences(a,b)>0,"metal highlight responds to authored light");
    glUniform2f(glGetUniformLocation(p,"surfaceResponse"),6,.008f);clearTargets();draw(p,1,v);readTarget(0,c);require(differences(b,c)>0,"cloth and metal differ");
    integer(p,"materialLighting",0);integer(p,"effectLightCount",1);glUniform4f(glGetUniformLocation(p,"effectPositions"),0,0,0,3);glUniform4f(glGetUniformLocation(p,"effectColours"),.22f,.02f,0,1);clearTargets();draw(p,1,v);readTarget(0,b);assertCoverage(a,b);require(b[center]>a[center]&&b[center]-a[center]>b[center+1]-a[center+1],"warm nearby light preserves hue");
    glUniform4f(glGetUniformLocation(p,"effectPositions"),100,100,100,1);clearTargets();draw(p,1,v);readTarget(0,b);require(memcmp(a,b,sizeof a)==0,"distant local light has no contribution");
    integer(p,"effectLightCount",0);integer(p,"materialLighting",1);
    quad(v,5|32|(1<<6)|(255<<9),1);clearTargets();draw(p,1,v);readTarget(0,b);
    quad(v,5|32|(3<<6)|(64<<9),1);clearTargets();draw(p,1,v);readTarget(0,c);assertCoverage(b,c);require(differences(b,c)>0,"per-face cloth and metal responses differ without vertex layout changes");
    quad(v,5|32|(3<<6)|(255<<9),1);clearTargets();draw(p,1,v);readTarget(0,b);require(differences(b,c)>0,"per-face roughness changes highlight shape");
    quad(v,5,1);integer(p,"materialLighting",0);glUniform4f(glGetUniformLocation(p,"environmentDirection"),0,0,-1,.5f);glUniform3f(glGetUniformLocation(p,"environmentColour"),.9f,.3f,.1f);glUniform3f(glGetUniformLocation(p,"environmentAmbient"),.08f,.08f,.12f);
    clearTargets();draw(p,1,v);readTarget(0,b);assertCoverage(a,b);require(b[center]>b[center+1]&&b[center+1]>b[center+2],"environment lighting retains authored warm hue");
    glUniform4f(glGetUniformLocation(p,"environmentDirection"),0,0,-1,1);glUniform3f(glGetUniformLocation(p,"environmentColour"),0,0,0);glUniform3f(glGetUniformLocation(p,"environmentAmbient"),0,0,0);integer(p,"materialLighting",1);
    clearTargets();draw(p,1,v);readTarget(0,b);require(b[center]==0&&b[center+1]==0&&b[center+2]==0,"black environment has no stray material highlights");
    glUniform4f(glGetUniformLocation(p,"environmentDirection"),0,0,-1,0);

    unsigned char white[]={200,200,200,0},normal[]={255,128,128,255},rough[]={255,0,0,255};
    GLuint albedo=texture(0,1,1,GL_RGBA8,GL_RGBA,white),detail=texture(6,1,1,GL_RGBA8,GL_RGBA,normal),roughness=texture(7,1,1,GL_RGBA8,GL_RGBA,rough);
    integer(p,"normalMapTex",6);integer(p,"roughnessMapTex",7);integer(p,"normalMapEnabled",1);uniform(p,"normalMapStrength",0);quad(v,3,1);
    integer(p,"materialLighting",0);clearTargets();draw(p,1,v);readTarget(0,b);uniform(p,"normalMapStrength",.7f);clearTargets();draw(p,1,v);readTarget(0,c);assertCoverage(b,c);require(differences(b,c)>0,"normal map affects HD model lighting without changing coverage");
    integer(p,"normalMapEnabled",0);clearTargets();draw(p,1,v);readTarget(0,c);require(memcmp(b,c,sizeof b)==0,"normal-map reset exactly restores source lighting");
    integer(p,"materialLighting",1);integer(p,"roughnessMapEnabled",0);glUniform2f(glGetUniformLocation(p,"surfaceResponse"),64,.16f);clearTargets();draw(p,1,v);readTarget(0,b);
    integer(p,"roughnessMapEnabled",1);clearTargets();draw(p,1,v);readTarget(0,c);assertCoverage(b,c);require(differences(b,c)>0,"roughness map changes HD highlights without changing coverage");
    integer(p,"roughnessMapEnabled",0);glDeleteTextures(1,&albedo);glDeleteTextures(1,&detail);glDeleteTextures(1,&roughness);
    for(int flags=4;flags<=13;flags+=(flags==4?8:1)){quad(v,flags,1);integer(p,"modernLighting",0);integer(p,"effectLightCount",0);clearTargets();draw(p,1,v);readTarget(0,a);integer(p,"modernLighting",1);integer(p,"materialLighting",1);integer(p,"effectLightCount",1);glUniform4f(glGetUniformLocation(p,"effectPositions"),0,0,0,3);clearTargets();draw(p,1,v);readTarget(0,b);require(memcmp(a,b,sizeof a)==0,"unlit/translucent surfaces bypass modern lighting");}
    integer(p,"modernLighting",0);integer(p,"materialLighting",0);integer(p,"effectLightCount",0);quad(v,5,1);clearTargets();draw(p,1,v);readTarget(0,b);uniforms(old,lights);clearTargets();draw(old,1,v);readTarget(0,a);require(memcmp(a,b,sizeof a)==0,"lighting reset restores committed output");
    glDeleteProgram(p);glDeleteProgram(old);
  }
  printf("PASS: field and battle original bypass, per-face materials/roughness, HD normal/roughness maps, authored environment hue/black/reset, colored point lighting, radius falloff, unlit/translucent bypass and exact reset.\n");

  // HD RGB filtering must never average alpha/STP or fill an empty source texel.
  uniforms(standard,lights);integer(standard,"uiLayer",0);uniform(standard,"emission",0);uniform(standard,"alpha",-1);quad(v,6,0);for(int i=0;i<6;i++)v[i*16+11]=v[i*16+12]=v[i*16+13]=1;
  unsigned char rgba[]={0,0,0,0, 255,0,0,255, 0,255,0,0, 0,0,255,255};GLuint hd=texture(0,2,2,GL_RGBA8,GL_RGBA,rgba);
  integer(standard,"hdTexture",0);clearTargets();draw(standard,0,v);readTarget(0,a);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glGenerateMipmap(GL_TEXTURE_2D);
  integer(standard,"hdTexture",1);clearTargets();draw(standard,0,v);readTarget(0,b);assertCoverage(a,b);require(differences(a,b)>0,"HD RGB is filtered");
  uniform(standard,"discardTranslucency",1);for(int i=0;i<6;i++)v[i*16+15]=14;integer(standard,"hdTexture",1);clearTargets();draw(standard,0,v);readTarget(0,b);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);integer(standard,"hdTexture",0);clearTargets();draw(standard,0,v);readTarget(0,a);assertCoverage(a,b);
  printf("PASS: mipmapped HD RGB filtering preserves source transparency and STP partition coverage.\n");

  // Reuse scene MRT textures as post inputs, with a distinct output target.
  GLuint output=texture(5,W,H,GL_RGBA8,GL_RGBA,NULL);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,0,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_TEXTURE_2D,0,0);glDrawBuffers(1,targets);glReadBuffer(GL_COLOR_ATTACHMENT0);
  float q[]={-1,-1,0,0,1,-1,1,0,1,1,1,1,-1,-1,0,0,1,1,1,1,-1,1,0,1};GLuint qvao;glGenVertexArrays(1,&qvao);glBindVertexArray(qvao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,sizeof q,q,GL_STREAM_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,16,0);glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,16,(void*)8);
  unsigned char zeros[W*H*4]={0},coverage[W*H]={0},glow[W*H*4]={0};
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;input[i]=input[i+1]=input[i+2]=(x>y/2+25?130:100);input[i+3]=255;if(x>=20&&x<90&&y>=35&&y<50)coverage[y*W+x]=255;}
  glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,emission);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,zeros);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
  glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,mask);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RED,GL_UNSIGNED_BYTE,coverage);
  glUseProgram(post);integer(post,"sceneEmission",2);integer(post,"interfaceCoverage",3);integer(post,"protectInterface",1);uniform(post,"sceneBloom",.25f);screenDraw(post,colour,0,a);require(memcmp(input,a,sizeof a)==0,"bright non-emissive scene has no bloom");
  for(int y=42;y<46;y++)for(int x=49;x<53;x++)glow[(y*W+x)*4]=glow[(y*W+x)*4+1]=255;
  for(int y=72;y<76;y++)for(int x=49;x<53;x++)glow[(y*W+x)*4]=255;
  glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,emission);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,glow);
  uniform(post,"sharpening",.2f);screenDraw(post,colour,.5f,b);screenDraw(post,colour,.5f,c);require(memcmp(b,c,sizeof b)==0,"modern effects are deterministic");int changed=0;
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;if(x>=19&&x<=90&&y>=34&&y<=50)require(memcmp(a+i,b+i,4)==0,"UI and its one-pixel fringe remain exact");else if(memcmp(a+i,b+i,3))changed++;require(b[i+3]==255,"screen alpha remains opaque");}
  require(changed>0,"scene effects change unprotected scene pixels");
  uniform(post,"sceneBloom",0);screenDraw(post,colour,0,b);for(int i=0;i<W*H*4;i+=4)require(b[i]>=100&&b[i]<=130,"sharpening cannot ring outside neighborhood range");
  uniform(post,"sharpening",0);screenDraw(post,colour,0,b);require(memcmp(a,b,sizeof a)==0,"all scene-effect strengths at zero restore baseline");
  printf("PASS: selective bloom, crisp UI with protected fringe, deterministic scene effects, bounded sharpening and exact zero-strength reset.\n");
  require(glGetError()==GL_NO_ERROR,"final GPU state");CGLSetCurrentContext(NULL);CGLDestroyContext(context);return 0;
}
