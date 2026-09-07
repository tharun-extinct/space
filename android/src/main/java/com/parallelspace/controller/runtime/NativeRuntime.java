package com.parallelspace.controller.runtime;

/** Coarse, versioned JNI boundary. Android framework routing stays in Java. */
public final class NativeRuntime {
  public static final int API_VERSION = 1;
  static { System.loadLibrary("parallel_runtime"); }
  public native long createRuntime(String storagePath);
  public native void startInstance(long runtime, String instanceId, String packageDescriptor);
  public native void stopInstance(long runtime, String instanceId);
  public native String getRuntimeStatus(long runtime, String instanceId);
  public native void destroyRuntime(long runtime);
}
