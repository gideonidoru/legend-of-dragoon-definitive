// Windowless GPU fixture for the experimental supplemental albedo. AGPL v3.
#define main legacy_probe_main
#include "../../scripts/headless-rendering-probe.c"
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



int main(int argc, char **argv) {
  require(argc == 3, "usage: face-detail-probe shader-dir baseline-shader-dir");
  CGLPixelFormatAttribute attributes[] = {kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core, kCGLPFAAccelerated, 0};
  CGLPixelFormatObj format; GLint count;
  require(CGLChoosePixelFormat(attributes, &format, &count) == kCGLNoError && count > 0, "offscreen format");
  CGLContextObj context;
  require(CGLCreateContext(format, NULL, &context) == kCGLNoError, "offscreen context");
  CGLDestroyPixelFormat(format); CGLSetCurrentContext(context);
  printf("Offscreen GPU: %s / %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));
  GLuint fbo; glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
  GLuint destination = texture(7, W, H, GL_RGBA8, GL_RGBA, NULL);
  glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, destination, 0);
  require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "framebuffer");
  unsigned char body[] = {25, 180, 40, 0}, face[] = {210, 80, 25, 255};
  texture(0, 1, 1, GL_RGBA8, GL_RGBA, body);
  texture(6, 1, 1, GL_RGBA8, GL_RGBA, face);
  unsigned short indexed = 0; texture(1, 1, 1, GL_R16UI, GL_RED_INTEGER, &indexed);
  GLuint vao, vbo; glGenVertexArrays(1, &vao); glBindVertexArray(vao);
  glGenBuffers(1, &vbo); glBindBuffer(GL_ARRAY_BUFFER, vbo);
  int sizes[] = {4, 3, 2, 1, 1, 4, 1}, offset = 0;
  for(int i = 0; i < 7; i++) {
    glEnableVertexAttribArray(i); glVertexAttribPointer(i, sizes[i], GL_FLOAT, GL_FALSE, 64, (void *)(long)(offset*4)); offset += sizes[i];
  }
  glViewport(0, 0, W, H); float lights[4096] = {0}, vertices[96];
  lights[10] = -1; lights[24] = lights[25] = lights[26] = .25f;
  lights[28] = lights[29] = lights[30] = .1f;
  for(int pipeline = 0; pipeline < 2; pipeline++) {
    const char *vertex = pipeline ? "battle_tmd.vsh" : "tmd.vsh", *fragment = pipeline ? "battle_tmd.fsh" : "tmd.fsh";
    GLuint p = program(argv[1], vertex, "tmd.gsh", fragment), old = program(argv[2], vertex, "tmd.gsh", fragment);
    uniforms(old, lights); quad(vertices, 7, 1); clearTargets(); draw(old, 1, vertices); readTarget(0, a);
    uniforms(p, lights); integer(p, "faceDetailTex", 6); clearTargets(); draw(p, 1, vertices); readTarget(0, b);
    require(memcmp(a, b, sizeof a) == 0, "detail disabled matches committed body pixels");
    quad(vertices, 7 | 0x20000, 1); clearTargets(); draw(p, 1, vertices); readTarget(0, c);
    int center = (64*W+64)*4;
    require(c[center] > c[center+1] && c[center+1] > c[center+2] && c[center+3] == 255, "independent face albedo is opaque and retains hue");
    assertCoverage(a, c); require(differences(a, c) > 0, "detail changes color without changing geometry coverage");
    glUniform2f(glGetUniformLocation(p,"uvOffset"), 123, 123); clearTargets(); draw(p, 1, vertices); readTarget(0, b);
    require(memcmp(b, c, sizeof b) == 0, "body UV offset cannot shift face detail");
    integer(p,"normalMapEnabled",1); integer(p,"roughnessMapEnabled",1);
    integer(p,"normalMapTex",0); integer(p,"roughnessMapTex",0);
    uniform(p,"normalMapStrength",1); clearTargets(); draw(p,1,vertices); readTarget(0,b);
    require(memcmp(b,c,sizeof b) == 0, "body surface maps cannot distort face detail");
    unsigned char black[] = {0,0,0,255};
    glActiveTexture(GL_TEXTURE6); glTexSubImage2D(GL_TEXTURE_2D,0,0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,black);
    clearTargets(); draw(p,1,vertices); readTarget(0,b);
    require(b[center]==0 && b[center+1]==0 && b[center+2]==0 && b[center+3]==255,"authored opaque black is not a PSX discard pixel");
    glActiveTexture(GL_TEXTURE6); glTexSubImage2D(GL_TEXTURE_2D,0,0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,face);
    printf("PASS: %s compile/link, baseline bypass, independent face/body sampling, opaque alpha, matching coverage, body UV/map isolation.\n",pipeline ? "battle" : "field");
    glDeleteProgram(old); glDeleteProgram(p);
  }
  require(glGetError() == GL_NO_ERROR, "final GPU state");
  CGLSetCurrentContext(NULL); CGLDestroyContext(context); return 0;
}
