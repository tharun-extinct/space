#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_getRuntimeStatus(
    JNIEnv* env, jobject, jlong, jstring) {
  return env->NewStringUTF("not_initialized");
}

// Remaining NativeRuntime methods are intentionally deferred until the Java
// control plane has durable state and compatibility validation.
