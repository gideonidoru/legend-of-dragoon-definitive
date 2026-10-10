// Definitive renderer verification, AGPL v3; see LICENSE.
// macOS CGL context only: no window, SDL, game bootstrap or desktop interaction.
#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

static void require(int ok, const char *message) { if(!ok) { fprintf(stderr,"FAIL: %s\n",message); exit(1); } }
static char *source(const char *root, const char *name) {
  char path[4096]; snprintf(path,sizeof path,"%s/%s",root,name);
  FILE *f=fopen(path,"rb"); require(f!=NULL,path); fseek(f,0,SEEK_END); long n=ftell(f); rewind(f);
  require(n>0&&n<131072,"bounded shader"); char *s=calloc(n+1,1); require(fread(s,1,n,f)==n,"read shader"); fclose(f); return s;
}
static GLuint stage(const char *root, const char *name, GLenum kind) {
  char *s=source(root,name); GLuint id=glCreateShader(kind); glShaderSource(id,1,(const char**)&s,NULL); glCompileShader(id); free(s);
  GLint ok; glGetShaderiv(id,GL_COMPILE_STATUS,&ok); if(!ok) {char log[8192]; glGetShaderInfoLog(id,sizeof log,NULL,log); fprintf(stderr,"%s: %s\n",name,log);} require(ok,"shader compilation"); return id;
}
static GLuint program(const char *root, const char *v, const char *g, const char *f) {
  GLuint p=glCreateProgram(),vs=stage(root,v,GL_VERTEX_SHADER),fs=stage(root,f,GL_FRAGMENT_SHADER),gs=0;
  glAttachShader(p,vs);glAttachShader(p,fs);if(g){gs=stage(root,g,GL_GEOMETRY_SHADER);glAttachShader(p,gs);}glLinkProgram(p);
  GLint ok;glGetProgramiv(p,GL_LINK_STATUS,&ok);if(!ok){char log[8192];glGetProgramInfoLog(p,sizeof log,NULL,log);fprintf(stderr,"%s: %s\n",f,log);}require(ok,"shader link");
  glDeleteShader(vs);glDeleteShader(fs);if(g)glDeleteShader(gs);
  // Set sampler units before first use so incompatible sampler types never share unit zero.
  GLint loc=glGetUniformLocation(p,"tex15");if(loc>=0)glProgramUniform1i(p,loc,1);
  return p;
}
static void uniform(GLuint p, const char *name, float value) { GLint loc=glGetUniformLocation(p,name); if(loc>=0)glUniform1f(loc,value); }
static void integer(GLuint p,const char *name,int value) { GLint loc=glGetUniformLocation(p,name);if(loc>=0)glUniform1i(loc,value); }
#define W 128
#define H 128
static unsigned char input[W*H*4],a[W*H*4],b[W*H*4],c[W*H*4];
static void screenDraw(GLuint p, GLuint tex, float strength, unsigned char *out) {
  glUseProgram(p);integer(p,"screen",0);integer(p,"enableCrt",0);uniform(p,"edgeSmoothing",strength);
  glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,tex);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,input);
  glDrawArrays(GL_TRIANGLES,0,6);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,out);
  require(glGetError()==GL_NO_ERROR,"screen draw");
}

