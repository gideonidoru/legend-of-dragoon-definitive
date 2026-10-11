// Optional macOS JNI test context. No window or application UI. AGPL v3; see LICENSE.
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
JNIEXPORT jlong JNICALL Java_legend_definitive_rendering_NativeRendererProbe_open(JNIEnv *env,jclass type) {
  CGLPixelFormatAttribute attributes[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAccelerated,(CGLPixelFormatAttribute)0};
  CGLPixelFormatObj format=NULL;GLint count=0;CGLContextObj context=NULL;
  CGLError error=CGLChoosePixelFormat(attributes,&format,&count);
  if(error!=kCGLNoError||count==0){if(format)CGLDestroyPixelFormat(format);return 0;}
  error=CGLCreateContext(format,NULL,&context);
  CGLError formatError=CGLDestroyPixelFormat(format);
  if(error!=kCGLNoError||formatError!=kCGLNoError){if(context)CGLDestroyContext(context);return 0;}
  if(CGLSetCurrentContext(context)!=kCGLNoError){CGLDestroyContext(context);return 0;}
  return (jlong)context;
}
static jlong closeContext(jlong handle,jboolean injectFailure) {
  // Both operations must run even when unbinding fails. Injection reports a failure
  // after real destruction, so the negative control cannot leak or double-close CGL.
  CGLError unbind=CGLSetCurrentContext(NULL);
  CGLError destroy=CGLDestroyContext((CGLContextObj)handle);
  if(injectFailure&&destroy==kCGLNoError)destroy=kCGLBadContext;
  return (jlong)(((uint64_t)(uint32_t)unbind<<32)|(uint32_t)destroy);
}
JNIEXPORT void JNICALL Java_legend_definitive_rendering_NativeRendererProbe_close(JNIEnv *env,jclass type,jlong handle) {
  uint64_t statuses=(uint64_t)closeContext(handle,JNI_FALSE);
  if(statuses){
    char message[128];snprintf(message,sizeof message,"CGL cleanup failed: unbind=%u destroy=%u",(unsigned)(statuses>>32),(unsigned)statuses);
    jclass exception=(*env)->FindClass(env,"java/lang/IllegalStateException");
    if(exception)(*env)->ThrowNew(env,exception,message);
  }
}
JNIEXPORT jlong JNICALL Java_legend_definitive_rendering_NativeViewportReplayProbe_closeContext(JNIEnv *env,jclass type,jlong handle,jboolean injectFailure) {
  return closeContext(handle,injectFailure);
}
