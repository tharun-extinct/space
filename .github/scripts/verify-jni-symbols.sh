#!/usr/bin/env bash
set -euo pipefail

java_source="android/src/main/java/com/parallelspace/controller/runtime/NativeRuntime.java"
native_library="android/runtime-core/jniLibs/arm64-v8a/libparallel_runtime.so"

package_name="$(sed -n 's/^package \([^;]*\);/\1/p' "$java_source")"
if [[ -z "$package_name" ]]; then
  echo "Could not determine the NativeRuntime Java package" >&2
  exit 1
fi

jni_class="${package_name//./_}_NativeRuntime"
methods=(
  getApiVersion
  createRuntime
  mountInstanceStorage
  startInstance
  stopInstance
  getRuntimeStatus
  destroyRuntime
)

symbols="$(nm -D --defined-only "$native_library")"
for method in "${methods[@]}"; do
  expected="Java_${jni_class}_${method}"
  if ! grep -q "[[:space:]]${expected}$" <<<"$symbols"; then
    echo "Missing JNI export: $expected" >&2
    exit 1
  fi
done

echo "Verified ${#methods[@]} NativeRuntime JNI exports for $package_name"