static void block(GLuint p,const char *name,int binding,float *values,int count) {
  GLuint index=glGetUniformBlockIndex(p,name);if(index==GL_INVALID_INDEX)return;
  GLint bytes;glGetActiveUniformBlockiv(p,index,GL_UNIFORM_BLOCK_DATA_SIZE,&bytes);require(bytes<=count*4,"uniform block size");
  GLuint id;glGenBuffers(1,&id);glBindBuffer(GL_UNIFORM_BUFFER,id);glBufferData(GL_UNIFORM_BUFFER,count*4,values,GL_STATIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,binding,id);glUniformBlockBinding(p,index,binding);
}
static unsigned read32(FILE *f) { unsigned char q[4];require(fread(q,1,4,f)==4,"scene read");return (unsigned)q[0]<<24|(unsigned)q[1]<<16|(unsigned)q[2]<<8|q[3]; }
static float readFloat(FILE *f) { unsigned bits=read32(f);float x;memcpy(&x,&bits,4);require(isfinite(x)&&fabsf(x)<1000000,"finite scene float");return x; }
static void sceneTint(GLuint p,int enabled) {
  glUseProgram(p);integer(p,"sceneLighting",enabled);
  GLint loc=glGetUniformLocation(p,"sceneKeyTint");if(loc>=0)glUniform3f(loc,1.03f,1.03f,1.03f);
  loc=glGetUniformLocation(p,"sceneAmbientTint");if(loc>=0)glUniform3f(loc,.98f,.98f,.98f);
}
static void modelDraw(GLuint p,int smooth,int flags,int n,float *v,unsigned char *out,int w,int h) {
  glUseProgram(p);integer(p,"smoothLighting",smooth);integer(p,"tex24",0);integer(p,"tex15",1);integer(p,"ctmdFlags",0);integer(p,"usePs1Depth",0);
  glUniform3f(glGetUniformLocation(p,"recolour"),1,1,1);glUniform3f(glGetUniformLocation(p,"battleColour"),1,1,1);
  if(flags>=0)for(int i=0;i<n;i++)v[i*16+15]=flags;
  glBufferData(GL_ARRAY_BUFFER,n*16*4,v,GL_STREAM_DRAW);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDrawArrays(GL_TRIANGLES_ADJACENCY,0,n);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,out);require(glGetError()==GL_NO_ERROR,"model draw");
}
static void scene(GLuint p,GLuint old,GLuint post,const char *path,const char *output) {
  FILE *f=fopen(path,"rb");require(f!=NULL,"scene file");require(read32(f)==0x44475343&&read32(f)==1,"scene version");int w=read32(f),h=read32(f),n=read32(f),tw=read32(f),th=read32(f),size=read32(f);
  require(w>0&&w<=1280&&h>0&&h<=800&&n>=6&&n<=200000&&n%6==0&&tw>0&&tw<=4096&&th>0&&th<=4096&&size==tw*th*4&&size<=24*1024*1024,"scene bounds");
  float transforms[32]={0},models[128*20]={0},lights[128*32]={0},cluts[1024*4]={-1},projection[]={0,.1,10,0},scissor[]={0,0,w,h};
  for(int i=0;i<4;i++)transforms[i*5]=models[i*5]=1;
  for(int i=0;i<16;i++)transforms[16+i]=readFloat(f);for(int i=0;i<32;i++)lights[i]=readFloat(f);
  unsigned short *vram=malloc(1024*512*2);for(int i=0;i<1024*512;i++){unsigned char q[2];require(fread(q,1,2,f)==2,"vram");vram[i]=(unsigned)q[0]<<8|q[1];}
  float *v=malloc(n*16*4);for(int i=0;i<n*16;i++)v[i]=readFloat(f);unsigned char *rgba=malloc(size);require(fread(rgba,1,size,f)==size&&fgetc(f)==EOF,"texture length");fclose(f);
  GLuint programs[]={p,old};for(int i=0;i<2;i++){block(programs[i],"transforms",0,transforms,32);block(programs[i],"transforms2",1,models,128*20);block(programs[i],"lighting",2,lights,128*32);block(programs[i],"clutAnimation",3,cluts,4096);block(programs[i],"projectionInfo",4,projection,4);block(programs[i],"scissor",5,scissor,4);}
  GLuint tex,dest,fbo,depth,vao,vbo;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenTextures(1,&dest);glBindTexture(GL_TEXTURE_2D,dest);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,dest,0);
  glGenRenderbuffers(1,&depth);glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,w,h);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"model target");
  glGenTextures(1,&tex);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,tw,th,0,GL_RGBA,GL_UNSIGNED_BYTE,rgba);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
  glGenTextures(1,&tex);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_R16UI,1024,512,0,GL_RED_INTEGER,GL_UNSIGNED_SHORT,vram);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
  glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);int sizes[]={4,3,2,1,1,4,1},offset=0;for(int i=0;i<7;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,GL_FALSE,64,(void*)(long)(offset*4));offset+=sizes[i];}
  glViewport(0,0,w,h);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);
  sceneTint(p,0);
  int bytes=w*h*4;unsigned char *base=malloc(bytes),*candidate=malloc(bytes),*off=malloc(bytes);modelDraw(old,0,-1,n,v,base,w,h);modelDraw(p,0,-1,n,v,off,w,h);require(memcmp(base,off,bytes)==0,"disabled lighting matches original");modelDraw(p,1,-1,n,v,candidate,w,h);
  int changed=0,coverage=0,visible=0;for(int i=0;i<bytes;i+=4){if(base[i+3])visible++;if(base[i+3]!=candidate[i+3])coverage++;if(memcmp(base+i,candidate+i,3))changed++;}require(coverage==0&&visible>0,"lit geometry preserves coverage");
  char name[4096];snprintf(name,sizeof name,"%s-off.rgba",output);f=fopen(name,"wb");require(f!=NULL,"off output");fwrite(base,1,bytes,f);fclose(f);snprintf(name,sizeof name,"%s-on.rgba",output);f=fopen(name,"wb");require(f!=NULL,"on output");fwrite(candidate,1,bytes,f);fclose(f);

  unsigned char *smoothReference=malloc(bytes);memcpy(smoothReference,candidate,bytes);
  // Record the whole-game lighting response and require identical geometry/coverage.
  sceneTint(p,1);modelDraw(p,1,-1,n,v,candidate,w,h);
  int profileChanged=0;for(int i=0;i<bytes;i+=4){require(candidate[i+3]==base[i+3],"scene tint preserves coverage");if(memcmp(candidate+i,base+i,3))profileChanged++;}
  require(profileChanged>0,"scene profile changes lit surface color");
  snprintf(name,sizeof name,"%s-enhanced.rgba",output);f=fopen(name,"wb");require(f!=NULL,"enhanced output");fwrite(candidate,1,bytes,f);fclose(f);
  sceneTint(p,0);modelDraw(p,1,-1,n,v,off,w,h);require(memcmp(smoothReference,off,bytes)==0,"profile reset restores original scene lighting");free(smoothReference);sceneTint(p,1);modelDraw(p,0,-1,n,v,candidate,w,h);
  snprintf(name,sizeof name,"%s-enhanced-vertex.rgba",output);f=fopen(name,"wb");require(f!=NULL,"enhanced vertex output");fwrite(candidate,1,bytes,f);fclose(f);
  sceneTint(p,1);modelDraw(p,1,-1,n,v,candidate,w,h);
  // Exercise authored warm/cool/dim/black lighting, in each model pipeline.
  float authored[128*32];memcpy(authored,lights,sizeof lights);
  float environments[][3]={{1,.55f,.22f},{.25f,.55f,1},{.06f,.04f,.08f},{0,0,0}};
  for(int environment=0;environment<4;environment++) {
    memcpy(lights,authored,sizeof lights);
    for(int column=0;column<3;column++)for(int channel=0;channel<3;channel++)lights[16+column*4+channel]*=environments[environment][channel];
    for(int channel=0;channel<3;channel++)lights[28+channel]*=environments[environment][channel];
    block(p,"lighting",2,lights,128*32);
    sceneTint(p,0);modelDraw(p,1,-1,n,v,off,w,h);
    sceneTint(p,1);modelDraw(p,1,-1,n,v,candidate,w,h);
    for(int i=0;i<bytes;i+=4) {
      require(off[i+3]==candidate[i+3],"warm/cool/dim light coverage unchanged");
      if(environment==3)require(candidate[i]==0&&candidate[i+1]==0&&candidate[i+2]==0,"zero authored lights remain black");
    }
    sceneTint(p,0);modelDraw(p,1,-1,n,v,candidate,w,h);require(memcmp(off,candidate,bytes)==0,"lighting toggle resets in every environment");
  }
  memcpy(lights,authored,sizeof lights);block(p,"lighting",2,lights,128*32);
  sceneTint(p,1);modelDraw(p,1,-1,n,v,candidate,w,h);
  printf("PASS: warm, cool, dim and black authored lights, reset and coverage in this pipeline.\n");
  // Feed the lighting candidate through the exact screen pass at moderate strength.
  glDisable(GL_DEPTH_TEST);glUseProgram(post);integer(post,"screen",0);integer(post,"enableCrt",0);uniform(post,"edgeSmoothing",.5f);
  glGenTextures(1,&tex);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,candidate);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
  float quad[]={-1,-1,0,0,1,-1,1,0,1,1,1,1,-1,-1,0,0,1,1,1,1,-1,1,0,1};
  GLuint qvao,qvbo;glGenVertexArrays(1,&qvao);glBindVertexArray(qvao);glGenBuffers(1,&qvbo);glBindBuffer(GL_ARRAY_BUFFER,qvbo);glBufferData(GL_ARRAY_BUFFER,sizeof quad,quad,GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,16,0);glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,16,(void*)8);glDrawArrays(GL_TRIANGLES,0,6);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,off);require(glGetError()==GL_NO_ERROR,"model screen pass");
  snprintf(name,sizeof name,"%s-smooth.rgba",output);f=fopen(name,"wb");require(f!=NULL,"edge output");fwrite(off,1,bytes,f);fclose(f);
  glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glEnable(GL_DEPTH_TEST);
  int flags[]={4,12,13};for(int i=0;i<3;i++){sceneTint(p,0);modelDraw(p,0,flags[i],n,v,base,w,h);sceneTint(p,1);modelDraw(p,1,flags[i],n,v,candidate,w,h);require(memcmp(base,candidate,bytes)==0,"unlit/translucent bypass matches exactly");}
  printf("PASS: scene %dx%d; %d visible pixels, %d lighting changes, exact original bypass and unlit/translucent bypass, identical coverage; %d scene-profile color changes.\n",w,h,visible,changed,profileChanged);
  free(vram);free(v);free(rgba);free(base);free(candidate);free(off);
}

