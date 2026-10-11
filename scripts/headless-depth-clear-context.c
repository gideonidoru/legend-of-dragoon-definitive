// Optional macOS JNI regression context. No window/application UI. AGPL v3; see LICENSE.
#include <jni.h>
#include <stdint.h>
#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>

JNIEXPORT jlong JNICALL Java_legend_definitive_rendering_NativeDepthClearProbe_open(JNIEnv *env, jclass type) {
  CGLPixelFormatAttribute attributes[] = {kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
    kCGLPFAAccelerated, (CGLPixelFormatAttribute)0};
  CGLPixelFormatObj format;
  GLint count;
  CGLContextObj context;
  if(CGLChoosePixelFormat(attributes, &format, &count) != kCGLNoError || count == 0) return 0;
  CGLError error = CGLCreateContext(format, NULL, &context);
  CGLDestroyPixelFormat(format);
  if(error != kCGLNoError) return 0;
  if(CGLSetCurrentContext(context) != kCGLNoError) {
    CGLDestroyContext(context);
    return 0;
  }
  return (jlong)context;
}

JNIEXPORT jlong JNICALL Java_legend_definitive_rendering_NativeDepthClearProbe_close(JNIEnv *env, jclass type, jlong handle, jboolean injectDestroyFailure) {
  CGLError unbindError = CGLSetCurrentContext(NULL);
  CGLError destroyError = CGLDestroyContext((CGLContextObj)handle);
  // Fault fixture reports a failed teardown status AFTER real context destruction.
  // It never calls the driver with an invalid pointer or leaks a test context.
  if(injectDestroyFailure && destroyError == kCGLNoError) destroyError = kCGLBadContext;
  return (jlong)(((uint64_t)(uint32_t)unbindError << 32) | (uint32_t)destroyError);
}
