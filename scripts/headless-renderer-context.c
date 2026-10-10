// Optional macOS JNI test context. No window or application UI. AGPL v3; see LICENSE.
#include <jni.h>
#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
JNIEXPORT jlong JNICALL Java_legend_definitive_rendering_NativeRendererProbe_open(JNIEnv *env,jclass type) {
  CGLPixelFormatAttribute attributes[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAccelerated,(CGLPixelFormatAttribute)0};
  CGLPixelFormatObj format;GLint count;CGLContextObj context;
  if(CGLChoosePixelFormat(attributes,&format,&count)!=kCGLNoError||count==0)return 0;
  CGLError error=CGLCreateContext(format,NULL,&context);CGLDestroyPixelFormat(format);
  if(error!=kCGLNoError)return 0;CGLSetCurrentContext(context);return (jlong)context;
}
JNIEXPORT void JNICALL Java_legend_definitive_rendering_NativeRendererProbe_close(JNIEnv *env,jclass type,jlong handle) {
  CGLSetCurrentContext(NULL);CGLDestroyContext((CGLContextObj)handle);
}
