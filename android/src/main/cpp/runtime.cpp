#include <jni.h>
#include <mutex>
#include <string>
#include <unordered_map>

namespace {
struct Runtime {
  std::string storage_root;
  std::unordered_map<std::string, std::string> states;
  std::unordered_map<std::string, std::string> storage;
  std::mutex mutex;
};

std::string to_string(JNIEnv* env, jstring value) {
  if (value == nullptr) return {};
  const char* chars = env->GetStringUTFChars(value, nullptr);
  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}

Runtime* from_handle(jlong handle) { return reinterpret_cast<Runtime*>(handle); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_createRuntime(
    JNIEnv* env, jobject, jstring storage_root) {
  auto* runtime = new Runtime();
  runtime->storage_root = to_string(env, storage_root);
  return reinterpret_cast<jlong>(runtime);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_mountInstanceStorage(
    JNIEnv* env, jobject, jlong handle, jstring instance_id, jstring storage_path) {
  Runtime* runtime = from_handle(handle);
  if (runtime == nullptr) return JNI_FALSE;
  std::lock_guard<std::mutex> lock(runtime->mutex);
  runtime->storage[to_string(env, instance_id)] = to_string(env, storage_path);
  return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_startInstance(
    JNIEnv* env, jobject, jlong handle, jstring instance_id, jstring) {
  Runtime* runtime = from_handle(handle);
  if (runtime == nullptr) return;
  std::lock_guard<std::mutex> lock(runtime->mutex);
  runtime->states[to_string(env, instance_id)] = "running";
}

extern "C" JNIEXPORT void JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_stopInstance(
    JNIEnv* env, jobject, jlong handle, jstring instance_id) {
  Runtime* runtime = from_handle(handle);
  if (runtime == nullptr) return;
  std::lock_guard<std::mutex> lock(runtime->mutex);
  runtime->states[to_string(env, instance_id)] = "stopped";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_getRuntimeStatus(
    JNIEnv* env, jobject, jlong handle, jstring instance_id) {
  Runtime* runtime = from_handle(handle);
  if (runtime == nullptr) return env->NewStringUTF("destroyed");
  std::lock_guard<std::mutex> lock(runtime->mutex);
  auto found = runtime->states.find(to_string(env, instance_id));
  return env->NewStringUTF(found == runtime->states.end() ? "not_initialized" : found->second.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_parallelspace_controller_runtime_NativeRuntime_destroyRuntime(
    JNIEnv*, jobject, jlong handle) {
  delete from_handle(handle);
}