static void shadowDraw(const char *root,const char *path,const char *output,int pipeline) {
  glUseProgram(0);glActiveTexture(GL_TEXTURE0);
  FILE *f=fopen(path,"rb");require(f!=NULL,"shadow mesh");int count=read32(f);require(count>=12&&count<=4096&&count%12==0,"shadow mesh bounds");int n=count/4;float *v=calloc(n*16,sizeof(float));
  for(int i=0;i<n;i++){float x=readFloat(f),y=readFloat(f),z=readFloat(f),darkness=readFloat(f);require(y==0&&fabsf(x)<=32&&fabsf(z)<=32&&darkness>=0&&darkness<=.5,"shadow mesh data");v[i*16]=x/40;v[i*16+1]=z/100;v[i*16+2]=.2;v[i*16+11]=v[i*16+12]=v[i*16+13]=darkness;v[i*16+14]=1;v[i*16+15]=12;}require(fgetc(f)==EOF,"shadow mesh length");fclose(f);
  if(pipeline) {
    float *adjacent=malloc(n*2*16*sizeof(float));
    for(int i=0;i<n;i++){memcpy(adjacent+i*32,v+i*16,64);memcpy(adjacent+i*32+16,v+i*16,64);}
    free(v);v=adjacent;n*=2;
  }
  GLuint p=program(root,pipeline==2?"battle_tmd.vsh":pipeline==1?"tmd.vsh":"standard.vsh",pipeline?"tmd.gsh":NULL,pipeline==2?"battle_tmd.fsh":pipeline==1?"tmd.fsh":"standard.fsh");float transforms[32]={0},models[128*20]={0},lights[128*32]={0},cluts[4096]={-1},projection[]={0,.1,10,0},scissor[]={0,0,256,256};for(int i=0;i<4;i++)transforms[i*5]=transforms[16+i*5]=models[i*5]=1;
  block(p,"transforms",0,transforms,32);block(p,"transforms2",1,models,2560);block(p,"lighting",2,lights,4096);block(p,"clutAnimation",3,cluts,4096);block(p,"projectionInfo",4,projection,4);block(p,"scissor",5,scissor,4);
  GLuint fbo,tex,vao,vbo;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenTextures(1,&tex);glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,256,256,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"shadow target");
  glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,n*64,v,GL_STATIC_DRAW);int sizes[]={4,3,2,1,1,4,1},offset=0;for(int i=0;i<7;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,GL_FALSE,64,(void*)(long)(offset*4));offset+=sizes[i];}
  GLuint dummy;unsigned char white[4]={255,255,255,255};unsigned short index=0;glGenTextures(1,&dummy);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,dummy);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,white);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glGenTextures(1,&dummy);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,dummy);glTexImage2D(GL_TEXTURE_2D,0,GL_R16UI,1,1,0,GL_RED_INTEGER,GL_UNSIGNED_SHORT,&index);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
  glUseProgram(p);integer(p,"tex24",0);integer(p,"tex15",1);uniform(p,"alpha",-1);uniform(p,"translucency",2);integer(p,"tmdTranslucency",2);integer(p,"ctmdFlags",0);glUniform3f(glGetUniformLocation(p,"recolour"),1,1,1);
  glViewport(0,0,256,256);glDisable(GL_DEPTH_TEST);glClearColor(.62,.64,.67,1);glClear(GL_COLOR_BUFFER_BIT);glEnable(GL_BLEND);glBlendEquation(GL_FUNC_REVERSE_SUBTRACT);glBlendFunc(GL_ONE,GL_ONE);glDrawArrays(pipeline?GL_TRIANGLES_ADJACENCY:GL_TRIANGLES,0,n);glDisable(GL_BLEND);glBlendEquation(GL_FUNC_ADD);
  unsigned char *pixels=malloc(256*256*4);glReadPixels(0,0,256,256,GL_RGBA,GL_UNSIGNED_BYTE,pixels);require(glGetError()==GL_NO_ERROR,"subtractive shadow draw");int changed=0;for(int i=0;i<256*256;i++){if(pixels[i*4]<150)changed++;}require(changed>1000&&changed<256*256/2,"shadow coverage bounded and nonempty");f=fopen(output,"wb");require(f!=NULL,"shadow output");fwrite(pixels,1,256*256*4,f);fclose(f);printf("PASS: shadow %d triangles through pipeline %d/subtractive blend, %d darkened pixels.\n",n/(pipeline?6:3),pipeline,changed);free(v);free(pixels);glDeleteProgram(p);
}
int main(int argc,char **argv) {
  require(argc==3||argc==5||argc==6||argc==7,"usage: probe current-shader-dir baseline-shader-dir");
  CGLPixelFormatAttribute attributes[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAccelerated,0};
  CGLPixelFormatObj format;GLint count;require(CGLChoosePixelFormat(attributes,&format,&count)==kCGLNoError&&count>0,"choose offscreen format");
  CGLContextObj context;require(CGLCreateContext(format,NULL,&context)==kCGLNoError,"offscreen context");CGLDestroyPixelFormat(format);CGLSetCurrentContext(context);
  printf("Offscreen GPU: %s / %s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
  GLuint tmd=program(argv[1],"tmd.vsh","tmd.gsh","tmd.fsh"),battle=program(argv[1],"battle_tmd.vsh","tmd.gsh","battle_tmd.fsh");
  require(glGetUniformLocation(tmd,"smoothLighting")>=0&&glGetUniformLocation(battle,"smoothLighting")>=0,"lighting controls active in both pipelines");
  GLuint post=program(argv[1],"post.vsh",NULL,"screen.fsh"),old=program(argv[2],"post.vsh",NULL,"screen.fsh");
  GLuint fbo,dest,tex,vao,vbo;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenTextures(1,&dest);glBindTexture(GL_TEXTURE_2D,dest);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,dest,0);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"offscreen framebuffer");
  glGenTextures(1,&tex);glBindTexture(GL_TEXTURE_2D,tex);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
  // Same position/UV vertex layout as post.vsh.
  float vertices[]={-1,-1,0,0, 1,-1,1,0, 1,1,1,1, -1,-1,0,0, 1,1,1,1, -1,1,0,1};
  glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,sizeof vertices,vertices,GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,4*sizeof(float),0);glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));glViewport(0,0,W,H);
  for(int i=0;i<W*H;i++){input[i*4]=37;input[i*4+1]=112;input[i*4+2]=196;input[i*4+3]=255;}
  screenDraw(old,tex,0,a);screenDraw(post,tex,1,b);require(memcmp(a,b,sizeof a)==0,"flat colours remain exact with edge smoothing");
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;input[i]=input[i+1]=input[i+2]=(x>y/2+25?230:20);}
  screenDraw(old,tex,0,a);screenDraw(post,tex,0,b);require(memcmp(a,b,sizeof a)==0,"disabled screen matches original pixels");
  screenDraw(post,tex,1,b);screenDraw(post,tex,1,c);require(memcmp(b,c,sizeof b)==0,"no temporal noise/history");
  int changed=0,remote=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;require(b[i+3]==255,"opaque output");if(memcmp(a+i,b+i,3)){changed++;if(abs(x-(y/2+25))>6&&x>5&&x<W-6&&y>5&&y<H-6)remote++;}}
  require(changed>0&&remote==0,"edge smoothing affects the diagonal without blurring remote flat regions");printf("PASS: pipelines compile/link; flat preservation, original bypass, deterministic diagonal smoothing (%d pixels), bounded footprint and alpha.\n",changed);
  if(argc==6){GLuint reference=program(argv[5],"post.vsh",NULL,"screen.fsh");screenDraw(reference,tex,1,c);int maximum=0;for(int i=0;i<sizeof b;i++){int error=abs((int)b[i]-c[i]);if(error>maximum)maximum=error;}require(maximum<=1,"hardware filtering matches manual bilinear within one output level");printf("PASS: optimized filter vs manual reference: maximum channel difference %d/255.\n",maximum);glDeleteProgram(reference);}
  if(argc==5||argc==6){GLuint original=program(argv[2],"battle_tmd.vsh","tmd.gsh","battle_tmd.fsh");scene(battle,original,post,argv[3],argv[4]);glDeleteProgram(original);char output[4096];snprintf(output,sizeof output,"%s-field",argv[4]);original=program(argv[2],"tmd.vsh","tmd.gsh","tmd.fsh");scene(tmd,original,post,argv[3],output);glDeleteProgram(original);}
  if(argc==7){shadowDraw(argv[1],argv[3],argv[5],0);shadowDraw(argv[1],argv[4],argv[6],0);for(int pipeline=1;pipeline<=2;pipeline++){char output[4096];snprintf(output,sizeof output,"%s-tmd%d",argv[6],pipeline);shadowDraw(argv[1],argv[4],output,pipeline);}}
  glDeleteProgram(post);glDeleteProgram(old);glDeleteProgram(tmd);glDeleteProgram(battle);CGLSetCurrentContext(NULL);CGLDestroyContext(context);return 0;
}
